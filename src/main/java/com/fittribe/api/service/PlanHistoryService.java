package com.fittribe.api.service;

import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.util.MuscleGroupUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides recent set history for AI plan generation as {@link HistoricalSet} records.
 *
 * <p>Backed by {@code set_logs} in commit 1. Commit 2 will replace the implementation
 * with a scan of {@code workout_sessions.exercises} JSONB and {@code pr_events},
 * removing the live {@code set_logs} dependency from the plan generation chain.
 */
@Service
public class PlanHistoryService {

    private final WorkoutSessionRepository sessionRepo;
    private final SetLogRepository         setLogRepo;

    public PlanHistoryService(WorkoutSessionRepository sessionRepo,
                               SetLogRepository setLogRepo) {
        this.sessionRepo = sessionRepo;
        this.setLogRepo  = setLogRepo;
    }

    /**
     * Returns all logged sets for a user's completed sessions since the given instant.
     *
     * @param userId the user whose history to load
     * @param since  lower bound for session {@code finishedAt} (inclusive)
     * @param exMap  exercise catalogue keyed by exerciseId — used to resolve
     *               {@code exerciseName} and canonical {@code muscleGroup}
     * @return flat list of {@link HistoricalSet}, one entry per logged set;
     *         empty list when no completed sessions exist in the window
     */
    public List<HistoricalSet> getRecentLoggedSets(UUID userId, Instant since,
                                                    Map<String, Exercise> exMap) {
        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndFinishedAtBetween(userId, "COMPLETED", since, Instant.now());
        if (sessions.isEmpty()) return List.of();

        List<UUID> sessionIds = sessions.stream()
                .map(WorkoutSession::getId)
                .collect(Collectors.toList());

        Map<UUID, Instant> finishedAtById = sessions.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, WorkoutSession::getFinishedAt));

        List<SetLog> setLogs = setLogRepo.findBySessionIdIn(sessionIds);

        return setLogs.stream().map(sl -> {
            Exercise ex = exMap != null ? exMap.get(sl.getExerciseId()) : null;
            String exerciseName = ex != null ? ex.getName() : sl.getExerciseId();
            String muscleGroup  = "";
            if (ex != null && ex.getMuscleGroup() != null) {
                String canonical = MuscleGroupUtil.canonicalize(ex.getMuscleGroup());
                if (!canonical.isEmpty()) muscleGroup = canonical;
            }
            return new HistoricalSet(
                    sl.getSessionId(),
                    finishedAtById.get(sl.getSessionId()),
                    sl.getExerciseId(),
                    exerciseName,
                    muscleGroup,
                    sl.getWeightKg(),
                    sl.getReps(),
                    sl.getSetNumber(),
                    sl.getId(),   // setId maps to set_logs.id per established LoggedSet vocabulary
                    Boolean.TRUE.equals(sl.getIsPr())
            );
        }).collect(Collectors.toList());
    }
}
