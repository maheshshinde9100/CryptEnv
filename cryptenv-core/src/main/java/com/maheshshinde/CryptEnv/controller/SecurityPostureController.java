package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.SecurityPostureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/security-posture")
@RequiredArgsConstructor
public class SecurityPostureController {
    private final SecurityPostureService postureService;
    @GetMapping public ResponseEntity<Map<String, Object>> current() { return ResponseEntity.ok(postureService.current()); }
}
