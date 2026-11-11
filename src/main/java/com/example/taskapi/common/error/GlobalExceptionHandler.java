package com.example.taskapi.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception to RFC 9457 Problem Details. Client errors carry a safe,
 * hand-written detail; anything unexpected becomes a generic 500 and the real
 * cause is only logged (correlated by requestId), never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = Problems.of(ex.status(), ex.type(), ex.getMessage(), request);
        if (!ex.errors().isEmpty()) {
            problem.setProperty("errors", ex.errors());
        }
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification",
                "The resource was modified concurrently; fetch it again and retry", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "conflict", "The request conflicts with existing data", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiException.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiException.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validationProblem(errors, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.add(new ApiException.FieldError(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.add(new ApiException.FieldError(e.getObjectName(), e.getDefaultMessage())));
        return validationProblem(errors, servletRequest(request));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiException.FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return validationProblem(errors, servletRequest(request));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpServletRequest servletRequest = servletRequest(request);
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PayloadTooLargeException) {
                return problem(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large",
                        "Request body exceeds the configured limit", servletRequest);
            }
        }
        if (ex.getCause() instanceof UnrecognizedPropertyException unknown) {
            String field = jsonPath(unknown);
            return validationProblem(
                    List.of(new ApiException.FieldError(field, "unknown field")), servletRequest);
        }
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            return validationProblem(
                    List.of(new ApiException.FieldError(jsonPath(mismatch), "invalid value or type")), servletRequest);
        }
        return problem(HttpStatus.BAD_REQUEST, "malformed-request",
                "Request body is missing or is not valid JSON", servletRequest);
    }

    /** Catch-all for the framework exceptions handled by the superclass. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (request instanceof ServletWebRequest swr && swr.getResponse() != null && swr.getResponse().isCommitted()) {
            return null;
        }
        String detail;
        if (statusCode.is5xxServerError()) {
            log.error("Request failed", ex);
            detail = "An unexpected error occurred";
        } else if (ex instanceof NoResourceFoundException) {
            detail = "The requested resource was not found";
        } else if (body instanceof ProblemDetail pd && pd.getDetail() != null) {
            detail = pd.getDetail(); // framework 4xx details are written for clients
        } else {
            detail = Problems.title(statusCode);
        }
        ProblemDetail problem = Problems.of(statusCode, Problems.typeFor(statusCode), detail, servletRequest(request));
        return new ResponseEntity<>(problem, headers, statusCode);
    }

    private ResponseEntity<Object> validationProblem(List<ApiException.FieldError> errors, HttpServletRequest request) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "validation-error",
                "The request contains invalid fields", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ResponseEntity<Object> problem(
            HttpStatus status, String type, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Problems.of(status, type, detail, request));
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest() : null;
    }

    private static String jsonPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
