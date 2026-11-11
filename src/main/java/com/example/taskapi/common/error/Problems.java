package com.example.taskapi.common.error;

import com.example.taskapi.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Factory for RFC 9457 Problem Details with a consistent shape and a requestId. */
public final class Problems {

    public static final String TYPE_PREFIX = "urn:problem-type:task-api:";

    private Problems() {}

    public static ProblemDetail of(HttpStatusCode status, String type, String detail, HttpServletRequest request) {
        return of(status, type, detail, request, request == null ? null : request.getRequestURI());
    }

    public static ProblemDetail of(
            HttpStatusCode status, String type, String detail, HttpServletRequest request, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title(status));
        if (instance != null) {
            try {
                problem.setInstance(new URI(null, null, instance, null));
            } catch (URISyntaxException ignored) {
                // leave instance unset rather than echo an unparsable path
            }
        }
        if (request != null) {
            problem.setProperty("requestId", RequestIdFilter.requestId(request));
        }
        return problem;
    }

    public static String title(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }

    /** A stable type slug for statuses raised by the framework rather than by our code. */
    public static String typeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "bad-request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 406 -> "not-acceptable";
            case 409 -> "conflict";
            case 412 -> "precondition-failed";
            case 413 -> "payload-too-large";
            case 415 -> "unsupported-media-type";
            case 429 -> "too-many-requests";
            case 503 -> "service-unavailable";
            default -> status.is5xxServerError() ? "internal-error" : "client-error";
        };
    }
}
