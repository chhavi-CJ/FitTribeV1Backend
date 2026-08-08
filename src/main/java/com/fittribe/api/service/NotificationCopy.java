package com.fittribe.api.service;

import java.util.List;
import java.util.Random;

public final class NotificationCopy {

    private NotificationCopy() {}

    private static final Random RNG = new Random();

    public record Copy(String title, String body) {}

    private static Copy pick(List<Copy> options) {
        return options.get(RNG.nextInt(options.size()));
    }

    public static Copy streakRisk(int streak) {
        return pick(List.of(
            new Copy("Your streak is giving you side-eye 👀",
                     streak + " days of showing up. Don’t ghost the gym now."),
            new Copy("The gym misses you today 🥺",
                     streak + " day streak on the line. One session. That’s all."),
            new Copy("Plot twist: you skip today? 😬",
                     "Your " + streak + " day streak doesn’t approve this message."),
            new Copy("Bhai, gym ka kya plan hai? 🏋️",
                     streak + " days strong. Don’t let today be the villain.")
        ));
    }

    public static Copy streakMilestone(int streak) {
        if (streak == 5) return new Copy(
            "5 days in! You’re not a beginner anymore 🔥",
            "Most people quit by day 3. You didn’t.");
        if (streak == 10) return new Copy(
            "Double digits! 🔟🔥",
            "10 days of showing up. The gym knows your name now.");
        if (streak == 30) return new Copy(
            "30 days?! That’s a whole habit 💪",
            "Science says 21 days makes a habit. You did 30.");
        if (streak == 50) return new Copy(
            "50 days. You’re basically a gym uncle now 🫡",
            "Half a century of not skipping. Respect.");
        if (streak == 100) return new Copy(
            "💯 DAYS. That’s not a streak, that’s a lifestyle.",
            "100 days of discipline. Take a bow.");
        if (streak == 365) return new Copy(
            "365 days. One full year. 👑",
            "You trained every single day for a year. Legend.");
        return new Copy("🔥 " + streak + " day streak!",
            "You’ve trained " + streak + " days in a row. Keep going!");
    }

    public static Copy streakBroken(int previousStreak) {
        return pick(List.of(
            new Copy("RIP streak 💀",
                     "Your " + previousStreak + " day streak just flatlined. New one starts today?"),
            new Copy("Streak said bye-bye 👋",
                     previousStreak + " days gone. But comebacks > streaks."),
            new Copy("Well... that happened 😅",
                     "The streak’s gone. The gains aren’t. Show up today.")
        ));
    }

    public static Copy streakFreezeUsed(int streak, int remaining) {
        return pick(List.of(
            new Copy("Freeze to the rescue! ❄️",
                     "Your " + streak + " day streak lives another day. " + remaining
                             + " freezes left — use them wisely."),
            new Copy("Close call 😮‍💨",
                     "A freeze just saved your " + streak + " day streak. You’re welcome."),
            new Copy("Streak: saved. You: lucky. ❄️",
                     remaining + " freezes remaining. The gym’s waiting tomorrow.")
        ));
    }

    public static Copy weeklyGoalHit(int weeklyGoal) {
        return pick(List.of(
            new Copy("Goal smashed! You’re a menace 🎯",
                     weeklyGoal + " sessions done this week. Consistency looks good on you."),
            new Copy("Weekly goal? ✅ Done and dusted.",
                     "You said " + weeklyGoal + " sessions. You did " + weeklyGoal
                             + " sessions. Respect."),
            new Copy("This week’s boss? You. 💪",
                     weeklyGoal + "/" + weeklyGoal
                             + " — that’s what showing up looks like.")
        ));
    }

    public static Copy groupGoalHit(String groupName) {
        return pick(List.of(
            new Copy("🏆 " + groupName + " = UNSTOPPABLE",
                     "Everyone hit their goal. This group is actually built different."),
            new Copy("Full squad showed up! 🔥",
                     groupName + " — every single member crushed their weekly goal."),
            new Copy("Group goals > solo goals 🤝",
                     groupName + " just proved accountability works. All goals hit.")
        ));
    }

    public static Copy groupMemberLogged(String memberName, String groupName) {
        return pick(List.of(
            new Copy("💪 " + memberName + " just trained",
                     memberName + " logged a workout in " + groupName + ". Your move."),
            new Copy("👀 " + memberName + " showed up",
                     memberName + " just hit the gym. Feeling inspired yet?"),
            new Copy(memberName + " is putting in the work 🏋️",
                     "Another session logged in " + groupName + ". What about you?")
        ));
    }

    public static Copy groupMemberJoined(String memberName, String groupName) {
        return pick(List.of(
            new Copy("👋 New challenger has entered!",
                     memberName + " just joined " + groupName + ". The crew grows stronger."),
            new Copy("Squad update: +1 member 🔥",
                     memberName + " is now part of " + groupName + ". Welcome to the grind.")
        ));
    }

    public static Copy groupTrainedWithoutYou(String groupName, int count) {
        return pick(List.of(
            new Copy("Your group trained without you 😶",
                     count + " members of " + groupName + " hit the gym today. Just saying."),
            new Copy("FOMO alert 🚨",
                     groupName + " is getting gains without you. " + count
                             + " sessions logged today."),
            new Copy(groupName + " didn’t wait for you 🏃",
                     count + " of your crew showed up. Tomorrow’s your turn?")
        ));
    }

    public static Copy weeklyReportReady() {
        return pick(List.of(
            new Copy("📊 Your week, reviewed.",
                     "Sessions, streaks, progress — it’s all in your weekly report."),
            new Copy("Report card’s out! 📋",
                     "Let’s see how this week went. No judgment. Maybe some."),
            new Copy("Your weekly report just dropped 🔥",
                     "The numbers don’t lie. Go check how you did.")
        ));
    }

    public static Copy comebackNudge(int daysSince) {
        return pick(List.of(
            new Copy("Remember the gym? It remembers you 🫠",
                     "It’s been " + daysSince + " days. Even a 20-min session counts."),
            new Copy("The weights are gathering dust 🕸️",
                     daysSince + " days since your last workout. Quick session today?"),
            new Copy("Your dumbbells filed a missing report 🔍",
                     "Last seen: " + daysSince + " days ago. Time to reunite."),
            new Copy("Bro? You alive? 👀",
                     daysSince + " days of radio silence. The gym’s not going anywhere,"
                             + " but your gains might.")
        ));
    }

    public static Copy poke(String pokerName, String groupName, int sessionsRemaining) {
        return new Copy(
            pokerName + " poked you 👈",
            pokerName + " poked you in " + groupName
                + " — " + sessionsRemaining
                + " session" + (sessionsRemaining != 1 ? "s" : "") + " to go 💪"
        );
    }

    public static String reactionPush(String reactionType) {
        return switch (reactionType) {
            case "STRONG" -> "💪";
            case "RESPECT" -> "🫡";
            case "KEEP_GOING" -> "🔥";
            case "COMMENDABLE" -> "👏";
            default -> "❤️";
        };
    }
}
