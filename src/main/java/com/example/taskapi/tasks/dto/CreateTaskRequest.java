package com.example.taskapi.tasks.dto;

import com.example.taskapi.tasks.TaskPriority;
import com.example.taskapi.tasks.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/** status defaults to TODO and priority to MEDIUM when omitted. */
public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        TaskStatus status,
        TaskPriority priority,
        @Size(max = 20) Set<@NotBlank @Size(max = 40) @Pattern(regexp = TaskConstraints.TAG_PATTERN,
                message = TaskConstraints.TAG_MESSAGE) String> tags,
        LocalDate dueDate) {}
