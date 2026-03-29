package com.fittribe.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Universal response envelope.
 *
 * Success: { "data": { ... }, "error": null }
 * Error:   { "data": null,    "error": { "message": "...", "code": "..." } }
 * Error with extra field (SESSION_TOO_SOON):
 *          { "data": null,    "error": { "message": "...", "code": "...", "unlocksAt": "..." } }
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final T data;
    private final ErrorDetail error;

    private ApiResponse(T data, ErrorDetail error) {
        this.data = data;
        this.error = error;
    }

    public T getData() { return data; }
    public ErrorDetail getError() { return error; }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static ApiResponse<Void> error(String message, String code) {
        return new ApiResponse<>(null, new ErrorDetail(message, code, null));
    }

    public static ApiResponse<Void> errorWithMeta(String message, String code, String unlocksAt) {
        return new ApiResponse<>(null, new ErrorDetail(message, code, unlocksAt));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class ErrorDetail {
        private final String message;
        private final String code;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final String unlocksAt;

        public ErrorDetail(String message, String code, String unlocksAt) {
            this.message = message;
            this.code = code;
            this.unlocksAt = unlocksAt;
        }

        public String getMessage()  { return message; }
        public String getCode()     { return code; }
        public String getUnlocksAt(){ return unlocksAt; }
    }
}
