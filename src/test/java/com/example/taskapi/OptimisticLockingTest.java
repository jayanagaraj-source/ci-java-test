package com.example.taskapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class OptimisticLockingTest extends ApiTestSupport {

    private String token;
    private long id;

    @BeforeEach
    void setUp() throws Exception {
        token = register("locker@example.com");
        id = createTask(token, """
                {"title":"Original","description":"keep me","priority":"HIGH","tags":["a","b"],"dueDate":"2030-01-31"}
                """).get("id").asLong();
    }

    @Test
    void getExposesVersionAsETagAndHonoursIfNoneMatch() throws Exception {
        mvc.perform(get(path()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""));
        mvc.perform(get(path()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(HttpHeaders.IF_NONE_MATCH, "\"0\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void patchWithCurrentETagSucceedsAndStaleETagReturns412() throws Exception {
        mvc.perform(patchTitle("First edit", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(patchTitle("Lost update", "\"0\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.type").value("urn:problem-type:task-api:precondition-failed"));

        mvc.perform(get(path()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.title").value("First edit"));
    }

    @Test
    void weakOrMalformedETagsDoNotMatch() throws Exception {
        mvc.perform(patchTitle("x", "W/\"0\"")).andExpect(status().isPreconditionFailed());
        mvc.perform(patchTitle("x", "0")).andExpect(status().isPreconditionFailed());
        mvc.perform(patchTitle("x", "\"7\", \"0\"")).andExpect(status().isOk());
    }

    @Test
    void patchOnlyChangesFieldsThatWereSent() throws Exception {
        mvc.perform(patch(path()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.title").value("Original"))
                .andExpect(jsonPath("$.description").value("keep me"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.dueDate").value("2030-01-31"));

        // Explicit null clears a nullable field; other fields stay untouched.
        mvc.perform(patch(path()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType("application/merge-patch+json").content("{\"dueDate\":null,\"tags\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.tags.length()").value(0))
                .andExpect(jsonPath("$.description").value("keep me"));
    }

    @Test
    void deleteHonoursIfMatch() throws Exception {
        mvc.perform(delete(path()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(HttpHeaders.IF_MATCH, "\"5\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(delete(path()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());
        mvc.perform(get(path()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    private String path() {
        return "/api/v1/tasks/" + id;
    }

    private org.springframework.test.web.servlet.RequestBuilder patchTitle(String title, String ifMatch) {
        return patch(path()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\"}");
    }
}
