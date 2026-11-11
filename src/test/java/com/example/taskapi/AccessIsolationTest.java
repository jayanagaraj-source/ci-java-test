package com.example.taskapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AccessIsolationTest extends ApiTestSupport {

    private String admin;
    private String alice;
    private String bob;
    private long aliceTask;

    @BeforeEach
    void setUp() throws Exception {
        admin = register("admin@example.com");
        alice = register("alice@example.com");
        bob = register("bob@example.com");
        aliceTask = createTaskId(alice, "alice private task");
        createTaskId(bob, "bob task");
    }

    @Test
    void usersOnlyListTheirOwnTasks() throws Exception {
        mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("bob task"));
    }

    @Test
    void anotherUsersTaskIsNotFoundRatherThanForbidden() throws Exception {
        String path = "/api/v1/tasks/" + aliceTask;

        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task not found"));
        mvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound());

        // Unchanged for the owner.
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("alice private task"));
    }

    @Test
    void notFoundLooksTheSameForMissingAndForeignTasks() throws Exception {
        String foreign = mvc.perform(get("/api/v1/tasks/" + aliceTask).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/v1/tasks/999999").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(json.readTree(foreign).get("detail"))
                .isEqualTo(json.readTree(missing).get("detail"));
    }

    @Test
    void adminSeesAllTasks() throws Exception {
        mvc.perform(get("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/v1/tasks/" + aliceTask).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminsCanListUsers() throws Exception {
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());

        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:problem-type:task-api:forbidden"));

        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }
}
