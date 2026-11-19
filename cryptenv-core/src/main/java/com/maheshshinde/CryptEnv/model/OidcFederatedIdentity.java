package com.maheshshinde.CryptEnv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "oidc_federated_identities", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "provider_subject"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcFederatedIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_identity_id", nullable = false)
    private Long machineIdentityId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "issuer_url", nullable = false)
    private String issuerUrl;

    @Column
    private String audience;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
