package com.example.taskapi.tasks.dto;

import com.example.taskapi.tasks.Task;
import com.example.taskapi.tasks.TaskPriority;
import com.example.taskapi.tasks.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaskResponse(
        long id,
        long ownerId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        List<String> tags,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getOwnerId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority(), task.getTags().stream().sorted().toList(), task.getDueDate(),
                task.getCreatedAt(), task.getUpdatedAt(), task.getVersion());
    }
}
