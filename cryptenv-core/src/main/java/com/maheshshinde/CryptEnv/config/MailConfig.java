package com.maheshshinde.CryptEnv.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * SMTP wiring for the email notification channel (§10).
 * <p>
 * The {@link JavaMailSender} bean is created <em>only</em> when {@code spring.mail.host} is set,
 * so a deployment without SMTP configured still boots and simply skips email delivery.
 */
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    public JavaMailSender javaMailSender(Environment env) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(env.getProperty("spring.mail.host"));
        sender.setPort(Integer.parseInt(env.getProperty("spring.mail.port", "587")));

        String username = env.getProperty("spring.mail.username");
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        String password = env.getProperty("spring.mail.password");
        if (password != null && !password.isBlank()) {
            sender.setPassword(password);
        }

        Properties props = sender.getJavaMailProperties();
        boolean hasCredentials = username != null && !username.isBlank();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth",
                env.getProperty("spring.mail.properties.mail.smtp.auth", String.valueOf(hasCredentials)));
        props.put("mail.smtp.starttls.enable",
                env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", String.valueOf(hasCredentials)));
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        return sender;
    }
}
