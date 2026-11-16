package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Four-eyes approval (§10) for destructive actions. A request raised by one user must be
 * approved or rejected by a <em>different</em> eligible owner/admin before it is executed.
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Four-eyes approval workflow for destructive actions")
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/supported")
    @Operation(summary = "List approval-gated actions available on this deployment")
    public ResponseEntity<List<Map<String, Object>>> supported() {
        return ResponseEntity.ok(approvalService.supportedActions());
    }

    @GetMapping
    @Operation(summary = "List approval requests raised by the caller")
    public ResponseEntity<List<Map<String, Object>>> mine() {
        return ResponseEntity.ok(approvalService.listMine());
    }

    @GetMapping("/pending")
    @Operation(summary = "List pending requests awaiting the caller's decision")
    public ResponseEntity<List<Map<String, Object>>> pending() {
        return ResponseEntity.ok(approvalService.listPendingForMe());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single approval request")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return ResponseEntity.ok(approvalService.get(id));
    }

    @PostMapping
    @Operation(summary = "Raise an approval request for a destructive action")
    public ResponseEntity<Map<String, Object>> request(@RequestBody Map<String, Object> body) {
        Map<String, Object> view = approvalService.request(
                stringValue(body, "action"),
                longValue(body, "workspaceId"),
                stringValue(body, "resourceType"),
                stringValue(body, "resourceId"),
                mapValue(body.get("payload")),
                stringValue(body, "reason"),
                intValue(body, "ttlHours"));
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending request and execute its action")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(approvalService.decide(id, true, body != null ? stringValue(body, "note") : null));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(approvalService.decide(id, false, body != null ? stringValue(body, "note") : null));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel the caller's own pending request")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(approvalService.cancel(id));
    }

    private String stringValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null ? value.toString() : null;
    }

    private Long longValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return null;
    }
}
