package com.maheshshinde.CryptEnv.config;

import com.maheshshinde.CryptEnv.service.WebAuthnService;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(
            WebAuthnService webAuthnService,
            @Value("${cryptenv.webauthn.rp-id:localhost}") String relyingPartyId,
            @Value("${cryptenv.webauthn.rp-name:CryptEnv}") String relyingPartyName,
            @Value("${cryptenv.webauthn.origins:http://localhost:5173,http://localhost:8080}") String configuredOrigins) {
        Set<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        if (origins.isEmpty()) {
            throw new IllegalStateException("At least one WebAuthn origin must be configured");
        }

        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(relyingPartyId)
                        .name(relyingPartyName)
                        .build())
                .credentialRepository(webAuthnService)
                .origins(origins)
                .build();
    }
}