package com.example.taskapi.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application settings, bound from {@code app.*} which in turn is fed by
 * environment variables (see application.yml and .env.example).
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Http http,
        @Valid @NotNull Sse sse,
        @Valid @NotNull Security security) {

    /**
     * The secret is validated in {@code JwtConfig} rather than with Bean
     * Validation so that a rejected value is never echoed into startup logs.
     */
    public record Jwt(String secret, @NotBlank String issuer, @NotNull Duration ttl) {
        @Override
        public String toString() {
            return "Jwt[secret=<redacted>, issuer=" + issuer + ", ttl=" + ttl + "]";
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public List<String> origins() {
            return allowedOrigins == null
                    ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record RateLimit(@Min(1) int authRequests, @NotNull Duration authWindow) {}

    public record Http(@NotNull DataSize maxRequestBodySize) {}

    public record Sse(@NotNull Duration timeout, @NotNull Duration heartbeat, @Min(1) int maxConnectionsPerUser) {}

    public record Security(@Min(4) @Max(31) int bcryptStrength) {}
}
