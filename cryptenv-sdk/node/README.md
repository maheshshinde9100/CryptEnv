# @cryptenv/sdk

Runtime secret consumption SDK for Node.js applications.

Install CryptEnv secrets into your app at runtime — authenticate with CryptEnv, fetch encrypted secrets, decrypt locally with your master key, and use plaintext values in code or `process.env`. No HTTP, JWT, or crypto code in your application.

For secret **management** (login, set, delete, workspace admin), use [`cryptenv-cli`](../../cryptenv-cli) instead.

## Install

```bash
npm install @cryptenv/sdk
```

## Configuration

Add CryptEnv credentials to your application `.env`. Store only CryptEnv config here — not your actual application secrets.

```env
CRYPTENV_API_URL=https://cryptenv-backend.onrender.com
CRYPTENV_EMAIL=you@example.com
CRYPTENV_PASSWORD=your-password
CRYPTENV_WORKSPACE_ID=1
CRYPTENV_MASTER_KEY=your-workspace-encryption-key
```

| Variable | Required | Description |
|----------|----------|-------------|
| `CRYPTENV_EMAIL` | Yes* | CryptEnv account email |
| `CRYPTENV_PASSWORD` | Yes* | CryptEnv account password |
| `CRYPTENV_MASTER_KEY` | Yes | Workspace encryption key (local decryption only; never sent to the server) |
| `CRYPTENV_WORKSPACE_ID` | No | Active workspace (defaults to first workspace with a key) |
| `CRYPTENV_API_URL` | No | Backend URL (`https://host` or `https://host/api`) |
| `CRYPTENV_API_KEY` | Alt* | API key auth instead of email/password (CI/CD) |
| `CRYPTENV_TOKEN` | Alt* | Pre-issued JWT instead of login |

\* Use email/password, `CRYPTENV_API_KEY`, or `CRYPTENV_TOKEN`.

`CRYPTENV_WORKSPACE_ENCRYPTION_KEY` is accepted as an alias for `CRYPTENV_MASTER_KEY`.

Optional: `.cryptenv.json` in the project root can supply `apiUrl` and `workspace` name (same as the CLI).

## Usage

### Retrieve secrets in code

```js
const cryptenv = require('@cryptenv/sdk');

await cryptenv.init();

const databaseUrl = cryptenv.get('DATABASE_URL');
const jwtSecret = cryptenv.get('JWT_SECRET');

const db = new Database(cryptenv.get('DATABASE_URL'));
```

### Load into process.env

```js
await cryptenv.init();
await cryptenv.load();

// Now use standard env vars — no plaintext secrets in .env
console.log(process.env.DATABASE_URL);
```

`load()` only sets in-memory `process.env` values. It does **not** write files.

### Full runtime API

```js
await cryptenv.init();

cryptenv.get('DATABASE_URL');   // sync — decrypt one secret (cached)
await cryptenv.getAll();        // decrypt all secrets in workspace
cryptenv.listKeys();            // secret key names
await cryptenv.refresh();       // reload ciphertext from server
await cryptenv.load();          // init + decrypt all + inject process.env
```

## How it works

```
Node.js Application
        │
        │ @cryptenv/sdk
        ▼
Authenticate (email/password, API key, or JWT)
        │
        ▼
Authorized workspace
        │
        ▼
Encrypted secrets (GET /api/sdk/…)
        │
        ▼
Local AES-256-GCM decryption
        │ CRYPTENV_MASTER_KEY (stays local)
        ▼
Plaintext → cryptenv.get("KEY")
```

The master key decrypts secrets on your machine. Ciphertext travels over HTTPS; plaintext exists only in application memory.

## Security

- Application secrets do not belong in `.env` — only CryptEnv credentials and the master key.
- The master key is never sent to the CryptEnv backend.
- Errors do not include tokens, API keys, or secret values.
- Use `cryptenv-cli` for operator workflows; use this SDK at application runtime.

## Development

```bash
npm test              # offline crypto + config + SDK unit tests
npm run example       # live example (requires running backend + .env)
```

## License

MIT
