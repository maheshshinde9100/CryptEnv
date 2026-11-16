package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByStatus(String status);
    List<OutboxEvent> findByWorkspaceId(Long workspaceId);
    List<OutboxEvent> findByStatusAndRetryCountLessThan(String status, Integer maxRetries);
}
