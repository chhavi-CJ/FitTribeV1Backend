# ADR-003: Retirement of the legacy personal_records table

**Status:** Accepted  
**Date:** 2026-04-19  
**Context:** PR System V2 (follows ADR-001 and ADR-002)

## Context

`personal_records` (V22, April 2026) was the original PR storage — one row per (user, exercise), always the best weight, overwriting on improvement. It had no notion of categories, supersession, or coin tracking.

PR System V2 (V44) introduced `pr_events` + `user_exercise_bests` as the authoritative replacement. The new system captures:
- Multiple PR categories per exercise (FIRST_EVER, WEIGHT_PR, REP_PR, VOLUME_PR, MAX_ATTEMPT)
- Edit-time supersession (PR 1.1)
- Coin award/revocation ledger integration
- Multi-signal enrichment (ADR-002 / PR 1.5)

The writer for `personal_records` (`PersonalRecordRepository.upsertPr`) had zero callers at the time of this ADR — confirmed via grep. Four readers remained:
- `AchievementsController` — monthly PR count for profile
- `UserController` — `prsTotal` in profile response
- `WeekDataBuilder` — dead injection (imported but never invoked; javadoc was misleading)
- `AccountPurgeScheduler` — `DELETE` on user purge

Production data at retirement: 50 rows in `personal_records`, all legacy test-user data. No real user history needed preservation.

## Decision

Migrate readers to `pr_events` / `user_exercise_bests` with category-aware semantics, delete the JPA entity and repository, and drop the `personal_records` table in a single atomic PR.

Reader corrections:
- `DISTINCT` where legacy one-row-per-(user, exercise) semantics inherently deduplicated.
- `superseded_at IS NULL` filter so revoked PRs don't count toward achievements.
- `user_exercise_bests.countByUserId` for "X PRs held" profile count — matches legacy "one row per exercise" semantic better than counting `pr_events` rows (which would include superseded history).
- `AccountPurgeScheduler` no longer needs explicit DELETE; pr_events and user_exercise_bests both have `ON DELETE CASCADE` on `user_id`.

Weekly report PR section preserved as-is. The legacy `WeekData.PrEntry` shape and `WeeklyReportComputer.buildPersonalRecordsPayload` continue to work unchanged because they source PR identification from `workout_sessions.exercises.isPr` and previous max from `set_logs`, neither affected by this change.

## Consequences

### Positive
- **Single source of truth.** Only one table defines "what is a PR today."
- **Rich data available downstream.** Readers gain access to signal-level detail via `signals_met` for future weekly report redesigns.
- **Schema hardening.** Drops a table with no writer, preventing accidental references.
- **Cleaner purge cascade.** User deletion relies on CASCADE at the FK level, no manual DELETE maintenance.
- **Javadoc drift corrected.** `WeekDataBuilder` no longer lists `personal_records` as a data source when it wasn't one.

### Negative / Tradeoffs
- **Data loss.** 50 legacy rows dropped. Test-user data only; no real users affected.
- **Weekly report PR section preserved as-is.** Signal-level count display ("3 weight PRs, 7 rep PRs") deferred as a separate design decision requiring frontend coordination.

### Rejected alternatives
- **Backfill personal_records → pr_events before drop:** Rejected. Would produce pr_events rows with NULL session_id, NULL set_id, placeholder detector_version. Second-class data. Not worth it for test data.
- **Dual-read with feature flag:** Rejected. Over-engineering when the new system is already authoritative.
- **Leave the table, just remove the readers:** Rejected. Dead table in the schema contradicts single-source-of-truth.

## Verification

- Post-migration: `SELECT COUNT(*) FROM personal_records` → table-does-not-exist error.
- `AchievementsController.achievements` returns monthly PR count from active `pr_events`.
- `UserController` profile response shows PR count from `user_exercise_bests.countByUserId`.
- `WeekDataBuilder` no longer imports or injects `PersonalRecordRepository`.
- `AccountPurgeScheduler` logs no longer mention `personal_records`.
- `WeeklyReportComputer` continues populating the `personal_records` JSONB column on `weekly_reports` (unchanged; sourced from JSONB `isPr` flag and `set_logs`).

## Related

- ADR-001 / `PR_System_V2_HLD_with_Flowcharts.md` — PR System V2 design
- ADR-002 / PR 1.5 — multi-signal detector enrichment
- PR 1.1 — cascade idempotency
- PR 1.2 — SavedRoutine Hibernate fix
- PR 2 — `pr_events.set_id` NOT NULL (V45)
