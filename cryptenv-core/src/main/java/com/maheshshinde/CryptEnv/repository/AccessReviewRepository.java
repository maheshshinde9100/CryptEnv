package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.AccessReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessReviewRepository extends JpaRepository<AccessReview, Long> {
    List<AccessReview> findByWorkspaceId(Long workspaceId);
    List<AccessReview> findByStatus(String status);
    Optional<AccessReview> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
