package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.WebAuthnCredential;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WebAuthnCredentialRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.CredentialRegistration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebAuthnService implements CredentialRepository {

    private final WebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;

    @Override
    public Set<RegisteredCredential> getCredentialIdsForUsername(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Set.of();
        }
        return credentialRepository.findByUserId(user.getId()).stream()
                .map(cred -> RegisteredCredential.builder()
                        .credentialId(ByteArray.fromBase64(cred.getCredentialId()))
                        .userHandle(ByteArray.fromBase64(cred.getUserHandle()))
                        .publicKeyCose(ByteArray.fromBase64(cred.getPublicKey()))
                        .signatureCount(cred.getSignatureCount())
                        .build())
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> ByteArray.fromBase64(user.getId().toString()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        try {
            Long userId = Long.parseLong(userHandle.getBase64());
            return userRepository.findById(userId).map(User::getUsername);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Optional<CredentialRegistration> lookup(ByteArray credentialId, ByteArray userHandle) {
        Optional<WebAuthnCredential> credOpt = credentialRepository.findByCredentialId(credentialId.getBase64());
        if (credOpt.isEmpty()) {
            return Optional.empty();
        }
        WebAuthnCredential cred = credOpt.get();
        User user = userRepository.findById(cred.getUserId()).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(CredentialRegistration.builder()
                .credential(RegisteredCredential.builder()
                        .credentialId(ByteArray.fromBase64(cred.getCredentialId()))
                        .userHandle(ByteArray.fromBase64(cred.getUserHandle()))
                        .publicKeyCose(ByteArray.fromBase64(cred.getPublicKey()))
                        .signatureCount(cred.getSignatureCount())
                        .build())
                .userHandle(ByteArray.fromBase64(cred.getUserHandle()))
                .username(user.getUsername())
                .build());
    }

    @Override
    @Transactional
    public Set<CredentialRegistration> lookupAll(ByteArray credentialId) {
        Optional<WebAuthnCredential> credOpt = credentialRepository.findByCredentialId(credentialId.getBase64());
        if (credOpt.isEmpty()) {
            return Set.of();
        }
        WebAuthnCredential cred = credOpt.get();
        User user = userRepository.findById(cred.getUserId()).orElse(null);
        if (user == null) {
            return Set.of();
        }
        return Set.of(CredentialRegistration.builder()
                .credential(RegisteredCredential.builder()
                        .credentialId(ByteArray.fromBase64(cred.getCredentialId()))
                        .userHandle(ByteArray.fromBase64(cred.getUserHandle()))
                        .publicKeyCose(ByteArray.fromBase64(cred.getPublicKey()))
                        .signatureCount(cred.getSignatureCount())
                        .build())
                .userHandle(ByteArray.fromBase64(cred.getUserHandle()))
                .username(user.getUsername())
                .build());
    }

    @Transactional
    public void addCredential(Long userId, String credentialId, String publicKey, String userHandle, 
                             String aaguid, String attestationType, String deviceName) {
        WebAuthnCredential credential = WebAuthnCredential.builder()
                .userId(userId)
                .credentialId(credentialId)
                .publicKey(publicKey)
                .userHandle(userHandle)
                .aaguid(aaguid)
                .attestationType(attestationType)
                .deviceName(deviceName)
                .signatureCount(0L)
                .build();
        credentialRepository.save(credential);
    }

    @Transactional
    public void updateSignatureCount(String credentialId, long signatureCount) {
        credentialRepository.findByCredentialId(credentialId).ifPresent(cred -> {
            cred.setSignatureCount(signatureCount);
            credentialRepository.save(cred);
        });
    }

    @Transactional
    public void removeCredential(String credentialId) {
        credentialRepository.findByCredentialId(credentialId).ifPresent(credentialRepository::delete);
    }
}
