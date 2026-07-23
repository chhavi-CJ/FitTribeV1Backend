package com.fittribe.api.service;

import com.fittribe.api.dto.group.LeaderboardEntryDto;
import com.fittribe.api.dto.group.LeaderboardResponseDto;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private final GroupMemberRepository  memberRepo;
    private final UserRepository         userRepo;
    private final EffortScoreCalculator  effortCalc;
    private final MostImprovedCalculator mostImprovedCalc;
    private final GrinderCalculator      grinderCalc;

    public LeaderboardService(GroupMemberRepository memberRepo,
                              UserRepository userRepo,
                              EffortScoreCalculator effortCalc,
                              MostImprovedCalculator mostImprovedCalc,
                              GrinderCalculator grinderCalc) {
        this.memberRepo       = memberRepo;
        this.userRepo         = userRepo;
        this.effortCalc       = effortCalc;
        this.mostImprovedCalc = mostImprovedCalc;
        this.grinderCalc      = grinderCalc;
    }

    public LeaderboardResponseDto getLeaderboard(UUID groupId, String type,
                                                 LocalDate weekStart, UUID currentUserId) {
        List<UUID> memberIds = memberRepo.findUserIdsByGroupId(groupId);
        Map<UUID, User> userMap = userRepo.findByIdIn(memberIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        int isoYear = weekStart.get(IsoFields.WEEK_BASED_YEAR);
        int isoWeek = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        List<LeaderboardEntryDto> entries = switch (type.toLowerCase()) {
            case "effort"      -> buildEffortEntries(memberIds, userMap, weekStart, currentUserId);
            case "improvement" -> buildImprovementEntries(memberIds, userMap, weekStart, currentUserId);
            case "grinder"     -> buildGrinderEntries(memberIds, userMap, currentUserId);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Invalid leaderboard type: " + type + ". Valid values: effort, improvement, grinder");
        };

        return new LeaderboardResponseDto(type, isoYear, isoWeek, entries);
    }

    // ── Effort ───────────────────────────────────────────────────────────────

    private List<LeaderboardEntryDto> buildEffortEntries(List<UUID> memberIds,
                                                         Map<UUID, User> userMap,
                                                         LocalDate weekStart,
                                                         UUID currentUserId) {
        record Scored(UUID userId, int score) {}

        List<Scored> scored = memberIds.stream()
                .map(id -> new Scored(id, effortCalc.calculateEffortScore(id, weekStart)))
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .collect(Collectors.toList());

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        int rank = 1;
        for (Scored s : scored) {
            String displayName = displayName(userMap.get(s.userId()));
            LeaderboardEntryDto e = new LeaderboardEntryDto();
            e.setUserId(s.userId());
            e.setDisplayName(displayName);
            e.setAvatarInitials(avatarInitials(displayName));
            e.setScore(s.score());
            e.setRank(rank++);
            e.setCurrentUser(s.userId().equals(currentUserId));
            e.setStatus(s.score() == 0 ? "INACTIVE" : null);
            e.setDisplaySuffix("pts");
            e.setScoreDisplay("Score " + s.score());
            entries.add(e);
        }
        return entries;
    }

    // ── Improvement ──────────────────────────────────────────────────────────

    private List<LeaderboardEntryDto> buildImprovementEntries(List<UUID> memberIds,
                                                              Map<UUID, User> userMap,
                                                              LocalDate weekStart,
                                                              UUID currentUserId) {
        List<MostImprovedCalculator.MostImprovedResult> all = memberIds.stream()
                .map(id -> mostImprovedCalc.calculate(id, weekStart))
                .collect(Collectors.toList());

        // RANKED entries — sorted DESC by internalPct, ties broken by displayName ASC
        List<MostImprovedCalculator.MostImprovedResult> ranked = all.stream()
                .filter(r -> r.status == MostImprovedCalculator.MostImprovedStatus.RANKED)
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.internalPct, a.internalPct);
                    if (cmp != 0) return cmp;
                    return displayName(userMap.get(a.userId))
                            .compareToIgnoreCase(displayName(userMap.get(b.userId)));
                })
                .collect(Collectors.toList());

        // Unranked-but-visible entries (NEW_MEMBER / GOAL_NOT_HIT) — sorted alphabetically
        List<MostImprovedCalculator.MostImprovedResult> unranked = all.stream()
                .filter(r -> r.status == MostImprovedCalculator.MostImprovedStatus.NEW_MEMBER
                          || r.status == MostImprovedCalculator.MostImprovedStatus.GOAL_NOT_HIT)
                .sorted((a, b) -> displayName(userMap.get(a.userId))
                        .compareToIgnoreCase(displayName(userMap.get(b.userId))))
                .collect(Collectors.toList());

        // ROUTE_TO_COMEBACK → Comeback Spotlight (not on this board)
        // NOT_ELIGIBLE → excluded entirely

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        int rankCounter = 1;

        for (MostImprovedCalculator.MostImprovedResult r : ranked) {
            String displayName = displayName(userMap.get(r.userId));
            String pctStr = r.isOverCap ? "200%+" : r.displayPct + "%";

            LeaderboardEntryDto e = new LeaderboardEntryDto();
            e.setUserId(r.userId);
            e.setDisplayName(displayName);
            e.setAvatarInitials(avatarInitials(displayName));
            e.setScore(r.displayPct);
            e.setRank(rankCounter++);
            e.setCurrentUser(r.userId.equals(currentUserId));
            e.setStatus(null); // spec: null = normal ranking
            e.setDisplaySuffix(pctStr);
            e.setScoreDisplay(pctStr);
            entries.add(e);
        }

        for (MostImprovedCalculator.MostImprovedResult r : unranked) {
            String displayName  = displayName(userMap.get(r.userId));
            boolean isNewMember = r.status == MostImprovedCalculator.MostImprovedStatus.NEW_MEMBER;
            String label = isNewMember ? "Join next week" : "Hit goal to be ranked";

            LeaderboardEntryDto e = new LeaderboardEntryDto();
            e.setUserId(r.userId);
            e.setDisplayName(displayName);
            e.setAvatarInitials(avatarInitials(displayName));
            e.setScore(null);
            e.setRank(null);
            e.setCurrentUser(r.userId.equals(currentUserId));
            e.setStatus(r.status.name()); // "NEW_MEMBER" or "GOAL_NOT_HIT"
            e.setDisplaySuffix(label);
            e.setScoreDisplay(label);
            entries.add(e);
        }

        return entries;
    }

    // ── Grinder ──────────────────────────────────────────────────────────────

    private List<LeaderboardEntryDto> buildGrinderEntries(List<UUID> memberIds,
                                                          Map<UUID, User> userMap,
                                                          UUID currentUserId) {
        List<GrinderCalculator.GrinderResult> results = memberIds.stream()
                .map(grinderCalc::compute)
                .collect(Collectors.toList());

        results.sort((a, b) -> {
            int sc = Integer.compare(b.totalSessions60Days, a.totalSessions60Days);
            if (sc != 0) return sc;
            return b.totalVolume60Days.compareTo(a.totalVolume60Days);
        });

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        int rank = 1;
        for (GrinderCalculator.GrinderResult r : results) {
            String displayName = displayName(userMap.get(r.userId));
            String sessionWord = r.totalSessions60Days == 1 ? " session" : " sessions";
            String volFormatted = formatVolume(r.totalVolume60Days);

            LeaderboardEntryDto e = new LeaderboardEntryDto();
            e.setUserId(r.userId);
            e.setDisplayName(displayName);
            e.setAvatarInitials(avatarInitials(displayName));
            e.setScore(r.totalSessions60Days);
            e.setRank(rank++);
            e.setCurrentUser(r.userId.equals(currentUserId));
            e.setStatus(r.totalSessions60Days == 0 ? "INACTIVE" : null);
            e.setDisplaySuffix(r.totalSessions60Days + sessionWord);
            e.setScoreDisplay(r.totalSessions60Days + sessionWord + " · " + volFormatted + " kg");
            entries.add(e);
        }
        return entries;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    static String avatarInitials(String displayName) {
        if (displayName == null || displayName.isBlank()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private static String displayName(User user) {
        return user != null && user.getDisplayName() != null ? user.getDisplayName() : "Unknown";
    }

    private static String formatVolume(BigDecimal vol) {
        if (vol == null) return "0";
        long rounded = vol.setScale(0, RoundingMode.HALF_UP).longValue();
        return rounded >= 1_000
                ? String.format(Locale.ENGLISH, "%,d", rounded)
                : String.valueOf(rounded);
    }
}
