package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.MasterKeyRotation;
import com.maheshshinde.CryptEnv.service.AuthenticationService;
import com.maheshshinde.CryptEnv.service.MasterKeyRotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master-key-rotation")
@RequiredArgsConstructor
@Tag(name = "Master Key Rotation", description = "Master key rotation management APIs")
public class MasterKeyRotationController {

    private final MasterKeyRotationService masterKeyRotationService;
    private final AuthenticationService authenticationService;

    @PostMapping
    @Operation(summary = "Start master key rotation for a workspace")
    public ResponseEntity<MasterKeyRotation> startRotation(@RequestBody Map<String, String> body) {
        Long workspaceId = Long.valueOf(body.get("workspaceId"));
        String oldKeyHash = body.get("oldKeyHash");
        String newKeyHash = body.get("newKeyHash");
        var currentUser = authenticationService.getCurrentUser();
        
        MasterKeyRotation rotation = masterKeyRotationService.startRotation(workspaceId, oldKeyHash, newKeyHash, currentUser.getId());
        return ResponseEntity.ok(rotation);
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get all rotation history for a workspace")
    public ResponseEntity<List<MasterKeyRotation>> getWorkspaceRotations(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(masterKeyRotationService.getWorkspaceRotations(workspaceId));
    }
}
