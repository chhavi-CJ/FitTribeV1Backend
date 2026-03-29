package com.fittribe.api.exception;

import com.fittribe.api.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Expected business errors — messages are all hardcoded in ApiException factory methods. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        log.debug("ApiException [{}] {}: {}", ex.getStatus(), ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getCode()));
    }

    /** Bean Validation failures — messages come from annotation definitions, not internal state. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(message, "VALIDATION_ERROR"));
    }

    /**
     * Malformed or unreadable JSON body.
     * ex.getMessage() contains Jackson internal class/path details — never forwarded to client.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Invalid request body.", "BAD_REQUEST"));
    }

    /**
     * Wrong type for a path or query parameter (e.g. non-UUID where UUID is expected).
     * ex.getMessage() contains the parameter name and Java class name — never forwarded to client.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Invalid parameter: " + ex.getName() + ".", "BAD_REQUEST"));
    }

    /** Wrong HTTP method for an existing endpoint (e.g. GET where POST is required). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.debug("Method not supported: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(
                        "Request method '" + ex.getMethod() + "' is not supported.", "METHOD_NOT_ALLOWED"));
    }

    /** No route matched the request path at all. Requires throw-exception-if-no-handler-found=true. */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex) {
        log.debug("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("No endpoint found for "
                        + ex.getHttpMethod() + " " + ex.getRequestURL(), "NOT_FOUND"));
    }

    /**
     * Database constraint violations (unique key, not-null, check constraint, null byte in text).
     * ex.getMessage() contains internal SQL/table detail — never forwarded to client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Request contains invalid data.", "BAD_REQUEST"));
    }

    /**
     * Catch-all for anything not handled above.
     * Full exception is logged server-side only. Client receives a generic message with no
     * internal detail — no stack traces, SQL error text, or file paths are forwarded.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred.", "INTERNAL_ERROR"));
    }
}
