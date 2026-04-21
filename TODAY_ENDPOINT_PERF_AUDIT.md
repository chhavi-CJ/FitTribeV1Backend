# GET /sessions/today — Performance Audit

**Date:** 2026-04-20
**Symptom:** ~600ms–1s TTFB on Railway warm requests. Target: <200ms.

---

## 1. Query count per request

| # | Query | Source | Line |
|---|-------|--------|------|
| 0 | `SELECT * FROM users WHERE id = ?` | JwtAuthFilter (auth check) | JwtAuthFilter.java:48 |
| 1 | `SELECT * FROM workout_sessions WHERE user_id = ? AND started_at BETWEEN ? AND ? ORDER BY started_at DESC LIMIT 1` | Session fetch | SessionController.java:1072 |
| 2 | `SELECT * FROM set_logs WHERE session_id = ?` | SetLog fetch (unused for COMPLETED — only for fallback totalSets) | SessionController.java:1080 |
| 3 | `SELECT * FROM users WHERE id = ?` | User fetch (streak) — **DUPLICATE of query 0** | SessionController.java:1082 |
| 4 | `SELECT COUNT(*) FROM workout_sessions WHERE user_id = ? AND status = 'COMPLETED' AND finished_at BETWEEN ? AND ?` | Weekly completion count | SessionController.java:1087 |
| 5 | `SELECT * FROM user_session_feedback WHERE session_id = ?` | Feedback fetch | SessionController.java:1114 |
| 6 | `SELECT * FROM pr_events WHERE session_id = ? AND superseded_at IS NULL` | PR flags | SessionController.java:1129 |

**Total: 7 SQL queries per request.** Query 2 (set_logs) is wasted for COMPLETED sessions — its only use is the `logs.size()` fallback for `totalSets` when `session.getTotalSets()` is null, which never happens for completed sessions (finish always writes totalSets). Query 3 is a duplicate user load already performed by JwtAuthFilter.

---

## 2. aiCoachInsight source

**Cached in DB. NOT generated per-request.**

`session.getAiInsight()` at line 1126 reads the `ai_insight` TEXT column from `workout_sessions`. This column is populated asynchronously by `AiService.generateAndSaveInsight()` which runs `@Async` after session finish. The `/today` endpoint just reads the stored value. **Not a bottleneck.**

---

## 3. Index coverage audit

### workout_sessions

| Query | Needs | Has | Status |
|-------|-------|-----|--------|
| `findFirstByUserIdAndStartedAtBetween...Desc` | `(user_id, started_at DESC)` composite | `idx_sessions_user_id (user_id)` + `idx_sessions_started_at (started_at DESC)` — **separate indexes** | MISSING composite |
| `countByUserIdAndStatusAndFinishedAtBetween` | `(user_id, status, finished_at)` composite | `idx_workout_sessions_user_finished (user_id, finished_at DESC)` — **missing status** | MISSING status column |

### set_logs

| Query | Needs | Has | Status |
|-------|-------|-----|--------|
| `findBySessionId` | `(session_id)` | `idx_set_logs_session_id` | COVERED |

### user_session_feedback

| Query | Needs | Has | Status |
|-------|-------|-----|--------|
| `findBySessionId` | `(session_id)` | `idx_session_feedback_session` + unique constraint on session_id | COVERED |

### pr_events

| Query | Needs | Has | Status |
|-------|-------|-----|--------|
| `findBySessionIdAndSupersededAtIsNull` | `(session_id) WHERE superseded_at IS NULL` partial | `idx_pr_events_session_id (session_id)` + `idx_pr_events_superseded (superseded_at) WHERE superseded_at IS NULL` — **separate** | MISSING composite partial |

---

## 4. Timing estimate (no live profiling yet — based on code analysis)

| Operation | Est. cost | Notes |
|-----------|-----------|-------|
| JwtAuthFilter user load | ~50ms | Railway PG in Singapore, round-trip latency ~30-50ms per query |
| Session fetch (no composite index) | ~50-80ms | Index scan on user_id, then sort on started_at |
| SetLog fetch (WASTED) | ~40-50ms | Full scan of session's set_logs — unnecessary for COMPLETED |
| User fetch (DUPLICATE) | ~50ms | Identical to JwtAuthFilter query |
| Weekly completion count | ~50-80ms | Partial index miss (no status in composite) |
| Feedback fetch | ~30ms | Indexed, single row |
| pr_events fetch | ~30-40ms | session_id indexed, small result set |
| JSONB parsing (CPU) | ~5ms | In-memory, no DB |
| Response serialization | ~5ms | In-memory |
| **Total estimated** | **~310-440ms** | |

The 600ms–1s observed on Railway includes network overhead (client → Railway → PG → back). Each DB round-trip is ~30-50ms network latency alone (Railway app + Railway PG both in Singapore, but still separate containers).

---

## 5. Recommended fixes (priority order)

### P0: Eliminate duplicate user fetch — saves 1 round-trip (~50ms)

`JwtAuthFilter` already loads `users WHERE id = ?` to check `is_active`. The controller loads the same user again at line 1082 for `user.getStreak()`. Pass the loaded user via `SecurityContext` attributes or cache it in a request-scoped bean.

### P1: Remove wasted set_logs query — saves 1 round-trip (~40-50ms)

`setLogRepo.findBySessionId()` at line 1080 fetches all set_log rows for the session. For COMPLETED sessions, `session.getTotalSets()` is always non-null (written at finish). The `logs.size()` fallback is dead code for the COMPLETED path. Guard with: `if (session.getTotalSets() == null) { logs = setLogRepo.findBySessionId(...); }` — or just remove the query and use 0 as fallback.

### P2: Add composite indexes — saves ~50-100ms from better query plans

New migration with three indexes:
```sql
-- Composite for the /today session lookup
CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_started
    ON workout_sessions(user_id, started_at DESC);

-- Composite with status for weekly completion count
CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_status_finished
    ON workout_sessions(user_id, status, finished_at DESC);

-- Composite partial for PR event lookup
CREATE INDEX IF NOT EXISTS idx_pr_events_session_not_superseded
    ON pr_events(session_id) WHERE superseded_at IS NULL;
```

### P3: Consider caching user in JwtAuthFilter (request scope)

Store the loaded `User` entity in a request attribute during authentication. Controllers extract it instead of re-querying. Eliminates ALL duplicate user loads across every authenticated endpoint, not just `/today`.

### Expected total savings

| Fix | Savings | Queries after |
|-----|---------|---------------|
| Before | — | 7 |
| P0 (dedup user) | ~50ms, -1 query | 6 |
| P1 (remove set_logs) | ~40-50ms, -1 query | 5 |
| P2 (composite indexes) | ~50-100ms, same queries | 5 |
| **After all** | **~140-200ms** | **5 queries** |

Target 200ms TTFB should be achievable with P0 + P1 + P2 combined, bringing estimated server time to ~150-250ms.

---

## 6. Out of scope (noted for future)

- **Connection pooling:** Railway PG default pool may be undersized. Check `spring.datasource.hikari.maximum-pool-size` if latency spikes under concurrent load.
- **5am day boundary:** The `/today` query uses UTC midnight, not 5am IST. Separate ticket (deferred from previous audit).
- **JSONB parsing:** The exercises JSONB parse + enrichment loop is CPU-bound, not IO-bound. ~5ms. Not a bottleneck.
