package com.maheshshinde.CryptEnv.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Delivers transactional mail through SMTP when available, or through an HTTPS API
 * (Brevo / Resend) when the host blocks outbound SMTP ports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSender {

    private static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    @Value("${CRYPTENV_MAIL_PROVIDER:auto}")
    private String configuredProvider;

    @Value("${CRYPTENV_NOTIFICATIONS_FROM:no-reply@cryptenv.local}")
    private String fromAddress;

    @Value("${CRYPTENV_NOTIFICATIONS_FROM_NAME:CryptEnv}")
    private String fromName;

    private RestClient http;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(10));
        http = RestClient.builder().requestFactory(factory).build();
        log.info("EmailSender configured: provider={}, from={}", provider(), fromAddress);
    }

    public boolean isConfigured() {
        return !"none".equals(provider());
    }

    /** Returns the active transport: smtp, brevo, resend, or none. */
    public String provider() {
        String requested = configuredProvider == null ? "auto" : configuredProvider.trim().toLowerCase();
        if (!"auto".equals(requested)) {
            return switch (requested) {
                case "smtp" -> mailSender.getIfAvailable() != null ? "smtp" : "none";
                case "brevo" -> StringUtils.hasText(brevoApiKey) ? "brevo" : "none";
                case "resend" -> StringUtils.hasText(resendApiKey) ? "resend" : "none";
                default -> "none";
            };
        }
        if (mailSender.getIfAvailable() != null) return "smtp";
        if (StringUtils.hasText(brevoApiKey)) return "brevo";
        if (StringUtils.hasText(resendApiKey)) return "resend";
        return "none";
    }

    /**
     * Sends a plain-text message. Mail failures are logged and swallowed so that
     * registration stays usable; callers expose a resend action instead.
     */
    public boolean send(String to, String subject, String text) {
        String transport = provider();
        try {
            switch (transport) {
                case "smtp" -> sendSmtp(to, subject, text);
                case "brevo" -> sendBrevo(to, subject, text);
                case "resend" -> sendResend(to, subject, text);
                default -> {
                    log.warn("Mail not sent to {} ({}): no SMTP host, BREVO_API_KEY or RESEND_API_KEY configured",
                            to, subject);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("Mail delivery via {} failed for {}: {}", transport, to, e.getMessage());
            return false;
        }
    }

    private void sendSmtp(String to, String subject, String text) {
        JavaMailSender sender = mailSender.getObject();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }

    private void sendBrevo(String to, String subject, String text) {
        http.post()
                .uri(BREVO_ENDPOINT)
                .header("api-key", brevoApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sender", Map.of("email", fromAddress, "name", fromName),
                        "to", List.of(Map.of("email", to)),
                        "subject", subject,
                        "textContent", text
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private void sendResend(String to, String subject, String text) {
        http.post()
                .uri(RESEND_ENDPOINT)
                .header("Authorization", "Bearer " + resendApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", fromName + " <" + fromAddress + ">",
                        "to", List.of(to),
                        "subject", subject,
                        "text", text
                ))
                .retrieve()
                .toBodilessEntity();
    }
}