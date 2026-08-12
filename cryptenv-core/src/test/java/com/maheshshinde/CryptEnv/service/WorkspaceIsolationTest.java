package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.WorkspaceResponseDto;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.model.Workspace;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import com.maheshshinde.CryptEnv.repository.WorkspaceRepository;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import com.maheshshinde.CryptEnv.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceService — User Isolation (Critical Security Tests)")
class WorkspaceIsolationTest {

    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private UserRepository userRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private SecurityService securityService;

    @InjectMocks private WorkspaceService workspaceService;

    private User alice;
    private User bob;
    private User mallory;

    private Workspace aliceOwned;
    private Workspace bobOwned;
    private Workspace sharedWithAlice;

    @BeforeEach
    void setUp() {
        alice = user(1L, "alice@x.com", "alice");
        bob = user(2L, "bob@x.com", "bob");
        mallory = user(3L, "mallory@x.com", "mallory");

        aliceOwned = ws(10L, "alice-own", alice, Collections.emptySet());
        bobOwned = ws(20L, "bob-own", bob, Collections.emptySet());
        sharedWithAlice = ws(21L, "bob-shared", bob, new HashSet<>(List.of(alice)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(Long id, String email, String username) {
        return User.builder()
                .id(id).email(email).username(username)
                .password("encoded").enabled(true)
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true)
                .ownedWorkspaces(new HashSet<>())
                .memberWorkspaces(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Workspace ws(Long id, String name, User owner, Set<User> members) {
        return Workspace.builder()
                .id(id).name(name).description("desc")
                .owner(owner).members(members)
                .environments(new HashSet<>())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private void authenticate(User u) {
        UserPrincipal up = UserPrincipal.builder()
                .id(u.getId()).email(u.getEmail()).username(u.getUsername())
                .password(u.getPassword()).enabled(true)
                .accountNonExpired(true).accountNonLocked(true)
                .credentialsNonExpired(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(up, null, Collections.emptyList()));
        lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(securityService.getCurrentUser()).thenReturn(u);
    }

    // ------------------------------------------------------------------
    // The CRITICAL tests that guarantee user isolation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getUserWorkspaces returns BOTH owned + member workspaces")
    void aliceSeesOwnedAndMemberWorkspaces() {
        authenticate(alice);
        when(workspaceRepository.findByOwnerId(alice.getId())).thenReturn(List.of(aliceOwned));
        when(workspaceRepository.findByMembersId(alice.getId())).thenReturn(List.of(sharedWithAlice));

        List<WorkspaceResponseDto> dtos = workspaceService.getUserWorkspaces();

        assertEquals(2, dtos.size(),
                "Alice must see her own workspace AND the one Bob shared with her");
        Set<String> names = new HashSet<>();
        for (WorkspaceResponseDto d : dtos) names.add(d.getName());
        assertTrue(names.contains("alice-own"));
        assertTrue(names.contains("bob-shared"));
    }

    @Test
    @DisplayName("Bob's workspace owned list never shows to Mallory")
    void malloryNeverSeesBobsWorkspaces() {
        authenticate(mallory);
        when(workspaceRepository.findByOwnerId(mallory.getId())).thenReturn(Collections.emptyList());
        when(workspaceRepository.findByMembersId(mallory.getId())).thenReturn(Collections.emptyList());

        List<WorkspaceResponseDto> dtos = workspaceService.getUserWorkspaces();
        assertTrue(dtos.isEmpty());
    }

    @Test
    @DisplayName("Member cannot invite other members — only owner can")
    void nonOwnerCannotInviteMembers() {
        authenticate(alice); // Alice is a MEMBER of "bob-shared", NOT the owner
        when(workspaceRepository.findById(sharedWithAlice.getId()))
                .thenReturn(Optional.of(sharedWithAlice));

        assertThrows(RuntimeException.class,
                () -> workspaceService.inviteMember(sharedWithAlice.getId(), mallory.getEmail()));
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Owner CAN invite members")
    void ownerCanInviteMembers() {
        authenticate(bob);
        when(workspaceRepository.findById(sharedWithAlice.getId()))
                .thenReturn(Optional.of(sharedWithAlice));
        when(userRepository.findByEmail(mallory.getEmail()))
                .thenReturn(Optional.of(mallory));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceResponseDto dto = workspaceService.inviteMember(sharedWithAlice.getId(), mallory.getEmail());
        assertNotNull(dto);
        verify(workspaceRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("getWorkspaceById rejects non-owner / non-member user")
    void malloryCannotAccessBobWorkspaceById() {
        authenticate(mallory);
        when(workspaceRepository.findById(bobOwned.getId()))
                .thenReturn(Optional.of(bobOwned));

        assertThrows(RuntimeException.class,
                () -> workspaceService.getWorkspaceById(bobOwned.getId()));
    }

    @Test
    @DisplayName("hasEncryptionKey is NEVER true when workspaceEncryptionKey is null")
    void noEncryptionKeyShowsFalseInDto() {
        authenticate(alice);
        Workspace plain = ws(99L, "plain", alice, Collections.emptySet());
        when(workspaceRepository.findById(99L)).thenReturn(Optional.of(plain));

        WorkspaceResponseDto dto = workspaceService.getWorkspaceById(99L);
        assertFalse(Boolean.TRUE.equals(dto.getHasEncryptionKey()));
    }

    @Test
    @DisplayName("getWorkspaceById hasEncryptionKey is true when encrypted key present")
    void encryptedKeyShowsTrueInDto() {
        authenticate(alice);
        Workspace encrypted = ws(88L, "enc", alice, Collections.emptySet());
        encrypted.setWorkspaceEncryptionKey("wrapped-key-base64");
        when(workspaceRepository.findById(88L)).thenReturn(Optional.of(encrypted));

        WorkspaceResponseDto dto = workspaceService.getWorkspaceById(88L);
        assertTrue(Boolean.TRUE.equals(dto.getHasEncryptionKey()),
                "hasEncryptionKey should be TRUE when wrapped workspace key exists in DB");
    }
}
