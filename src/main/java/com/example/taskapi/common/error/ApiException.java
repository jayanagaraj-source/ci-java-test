package com.example.taskapi.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A client-facing error. The message is safe to return to callers; it must never
 * contain internal details. Rendered as RFC 9457 Problem Details by
 * {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    public record FieldError(String field, String message) {}

    private final HttpStatus status;
    private final String type;
    private final List<FieldError> errors;

    public ApiException(HttpStatus status, String type, String detail, List<FieldError> errors) {
        super(detail, null, false, false);
        this.status = status;
        this.type = type;
        this.errors = List.copyOf(errors);
    }

    public ApiException(HttpStatus status, String type, String detail) {
        this(status, type, detail, List.of());
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", detail);
    }

    public static ApiException conflict(String type, String detail) {
        return new ApiException(HttpStatus.CONFLICT, type, detail);
    }

    public static ApiException unauthorized(String type, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, type, detail);
    }

    public static ApiException preconditionFailed(String detail) {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "precondition-failed", detail);
    }

    public static ApiException tooManyRequests(String detail) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", detail);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation-error",
                "The request contains invalid fields", List.of(new FieldError(field, message)));
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
