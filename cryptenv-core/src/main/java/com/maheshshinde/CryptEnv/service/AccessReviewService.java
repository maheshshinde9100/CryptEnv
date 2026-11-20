package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.AccessReview;
import com.maheshshinde.CryptEnv.model.AccessReviewItem;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.model.WorkspaceMember;
import com.maheshshinde.CryptEnv.repository.AccessReviewItemRepository;
import com.maheshshinde.CryptEnv.repository.AccessReviewRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceMemberRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessReviewService {

    private static final Set<String> ITEM_STATUSES = Set.of("approved", "revoked", "escalated");

    private final AccessReviewRepository accessReviewRepository;
    private final AccessReviewItemRepository accessReviewItemRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;
    private final SecurityService securityService;

    @Transactional
    public AccessReview createAccessReview(Long workspaceId, String name, LocalDateTime reviewPeriodStart,
                                           LocalDateTime reviewPeriodEnd, LocalDateTime dueDate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Review name is required");
        }
        Workspace workspace = requireAdmin(workspaceId);

        LocalDateTime start = reviewPeriodStart != null ? reviewPeriodStart : LocalDateTime.now();
        LocalDateTime end = reviewPeriodEnd != null ? reviewPeriodEnd : start.plusDays(90);
        LocalDateTime due = dueDate != null ? dueDate : end;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Review period end must be after start");
        }

        AccessReview review = accessReviewRepository.save(AccessReview.builder()
                .workspaceId(workspace.getId())
                .name(name.trim())
                .reviewPeriodStart(start)
                .reviewPeriodEnd(end)
                .dueDate(due)
                .createdBy(securityService.getCurrentUser().getId())
                .status("pending")
                .build());

        var owner = workspace.getOwner();
        accessReviewItemRepository.save(AccessReviewItem.builder()
                .accessReviewId(review.getId())
                .userId(owner.getId())
                .role(Role.OWNER.name())
                .status("pending")
                .build());

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspace.getId());
        for (WorkspaceMember member : members) {
            if (member.getUser().getId().equals(owner.getId())) {
                continue;
            }
            accessReviewItemRepository.save(AccessReviewItem.builder()
                    .accessReviewId(review.getId())
                    .userId(member.getUser().getId())
                    .role(member.getRole().name())
                    .status("pending")
                    .build());
        }

        log.info("Created access review {} for workspace {} with {} items",
                review.getId(), workspace.getId(), members.size() + 1);
        return review;
    }

    @Transactional
    public AccessReviewItem reviewItem(Long itemId, String status, String comment) {
        AccessReviewItem item = accessReviewItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Review item not found"));
        AccessReview review = accessReviewRepository.findById(item.getAccessReviewId())
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        requireAdmin(review.getWorkspaceId());

        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (!ITEM_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Status must be one of APPROVED, REVOKED, ESCALATED");
        }

        item.setStatus(normalized);
        item.setReviewedBy(securityService.getCurrentUser().getId());
        item.setReviewedAt(LocalDateTime.now());
        item.setComment(comment);
        return accessReviewItemRepository.save(item);
    }

    @Transactional
    public AccessReview completeReview(Long reviewId) {
        AccessReview review = accessReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        requireAdmin(review.getWorkspaceId());
        review.setStatus("completed");
        return accessReviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<AccessReview> getWorkspaceReviews(Long workspaceId) {
        requireMember(workspaceId);
        return accessReviewRepository.findByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<AccessReviewItem> getReviewItems(Long reviewId) {
        AccessReview review = accessReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        requireMember(review.getWorkspaceId());
        return accessReviewItemRepository.findByAccessReviewId(reviewId);
    }

    private Workspace requireAdmin(Long workspaceId) {
        User user = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        Role role = workspaceService.getEffectiveRole(workspace, user);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw new RuntimeException("Only workspace owner or admin can manage access reviews");
        }
        return workspace;
    }

    private void requireMember(Long workspaceId) {
        User user = securityService.getCurrentUser();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        if (workspaceService.getEffectiveRole(workspace, user) == null) {
            throw new ResourceNotFoundException("Workspace not found");
        }
    }
}
