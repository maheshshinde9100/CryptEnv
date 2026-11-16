package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.OutboxEvent;
import com.maheshshinde.CryptEnv.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public OutboxEvent publishEvent(String eventType, String payload, String aggregateType,
                                    Long aggregateId, Long workspaceId) {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(eventType)
                .payload(payload)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .workspaceId(workspaceId)
                .status("pending")
                .retryCount(0)
                .build();
        return outboxEventRepository.save(event);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusAndRetryCountLessThan("pending", 3);
        for (OutboxEvent event : pendingEvents) {
            try {
                // Process the event (e.g., send webhook, trigger notification)
                processEvent(event);
                event.setStatus("processed");
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event {}", event.getId(), e);
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage(e.getMessage());
                if (event.getRetryCount() >= 3) {
                    event.setStatus("failed");
                }
                outboxEventRepository.save(event);
            }
        }
    }

    private void processEvent(OutboxEvent event) {
        // In a real implementation, this would dispatch the event to the appropriate handler
        // For now, we'll just log it
        log.info("Processing event: {} for aggregate: {}", event.getEventType(), event.getAggregateId());
    }

    public List<OutboxEvent> getWorkspaceEvents(Long workspaceId) {
        return outboxEventRepository.findByWorkspaceId(workspaceId);
    }
}
