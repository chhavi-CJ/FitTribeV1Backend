package com.fittribe.api.matching;

import java.util.List;

/**
 * Result of a single Conscious Matching batch run.
 *
 * <p>Returned by {@link MatchingService#runBatch()} and exposed by the
 * admin endpoint (Step 4b.ii.C) and logged by the cron.
 *
 * @param groupsFormed   number of groups successfully persisted
 * @param usersMatched   sum of members across all persisted groups
 *                       (each user counted once; should equal
 *                       sum-of-group-sizes)
 * @param usersRemaining users who were in the input pool but did NOT
 *                       end up matched — engine remainder + persistence-
 *                       failure deferrals. Their user_matching_status
 *                       stays QUEUED and they're retried next batch.
 * @param errors         human-readable error lines, one per failed
 *                       persistence attempt. Empty list = clean run.
 *                       Intentionally List&lt;String&gt; not
 *                       List&lt;Exception&gt; — the BatchSummary
 *                       crosses the HTTP boundary in 4b.ii.C and we
 *                       don't want stack traces serialised to admin
 *                       clients.
 */
public record BatchSummary(
        int groupsFormed,
        int usersMatched,
        int usersRemaining,
        List<String> errors
) {
    /** Convenience for "nothing happened — pool was too small". */
    public static BatchSummary empty() {
        return new BatchSummary(0, 0, 0, List.of());
    }
}
