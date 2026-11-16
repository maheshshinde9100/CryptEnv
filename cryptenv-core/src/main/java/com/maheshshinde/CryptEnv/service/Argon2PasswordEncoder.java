package com.maheshshinde.CryptEnv.service;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Argon2PasswordEncoder implements PasswordEncoder {

    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private static final int ITERATIONS = 3;
    private static final int MEMORY = 65536;
    private static final int PARALLELISM = 2;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    @Override
    public String encode(CharSequence rawPassword) {
        return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        try {
            return argon2.verify(encodedPassword, rawPassword.toString());
        } catch (Exception e) {
            log.error("Argon2 verification failed", e);
            return false;
        }
    }

    public void wipe(char[] password) {
        argon2.wipeArray(password);
    }
}
