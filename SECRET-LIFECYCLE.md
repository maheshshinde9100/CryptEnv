# Secret Lifecycle Management Documentation

## Overview

CryptEnv provides enterprise-grade secret lifecycle management with versioning, rotation, rollback, soft delete, and expiration features to ensure secure and compliant secret management.

## Secret Lifecycle States

```
┌─────────────┐
│   Created   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Active    │ ◄──────┐
└──────┬──────┘        │
       │               │
       ▼               │
┌─────────────┐        │
│ Inactive    │        │
└──────┬──────┘        │
       │               │
       ▼               │
┌─────────────┐        │
│ Soft Deleted│        │
└─────────────┘        │
       │               │
       │               │
       └───────────────┘
```

## Features

### 1. Secret Versioning

Every secret change creates a new version, maintaining a complete history of all changes.

**Model:**
- `SecretVersion` entity stores each version
- Version numbers increment automatically
- Only one version is active at a time
- All versions are encrypted

**APIs:**
```
GET /api/secrets/{key}/versions           - Get version history
GET /api/secrets/{key}/versions/{version} - Get specific version
GET /api/secrets/{key}/versions/active   - Get active version
POST /api/secrets/{key}/versions/rollback/{version} - Rollback to version
```

**Use Cases:**
- Track who changed secrets and when
- Revert to previous values if needed
- Audit compliance requirements
- Debug issues caused by secret changes

### 2. Secret Rotation

Automatic and manual secret rotation to maintain security compliance.

**Configuration:**
- `rotationIntervalDays`: Days between rotations
- `autoRotate`: Enable/disable automatic rotation
- `lastRotatedAt`: Timestamp of last rotation
- `nextRotationAt`: Scheduled next rotation time

**Rotation Scheduler:**
- Runs every hour (configurable via `secret.rotation.cron`)
- Checks secrets needing rotation
- Automatically creates new versions
- Updates rotation timestamps

**APIs:**
```
POST /api/secrets/{key}/lifecycle/rotation-interval?intervalDays=30
POST /api/secrets/{key}/lifecycle/auto-rotate/enable
POST /api/secrets/{key}/lifecycle/auto-rotate/disable
```

**Best Practices:**
- Set rotation intervals based on security requirements
- Enable auto-rotation for high-value secrets
- Monitor rotation logs for failures
- Integrate with secret providers for value generation

### 3. Rollback

Revert secrets to any previous version instantly.

**Process:**
1. Deactivate current version
2. Activate target version
3. Update secret value to match version
4. Log rollback event

**API:**
```
POST /api/secrets/{key}/versions/rollback/{version}
```

**Use Cases:**
- Recover from incorrect secret updates
- Rollback compromised secrets
- Test different secret values
- Emergency recovery

### 4. Soft Delete

Mark secrets as deleted without immediate removal.

**Benefits:**
- Recovery window for accidental deletions
- Audit trail preservation
- Compliance with retention policies
- Graceful cleanup

**APIs:**
```
POST /api/secrets/{key}/lifecycle/soft-delete
POST /api/secrets/{key}/lifecycle/restore
```

**Cleanup:**
- Soft-deleted secrets are retained for 90 days (configurable)
- Scheduled cleanup runs daily at 2 AM
- Hard delete removes all versions permanently

### 5. Expiration

Set expiration dates for secrets with automatic deactivation.

**Configuration:**
- `expiresAt`: Expiration timestamp
- Automatic deactivation on expiration
- Alerts for upcoming expirations

**APIs:**
```
POST /api/secrets/{key}/lifecycle/expiration?expiresAt=2024-12-31T23:59:59
DELETE /api/secrets/{key}/lifecycle/expiration
```

**Expiration Check:**
- Runs every hour (configurable)
- Deactivates expired secrets
- Logs expiration events
- Can trigger notifications

### 6. Activation Status

Control secret availability without deletion.

**States:**
- `isActive`: Secret is accessible
- `isDeleted`: Secret is soft-deleted
- Both must be true for access

**APIs:**
```
POST /api/secrets/{key}/lifecycle/activate
POST /api/secrets/{key}/lifecycle/deactivate
```

**Use Cases:**
- Temporarily disable secrets
- Maintenance windows
- Security incidents
- Testing environments

## API Reference

### Secret Lifecycle Endpoints

#### Soft Delete
```http
POST /api/secrets/{key}/lifecycle/soft-delete
```
Marks a secret as deleted. Secret remains recoverable for 90 days.

#### Restore
```http
POST /api/secrets/{key}/lifecycle/restore
```
Restores a soft-deleted secret to active state.

#### Activate
```http
POST /api/secrets/{key}/lifecycle/activate
```
Activates an inactive secret.

#### Deactivate
```http
POST /api/secrets/{key}/lifecycle/deactivate
```
Deactivates a secret without deleting it.

#### Set Rotation Interval
```http
POST /api/secrets/{key}/lifecycle/rotation-interval?intervalDays=30
```
Sets the rotation interval in days. Automatically calculates next rotation date.

#### Enable Auto Rotation
```http
POST /api/secrets/{key}/lifecycle/auto-rotate/enable
```
Enables automatic rotation. Rotation interval must be set first.

#### Disable Auto Rotation
```http
POST /api/secrets/{key}/lifecycle/auto-rotate/disable
```
Disables automatic rotation and clears next rotation date.

#### Set Expiration
```http
POST /api/secrets/{key}/lifecycle/expiration?expiresAt=2024-12-31T23:59:59
```
Sets an expiration date. Secret will be deactivated automatically.

#### Remove Expiration
```http
DELETE /api/secrets/{key}/lifecycle/expiration
```
Removes expiration date from secret.

### Secret Version Endpoints

#### Get Version History
```http
GET /api/secrets/{key}/versions
```
Returns all versions of a secret, ordered by version number (newest first).

#### Get Specific Version
```http
GET /api/secrets/{key}/versions/{version}
```
Returns details of a specific version.

#### Get Active Version
```http
GET /api/secrets/{key}/versions/active
```
Returns the currently active version.

#### Rollback to Version
```http
POST /api/secrets/{key}/versions/rollback/{version}
```
Rolls back the secret to the specified version.

## Configuration

### Application Properties

```properties
# Secret Rotation Scheduler
secret.rotation.cron=0 0 * * * ?              # Every hour
secret.expiration.check.cron=0 0 * * * ?       # Every hour
secret.cleanup.cron=0 0 2 * * ?                # Daily at 2 AM

# Retention Periods
secret.soft-delete.retention-days=90
secret.version.retention-days=365
```

## Best Practices

### Versioning
- Review version history regularly
- Set appropriate retention policies
- Monitor version storage growth
- Clean up old versions periodically

### Rotation
- Use 30-90 day rotation intervals for production secrets
- Enable auto-rotation for critical secrets
- Monitor rotation logs for failures
- Test rotation in non-production first

### Expiration
- Set expiration for temporary secrets
- Monitor upcoming expirations
- Automate renewal processes
- Alert before expiration

### Soft Delete
- Always use soft delete before hard delete
- Review soft-deleted secrets before cleanup
- Set appropriate retention periods
- Document deletion reasons

### Security
- Require elevated permissions for lifecycle operations
- Log all lifecycle changes
- Implement approval workflows for critical operations
- Regularly audit lifecycle events

## Monitoring and Alerts

### Key Metrics
- Secrets needing rotation
- Expired secrets
- Soft-deleted secrets
- Version count per secret
- Rotation failures

### Alert Triggers
- Rotation failures
- Secret expiration
- Unusual rollback activity
- Excessive version creation
- Cleanup failures

## Troubleshooting

### Rotation Not Working
- Check if auto-rotate is enabled
- Verify rotation interval is set
- Review scheduler logs
- Check secret permissions

### Secret Not Accessible
- Verify secret is active
- Check if soft-deleted
- Confirm not expired
- Review RBAC permissions

### Rollback Fails
- Verify version exists
- Check user permissions
- Review secret state
- Check for concurrent modifications

### Cleanup Not Running
- Verify scheduler is enabled
- Check cron configuration
- Review application logs
- Confirm retention settings

## Migration Notes

When upgrading to lifecycle-enabled version:

1. Run database migration to add lifecycle columns
2. Set default values for existing secrets
3. Enable scheduler in production
4. Configure rotation intervals
5. Review and set retention policies
6. Monitor initial rotation cycles

## Future Enhancements

- Webhook notifications for lifecycle events
- Custom rotation strategies
- Secret value generation integration
- Approval workflows for critical operations
- Lifecycle policy templates
- Advanced scheduling options
- Multi-environment rotation coordination
