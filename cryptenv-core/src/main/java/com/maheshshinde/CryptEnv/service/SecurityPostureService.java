package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Computes an explainable security posture score without reading secret plaintext. */
@Service
@RequiredArgsConstructor
public class SecurityPostureService {
    private final SecretRepository secretRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    @Transactional(readOnly = true)
    public Map<String, Object> current() {
        User user = securityService.getCurrentUser();
        List<Secret> secrets = secretRepository.findByEnvironmentWorkspaceOwnerOrMemberId(user.getId()).stream()
                .filter(secret -> !Boolean.TRUE.equals(secret.getIsDeleted())).toList();
        LocalDateTime now = LocalDateTime.now();
        long expired = secrets.stream().filter(s -> s.getExpiresAt() != null && !s.getExpiresAt().isAfter(now)).count();
        long inactive = secrets.stream().filter(s -> !Boolean.TRUE.equals(s.getIsActive())).count();
        long overdueRotation = secrets.stream().filter(s -> Boolean.TRUE.equals(s.getAutoRotate())
                && s.getNextRotationAt() != null && !s.getNextRotationAt().isAfter(now)).count();
        long noRotationPolicy = secrets.stream().filter(s -> !Boolean.TRUE.equals(s.getAutoRotate())
                && s.getRotationIntervalDays() == null).count();
        long mfaMissing = userRepository.findAll().stream()
                .filter(candidate -> candidate.getId().equals(user.getId()))
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getMfaEnabled())).count();

        int penalty = (int) Math.min(35, expired * 10)
                + (int) Math.min(20, overdueRotation * 5)
                + (int) Math.min(15, noRotationPolicy * 2)
                + (mfaMissing > 0 ? 15 : 0)
                + (inactive > 0 ? 3 : 0);
        int score = Math.max(0, 100 - penalty);
        List<Map<String, Object>> remediation = new ArrayList<>();
        if (mfaMissing > 0) remediation.add(item("Enable MFA", "Protect the account that manages these secrets.", "HIGH"));
        if (expired > 0) remediation.add(item("Resolve expired secrets", expired + " secret(s) have expired.", "HIGH"));
        if (overdueRotation > 0) remediation.add(item("Rotate overdue secrets", overdueRotation + " secret(s) are past their rotation date.", "HIGH"));
        if (noRotationPolicy > 0) remediation.add(item("Set rotation policies", noRotationPolicy + " secret(s) have no rotation policy.", "MEDIUM"));
        if (inactive > 0) remediation.add(item("Review inactive secrets", inactive + " inactive secret(s) should be removed or restored.", "LOW"));
        if (remediation.isEmpty()) remediation.add(item("Maintain your posture", "No immediate account-level remediation is required.", "INFO"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("grade", score >= 90 ? "A" : score >= 75 ? "B" : score >= 60 ? "C" : score >= 40 ? "D" : "F");
        result.put("metrics", Map.of("totalSecrets", secrets.size(), "expiredSecrets", expired, "inactiveSecrets", inactive,
                "overdueRotation", overdueRotation, "withoutRotationPolicy", noRotationPolicy, "mfaEnabled", mfaMissing == 0));
        result.put("remediation", remediation);
        return result;
    }

    private Map<String, Object> item(String title, String description, String priority) {
        return Map.of("title", title, "description", description, "priority", priority);
    }
}
