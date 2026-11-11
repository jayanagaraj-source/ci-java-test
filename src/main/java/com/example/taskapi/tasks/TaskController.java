package com.example.taskapi.tasks;

import com.example.taskapi.auth.CurrentUser;
import com.example.taskapi.common.web.PageResponse;
import com.example.taskapi.tasks.dto.CreateTaskRequest;
import com.example.taskapi.tasks.dto.TaskFilter;
import com.example.taskapi.tasks.dto.TaskResponse;
import com.example.taskapi.tasks.dto.UpdateTaskRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/tasks")
class TaskController {

    private final TaskService tasks;
    private final TaskEventBroadcaster broadcaster;

    TaskController(TaskService tasks, TaskEventBroadcaster broadcaster) {
        this.tasks = tasks;
        this.broadcaster = broadcaster;
    }

    @GetMapping
    PageResponse<TaskResponse> list(
            CurrentUser user,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) @Size(max = 40) String tag,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "-createdAt") @Size(max = 100) String sort,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return tasks.list(user, new TaskFilter(status, priority, tag, q), TaskSort.parse(sort), page, limit);
    }

    /** Spring answers a matching If-None-Match with 304 automatically. */
    @GetMapping("/{id}")
    ResponseEntity<TaskResponse> get(CurrentUser user, @PathVariable long id) {
        TaskResponse task = tasks.get(user, id);
        return ResponseEntity.ok().eTag(ETags.of(task.version())).body(task);
    }

    @PostMapping
    ResponseEntity<TaskResponse> create(CurrentUser user, @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = tasks.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + task.id()))
                .eTag(ETags.of(task.version()))
                .body(task);
    }

    @PatchMapping(path = "/{id}", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json"})
    ResponseEntity<TaskResponse> update(
            CurrentUser user,
            @PathVariable long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateTaskRequest request) {
        TaskResponse task = tasks.update(user, id, request, ifMatch);
        return ResponseEntity.ok().eTag(ETags.of(task.version())).body(task);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            CurrentUser user,
            @PathVariable long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        tasks.delete(user, id, ifMatch);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(CurrentUser user, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no"); // disable proxy buffering (nginx)
        return broadcaster.subscribe(user);
    }
}
