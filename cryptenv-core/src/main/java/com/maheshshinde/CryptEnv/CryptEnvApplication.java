package com.maheshshinde.CryptEnv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptEnvApplication {

	public static void main(String[] args) {
		// Strip surrounding quotes from env vars (Render sometimes wraps values in quotes)
		stripQuotedEnvVars(new String[]{"DB_URL", "DB_USER", "DB_PASS", "JWT_SECRET", "MASTER_ENCRYPTION_KEY", "CORS_ALLOWED_ORIGINS"});
		// Load from .env file if present (local dev)
		loadEnvFile();
		SpringApplication.run(CryptEnvApplication.class, args);
	}

	private static void stripQuotedEnvVars(String[] keys) {
		for (String key : keys) {
			String value = System.getenv(key);
			if (value != null) {
				String stripped = value.trim();
				if ((stripped.startsWith("\"") && stripped.endsWith("\"") && stripped.length() > 1) ||
					(stripped.startsWith("'") && stripped.endsWith("'") && stripped.length() > 1)) {
					stripped = stripped.substring(1, stripped.length() - 1);
				}
				// Set as system property so Spring picks it up
				System.setProperty(key, stripped);
			}
		}
	}

	private static void loadEnvFile() {
		java.io.File envFile = new java.io.File(".env.production");
		if (!envFile.exists()) {
			envFile = new java.io.File(".env");
		}
		if (envFile.exists()) {
			try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					int eqIdx = line.indexOf('=');
					if (eqIdx > 0) {
						String key = line.substring(0, eqIdx).trim();
						String value = line.substring(eqIdx + 1).trim();
						if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
							value = value.substring(1, value.length() - 1);
						} else if (value.startsWith("'") && value.endsWith("'") && value.length() > 1) {
							value = value.substring(1, value.length() - 1);
						}
						// Only set if not already in OS environment
						if (System.getenv(key) == null) {
							System.setProperty(key, value);
						}
					}
				}
			} catch (java.io.IOException e) {
				System.err.println("Failed to load environment file: " + e.getMessage());
			}
		}
	}
}

