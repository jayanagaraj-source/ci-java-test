package com.example.taskapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class JwtSecretStartupTest {

    @Test
    void startupFailsWhenJwtSecretIsMissing() {
        assertThatThrownBy(() -> start(""))
                .rootCause()
                .hasMessageContaining("JWT_SECRET must be set and at least 32 characters long");
    }

    @Test
    void startupFailsWhenJwtSecretIsTooShortWithoutLeakingIt() {
        String shortSecret = "only-31-characters-long-secret!";
        assertThatThrownBy(() -> start(shortSecret))
                .rootCause()
                .hasMessageContaining("at least 32 characters")
                .hasMessageNotContaining(shortSecret);
    }

    private static void start(String secret) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(TaskApiApplication.class)
                .profiles("test")
                .run("--server.port=0", "--app.jwt.secret=" + secret)) {
            // should not get here
        }
    }
}
