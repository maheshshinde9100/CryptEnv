package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Notifications (§10): the in-app notification feed and per-user channel preferences
 * (in-app / email / Slack / Discord).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification feed and channel preferences")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List the caller's notifications (newest first)")
    public ResponseEntity<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(notificationService.list(unreadOnly, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count the caller's unread notifications")
    public ResponseEntity<Map<String, Object>> unreadCount() {
        return ResponseEntity.ok(notificationService.unreadCount());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all of the caller's notifications as read")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        return ResponseEntity.ok(notificationService.markAllRead());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    @Operation(summary = "Read the caller's notification preferences")
    public ResponseEntity<Map<String, Object>> preferences() {
        return ResponseEntity.ok(notificationService.getPreferences());
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update the caller's notification preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(notificationService.updatePreferences(body));
    }

    @PostMapping("/test/{channel}")
    @Operation(summary = "Send a test notification through a channel (email | slack | discord)")
    public ResponseEntity<Map<String, Object>> test(@PathVariable String channel) {
        return ResponseEntity.ok(notificationService.testChannel(channel));
    }
}
