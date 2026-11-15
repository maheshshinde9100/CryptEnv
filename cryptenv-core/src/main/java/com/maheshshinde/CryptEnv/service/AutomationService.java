package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.AutomationRule;
import com.maheshshinde.CryptEnv.model.AutomationRun;
import com.maheshshinde.CryptEnv.model.DomainEvent;
import com.maheshshinde.CryptEnv.model.Notification;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.WebhookEndpoint;
import com.maheshshinde.CryptEnv.repository.AutomationRuleRepository;
import com.maheshshinde.CryptEnv.repository.AutomationRunRepository;
import com.maheshshinde.CryptEnv.repository.NotificationRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation rules (§10): <em>ON event WHERE condition DO action</em>.
 * <p>
 * Rules are evaluated by {@code EventDispatcher} after an event is written to the outbox. A rule
 * may only fire {@value #MAX_FIRES_PER_WINDOW} times per rolling {@value #WINDOW_SECONDS}-second
 * window; further matches are recorded as {@code BLOCKED} runs. This loop guard is what stops an
 * action that itself publishes an event (e.g. {@code AUDIT}) from cascading without bound.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomationService {

    public static final String ALL_EVENTS = "*";
    static final int MAX_FIRES_PER_WINDOW = 3;
    static final int WINDOW_SECONDS = 60;

    private final AutomationRuleRepository ruleRepository;
    private final AutomationRunRepository runRepository;
    private final NotificationRepository notificationRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;

    @Value("${cryptenv.automations.enabled:true}")
    boolean automationsEnabled;

    // ------------------------------------------------------------------ rules CRUD

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRules() {
        User user = securityService.getCurrentUser();
        return ruleRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toRuleView)
                .toList();
    }

    @Transactional
    public Map<String, Object> createRule(String name, String description, Long workspaceId, String eventType,
                                          Map<String, Object> conditions, String actionType,
                                          Map<String, Object> actionConfig, Boolean enabled) {
        User user = securityService.getCurrentUser();
        AutomationRule rule = AutomationRule.builder()
                .userId(user.getId())
                .workspaceId(workspaceId)
                .name(requireName(name))
                .description(description)
                .enabled(enabled == null || enabled)
                .eventType(normalizeEventType(eventType))
                .conditions(writeJson(conditions))
                .actionType(parseActionType(actionType))
                .actionConfig(requireActionConfig(actionConfig))
                .fireCount(0)
                .build();
        return toRuleView(ruleRepository.save(rule));
    }

    @Transactional
    public Map<String, Object> updateRule(Long id, String name, String description, Long workspaceId,
                                          String eventType, Map<String, Object> conditions, String actionType,
                                          Map<String, Object> actionConfig, Boolean enabled) {
        AutomationRule rule = ownedRule(id);
        if (name != null && !name.isBlank()) {
            rule.setName(name.trim());
        }
        if (description != null) {
            rule.setDescription(description);
        }
        if (workspaceId != null) {
            rule.setWorkspaceId(workspaceId);
        }
        if (eventType != null && !eventType.isBlank()) {
            rule.setEventType(normalizeEventType(eventType));
        }
        if (conditions != null) {
            rule.setConditions(writeJson(conditions));
        }
        if (actionType != null && !actionType.isBlank()) {
            rule.setActionType(parseActionType(actionType));
        }
        if (actionConfig != null) {
            rule.setActionConfig(requireActionConfig(actionConfig));
        }
        if (enabled != null) {
            rule.setEnabled(enabled);
        }
        return toRuleView(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.delete(ownedRule(id));
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listRuns(Long ruleId, Pageable pageable) {
        User user = securityService.getCurrentUser();
        if (ruleId != null) {
            ownedRule(ruleId);
            return runRepository.findByRuleIdOrderByCreatedAtDesc(ruleId, pageable).map(this::toRunView);
        }
        return runRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable).map(this::toRunView);
    }

    // ------------------------------------------------------------------ evaluation

    /** Evaluates every enabled rule owned by the event's user against the given event. */
    @Transactional
    public void evaluate(DomainEvent event) {
        if (!automationsEnabled || event == null || event.getUserId() == null) {
            return;
        }
        List<AutomationRule> rules = ruleRepository.findByUserIdAndEnabledTrue(event.getUserId());
        LocalDateTime now = LocalDateTime.now();
        for (AutomationRule rule : rules) {
            try {
                if (!triggers(rule, event) || !conditionsMatch(rule, event)) {
                    continue;
                }
                if (loopGuardBlocked(rule, now)) {
                    recordRun(rule, event, AutomationRun.Status.BLOCKED,
                            "Loop guard: at most " + MAX_FIRES_PER_WINDOW + " fires per " + WINDOW_SECONDS + "s");
                    ruleRepository.save(rule);
                    continue;
                }
                String detail = execute(rule, event);
                rule.setFireCount(rule.getFireCount() + 1);
                rule.setLastFiredAt(now);
                ruleRepository.save(rule);
                recordRun(rule, event, AutomationRun.Status.SUCCESS, detail);
            } catch (Exception e) {
                log.warn("Automation rule {} failed for event {}: {}", rule.getId(), event.getId(), e.getMessage());
                recordRun(rule, event, AutomationRun.Status.FAILED, e.getMessage());
            }
        }
    }

    private boolean triggers(AutomationRule rule, DomainEvent event) {
        if (rule.getEventType() == null || rule.getEventType().isBlank() || ALL_EVENTS.equals(rule.getEventType())) {
            return true;
        }
        return Arrays.stream(rule.getEventType().split(","))
                .map(String::trim)
                .anyMatch(t -> t.equals(ALL_EVENTS) || t.equalsIgnoreCase(event.getType()));
    }

    private boolean conditionsMatch(AutomationRule rule, DomainEvent event) {
        Map<String, Object> conditions = readJson(rule.getConditions());
        if (conditions.isEmpty()) {
            return true;
        }
        Map<String, Object> payload = readJson(event.getPayload());
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            Object expected = condition.getValue();
            if (expected == null) {
                continue;
            }
            boolean matches = switch (condition.getKey()) {
                case "severity" -> NotificationService.severityOf(event.getType()).name()
                        .equalsIgnoreCase(expected.toString());
                case "resourceType" -> equalsIgnoreCase(event.getResourceType(), expected.toString());
                case "typeEquals" -> equalsIgnoreCase(event.getType(), expected.toString());
                case "typeContains" -> event.getType() != null
                        && event.getType().toUpperCase().contains(expected.toString().toUpperCase());
                case "workspaceId" -> event.getWorkspaceId() != null
                        && event.getWorkspaceId().toString().equals(expected.toString());
                case "success" -> payload.get("success") != null
                        && payload.get("success").toString().equalsIgnoreCase(expected.toString());
                default -> true;
            };
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies the loop guard: resets the window when it has elapsed and reports whether the rule
     * has already fired its maximum number of times within the current window.
     */
    private boolean loopGuardBlocked(AutomationRule rule, LocalDateTime now) {
        if (rule.getWindowStartedAt() == null
                || Duration.between(rule.getWindowStartedAt(), now).getSeconds() >= WINDOW_SECONDS) {
            rule.setWindowStartedAt(now);
            rule.setFireCount(0);
        }
        return rule.getFireCount() >= MAX_FIRES_PER_WINDOW;
    }

    // ------------------------------------------------------------------ actions

    private String execute(AutomationRule rule, DomainEvent event) {
        Map<String, Object> config = readJson(rule.getActionConfig());
        return switch (rule.getActionType()) {
            case NOTIFY -> {
                String title = config.get("title") != null
                        ? config.get("title").toString()
                        : "Automation: " + rule.getName();
                String message = config.get("message") != null
                        ? config.get("message").toString()
                        : "Rule '" + rule.getName() + "' matched event " + event.getType();
                notificationRepository.save(Notification.builder()
                        .userId(rule.getUserId())
                        .workspaceId(event.getWorkspaceId())
                        .eventId(event.getId())
                        .type("AUTOMATION_NOTIFY")
                        .severity(NotificationService.severityOf(event.getType()))
                        .title(title)
                        .body(message)
                        .resourceType(event.getResourceType())
                        .resourceId(event.getResourceId())
                        .build());
                yield "Created notification: " + title;
            }
            case AUDIT -> {
                String message = config.get("message") != null
                        ? config.get("message").toString()
                        : "Automation rule '" + rule.getName() + "' fired for event " + event.getType();
                User owner = userRepository.findById(rule.getUserId()).orElse(null);
                auditLogService.logEvent(owner, "AUTOMATION_ACTION", "AUTOMATION_RULE",
                        String.valueOf(rule.getId()), true, null, message, event.getWorkspaceId());
                yield "Recorded audit entry: " + message;
            }
            case DISABLE_WEBHOOK -> {
                Long endpointId = longValue(config.get("endpointId"));
                if (endpointId == null) {
                    throw new IllegalArgumentException("DISABLE_WEBHOOK requires actionConfig.endpointId");
                }
                WebhookEndpoint endpoint = webhookEndpointRepository.findById(endpointId)
                        .filter(e -> e.getUser() != null && e.getUser().getId().equals(rule.getUserId()))
                        .orElseThrow(() -> new ResourceNotFoundException("Webhook endpoint not found: " + endpointId));
                endpoint.setActive(false);
                webhookEndpointRepository.save(endpoint);
                yield "Disabled webhook endpoint " + endpointId;
            }
        };
    }

    // ------------------------------------------------------------------ helpers

    private AutomationRule ownedRule(Long id) {
        User user = securityService.getCurrentUser();
        return ruleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Automation rule not found: " + id));
    }

    private void recordRun(AutomationRule rule, DomainEvent event, AutomationRun.Status status, String detail) {
        runRepository.save(AutomationRun.builder()
                .ruleId(rule.getId())
                .userId(rule.getUserId())
                .eventId(event.getId())
                .eventType(event.getType())
                .status(status)
                .detail(truncate(detail, 1000))
                .build());
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A rule name is required");
        }
        return name.trim();
    }

    private String requireActionConfig(Map<String, Object> actionConfig) {
        if (actionConfig == null || actionConfig.isEmpty()) {
            throw new IllegalArgumentException("actionConfig is required");
        }
        return writeJson(actionConfig);
    }

    private AutomationRule.ActionType parseActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("An action type is required");
        }
        try {
            return AutomationRule.ActionType.valueOf(actionType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown action type: " + actionType);
        }
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return ALL_EVENTS;
        }
        String joined = Arrays.stream(eventType.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .reduce((a, b) -> a + "," + b)
                .orElse(ALL_EVENTS);
        return joined;
    }

    private boolean equalsIgnoreCase(String actual, String expected) {
        return actual != null && actual.equalsIgnoreCase(expected);
    }

    private Map<String, Object> toRuleView(AutomationRule rule) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", rule.getId());
        view.put("name", rule.getName());
        view.put("description", rule.getDescription());
        view.put("workspaceId", rule.getWorkspaceId());
        view.put("enabled", rule.getEnabled());
        view.put("eventType", rule.getEventType());
        view.put("conditions", readJson(rule.getConditions()));
        view.put("actionType", rule.getActionType().name());
        view.put("actionConfig", readJson(rule.getActionConfig()));
        view.put("fireCount", rule.getFireCount());
        view.put("windowStartedAt", rule.getWindowStartedAt());
        view.put("lastFiredAt", rule.getLastFiredAt());
        view.put("createdAt", rule.getCreatedAt());
        view.put("updatedAt", rule.getUpdatedAt());
        return view;
    }

    private Map<String, Object> toRunView(AutomationRun run) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", run.getId());
        view.put("ruleId", run.getRuleId());
        view.put("eventId", run.getEventId());
        view.put("eventType", run.getEventType());
        view.put("status", run.getStatus().name());
        view.put("detail", run.getDetail());
        view.put("createdAt", run.getCreatedAt());
        return view;
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
