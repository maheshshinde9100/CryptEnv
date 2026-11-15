package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.DomainEvent;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.DomainEventRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Domain event outbox + live feed (§10). Events are persisted in the caller's transaction and
 * then, after commit, pushed to any open Server-Sent Events subscribers whose scope matches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    public static final String ALL_EVENTS = "*";

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int REPLAY_LIMIT = 25;

    private final DomainEventRepository eventRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    private final Map<Long, List<SseSubscription>> subscriptions = new ConcurrentHashMap<>();

    private record SseSubscription(Set<Long> workspaceIds, SseEmitter emitter) {
    }

    // ------------------------------------------------------------------ publish

    /** Records a domain event in the caller's transaction (outbox write). */
    @Transactional
    public DomainEvent publish(String type, Long userId, Long workspaceId, String resourceType,
                               String resourceId, Map<String, Object> payload) {
        DomainEvent event = DomainEvent.builder()
                .type(type)
                .userId(userId)
                .workspaceId(workspaceId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .payload(payload != null ? writeJson(payload) : null)
                .status(DomainEvent.Status.PENDING)
                .attempts(0)
                .maxAttempts(3)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        DomainEvent saved = eventRepository.save(event);
        applicationEventPublisher.publishEvent(new DomainEventRecorded(saved));
        return saved;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEventRecorded(DomainEventRecorded recorded) {
        broadcast(recorded.event());
    }

    // ------------------------------------------------------------------ outbox dispatch

    /** Claims up to {@code limit} due outbox rows for dispatch (marks them PROCESSING). */
    @Transactional
    public List<DomainEvent> claimDueEvents(int limit) {
        List<DomainEvent> due = eventRepository.findDueEvents(LocalDateTime.now(), PageRequest.of(0, limit));
        for (DomainEvent event : due) {
            event.setStatus(DomainEvent.Status.PROCESSING);
            event.setAttempts(event.getAttempts() + 1);
            eventRepository.save(event);
        }
        return due;
    }

    @Transactional
    public void markProcessed(Long id) {
        eventRepository.findById(id).ifPresent(event -> {
            event.setStatus(DomainEvent.Status.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            event.setNextAttemptAt(null);
            event.setLastError(null);
            eventRepository.save(event);
        });
    }

    @Transactional
    public void markFailed(Long id, String error) {
        eventRepository.findById(id).ifPresent(event -> {
            event.setStatus(DomainEvent.Status.FAILED);
            event.setLastError(error != null && error.length() > 1000 ? error.substring(0, 1000) : error);
            if (event.getAttempts() >= event.getMaxAttempts()) {
                event.setNextAttemptAt(null);
            } else {
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(30L * event.getAttempts()));
            }
            eventRepository.save(event);
        });
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(Long workspaceId, Pageable pageable) {
        User user = securityService.getCurrentUser();
        Page<DomainEvent> events = workspaceId != null
                ? eventRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                : eventRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        return events.map(this::toView);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        User user = securityService.getCurrentUser();
        List<DomainEvent> events = eventRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        Map<String, Long> byType = events.stream()
                .collect(Collectors.groupingBy(DomainEvent::getType, LinkedHashMap::new, Collectors.counting()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", (long) events.size());
        result.put("byType", byType);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (DomainEvent.Status status : DomainEvent.Status.values()) {
            byStatus.put(status.name(), events.stream().filter(e -> e.getStatus() == status).count());
        }
        result.put("byStatus", byStatus);
        return result;
    }

    @Transactional
    public Map<String, Object> replay(Long id) {
        User user = securityService.getCurrentUser();
        DomainEvent event = eventRepository.findById(id)
                .filter(e -> user.getId().equals(e.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
        event.setStatus(DomainEvent.Status.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now());
        event.setLastError(null);
        DomainEvent saved = eventRepository.save(event);
        applicationEventPublisher.publishEvent(new DomainEventRecorded(saved));
        return toView(saved);
    }

    // ------------------------------------------------------------------ SSE feed

    /** Opens a Server-Sent Events stream scoped to the caller's user and workspaces. */
    @Transactional(readOnly = true)
    public SseEmitter subscribe() {
        User user = securityService.getCurrentUser();
        Set<Long> workspaceIds = workspaceIdsFor(user.getId());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseSubscription subscription = new SseSubscription(workspaceIds, emitter);
        subscriptions.computeIfAbsent(user.getId(), k -> new CopyOnWriteArrayList<>()).add(subscription);

        Runnable cleanup = () -> remove(user.getId(), subscription);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            for (DomainEvent event : recentFor(user.getId(), workspaceIds, REPLAY_LIMIT)) {
                send(emitter, "event", toView(event));
            }
            emitter.send(SseEmitter.event().name("ready")
                    .data(Map.of("workspaces", workspaceIds, "replay", REPLAY_LIMIT)));
        } catch (Exception e) {
            log.debug("SSE subscriber {} disconnected during replay: {}", user.getId(), e.getMessage());
            cleanup.run();
        }
        return emitter;
    }

    private void broadcast(DomainEvent event) {
        if (subscriptions.isEmpty()) {
            return;
        }
        Map<String, Object> view = toView(event);
        for (Map.Entry<Long, List<SseSubscription>> entry : subscriptions.entrySet()) {
            for (SseSubscription sub : entry.getValue()) {
                boolean matches = entry.getKey().equals(event.getUserId())
                        || (event.getWorkspaceId() != null && sub.workspaceIds().contains(event.getWorkspaceId()));
                if (matches) {
                    send(sub.emitter(), "event", view);
                }
            }
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            log.trace("Dropping SSE send: {}", e.getMessage());
        }
    }

    private void remove(Long userId, SseSubscription subscription) {
        List<SseSubscription> list = subscriptions.get(userId);
        if (list != null) {
            list.remove(subscription);
            if (list.isEmpty()) {
                subscriptions.remove(userId);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private List<DomainEvent> recentFor(Long userId, Set<Long> workspaceIds, int limit) {
        List<DomainEvent> byUser = eventRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<DomainEvent> combined = new ArrayList<>(byUser);
        if (!workspaceIds.isEmpty()) {
            combined.addAll(eventRepository.findRecentByWorkspaceIds(
                    new ArrayList<>(workspaceIds), PageRequest.of(0, limit)));
        }
        return combined.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Set<Long> workspaceIdsFor(Long userId) {
        Set<Long> ids = new HashSet<>();
        workspaceRepository.findByOwnerId(userId).forEach(w -> ids.add(w.getId()));
        workspaceRepository.findByMembershipsUserId(userId).forEach(w -> ids.add(w.getId()));
        return ids;
    }

    public Map<String, Object> toView(DomainEvent event) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", event.getId());
        view.put("type", event.getType());
        view.put("userId", event.getUserId());
        view.put("workspaceId", event.getWorkspaceId());
        view.put("resourceType", event.getResourceType());
        view.put("resourceId", event.getResourceId());
        view.put("payload", readJson(event.getPayload()));
        view.put("status", event.getStatus().name());
        view.put("attempts", event.getAttempts());
        view.put("lastError", event.getLastError());
        view.put("createdAt", event.getCreatedAt());
        view.put("processedAt", event.getProcessedAt());
        return view;
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }
}
