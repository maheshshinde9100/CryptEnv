package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.AuditLogService;
import com.maheshshinde.CryptEnv.service.DriftService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Drift Detection (§9): fingerprint-only comparison of CryptEnv secrets against values
 * last pushed to external providers.
 */
@RestController
@RequestMapping("/api/drift")
@RequiredArgsConstructor
@Tag(name = "Drift Detection", description = "Detect divergence between CryptEnv and pushed providers")
public class DriftController {

    private final DriftService driftService;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    @PostMapping("/scan")
    @Operation(summary = "Run a drift scan for an environment")
    public ResponseEntity<Map<String, Object>> scan(@RequestParam Long environmentId, HttpServletRequest request) {
        Map<String, Object> scan = driftService.runScan(environmentId);
        auditLogService.logEvent(securityService.getCurrentUser(), "DRIFT_SCAN", "ENVIRONMENT",
                environmentId.toString(), true, request,
                "Drift scan: modified=" + scan.get("modified") + " missing=" + scan.get("missing")
                        + " stale=" + scan.get("stale"));
        return ResponseEntity.ok(scan);
    }

    @GetMapping("/scans")
    @Operation(summary = "List this caller's drift scans")
    public ResponseEntity<List<Map<String, Object>>> scans() {
        return ResponseEntity.ok(driftService.listScans());
    }

    @GetMapping("/scans/{id}")
    @Operation(summary = "Get a drift scan with its findings")
    public ResponseEntity<Map<String, Object>> scanDetail(@PathVariable Long id) {
        return ResponseEntity.ok(driftService.getScan(id));
    }

    @GetMapping("/findings")
    @Operation(summary = "Latest findings for an environment (or all latest for the caller)")
    public ResponseEntity<List<Map<String, Object>>> findings(@RequestParam(required = false) Long environmentId) {
        return ResponseEntity.ok(driftService.latestFindings(environmentId));
    }

    @GetMapping(value = "/scans/{id}/report", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Export an attestation report as JSON or CSV")
    public ResponseEntity<String> report(@PathVariable Long id, @RequestParam(defaultValue = "json") String format) {
        return ResponseEntity.ok(driftService.exportReport(id, format));
    }

    @PostMapping("/scans/{id}/remediate")
    @Operation(summary = "Queue re-syncs for the drifting secrets of a scan")
    public ResponseEntity<Map<String, Object>> remediate(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = driftService.remediate(id);
        auditLogService.logEvent(securityService.getCurrentUser(), "DRIFT_REMEDIATE", "DRIFT_SCAN",
                id.toString(), true, request, "Queued " + result.get("queued") + " remediation syncs");
        return ResponseEntity.ok(result);
    }
}
