# CryptEnv CLI

**Runtime secret injection for developers and CI — no `.env` files, no plaintext on disk.**

[![npm version](https://img.shields.io/npm/v/cryptenv-cli.svg)](https://www.npmjs.com/package/cryptenv-cli)
[![license](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![node](https://img.shields.io/badge/node-%3E%3D18-339933.svg)](https://nodejs.org)

CryptEnv keeps secrets encrypted with AES-256-GCM in the CryptEnv backend and injects them straight into the process you run. Your project never stores a `.env` file, and `cryptenv run` never writes secrets to disk.

- Default backend: `https://cryptenv-backend.onrender.com/api`
- Works with the CryptEnv dashboard, REST API, VS Code extension and CLI
- Handles email verification, password reset, TOTP MFA and automatic token refresh

---

## Install

```bash
npm install -g cryptenv-cli
cryptenv --version
```

Or run without installing:

```bash
npx cryptenv-cli --help
```

Linux keyring support (optional but recommended):

```bash
sudo apt-get install libsecret-1-dev
```

If the OS keyring is unavailable, tokens are kept in a machine-bound AES-256-GCM file at `~/.cryptenv/credentials.json` (mode `0600`).

---

## Quick start

```bash
cryptenv register                 # create an account, then enter the emailed 6-digit code
cryptenv login                    # sign in (handles MFA)
cd my-app
cryptenv init                     # writes .cryptenv.json (no secrets)
cryptenv use                      # pick a workspace + environment for this directory
cryptenv secrets set DATABASE_URL "postgresql://user:pass@host/db"
cryptenv run -- npm start         # secrets exist only in the child process
```

Already have an account? Skip `register` and start with `cryptenv login`.

---

## Account & authentication

| Command | What it does |
|---------|--------------|
| `cryptenv register` | Creates an account, validates the email (including MX lookup), then verifies the emailed 6-digit code |
| `cryptenv verify-email [email]` | Verifies a signup email later; `--resend` sends a fresh code |
| `cryptenv login [--email <email>]` | Password login with TOTP/recovery-code MFA challenge support |
| `cryptenv forgot-password [email]` | Requests a reset code, verifies it, and sets a new password |
| `cryptenv logout [--everywhere]` | Revokes the session server-side and clears local credentials |
| `cryptenv whoami [--json]` | Shows the authenticated user |
| `cryptenv profile [--json]` | Alias for `whoami` |
| `cryptenv status [--json]` | Shows the current user and workspaces |
| `cryptenv doctor` | Checks backend health, mail transport, credentials and context |

Access tokens are refreshed automatically. If a request gets a `401`, the CLI rotates the refresh token and retries once, so long sessions keep working.

### Non-interactive / CI authentication

Create an API key in the dashboard under **Settings**, then:

```bash
export CRYPTENV_API_KEY="ce_live_xxxxxxxx"
export CRYPTENV_API_URL="https://cryptenv-backend.onrender.com/api"   # optional
cryptenv run -- npm test
```

Commands return a non-zero exit code on failure, so they are safe in pipelines.

---

## Project context

| Command | What it does |
|---------|--------------|
| `cryptenv init [--api-url <url>]` | Creates `.cryptenv.json` for this directory |
| `cryptenv use [-w <workspaceId>] [-e <environmentId>] [-y]` | Selects the active workspace/environment and saves it to `.cryptenv.json` |
| `cryptenv envs [-w <workspaceId>] [--json]` | Lists environments in a workspace |
| `cryptenv project ls [-w <workspaceId>] [--json]` | Lists projects |
| `cryptenv project create <name> [-d <text>]` | Creates a project |

`.cryptenv.json` holds only IDs and the API URL — no secrets. Commit it if you want the whole team to share the same context.

```json
{
  "apiUrl": "https://cryptenv-backend.onrender.com/api",
  "workspaceId": 3,
  "environmentId": 12,
  "createdAt": "2026-11-22T00:00:00.000Z"
}
```

---

## Workspaces

| Command | What it does |
|---------|--------------|
| `cryptenv workspaces ls [--json]` | Lists workspaces, your role, and whether an encryption key is set |
| `cryptenv workspaces create <name> [-k <key>] [-d <text>]` | Creates a workspace; `-k` sets the workspace encryption key (16+ chars) |
| `cryptenv ws …` | Alias for `workspaces` |

Every secret is encrypted with the workspace key. If a workspace shows `no key`, set one in the dashboard before storing secrets.

---

## Secrets

Secret commands accept `-w/--workspace` and `-e/--env`; otherwise they use `.cryptenv.json`, then `CRYPTENV_WORKSPACE` / `CRYPTENV_ENV`.

| Command | What it does |
|---------|--------------|
| `cryptenv secrets ls [--json]` | Lists keys with type, version and state (values stay hidden) |
| `cryptenv secrets get <KEY>` | Prints the decrypted value |
| `cryptenv secrets set <KEY> <VALUE> [-d <text>] [-t <type>] [--tags <tags>]` | Creates or updates a secret |
| `cryptenv secrets rotate <KEY>` | Generates and applies a new random value |
| `cryptenv secrets delete <KEY>` | Deletes a secret |
| `cryptenv secrets import <file> -e <id> [-f ENV\|JSON\|YAML]` | Imports secrets from a file |
| `cryptenv secrets export -e <id> [-f …] [-o <file>]` | Exports secrets (`-o` writes a `0600` file) |
| `cryptenv secrets versions <KEY> [--json]` | Lists version history |
| `cryptenv secrets rollback <KEY> <VERSION>` | Rolls back to a previous version |

### Lifecycle

| Command | What it does |
|---------|--------------|
| `cryptenv secrets activate <KEY>` / `deactivate <KEY>` | Enable or disable a secret |
| `cryptenv secrets soft-delete <KEY>` / `restore <KEY>` | Recoverable deletion |
| `cryptenv secrets expire <KEY> --at <ISO>` | Sets an expiry timestamp |
| `cryptenv secrets no-expire <KEY>` | Removes the expiry |
| `cryptenv secrets rotation <KEY> --days <n>` | Sets the rotation interval |
| `cryptenv secrets auto-rotate <KEY> [--off]` | Enables or disables automatic rotation |

---

## JIT access

Protected environments require a time-boxed grant for non-admins.

| Command | What it does |
|---------|--------------|
| `cryptenv jit request <justification> [-e <id>] [--hours <1-24>]` | Requests elevated access |
| `cryptenv jit ls [-w <id>] [--json]` | Lists requests and their status |
| `cryptenv jit approve <id>` / `deny <id>` | Decides a request |

---

## Runtime injection

```bash
cryptenv run -- npm start
cryptenv run -e 12 -- python manage.py runserver
cryptenv run --dry-run -- npm start   # show variable names only
```

`cryptenv run` fetches the selected environment's secrets, merges them over `process.env`, and spawns the command. Values are never printed and never written to disk. If no environment is selected, it merges all environments and warns you.

---

## Leak scanning

```bash
cryptenv scan .                 # scan a directory or file
cryptenv scan --staged          # scan staged Git changes
cryptenv install-hook           # add a pre-commit secret scan hook
```

Detects AWS keys, GitHub/Stripe tokens, private keys, high-entropy strings and generic credential assignments. Put `cryptenv:ignore` or `gitleaks:allow` on a line to suppress a finding.

---

## Shell completion

```bash
eval "$(cryptenv completion --shell bash)"
eval "$(cryptenv completion --shell zsh)"
cryptenv completion --shell fish | source
```

---

## Configuration

| Source | Purpose |
|--------|---------|
| `CRYPTENV_API_URL` | Backend base URL — highest priority |
| `CRYPTENV_API_KEY` | API-key auth for CI/CD |
| `CRYPTENV_TOKEN` | Pre-issued access token (overrides the credential store) |
| `CRYPTENV_WORKSPACE` / `CRYPTENV_ENV` | Default workspace / environment IDs |
| `.cryptenv.json` | Per-directory `apiUrl`, `workspaceId`, `environmentId` |

API URL precedence: `CRYPTENV_API_URL` → `.cryptenv.json` → built-in default.

### Pointing the CLI at your own backend

One constant controls the published default:

```js
// src/utils/config.js
const DEFAULT_API_URL = 'https://cryptenv-backend.onrender.com/api';
```

Change that line, bump the version, then publish:

```bash
npm test
npm pack --dry-run
npm publish
```

Every user can still override it without republishing:

```bash
export CRYPTENV_API_URL="https://secrets.example.com/api"
# or, per project:
cryptenv init --api-url "https://secrets.example.com/api"
```

---

## Security notes

- Secrets are encrypted server-side (AES-256-GCM) with the workspace encryption key.
- Access tokens use short TTLs and rotating refresh tokens; revoked sessions stop working immediately.
- Credentials live in the OS keyring, or a machine-bound encrypted file when no keyring exists.
- `secrets get` and `secrets export` print plaintext to stdout — redirect carefully.
- Prefer API keys for CI and password login (with MFA) for local machines.
- The CLI never teleports secrets into `.env` files or shell history; `run` injects into the child process only.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Connection error` | Check `CRYPTENV_API_URL`; free-tier backends can take ~30s to wake |
| `Email not verified` | Run `cryptenv verify-email you@company.com` (or `--resend`) |
| No verification/reset email | The backend has no mail transport; configure SMTP, `BREVO_API_KEY` or `RESEND_API_KEY` |
| `Not authenticated` | `cryptenv login`, or set `CRYPTENV_API_KEY` |
| Decrypt errors / empty values | The workspace has no encryption key, or the workspace key changed — recreate the secrets |
| Wrong workspace/environment | Run `cryptenv use` to reset the local context |
| `Cannot find module 'keytar'` | Expected on headless machines; the encrypted file store is used instead |
| Session mysteriously fails | Run `cryptenv doctor` to check health, auth and refresh-token state |

---

## License

Apache-2.0 © Mahesh Shinde

- Repository: https://github.com/maheshshinde9100/CryptEnv
- Issues: https://github.com/maheshshinde9100/CryptEnv/issues