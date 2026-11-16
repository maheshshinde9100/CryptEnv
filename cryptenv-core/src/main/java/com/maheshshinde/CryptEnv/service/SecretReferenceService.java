package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.SecretReference;
import com.maheshshinde.CryptEnv.repository.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretReferenceService {

    private final SecretReferenceRepository secretReferenceRepository;
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)\\}");

    @Transactional
    public SecretReference createReference(Long secretId, String referenceKey, Long targetSecretId, Long targetEnvironmentId) {
        SecretReference reference = SecretReference.builder()
                .secretId(secretId)
                .referenceKey(referenceKey)
                .targetSecretId(targetSecretId)
                .targetEnvironmentId(targetEnvironmentId)
                .build();
        return secretReferenceRepository.save(reference);
    }

    public List<SecretReference> getSecretReferences(Long secretId) {
        return secretReferenceRepository.findBySecretId(secretId);
    }

    @Transactional
    public void deleteReferences(Long secretId) {
        secretReferenceRepository.deleteBySecretId(secretId);
    }

    public String resolveReferences(String value) {
        if (value == null) return null;
        Matcher matcher = REFERENCE_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            // In a real implementation, this would fetch the target secret value
            // For now, we'll leave the reference as-is
            matcher.appendReplacement(result, "\\${" + key + "}");
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
