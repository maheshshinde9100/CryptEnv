package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.DeviceCode;
import com.maheshshinde.CryptEnv.repository.DeviceCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCodeService {

    private static final int DEVICE_CODE_LENGTH = 32;
    private static final int USER_CODE_LENGTH = 8;
    private static final int EXPIRY_MINUTES = 15;
    private final DeviceCodeRepository deviceCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public DeviceCode createDeviceCode(String scope) {
        String deviceCode = generateRandomCode(DEVICE_CODE_LENGTH);
        String userCode = generateUserCode();
        
        DeviceCode deviceCodeEntity = DeviceCode.builder()
                .deviceCode(deviceCode)
                .userCode(userCode)
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES))
                .status("pending")
                .scope(scope)
                .intervalSeconds(5)
                .build();
        
        return deviceCodeRepository.save(deviceCodeEntity);
    }

    @Transactional
    public DeviceCode approveDeviceCode(String userCode, Long userId) {
        DeviceCode deviceCode = deviceCodeRepository.findByUserCode(userCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user code"));
        
        if (deviceCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Device code expired");
        }
        
        if (!"pending".equals(deviceCode.getStatus())) {
            throw new IllegalArgumentException("Device code already used");
        }
        
        deviceCode.setUserId(userId);
        deviceCode.setStatus("approved");
        return deviceCodeRepository.save(deviceCode);
    }

    @Transactional
    public DeviceCode pollDeviceCode(String deviceCode) {
        DeviceCode dc = deviceCodeRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid device code"));
        
        if (dc.getExpiresAt().isBefore(LocalDateTime.now())) {
            dc.setStatus("expired");
            deviceCodeRepository.save(dc);
            throw new IllegalArgumentException("Device code expired");
        }
        
        return dc;
    }

    @Transactional
    public void cleanupExpiredCodes() {
        deviceCodeRepository.findAll().stream()
                .filter(dc -> dc.getExpiresAt().isBefore(LocalDateTime.now()))
                .forEach(dc -> {
                    dc.setStatus("expired");
                    deviceCodeRepository.save(dc);
                });
    }

    private String generateRandomCode(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateUserCode() {
        String chars = "BCDFGHJKLMNPQRSTVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < USER_CODE_LENGTH; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
            if (i == 3) code.append("-");
        }
        return code.toString();
    }
}
