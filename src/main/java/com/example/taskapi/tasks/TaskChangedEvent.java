package com.example.taskapi.tasks;

import com.example.taskapi.tasks.dto.TaskResponse;
import java.time.Instant;

/** Published inside the transaction; delivered to SSE subscribers only after commit. */
record TaskChangedEvent(Type type, long taskId, long ownerId, TaskResponse task, Instant occurredAt) {

    enum Type {
        CREATED("task.created"),
        UPDATED("task.updated"),
        DELETED("task.deleted");

        private final String eventName;

        Type(String eventName) {
            this.eventName = eventName;
        }

        String eventName() {
            return eventName;
        }
    }
}
