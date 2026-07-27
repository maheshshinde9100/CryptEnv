package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SecretService {

    private final SecretRepository secretRepository;

    @Transactional
    public Secret createSecret(Secret secret) {
        if (secretRepository.existsByKey(secret.getKey())) {
            throw new ResourceAlreadyExistsException("Secret with key already exists: " + secret.getKey());
        }
        return secretRepository.save(secret);
    }

    @Transactional(readOnly = true)
    public List<Secret> getAllSecrets() {
        return secretRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Secret getSecretByKey(String key) {
        return secretRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Secret not found with key: " + key));
    }

    @Transactional
    public Secret updateSecret(String key, Secret secret) {
        Secret existingSecret = getSecretByKey(key);
        existingSecret.setValue(secret.getValue());
        existingSecret.setDescription(secret.getDescription());
        existingSecret.setVersion(existingSecret.getVersion() + 1);
        return secretRepository.save(existingSecret);
    }

    @Transactional
    public void deleteSecret(String key) {
        Secret secret = getSecretByKey(key);
        secretRepository.delete(secret);
    }
}
