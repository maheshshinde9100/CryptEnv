package com.maheshshinde.cryptenv.sdk;

public class CryptEnvConfig {
    private String apiUrl;
    private String email;
    private String password;
    private String apiKey;
    private String token;
    private String workspaceId;
    private String masterKey;

    public CryptEnvConfig() {
        this.apiUrl = getEnvOrDefault("CRYPTENV_API_URL", "https://cryptenv-backend.onrender.com");
        this.email = System.getenv("CRYPTENV_EMAIL");
        this.password = System.getenv("CRYPTENV_PASSWORD");
        this.apiKey = System.getenv("CRYPTENV_API_KEY");
        this.token = System.getenv("CRYPTENV_TOKEN");
        this.workspaceId = System.getenv("CRYPTENV_WORKSPACE_ID");
        this.masterKey = getEnvOrDefault("CRYPTENV_MASTER_KEY", System.getenv("CRYPTENV_WORKSPACE_ENCRYPTION_KEY"));
        
        if (this.apiUrl.endsWith("/api")) {
            this.apiUrl = this.apiUrl.substring(0, this.apiUrl.length() - 4);
        } else if (this.apiUrl.endsWith("/")) {
            this.apiUrl = this.apiUrl.substring(0, this.apiUrl.length() - 1);
        }
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    // Getters
    public String getApiUrl() { return apiUrl; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getApiKey() { return apiKey; }
    public String getToken() { return token; }
    public String getWorkspaceId() { return workspaceId; }
    public String getMasterKey() { return masterKey; }

    // Setters
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setToken(String token) { this.token = token; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public void setMasterKey(String masterKey) { this.masterKey = masterKey; }
}
