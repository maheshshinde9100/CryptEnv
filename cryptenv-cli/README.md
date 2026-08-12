﻿# cryptenv-cli

**Version 1.3.0**

CryptEnv CLI injects encrypted secrets into local and CI processes at runtime. Secrets stay in CryptEnv (AES-GCM at rest) and are never written as project `.env` files.

Default API: `https://cryptenv-backend.onrender.com/api`

---

## Install

```bash
npm install -g cryptenv-cli
cryptenv --version
# cryptenv-cli 1.3.0
```

Or: `npx cryptenv-cli <command>`

Linux credential store: `sudo apt-get install libsecret-1-dev`

---

## Quick start

```bash
cryptenv register          # or sign up in the dashboard
cryptenv login             # JWT stored in OS keychain
cd my-app && cryptenv init # writes .cryptenv.json (no secrets)
cryptenv secrets set DATABASE_URL "postgresql://..."
cryptenv run -- npm start  # secrets injected into the child process only
```

---

## Commands

### Account
| Command | Description |
|---------|-------------|
| `cryptenv register` | Create account |
| `cryptenv login` | Authenticate (JWT → OS credential store) |
| `cryptenv logout` | Clear stored token |
| `cryptenv profile` | Show profile / API key hints |

### Project
| Command | Description |
|---------|-------------|
| `cryptenv init` | Create `.cryptenv.json` (API URL + workspace name) |

### Workspaces (1.2+)
| Command | Description |
|---------|-------------|
| `cryptenv workspaces ls` | List workspaces (`key✓` / `no-key`) |
| `cryptenv workspaces create <name> -k <key>` | Create workspace with encryption key (`-d` description) |
| `cryptenv ws …` | Alias for `workspaces` |

### Secrets
| Command | Description |
|---------|-------------|
| `cryptenv secrets ls` | List keys (values masked) |
| `cryptenv secrets get <KEY>` | Print decrypted value |
| `cryptenv secrets set <KEY> <VALUE>` | Create secret (server encrypts) |
| `cryptenv secrets delete <KEY>` | Delete secret |

### Runtime
| Command | Description |
|---------|-------------|
| `cryptenv run -- <cmd> [args…]` | Run command with all secrets as env vars |

---

## CI / non-interactive auth

```bash
export CRYPTENV_API_KEY="ce_live_xxxxxxxx"
export CRYPTENV_API_URL="https://cryptenv-backend.onrender.com/api"   # optional
cryptenv run -- npm test
```

Generate/regenerate API keys in the CryptEnv dashboard → **Settings**.

---

## Configuration

| Source | Purpose |
|--------|---------|
| `CRYPTENV_API_URL` | Backend base URL (highest priority) |
| `CRYPTENV_API_KEY` | API key auth (CI/CD) |
| `.cryptenv.json` | `apiUrl` + `workspace` name (safe to commit) |

```json
{
  "apiUrl": "<your CryptEnv API endpoint>",
  "workspace": "my-project",
  "createdAt": "2026-08-12T00:00:00.000Z"
}
```

---

## Security notes

- Secrets are encrypted server-side with your **workspace encryption key**.
- `cryptenv run` injects into the **child process only** — nothing written to disk.
- Prefer API keys for pipelines; prefer login for local machines.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Connection error | Check `CRYPTENV_API_URL`; free-tier hosts may cold-start (~30s) |
| Not authenticated | `cryptenv login` or set `CRYPTENV_API_KEY` |
| No secrets / decrypt errors | Ensure workspace has an encryption key; recreate secrets if master key was rotated |

Issues: https://github.com/maheshshinde9100/CryptEnv/issues

---

## License & author

MIT · Mahesh Shinde · https://github.com/maheshshinde9100
