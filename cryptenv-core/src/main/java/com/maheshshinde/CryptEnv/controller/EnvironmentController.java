package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.dto.EnvironmentCreateDto;
import com.maheshshinde.CryptEnv.dto.EnvironmentResponseDto;
import com.maheshshinde.CryptEnv.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/environments")
@RequiredArgsConstructor
@Tag(name = "Environments", description = "Environment management APIs")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @PostMapping
    @Operation(summary = "Create a new environment")
    public ResponseEntity<EnvironmentResponseDto> createEnvironment(@Valid @RequestBody EnvironmentCreateDto createDto) {
        EnvironmentResponseDto environmentResponse = environmentService.createEnvironment(createDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(environmentResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get environment by ID")
    public ResponseEntity<EnvironmentResponseDto> getEnvironmentById(@PathVariable Long id) {
        EnvironmentResponseDto environmentResponse = environmentService.getEnvironmentById(id);
        return ResponseEntity.ok(environmentResponse);
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get all environments for a workspace")
    public ResponseEntity<List<EnvironmentResponseDto>> getEnvironmentsByWorkspace(@PathVariable Long workspaceId) {
        List<EnvironmentResponseDto> environments = environmentService.getEnvironmentsByWorkspace(workspaceId);
        return ResponseEntity.ok(environments);
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle environment active status")
    public ResponseEntity<EnvironmentResponseDto> toggleEnvironmentStatus(@PathVariable Long id) {
        EnvironmentResponseDto environmentResponse = environmentService.toggleEnvironmentStatus(id);
        return ResponseEntity.ok(environmentResponse);
    }
}
