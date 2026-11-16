package com.maheshshinde.CryptEnv.repository;

import com.maheshshinde.CryptEnv.model.AccessReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessReviewItemRepository extends JpaRepository<AccessReviewItem, Long> {
    List<AccessReviewItem> findByAccessReviewId(Long accessReviewId);
    List<AccessReviewItem> findByUserId(Long userId);
    List<AccessReviewItem> findByAccessReviewIdAndUserId(Long accessReviewId, Long userId);
}
