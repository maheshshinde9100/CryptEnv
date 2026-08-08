package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretService {

    private final SecretRepository secretRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public Secret createSecret(Secret secret) {
        if (secretRepository.existsByKey(secret.getKey())) {
            throw new ResourceAlreadyExistsException("Secret with key already exists: " + secret.getKey());
        }
        
        try {
            if (Boolean.TRUE.equals(secret.getEncrypted())) {
                secret.setValue(encryptionService.encrypt(secret.getValue()));
            }
        } catch (Exception e) {
            log.error("Failed to encrypt secret", e);
            throw new RuntimeException("Failed to encrypt secret", e);
        }
        
        Secret savedSecret = secretRepository.save(secret);
        return decryptSecret(savedSecret);
    }

    @Transactional(readOnly = true)
    public List<Secret> getAllSecrets() {
        return secretRepository.findAll().stream()
                .map(this::decryptSecret)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Secret getSecretByKey(String key) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        return decryptSecret(secret);
    }

    @Transactional
    public Secret updateSecret(String key, Secret secret) {
        Secret existingSecret = secretRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
                
        try {
            if (Boolean.TRUE.equals(existingSecret.getEncrypted())) {
                existingSecret.setValue(encryptionService.encrypt(secret.getValue()));
            } else {
                existingSecret.setValue(secret.getValue());
            }
        } catch (Exception e) {
            log.error("Failed to encrypt secret on update", e);
            throw new RuntimeException("Failed to encrypt secret on update", e);
        }
        
        existingSecret.setDescription(secret.getDescription());
        existingSecret.setVersion(existingSecret.getVersion() + 1);
        
        Secret savedSecret = secretRepository.save(existingSecret);
        return decryptSecret(savedSecret);
    }

    @Transactional
    public void deleteSecret(String key) {
        Secret secret = secretRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
        secretRepository.delete(secret);
    }
    
    private Secret decryptSecret(Secret secret) {
        if (Boolean.TRUE.equals(secret.getEncrypted()) && secret.getValue() != null) {
            try {
                // Create a copy so we don't modify the entity in the persistence context
                Secret decrypted = new Secret();
                decrypted.setId(secret.getId());
                decrypted.setKey(secret.getKey());
                decrypted.setValue(encryptionService.decrypt(secret.getValue()));
                decrypted.setEnvironment(secret.getEnvironment());
                decrypted.setDescription(secret.getDescription());
                decrypted.setEncrypted(secret.getEncrypted());
                decrypted.setVersioned(secret.getVersioned());
                decrypted.setVersion(secret.getVersion());
                decrypted.setCreatedAt(secret.getCreatedAt());
                decrypted.setUpdatedAt(secret.getUpdatedAt());
                return decrypted;
            } catch (Exception e) {
                log.error("Failed to decrypt secret: {}", secret.getKey(), e);
                // Return original if decryption fails (might be plain text from old versions)
            }
        }
        return secret;
    }
}
