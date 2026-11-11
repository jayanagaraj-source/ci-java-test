package com.example.taskapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ErrorHandlingAndHeadersTest extends ApiTestSupport {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = register("errors@example.com");
    }

    @Test
    void notFoundIsAProblemDetailWithRequestId() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/tasks/424242").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:task-api:not-found"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Task not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks/424242"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        String requestId = body(result).get("requestId").asText();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo(requestId);
    }

    @Test
    void unknownRoutesAreProblemDetailsWithoutInternalDetail() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The requested resource was not found"));
    }

    @Test
    void safeIncomingRequestIdIsReusedAndUnsafeOneIsReplaced() throws Exception {
        mvc.perform(get("/api/v1/tasks/1").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("X-Request-Id", "client-abc-123"))
                .andExpect(header().string("X-Request-Id", "client-abc-123"))
                .andExpect(jsonPath("$.requestId").value("client-abc-123"));

        MvcResult unsafe = mvc.perform(get("/api/v1/tasks/1").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("X-Request-Id", "bad\r\nid<script>"))
                .andReturn();
        assertThat(unsafe.getResponse().getHeader("X-Request-Id"))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Permissions-Policy"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/v1/tasks")
                        .header(HttpHeaders.ORIGIN, "https://app.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.test"));

        mvc.perform(options("/api/v1/tasks")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void oversizedBodiesAreRejectedWith413() throws Exception {
        String huge = "{\"title\":\"" + "x".repeat(70 * 1024) + "\"}";
        mvc.perform(post("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void healthProbesArePublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
