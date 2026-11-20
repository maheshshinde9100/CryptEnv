package com.maheshshinde.CryptEnv.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Every platform CryptEnv can connect, grouped by category. Native push adapters implement
 * {@link SecretProvider}; the remaining platforms store their token so workflows and
 * notification pipelines can use it without a sync adapter.
 */
public final class ProviderCatalog {

    public static final String SOURCE_CONTROL = "Source Control";
    public static final String DEPLOYMENT = "Deployment";
    public static final String CLOUD = "Cloud";
    public static final String NOTIFICATIONS = "Notifications";

    public record Entry(String id, String name, String category, String description, String docsUrl) {
    }

    private static final List<Entry> ENTRIES = List.of(
            // Source control
            new Entry("github", "GitHub", SOURCE_CONTROL, "Repository access tokens for Actions secrets", "https://github.com"),
            new Entry("gitlab", "GitLab", SOURCE_CONTROL, "Project and group variables from gitlab.com", "https://gitlab.com"),
            new Entry("bitbucket", "Bitbucket", SOURCE_CONTROL, "Repository variables for Bitbucket Cloud", "https://bitbucket.org"),
            new Entry("bitbucket_pipelines", "Bitbucket Pipelines", SOURCE_CONTROL, "Pipeline-scoped deployment variables", "https://bitbucket.org/product/features/pipelines"),
            new Entry("gitlab-self-managed", "GitLab Self-Managed", SOURCE_CONTROL, "Dedicated GitLab instance with a custom API URL", "https://about.gitlab.com"),
            new Entry("gitlab_self_managed", "GitLab Self-Managed (legacy)", SOURCE_CONTROL, "Legacy alias kept for existing integrations", "https://about.gitlab.com"),

            // Deployment
            new Entry("vercel", "Vercel", DEPLOYMENT, "Environment variables for projects and teams", "https://vercel.com"),
            new Entry("netlify", "Netlify", DEPLOYMENT, "Build and runtime environment variables", "https://netlify.com"),
            new Entry("railway", "Railway", DEPLOYMENT, "Service variables for Railway projects", "https://railway.app"),
            new Entry("flyio", "Fly.io", DEPLOYMENT, "Application secrets injected into machines", "https://fly.io"),
            new Entry("render", "Render", DEPLOYMENT, "Environment groups and service secrets", "https://render.com"),
            new Entry("heroku", "Heroku", DEPLOYMENT, "Config vars for Heroku applications", "https://heroku.com"),
            new Entry("circleci", "CircleCI", DEPLOYMENT, "Project environment variables for pipelines", "https://circleci.com"),
            new Entry("jenkins", "Jenkins", DEPLOYMENT, "Credentials for Jenkins jobs and pipelines", "https://jenkins.io"),
            new Entry("travisci", "Travis CI", DEPLOYMENT, "Repository environment variables", "https://travis-ci.com"),
            new Entry("buildkite", "Buildkite", DEPLOYMENT, "Pipeline environment variables and secrets", "https://buildkite.com"),
            new Entry("cloudflare", "Cloudflare", DEPLOYMENT, "Workers and Pages secrets with account ID", "https://cloudflare.com"),
            new Entry("kubernetes", "Kubernetes", DEPLOYMENT, "Cluster secrets delivered to a namespace", "https://kubernetes.io"),

            // Cloud & services
            new Entry("aws", "AWS", CLOUD, "Secrets Manager and Systems Manager parameters", "https://aws.amazon.com"),
            new Entry("google_cloud", "Google Cloud", CLOUD, "Secret Manager entries for Google Cloud projects", "https://cloud.google.com"),
            new Entry("azure", "Azure", CLOUD, "Key Vault secrets for Azure workloads", "https://azure.microsoft.com"),
            new Entry("digitalocean", "DigitalOcean", CLOUD, "App Platform environment variables", "https://digitalocean.com"),
            new Entry("doppler", "Doppler", CLOUD, "Mirror configs from a shared secrets provider", "https://doppler.com"),
            new Entry("hashicorp_vault", "HashiCorp Vault", CLOUD, "KV secrets engine paths", "https://developer.hashicorp.com/vault"),
            new Entry("terraform_cloud", "Terraform Cloud", CLOUD, "Workspace variables for remote runs", "https://app.terraform.io"),
            new Entry("supabase", "Supabase", CLOUD, "Edge Function secrets and project env", "https://supabase.com"),
            new Entry("planetscale", "PlanetScale", CLOUD, "Database passwords and connection strings", "https://planetscale.com"),
            new Entry("hasura_cloud", "Hasura Cloud", CLOUD, "Project env vars and admin secrets", "https://hasura.io"),
            new Entry("postman", "Postman", CLOUD, "Collection and environment variables", "https://postman.com"),
            new Entry("shopify", "Shopify", CLOUD, "App credentials and storefront secrets", "https://shopify.com"),
            new Entry("twilio", "Twilio", CLOUD, "Account SID, auth token and API keys", "https://twilio.com"),
            new Entry("linear", "Linear", CLOUD, "API keys for issue automations", "https://linear.app"),
            new Entry("bitwarden", "Bitwarden", CLOUD, "Shared vault items for rotation workflows", "https://bitwarden.com"),
            new Entry("1password", "1Password", CLOUD, "Vault items via service-account tokens", "https://1password.com"),
            new Entry("ghost_cms", "Ghost CMS", CLOUD, "Admin and integration API keys", "https://ghost.org"),
            new Entry("appwrite", "Appwrite", CLOUD, "Project API keys and function env", "https://appwrite.io"),
            new Entry("firebase", "Firebase", CLOUD, "Config and service-account secrets", "https://firebase.google.com"),
            new Entry("sentry", "Sentry", CLOUD, "DSN and release automation tokens", "https://sentry.io"),
            new Entry("notion", "Notion", CLOUD, "Integration tokens for content sync", "https://notion.so"),
            new Entry("google_drive", "Google Drive", CLOUD, "Backup exports for rotation artifacts", "https://drive.google.com"),
            new Entry("zapier", "Zapier", CLOUD, "Webhook triggers for automated workflows", "https://zapier.com"),
            new Entry("checkly", "Checkly", CLOUD, "Synthetic monitoring environment variables", "https://checklyhq.com"),

            // Notifications
            new Entry("slack", "Slack", NOTIFICATIONS, "Channel alerts for secret and approval events", "https://slack.com"),
            new Entry("discord", "Discord", NOTIFICATIONS, "Server notifications through a bot token", "https://discord.com"),
            new Entry("discord_webhook", "Discord Webhook", NOTIFICATIONS, "Post events to a channel webhook URL", "https://discord.com"),
            new Entry("teams", "Microsoft Teams", NOTIFICATIONS, "Adaptive card alerts in Teams channels", "https://teams.microsoft.com"),
            new Entry("mattermost", "Mattermost", NOTIFICATIONS, "Self-hosted team chat notifications", "https://mattermost.com"),
            new Entry("pagerduty", "PagerDuty", NOTIFICATIONS, "Incident escalation from security events", "https://pagerduty.com"),
            new Entry("opsgenie", "Opsgenie", NOTIFICATIONS, "On-call alerting for leak findings", "https://opsgenie.com"),
            new Entry("telegram", "Telegram", NOTIFICATIONS, "Bot messages for workspace activity", "https://telegram.org"),
            new Entry("email", "Email", NOTIFICATIONS, "SMTP digest and alert delivery", "https://cryptenv.local")
    );

    private static final Map<String, Entry> BY_ID = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::id, entry -> entry));

    private ProviderCatalog() {
    }

    public static List<Entry> all() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String providerId) {
        return providerId == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(providerId));
    }

    public static boolean contains(String providerId) {
        return providerId != null && BY_ID.containsKey(providerId);
    }
}
