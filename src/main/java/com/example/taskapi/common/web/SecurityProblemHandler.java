package com.example.taskapi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 401/403 responses from Spring Security, rendered as Problem Details. */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    public SecurityProblemHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean invalidToken = ex instanceof InvalidBearerTokenException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized",
                invalidToken ? "The access token is invalid or has expired"
                        : "Authentication is required to access this resource");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writer.write(request, response, HttpStatus.FORBIDDEN, "forbidden",
                "You do not have permission to access this resource");
    }
}
