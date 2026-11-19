package com.maheshshinde.CryptEnv.security;

import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.stereotype.Service;

/** Persists a stronger encoded password after DaoAuthenticationProvider verifies a legacy one. */
@Service
@RequiredArgsConstructor
public class UserPasswordUpgradeService implements UserDetailsPasswordService {
    private final UserRepository users;

    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        User entity = users.findByEmail(user.getUsername()).orElseThrow();
        entity.setPassword(newPassword);
        users.save(entity);
        return UserPrincipal.create(entity);
    }
}
