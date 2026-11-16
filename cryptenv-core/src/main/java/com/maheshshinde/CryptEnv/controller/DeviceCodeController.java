package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.model.DeviceCode;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.DeviceCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/device-code")
@RequiredArgsConstructor
@Tag(name = "Device Code", description = "CLI device-code flow for headless login")
public class DeviceCodeController {

    private final DeviceCodeService deviceCodeService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    @PostMapping("/request")
    @Operation(summary = "Request device code for CLI login")
    public ResponseEntity<DeviceCode> requestDeviceCode(@RequestBody(required = false) Map<String, String> body) {
        String scope = body != null ? body.get("scope") : null;
        DeviceCode deviceCode = deviceCodeService.createDeviceCode(scope);
        return ResponseEntity.ok(deviceCode);
    }

    @PostMapping("/approve")
    @Operation(summary = "Approve device code (via browser)")
    public ResponseEntity<Map<String, String>> approveDeviceCode(@RequestBody Map<String, String> body,
                                                                 HttpServletRequest request) {
        String userCode = body.get("userCode");
        var currentUser = authenticationService.getCurrentUser();
        deviceCodeService.approveDeviceCode(userCode, currentUser.getId());
        return ResponseEntity.ok(Map.of("message", "Device code approved"));
    }

    @PostMapping("/poll")
    @Operation(summary = "Poll for device code approval (CLI)")
    public ResponseEntity<Map<String, Object>> pollDeviceCode(@RequestBody Map<String, String> body) {
        String deviceCode = body.get("deviceCode");
        DeviceCode dc = deviceCodeService.pollDeviceCode(deviceCode);
        
        if ("approved".equals(dc.getStatus()) && dc.getUserId() != null) {
            var user = userRepository.findById(dc.getUserId()).orElseThrow();
            // Generate tokens without password for device code flow
            JwtResponseDto jwtResponse = authenticationService.authenticate(
                    new com.maheshshinde.CryptEnv.dto.UserLoginDto(user.getEmail(), null)
            );
            return ResponseEntity.ok(Map.of(
                    "status", "approved",
                    "token", jwtResponse.getToken(),
                    "refreshToken", jwtResponse.getRefreshToken(),
                    "expiresIn", jwtResponse.getExpiresIn()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "status", dc.getStatus(),
                "interval", dc.getIntervalSeconds()
        ));
    }
}
