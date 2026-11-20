package com.maheshshinde.CryptEnv.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.OutboxEvent;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.service.OutboxEventService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import com.maheshshinde.CryptEnv.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Tag(name = "SSE", description = "Server-Sent Events for live updates")
public class SseController {

    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "cryptenv-sse");
        thread.setDaemon(true);
        return thread;
    });

    private final OutboxEventService outboxEventService;
    private final WorkspaceService workspaceService;
    private final SecurityService securityService;
    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/events/{workspaceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live workspace events via SSE")
    public SseEmitter streamWorkspaceEvents(@PathVariable Long workspaceId) {
        User user = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));
        if (workspaceService.getEffectiveRole(workspace, user) == null) {
            throw new ResourceNotFoundException("Workspace not found with id: " + workspaceId);
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        long[] lastSeenId = {0L};

        ScheduledFuture<?> task = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                List<OutboxEvent> events = outboxEventService.getWorkspaceEvents(workspaceId).stream()
                        .filter(event -> event.getId() != null && event.getId() > lastSeenId[0])
                        .sorted(Comparator.comparing(OutboxEvent::getId))
                        .toList();
                for (OutboxEvent event : events) {
                    lastSeenId[0] = event.getId();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", event.getId());
                    payload.put("type", event.getEventType());
                    payload.put("aggregateType", event.getAggregateType());
                    payload.put("aggregateId", event.getAggregateId());
                    payload.put("payload", event.getPayload());
                    payload.put("createdAt", event.getCreatedAt());
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.getId()))
                            .name("workspace-event")
                            .data(objectMapper.writeValueAsString(payload)));
                }
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, 0, 5, TimeUnit.SECONDS);

        Runnable stop = () -> task.cancel(true);
        emitter.onCompletion(stop);
        emitter.onTimeout(() -> {
            stop.run();
            emitter.complete();
        });
        emitter.onError(error -> stop.run());
        return emitter;
    }
}
