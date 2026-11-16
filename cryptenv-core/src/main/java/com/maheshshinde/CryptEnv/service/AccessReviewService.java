package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.AccessReview;
import com.maheshshinde.CryptEnv.model.AccessReviewItem;
import com.maheshshinde.CryptEnv.model.WorkspaceMember;
import com.maheshshinde.CryptEnv.repository.AccessReviewItemRepository;
import com.maheshshinde.CryptEnv.repository.AccessReviewRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessReviewService {

    private final AccessReviewRepository accessReviewRepository;
    private final AccessReviewItemRepository accessReviewItemRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional
    public AccessReview createAccessReview(Long workspaceId, String name, LocalDateTime reviewPeriodStart,
                                           LocalDateTime reviewPeriodEnd, LocalDateTime dueDate, Long createdBy) {
        AccessReview review = AccessReview.builder()
                .workspaceId(workspaceId)
                .name(name)
                .reviewPeriodStart(reviewPeriodStart)
                .reviewPeriodEnd(reviewPeriodEnd)
                .dueDate(dueDate)
                .createdBy(createdBy)
                .status("pending")
                .build();
        review = accessReviewRepository.save(review);

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        for (WorkspaceMember member : members) {
            AccessReviewItem item = AccessReviewItem.builder()
                    .accessReviewId(review.getId())
                    .userId(member.getUser().getId())
                    .workspaceMemberId(member.getWorkspace().getId())
                    .role(member.getRole().name())
                    .status("pending")
                    .build();
            accessReviewItemRepository.save(item);
        }

        return review;
    }

    @Transactional
    public AccessReviewItem reviewItem(Long itemId, String status, Long reviewedBy, String comment) {
        AccessReviewItem item = accessReviewItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Review item not found"));
        item.setStatus(status);
        item.setReviewedBy(reviewedBy);
        item.setReviewedAt(LocalDateTime.now());
        item.setComment(comment);
        return accessReviewItemRepository.save(item);
    }

    @Transactional
    public AccessReview completeReview(Long reviewId) {
        AccessReview review = accessReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        review.setStatus("completed");
        return accessReviewRepository.save(review);
    }

    public List<AccessReview> getWorkspaceReviews(Long workspaceId) {
        return accessReviewRepository.findByWorkspaceId(workspaceId);
    }

    public List<AccessReviewItem> getReviewItems(Long reviewId) {
        return accessReviewItemRepository.findByAccessReviewId(reviewId);
    }
}
