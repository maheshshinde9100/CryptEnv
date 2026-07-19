package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.WorkspaceCreateDto;
import com.maheshshinde.CryptEnv.dto.WorkspaceResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceAlreadyExistsException;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    private WorkspaceCreateDto createDto;
    private Workspace workspace;
    private User user;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        createDto = WorkspaceCreateDto.builder()
                .name("test-workspace")
                .description("Test workspace")
                .build();

        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .password("encodedPassword")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .ownedWorkspaces(new HashSet<>())
                .memberWorkspaces(new HashSet<>())
                .build();

        userPrincipal = UserPrincipal.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .password("encodedPassword")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        workspace = Workspace.builder()
                .id(1L)
                .name("test-workspace")
                .description("Test workspace")
                .owner(user)
                .members(new HashSet<>())
                .environments(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createWorkspace_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceRepository.existsByName("test-workspace")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(workspace);

        WorkspaceResponseDto result = workspaceService.createWorkspace(createDto);

        assertNotNull(result);
        assertEquals("test-workspace", result.getName());
        assertEquals("Test workspace", result.getDescription());
        verify(workspaceRepository, times(1)).save(any(Workspace.class));
    }

    @Test
    void createWorkspace_NameAlreadyExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceRepository.existsByName("test-workspace")).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () -> workspaceService.createWorkspace(createDto));
        verify(workspaceRepository, never()).save(any(Workspace.class));
    }

    @Test
    void getWorkspaceById_Success() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        WorkspaceResponseDto result = workspaceService.getWorkspaceById(1L);

        assertNotNull(result);
        assertEquals("test-workspace", result.getName());
        verify(workspaceRepository, times(1)).findById(1L);
    }

    @Test
    void getWorkspaceById_NotFound() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workspaceService.getWorkspaceById(1L));
    }

    @Test
    void inviteMember_Success() {
        User member = User.builder()
                .id(2L)
                .email("member@example.com")
                .username("memberuser")
                .password("encodedPassword")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(workspace);

        WorkspaceResponseDto result = workspaceService.inviteMember(1L, "member@example.com");

        assertNotNull(result);
        verify(workspaceRepository, times(1)).save(any(Workspace.class));
    }

    @Test
    void inviteMember_UserNotFound() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workspaceService.inviteMember(1L, "member@example.com"));
    }
}
