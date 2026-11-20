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
    @Operation(summary = "Create a new access review (owner/admin). Dates default to a 90-day period.")
    public ResponseEntity<AccessReview> createReview(@RequestBody Map<String, Object> body) {
        Long workspaceId = Long.valueOf(String.valueOf(body.get("workspaceId")));
        String name = body.get("name") == null ? null : body.get("name").toString();
        AccessReview review = accessReviewService.createAccessReview(
                workspaceId,
                name,
                parseDate(body.get("reviewPeriodStart")),
                parseDate(body.get("reviewPeriodEnd")),
                parseDate(body.get("dueDate")));
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
    @Operation(summary = "Approve, revoke, or escalate an access review item (owner/admin)")
    public ResponseEntity<AccessReviewItem> reviewItem(@PathVariable Long itemId,
                                                       @RequestBody Map<String, Object> body) {
        String status = body.get("status") == null ? null : body.get("status").toString();
        String comment = body.get("comment") == null ? null : body.get("comment").toString();
        return ResponseEntity.ok(accessReviewService.reviewItem(itemId, status, comment));
    }

    @PostMapping("/{reviewId}/complete")
    @Operation(summary = "Complete an access review (owner/admin)")
    public ResponseEntity<AccessReview> completeReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(accessReviewService.completeReview(reviewId));
    }

    private LocalDateTime parseDate(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.toString());
    }
}
