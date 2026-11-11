package com.example.taskapi.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default /error page so that errors raised outside of
 * Spring MVC (container errors, filters) are also Problem Details without
 * internal messages.
 */
@RestController
class ProblemErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer i && HttpStatus.resolve(i) != null
                ? HttpStatus.valueOf(i)
                : HttpStatus.INTERNAL_SERVER_ERROR;
        String detail = status.is5xxServerError() ? "An unexpected error occurred" : status.getReasonPhrase();
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        ProblemDetail problem = Problems.of(status, Problems.typeFor(status), detail, request,
                uri instanceof String s ? s : null);
        return ResponseEntity.status(status).body(problem);
    }
}
