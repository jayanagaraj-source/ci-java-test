package com.example.taskapi.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.tasks.TaskEventBroadcaster;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full-stack tests through MockMvc (all servlet filters + Spring Security).
 * Tests are not @Transactional on purpose: SSE events are delivered after
 * commit, so every request must really commit. The database is wiped instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TaskEventBroadcaster broadcaster;

    @BeforeEach
    void resetState() {
        broadcaster.closeAll();
        jdbc.update("DELETE FROM task_tags");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM users");
        jdbc.update("UPDATE app_state SET admin_assigned = FALSE");
    }

    protected JsonNode registerUser(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result);
    }

    /** Registers a user and returns their access token. */
    protected String register(String email) throws Exception {
        return registerUser(email).get("accessToken").asText();
    }

    protected JsonNode createTask(String token, String jsonBody) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result);
    }

    protected long createTaskId(String token, String title) throws Exception {
        return createTask(token, "{\"title\":\"" + title + "\"}").get("id").asLong();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    public record Credentials(String email, String password) {}
}
