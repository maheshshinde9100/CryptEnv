package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/** Metadata-only leak finding; the matched secret is never persisted. */
@Entity @Table(name = "leak_findings") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LeakFinding {
    public enum Status { OPEN, IGNORED, ROTATION_SUGGESTED, RESOLVED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(name = "workspace_id") private Long workspaceId;
    @Column(name = "environment_id") private Long environmentId;
    @Column(nullable = false, length = 500) private String source;
    @Column(name = "line_number") private Integer lineNumber;
    @Column(nullable = false, length = 100) private String ruleName;
    @Column(name = "value_fingerprint", nullable = false, length = 64) private String valueFingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) @Builder.Default private Status status = Status.OPEN;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
