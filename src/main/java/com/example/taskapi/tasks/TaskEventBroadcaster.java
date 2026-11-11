package com.example.taskapi.tasks;

import com.example.taskapi.auth.CurrentUser;
import com.example.taskapi.common.error.ApiException;
import com.example.taskapi.config.AppProperties;
import com.example.taskapi.tasks.dto.TaskEventPayload;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Fans task change events out to SSE subscribers. A subscriber receives an
 * event only if it could read the task: admins get everything, users only
 * events for tasks they own.
 *
 * As a SmartLifecycle in the highest phase it stops before the web server's
 * graceful-shutdown phase, completing every open stream so shutdown is not held
 * up by long-lived connections.
 */
@Component
public class TaskEventBroadcaster implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TaskEventBroadcaster.class);

    private record Subscription(long userId, boolean admin, SseEmitter emitter) {}

    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final AppProperties.Sse settings;
    private final TaskScheduler scheduler;
    private volatile ScheduledFuture<?> heartbeat;
    private volatile boolean running;

    public TaskEventBroadcaster(AppProperties properties, TaskScheduler scheduler) {
        this.settings = properties.sse();
        this.scheduler = scheduler;
    }

    public SseEmitter subscribe(CurrentUser user) {
        long open = subscriptions.stream().filter(s -> s.userId() == user.id()).count();
        if (open >= settings.maxConnectionsPerUser()) {
            throw ApiException.tooManyRequests("Too many open event streams for this user");
        }
        SseEmitter emitter = new SseEmitter(settings.timeout().toMillis());
        Subscription subscription = new Subscription(user.id(), user.isAdmin(), emitter);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> {
            subscriptions.remove(subscription);
            emitter.complete();
        });
        emitter.onError(error -> subscriptions.remove(subscription));
        send(subscription, () -> SseEmitter.event().name("ready").data("{\"status\":\"connected\"}"));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onTaskChanged(TaskChangedEvent event) {
        TaskEventPayload payload = new TaskEventPayload(
                event.type().eventName(), event.taskId(), event.task(), event.occurredAt());
        String eventId = UUID.randomUUID().toString();
        for (Subscription subscription : subscriptions) {
            if (subscription.admin() || subscription.userId() == event.ownerId()) {
                send(subscription, () -> SseEmitter.event()
                        .id(eventId)
                        .name(payload.type())
                        .data(payload, MediaType.APPLICATION_JSON));
            }
        }
    }

    /** Completes every open stream (used on shutdown). */
    public void closeAll() {
        for (Subscription subscription : subscriptions) {
            try {
                subscription.emitter().complete();
            } catch (RuntimeException ignored) {
                // already completed
            }
        }
        subscriptions.clear();
    }

    int subscriberCount() {
        return subscriptions.size();
    }

    private void heartbeat() {
        for (Subscription subscription : subscriptions) {
            send(subscription, () -> SseEmitter.event().comment("keep-alive"));
        }
    }

    /** A fresh builder per send: SseEventBuilder.build() is not idempotent. */
    private void send(Subscription subscription, Supplier<SseEventBuilder> event) {
        try {
            subscription.emitter().send(event.get());
        } catch (IOException | IllegalStateException e) {
            // Client went away or the emitter already completed.
            subscriptions.remove(subscription);
            log.atDebug().addKeyValue("user.id", subscription.userId()).log("dropped SSE subscriber");
        }
    }

    @Override
    public void start() {
        heartbeat = scheduler.scheduleWithFixedDelay(this::heartbeat, settings.heartbeat());
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        ScheduledFuture<?> task = heartbeat;
        if (task != null) {
            task.cancel(false);
        }
        closeAll();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
