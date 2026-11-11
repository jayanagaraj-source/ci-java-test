package com.example.taskapi.common.web;

import com.example.taskapi.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per client IP for the unauthenticated auth endpoints
 * (register / login), to slow down credential stuffing and account spam.
 *
 * Runs after the security filter chain so 429 responses still carry CORS headers.
 * State is in-memory, i.e. per instance. Behind a reverse proxy, enable
 * server.forward-headers-strategy so getRemoteAddr() is the real client.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");

    private record Window(long startMillis, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ProblemResponseWriter problems;

    public AuthRateLimitFilter(AppProperties properties, Clock clock, ProblemResponseWriter problems) {
        this.limit = properties.rateLimit().authRequests();
        this.windowMillis = properties.rateLimit().authWindow().toMillis();
        this.clock = clock;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        Window window = windows.compute(request.getRemoteAddr(), (key, current) ->
                current == null || now - current.startMillis() >= windowMillis
                        ? new Window(now, 1)
                        : new Window(current.startMillis(), current.count() + 1));

        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(Math.max(0, limit - window.count())));
        if (window.count() > limit) {
            long retryAfterSeconds = Math.max(1, (window.startMillis() + windowMillis - now + 999) / 1000);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
                    "Too many authentication attempts; try again later");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Drops expired windows so the map does not grow without bound. */
    @Scheduled(fixedRate = 60_000)
    void evictExpired() {
        long now = clock.millis();
        windows.values().removeIf(w -> now - w.startMillis() >= windowMillis);
    }
}
