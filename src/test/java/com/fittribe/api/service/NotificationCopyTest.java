package com.fittribe.api.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotificationCopyTest {

    // ── Non-null + non-empty for every method ────────────────────────────────

    @Test
    void streakRisk_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.streakRisk(5);
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void streakBroken_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.streakBroken(14);
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void streakFreezeUsed_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.streakFreezeUsed(7, 2);
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void weeklyGoalHit_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.weeklyGoalHit(4);
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void groupGoalHit_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.groupGoalHit("Mumbai Crew");
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void groupMemberLogged_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.groupMemberLogged("Riya", "Mumbai Crew");
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void groupMemberJoined_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.groupMemberJoined("Priya", "Delhi Gym");
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void groupTrainedWithoutYou_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.groupTrainedWithoutYou("Squad", 3);
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void weeklyReportReady_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.weeklyReportReady();
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void comebackNudge_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.comebackNudge(5);
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    @Test
    void poke_returnsNonEmptyCopy() {
        NotificationCopy.Copy c = NotificationCopy.poke("Arjun", "Crew", 2);
        assertNotNull(c);
        assertFalse(c.title().isBlank());
        assertFalse(c.body().isBlank());
    }

    // ── streakMilestone: specific copy per milestone ─────────────────────────

    @Test
    void streakMilestone_5_hasDistinctTitle() {
        String title = NotificationCopy.streakMilestone(5).title();
        assertTrue(title.contains("5"), "Expected streak 5 title to mention '5': " + title);
    }

    @Test
    void streakMilestone_10_returnsDoubledigitsCopy() {
        String title = NotificationCopy.streakMilestone(10).title();
        // "Double digits!" — doesn't contain "10" but that's intentional quirky copy
        assertFalse(title.isBlank());
    }

    @Test
    void streakMilestone_30_hasDistinctTitle() {
        String title = NotificationCopy.streakMilestone(30).title();
        assertTrue(title.contains("30"), "Expected streak 30 title to mention '30': " + title);
    }

    @Test
    void streakMilestone_50_hasDistinctTitle() {
        String title = NotificationCopy.streakMilestone(50).title();
        assertTrue(title.contains("50"), "Expected streak 50 title to mention '50': " + title);
    }

    @Test
    void streakMilestone_100_returnsSpecialCopy() {
        NotificationCopy.Copy c = NotificationCopy.streakMilestone(100);
        // "💯 DAYS" — body mentions 100
        assertTrue(c.body().contains("100"),
                "Expected streak 100 body to mention '100': " + c.body());
    }

    @Test
    void streakMilestone_365_hasDistinctTitle() {
        String title = NotificationCopy.streakMilestone(365).title();
        assertTrue(title.contains("365"), "Expected streak 365 title to mention '365': " + title);
    }

    @Test
    void streakMilestone_nonMilestone_returnsFallback() {
        NotificationCopy.Copy c = NotificationCopy.streakMilestone(7);
        assertTrue(c.title().contains("7"),
                "Fallback title should mention the streak count: " + c.title());
    }

    // ── poke: deterministic (not random) ────────────────────────────────────

    @Test
    void poke_titleContainsPokerName() {
        NotificationCopy.Copy c = NotificationCopy.poke("Virat", "Crew", 2);
        assertTrue(c.title().contains("Virat"), "Poke title must include poker name: " + c.title());
    }

    @Test
    void poke_bodyContainsGroupNameAndSessions() {
        NotificationCopy.Copy c = NotificationCopy.poke("Virat", "Mumbai Squad", 3);
        assertTrue(c.body().contains("Mumbai Squad"),
                "Poke body must include group name: " + c.body());
        assertTrue(c.body().contains("3"),
                "Poke body must include sessions remaining: " + c.body());
    }

    @Test
    void poke_pluralisesSessions() {
        assertTrue(NotificationCopy.poke("X", "G", 1).body().contains("1 session "));
        assertTrue(NotificationCopy.poke("X", "G", 3).body().contains("3 sessions"));
    }

    // ── Randomised methods return varied results ──────────────────────────────

    @Test
    void streakRisk_producesMultipleVariants() {
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            titles.add(NotificationCopy.streakRisk(5).title());
        }
        assertTrue(titles.size() > 1,
                "Expected multiple distinct streakRisk titles over 40 calls, got: " + titles);
    }

    @Test
    void weeklyGoalHit_producesMultipleVariants() {
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            titles.add(NotificationCopy.weeklyGoalHit(4).title());
        }
        assertTrue(titles.size() > 1,
                "Expected multiple distinct weeklyGoalHit titles over 40 calls, got: " + titles);
    }

    @Test
    void comebackNudge_producesMultipleVariants() {
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            titles.add(NotificationCopy.comebackNudge(3).title());
        }
        assertTrue(titles.size() > 1,
                "Expected multiple distinct comebackNudge titles over 40 calls, got: " + titles);
    }

    @Test
    void groupMemberLogged_producesMultipleVariants() {
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            titles.add(NotificationCopy.groupMemberLogged("Riya", "Crew").title());
        }
        assertTrue(titles.size() > 1,
                "Expected multiple distinct groupMemberLogged titles over 40 calls, got: " + titles);
    }

    // ── groupMemberLogged: memberName always in title ─────────────────────────

    @Test
    void groupMemberLogged_memberNameAlwaysInTitle() {
        for (int i = 0; i < 20; i++) {
            String title = NotificationCopy.groupMemberLogged("Arjun", "Squad").title();
            assertTrue(title.contains("Arjun"),
                    "All groupMemberLogged title variants must include memberName: " + title);
        }
    }
}
