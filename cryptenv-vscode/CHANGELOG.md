# Changelog

## 1.5.0

- Sign in handles TOTP/recovery-code MFA challenges
- Access tokens refresh automatically; 401 responses retry once before asking you to sign in
- Create Account validates the email, sends a verification code, and verifies it in-editor
- New commands: **Verify Email** and **Reset Password**
- Secret values are revealed through the audited reveal endpoint, with step-up MFA support
- Sign Out now revokes the session on the server
- New `cryptenv.backendUrl` setting for self-hosted deployments
- Profile shows the API key prefix returned by the current API
- Lighter extension icon, cleaned VSIX contents, Apache-2.0 licensing
- Updated README and tests

## 1.4.0

- Workspace encryption keys, environment management, and secret CRUD in the explorer
- Preview, copy, and insert secret keys/values at the cursor
- API-key authentication and VS Code SecretStorage persistence