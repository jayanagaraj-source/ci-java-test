package com.example.taskapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TaskValidationTest extends ApiTestSupport {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = register("validator@example.com");
    }

    @Test
    void createRequiresATitle() throws Exception {
        postTask("{\"title\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:task-api:validation-error"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void unknownJsonFieldsAreRejected() throws Exception {
        postTask("{\"title\":\"ok\",\"ownerId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ownerId"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"));
    }

    @Test
    void invalidEnumAndDateValuesAreRejected() throws Exception {
        postTask("{\"title\":\"ok\",\"status\":\"BLOCKED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        postTask("{\"title\":\"ok\",\"dueDate\":\"tomorrow\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
    }

    @Test
    void malformedJsonIsAProblemWithoutInternals() throws Exception {
        postTask("{\"title\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is missing or is not valid JSON"));
    }

    @Test
    void tagsAreValidated() throws Exception {
        postTask("{\"title\":\"ok\",\"tags\":[\"<script>\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tags[]"));
    }

    @Test
    void createAppliesDefaults() throws Exception {
        postTask("{\"title\":\"  Write report  \",\"tags\":[\"Work\",\"work\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Write report"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andExpect(jsonPath("$.tags[0]").value("work"));
    }

    @Test
    void patchRejectsNullForRequiredFieldsAndEmptyBodies() throws Exception {
        long id = createTaskId(token, "task");
        mvc.perform(patch("/api/v1/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mvc.perform(patch("/api/v1/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queryParametersAreValidated() throws Exception {
        listWith("limit", "101").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        listWith("page", "0").andExpect(status().isBadRequest());
        listWith("sort", "passwordHash").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        listWith("status", "NOPE").andExpect(status().isBadRequest());
    }

    private ResultActions postTask(String body) throws Exception {
        return mvc.perform(post("/api/v1/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions listWith(String param, String value) throws Exception {
        return mvc.perform(get("/api/v1/tasks").param(param, value).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }
}
