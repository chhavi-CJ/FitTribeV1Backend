package com.fittribe.api.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    // ── Named factory methods for every documented error code ──────────

    public static ApiException sessionTooSoon() {
        return new ApiException(HttpStatus.CONFLICT, "SESSION_TOO_SOON",
                "A workout was already logged within the last 8 hours.");
    }

    public static ApiException needsDisplayName() {
        return new ApiException(HttpStatus.ACCEPTED, "NEEDS_DISPLAY_NAME",
                "Account created. Please provide a display name to continue.");
    }

    public static ApiException alreadyMember() {
        return new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                "You are already a member of this group.");
    }

    public static ApiException insufficientCoins() {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_COINS",
                "You do not have enough FitCoins for this action.");
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                resource + " not found.");
    }

    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required.");
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.");
    }
}
