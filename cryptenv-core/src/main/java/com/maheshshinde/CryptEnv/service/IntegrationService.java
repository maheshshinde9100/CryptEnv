package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Integration;
import com.maheshshinde.CryptEnv.model.IntegrationSyncLog;
import com.maheshshinde.CryptEnv.repository.IntegrationRepository;
import com.maheshshinde.CryptEnv.repository.IntegrationSyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final IntegrationSyncLogRepository integrationSyncLogRepository;

    private static final Map<String, IntegrationConfig> INTEGRATION_CONFIGS = Map.ofEntries(
        // Source Control
        Map.entry("github", new IntegrationConfig("GitHub", "Source Control", "https://github.com")),
        Map.entry("gitlab", new IntegrationConfig("GitLab", "Source Control", "https://gitlab.com")),
        Map.entry("bitbucket", new IntegrationConfig("Bitbucket", "Source Control", "https://bitbucket.org")),
        
        // Deployment
        Map.entry("vercel", new IntegrationConfig("Vercel", "Deployment", "https://vercel.com")),
        Map.entry("netlify", new IntegrationConfig("Netlify", "Deployment", "https://netlify.com")),
        Map.entry("railway", new IntegrationConfig("Railway", "Deployment", "https://railway.app")),
        Map.entry("flyio", new IntegrationConfig("Fly.io", "Deployment", "https://fly.io")),
        Map.entry("render", new IntegrationConfig("Render", "Deployment", "https://render.com")),
        Map.entry("heroku", new IntegrationConfig("Heroku", "Deployment", "https://heroku.com")),
        Map.entry("circleci", new IntegrationConfig("CircleCI", "Deployment", "https://circleci.com")),
        Map.entry("jenkins", new IntegrationConfig("Jenkins", "Deployment", "https://jenkins.io")),
        Map.entry("travisci", new IntegrationConfig("Travis CI", "Deployment", "https://travis-ci.com")),
        Map.entry("buildkite", new IntegrationConfig("Buildkite", "Deployment", "https://buildkite.com")),
        Map.entry("bitbucket_pipelines", new IntegrationConfig("Bitbucket Pipelines", "Deployment", "https://bitbucket.org")),
        Map.entry("gitlab_self_managed", new IntegrationConfig("GitLab Self-Managed", "Deployment", null)),
        
        // Cloud
        Map.entry("aws", new IntegrationConfig("AWS", "Cloud", "https://aws.amazon.com")),
        Map.entry("google_cloud", new IntegrationConfig("Google Cloud", "Cloud", "https://cloud.google.com")),
        Map.entry("azure", new IntegrationConfig("Azure", "Cloud", "https://azure.microsoft.com")),
        Map.entry("digitalocean", new IntegrationConfig("DigitalOcean", "Cloud", "https://digitalocean.com")),
        Map.entry("cloudflare", new IntegrationConfig("Cloudflare", "Cloud", "https://cloudflare.com")),
        Map.entry("terraform_cloud", new IntegrationConfig("Terraform Cloud", "Cloud", "https://app.terraform.io")),
        
        // Databases
        Map.entry("supabase", new IntegrationConfig("Supabase", "Cloud", "https://supabase.com")),
        Map.entry("planetscale", new IntegrationConfig("PlanetScale", "Cloud", "https://planetscale.com")),
        Map.entry("hasura_cloud", new IntegrationConfig("Hasura Cloud", "Cloud", "https://hasura.io")),
        
        // Secrets Management
        Map.entry("doppler", new IntegrationConfig("Doppler", "Cloud", "https://doppler.com")),
        Map.entry("hashicorp_vault", new IntegrationConfig("HashiCorp Vault", "Cloud", "https://vaultproject.io")),
        Map.entry("bitwarden", new IntegrationConfig("Bitwarden", "Cloud", "https://bitwarden.com")),
        Map.entry("1password", new IntegrationConfig("1Password", "Cloud", "https://1password.com")),
        
        // Notifications
        Map.entry("slack", new IntegrationConfig("Slack", "Notifications", "https://slack.com")),
        Map.entry("discord", new IntegrationConfig("Discord", "Notifications", "https://discord.com")),
        Map.entry("discord_webhook", new IntegrationConfig("Discord Webhook", "Notifications", null)),
        Map.entry("teams", new IntegrationConfig("Microsoft Teams", "Notifications", "https://teams.microsoft.com")),
        Map.entry("mattermost", new IntegrationConfig("Mattermost", "Notifications", "https://mattermost.com")),
        Map.entry("pagerduty", new IntegrationConfig("PagerDuty", "Notifications", "https://pagerduty.com")),
        Map.entry("opsgenie", new IntegrationConfig("Opsgenie", "Notifications", "https://opsgenie.com")),
        Map.entry("telegram", new IntegrationConfig("Telegram", "Notifications", "https://telegram.org")),
        Map.entry("email", new IntegrationConfig("Email", "Notifications", null)),
        
        // Monitoring & Observability
        Map.entry("sentry", new IntegrationConfig("Sentry", "Cloud", "https://sentry.io")),
        
        // E-commerce
        Map.entry("shopify", new IntegrationConfig("Shopify", "Cloud", "https://shopify.com")),
        
        // Communication
        Map.entry("twilio", new IntegrationConfig("Twilio", "Cloud", "https://twilio.com")),
        
        // Project Management
        Map.entry("linear", new IntegrationConfig("Linear", "Cloud", "https://linear.app")),
        
        // CMS
        Map.entry("ghost_cms", new IntegrationConfig("Ghost CMS", "Cloud", "https://ghost.org")),
        Map.entry("appwrite", new IntegrationConfig("Appwrite", "Cloud", "https://appwrite.io")),
        Map.entry("notion", new IntegrationConfig("Notion", "Cloud", "https://notion.so")),
        
        // Backend Services
        Map.entry("firebase", new IntegrationConfig("Firebase", "Cloud", "https://firebase.google.com")),
        
        // Productivity
        Map.entry("google_drive", new IntegrationConfig("Google Drive", "Cloud", "https://drive.google.com")),
        Map.entry("zapier", new IntegrationConfig("Zapier", "Cloud", "https://zapier.com")),
        
        // API Testing
        Map.entry("postman", new IntegrationConfig("Postman", "Cloud", "https://postman.com")),
        Map.entry("checkly", new IntegrationConfig("Checkly", "Cloud", "https://checklyhq.com"))
    );

    @Transactional
    public Integration connectIntegration(Long workspaceId, String integrationType, String token, 
                                         Map<String, Object> config, Long createdBy) {
        IntegrationConfig integrationConfig = INTEGRATION_CONFIGS.get(integrationType);
        if (integrationConfig == null) {
            throw new IllegalArgumentException("Unknown integration type: " + integrationType);
        }

        Optional<Integration> existing = integrationRepository.findByWorkspaceIdAndIntegrationType(workspaceId, integrationType);
        if (existing.isPresent()) {
            Integration integration = existing.get();
            integration.setToken(token);
            integration.setConfig(serializeConfig(config));
            integration.setIsConnected(true);
            integration.setUpdatedAt(LocalDateTime.now());
            return integrationRepository.save(integration);
        }

        Integration integration = Integration.builder()
                .workspaceId(workspaceId)
                .integrationType(integrationType)
                .integrationName(integrationConfig.name())
                .category(integrationConfig.category())
                .token(token)
                .config(serializeConfig(config))
                .isConnected(true)
                .syncStatus("idle")
                .createdBy(createdBy)
                .build();
        return integrationRepository.save(integration);
    }

    @Transactional
    public void disconnectIntegration(Long workspaceId, String integrationType) {
        Integration integration = integrationRepository.findByWorkspaceIdAndIntegrationType(workspaceId, integrationType)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found"));
        integration.setIsConnected(false);
        integration.setToken(null);
        integration.setSyncStatus("idle");
        integrationRepository.save(integration);
    }

    public List<Integration> getWorkspaceIntegrations(Long workspaceId) {
        return integrationRepository.findByWorkspaceId(workspaceId);
    }

    public List<Integration> getWorkspaceIntegrationsByCategory(Long workspaceId, String category) {
        return integrationRepository.findByWorkspaceIdAndCategory(workspaceId, category);
    }

    public List<Map<String, Object>> getAllIntegrationTypes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, IntegrationConfig> entry : INTEGRATION_CONFIGS.entrySet()) {
            Map<String, Object> integration = new HashMap<>();
            integration.put("type", entry.getKey());
            integration.put("name", entry.getValue().name());
            integration.put("category", entry.getValue().category());
            integration.put("url", entry.getValue().url());
            result.add(integration);
        }
        return result;
    }

    @Transactional
    public IntegrationSyncLog syncIntegration(Long integrationId, String syncType) {
        Integration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new IllegalArgumentException("Integration not found"));

        integration.setSyncStatus("syncing");
        integrationRepository.save(integration);

        IntegrationSyncLog syncLog = IntegrationSyncLog.builder()
                .integrationId(integrationId)
                .syncType(syncType)
                .status("in_progress")
                .build();
        syncLog = integrationSyncLogRepository.save(syncLog);

        try {
            // Perform sync based on integration type
            int recordsSynced = performSync(integration, syncType);
            
            syncLog.setStatus("completed");
            syncLog.setRecordsSynced(recordsSynced);
            syncLog.setCompletedAt(LocalDateTime.now());
            integrationSyncLogRepository.save(syncLog);

            integration.setSyncStatus("idle");
            integration.setLastSyncedAt(LocalDateTime.now());
            integrationRepository.save(integration);

            return syncLog;
        } catch (Exception e) {
            log.error("Sync failed for integration {}", integrationId, e);
            syncLog.setStatus("failed");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setCompletedAt(LocalDateTime.now());
            integrationSyncLogRepository.save(syncLog);

            integration.setSyncStatus("error");
            integration.setSyncError(e.getMessage());
            integrationRepository.save(integration);

            return syncLog;
        }
    }

    public List<IntegrationSyncLog> getIntegrationSyncLogs(Long integrationId) {
        return integrationSyncLogRepository.findByIntegrationIdOrderByStartedAtDesc(integrationId);
    }

    private int performSync(Integration integration, String syncType) {
        // In a real implementation, this would call the specific integration's API
        // For now, we'll simulate a sync
        log.info("Syncing {} for integration {}", syncType, integration.getIntegrationType());
        return 10; // Simulate 10 records synced
    }

    private String serializeConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize config", e);
            return null;
        }
    }

    private Map<String, Object> deserializeConfig(String config) {
        if (config == null || config.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(config, Map.class);
        } catch (Exception e) {
            log.error("Failed to deserialize config", e);
            return new HashMap<>();
        }
    }

    private record IntegrationConfig(String name, String category, String url) {}
}
