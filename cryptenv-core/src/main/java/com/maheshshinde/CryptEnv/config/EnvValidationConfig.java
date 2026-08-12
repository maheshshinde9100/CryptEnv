package com.maheshshinde.CryptEnv.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Configuration
@Profile("!test")
@Slf4j
public class EnvValidationConfig {

    @Value("${DB_URL:__MISSING__}")
    private String dbUrl;

    @Value("${DB_USER:__MISSING__}")
    private String dbUser;

    @Value("${DB_PASS:__MISSING__}")
    private String dbPass;

    @Value("${JWT_SECRET:__MISSING__}")
    private String jwtSecret;

    @Value("${MASTER_ENCRYPTION_KEY:__MISSING__}")
    private String masterKey;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();

        required(errors, "DB_URL", dbUrl);
        required(errors, "DB_USER", dbUser);
        required(errors, "DB_PASS", dbPass);
        required(errors, "JWT_SECRET", jwtSecret);
        required(errors, "MASTER_ENCRYPTION_KEY", masterKey);

        if (dbUrl != null && !"__MISSING__".equals(dbUrl)
                && !dbUrl.startsWith("jdbc:postgresql://")) {
            errors.add("DB_URL must start with 'jdbc:postgresql://'");
        }

        if (jwtSecret != null && !"__MISSING__".equals(jwtSecret)
                && jwtSecret.length() < 32) {
            errors.add("JWT_SECRET must be at least 32 characters (256 bits). " +
                    "Generate with: openssl rand -hex 32");
        }

        if (masterKey != null && !"__MISSING__".equals(masterKey)) {
            try {
                byte[] decoded = Base64.getDecoder().decode(masterKey);
                if (decoded.length < 32) {
                    errors.add("MASTER_ENCRYPTION_KEY decoded length is "
                            + decoded.length
                            + " bytes; must be at least 32 bytes (AES-256). "
                            + "Generate with: openssl rand -base64 32");
                } else {
                    log.info("EnvValidation: MASTER_ENCRYPTION_KEY is valid Base64 " +
                            "({} bytes = AES-{} key)", decoded.length, decoded.length * 8);
                }
            } catch (IllegalArgumentException e) {
                errors.add("MASTER_ENCRYPTION_KEY is not valid Base64. " +
                        "Generate with: openssl rand -base64 32");
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=========================================================================\n");
            sb.append("  CRYPTENV STARTUP FAILED — MISSING OR INVALID ENVIRONMENT VARIABLES\n");
            sb.append("=========================================================================\n");
            sb.append("  All secrets MUST be set via environment variables (see .env.production\n");
            sb.append("  or Render.com Dashboard → Environment).  No hardcoded fallbacks exist\n");
            sb.append("  anywhere in this codebase — this is an intentional security measure.\n\n");
            for (String err : errors) {
                sb.append("    ❌  ").append(err).append("\n");
            }
            sb.append("\n  For detailed setup instructions, see:\n");
            sb.append("    - cryptenv-core/.env.production\n");
            sb.append("    - cryptenv-core/src/main/resources/application.properties.example\n");
            sb.append("=========================================================================\n");
            throw new IllegalStateException(sb.toString());
        }

        log.info("EnvValidation: All 5 required environment variables are set and valid.");
        log.info("EnvValidation: DB_URL={}", maskJdbcUrl(dbUrl));
        log.info("EnvValidation: JWT_SECRET length={} chars ✓", jwtSecret.length());
        log.info("EnvValidation: MASTER_ENCRYPTION_KEY Base64 decode: {} bytes ✓",
                Base64.getDecoder().decode(masterKey).length);
    }

    private void required(List<String> errors, String name, String value) {
        if (!StringUtils.hasText(value) || "__MISSING__".equals(value)) {
            errors.add(name + " is NOT SET — required environment variable is missing.");
        }
    }

    private String maskJdbcUrl(String url) {
        if (url == null) return "(null)";
        int userIdx = url.indexOf("://");
        if (userIdx < 0) return url;
        String proto = url.substring(0, userIdx + 3);
        String rest = url.substring(userIdx + 3);
        int pathIdx = rest.indexOf("/");
        int queryIdx = rest.indexOf("?");
        int cut = pathIdx >= 0 ? pathIdx : (queryIdx >= 0 ? queryIdx : rest.length());
        String hostPart = rest.substring(0, cut);
        String restPart = rest.substring(cut);
        int atIdx = hostPart.indexOf("@");
        String maskedHost;
        if (atIdx >= 0) {
            String userPart = hostPart.substring(0, atIdx);
            int colonIdx = userPart.indexOf(":");
            String maskedUser;
            if (colonIdx >= 0) {
                maskedUser = userPart.substring(0, Math.min(2, colonIdx)) + "***:***";
            } else {
                maskedUser = userPart.substring(0, Math.min(2, userPart.length())) + "***";
            }
            maskedHost = maskedUser + "@" + hostPart.substring(atIdx + 1);
        } else {
            maskedHost = hostPart;
        }
        return proto + maskedHost + restPart;
    }
}
