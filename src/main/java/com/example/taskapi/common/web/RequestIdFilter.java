package com.example.taskapi.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request an id: a well-formed incoming X-Request-Id is reused,
 * anything else is replaced with a fresh UUID. The id is echoed in the response,
 * put in the logging MDC and attached to every Problem Details body. Also writes
 * one structured access-log line per request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{7,63}$");
    private static final Logger accessLog = LoggerFactory.getLogger("access");

    public static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        return id instanceof String s ? s : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            accessLog.atInfo()
                    .addKeyValue("http.method", request.getMethod())
                    .addKeyValue("http.path", request.getRequestURI())
                    .addKeyValue("http.status", response.getStatus())
                    .addKeyValue("duration_ms", (System.nanoTime() - start) / 1_000_000)
                    .log("request completed");
            MDC.remove(MDC_KEY);
        }
    }
}
