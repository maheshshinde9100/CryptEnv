package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A single evaluation of an {@link AutomationRule} (§10): the outcome of running the rule's
 * action for a matching event, or a record that the loop guard suppressed it.
 */
@Entity
@Table(name = "automation_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationRun {

    public enum Status { SUCCESS, FAILED, BLOCKED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "event_type", length = 60)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 1000)
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
