package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.JwtResponseDto;
import com.maheshshinde.CryptEnv.dto.UserLoginDto;
import com.maheshshinde.CryptEnv.dto.UserRegistrationDto;
import com.maheshshinde.CryptEnv.dto.UserResponseDto;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody UserRegistrationDto registrationDto) {
        UserResponseDto userResponse = authenticationService.register(registrationDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT token")
    public ResponseEntity<JwtResponseDto> authenticate(@Valid @RequestBody UserLoginDto loginDto) {
        JwtResponseDto jwtResponse = authenticationService.authenticate(loginDto);
        return ResponseEntity.ok(jwtResponse);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        UserResponseDto userResponse = authenticationService.getCurrentUser();
        return ResponseEntity.ok(userResponse);
    }
}
