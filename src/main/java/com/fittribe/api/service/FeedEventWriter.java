package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.FeedItem;
import com.fittribe.api.entity.GroupWeeklyCard;
import com.fittribe.api.entity.GroupWeeklyTopPerformer;
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.FeedItemRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FeedEventWriter {

    private static final Logger log = LoggerFactory.getLogger(FeedEventWriter.class);

    private final FeedItemRepository    feedRepo;
    private final GroupMemberRepository memberRepo;
    private final UserRepository        userRepo;
    private final ObjectMapper          mapper;

    public FeedEventWriter(FeedItemRepository feedRepo,
                           GroupMemberRepository memberRepo,
                           UserRepository userRepo,
                           ObjectMapper mapper) {
        this.feedRepo   = feedRepo;
        this.memberRepo = memberRepo;
        this.userRepo   = userRepo;
        this.mapper     = mapper;
    }

    // ── WORKOUT_FINISHED ─────────────────────────────────────────────────────

    // TODO: Idempotency. Currently relies on SessionController's
    // COMPLETED-status guard. If processSessionFinish becomes reachable
    // from new call paths (admin tools, replay jobs), add a partial
    // unique index on feed_items:
    // (group_id, type, (event_data->>'sessionId'))
    // WHERE type IN ('WORKOUT_FINISHED','PR_DETECTED').
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeWorkoutFinished(WorkoutSession session,
                                     List<String> muscleGroups,
                                     BigDecimal topLiftKg) {
        try {
            String name = firstName(session.getUserId());
            String body = buildWorkoutBody(name, session.getDurationMins(), session.getTotalVolumeKg());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId",     session.getId());
            data.put("durationMins",  session.getDurationMins());
            data.put("totalVolumeKg", session.getTotalVolumeKg());
            data.put("muscleGroups",  muscleGroups != null ? muscleGroups : List.of());
            data.put("topLiftKg",     topLiftKg);
            data.put("sets",          session.getTotalSets());
            data.put("streak",        session.getStreak());
            data.put("workoutType",   session.getName());

            writeFeedToAllGroups(session.getUserId(), "WORKOUT_FINISHED", body, data);
        } catch (Exception e) {
            log.warn("writeWorkoutFinished failed for session={}: {}", session.getId(), e.getMessage());
        }
    }

    // ── PR_DETECTED ──────────────────────────────────────────────────────────

    // TODO: Idempotency. Currently relies on SessionController's
    // COMPLETED-status guard. If processSessionFinish becomes reachable
    // from new call paths (admin tools, replay jobs), add a partial
    // unique index on feed_items:
    // (group_id, type, (event_data->>'sessionId'))
    // WHERE type IN ('WORKOUT_FINISHED','PR_DETECTED').
    /**
     * @param exerciseNames map of exerciseId → display name, used to populate lifts[].exerciseName
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writePrRoundup(UUID userId, UUID sessionId,
                               List<PrEvent> prs,
                               Map<String, String> exerciseNames) {
        try {
            if (prs == null || prs.isEmpty()) return;

            String name  = firstName(userId);
            int    count = prs.size();
            String body;
            if (count == 1) {
                PrEvent    first     = prs.get(0);
                String     exName    = exerciseNames.getOrDefault(first.getExerciseId(), first.getExerciseId());
                @SuppressWarnings("unchecked")
                Map<String, Object> newBest1 = (Map<String, Object>) first.getValuePayload().get("new_best");
                Object     rawKg     = newBest1 != null ? newBest1.get("weight_kg") : null;
                String     kgStr;
                if (rawKg != null) {
                    BigDecimal bd       = new BigDecimal(String.valueOf(rawKg));
                    BigDecimal stripped = bd.stripTrailingZeros();
                    kgStr = stripped.scale() < 0
                            ? stripped.setScale(0).toPlainString()
                            : stripped.toPlainString();
                    body = name + " hit a " + exName + " PR — " + kgStr + " kg";
                } else {
                    body = name + " hit a " + exName + " PR";
                }
            } else {
                body = name + " hit " + count + " PRs in their session";
            }

            List<Map<String, Object>> lifts = prs.stream().map(pr -> {
                Map<String, Object> lift = new LinkedHashMap<>();
                lift.put("exerciseName", exerciseNames.getOrDefault(pr.getExerciseId(), pr.getExerciseId()));
                lift.put("prCategory",   pr.getPrCategory());
                Map<String, Object> vp = pr.getValuePayload();
                @SuppressWarnings("unchecked")
                Map<String, Object> newBest = (Map<String, Object>) vp.get("new_best");
                lift.put("deltaKg",     vp.get("delta_kg"));
                lift.put("newBestKg",   newBest != null ? newBest.get("weight_kg") : null);
                lift.put("newBestReps", newBest != null ? newBest.get("reps")      : null);
                return lift;
            }).collect(Collectors.toList());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", sessionId);
            data.put("prCount",   count);
            data.put("lifts",     lifts);

            writeFeedToAllGroups(userId, "PR_DETECTED", body, data);
        } catch (Exception e) {
            log.warn("writePrRoundup failed for session={}: {}", sessionId, e.getMessage());
        }
    }

    // ── TIER_LOCKED ──────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeTierLocked(GroupWeeklyCard card) {
        try {
            String tierTitle = toTitleCase(card.getFinalTier());
            String emoji     = tierEmoji(card.getFinalTier());
            String body = "Group hit " + tierTitle + " tier — "
                    + card.getSessionsLogged() + "/" + card.getTargetSessions()
                    + " sessions " + emoji;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tier",           card.getFinalTier());
            data.put("sessionsLogged", card.getSessionsLogged());
            data.put("targetSessions", card.getTargetSessions());
            data.put("overachiever",   card.isOverachiever());
            data.put("streakAtLock",   card.getStreakAtLock());

            FeedItem fi = buildFeedItem(card.getGroupId(), null, "TIER_LOCKED", body, data);
            feedRepo.save(fi);
        } catch (Exception e) {
            log.warn("writeTierLocked failed for group={}: {}", card.getGroupId(), e.getMessage());
        }
    }

    // ── TOP_PERFORMER_CROWNED ────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeTopPerformerCrowned(GroupWeeklyTopPerformer tp) {
        try {
            String winnerName = firstName(tp.getWinnerUserId());
            String body = winnerName + " was top performer — "
                    + dimensionToTitle(tp.getDimension()) + " · " + tp.getScoreValue() + " pts";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("winnerUserId", tp.getWinnerUserId());
            data.put("dimension",    tp.getDimension());
            data.put("scoreValue",   tp.getScoreValue());
            data.put("metricLabel",  tp.getMetricLabel());

            FeedItem fi = buildFeedItem(tp.getGroupId(), tp.getWinnerUserId(), "TOP_PERFORMER_CROWNED", body, data);
            feedRepo.save(fi);
        } catch (Exception e) {
            log.warn("writeTopPerformerCrowned failed for group={}: {}", tp.getGroupId(), e.getMessage());
        }
    }

    // ── STREAK_MILESTONE ─────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeStreakMilestone(UUID userId, int streakDays, int coinsEarned) {
        try {
            String name = firstName(userId);
            String body = name + " hit a " + streakDays + "-day streak 🎉";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("streak",      streakDays);
            data.put("coinsEarned", coinsEarned);

            writeFeedToAllGroups(userId, "STREAK_MILESTONE", body, data);
        } catch (Exception e) {
            log.warn("writeStreakMilestone failed for user={}: {}", userId, e.getMessage());
        }
    }

    // ── STATUS_CHANGED ───────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeStatusChanged(UUID userId, LocalDate date,
                                   String newStatus, String previousStatus) {
        try {
            String name = firstName(userId);
            String body = buildStatusBody(name, newStatus);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("date",           date.toString());
            data.put("newStatus",      newStatus);
            data.put("previousStatus", previousStatus);

            writeFeedToAllGroups(userId, "STATUS_CHANGED", body, data);
        } catch (Exception e) {
            log.warn("writeStatusChanged failed for user={}: {}", userId, e.getMessage());
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void writeFeedToAllGroups(UUID userId, String type, String body, Map<String, Object> data) {
        String json = toJson(data);
        List<UUID> groupIds = memberRepo.findByUserId(userId).stream()
                .map(com.fittribe.api.entity.GroupMember::getGroupId)
                .collect(Collectors.toList());
        for (UUID groupId : groupIds) {
            FeedItem fi = new FeedItem();
            fi.setGroupId(groupId);
            fi.setUserId(userId);
            fi.setType(type);
            fi.setBody(body);
            fi.setEventData(json);
            feedRepo.save(fi);
        }
    }

    private FeedItem buildFeedItem(UUID groupId, UUID userId, String type,
                                   String body, Map<String, Object> data) {
        FeedItem fi = new FeedItem();
        fi.setGroupId(groupId);
        fi.setUserId(userId);
        fi.setType(type);
        fi.setBody(body);
        fi.setEventData(toJson(data));
        return fi;
    }

    private String firstName(UUID userId) {
        String full = userRepo.findById(userId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : "Someone")
                .orElse("Someone");
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    private static String buildWorkoutBody(String firstName, Integer durationMins, BigDecimal totalVolumeKg) {
        StringBuilder sb = new StringBuilder(firstName).append(" finished a workout");
        if (durationMins != null)  sb.append(" · ").append(durationMins).append(" min");
        if (totalVolumeKg != null) sb.append(" · ").append(formatVolumeShort(totalVolumeKg)).append(" vol");
        return sb.toString();
    }

    private static String buildStatusBody(String firstName, String status) {
        return switch (status) {
            case "REST"       -> firstName + " set today as a rest day";
            case "TRAVELLING" -> firstName + " set status to Travelling ✈";
            case "SICK"       -> firstName + " set status to Sick 🤒";
            case "BUSY"       -> firstName + " set status to Busy";
            default           -> firstName + " updated their status";
        };
    }

    private static String formatVolumeShort(BigDecimal vol) {
        if (vol == null) return "0";
        long v = vol.setScale(0, RoundingMode.HALF_UP).longValue();
        if (v >= 1000) {
            long rem = v % 1000;
            return rem == 0
                    ? (v / 1000) + "k"
                    : String.format("%.1fk", v / 1000.0);
        }
        return String.valueOf(v);
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String dimensionToTitle(String dimension) {
        return switch (dimension != null ? dimension.toUpperCase() : "") {
            case "MOST_IMPROVED" -> "Most Improved";
            case "GRINDER"       -> "Grinder";
            default              -> "Effort";
        };
    }

    private static String tierEmoji(String tier) {
        return switch (tier != null ? tier.toUpperCase() : "") {
            case "GOLD"   -> "🥇";
            case "SILVER" -> "🥈";
            case "BRONZE" -> "🥉";
            default       -> "";
        };
    }

    private String toJson(Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("FeedEventWriter: failed to serialize event_data: {}", e.getMessage());
            return "{}";
        }
    }
}
