package com.example.taskapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.rate-limit.auth-requests=3", "app.rate-limit.auth-window=10m"})
class AuthRateLimitTest extends ApiTestSupport {

    @Test
    void authEndpointsAreRateLimitedPerClient() throws Exception {
        String body = json.writeValueAsString(new Credentials("nobody@example.com", PASSWORD));
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:task-api:too-many-requests"));

        // A different client is unaffected.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(r -> { r.setRemoteAddr("198.51.100.9"); return r; }))
                .andExpect(status().isUnauthorized());
    }
}
