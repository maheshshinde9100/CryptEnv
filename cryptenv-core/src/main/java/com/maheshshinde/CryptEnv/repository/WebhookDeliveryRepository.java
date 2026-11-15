package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(Long endpointId);

    List<WebhookDelivery> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WebhookDelivery> findByEndpointIdAndStatusOrderByCreatedAtDesc(Long endpointId, WebhookDelivery.Status status);

    /** Deliveries that are due for their next (re)attempt, oldest first. */
    @Query("SELECT d FROM WebhookDelivery d WHERE d.status IN ('PENDING','FAILED') "
            + "AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now) ORDER BY d.createdAt ASC")
    List<WebhookDelivery> findDueDeliveries(@Param("now") LocalDateTime now);

    long countByStatus(WebhookDelivery.Status status);
}
