# RBAC and Permission Model Documentation

## Overview

CryptEnv implements a Role-Based Access Control (RBAC) system with four distinct roles and granular permissions to ensure secure access to secrets, workspaces, and audit logs.

## Roles

### 1. Owner
- **Description**: Full administrative control over the entire system
- **Permissions**: All permissions including audit log deletion
- **Use Case**: System administrators and primary account owners

### 2. Admin
- **Description**: Administrative control except audit log deletion
- **Permissions**: All permissions except AUDIT_DELETE
- **Use Case**: Workspace administrators and team leads

### 3. Developer
- **Description**: Read and write access to secrets and workspaces
- **Permissions**: SECRET_READ, SECRET_WRITE, WORKSPACE_READ
- **Use Case**: Developers who need to manage secrets in their workspaces

### 4. Auditor
- **Description**: Read-only access for compliance and auditing
- **Permissions**: SECRET_READ, WORKSPACE_READ, AUDIT_READ
- **Use Case**: Compliance officers, security auditors, and reviewers

## Permissions

### Secret Permissions

| Permission | Description | Owner | Admin | Developer | Auditor |
|------------|-------------|-------|-------|-----------|---------|
| SECRET_READ | View secret values | ✅ | ✅ | ✅ | ✅ |
| SECRET_WRITE | Create/update secrets | ✅ | ✅ | ✅ | ❌ |
| SECRET_DELETE | Delete secrets | ✅ | ✅ | ❌ | ❌ |

### Workspace Permissions

| Permission | Description | Owner | Admin | Developer | Auditor |
|------------|-------------|-------|-------|-----------|---------|
| WORKSPACE_READ | View workspace details | ✅ | ✅ | ✅ | ✅ |
| WORKSPACE_WRITE | Create/update workspaces | ✅ | ✅ | ❌ | ❌ |
| WORKSPACE_DELETE | Delete workspaces | ✅ | ✅ | ❌ | ❌ |
| WORKSPACE_MANAGE_MEMBERS | Invite/remove members | ✅ | ✅ | ❌ | ❌ |

### User Permissions

| Permission | Description | Owner | Admin | Developer | Auditor |
|------------|-------------|-------|-------|-----------|---------|
| USER_READ | View user information | ✅ | ✅ | ❌ | ❌ |
| USER_WRITE | Update user information | ✅ | ✅ | ❌ | ❌ |
| USER_DELETE | Delete users | ✅ | ✅ | ❌ | ❌ |

### Audit Permissions

| Permission | Description | Owner | Admin | Developer | Auditor |
|------------|-------------|-------|-------|-----------|---------|
| AUDIT_READ | View audit logs | ✅ | ✅ | ❌ | ✅ |
| AUDIT_DELETE | Delete audit logs | ✅ | ❌ | ❌ | ❌ |

## Permission Matrix

```
                    OWNER  ADMIN  DEV  AUDITOR
┌─────────────────────────────────────────┐
│ SECRET_READ         ✅     ✅    ✅      ✅ │
│ SECRET_WRITE        ✅     ✅    ✅      ❌ │
│ SECRET_DELETE       ✅     ✅    ❌      ❌ │
│ WORKSPACE_READ      ✅     ✅    ✅      ✅ │
│ WORKSPACE_WRITE     ✅     ✅    ❌      ❌ │
│ WORKSPACE_DELETE    ✅     ✅    ❌      ❌ │
│ WORKSPACE_MEMBERS   ✅     ✅    ❌      ❌ │
│ USER_READ           ✅     ✅    ❌      ❌ │
│ USER_WRITE          ✅     ✅    ❌      ❌ │
│ USER_DELETE         ✅     ✅    ❌      ❌ │
│ AUDIT_READ          ✅     ✅    ❌      ✅ │
│ AUDIT_DELETE        ✅     ❌    ❌      ❌ │
└─────────────────────────────────────────┘
```

## Implementation

### Role Assignment

Roles are assigned to users during registration and can be updated by administrators:

```java
@Entity
public class User {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.DEVELOPER;
}
```

### Permission Checking

The `SecurityService` provides methods to check permissions:

```java
securityService.checkPermission(Permission.SECRET_WRITE);
securityService.hasPermission(Permission.AUDIT_READ);
securityService.isOwner();
securityService.isAdmin();
securityService.isDeveloper();
```

### Permission Evaluation

The `Permission.hasPermission()` method evaluates whether a role has a specific permission:

```java
public static boolean hasPermission(Role role, Permission permission) {
    switch (role) {
        case OWNER:
            return true;
        case ADMIN:
            return permission != Permission.AUDIT_DELETE;
        case DEVELOPER:
            return permission == Permission.SECRET_READ ||
                   permission == Permission.SECRET_WRITE ||
                   permission == Permission.WORKSPACE_READ;
        case AUDITOR:
            return permission == Permission.SECRET_READ ||
                   permission == Permission.WORKSPACE_READ ||
                   permission == Permission.AUDIT_READ;
        default:
            return false;
    }
}
```

## Audit Logging

All actions are logged with the following information:

- **User**: Who performed the action
- **Action**: Type of action (LOGIN, SECRET_CREATE, etc.)
- **Resource Type**: Type of resource affected (SECRET, WORKSPACE, etc.)
- **Resource ID**: ID of the affected resource
- **Success**: Whether the action succeeded
- **IP Address**: Client IP address
- **User Agent**: Client user agent string
- **Timestamp**: When the action occurred
- **Details**: Additional context (error messages, etc.)

### Tracked Actions

- **Authentication**: LOGIN, LOGOUT, FAILED_LOGIN, USER_REGISTER
- **Secrets**: SECRET_CREATE, SECRET_UPDATE, SECRET_DELETE, SECRET_ACCESS
- **Workspaces**: WORKSPACE_CREATE, WORKSPACE_UPDATE, WORKSPACE_DELETE, WORKSPACE_ACCESS, WORKSPACE_MEMBER_ADD
- **Environments**: ENVIRONMENT_CREATE, ENVIRONMENT_UPDATE, ENVIRONMENT_ACCESS

### Audit Log API

```
GET /api/audit-logs
GET /api/audit-logs/user/{userId}
GET /api/audit-logs/action/{action}
GET /api/audit-logs/resource/{resourceType}/{resourceId}
```

Query parameters for filtering:
- `userId`: Filter by user
- `action`: Filter by action type
- `resourceType`: Filter by resource type
- `startDate`: Filter by start date
- `endDate`: Filter by end date
- `success`: Filter by success status
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)
- `sortBy`: Sort field (default: timestamp)
- `sortDir`: Sort direction (default: desc)

## Security Best Practices

1. **Principle of Least Privilege**: Assign the minimum required role for each user
2. **Regular Audits**: Review audit logs regularly for suspicious activity
3. **Role Rotation**: Periodically review and update user roles
4. **Audit Log Retention**: Implement log retention policies (e.g., 90 days)
5. **Failed Login Monitoring**: Monitor and alert on repeated failed login attempts
6. **IP Whitelisting**: Consider IP-based restrictions for sensitive operations

## Testing

RBAC functionality is tested in `RBACIntegrationTest.java`:

```bash
./mvnw test -Dtest=RBACIntegrationTest
```

## Migration Notes

When upgrading to RBAC-enabled version:

1. Run database migration to add `role` column to `users` table
2. Assign default role (DEVELOPER) to existing users
3. Review and update user roles as needed
4. Enable audit logging in production

## Future Enhancements

- Custom roles with configurable permissions
- Role-based API rate limiting
- Temporary role elevation (sudo-like functionality)
- Role inheritance and hierarchy
- Granular resource-level permissions
- Time-based access controls
