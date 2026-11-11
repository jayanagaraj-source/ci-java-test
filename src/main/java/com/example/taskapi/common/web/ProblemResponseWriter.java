package com.example.taskapi.common.web;

import com.example.taskapi.common.error.Problems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Writes Problem Details from servlet filters and security handlers (outside Spring MVC). */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String type,
            String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = Problems.of(status, type, detail, request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
