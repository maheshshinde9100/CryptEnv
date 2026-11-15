package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {

    List<WebhookEndpoint> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WebhookEndpoint> findByUserIdAndActiveTrue(Long userId);

    List<WebhookEndpoint> findByWorkspaceIdAndActiveTrue(Long workspaceId);

    List<WebhookEndpoint> findByActiveTrue();
}
