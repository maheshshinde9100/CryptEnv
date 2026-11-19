package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.SecretBranch;
import com.maheshshinde.CryptEnv.model.SecretBranchVersion;
import com.maheshshinde.CryptEnv.service.SecretBranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/secret-branches")
@RequiredArgsConstructor
@Tag(name = "Secret Branches", description = "Secret branch management APIs")
public class SecretBranchController {

    private final SecretBranchService secretBranchService;

    @PostMapping
    @Operation(summary = "Create a new secret branch")
    public ResponseEntity<SecretBranch> createBranch(@RequestBody Map<String, Object> body) {
        Long workspaceId = Long.valueOf(body.get("workspaceId").toString());
        Long projectId = body.get("projectId") != null ? Long.valueOf(body.get("projectId").toString()) : null;
        Long environmentId = body.get("environmentId") != null ? Long.valueOf(body.get("environmentId").toString()) : null;
        String name = (String) body.get("name");
        Long createdBy = Long.valueOf(body.get("createdBy").toString());
        Long parentBranchId = body.get("parentBranchId") != null ? Long.valueOf(body.get("parentBranchId").toString()) : null;
        
        SecretBranch branch = secretBranchService.createBranch(workspaceId, projectId, environmentId, name,
                createdBy, parentBranchId);
        return ResponseEntity.ok(branch);
    }

    @GetMapping("/workspace/{workspaceId}")
    @Operation(summary = "Get all branches for a workspace")
    public ResponseEntity<List<SecretBranch>> getWorkspaceBranches(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(secretBranchService.getWorkspaceBranches(workspaceId));
    }

    @PostMapping("/{branchId}/activate")
    @Operation(summary = "Activate a branch")
    public ResponseEntity<SecretBranch> activateBranch(@PathVariable Long branchId) {
        SecretBranch branch = secretBranchService.activateBranch(branchId);
        return ResponseEntity.ok(branch);
    }

    @PostMapping("/{branchId}/secrets")
    @Operation(summary = "Add a secret to a branch")
    public ResponseEntity<SecretBranchVersion> addSecretToBranch(@PathVariable Long branchId,
                                                                 @RequestBody Map<String, Object> body) {
        Long secretId = Long.valueOf(body.get("secretId").toString());
        Long secretVersionId = Long.valueOf(body.get("secretVersionId").toString());
        
        SecretBranchVersion version = secretBranchService.addSecretToBranch(branchId, secretId, secretVersionId);
        return ResponseEntity.ok(version);
    }

    @GetMapping("/{branchId}/versions")
    @Operation(summary = "Get all versions in a branch")
    public ResponseEntity<List<SecretBranchVersion>> getBranchVersions(@PathVariable Long branchId) {
        return ResponseEntity.ok(secretBranchService.getBranchVersions(branchId));
    }

    @DeleteMapping("/{branchId}")
    @Operation(summary = "Delete a branch")
    public ResponseEntity<Map<String, String>> deleteBranch(@PathVariable Long branchId) {
        secretBranchService.deleteBranch(branchId);
        return ResponseEntity.ok(Map.of("message", "Branch deleted"));
    }
}
