package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.service.LeakDetectionService;
import com.maheshshinde.CryptEnv.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/leaks") @RequiredArgsConstructor
public class LeakDetectionController {
    private final LeakDetectionService leaks;
    private final SecurityService security;
    @PostMapping("/scan") public ResponseEntity<List<Map<String,Object>>> scan(@RequestBody Map<String,Object> body) {
        security.checkPermission(Permission.SECRET_WRITE);
        return ResponseEntity.ok(leaks.scan((String) body.get("source"), (String) body.get("content"), asLong(body.get("workspaceId")), asLong(body.get("environmentId"))));
    }
    @GetMapping public ResponseEntity<List<Map<String,Object>>> list() { return ResponseEntity.ok(leaks.list()); }
    @PatchMapping("/{id}") public ResponseEntity<Map<String,Object>> update(@PathVariable Long id, @RequestBody Map<String,String> body) { return ResponseEntity.ok(leaks.update(id, body.get("status"))); }
    private Long asLong(Object value) { return value == null ? null : Long.valueOf(String.valueOf(value)); }
}
