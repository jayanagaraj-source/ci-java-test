package com.example.taskapi.tasks.dto;

import com.example.taskapi.tasks.TaskPriority;
import com.example.taskapi.tasks.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/**
 * PATCH body (JSON merge-patch semantics). Jackson only calls a setter for a
 * property that is present in the JSON, so each setter records presence. That
 * distinguishes "field absent" (leave unchanged) from "field: null" (clear it,
 * where allowed).
 */
public class UpdateTaskRequest {

    @Size(min = 1, max = 200)
    @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank")
    private String title;
    private boolean titlePresent;

    @Size(max = 5000)
    private String description;
    private boolean descriptionPresent;

    private TaskStatus status;
    private boolean statusPresent;

    private TaskPriority priority;
    private boolean priorityPresent;

    @Size(max = 20)
    private Set<@NotBlank @Size(max = 40) @Pattern(regexp = TaskConstraints.TAG_PATTERN,
            message = TaskConstraints.TAG_MESSAGE) String> tags;
    private boolean tagsPresent;

    private LocalDate dueDate;
    private boolean dueDatePresent;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public boolean hasTitle() {
        return titlePresent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public boolean hasDescription() {
        return descriptionPresent;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.statusPresent = true;
    }

    public boolean hasStatus() {
        return statusPresent;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
        this.priorityPresent = true;
    }

    public boolean hasPriority() {
        return priorityPresent;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
        this.tagsPresent = true;
    }

    public boolean hasTags() {
        return tagsPresent;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.dueDatePresent = true;
    }

    public boolean hasDueDate() {
        return dueDatePresent;
    }

    public boolean noFieldsPresent() {
        return !(titlePresent || descriptionPresent || statusPresent || priorityPresent || tagsPresent
                || dueDatePresent);
    }
}
