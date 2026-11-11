package com.example.taskapi.tasks.dto;

import java.time.Instant;

/** SSE event body. {@code task} is null for task.deleted. */
public record TaskEventPayload(String type, long taskId, TaskResponse task, Instant occurredAt) {}
