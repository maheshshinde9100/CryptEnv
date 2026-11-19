# cryptenv-cli

**Version 1.4.0**

CryptEnv CLI injects encrypted secrets into local and CI processes at runtime. Secrets stay in CryptEnv (AES-GCM at rest) and are never written as project `.env` files.

Default API: `https://cryptenv-backend.onrender.com/api`

---

## Install

```bash
npm install -g cryptenv-cli
cryptenv --version
# cryptenv-cli 1.4.0
```

Or: `npx cryptenv-cli <command>`

Linux credential store: `sudo apt-get install libsecret-1-dev`

If the OS keyring is unavailable, tokens are stored in a machine-bound AES-GCM encrypted file (`~/.cryptenv/credentials.json`).

---

## Quick start

```bash
cryptenv register          # or sign up in the dashboard
cryptenv login             # JWT stored in OS keychain
cd my-app && cryptenv init # writes .cryptenv.json (no secrets)
cryptenv use               # pick workspace + environment for this directory
cryptenv secrets set DATABASE_URL "postgresql://..."
cryptenv run -- npm start  # secrets injected into the child process only
```

---

## Commands

### Account
| Command | Description |
|---------|-------------|
| `cryptenv register` | Create account |
| `cryptenv login` | Authenticate (handles TOTP MFA challenge) |
| `cryptenv logout [--everywhere]` | Revoke token(s) and clear local credentials |
| `cryptenv profile` | Show profile / API key hints |
| `cryptenv whoami [--json]` | Show the authenticated user |
| `cryptenv status [--json]` | Show auth state and workspaces |
| `cryptenv doctor` | Check API health, credential store and local config |

### Project context
| Command | Description |
|---------|-------------|
| `cryptenv init` | Create `.cryptenv.json` (API URL + workspace name) |
| `cryptenv use [-w <id>] [-e <id>]` | Set active workspace/environment for this directory |
| `cryptenv envs [-w <id>] [--json]` | List environments for a workspace |
| `cryptenv project ls [-w <id>]` | List projects in a workspace |
| `cryptenv project create <name> [-d <text>]` | Create a project |

### Workspaces
| Command | Description |
|---------|-------------|
| `cryptenv workspaces ls` | List workspaces (`key✓` / `no-key`) |
| `cryptenv workspaces create <name> -k <key>` | Create workspace with encryption key (`-d` description) |
| `cryptenv ws …` | Alias for `workspaces` |

### Secrets
All secret commands accept `-w/--workspace` and `-e/--env` (falling back to `.cryptenv.json` / `CRYPTENV_WORKSPACE` / `CRYPTENV_ENV`).

| Command | Description |
|---------|-------------|
| `cryptenv secrets ls` | List keys (values masked) |
| `cryptenv secrets get <KEY>` | Print decrypted value |
| `cryptenv secrets set <KEY> <VALUE>` | Create or update a secret |
| `cryptenv secrets delete <KEY>` | Delete secret |
| `cryptenv secrets import <file> -e <id> [-f ENV\|JSON\|YAML]` | Import secrets from a file |
| `cryptenv secrets export -e <id> [-f …] [-o <file>]` | Export secrets |
| `cryptenv secrets versions <KEY> [--json]` | List version history |
| `cryptenv secrets rollback <KEY> <VERSION>` | Roll back to a version |

### Secret lifecycle
| Command | Description |
|---------|-------------|
| `cryptenv secrets activate <KEY>` / `deactivate <KEY>` | Toggle active state |
| `cryptenv secrets soft-delete <KEY>` / `restore <KEY>` | Soft delete / restore |
| `cryptenv secrets expire <KEY> --at <ISO>` | Set expiry |
| `cryptenv secrets no-expire <KEY>` | Remove expiry |
| `cryptenv secrets rotation <KEY> --days <n>` | Set rotation interval |
| `cryptenv secrets auto-rotate <KEY> [--off]` | Enable/disable automatic rotation |

### JIT access
| Command | Description |
|---------|-------------|
| `cryptenv jit request <justification> [-e <id>] [--hours <n>]` | Request elevated access |
| `cryptenv jit approve <id>` / `deny <id>` | Approve or deny a request |
| `cryptenv jit ls [-w <id>]` | List requests for a workspace |

### Runtime
| Command | Description |
|---------|-------------|
| `cryptenv run -- <cmd> [args…]` | Run command with all secrets as env vars (`-e` to scope to an environment) |
| `cryptenv completion [-s bash\|zsh\|fish]` | Print a shell completion script |

---

## CI / non-interactive auth

```bash
export CRYPTENV_API_KEY="ce_live_xxxxxxxx"
export CRYPTENV_API_URL="https://cryptenv-backend.onrender.com/api"   # optional
cryptenv run -- npm test
```

Generate/regenerate API keys in the CryptEnv dashboard → **Settings**. Commands return a non-zero exit code on failure for CI use.

---

## Configuration

| Source | Purpose |
|--------|---------|
| `CRYPTENV_API_URL` | Backend base URL (highest priority) |
| `CRYPTENV_API_KEY` | API key auth (CI/CD) |
| `CRYPTENV_TOKEN` | Pre-issued access token (overrides keychain) |
| `CRYPTENV_WORKSPACE` / `CRYPTENV_ENV` | Default workspace / environment ids |
| `.cryptenv.json` | `apiUrl`, `workspaceId`, `environmentId` (safe to commit) |

```json
{
  "apiUrl": "<your CryptEnv API endpoint>",
  "workspaceId": 3,
  "environmentId": 12,
  "createdAt": "2026-08-12T00:00:00.000Z"
}
```

---

## Security notes

- Secrets are encrypted server-side with your **workspace encryption key**.
- `cryptenv run` injects into the **child process only** — nothing written to disk.
- `cryptenv secrets export` prints plaintext to stdout — handle output carefully.
- Prefer API keys for pipelines; prefer login for local machines.

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Connection error | Check `CRYPTENV_API_URL`; free-tier hosts may cold-start (~30s) |
| Not authenticated | `cryptenv login` or set `CRYPTENV_API_KEY` |
| No secrets / decrypt errors | Ensure workspace has an encryption key; recreate secrets if master key was rotated |
| Wrong workspace/environment | Run `cryptenv use` to reset the local context |

Issues: https://github.com/maheshshinde9100/CryptEnv/issues

---

## License & author

MIT · Mahesh Shinde · https://github.com/maheshshinde9100
