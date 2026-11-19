package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.SecurityIncident;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.SecurityIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class IncidentService {
    private final SecurityIncidentRepository incidentRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecurityService securityService;

    @Transactional public Map<String, Object> create(Map<String, Object> body) {
        User user = securityService.getCurrentUser();
        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        if (title.isBlank()) throw new IllegalArgumentException("Incident title is required");
        SecurityIncident incident = incidentRepository.save(SecurityIncident.builder().reporter(user).title(title)
                .description(string(body, "description")).workspaceId(longValue(body, "workspaceId"))
                .environmentId(longValue(body, "environmentId")).timeline("[" + LocalDateTime.now() + "] OPEN: incident created").build());
        incident.setIncidentNumber(String.format("INC-%06d", incident.getId()));
        return view(incidentRepository.save(incident));
    }
    @Transactional(readOnly = true) public List<Map<String, Object>> list() { Long id = securityService.getCurrentUser().getId(); return incidentRepository.findByReporterIdOrderByCreatedAtDesc(id).stream().map(this::view).toList(); }
    @Transactional public Map<String, Object> transition(Long id, String status, String note) {
        SecurityIncident incident = owned(id); SecurityIncident.Status next;
        try { next = SecurityIncident.Status.valueOf(status); } catch (Exception e) { throw new IllegalArgumentException("Invalid incident status"); }
        incident.setStatus(next); incident.setTimeline((incident.getTimeline() == null ? "" : incident.getTimeline() + "\n") + "[" + LocalDateTime.now() + "] " + next + (note == null ? "" : ": " + note));
        return view(incidentRepository.save(incident));
    }
    @Transactional public Map<String, Object> quarantine(Long id) {
        SecurityIncident incident = owned(id);
        if (incident.getEnvironmentId() == null) throw new IllegalArgumentException("Incident has no environment to quarantine");
        Environment environment = environmentRepository.findById(incident.getEnvironmentId()).orElseThrow(() -> new ResourceNotFoundException("Environment not found"));
        environment.setIsProtected(true);
        incident.setStatus(SecurityIncident.Status.CONTAINED);
        incident.setTimeline((incident.getTimeline() == null ? "" : incident.getTimeline() + "\n") + "[" + LocalDateTime.now() + "] CONTAINED: environment writes quarantined");
        return view(incidentRepository.save(incident));
    }
    private SecurityIncident owned(Long id) { return incidentRepository.findByIdAndReporterId(id, securityService.getCurrentUser().getId()).orElseThrow(() -> new ResourceNotFoundException("Incident not found")); }
    private Map<String,Object> view(SecurityIncident i) { Map<String,Object> v=new LinkedHashMap<>(); v.put("id",i.getId());v.put("number",i.getIncidentNumber());v.put("title",i.getTitle());v.put("description",i.getDescription());v.put("status",i.getStatus().name());v.put("workspaceId",i.getWorkspaceId());v.put("environmentId",i.getEnvironmentId());v.put("timeline",i.getTimeline());v.put("createdAt",i.getCreatedAt());v.put("updatedAt",i.getUpdatedAt());return v; }
    private String string(Map<String,Object> b,String k){Object v=b.get(k);return v==null?null:v.toString();} private Long longValue(Map<String,Object>b,String k){Object v=b.get(k);return v==null?null:Long.valueOf(v.toString());}
}
