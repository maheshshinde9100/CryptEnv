package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Drains the {@link DomainEvent} outbox and the webhook delivery queue (§10). Event fan-out
 * (webhook endpoints, notifications, automation rules) runs here, decoupled from the request
 * that produced the event.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventDispatcher {

    private static final int MAX_EVENTS_PER_TICK = 20;
    private static final int MAX_DELIVERIES_PER_TICK = 10;

    private final EventService eventService;
    private final WebhookService webhookService;
    private final NotificationService notificationService;

    @Scheduled(
            fixedDelayString = "${cryptenv.events.poll-ms:10000}",
            initialDelayString = "${cryptenv.events.initial-delay-ms:15000}")
    public void dispatchEvents() {
        List<DomainEvent> events = eventService.claimDueEvents(MAX_EVENTS_PER_TICK);
        for (DomainEvent event : events) {
            try {
                webhookService.fanOut(event);
                notificationService.record(event);
                eventService.markProcessed(event.getId());
            } catch (Exception e) {
                log.warn("Failed to dispatch event {}: {}", event.getId(), e.getMessage());
                eventService.markFailed(event.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(
            fixedDelayString = "${cryptenv.webhooks.poll-ms:10000}",
            initialDelayString = "${cryptenv.webhooks.initial-delay-ms:18000}")
    public void drainDeliveries() {
        for (int processed = 0; processed < MAX_DELIVERIES_PER_TICK; processed++) {
            Optional<Long> deliveryId = webhookService.claimNextDueDelivery();
            if (deliveryId.isEmpty()) {
                return;
            }
            try {
                webhookService.executeDelivery(deliveryId.get());
            } catch (Exception e) {
                log.error("Unexpected error executing webhook delivery {}", deliveryId.get(), e);
            }
        }
    }
}
