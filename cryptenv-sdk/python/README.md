# CryptEnv Python SDK

**Version 1.0.0**

Fetch and decrypt CryptEnv workspace secrets client-side. Secrets are stored encrypted (AES-256-GCM) on the server and decrypted in your process with the workspace encryption key — the plaintext never touches the CryptEnv API.

Mirrors the Node.js `@cryptenv/sdk` API.

---

## Install

```bash
pip install cryptenv-sdk
# or, from this repo:
pip install ./cryptenv-sdk/python
```

Requires Python 3.9+.

---

## Quick start

```python
from cryptenv import CryptEnv

client = CryptEnv()
summary = client.init()          # reads credentials from the environment
print(summary["activeWorkspaceId"])

db_url = client.get("DATABASE_URL")
all_secrets = client.get_all()
```

Inject every secret into the process environment (nothing written to disk):

```python
client.load()                    # os.environ["DATABASE_URL"] = "..."

# or with a prefix / without overwriting existing vars
client.load(prefix="APP_", overwrite=False)
```

---

## Configuration

Resolution order for each value: explicit options → environment variable → `.cryptenv.json`.

| Option | Environment variable | Purpose |
|--------|----------------------|---------|
| `email` / `password` | `CRYPTENV_EMAIL` / `CRYPTENV_PASSWORD` | Password auth |
| `api_key` | `CRYPTENV_API_KEY` | API-key auth (CI/CD) |
| `token` | `CRYPTENV_TOKEN` | Pre-issued JWT |
| `workspace_id` | `CRYPTENV_WORKSPACE_ID` | Select a workspace |
| `master_key` | `CRYPTENV_MASTER_KEY` (or `CRYPTENV_WORKSPACE_ENCRYPTION_KEY`) | Workspace encryption key |
| `api_url` | `CRYPTENV_API_URL` | Override the API host |

```python
client = CryptEnv({
    "email": "you@example.com",
    "password": "...",
    "workspace_id": 3,
    "master_key": "your-workspace-encryption-key",
})
client.init()
```

Authentication precedence: password → token → API key. If several workspaces are available and none is selected, the first workspace with an encryption key is used.

---

## API

| Method | Description |
|--------|-------------|
| `init(options=None)` | Authenticate, select a workspace and load secrets. Returns a summary dict. |
| `refresh()` | Reload all encrypted secrets from the server. Returns the count. |
| `get(key, refresh=False, throw_on_missing=True)` | Decrypt a single secret (cached). |
| `get_or_fetch(key)` | Decrypt a secret, fetching it if not already loaded. |
| `get_all()` | Decrypt every secret into a `dict`. |
| `load(prefix="", overwrite=True)` | Inject all secrets into `os.environ`. Returns the count. |
| `list_keys()` | List secret names in the active workspace. |
| `list_workspaces()` | List workspaces available to the user. |
| `set_active_workspace(workspace_id)` | Switch workspace (clears caches). |
| `get_active_workspace()` | Current workspace or `None`. |
| `is_initialized()` | Whether `init()` has run. |
| `set_secret(key, value, environment_id=None, description="")` | Create/update a secret. |
| `delete_secret(key, environment_id=None)` | Delete a secret. |

### Errors

All failures raise `RuntimeError` with a sanitized message (tokens and API keys are redacted).

---

## Crypto format

* **v1 (default):** `base64(IV[12] ‖ ciphertext ‖ tag[16])`, key = workspace passphrase padded/truncated to 32 bytes. Compatible with the server, CLI and Java/Node SDKs.
* **v2 (opt-in on the server):** `"v2:" + base64(salt[16] ‖ IV[12] ‖ ciphertext ‖ tag[16])`, key = PBKDF2-HMAC-SHA256 (120 000 iterations).

The SDK transparently decrypts both formats and always encrypts with v1.

---

## Tests

```bash
cd cryptenv-sdk/python
pip install -e ".[test]"
pytest
```

---

## License

MIT · Mahesh Shinde · https://github.com/maheshshinde9100
