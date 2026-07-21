package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.model.Role;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public Role getCurrentUserRole() {
        User user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }

    public void checkPermission(Permission permission) {
        User user = getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not authenticated");
        }

        if (!Permission.hasPermission(user.getRole(), permission)) {
            throw new RuntimeException("User does not have permission: " + permission);
        }
    }

    public boolean hasPermission(Permission permission) {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }
        return Permission.hasPermission(user.getRole(), permission);
    }

    public boolean isOwner() {
        return getCurrentUserRole() == Role.OWNER;
    }

    public boolean isAdmin() {
        Role role = getCurrentUserRole();
        return role == Role.OWNER || role == Role.ADMIN;
    }

    public boolean isDeveloper() {
        Role role = getCurrentUserRole();
        return role == Role.OWNER || role == Role.ADMIN || role == Role.DEVELOPER;
    }

    public boolean isAuditor() {
        return getCurrentUserRole() == Role.AUDITOR;
    }
}
