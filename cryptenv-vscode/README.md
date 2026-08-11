# CryptEnv - Secrets Manager for Visual Studio Code

Version: 1.0.1

CryptEnv brings the CryptEnv secret management platform directly into the editor. Sign in to CryptEnv, browse the hierarchical Secrets Explorer (Workspaces, Environments, Secrets), create, preview, copy, edit, and delete secrets, and insert keys or values at the cursor with one click. Connect to the hosted service or point the extension at your own self-hosted CryptEnv backend.

Secrets and credentials are kept out of plaintext on disk. The extension uses the VS Code SecretStorage API to persist authentication tokens and API keys, backed by the operating system credential store.

---

## Features

- Two authentication methods
  - Email and password login with a short-lived session token
  - Long-lived CryptEnv API keys for persistent workstations
- Hierarchical Secrets Explorer in the Activity Bar
  - Workspaces, Environments (DEVELOPMENT, TEST, STAGING, PRODUCTION), and Secrets
  - Sorted, color-coded icons per environment type
  - Secure tooltips with masked values and full metadata
- Workspace and environment management
  - Create and delete workspaces
  - Create DEVELOPMENT, TEST, STAGING, and PRODUCTION environments inside any workspace
- Full secret lifecycle from the editor
  - Create, preview, copy, edit, and delete secrets
  - Plaintext preview inside an information modal with a Copy button
  - Insert a secret key or its value directly at the current editor cursor
- Profile and credential management
  - View the signed-in profile
  - Regenerate the API key from within VS Code and apply it immediately
- Custom backend URL for self-hosted CryptEnv installations
- Status bar entry for one-click access to the Secrets Explorer
- Modal confirmations for all destructive actions (delete workspace, delete secret, regenerate key)

---

## Requirements

- Visual Studio Code 1.85.0 or later
- A CryptEnv account. Create one directly from the extension via `CryptEnv: Create Account`, on the CryptEnv web dashboard, or with the companion `cryptenv-cli` npm package.
- Network access to the CryptEnv backend. The hosted service is used by default; self-hosted users can override the URL in-product.

---

## Quick Start

Open the CryptEnv view by clicking its icon in the VS Code Activity Bar, or open the Command Palette (Ctrl+Shift+P on Windows/Linux, Cmd+Shift+P on macOS) and run any `CryptEnv:` command. Then follow these five steps.

### 1. Authenticate

Choose one of two paths:

- Run `CryptEnv: Sign In` and enter your email and password. The session token is stored in VS Code SecretStorage.
- Run `CryptEnv: Set API Key` and paste a long-lived API key (prefix `ce_live_`). This is the recommended option for long-lived developer workstations.

If you do not already have an account, run `CryptEnv: Create Account` to register interactively from VS Code.

### 2. (Optional) Point the extension at a custom backend

If you host CryptEnv yourself, run `CryptEnv: Set Backend URL` and enter the base URL of your CryptEnv API (for example, `https://cryptenv.example.com/api`).

### 3. Create a workspace

If no workspaces appear in the explorer, click the new-folder icon in the view header or run `CryptEnv: Create Workspace`. Give the workspace a name (typically your project name) and an optional description.

### 4. Create an environment

Right-click any workspace and choose `CryptEnv: Create Environment`. Pick one of the standard tiers: DEVELOPMENT, TEST, STAGING, or PRODUCTION. Environments use distinct icons so production secrets are visually identifiable at a glance.

### 5. Add your first secret

Right-click an environment and choose `CryptEnv: Add Secret`. Enter a key name (for example, `DATABASE_URL`) and a value. The secret is created encrypted on the CryptEnv backend and appears in the explorer with a masked value.

After these steps, any secret in the tree is actionable: click it to preview, use the inline eye or copy icons, or right-click for Insert Key, Insert Value, Edit, or Delete.

---

## Commands Reference

All commands live under the `CryptEnv:` prefix in the Command Palette.

### Authentication

| Command | Description |
|---|---|
| CryptEnv: Sign In | Authenticate using email and password |
| CryptEnv: Create Account | Register a new CryptEnv account |
| CryptEnv: Sign Out | Remove all stored credentials |
| CryptEnv: View Profile | Show the signed-in profile and regenerate the API key |
| CryptEnv: Set API Key | Authenticate with a CryptEnv API key |
| CryptEnv: Set Backend URL | Configure a custom CryptEnv backend URL |

### Workspace and Environment

| Command | Description |
|---|---|
| CryptEnv: Refresh Explorer | Reload the Secrets Explorer tree |
| CryptEnv: Create Workspace | Create a new workspace |
| CryptEnv: Delete Workspace | Delete a workspace and all contents (with confirmation) |
| CryptEnv: Create Environment | Create DEVELOPMENT, TEST, STAGING, or PRODUCTION inside a workspace |

### Secrets

| Command | Description |
|---|---|
| CryptEnv: Add Secret | Create a new encrypted secret |
| CryptEnv: Edit Secret | Update the value or description of an existing secret |
| CryptEnv: Delete Secret | Permanently delete a secret (with confirmation) |
| CryptEnv: Preview Secret | Display the plaintext value in a modal with a Copy button |
| CryptEnv: Copy Secret Value | Copy the value to the system clipboard |
| CryptEnv: Insert Secret Key at Cursor | Paste the key name into the active text editor |
| CryptEnv: Insert Secret Value at Cursor | Paste the plaintext value into the active text editor |

---

## The Secrets Explorer

The Secrets Explorer lives in the CryptEnv Activity Bar container. Its hierarchy is:

```
Workspace (folder-library icon, sorted alphabetically)
  Environment (debug / test-view-icon / beaker / shield for DEVELOPMENT / TEST / STAGING / PRODUCTION)
    Secret (lock icon, alphabetical, value masked)
```

Inline actions available on secret rows:

- Eye (preview)
- Copy

Context menu actions available per item type:

- Workspace: Delete Workspace, Create Environment
- Environment: Add Secret
- Secret: Insert Key, Insert Value, Edit, Delete (plus the inline preview and copy actions)

---

## Configuration

The extension stores the backend URL in VS Code global state, not in plaintext configuration files. Credentials (session token or API key) are stored in the SecretStorage API, backed by the operating system credential store.

To switch backends at any time, run `CryptEnv: Set Backend URL`. Any HTTPS endpoint reachable from your machine is supported.

---

## Troubleshooting

| Symptom | Likely Cause | Action |
|---|---|---|
| `Not signed in. Use Sign in, Set API Key, or Create account first.` | No stored credentials | Run `CryptEnv: Sign In` or `CryptEnv: Set API Key` |
| `Could not load workspaces` or `Could not reach the CryptEnv backend` | Unreachable endpoint, incorrect URL, or service cold start | Run `CryptEnv: Set Backend URL` to verify the endpoint, then Refresh Explorer |
| `Authentication failed: invalid or expired credentials` | Session token expired or API key revoked | Sign in again, or regenerate and paste a new API key via `CryptEnv: View Profile > Regenerate API Key` |
| Insert Key / Insert Value are greyed out or do nothing | No active text editor is open | Open and focus a text editor, then right-click the secret again |
| Empty workspace or environment lists with no obvious error | Scope is correct but resources were never created | Right-click the parent item or run the corresponding Create command |

For bug reports and support, visit:
https://github.com/maheshshinde9100/CryptEnv/issues

---

## About

CryptEnv is a secret management platform for software engineering teams. It replaces scattered `.env` files with a single, auditable source of truth. The hosted service provides a web dashboard, a REST API, workspace and environment management, role-based access control, and client libraries for the command line and for Visual Studio Code.

Related tools in the same platform:
- `cryptenv-cli` - the official command-line client, available as an npm package
- CryptEnv Core - the Spring Boot backend reference implementation, suitable for self-hosting

License: MIT

---

## Developer

Mahesh Shinde

GitHub: https://github.com/maheshshinde9100
Email: maheshshinde9100@gmail.com