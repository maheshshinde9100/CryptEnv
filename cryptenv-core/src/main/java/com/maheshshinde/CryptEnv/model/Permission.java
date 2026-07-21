package com.maheshshinde.CryptEnv.model;

public enum Permission {
    // Secret permissions
    SECRET_READ,
    SECRET_WRITE,
    SECRET_DELETE,
    
    // Workspace permissions
    WORKSPACE_READ,
    WORKSPACE_WRITE,
    WORKSPACE_DELETE,
    WORKSPACE_MANAGE_MEMBERS,
    
    // User permissions
    USER_READ,
    USER_WRITE,
    USER_DELETE,
    
    // Audit permissions
    AUDIT_READ,
    AUDIT_DELETE;
    
    public static boolean hasPermission(Role role, Permission permission) {
        switch (role) {
            case OWNER:
                return true; // Owner has all permissions
            case ADMIN:
                return permission != Permission.AUDIT_DELETE;
            case DEVELOPER:
                return permission == Permission.SECRET_READ ||
                       permission == Permission.SECRET_WRITE ||
                       permission == Permission.SECRET_DELETE ||
                       permission == Permission.WORKSPACE_READ ||
                       permission == Permission.WORKSPACE_WRITE ||
                       permission == Permission.WORKSPACE_DELETE;
            case AUDITOR:
                return permission == Permission.SECRET_READ ||
                       permission == Permission.WORKSPACE_READ ||
                       permission == Permission.AUDIT_READ;
            default:
                return false;
        }
    }
}
