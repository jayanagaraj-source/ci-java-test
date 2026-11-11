package com.example.taskapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PaginationFilteringTest extends ApiTestSupport {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = register("lister@example.com");
        createTask(token, """
                {"title":"Buy milk","status":"TODO","priority":"LOW","tags":["home"],"dueDate":"2030-03-01"}""");
        createTask(token, """
                {"title":"Fix login bug","description":"100% reproducible","status":"IN_PROGRESS",
                 "priority":"HIGH","tags":["work","bug"],"dueDate":"2030-01-01"}""");
        createTask(token, """
                {"title":"Write docs","status":"DONE","priority":"MEDIUM","tags":["work"]}""");
        createTask(token, """
                {"title":"Plan trip","status":"TODO","priority":"HIGH","tags":["home","travel"],"dueDate":"2030-02-01"}""");
        createTask(token, """
                {"title":"Review PR","status":"TODO","priority":"MEDIUM","tags":["work"]}""");
    }

    @Test
    void paginatesWithMetadata() throws Exception {
        JsonNode page1 = list(get("/api/v1/tasks").param("limit", "2").param("page", "1"));
        JsonNode page3 = list(get("/api/v1/tasks").param("limit", "2").param("page", "3"));

        assertThat(page1.get("items")).hasSize(2);
        assertThat(page1.get("totalItems").asLong()).isEqualTo(5);
        assertThat(page1.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page1.get("page").asInt()).isEqualTo(1);
        assertThat(page3.get("items")).hasSize(1);
    }

    @Test
    void filtersByStatusPriorityAndTag() throws Exception {
        assertThat(titles(get("/api/v1/tasks").param("status", "TODO")))
                .containsExactlyInAnyOrder("Buy milk", "Plan trip", "Review PR");
        assertThat(titles(get("/api/v1/tasks").param("priority", "HIGH")))
                .containsExactlyInAnyOrder("Fix login bug", "Plan trip");
        assertThat(titles(get("/api/v1/tasks").param("tag", "WORK")))
                .containsExactlyInAnyOrder("Fix login bug", "Write docs", "Review PR");
        assertThat(titles(get("/api/v1/tasks").param("tag", "home").param("priority", "HIGH")))
                .containsExactly("Plan trip");
    }

    @Test
    void searchesTitleAndDescriptionCaseInsensitivelyWithLiteralWildcards() throws Exception {
        assertThat(titles(get("/api/v1/tasks").param("q", "LOGIN"))).containsExactly("Fix login bug");
        assertThat(titles(get("/api/v1/tasks").param("q", "100%"))).containsExactly("Fix login bug");
        assertThat(titles(get("/api/v1/tasks").param("q", "%"))).containsExactly("Fix login bug");
        assertThat(titles(get("/api/v1/tasks").param("q", "nothing-matches"))).isEmpty();
    }

    @Test
    void sortsByAllowListedFields() throws Exception {
        assertThat(titles(get("/api/v1/tasks").param("sort", "-priority,title")))
                .containsExactly("Fix login bug", "Plan trip", "Review PR", "Write docs", "Buy milk");
        assertThat(titles(get("/api/v1/tasks").param("sort", "dueDate")))
                .startsWith("Fix login bug", "Plan trip", "Buy milk");
        assertThat(titles(get("/api/v1/tasks").param("sort", "title")))
                .containsExactly("Buy milk", "Fix login bug", "Plan trip", "Review PR", "Write docs");
    }

    @Test
    void limitIsCappedAt100() throws Exception {
        mvc.perform(get("/api/v1/tasks").param("limit", "100").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100));
        mvc.perform(get("/api/v1/tasks").param("limit", "1000").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    private JsonNode list(MockHttpServletRequestBuilder request) throws Exception {
        return body(mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> titles(MockHttpServletRequestBuilder request) throws Exception {
        List<String> titles = new ArrayList<>();
        list(request).get("items").forEach(item -> titles.add(item.get("title").asText()));
        return titles;
    }
}
