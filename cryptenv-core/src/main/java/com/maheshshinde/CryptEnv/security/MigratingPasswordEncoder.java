package com.maheshshinde.CryptEnv.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Uses Argon2id for new hashes while safely accepting legacy BCrypt rows for one-login migration. */
public final class MigratingPasswordEncoder implements PasswordEncoder {
    private final Argon2PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override public String encode(CharSequence rawPassword) { return argon2.encode(rawPassword); }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encodedPassword != null && (isBcrypt(encodedPassword)
                ? bcrypt.matches(rawPassword, encodedPassword)
                : argon2.matches(rawPassword, encodedPassword));
    }
    @Override public boolean upgradeEncoding(String encodedPassword) {
        return isBcrypt(encodedPassword) || argon2.upgradeEncoding(encodedPassword);
    }
    private boolean isBcrypt(String hash) { return hash != null && hash.startsWith("$2"); }
}
