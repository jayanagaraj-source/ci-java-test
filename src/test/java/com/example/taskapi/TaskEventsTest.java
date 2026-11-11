package com.example.taskapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskapi.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class TaskEventsTest extends ApiTestSupport {

    @Test
    void eachSubscriberOnlyReceivesEventsForTasksTheyCanSee() throws Exception {
        String admin = register("admin@example.com");
        String alice = register("alice@example.com");
        String bob = register("bob@example.com");

        MvcResult adminStream = subscribe(admin);
        MvcResult aliceStream = subscribe(alice);
        MvcResult bobStream = subscribe(bob);

        long id = createTaskId(alice, "alice secret");
        mvc.perform(patch("/api/v1/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/tasks/" + id).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isNoContent());

        String aliceEvents = aliceStream.getResponse().getContentAsString();
        assertThat(aliceEvents).contains("event:ready")
                .contains("event:task.created", "event:task.updated", "event:task.deleted")
                .contains("alice secret");
        assertThat(adminStream.getResponse().getContentAsString())
                .contains("event:task.created", "event:task.updated", "event:task.deleted");

        String bobEvents = bobStream.getResponse().getContentAsString();
        assertThat(bobEvents).contains("event:ready").doesNotContain("task.", "alice secret");
    }

    @Test
    void streamRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/tasks/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult subscribe(String token) throws Exception {
        return mvc.perform(get("/api/v1/tasks/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }
}
