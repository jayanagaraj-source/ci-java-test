package com.example.taskapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends ApiTestSupport {

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @Test
    void firstRegisteredUserIsAdminAndLaterUsersAreUsers() throws Exception {
        JsonNode first = registerUser("first@example.com");
        JsonNode second = registerUser("second@example.com");

        assertThat(first.at("/user/role").asText()).isEqualTo("ADMIN");
        assertThat(second.at("/user/role").asText()).isEqualTo("USER");
        assertThat(first.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(first.get("expiresIn").asLong()).isPositive();
        assertThat(first.toString()).doesNotContain("password");
    }

    @Test
    void registrationNormalizesEmailAndRejectsDuplicates() throws Exception {
        registerUser("Someone@Example.com");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials("someone@example.com", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:task-api:email-taken"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
    }

    @Test
    void loginReturnsTokenThatWorksForMe() throws Exception {
        registerUser("me@example.com");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials("ME@example.com", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String token = body(login).get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
        registerUser("known@example.com");

        clearInvocations(passwordEncoder);
        JsonNode unknownEmail = failedLogin("nobody@example.com", PASSWORD);
        // The unknown-email path still runs exactly one BCrypt comparison.
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());

        clearInvocations(passwordEncoder);
        JsonNode wrongPassword = failedLogin("known@example.com", "wrong-password-123");
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());

        for (String field : new String[] {"type", "title", "status", "detail"}) {
            assertThat(unknownEmail.get(field)).isEqualTo(wrongPassword.get(field));
        }
        assertThat(unknownEmail.get("detail").asText()).isEqualTo("Invalid email or password");
    }

    @Test
    void meRequiresAValidToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        // header {"alg":"HS256"} / payload {"sub":"1","role":"ADMIN","iss":"task-api"} / bogus signature
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IkFETUlOIiwiaXNzIjoidGFzay1hcGkifQ."
                + "c2lnbmF0dXJlLXRoYXQtZG9lcy1ub3QtbWF0Y2g";
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode failedLogin(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, password))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        return body(result);
    }
}
