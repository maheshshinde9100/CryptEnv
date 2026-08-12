# CryptEnv — Secrets Manager for Visual Studio Code

**Version 1.2.0**

Manage CryptEnv workspaces, environments, and encrypted secrets without leaving the editor. Talks to the CryptEnv Spring Boot API (JWT or API key).

Default backend: `https://cryptenv-backend.onrender.com/api`

---

## Features (1.2.0)

- **Sign in** (email/password → JWT) or **API key** (`ce_live_…`)
- **Secrets Explorer** — Workspaces → Environments (`DEVELOPMENT` / `STAGING` / `PRODUCTION`) → Secrets
- **Create workspace** with optional **workspace encryption key** (required before storing secrets)
- Create / preview / copy / edit / delete secrets
- Insert secret **key** or **value** at the cursor
- Regenerate API key from **View Profile**
- Custom backend URL for self-hosted CryptEnv
- Credentials stored via VS Code **SecretStorage** (OS keychain)

---

## Install

1. VS Code Marketplace: search **CryptEnv**, or  
2. Manual: `Extensions: Install from VSIX…` → `cryptenv-1.2.0.vsix`

Requires VS Code **1.85+** and a CryptEnv account.

---

## Quick start

1. Activity Bar → **CryptEnv** icon (or Command Palette → `CryptEnv: Sign In`)
2. Optional: `CryptEnv: Set Backend URL` for self-hosted APIs
3. `CryptEnv: Create Workspace` — name + optional encryption key (min 16 chars)
4. Right-click workspace → **Create Environment**
5. Right-click environment → **Add Secret**

Use the eye / copy actions on a secret, or insert key/value at the cursor.

---

## Commands

### Auth
| Command | Description |
|---------|-------------|
| CryptEnv: Sign In | Email + password |
| CryptEnv: Create Account | Register |
| CryptEnv: Sign Out | Clear credentials |
| CryptEnv: View Profile | Profile + regenerate API key |
| CryptEnv: Set API Key | Persist `ce_live_…` key |
| CryptEnv: Set Backend URL | e.g. `https://host/api` |

### Workspace / environment
| Command | Description |
|---------|-------------|
| CryptEnv: Refresh Explorer | Reload tree |
| CryptEnv: Create Workspace | Name, description, encryption key |
| CryptEnv: Delete Workspace | Confirm delete |
| CryptEnv: Create Environment | DEVELOPMENT / STAGING / PRODUCTION |

### Secrets
| Command | Description |
|---------|-------------|
| CryptEnv: Add Secret | Encrypted create |
| CryptEnv: Edit Secret | Update value/description |
| CryptEnv: Delete Secret | Confirm delete |
| CryptEnv: Preview Secret | Show plaintext + copy |
| CryptEnv: Copy Secret Value | Clipboard |
| CryptEnv: Insert Secret Key at Cursor | Insert key name |
| CryptEnv: Insert Secret Value at Cursor | Insert plaintext |

---

## Security

- JWT / API keys live in **SecretStorage**, not workspace files.
- Secret values are encrypted at rest on the CryptEnv server with the workspace key.
- Destructive actions require confirmation.

---

## Companion tools

- CLI: `npm install -g cryptenv-cli` (`cryptenv run -- …`)
- Dashboard: web UI for vault, audit, API keys
- Node SDK: `@cryptenv/sdk` · Java SDK: `com.maheshshinde:cryptenv-sdk`

Repo: https://github.com/maheshshinde9100/CryptEnv

---

## License & author

MIT · Mahesh Shinde · https://github.com/maheshshinde9100
