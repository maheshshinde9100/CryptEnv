package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.DomainEvent;

/**
 * Spring application event published after a {@link DomainEvent} is persisted, so interested
 * listeners (SSE feed, automation rules) can react. Listeners should be
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so they never observe rolled-back work.
 */
public record DomainEventRecorded(DomainEvent event) {
}
