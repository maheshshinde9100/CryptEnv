package com.maheshshinde.CryptEnv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maheshshinde.CryptEnv.dto.SecretResponseDto;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.ProviderIntegration;
import com.maheshshinde.CryptEnv.model.ProviderSyncJob;
import com.maheshshinde.CryptEnv.model.ProviderSyncState;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.provider.ProviderCatalog;
import com.maheshshinde.CryptEnv.provider.ProviderCredentials;
import com.maheshshinde.CryptEnv.provider.ProviderException;
import com.maheshshinde.CryptEnv.provider.ProviderRegistry;
import com.maheshshinde.CryptEnv.provider.ProviderTarget;
import com.maheshshinde.CryptEnv.provider.SecretProvider;
import com.maheshshinde.CryptEnv.repository.ProviderIntegrationRepository;
import com.maheshshinde.CryptEnv.repository.ProviderSyncJobRepository;
import com.maheshshinde.CryptEnv.repository.ProviderSyncStateRepository;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Provider Sync (§8): connect external platforms, enqueue pushes of workspace secrets, and
 * report sync status. Credentials and queued payloads are always stored as master-key ciphertext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderSyncService {

    private static final int BASE_BACKOFF_SECONDS = 30;
    private static final int MAX_BACKOFF_SECONDS = 1800;

    private final ProviderIntegrationRepository integrationRepository;
    private final ProviderSyncJobRepository jobRepository;
    private final ProviderSyncStateRepository syncStateRepository;
    private final ProviderRegistry providerRegistry;
    private final EncryptionService encryptionService;
    private final SecurityService securityService;
    private final SecretService secretService;
    private final ObjectMapper objectMapper;

    private final SecureRandom random = new SecureRandom();

    // ------------------------------------------------------------------ integrations

    @Transactional(readOnly = true)
    public List<Map<String, Object>> availableProviders() {
        User user = securityService.getCurrentUser();
        Map<String, ProviderIntegration> connected = integrationRepository.findByUserIdOrderByCreatedAtAsc(user.getId())
                .stream()
                .collect(Collectors.toMap(ProviderIntegration::getProvider, i -> i, (a, b) -> a));

        List<Map<String, Object>> providers = new ArrayList<>();
        for (ProviderCatalog.Entry catalogEntry : ProviderCatalog.all()) {
            ProviderIntegration integration = connected.get(catalogEntry.id());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", catalogEntry.id());
            entry.put("name", catalogEntry.name());
            entry.put("category", catalogEntry.category());
            entry.put("description", catalogEntry.description());
            entry.put("docsUrl", catalogEntry.docsUrl());
            entry.put("native", providerRegistry.supports(catalogEntry.id()));
            entry.put("connected", integration != null);
            entry.put("status", integration != null ? integration.getStatus().name() : "DISCONNECTED");
            providers.add(entry);
        }
        return providers;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listIntegrations() {
        User user = securityService.getCurrentUser();
        return integrationRepository.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(this::toIntegrationView)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> connect(String providerId, String token, String name, Map<String, String> config) {
        ProviderCatalog.Entry catalogEntry = ProviderCatalog.find(providerId)
                .orElseThrow(() -> new ProviderException("Unsupported provider: " + providerId));
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("A provider token is required");
        }

        User user = securityService.getCurrentUser();
        Map<String, String> safeConfig = config != null ? config : Map.of();
        ProviderCredentials credentials = new ProviderCredentials(token, safeConfig);

        // Native adapters validate the credentials before persisting anything. Catalog-only
        // platforms store the token so workflows can reference it later.
        String account = providerRegistry.supports(providerId)
                ? providerRegistry.get(providerId).checkHealth(credentials)
                : null;

        String encryptedCredentials = encryptionService.encryptWithMasterKey(
                writeJson(Map.of("token", token)));
        String configJson = writeJson(safeConfig);

        ProviderIntegration integration = integrationRepository
                .findByUserIdAndProvider(user.getId(), providerId)
                .orElseGet(() -> ProviderIntegration.builder()
                        .user(user)
                        .provider(providerId)
                        .build());

        integration.setName(name != null && !name.isBlank() ? name : catalogEntry.name());
        integration.setEncryptedCredentials(encryptedCredentials);
        integration.setConfig(configJson);
        integration.setStatus(ProviderIntegration.Status.CONNECTED);
        integration.setLastCheckedAt(LocalDateTime.now());
        integration.setLastError(null);

        ProviderIntegration saved = integrationRepository.save(integration);
        Map<String, Object> view = toIntegrationView(saved);
        view.put("account", account);
        return view;
    }

    @Transactional
    public Map<String, Object> testConnection(String providerId) {
        ProviderIntegration integration = ownedIntegration(providerId);
        Map<String, Object> view = toIntegrationView(integration);
        if (!providerRegistry.supports(providerId)) {
            integration.setStatus(ProviderIntegration.Status.CONNECTED);
            integration.setLastError(null);
            integration.setLastCheckedAt(LocalDateTime.now());
            integrationRepository.save(integration);
            view.put("healthy", true);
            view.put("status", integration.getStatus().name());
            view.put("note", "Credentials stored; live checks are available for native providers only");
            return view;
        }
        try {
            String account = providerRegistry.get(providerId).checkHealth(credsFor(integration));
            integration.setStatus(ProviderIntegration.Status.CONNECTED);
            integration.setLastError(null);
            view.put("account", account);
            view.put("healthy", true);
        } catch (ProviderException e) {
            integration.setStatus(ProviderIntegration.Status.ERROR);
            integration.setLastError(truncate(e.getMessage()));
            view.put("healthy", false);
            view.put("error", e.getMessage());
        }
        integration.setLastCheckedAt(LocalDateTime.now());
        integrationRepository.save(integration);
        view.put("status", integration.getStatus().name());
        return view;
    }

    @Transactional
    public void disconnect(String providerId) {
        ProviderIntegration integration = ownedIntegration(providerId);
        integrationRepository.delete(integration);
    }

    @Transactional(readOnly = true)
    public List<ProviderTarget> listTargets(String providerId) {
        requireNative(providerId);
        ProviderIntegration integration = ownedIntegration(providerId);
        return providerRegistry.get(providerId).listTargets(credsFor(integration));
    }

    // ------------------------------------------------------------------ sync queue

    @Transactional
    public Map<String, Object> enqueueSync(String providerId, Long environmentId, String target,
                                           List<String> secretKeys) {
        requireNative(providerId);
        ProviderIntegration integration = ownedIntegration(providerId);
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("A sync target is required");
        }

        List<SecretResponseDto> secrets = secretService.getSecretsByEnvironment(environmentId);
        if (secrets.isEmpty()) {
            throw new ResourceNotFoundException("No secrets found in environment " + environmentId);
        }

        Map<String, String> payload = new TreeMap<>();
        for (SecretResponseDto secret : secrets) {
            if (secretKeys != null && !secretKeys.isEmpty() && !secretKeys.contains(secret.getKey())) {
                continue;
            }
            payload.put(secret.getKey(), secret.getValue());
        }
        if (payload.isEmpty()) {
            throw new ResourceNotFoundException("None of the requested secrets exist in environment " + environmentId);
        }

        User user = securityService.getCurrentUser();
        ProviderSyncJob job = ProviderSyncJob.builder()
                .integration(integration)
                .user(user)
                .environmentId(environmentId)
                .provider(providerId)
                .target(target)
                .operation(ProviderSyncJob.Operation.PUSH)
                .payloadEncrypted(encryptionService.encryptWithMasterKey(writeJson(payload)))
                .status(ProviderSyncJob.Status.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .nextAttemptAt(LocalDateTime.now())
                .build();

        return toJobView(jobRepository.save(job), payload.size());
    }

    @Transactional
    public Map<String, Object> enqueueDelete(String providerId, String target, String secretKey) {
        requireNative(providerId);
        ProviderIntegration integration = ownedIntegration(providerId);
        if (target == null || target.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Both target and secretKey are required");
        }

        User user = securityService.getCurrentUser();
        ProviderSyncJob job = ProviderSyncJob.builder()
                .integration(integration)
                .user(user)
                .provider(providerId)
                .target(target)
                .secretKey(secretKey)
                .operation(ProviderSyncJob.Operation.DELETE)
                .status(ProviderSyncJob.Status.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .nextAttemptAt(LocalDateTime.now())
                .build();

        return toJobView(jobRepository.save(job), null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listJobs() {
        User user = securityService.getCurrentUser();
        return jobRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(job -> toJobView(job, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> replay(Long jobId) {
        User user = securityService.getCurrentUser();
        ProviderSyncJob job = jobRepository.findById(jobId)
                .filter(j -> j.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Sync job not found: " + jobId));
        job.setStatus(ProviderSyncJob.Status.PENDING);
        job.setAttempts(0);
        job.setLastError(null);
        job.setResults(null);
        job.setNextAttemptAt(LocalDateTime.now());
        return toJobView(jobRepository.save(job), null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> queueStatus() {
        User user = securityService.getCurrentUser();
        List<ProviderSyncJob> jobs = jobRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ProviderSyncJob.Status status : ProviderSyncJob.Status.values()) {
            counts.put(status.name(), jobs.stream().filter(j -> j.getStatus() == status).count());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", (long) jobs.size());
        result.put("counts", counts);
        result.put("deadLetters", jobs.stream()
                .filter(j -> j.getStatus() == ProviderSyncJob.Status.DEAD)
                .map(j -> toJobView(j, null))
                .collect(Collectors.toList()));
        return result;
    }

    // ------------------------------------------------------------------ worker

    @Transactional
    public Optional<Long> claimNextDueJob() {
        List<ProviderSyncJob> due = jobRepository.findDueJobs(LocalDateTime.now());
        if (due.isEmpty()) {
            return Optional.empty();
        }
        ProviderSyncJob job = due.get(0);
        job.setStatus(ProviderSyncJob.Status.RUNNING);
        job.setAttempts(job.getAttempts() + 1);
        jobRepository.save(job);
        return Optional.of(job.getId());
    }

    @Transactional
    public void executeJob(Long jobId) {
        ProviderSyncJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        String failure = null;
        try {
            SecretProvider provider = providerRegistry.get(job.getProvider());
            ProviderCredentials credentials = credsFor(job.getIntegration());

            if (job.getOperation() == ProviderSyncJob.Operation.DELETE) {
                provider.deleteSecret(credentials, job.getTarget(), job.getSecretKey());
                syncStateRepository.deleteByIntegrationIdAndTargetAndSecretKey(
                        job.getIntegration().getId(), job.getTarget(), job.getSecretKey());
                results.add(result(job.getSecretKey(), true, null));
            } else {
                Map<String, String> payload = readPayload(job.getPayloadEncrypted());
                for (Map.Entry<String, String> entry : payload.entrySet()) {
                    try {
                        provider.pushSecret(credentials, job.getTarget(), entry.getKey(), entry.getValue());
                        recordSyncState(job, entry.getKey(), entry.getValue());
                        results.add(result(entry.getKey(), true, null));
                    } catch (Exception e) {
                        results.add(result(entry.getKey(), false, truncate(e.getMessage())));
                        failure = e.getMessage();
                    }
                }
            }
        } catch (Exception e) {
            failure = e.getMessage();
            log.warn("Provider sync job {} failed: {}", job.getId(), e.getMessage());
        }

        job.setResults(writeJson(results));

        boolean succeeded = failure == null && results.stream().allMatch(r -> Boolean.TRUE.equals(r.get("success")));
        if (succeeded) {
            job.setStatus(ProviderSyncJob.Status.SUCCESS);
            job.setLastError(null);
            job.setNextAttemptAt(null);
        } else {
            job.setLastError(truncate(failure != null ? failure : "One or more secrets failed to sync"));
            if (job.getAttempts() >= job.getMaxAttempts()) {
                job.setStatus(ProviderSyncJob.Status.DEAD);
                job.setNextAttemptAt(null);
                log.error("Provider sync job {} moved to dead-letter after {} attempts", job.getId(), job.getAttempts());
            } else {
                job.setStatus(ProviderSyncJob.Status.FAILED);
                job.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(job.getAttempts())));
            }
        }
        jobRepository.save(job);
    }

    /** Exponential backoff with jitter, capped at {@link #MAX_BACKOFF_SECONDS}. */
    private long backoffSeconds(int attempts) {
        long base = Math.min((long) BASE_BACKOFF_SECONDS * (1L << Math.min(attempts, 10)), MAX_BACKOFF_SECONDS);
        long jitter = (long) (random.nextDouble() * Math.max(base / 4, 1));
        return Math.min(base + jitter, MAX_BACKOFF_SECONDS);
    }

    // ------------------------------------------------------------------ export

    @Transactional(readOnly = true)
    public String exportEnvironment(Long environmentId, String format) {
        Map<String, String> secrets = new TreeMap<>();
        for (SecretResponseDto secret : secretService.getSecretsByEnvironment(environmentId)) {
            secrets.put(secret.getKey(), secret.getValue());
        }

        String normalized = format == null ? "env" : format.toLowerCase();
        return switch (normalized) {
            case "json" -> writeJson(secrets);
            case "compose" -> toCompose(secrets);
            default -> toDotEnv(secrets);
        };
    }

    private String toDotEnv(Map<String, String> secrets) {
        StringBuilder sb = new StringBuilder();
        secrets.forEach((key, value) ->
                sb.append(key).append('=').append(quote(value)).append(System.lineSeparator()));
        return sb.toString();
    }

    private String toCompose(Map<String, String> secrets) {
        StringBuilder sb = new StringBuilder("services:\n  app:\n    environment:\n");
        secrets.forEach((key, value) ->
                sb.append("      ").append(key).append(": ").append(quote(value)).append('\n'));
        return sb.toString();
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ------------------------------------------------------------------ helpers

    /** Upserts the fingerprint of a value just pushed, so drift detection can compare later. */
    private void recordSyncState(ProviderSyncJob job, String secretKey, String value) {
        ProviderIntegration integration = job.getIntegration();
        ProviderSyncState state = syncStateRepository
                .findByIntegrationIdAndTargetAndSecretKey(integration.getId(), job.getTarget(), secretKey)
                .orElseGet(() -> ProviderSyncState.builder()
                        .integration(integration)
                        .user(job.getUser())
                        .provider(job.getProvider())
                        .target(job.getTarget())
                        .secretKey(secretKey)
                        .build());
        state.setEnvironmentId(job.getEnvironmentId());
        state.setLastFingerprint(EncryptionService.fingerprint(value));
        state.setLastSyncedAt(LocalDateTime.now());
        state.setLastStatus("SUCCESS");
        syncStateRepository.save(state);
    }

    private void requireNative(String providerId) {
        if (!providerRegistry.supports(providerId)) {
            String name = ProviderCatalog.find(providerId).map(ProviderCatalog.Entry::name).orElse(providerId);
            throw new ProviderException(name + " does not support secret push yet; connect it for workflow use only");
        }
    }

    private ProviderIntegration ownedIntegration(String providerId) {
        User user = securityService.getCurrentUser();
        return integrationRepository.findByUserIdAndProvider(user.getId(), providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider '" + providerId + "' is not connected"));
    }

    private ProviderCredentials credsFor(ProviderIntegration integration) {
        String json = encryptionService.decryptWithMasterKey(integration.getEncryptedCredentials());
        Map<String, String> creds = readMap(json);
        Map<String, String> config = integration.getConfig() != null
                ? readMap(integration.getConfig())
                : Map.of();
        return new ProviderCredentials(creds.get("token"), config);
    }

    private Map<String, Object> toIntegrationView(ProviderIntegration integration) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", integration.getId());
        view.put("provider", integration.getProvider());
        view.put("name", integration.getName());
        view.put("status", integration.getStatus().name());
        view.put("config", integration.getConfig() != null ? readMap(integration.getConfig()) : Map.of());
        view.put("hasCredentials", integration.getEncryptedCredentials() != null);
        view.put("lastCheckedAt", integration.getLastCheckedAt());
        view.put("lastError", integration.getLastError());
        view.put("createdAt", integration.getCreatedAt());
        return view;
    }

    private Map<String, Object> toJobView(ProviderSyncJob job, Integer secretCount) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", job.getId());
        view.put("provider", job.getProvider());
        view.put("target", job.getTarget());
        view.put("operation", job.getOperation().name());
        view.put("environmentId", job.getEnvironmentId());
        view.put("status", job.getStatus().name());
        view.put("attempts", job.getAttempts());
        view.put("maxAttempts", job.getMaxAttempts());
        view.put("nextAttemptAt", job.getNextAttemptAt());
        view.put("lastError", job.getLastError());
        view.put("results", job.getResults() != null ? readList(job.getResults()) : List.of());
        view.put("createdAt", job.getCreatedAt());
        if (secretCount != null) {
            view.put("secretCount", secretCount);
        }
        return view;
    }

    private Map<String, Object> result(String key, boolean success, String error) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", key);
        entry.put("success", success);
        if (error != null) {
            entry.put("error", error);
        }
        return entry;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 950 ? message.substring(0, 950) : message;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ProviderException("Failed to serialize provider payload", e);
        }
    }

    private Map<String, String> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            throw new ProviderException("Failed to read provider payload", e);
        }
    }

    private Map<String, String> readPayload(String ciphertext) {
        return readMap(encryptionService.decryptWithMasterKey(ciphertext));
    }

    private List<Map<String, Object>> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
