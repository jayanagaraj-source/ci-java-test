package com.example.taskapi.tasks;

import com.example.taskapi.auth.CurrentUser;
import com.example.taskapi.common.error.ApiException;
import com.example.taskapi.common.web.PageResponse;
import com.example.taskapi.tasks.dto.CreateTaskRequest;
import com.example.taskapi.tasks.dto.TaskFilter;
import com.example.taskapi.tasks.dto.TaskResponse;
import com.example.taskapi.tasks.dto.UpdateTaskRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task use cases. Every lookup is scoped to the caller: a task the caller may
 * not see is indistinguishable from one that does not exist (404, never 403).
 */
@Service
@Transactional
public class TaskService {

    private final TaskRepository tasks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TaskService(TaskRepository tasks, ApplicationEventPublisher events, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(CurrentUser user, TaskFilter filter, Sort sort, int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit, sort);
        return PageResponse.of(tasks.findAll(TaskSpecifications.matching(user, filter), pageRequest)
                .map(TaskResponse::from));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(CurrentUser user, long id) {
        return TaskResponse.from(load(user, id));
    }

    public TaskResponse create(CurrentUser user, CreateTaskRequest request) {
        Task task = new Task(user.id(), Instant.now(clock));
        task.setTitle(request.title().strip());
        task.setDescription(request.description());
        task.setStatus(request.status() != null ? request.status() : TaskStatus.TODO);
        task.setPriority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM);
        task.replaceTags(normalizeTags(request.tags()));
        task.setDueDate(request.dueDate());
        TaskResponse created = TaskResponse.from(tasks.saveAndFlush(task));
        publish(TaskChangedEvent.Type.CREATED, created.id(), created.ownerId(), created);
        return created;
    }

    /** Applies only the fields present in the request. */
    public TaskResponse update(CurrentUser user, long id, UpdateTaskRequest request, String ifMatch) {
        rejectNullsForRequiredFields(request);
        Task task = load(user, id);
        if (!ETags.ifMatchSatisfied(ifMatch, task.getVersion())) {
            throw ApiException.preconditionFailed("The task has changed since it was read (If-Match does not match)");
        }
        if (request.hasTitle()) {
            task.setTitle(request.getTitle().strip());
        }
        if (request.hasDescription()) {
            task.setDescription(request.getDescription());
        }
        if (request.hasStatus()) {
            task.setStatus(request.getStatus());
        }
        if (request.hasPriority()) {
            task.setPriority(request.getPriority());
        }
        if (request.hasTags()) {
            task.replaceTags(normalizeTags(request.getTags()));
        }
        if (request.hasDueDate()) {
            task.setDueDate(request.getDueDate());
        }
        task.setUpdatedAt(Instant.now(clock));
        TaskResponse updated = TaskResponse.from(tasks.saveAndFlush(task));
        publish(TaskChangedEvent.Type.UPDATED, updated.id(), updated.ownerId(), updated);
        return updated;
    }

    public void delete(CurrentUser user, long id, String ifMatch) {
        Task task = load(user, id);
        if (!ETags.ifMatchSatisfied(ifMatch, task.getVersion())) {
            throw ApiException.preconditionFailed("The task has changed since it was read (If-Match does not match)");
        }
        tasks.delete(task);
        tasks.flush();
        publish(TaskChangedEvent.Type.DELETED, task.getId(), task.getOwnerId(), null);
    }

    private Task load(CurrentUser user, long id) {
        return (user.isAdmin() ? tasks.findById(id) : tasks.findByIdAndOwnerId(id, user.id()))
                .orElseThrow(() -> ApiException.notFound("Task not found"));
    }

    private void publish(TaskChangedEvent.Type type, long taskId, long ownerId, TaskResponse task) {
        events.publishEvent(new TaskChangedEvent(type, taskId, ownerId, task, Instant.now(clock)));
    }

    private static void rejectNullsForRequiredFields(UpdateTaskRequest request) {
        if (request.noFieldsPresent()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "validation-error",
                    "The request body must contain at least one field to update");
        }
        if (request.hasTitle() && request.getTitle() == null) {
            throw ApiException.validation("title", "must not be null");
        }
        if (request.hasStatus() && request.getStatus() == null) {
            throw ApiException.validation("status", "must not be null");
        }
        if (request.hasPriority() && request.getPriority() == null) {
            throw ApiException.validation("priority", "must not be null");
        }
        if (request.hasTags() && request.getTags() == null) {
            throw ApiException.validation("tags", "must not be null (send [] to clear)");
        }
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        Set<String> normalized = new LinkedHashSet<>();
        if (tags != null) {
            tags.forEach(tag -> normalized.add(tag.trim().toLowerCase(Locale.ROOT)));
        }
        return normalized;
    }
}
