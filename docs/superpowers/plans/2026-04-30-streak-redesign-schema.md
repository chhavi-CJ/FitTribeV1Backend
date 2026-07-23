# Streak Redesign — Schema Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay the schema and entity foundation for the new streak/freeze system — two Flyway migrations plus Java rename — with zero behavior changes and a green build throughout.

**Architecture:** Two sequential Flyway migrations (V61, V62) create the `bonus_freeze_grants` table and rename the `streak_freeze_balance` column. All Java callers of the renamed column/field are updated in the same changeset so the build stays green. No scheduler deletions, no new endpoints, no logic changes.

**Tech Stack:** Spring Boot 3.3.5, JPA/Hibernate, Flyway, PostgreSQL, Lombok (not used on freeze field — plain getters/setters), Maven.

---

## Constraints (read before touching anything)

- **Branch:** `feat/streak-redesign-schema` off `main` — create this first
- **Do NOT** delete `StreakFreezeScheduler.java` or `StreakScheduler.java`
- **Do NOT** run Flyway against any database
- **Do NOT** add any business logic, services, or endpoints
- **Do NOT** commit or push — user reviews first

---

## File Map

| Action | File | What changes |
|--------|------|-------------|
| Create | `src/main/resources/db/migration/V61__bonus_freeze_grants.sql` | New table + indexes |
| Create | `src/main/resources/db/migration/V62__rename_streak_freeze_balance.sql` | Column rename DDL |
| Modify | `src/main/java/com/fittribe/api/entity/User.java` | Field name, `@Column`, getter, setter |
| Modify | `src/main/java/com/fittribe/api/dto/response/UserProfileResponse.java` | Record component name |
| Modify | `src/main/java/com/fittribe/api/controller/RedeemController.java` | 2 SQL strings + 1 map key |
| Modify | `src/main/java/com/fittribe/api/controller/UserController.java` | 1 getter call |
| Modify | `src/main/java/com/fittribe/api/scheduler/StreakFreezeScheduler.java` | 2 SQL strings (keep file, just fix column name) |

---

## Task 1: Create the branch

**Files:** none

- [ ] **Step 1.1: Confirm you are on main and it is clean**

```bash
git status
git branch --show-current
```

Expected: `main`, nothing to commit.

- [ ] **Step 1.2: Create the feature branch**

```bash
git checkout -b feat/streak-redesign-schema
```

Expected output:
```
Switched to a new branch 'feat/streak-redesign-schema'
```

---

## Task 2: V61 — Create `bonus_freeze_grants` table

**Files:**
- Create: `src/main/resources/db/migration/V61__bonus_freeze_grants.sql`

- [ ] **Step 2.1: Confirm V60 is the highest existing migration**

```bash
ls src/main/resources/db/migration/ | sort -V | tail -5
```

Expected: last line is `V60__create_device_tokens.sql`. If any V61 already exists, stop and flag it — do not overwrite.

- [ ] **Step 2.2: Create V61**

Create `src/main/resources/db/migration/V61__bonus_freeze_grants.sql` with this exact content:

```sql
-- Bonus freeze tokens earned by hitting the weekly workout goal.
-- Purchased freezes live on users.purchased_freeze_balance (formerly streak_freeze_balance).
-- The active bonus balance is DERIVED:
--   SELECT COUNT(*) FROM bonus_freeze_grants
--   WHERE user_id = ? AND consumed_at IS NULL
--     AND valid_from <= NOW() AND expires_at > NOW()

CREATE TABLE bonus_freeze_grants (
    id                   BIGSERIAL                    PRIMARY KEY,
    user_id              UUID                         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    earned_at            TIMESTAMP WITH TIME ZONE     NOT NULL,
    valid_from           TIMESTAMP WITH TIME ZONE     NOT NULL,
    expires_at           TIMESTAMP WITH TIME ZONE     NOT NULL,
    consumed_at          TIMESTAMP WITH TIME ZONE,
    consumption_reason   VARCHAR(20)                  CHECK (consumption_reason IN ('AUTO_APPLY', 'EXPIRED')),
    created_at           TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW()
);

-- Supports active-balance queries (user_id + unexpired + unconsumed)
CREATE INDEX idx_bonus_freeze_grants_user_active
    ON bonus_freeze_grants(user_id, expires_at)
    WHERE consumed_at IS NULL;

-- Supports the nightly expiry cleanup cron (scan all unexpired rows globally)
CREATE INDEX idx_bonus_freeze_grants_expiry_cleanup
    ON bonus_freeze_grants(expires_at)
    WHERE consumed_at IS NULL;
```

- [ ] **Step 2.3: Verify the file exists and is not empty**

```bash
ls -la src/main/resources/db/migration/V61__bonus_freeze_grants.sql
cat src/main/resources/db/migration/V61__bonus_freeze_grants.sql
```

Expected: file exists, content matches exactly what you wrote.

---

## Task 3: V62 — Rename `streak_freeze_balance` → `purchased_freeze_balance`

**Files:**
- Create: `src/main/resources/db/migration/V62__rename_streak_freeze_balance.sql`

- [ ] **Step 3.1: Create V62**

Create `src/main/resources/db/migration/V62__rename_streak_freeze_balance.sql` with this exact content:

```sql
-- Rename to make clear this column tracks only purchased freezes.
-- Bonus freezes earned by hitting weekly goal live in bonus_freeze_grants.
ALTER TABLE users RENAME COLUMN streak_freeze_balance TO purchased_freeze_balance;
```

- [ ] **Step 3.2: Verify**

```bash
ls -la src/main/resources/db/migration/V62__rename_streak_freeze_balance.sql
cat src/main/resources/db/migration/V62__rename_streak_freeze_balance.sql
```

Expected: single `ALTER TABLE` statement, no trailing content.

---

## Task 4: Update `User.java` entity

**Files:**
- Modify: `src/main/java/com/fittribe/api/entity/User.java`
  - Lines 71-72: field declaration + `@Column`
  - Lines 162-163: getter and setter

The current code at lines 71-72:
```java
@Column(name = "streak_freeze_balance", nullable = false)
private Integer streakFreezeBalance = 0;
```

The current code at lines 162-163:
```java
public Integer getStreakFreezeBalance()                          { return streakFreezeBalance; }
public void setStreakFreezeBalance(Integer streakFreezeBalance)  { this.streakFreezeBalance = streakFreezeBalance; }
```

- [ ] **Step 4.1: Replace the field declaration (lines 71-72)**

Replace:
```java
    @Column(name = "streak_freeze_balance", nullable = false)
    private Integer streakFreezeBalance = 0;
```

With:
```java
    /**
     * Count of freeze tokens the user has purchased with coins.
     * Persistent — never expires, no cap, never auto-zeroed.
     * Bonus freezes (earned by hitting weekly goal) live separately
     * in the bonus_freeze_grants table and have a 28-day expiry.
     */
    @Column(name = "purchased_freeze_balance", nullable = false)
    private Integer purchasedFreezeBalance = 0;
```

- [ ] **Step 4.2: Replace the getter and setter (lines 162-163)**

Replace:
```java
    public Integer getStreakFreezeBalance()                          { return streakFreezeBalance; }
    public void setStreakFreezeBalance(Integer streakFreezeBalance)  { this.streakFreezeBalance = streakFreezeBalance; }
```

With:
```java
    public Integer getPurchasedFreezeBalance()                             { return purchasedFreezeBalance; }
    public void setPurchasedFreezeBalance(Integer purchasedFreezeBalance)  { this.purchasedFreezeBalance = purchasedFreezeBalance; }
```

- [ ] **Step 4.3: Verify no old name remains in User.java**

```bash
grep -n "streakFreezeBalance\|streak_freeze_balance" src/main/java/com/fittribe/api/entity/User.java
```

Expected: no output (zero hits).

---

## Task 5: Update `UserProfileResponse.java` DTO

**Files:**
- Modify: `src/main/java/com/fittribe/api/dto/response/UserProfileResponse.java`
  - Line 20: record component name

Current line 20:
```java
        int streakFreezeBalance,
```

- [ ] **Step 5.1: Rename the record component**

Replace:
```java
        int streakFreezeBalance,
```

With:
```java
        int purchasedFreezeBalance,
```

- [ ] **Step 5.2: Verify**

```bash
grep -n "streakFreezeBalance\|streak_freeze_balance" src/main/java/com/fittribe/api/dto/response/UserProfileResponse.java
```

Expected: no output.

---

## Task 6: Update `UserController.java`

**Files:**
- Modify: `src/main/java/com/fittribe/api/controller/UserController.java`
  - Line 338: the call to `user.getStreakFreezeBalance()`

This line is inside the method that builds a `UserProfileResponse`. It passes the freeze balance as a positional record component.

- [ ] **Step 6.1: Find the exact context**

```bash
grep -n "getStreakFreezeBalance\|StreakFreezeBalance\|streak_freeze" src/main/java/com/fittribe/api/controller/UserController.java
```

Expected output: one hit at approximately line 338.

- [ ] **Step 6.2: Replace the getter call**

Replace:
```java
                user.getStreakFreezeBalance() != null ? user.getStreakFreezeBalance() : 0,
```

With:
```java
                user.getPurchasedFreezeBalance() != null ? user.getPurchasedFreezeBalance() : 0,
```

- [ ] **Step 6.3: Verify**

```bash
grep -n "streakFreezeBalance\|streak_freeze_balance\|getStreakFreeze\|setStreakFreeze" src/main/java/com/fittribe/api/controller/UserController.java
```

Expected: no output.

---

## Task 7: Update `RedeemController.java`

**Files:**
- Modify: `src/main/java/com/fittribe/api/controller/RedeemController.java`
  - Line 83: `UPDATE users SET streak_freeze_balance = ...`
  - Line 88: `SELECT streak_freeze_balance FROM users ...`
  - Line 92: `result.put("streakFreezeBalance", ...)`

- [ ] **Step 7.1: Replace the UPDATE SQL (line 83)**

Replace:
```java
                "UPDATE users SET streak_freeze_balance = streak_freeze_balance + 1 WHERE id = ?",
```

With:
```java
                "UPDATE users SET purchased_freeze_balance = purchased_freeze_balance + 1 WHERE id = ?",
```

- [ ] **Step 7.2: Replace the SELECT SQL (line 88)**

Replace:
```java
                "SELECT streak_freeze_balance FROM users WHERE id = ?", Integer.class, userId);
```

With:
```java
                "SELECT purchased_freeze_balance FROM users WHERE id = ?", Integer.class, userId);
```

- [ ] **Step 7.3: Replace the response map key (line 92)**

Replace:
```java
        result.put("streakFreezeBalance", freezeBalance);
```

With:
```java
        result.put("purchasedFreezeBalance", freezeBalance);
```

Note: this changes the JSON key returned to the frontend. Frontend callers must be updated separately (out of scope for this backend-only commit, but flag it).

- [ ] **Step 7.4: Verify**

```bash
grep -n "streakFreezeBalance\|streak_freeze_balance" src/main/java/com/fittribe/api/controller/RedeemController.java
```

Expected: no output.

---

## Task 8: Update `StreakFreezeScheduler.java`

**Files:**
- Modify: `src/main/java/com/fittribe/api/scheduler/StreakFreezeScheduler.java`
  - Line 45: `streak_freeze_balance > 0` in the eligibility query
  - Line 72: `UPDATE users SET streak_freeze_balance = ...`

This file is NOT being deleted in this commit. It keeps running daily as before. The only change is fixing the column name so it references a column that actually exists after V62 runs.

- [ ] **Step 8.1: Replace the WHERE clause (line 45)**

Replace:
```java
                "  AND u.streak_freeze_balance > 0 " +
```

With:
```java
                "  AND u.purchased_freeze_balance > 0 " +
```

- [ ] **Step 8.2: Replace the UPDATE (line 72)**

Replace:
```java
                        "UPDATE users SET streak_freeze_balance = streak_freeze_balance - 1 WHERE id = ?",
```

With:
```java
                        "UPDATE users SET purchased_freeze_balance = purchased_freeze_balance - 1 WHERE id = ?",
```

- [ ] **Step 8.3: Verify**

```bash
grep -n "streakFreezeBalance\|streak_freeze_balance" src/main/java/com/fittribe/api/scheduler/StreakFreezeScheduler.java
```

Expected: no output.

---

## Task 9: Compile verification

- [ ] **Step 9.1: Run a full compile from the project root**

```bash
cd /Users/chhavijaiswal/Downloads/Chhavi_MVP_release1/FitTribeRepo/FitTribeV1Backend/FitTribeV1Backend
mvn compile -q 2>&1
```

Expected: exits with code 0, no `[ERROR]` lines. If errors appear, read them carefully — they will point to a remaining old reference that was missed in Tasks 4-8.

- [ ] **Step 9.2: Global grep for old names across all production Java**

```bash
grep -rn "streakFreezeBalance\|streak_freeze_balance" src/main/java/ --include="*.java"
```

Expected: **zero output**. If any hits remain, fix them before proceeding.

- [ ] **Step 9.3: Confirm both migration files exist with sequential version numbers**

```bash
ls src/main/resources/db/migration/V6*.sql | sort -V
```

Expected output includes:
```
src/main/resources/db/migration/V60__create_device_tokens.sql
src/main/resources/db/migration/V61__bonus_freeze_grants.sql
src/main/resources/db/migration/V62__rename_streak_freeze_balance.sql
```

- [ ] **Step 9.4: Scan test sources for old name (informational — do not fix in this commit)**

```bash
grep -rn "streakFreezeBalance\|streak_freeze_balance" src/test/ --include="*.java" 2>/dev/null || echo "no test dir or no hits"
```

Report any hits. They are test fixtures or test assertions that will need updating before any test suite runs, but they are out of scope for this schema-only commit.

---

## Task 10: Report results

Do not commit or push. Report back with:

1. Full list of files created (paths)
2. Full list of files modified (paths + line ranges changed)
3. Output of the compile command from Step 9.1
4. Output of the global grep from Step 9.2
5. Output of the migration file listing from Step 9.3
6. Any remaining old-name hits in test sources (Step 9.4), if any

---

## Self-Review Checklist

- [x] V61 creates `bonus_freeze_grants` with all 8 columns from spec
- [x] V61 creates both indexes (user-active query, expiry-cleanup cron)
- [x] V62 is a single `ALTER TABLE ... RENAME COLUMN` — no data loss risk
- [x] `User.java` field, `@Column`, getter, setter all renamed
- [x] Javadoc comment added to field per spec requirement
- [x] `UserProfileResponse.java` record component renamed
- [x] `UserController.java` getter call updated to match new method name
- [x] `RedeemController.java` UPDATE SQL, SELECT SQL, and map key all updated
- [x] `StreakFreezeScheduler.java` SQL updated — file not deleted, build stays green
- [x] No new business logic introduced anywhere
- [x] No schedulers deleted
- [x] No Flyway runs against DB
- [x] No commits or pushes

**Potential breakage to flag to user:** The `result.put("streakFreezeBalance", ...)` key change in `RedeemController.java` (Task 7.3) changes the JSON response shape of `POST /api/v1/redeem`. Any frontend code that reads `response.data.streakFreezeBalance` will break. Flag this in the report — frontend update is needed before this goes to production.
