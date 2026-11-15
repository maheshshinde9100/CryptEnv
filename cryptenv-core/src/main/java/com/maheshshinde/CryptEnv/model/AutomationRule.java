package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * An automation rule (§10) of the form <em>ON event WHERE condition DO action</em>.
 * <p>
 * {@code conditions} and {@code actionConfig} are JSON objects. A per-rule loop guard limits a
 * rule to {@code 3} fires per rolling 60-second window so that an action which itself emits an
 * event cannot produce an unbounded cascade.
 */
@Entity
@Table(name = "automation_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationRule {

    public enum ActionType { NOTIFY, AUDIT, DISABLE_WEBHOOK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** The "ON" trigger: an event type, or {@code *} for every event. */
    @Column(name = "event_type", nullable = false, length = 60)
    @Builder.Default
    private String eventType = "*";

    /** The "WHERE" clause as a JSON object; empty matches every event. */
    @Column(columnDefinition = "TEXT")
    private String conditions;

    /** The "DO" action. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ActionType actionType;

    /** JSON parameters for the action. */
    @Column(name = "action_config", columnDefinition = "TEXT")
    private String actionConfig;

    @Column(name = "fire_count", nullable = false)
    @Builder.Default
    private Integer fireCount = 0;

    @Column(name = "window_started_at")
    private LocalDateTime windowStartedAt;

    @Column(name = "last_fired_at")
    private LocalDateTime lastFiredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
