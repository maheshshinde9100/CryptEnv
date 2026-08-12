package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.SecretCreateDto;
import com.maheshshinde.CryptEnv.dto.SecretResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.*;
import com.maheshshinde.CryptEnv.repository.EnvironmentRepository;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretService — Secret Isolation + Encryption Path Tests")
class SecretIsolationServiceTest {

    @Mock private SecretRepository secretRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private UserRepository userRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private SecurityService securityService;
    @Mock private WorkspaceService workspaceService;

    @InjectMocks private SecretService secretService;

    private User alice;
    private User bob;
    private User mallory;

    private Workspace aliceWs;
    private Workspace bobWs;
    private Workspace bobSharedWithAlice;

    private Environment aliceEnv;
    private Environment bobEnv;
    private Environment bobSharedEnv;

    private Secret aliceSecret;
    private Secret bobSecret;
    private Secret bobSharedSecret;

    private static final String ALICE_WORKSPACE_KEY_B64 = "dGVzdC10ZXN0LXRlc3QtdGVzdC10ZXN0LXRlc3QtdGU=";

    @BeforeEach
    void setUp() {
        alice = user(1L, "alice@x.com", "alice");
        bob = user(2L, "bob@x.com", "bob");
        mallory = user(3L, "mallory@x.com", "mallory");

        aliceWs = ws(10L, "alice-ws", alice, Collections.emptySet());
        aliceWs.setWorkspaceEncryptionKey("wrapped-key");

        bobWs = ws(20L, "bob-ws", bob, Collections.emptySet());
        bobWs.setWorkspaceEncryptionKey("bob-wrapped-key");

        bobSharedWithAlice = ws(21L, "bob-shared", bob, new HashSet<>(List.of(alice)));
        bobSharedWithAlice.setWorkspaceEncryptionKey("bob-shared-wrapped");

        aliceEnv = env(100L, aliceWs, Environment.EnvironmentType.DEVELOPMENT);
        bobEnv = env(200L, bobWs, Environment.EnvironmentType.PRODUCTION);
        bobSharedEnv = env(210L, bobSharedWithAlice, Environment.EnvironmentType.STAGING);

        aliceSecret = secret(1000L, aliceEnv, "DB_URL", "postgresql://alice-db/alice",
                "ENC[alice_db_ct]", true);
        bobSecret = secret(2000L, bobEnv, "STRIPE_KEY", "sk_live_bob_realkey",
                "ENC[bob_stripe_ct]", true);
        bobSharedSecret = secret(2100L, bobSharedEnv, "API_TOKEN",
                "shared-token-value", "ENC[shared_ct]", true);
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
                .id(id).name(name).owner(owner).members(members)
                .description("desc").environments(new HashSet<>())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private Environment env(Long id, Workspace ws, Environment.EnvironmentType t) {
        return Environment.builder()
                .id(id).name(t).workspace(ws).isActive(true)
                .build();
    }

    private Secret secret(Long id, Environment env, String key, String valueRaw,
                          String encVal, boolean encrypted) {
        return Secret.builder()
                .id(id).environment(env).key(key).value(encVal)
                .description("secret " + key).encrypted(encrypted)
                .encryptedValue(encVal).currentVersion(1)
                .version(1).isActive(true).isDeleted(false)
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
        when(securityService.getCurrentUser()).thenReturn(u);
    }

    // ================================================================
    // CRITICAL ISOLATION TESTS
    // ================================================================

    @Test
    @DisplayName("getAllSecretsForCurrentUser: Alice sees own secrets + member workspace secrets, NOT Bob-only secrets")
    void aliceGetsOwnPlusMemberSecretsOnly() {
        authenticate(alice);
        when(secretRepository.findByEnvironmentWorkspaceOwnerOrMemberId(alice.getId()))
                .thenReturn(List.of(aliceSecret, bobSharedSecret));
        when(workspaceService.getDecryptedWorkspaceKey(aliceWs.getId()))
                .thenReturn(ALICE_WORKSPACE_KEY_B64);
        when(workspaceService.getDecryptedWorkspaceKey(bobSharedWithAlice.getId()))
                .thenReturn(ALICE_WORKSPACE_KEY_B64);
        when(encryptionService.decryptWithKey(eq("ENC[alice_db_ct]"), anyString()))
                .thenReturn("postgresql://alice-db/alice");
        when(encryptionService.decryptWithKey(eq("ENC[shared_ct]"), anyString()))
                .thenReturn("shared-token-value");

        List<SecretResponseDto> dtos = secretService.getAllSecretsForCurrentUser();

        assertEquals(2, dtos.size());
        Set<String> keys = new HashSet<>();
        for (SecretResponseDto d : dtos) keys.add(d.getKey());
        assertTrue(keys.contains("DB_URL"), "Alice must see her own DB_URL secret");
        assertTrue(keys.contains("API_TOKEN"), "Alice must see secrets in the workspace Bob shared with her");
        assertFalse(keys.contains("STRIPE_KEY"),
                "Alice MUST NOT see Bob's STRIPE_KEY from Bob's unshared workspace!");
    }

    @Test
    @DisplayName("getAllSecretsForCurrentUser: Mallory sees zero secrets (no workspaces)")
    void malloryGetsNoSecrets() {
        authenticate(mallory);
        when(secretRepository.findByEnvironmentWorkspaceOwnerOrMemberId(mallory.getId()))
                .thenReturn(Collections.emptyList());
        assertTrue(secretService.getAllSecretsForCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("getSecretByKey: Bob can retrieve his own STRIPE_KEY")
    void bobCanGetHisOwnSecret() {
        authenticate(bob);
        when(secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId("STRIPE_KEY", bob.getId()))
                .thenReturn(List.of(bobSecret));
        when(workspaceService.getDecryptedWorkspaceKey(bobWs.getId()))
                .thenReturn(ALICE_WORKSPACE_KEY_B64);
        when(encryptionService.decryptWithKey("ENC[bob_stripe_ct]", ALICE_WORKSPACE_KEY_B64))
                .thenReturn("sk_live_bob_realkey");

        SecretResponseDto dto = secretService.getSecretByKey("STRIPE_KEY");
        assertEquals("sk_live_bob_realkey", dto.getValue());
    }

    @Test
    @DisplayName("getSecretByKey: Mallory asking for STRIPE_KEY throws NotFound")
    void malloryCannotGetBobSecret() {
        authenticate(mallory);
        when(secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId("STRIPE_KEY", mallory.getId()))
                .thenReturn(Collections.emptyList());
        assertThrows(ResourceNotFoundException.class,
                () -> secretService.getSecretByKey("STRIPE_KEY"));
    }

    @Test
    @DisplayName("getSecretByKey: Alice can see shared secrets but not Bob's private ones")
    void aliceCanAccessSharedSecretOnly() {
        authenticate(alice);
        // Shared: API_TOKEN → should be accessible
        when(secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId("API_TOKEN", alice.getId()))
                .thenReturn(List.of(bobSharedSecret));
        when(workspaceService.getDecryptedWorkspaceKey(bobSharedWithAlice.getId()))
                .thenReturn(ALICE_WORKSPACE_KEY_B64);
        when(encryptionService.decryptWithKey(eq("ENC[shared_ct]"), anyString()))
                .thenReturn("shared-token-value");
        assertEquals("shared-token-value", secretService.getSecretByKey("API_TOKEN").getValue());

        // Bob's private STRIPE_KEY: Alice has NO access, should NOT find
        when(secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId("STRIPE_KEY", alice.getId()))
                .thenReturn(Collections.emptyList());
        assertThrows(ResourceNotFoundException.class,
                () -> secretService.getSecretByKey("STRIPE_KEY"));
    }

    @Test
    @DisplayName("createSecret: encrypts with workspace-specific key, never stores plaintext")
    void createSecretEncryptsWithWorkspaceKey() {
        authenticate(alice);
        SecretCreateDto dto = SecretCreateDto.builder()
                .key("NEW_KEY").value("plaintext-super-secret")
                .environmentId(aliceEnv.getId()).encrypted(true).build();

        when(environmentRepository.findById(aliceEnv.getId())).thenReturn(Optional.of(aliceEnv));
        when(workspaceService.getDecryptedWorkspaceKey(aliceWs.getId()))
                .thenReturn(ALICE_WORKSPACE_KEY_B64);
        when(encryptionService.encryptWithKey(eq("plaintext-super-secret"), eq(ALICE_WORKSPACE_KEY_B64)))
                .thenReturn("ENC[ct-new-key]");
        when(secretRepository.existsByEnvironmentIdAndKey(aliceEnv.getId(), "NEW_KEY"))
                .thenReturn(false);
        when(secretRepository.save(any(Secret.class))).then(returnsFirstArg());

        SecretResponseDto response = secretService.createSecret(dto);
        assertEquals("NEW_KEY", response.getKey());

        verify(encryptionService, times(1))
                .encryptWithKey("plaintext-super-secret", ALICE_WORKSPACE_KEY_B64);
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("deleteSecretGlobal: Mallory cannot delete Alice's secrets")
    void malloryCannotDeleteAliceSecret() {
        authenticate(mallory);
        when(secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId("DB_URL", mallory.getId()))
                .thenReturn(Collections.emptyList());
        assertThrows(ResourceNotFoundException.class,
                () -> secretService.deleteSecretGlobal("DB_URL"));
        verify(secretRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteSecretGlobal: Alice can delete a secret she owns")
    void aliceCanDeleteHerOwnSecret() {
        authenticate(alice);
        when(secretRepository.findByKeyAndEnvironmentWorkspaceOwnerOrMemberId("DB_URL", alice.getId()))
                .thenReturn(List.of(aliceSecret));
        assertDoesNotThrow(() -> secretService.deleteSecretGlobal("DB_URL"));
        verify(secretRepository, times(1)).delete(aliceSecret);
    }
}
