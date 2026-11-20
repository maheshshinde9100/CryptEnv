package com.maheshshinde.CryptEnv.controller;

import com.maheshshinde.CryptEnv.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidents;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(incidents.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return ResponseEntity.ok(incidents.get(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(incidents.create(body));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(incidents.transition(id, body.get("status"), body.get("note")));
    }

    @PostMapping("/{id}/quarantine")
    public ResponseEntity<Map<String, Object>> quarantine(@PathVariable Long id) {
        return ResponseEntity.ok(incidents.quarantine(id));
    }

    @PostMapping("/{id}/post-mortem")
    public ResponseEntity<Map<String, Object>> postMortem(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(incidents.savePostMortem(id, body.get("content")));
    }
}
