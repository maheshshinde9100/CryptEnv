package com.maheshshinde.cryptenv.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CryptEnvConfigTest {

    @Test
    public void testBuilderConstructsConfig() {
        CryptEnvConfig cfg = CryptEnvConfig.builder()
                .apiUrl("https://example.com")
                .email("ops@acme.io")
                .password("pw")
                .workspace("prod")
                .environment("production")
                .masterKey("mk-1234")
                .build();

        assertEquals("https://example.com", cfg.getApiUrl());
        assertEquals("ops@acme.io", cfg.getEmail());
        assertEquals("pw", cfg.getPassword());
        assertEquals("prod", cfg.getWorkspace());
        assertEquals("production", cfg.getEnvironment());
        assertEquals("mk-1234", cfg.getMasterKey());
    }

    @Test
    public void testBuilderEnvironmentDefaultsToProduction() {
        CryptEnvConfig cfg = CryptEnvConfig.builder()
                .apiUrl("https://example.com")
                .build();
        assertEquals("production", cfg.getEnvironment());
    }

    @Test
    public void testSettersWork() {
        CryptEnvConfig cfg = new CryptEnvConfig();
        cfg.setApiUrl("https://a.com");
        cfg.setWorkspaceId("w-1");
        cfg.setEnvironment("staging");
        cfg.setMasterKey("key123");
        assertEquals("https://a.com", cfg.getApiUrl());
        assertEquals("w-1", cfg.getWorkspaceId());
        assertEquals("staging", cfg.getEnvironment());
        assertEquals("key123", cfg.getMasterKey());
    }

    @Test
    public void testApiUrlTrailingApiStripped() {
        CryptEnvConfig cfg = CryptEnvConfig.builder()
                .apiUrl("https://example.com/api")
                .build();
        assertEquals("https://example.com", cfg.getApiUrl());
    }

    @Test
    public void testApiUrlTrailingSlashStripped() {
        CryptEnvConfig cfg = CryptEnvConfig.builder()
                .apiUrl("https://example.com/")
                .build();
        assertEquals("https://example.com", cfg.getApiUrl());
    }

    @Test
    public void testSecretValueWrapper() {
        CryptEnvClient.SecretValue sv = new CryptEnvClient.SecretValue("DB_URL", "postgres://x");
        assertEquals("DB_URL", sv.getKey());
        assertEquals("postgres://x", sv.getValue());
        assertEquals("postgres://x", sv.toString());
    }
}
