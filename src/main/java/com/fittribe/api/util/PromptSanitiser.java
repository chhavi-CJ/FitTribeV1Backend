package com.fittribe.api.util;

/**
 * Sanitises user-provided strings before they are embedded in OpenAI prompts.
 *
 * Rules:
 *  - Null / blank input returns "unknown"
 *  - All control characters (newlines, tabs, carriage returns, etc.) are collapsed to a space
 *  - Characters that could alter prompt structure or inject new instructions are stripped:
 *      backticks ` — markdown code blocks
 *      curly braces { } — collide with placeholder syntax
 *      angle brackets < > — HTML / XML injection
 *      square brackets [ ] — markdown links
 *  - Multiple consecutive spaces are collapsed to one
 *  - Result is trimmed to 50 characters maximum
 *  - If the cleaned result is blank, returns "unknown"
 */
public final class PromptSanitiser {

    private PromptSanitiser() {}

    private static final int MAX_LENGTH = 50;

    public static String sanitise(String input) {
        if (input == null || input.isBlank()) return "unknown";

        String cleaned = input
                .replaceAll("[\\p{Cntrl}]", " ")   // \n \r \t and all other control chars → space
                .replaceAll("[`{}\\[\\]<>]", "")   // strip prompt-structure-altering characters
                .replaceAll("\\s+", " ")            // collapse consecutive spaces
                .trim();

        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).trim();
        }

        return cleaned.isBlank() ? "unknown" : cleaned;
    }
}
