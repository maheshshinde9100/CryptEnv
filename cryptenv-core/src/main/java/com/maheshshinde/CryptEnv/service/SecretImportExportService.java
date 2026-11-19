package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.dto.SecretCreateDto;
import com.maheshshinde.CryptEnv.dto.SecretResponseDto;
import com.maheshshinde.CryptEnv.dto.SecretUpdateDto;
import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.Secret;
import com.maheshshinde.CryptEnv.repository.SecretRepository;
import com.maheshshinde.CryptEnv.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecretImportExportService {

    private final SecretService secretService;
    private final SecretRepository secretRepository;

    @Transactional
    public Map<String, Object> importSecrets(Long environmentId, String format, String content) {
        Map<String, String> parsed = parse(format, content);
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, String> e : parsed.entrySet()) {
            try {
                Optional<Secret> existing = secretRepository.findByEnvironmentIdAndKey(environmentId, e.getKey());
                if (existing.isPresent()) {
                    secretService.updateSecret(environmentId, e.getKey(),
                            SecretUpdateDto.builder().value(e.getValue()).build());
                    updated++;
                } else {
                    secretService.createSecret(SecretCreateDto.builder()
                            .key(e.getKey())
                            .value(e.getValue())
                            .environmentId(environmentId)
                            .encrypted(true)
                            .build());
                    created++;
                }
            } catch (Exception ex) {
                errors.add(e.getKey() + ": " + ex.getMessage());
            }
        }
        return Map.of("created", created, "updated", updated, "errors", errors, "total", parsed.size());
    }

    @Transactional(readOnly = true)
    public String exportSecrets(Long environmentId, String format) {
        List<SecretResponseDto> secrets = secretService.getSecretsByEnvironment(environmentId);
        return switch (format == null ? "ENV" : format.toUpperCase()) {
            case "JSON" -> toJson(secrets);
            case "YAML" -> toYaml(secrets);
            default -> toEnv(secrets);
        };
    }

    @Transactional
    public Map<String, Object> bulkUpsert(Long environmentId, List<Map<String, String>> items) {
        int created = 0;
        int updated = 0;
        for (Map<String, String> item : items) {
            String key = item.get("key");
            String value = item.get("value");
            if (key == null || value == null) continue;
            if (secretRepository.existsByEnvironmentIdAndKey(environmentId, key)) {
                secretService.updateSecret(environmentId, key,
                        SecretUpdateDto.builder().value(value).description(item.get("description")).build());
                updated++;
            } else {
                secretService.createSecret(SecretCreateDto.builder()
                        .key(key).value(value).description(item.get("description"))
                        .environmentId(environmentId).encrypted(true).build());
                created++;
            }
        }
        return Map.of("created", created, "updated", updated);
    }

    public static String fingerprint(String plaintext) {
        return EncryptionService.fingerprint(plaintext);
    }

    private Map<String, String> parse(String format, String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        String fmt = format == null ? "ENV" : format.toUpperCase();
        return switch (fmt) {
            case "JSON" -> parseJson(content);
            case "YAML" -> parseYamlSimple(content);
            default -> parseEnv(content);
        };
    }

    private Map<String, String> parseEnv(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseJson(String content) {
        // Minimal JSON object parser for flat string maps without pulling Jackson into this path twice —
        // Spring already has ObjectMapper via Jackson on classpath.
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> raw = mapper.readValue(content, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
    }

    private Map<String, String> parseYamlSimple(String content) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    private String toEnv(List<SecretResponseDto> secrets) {
        return secrets.stream()
                .map(s -> s.getKey() + "=" + escapeEnv(s.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private String toJson(List<SecretResponseDto> secrets) {
        try {
            Map<String, String> map = new LinkedHashMap<>();
            secrets.forEach(s -> map.put(s.getKey(), s.getValue()));
            return new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String toYaml(List<SecretResponseDto> secrets) {
        StringBuilder sb = new StringBuilder();
        for (SecretResponseDto s : secrets) {
            sb.append(s.getKey()).append(": ").append(quoteYaml(s.getValue())).append("\n");
        }
        return sb.toString();
    }

    private String escapeEnv(String v) {
        if (v == null) return "";
        if (v.contains(" ") || v.contains("#") || v.contains("=")) {
            return "\"" + v.replace("\"", "\\\"") + "\"";
        }
        return v;
    }

    private String quoteYaml(String v) {
        if (v == null) return "\"\"";
        return "\"" + v.replace("\"", "\\\"") + "\"";
    }
}
