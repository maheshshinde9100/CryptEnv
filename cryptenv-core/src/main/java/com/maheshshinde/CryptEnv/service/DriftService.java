package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.DriftFinding;
import com.maheshshinde.CryptEnv.model.DriftScan;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.ProviderSyncState;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.DriftFindingRepository;
import com.maheshshinde.CryptEnv.repository.DriftScanRepository;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.ProviderSyncStateRepository;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.security.EncryptionException;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Drift Detection (§9): fingerprint-only comparison between CryptEnv secrets and the values
 * last pushed to external providers. Classifies each secret as IN_SYNC, MODIFIED, MISSING or
 * STALE. No plaintext ever leaves the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriftService {

    private final DriftScanRepository scanRepository;
    private final DriftFindingRepository findingRepository;
    private final ProviderSyncStateRepository syncStateRepository;
    private final SecretRepository secretRepository;
    private final EnvironmentRepository environmentRepository;
    private final WorkspaceService workspaceService;
    private final SecurityService securityService;
    private final EncryptionService encryptionService;
    private final ProviderSyncService providerSyncService;
    private final ObjectMapper objectMapper;

    @Value("${cryptenv.drift.stale-days:30}")
    int staleThresholdDays;

    // ------------------------------------------------------------------ scans

    @Transactional
    public Map<String, Object> runScan(Long environmentId) {
        User user = securityService.getCurrentUser();
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + environmentId));
        validateOwnership(environment, user);
        return executeScan(environment, user, DriftScan.Trigger.MANUAL);
    }

    @Scheduled(cron = "${cryptenv.drift.cron:0 30 3 * * *}")
    @Transactional
    public void scheduledScan() {
        for (Environment environment : environmentRepository.findAll()) {
            try {
                User owner = environment.getWorkspace().getOwner();
                executeScan(environment, owner, DriftScan.Trigger.SCHEDULED);
            } catch (Exception e) {
                log.warn("Scheduled drift scan failed for environment {}: {}", environment.getId(), e.getMessage());
            }
        }
    }

    private Map<String, Object> executeScan(Environment environment, User user, DriftScan.Trigger trigger) {
        DriftScan scan = scanRepository.save(DriftScan.builder()
                .user(user)
                .workspaceId(environment.getWorkspace().getId())
                .environmentId(environment.getId())
                .trigger(trigger)
                .status(DriftScan.Status.RUNNING)
                .startedAt(LocalDateTime.now())
                .build());

        List<Map<String, Object>> findings = new ArrayList<>();
        int inSync = 0;
        int stale = 0;
        int modified = 0;
        int missing = 0;

        try {
            Map<String, Secret> secretsByKey = secretRepository.findByEnvironmentId(environment.getId()).stream()
                    .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                    .collect(Collectors.toMap(Secret::getKey, s -> s, (a, b) -> a));

            List<ProviderSyncState> states = syncStateRepository.findByEnvironmentId(environment.getId());
            Map<String, List<ProviderSyncState>> groups = states.stream()
                    .collect(Collectors.groupingBy(s -> s.getProvider() + "|" + s.getTarget()));

            LocalDateTime staleCutoff = LocalDateTime.now().minusDays(staleThresholdDays);

            for (Map.Entry<String, List<ProviderSyncState>> group : groups.entrySet()) {
                List<ProviderSyncState> groupStates = group.getValue();
                String provider = groupStates.get(0).getProvider();
                String target = groupStates.get(0).getTarget();
                Map<String, ProviderSyncState> stateByKey = groupStates.stream()
                        .collect(Collectors.toMap(ProviderSyncState::getSecretKey, s -> s, (a, b) -> a));

                for (Secret secret : secretsByKey.values()) {
                    String currentFingerprint = ensureFingerprint(secret);
                    ProviderSyncState state = stateByKey.get(secret.getKey());

                    DriftFinding.Classification classification;
                    String detail;
                    if (state == null || state.getLastFingerprint() == null) {
                        classification = DriftFinding.Classification.MISSING;
                        detail = "Not pushed to " + provider + "/" + target;
                    } else if (!state.getLastFingerprint().equals(currentFingerprint)) {
                        classification = DriftFinding.Classification.MODIFIED;
                        detail = "Value changed since last push to " + provider + "/" + target;
                    } else if (state.getLastSyncedAt() != null && state.getLastSyncedAt().isBefore(staleCutoff)) {
                        classification = DriftFinding.Classification.STALE;
                        detail = "Last synced to " + provider + "/" + target + " more than " + staleThresholdDays + " days ago";
                    } else {
                        classification = DriftFinding.Classification.IN_SYNC;
                        detail = "In sync with " + provider + "/" + target;
                    }

                    switch (classification) {
                        case IN_SYNC -> inSync++;
                        case STALE -> stale++;
                        case MODIFIED -> modified++;
                        case MISSING -> missing++;
                    }

                    DriftFinding saved = findingRepository.save(DriftFinding.builder()
                            .scan(scan)
                            .user(user)
                            .environmentId(environment.getId())
                            .secretKey(secret.getKey())
                            .provider(provider)
                            .target(target)
                            .classification(classification)
                            .expectedFingerprint(state != null ? state.getLastFingerprint() : null)
                            .actualFingerprint(currentFingerprint)
                            .detail(detail)
                            .detectedAt(LocalDateTime.now())
                            .build());
                    findings.add(toFindingView(saved));
                }

                // Secrets that exist on the provider (state present) but were deleted in CryptEnv.
                for (ProviderSyncState state : stateByKey.values()) {
                    if (!secretsByKey.containsKey(state.getSecretKey())) {
                        missing++;
                        DriftFinding saved = findingRepository.save(DriftFinding.builder()
                                .scan(scan)
                                .user(user)
                                .environmentId(environment.getId())
                                .secretKey(state.getSecretKey())
                                .provider(provider)
                                .target(target)
                                .classification(DriftFinding.Classification.MISSING)
                                .expectedFingerprint(state.getLastFingerprint())
                                .actualFingerprint(null)
                                .detail("Secret no longer exists in CryptEnv but is still pushed to " + provider + "/" + target)
                                .detectedAt(LocalDateTime.now())
                                .build());
                        findings.add(toFindingView(saved));
                    }
                }
            }

            scan.setTotal(findings.size());
            scan.setInSync(inSync);
            scan.setStale(stale);
            scan.setModified(modified);
            scan.setMissing(missing);
            scan.setStatus(DriftScan.Status.COMPLETED);
            scan.setCompletedAt(LocalDateTime.now());
            if (groups.isEmpty()) {
                scan.setDetail("No provider syncs recorded for this environment yet");
            }
            scanRepository.save(scan);
        } catch (Exception e) {
            log.error("Drift scan {} failed", scan.getId(), e);
            scan.setStatus(DriftScan.Status.FAILED);
            scan.setDetail(truncate(e.getMessage()));
            scan.setCompletedAt(LocalDateTime.now());
            scanRepository.save(scan);
        }

        Map<String, Object> view = toScanView(scan);
        view.put("findings", findings);
        return view;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listScans() {
        User user = securityService.getCurrentUser();
        return scanRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toScanView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getScan(Long scanId) {
        User user = securityService.getCurrentUser();
        DriftScan scan = scanRepository.findByIdAndUserId(scanId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Drift scan not found: " + scanId));
        Map<String, Object> view = toScanView(scan);
        view.put("findings", findingRepository.findByScanIdOrderByClassificationAscSecretKeyAsc(scanId).stream()
                .map(this::toFindingView)
                .collect(Collectors.toList()));
        return view;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> latestFindings(Long environmentId) {
        User user = securityService.getCurrentUser();
        List<DriftScan> scans = environmentId != null
                ? scanRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId)
                : scanRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        DriftScan latest = scans.stream()
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElse(null);
        if (latest == null) {
            return List.of();
        }
        return findingRepository.findByScanIdOrderByClassificationAscSecretKeyAsc(latest.getId()).stream()
                .map(this::toFindingView)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ remediation

    @Transactional
    public Map<String, Object> remediate(Long scanId) {
        User user = securityService.getCurrentUser();
        DriftScan scan = scanRepository.findByIdAndUserId(scanId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Drift scan not found: " + scanId));

        int queued = 0;
        List<Map<String, Object>> actions = new ArrayList<>();
        for (DriftFinding finding : findingRepository.findByScanIdOrderByClassificationAscSecretKeyAsc(scanId)) {
            if (finding.getClassification() == DriftFinding.Classification.IN_SYNC || finding.getProvider() == null) {
                continue;
            }
            try {
                Map<String, Object> job = providerSyncService.enqueueSync(
                        finding.getProvider(),
                        finding.getEnvironmentId(),
                        finding.getTarget(),
                        List.of(finding.getSecretKey()));
                queued++;
                actions.add(Map.of("secretKey", finding.getSecretKey(),
                        "provider", finding.getProvider(),
                        "status", "QUEUED",
                        "jobId", job.get("id")));
            } catch (Exception e) {
                actions.add(Map.of("secretKey", finding.getSecretKey(),
                        "provider", finding.getProvider(),
                        "status", "SKIPPED",
                        "error", String.valueOf(e.getMessage())));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanId", scan.getId());
        result.put("queued", queued);
        result.put("actions", actions);
        return result;
    }

    // ------------------------------------------------------------------ reports

    @Transactional(readOnly = true)
    public String exportReport(Long scanId, String format) {
        User user = securityService.getCurrentUser();
        DriftScan scan = scanRepository.findByIdAndUserId(scanId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Drift scan not found: " + scanId));
        List<DriftFinding> findings = findingRepository.findByScanIdOrderByClassificationAscSecretKeyAsc(scanId);

        String normalized = format == null ? "json" : format.toLowerCase();
        if ("csv".equals(normalized)) {
            StringBuilder sb = new StringBuilder("scan_id,environment_id,secret_key,provider,target,classification,expected_fingerprint,actual_fingerprint,detected_at\n");
            for (DriftFinding f : findings) {
                sb.append(scan.getId()).append(',')
                        .append(f.getEnvironmentId()).append(',')
                        .append(csv(f.getSecretKey())).append(',')
                        .append(csv(f.getProvider())).append(',')
                        .append(csv(f.getTarget())).append(',')
                        .append(f.getClassification().name()).append(',')
                        .append(csv(f.getExpectedFingerprint())).append(',')
                        .append(csv(f.getActualFingerprint())).append(',')
                        .append(f.getDetectedAt()).append('\n');
            }
            return sb.toString();
        }

        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("scan", toScanView(scan));
            report.put("findings", findings.stream().map(this::toFindingView).collect(Collectors.toList()));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize drift report", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private String ensureFingerprint(Secret secret) {
        if (secret.getContentFingerprint() != null && !secret.getContentFingerprint().isBlank()) {
            return secret.getContentFingerprint();
        }
        try {
            String plaintext = decrypt(secret);
            if (plaintext == null) {
                return null;
            }
            String fingerprint = EncryptionService.fingerprint(plaintext);
            secret.setContentFingerprint(fingerprint);
            secretRepository.save(secret);
            return fingerprint;
        } catch (Exception e) {
            log.warn("Could not fingerprint secret {}: {}", secret.getKey(), e.getMessage());
            return null;
        }
    }

    private String decrypt(Secret secret) {
        if (!Boolean.TRUE.equals(secret.getEncrypted())) {
            return secret.getValue() != null ? secret.getValue() : secret.getEncryptedValue();
        }
        Environment environment = secret.getEnvironment();
        String storedValue = secret.getValue() != null ? secret.getValue() : secret.getEncryptedValue();
        String workspaceKey = workspaceService.getDecryptedWorkspaceKey(environment.getWorkspace().getId());
        String aad = EncryptionService.aadFor(
                environment.getWorkspace().getId(),
                environment.getId(),
                secret.getKey(),
                secret.getVersion() != null ? secret.getVersion() : 1);
        try {
            return encryptionService.decryptWithKey(storedValue, workspaceKey, aad);
        } catch (EncryptionException aadMiss) {
            return encryptionService.decryptWithKey(storedValue, workspaceKey);
        }
    }

    private void validateOwnership(Environment environment, User user) {
        Long ownerId = environment.getWorkspace().getOwner().getId();
        boolean isOwner = ownerId.equals(user.getId());
        boolean isMember = environment.getWorkspace().getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()));
        if (!isOwner && !isMember) {
            throw new ResourceNotFoundException("Environment not found with id: " + environment.getId());
        }
    }

    private Map<String, Object> toScanView(DriftScan scan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", scan.getId());
        view.put("workspaceId", scan.getWorkspaceId());
        view.put("environmentId", scan.getEnvironmentId());
        view.put("trigger", scan.getTrigger().name());
        view.put("status", scan.getStatus().name());
        view.put("total", scan.getTotal());
        view.put("inSync", scan.getInSync());
        view.put("stale", scan.getStale());
        view.put("modified", scan.getModified());
        view.put("missing", scan.getMissing());
        view.put("detail", scan.getDetail());
        view.put("startedAt", scan.getStartedAt());
        view.put("completedAt", scan.getCompletedAt());
        view.put("createdAt", scan.getCreatedAt());
        return view;
    }

    private Map<String, Object> toFindingView(DriftFinding finding) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", finding.getId());
        view.put("environmentId", finding.getEnvironmentId());
        view.put("secretKey", finding.getSecretKey());
        view.put("provider", finding.getProvider());
        view.put("target", finding.getTarget());
        view.put("classification", finding.getClassification().name());
        view.put("expectedFingerprint", finding.getExpectedFingerprint());
        view.put("actualFingerprint", finding.getActualFingerprint());
        view.put("detail", finding.getDetail());
        view.put("detectedAt", finding.getDetectedAt());
        return view;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 950 ? message.substring(0, 950) : message;
    }
}
