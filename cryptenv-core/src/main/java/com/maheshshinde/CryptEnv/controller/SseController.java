package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.OutboxEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Tag(name = "SSE", description = "Server-Sent Events for live updates")
public class SseController {

    private final OutboxEventService outboxEventService;

    @GetMapping(value = "/events/{workspaceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live workspace events via SSE")
    public Flux<ServerSentEvent<String>> streamWorkspaceEvents(@PathVariable Long workspaceId) {
        return Flux.interval(Duration.ofSeconds(1))
                .map(sequence -> {
                    List<com.maheshshinde.CryptEnv.model.OutboxEvent> events = outboxEventService.getWorkspaceEvents(workspaceId);
                    return ServerSentEvent.<String>builder()
                            .data(events.size() + " events")
                            .build();
                });
    }
}
