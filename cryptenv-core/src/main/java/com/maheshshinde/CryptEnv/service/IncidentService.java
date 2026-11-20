package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Environment;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.SecurityIncident;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.SecurityIncidentRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

    private static final Map<SecurityIncident.Status, Set<SecurityIncident.Status>> TRANSITIONS = Map.of(
            SecurityIncident.Status.OPEN, EnumSet.of(SecurityIncident.Status.INVESTIGATING,
                    SecurityIncident.Status.CONTAINED, SecurityIncident.Status.RESOLVED),
            SecurityIncident.Status.INVESTIGATING, EnumSet.of(SecurityIncident.Status.CONTAINED,
                    SecurityIncident.Status.RESOLVED),
            SecurityIncident.Status.CONTAINED, EnumSet.of(SecurityIncident.Status.RESOLVED,
                    SecurityIncident.Status.INVESTIGATING),
            SecurityIncident.Status.RESOLVED, EnumSet.of(SecurityIncident.Status.POST_MORTEM,
                    SecurityIncident.Status.OPEN),
            SecurityIncident.Status.POST_MORTEM, EnumSet.noneOf(SecurityIncident.Status.class)
    );

    private final SecurityIncidentRepository incidentRepository;
    private final EnvironmentRepository environmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;
    private final EnvironmentService environmentService;
    private final SecurityService securityService;

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        User user = securityService.getCurrentUser();
        String title = string(body, "title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Incident title is required");
        }

        Long environmentId = longValue(body, "environmentId");
        Long workspaceId = longValue(body, "workspaceId");
        Environment environment = null;
        if (environmentId != null) {
            environment = environmentRepository.findById(environmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Environment not found with id: " + environmentId));
            Long envWorkspaceId = environment.getWorkspace().getId();
            if (workspaceId != null && !workspaceId.equals(envWorkspaceId)) {
                throw new IllegalArgumentException("Environment does not belong to the given workspace");
            }
            workspaceId = envWorkspaceId;
        }
        if (workspaceId != null) {
            Long resolvedWorkspaceId = workspaceId;
            Workspace workspace = workspaceRepository.findById(resolvedWorkspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + resolvedWorkspaceId));
            if (workspaceService.getEffectiveRole(workspace, user) == null) {
                throw new ResourceNotFoundException("Workspace not found with id: " + resolvedWorkspaceId);
            }
        }

        SecurityIncident incident = SecurityIncident.builder()
                .reporter(user)
                .title(title.trim())
                .description(string(body, "description"))
                .severity(string(body, "severity"))
                .workspaceId(workspaceId)
                .environmentId(environment != null ? environment.getId() : null)
                .status(SecurityIncident.Status.OPEN)
                .timeline(entry("OPEN: incident created by " + user.getUsername()))
                .build();
        incident = incidentRepository.save(incident);
        incident.setIncidentNumber(String.format("INC-%06d", incident.getId()));
        return view(incidentRepository.save(incident));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        User user = securityService.getCurrentUser();
        List<Long> workspaceIds = new ArrayList<>();
        workspaceRepository.findByOwnerId(user.getId()).forEach(w -> workspaceIds.add(w.getId()));
        workspaceRepository.findByMembershipsUserId(user.getId()).forEach(w -> workspaceIds.add(w.getId()));
        if (workspaceIds.isEmpty()) {
            workspaceIds.add(-1L);
        }
        return incidentRepository.findVisibleTo(user.getId(), workspaceIds).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        return view(loadVisible(id));
    }

    @Transactional
    public Map<String, Object> transition(Long id, String status, String note) {
        SecurityIncident incident = loadVisible(id);
        SecurityIncident.Status next;
        try {
            next = SecurityIncident.Status.valueOf(status == null ? "" : status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid incident status: " + status);
        }
        if (incident.getStatus() == next) {
            return view(incident);
        }
        Set<SecurityIncident.Status> allowed = TRANSITIONS.get(incident.getStatus());
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalArgumentException("Cannot move incident from " + incident.getStatus() + " to " + next);
        }
        requireResponder(incident);
        incident.setStatus(next);
        appendTimeline(incident, next + (note == null || note.isBlank() ? "" : ": " + note.trim()));
        return view(incidentRepository.save(incident));
    }

    @Transactional
    public Map<String, Object> quarantine(Long id) {
        SecurityIncident incident = loadVisible(id);
        requireAdmin(incident);
        if (incident.getEnvironmentId() == null) {
            throw new IllegalArgumentException("Incident has no environment to quarantine");
        }
        Environment environment = environmentRepository.findById(incident.getEnvironmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found"));
        environmentService.setProtected(environment.getId(), true);

        if (incident.getStatus() == SecurityIncident.Status.OPEN
                || incident.getStatus() == SecurityIncident.Status.INVESTIGATING) {
            incident.setStatus(SecurityIncident.Status.CONTAINED);
        }
        incident.setQuarantineActions("Environment " + environment.displayName()
                + " set to protected (writes require workspace admin or active JIT grant)");
        appendTimeline(incident, "CONTAINED: environment " + environment.displayName() + " quarantined");
        log.warn("Incident {} quarantined environment {}", incident.getIncidentNumber(), environment.getId());
        return view(incidentRepository.save(incident));
    }

    @Transactional
    public Map<String, Object> savePostMortem(Long id, String content) {
        SecurityIncident incident = loadVisible(id);
        requireResponder(incident);
        if (incident.getStatus() != SecurityIncident.Status.RESOLVED
                && incident.getStatus() != SecurityIncident.Status.POST_MORTEM) {
            throw new IllegalArgumentException("Post-mortem can only be written for resolved incidents");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Post-mortem content is required");
        }
        incident.setPostMortem(content.trim());
        if (incident.getStatus() == SecurityIncident.Status.RESOLVED) {
            incident.setStatus(SecurityIncident.Status.POST_MORTEM);
            appendTimeline(incident, "POST_MORTEM: post-mortem documented");
        }
        return view(incidentRepository.save(incident));
    }

    private SecurityIncident loadVisible(Long id) {
        User user = securityService.getCurrentUser();
        SecurityIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));
        if (incident.getReporter().getId().equals(user.getId())) {
            return incident;
        }
        if (incident.getWorkspaceId() != null) {
            Workspace workspace = workspaceRepository.findById(incident.getWorkspaceId()).orElse(null);
            if (workspace != null && workspaceService.getEffectiveRole(workspace, user) != null) {
                return incident;
            }
        }
        throw new ResourceNotFoundException("Incident not found with id: " + id);
    }

    private void requireResponder(SecurityIncident incident) {
        if (incident.getWorkspaceId() == null) {
            if (!incident.getReporter().getId().equals(securityService.getCurrentUser().getId())) {
                throw new RuntimeException("Only the reporter can update this incident");
            }
            return;
        }
        Role role = currentRole(incident);
        if (role != Role.OWNER && role != Role.ADMIN && role != Role.DEVELOPER) {
            throw new RuntimeException("Only owner, admin or developer can update this incident");
        }
    }

    private void requireAdmin(SecurityIncident incident) {
        if (incident.getWorkspaceId() == null) {
            if (!incident.getReporter().getId().equals(securityService.getCurrentUser().getId())) {
                throw new RuntimeException("Only the reporter can quarantine this incident");
            }
            return;
        }
        Role role = currentRole(incident);
        if (role != Role.OWNER && role != Role.ADMIN) {
            throw new RuntimeException("Only workspace owner or admin can quarantine an environment");
        }
    }

    private Role currentRole(SecurityIncident incident) {
        Workspace workspace = workspaceRepository.findById(incident.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        return workspaceService.getEffectiveRole(workspace, securityService.getCurrentUser());
    }

    private void appendTimeline(SecurityIncident incident, String message) {
        String existing = incident.getTimeline() == null || incident.getTimeline().isBlank() ? "" : incident.getTimeline() + "\n";
        incident.setTimeline(existing + entry(message));
    }

    private String entry(String message) {
        return "[" + LocalDateTime.now() + "] " + message;
    }

    private Map<String, Object> view(SecurityIncident i) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", i.getId());
        v.put("number", i.getIncidentNumber());
        v.put("title", i.getTitle());
        v.put("description", i.getDescription());
        v.put("severity", i.getSeverity());
        v.put("status", i.getStatus().name());
        v.put("workspaceId", i.getWorkspaceId());
        v.put("environmentId", i.getEnvironmentId());
        v.put("reporter", i.getReporter().getUsername());
        v.put("timeline", i.getTimeline());
        v.put("quarantineActions", i.getQuarantineActions());
        v.put("postMortem", i.getPostMortem());
        v.put("createdAt", i.getCreatedAt());
        v.put("updatedAt", i.getUpdatedAt());
        return v;
    }

    private String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private Long longValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null || value.toString().isBlank() ? null : Long.valueOf(value.toString());
    }
}
