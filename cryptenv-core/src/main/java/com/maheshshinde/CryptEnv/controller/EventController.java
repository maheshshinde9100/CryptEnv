package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Domain events & live feed (§10). Events are read from the outbox; {@code /stream} pushes
 * them to the dashboard in real time over Server-Sent Events.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Domain event outbox and live SSE feed")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "List domain events (newest first)")
    public ResponseEntity<Page<Map<String, Object>>> list(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(eventService.list(workspaceId, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/stats")
    @Operation(summary = "Event counts by type and status")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(eventService.stats());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-Sent Events stream of domain events")
    public SseEmitter stream() {
        return eventService.subscribe();
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Re-queue an event for dispatch")
    public ResponseEntity<Map<String, Object>> replay(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.replay(id));
    }
}
