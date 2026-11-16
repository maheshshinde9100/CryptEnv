package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.AccessReview;
import com.maheshshinde.CryptEnv.model.AccessReviewItem;
import com.maheshshinde.CryptEnv.service.AccessReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/access-reviews")
@RequiredArgsConstructor
@Tag(name = "Access Reviews", description = "Quarterly access-certification review APIs")
public class AccessReviewController {

    private final AccessReviewService accessReviewService;

    @PostMapping
    @Operation(summary = "Create a new access review")
    public ResponseEntity<AccessReview> createReview(@RequestBody Map<String, Object> body) {
        Long workspaceId = Long.valueOf(body.get("workspaceId").toString());
        String name = (String) body.get("name");
        LocalDateTime reviewPeriodStart = LocalDateTime.parse(body.get("reviewPeriodStart").toString());
        LocalDateTime reviewPeriodEnd = LocalDateTime.parse(body.get("reviewPeriodEnd").toString());
        LocalDateTime dueDate = LocalDateTime.parse(body.get("dueDate").toString());
        Long createdBy = Long.valueOf(body.get("createdBy").toString());
        
        AccessReview review = accessReviewService.createAccessReview(workspaceId, name, reviewPeriodStart,
                reviewPeriodEnd, dueDate, createdBy);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get all access reviews for a workspace")
    public ResponseEntity<List<AccessReview>> getWorkspaceReviews(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(accessReviewService.getWorkspaceReviews(workspaceId));
    }

    @GetMapping("/{reviewId}/items")
    @Operation(summary = "Get all items for an access review")
    public ResponseEntity<List<AccessReviewItem>> getReviewItems(@PathVariable Long reviewId) {
        return ResponseEntity.ok(accessReviewService.getReviewItems(reviewId));
    }

    @PostMapping("/items/{itemId}/review")
    @Operation(summary = "Review an access review item")
    public ResponseEntity<AccessReviewItem> reviewItem(@PathVariable Long itemId,
                                                        @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        Long reviewedBy = Long.valueOf(body.get("reviewedBy").toString());
        String comment = (String) body.get("comment");
        
        AccessReviewItem item = accessReviewService.reviewItem(itemId, status, reviewedBy, comment);
        return ResponseEntity.ok(item);
    }

    @PostMapping("/{reviewId}/complete")
    @Operation(summary = "Complete an access review")
    public ResponseEntity<AccessReview> completeReview(@PathVariable Long reviewId) {
        AccessReview review = accessReviewService.completeReview(reviewId);
        return ResponseEntity.ok(review);
    }
}
