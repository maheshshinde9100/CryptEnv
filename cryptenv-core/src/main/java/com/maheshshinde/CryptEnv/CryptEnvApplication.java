package com.maheshshinde.CryptEnv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CryptEnvApplication {

	public static void main(String[] args) {
		loadEnv();
		SpringApplication.run(CryptEnvApplication.class, args);
	}

	private static void loadEnv() {
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
						// Remove surrounding quotes if present
						if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
							value = value.substring(1, value.length() - 1);
						} else if (value.startsWith("'") && value.endsWith("'") && value.length() > 1) {
							value = value.substring(1, value.length() - 1);
						}
						System.setProperty(key, value);
					}
				}
			} catch (java.io.IOException e) {
				System.err.println("Failed to load environment file: " + e.getMessage());
			}
		}
	}
}
