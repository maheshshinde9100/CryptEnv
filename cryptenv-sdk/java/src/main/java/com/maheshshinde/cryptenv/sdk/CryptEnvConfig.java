package com.maheshshinde.cryptenv.sdk;

public class CryptEnvConfig {
    private String apiUrl;
    private String email;
    private String password;
    private String apiKey;
    private String token;
    private String workspaceId;
    private String workspace;
    private String environment;
    private String masterKey;

    public CryptEnvConfig() {
        this.apiUrl = getEnvOrDefault("CRYPTENV_API_URL", "https://cryptenv-backend.onrender.com");
        this.email = System.getenv("CRYPTENV_EMAIL");
        this.password = System.getenv("CRYPTENV_PASSWORD");
        this.apiKey = System.getenv("CRYPTENV_API_KEY");
        this.token = System.getenv("CRYPTENV_TOKEN");
        this.workspaceId = System.getenv("CRYPTENV_WORKSPACE_ID");
        this.workspace = System.getenv("CRYPTENV_WORKSPACE");
        this.environment = getEnvOrDefault("CRYPTENV_ENVIRONMENT", "production");
        this.masterKey = getEnvOrDefault("CRYPTENV_MASTER_KEY",
                getEnvOrDefault("CRYPTENV_WORKSPACE_ENCRYPTION_KEY", null));

        if (this.apiUrl.endsWith("/api")) {
            this.apiUrl = this.apiUrl.substring(0, this.apiUrl.length() - 4);
        } else if (this.apiUrl.endsWith("/")) {
            this.apiUrl = this.apiUrl.substring(0, this.apiUrl.length() - 1);
        }
    }

    private CryptEnvConfig(Builder b) {
        this.apiUrl = b.apiUrl != null ? b.apiUrl : getEnvOrDefault("CRYPTENV_API_URL", "https://cryptenv-backend.onrender.com");
        this.email = b.email != null ? b.email : System.getenv("CRYPTENV_EMAIL");
        this.password = b.password != null ? b.password : System.getenv("CRYPTENV_PASSWORD");
        this.apiKey = b.apiKey != null ? b.apiKey : System.getenv("CRYPTENV_API_KEY");
        this.token = b.token != null ? b.token : System.getenv("CRYPTENV_TOKEN");
        this.workspaceId = b.workspaceId != null ? b.workspaceId : System.getenv("CRYPTENV_WORKSPACE_ID");
        this.workspace = b.workspace != null ? b.workspace : System.getenv("CRYPTENV_WORKSPACE");
        this.environment = b.environment != null ? b.environment
                : getEnvOrDefault("CRYPTENV_ENVIRONMENT", "production");
        this.masterKey = b.masterKey != null ? b.masterKey
                : getEnvOrDefault("CRYPTENV_MASTER_KEY",
                        getEnvOrDefault("CRYPTENV_WORKSPACE_ENCRYPTION_KEY", null));

        if (this.apiUrl.endsWith("/api")) {
            this.apiUrl = this.apiUrl.substring(0, this.apiUrl.length() - 4);
        } else if (this.apiUrl.endsWith("/")) {
            this.apiUrl = this.apiUrl.substring(0, this.apiUrl.length() - 1);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    public String getApiUrl() { return apiUrl; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getApiKey() { return apiKey; }
    public String getToken() { return token; }
    public String getWorkspaceId() { return workspaceId; }
    public String getWorkspace() { return workspace; }
    public String getEnvironment() { return environment; }
    public String getMasterKey() { return masterKey; }

    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setToken(String token) { this.token = token; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setMasterKey(String masterKey) { this.masterKey = masterKey; }

    public static class Builder {
        private String apiUrl;
        private String email;
        private String password;
        private String apiKey;
        private String token;
        private String workspaceId;
        private String workspace;
        private String environment;
        private String masterKey;

        public Builder apiUrl(String v) { this.apiUrl = v; return this; }
        public Builder email(String v) { this.email = v; return this; }
        public Builder password(String v) { this.password = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder token(String v) { this.token = v; return this; }
        public Builder workspaceId(String v) { this.workspaceId = v; return this; }
        public Builder workspace(String v) { this.workspace = v; return this; }
        public Builder environment(String v) { this.environment = v; return this; }
        public Builder masterKey(String v) { this.masterKey = v; return this; }

        public CryptEnvConfig build() {
            return new CryptEnvConfig(this);
        }
    }
}
