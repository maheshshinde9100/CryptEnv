package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.ApprovalRequest;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.ApprovalRequestRepository;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceMemberRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ApprovalService")
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private AuditLogService auditLogService;

    private FakeExecutor executor;
    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        executor = new FakeExecutor();
        approvalService = new ApprovalService(approvalRepository, workspaceRepository, memberRepository,
                environmentRepository, securityService, auditLogService, new ObjectMapper(), List.of(executor));
        approvalService.approvalsEnabled = true;
        approvalService.ttlHours = 24;
    }

    @Test
    void unsupportedActionCannotBeRequested() {
        when(securityService.getCurrentUser()).thenReturn(user(1L, Role.DEVELOPER));

        assertThrows(IllegalArgumentException.class,
                () -> approvalService.request("NOPE", null, null, null, Map.of(), "why", null));
    }

    @Test
    void requestBecomePendingWithTtl() {
        when(securityService.getCurrentUser()).thenReturn(user(1L, Role.DEVELOPER));
        when(approvalRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> view = approvalService.request("SECRET_DELETE", null, "SECRET", "API_KEY",
                Map.of(), "cleanup", null);

        assertEquals("PENDING", view.get("status"));
        assertEquals("SECRET_DELETE", view.get("action"));
        assertEquals(1L, view.get("requesterId"));
    }

    @Test
    void requesterCannotDecideOwnRequest() {
        when(securityService.getCurrentUser()).thenReturn(user(1L, Role.OWNER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(pending(1L, "SECRET_DELETE")));

        assertThrows(IllegalStateException.class, () -> approvalService.decide(10L, true, null));
        assertFalse(executor.executed);
    }

    @Test
    void approveRunsRegisteredExecutorAndMarksExecuted() {
        when(securityService.getCurrentUser()).thenReturn(user(2L, Role.OWNER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(pending(1L, "SECRET_DELETE")));
        when(approvalRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> view = approvalService.decide(10L, true, "looks good");

        assertTrue(executor.executed);
        assertEquals("EXECUTED", view.get("status"));
        assertEquals(FakeExecutor.RESULT, view.get("resultDetail"));
        assertEquals(2L, view.get("approverId"));
    }

    @Test
    void executorFailureMarksRequestFailed() {
        executor.shouldFail = true;
        when(securityService.getCurrentUser()).thenReturn(user(2L, Role.OWNER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(pending(1L, "SECRET_DELETE")));
        when(approvalRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> view = approvalService.decide(10L, true, null);

        assertEquals("FAILED", view.get("status"));
    }

    @Test
    void rejectNeverExecutesTheAction() {
        when(securityService.getCurrentUser()).thenReturn(user(2L, Role.OWNER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(pending(1L, "SECRET_DELETE")));
        when(approvalRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> view = approvalService.decide(10L, false, "not yet");

        assertEquals("REJECTED", view.get("status"));
        assertFalse(executor.executed);
    }

    @Test
    void expiredRequestCannotBeDecided() {
        ApprovalRequest expired = pending(1L, "SECRET_DELETE");
        expired.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(securityService.getCurrentUser()).thenReturn(user(2L, Role.OWNER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(expired));

        assertThrows(IllegalStateException.class, () -> approvalService.decide(10L, true, null));
        assertFalse(executor.executed);
    }

    @Test
    void nonEligibleApproverCannotDecide() {
        when(securityService.getCurrentUser()).thenReturn(user(3L, Role.DEVELOPER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(pending(1L, "SECRET_DELETE")));

        assertThrows(IllegalStateException.class, () -> approvalService.decide(10L, true, null));
    }

    @Test
    void onlyRequesterCanCancel() {
        when(securityService.getCurrentUser()).thenReturn(user(99L, Role.DEVELOPER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(pending(1L, "SECRET_DELETE")));

        assertThrows(ResourceNotFoundException.class, () -> approvalService.cancel(10L));
        verify(approvalRepository, never()).save(any(ApprovalRequest.class));
    }

    @Test
    void requesterCanCancelOwnPendingRequest() {
        when(securityService.getCurrentUser()).thenReturn(user(1L, Role.DEVELOPER));
        when(approvalRepository.findById(10L)).thenReturn(Optional.of(pending(1L, "SECRET_DELETE")));
        when(approvalRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> view = approvalService.cancel(10L);

        assertEquals("CANCELLED", view.get("status"));
        assertFalse(executor.executed);
    }

    private User user(Long id, Role role) {
        return User.builder().id(id).username("user" + id).email("user" + id + "@cryptenv.local")
                .password("x").role(role).build();
    }

    private ApprovalRequest pending(Long requesterId, String action) {
        return ApprovalRequest.builder()
                .id(10L)
                .requesterId(requesterId)
                .action(action)
                .status(ApprovalRequest.Status.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }

    private static final class FakeExecutor implements ApprovalActionExecutor {

        static final String RESULT = "deleted";

        boolean executed;
        boolean shouldFail;

        @Override
        public String action() {
            return ApprovalService.ACTION_SECRET_DELETE;
        }

        @Override
        public String execute(ApprovalRequest request) {
            executed = true;
            if (shouldFail) {
                throw new IllegalStateException("boom");
            }
            return RESULT;
        }
    }
}
