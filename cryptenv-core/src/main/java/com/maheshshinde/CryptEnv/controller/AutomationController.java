package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.AutomationService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation rules (§10): <em>ON event WHERE condition DO action</em>, with a per-rule loop guard
 * and an execution log.
 */
@RestController
@RequestMapping("/api/automations")
@RequiredArgsConstructor
@Tag(name = "Automations", description = "Event-driven automation rules and their run history")
public class AutomationController {

    private final AutomationService automationService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @GetMapping
    @Operation(summary = "List the caller's automation rules")
    public ResponseEntity<List<Map<String, Object>>> rules() {
        return ResponseEntity.ok(automationService.listRules());
    }

    @PostMapping
    @Operation(summary = "Create an automation rule")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        Map<String, Object> rule = automationService.createRule(
                stringValue(body, "name"),
                stringValue(body, "description"),
                longValue(body, "workspaceId"),
                stringValue(body, "eventType"),
                mapValue(body.get("conditions")),
                stringValue(body, "actionType"),
                mapValue(body.get("actionConfig")),
                booleanValue(body, "enabled"));
        auditLogService.logEvent(securityService.getCurrentUser(), "AUTOMATION_CREATE", "AUTOMATION_RULE",
                String.valueOf(rule.get("id")), true, request, "Created automation rule " + body.get("name"));
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an automation rule")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        Map<String, Object> rule = automationService.updateRule(
                id,
                stringValue(body, "name"),
                stringValue(body, "description"),
                longValue(body, "workspaceId"),
                stringValue(body, "eventType"),
                body.containsKey("conditions") ? mapValue(body.get("conditions")) : null,
                stringValue(body, "actionType"),
                body.containsKey("actionConfig") ? mapValue(body.get("actionConfig")) : null,
                booleanValue(body, "enabled"));
        auditLogService.logEvent(securityService.getCurrentUser(), "AUTOMATION_UPDATE", "AUTOMATION_RULE",
                id.toString(), true, request, "Updated automation rule " + id);
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an automation rule and its run history")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        automationService.deleteRule(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "AUTOMATION_DELETE", "AUTOMATION_RULE",
                id.toString(), true, request, "Deleted automation rule " + id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/runs")
    @Operation(summary = "List automation runs (optionally filtered by rule)")
    public ResponseEntity<Page<Map<String, Object>>> runs(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(automationService.listRuns(ruleId, PageRequest.of(page, Math.min(size, 100))));
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

    private Boolean booleanValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof Boolean b ? b : null;
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
