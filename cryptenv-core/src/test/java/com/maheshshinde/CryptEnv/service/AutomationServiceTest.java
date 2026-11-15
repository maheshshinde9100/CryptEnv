package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.model.AutomationRule;
import com.maheshshinde.CryptEnv.model.AutomationRun;
import com.maheshshinde.CryptEnv.model.DomainEvent;
import com.maheshshinde.CryptEnv.model.Notification;
import com.maheshshinde.CryptEnv.repository.AutomationRuleRepository;
import com.maheshshinde.CryptEnv.repository.AutomationRunRepository;
import com.maheshshinde.CryptEnv.repository.NotificationRepository;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AutomationService")
@ExtendWith(MockitoExtension.class)
class AutomationServiceTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    @Mock
    private AutomationRunRepository runRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SecurityService securityService;

    private AutomationService automationService;

    @BeforeEach
    void setUp() {
        automationService = new AutomationService(
                ruleRepository, runRepository, notificationRepository, webhookEndpointRepository,
                userRepository, auditLogService, securityService, new ObjectMapper());
        automationService.automationsEnabled = true;
    }

    @Test
    void notifyActionCreatesNotificationForMatchingEvent() {
        DomainEvent event = event(7L, "SECRET_CREATE");
        when(ruleRepository.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(rule(AutomationRule.ActionType.NOTIFY)));

        automationService.evaluate(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("Automation: rotate on create", captor.getValue().getTitle());

        ArgumentCaptor<AutomationRun> run = ArgumentCaptor.forClass(AutomationRun.class);
        verify(runRepository).save(run.capture());
        assertEquals(AutomationRun.Status.SUCCESS, run.getValue().getStatus());
    }

    @Test
    void loopGuardBlocksAfterThreeFiresInWindow() {
        DomainEvent event = event(7L, "SECRET_CREATE");
        AutomationRule exhausted = rule(AutomationRule.ActionType.NOTIFY);
        exhausted.setFireCount(3);
        exhausted.setWindowStartedAt(LocalDateTime.now());
        when(ruleRepository.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(exhausted));

        automationService.evaluate(event);

        verify(notificationRepository, never()).save(any());
        ArgumentCaptor<AutomationRun> run = ArgumentCaptor.forClass(AutomationRun.class);
        verify(runRepository).save(run.capture());
        assertEquals(AutomationRun.Status.BLOCKED, run.getValue().getStatus());
    }

    @Test
    void conditionsThatDoNotMatchSkipTheRuleEntirely() {
        DomainEvent event = event(7L, "SECRET_CREATE");
        AutomationRule onlyDeletes = rule(AutomationRule.ActionType.NOTIFY);
        onlyDeletes.setConditions("{\"typeContains\":\"DELETE\"}");
        when(ruleRepository.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(onlyDeletes));

        automationService.evaluate(event);

        verify(notificationRepository, never()).save(any());
        verify(runRepository, never()).save(any());
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void disabledAutomationsAreNotEvaluated() {
        automationService.automationsEnabled = false;
        automationService.evaluate(event(7L, "SECRET_CREATE"));
        verify(ruleRepository, never()).findByUserIdAndEnabledTrue(any());
    }

    @Test
    void allEventTriggerFiresOncePerEvaluation() {
        DomainEvent event = event(7L, "WEBHOOK_DELETE");
        when(ruleRepository.findByUserIdAndEnabledTrue(7L)).thenReturn(List.of(rule(AutomationRule.ActionType.NOTIFY)));

        automationService.evaluate(event);

        verify(notificationRepository, times(1)).save(any());
        verify(ruleRepository, times(1)).save(any());
    }

    @Test
    void eventWithoutUserIsIgnored() {
        automationService.evaluate(event(null, "SECRET_CREATE"));
        verify(ruleRepository, never()).findByUserIdAndEnabledTrue(any());
    }

    private DomainEvent event(Long userId, String type) {
        return DomainEvent.builder()
                .id(1L)
                .userId(userId)
                .workspaceId(3L)
                .type(type)
                .resourceType("SECRET")
                .resourceId("API_KEY")
                .payload("{\"action\":\"" + type + "\",\"success\":true}")
                .build();
    }

    private AutomationRule rule(AutomationRule.ActionType actionType) {
        return AutomationRule.builder()
                .id(1L)
                .userId(7L)
                .name("rotate on create")
                .enabled(true)
                .eventType("*")
                .actionType(actionType)
                .actionConfig("{\"title\":\"Automation: rotate on create\"}")
                .fireCount(0)
                .build();
    }
}
