package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Security incident lifecycle record. It intentionally contains no secret material. */
@Entity
@Table(name = "security_incidents")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SecurityIncident {
    public enum Status { OPEN, INVESTIGATING, CONTAINED, RESOLVED, POST_MORTEM }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "incident_number", nullable = false, unique = true, length = 24) private String incidentNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reporter_id") private User reporter;
    @Column(name = "workspace_id") private Long workspaceId;
    @Column(name = "environment_id") private Long environmentId;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Status status = Status.OPEN;
    @Column(name = "timeline", columnDefinition = "TEXT") private String timeline;
    @Column(name = "post_mortem", columnDefinition = "TEXT") private String postMortem;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}
