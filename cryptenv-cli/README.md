# cryptenv-cli

Version: 1.1.2

CryptEnv CLI provides runtime secret injection for development teams. Store credentials securely in CryptEnv and inject them as environment variables at runtime. Secrets are encrypted at rest, fetched over HTTPS, and never written to your project directory.

---

## Requirements

- Node.js 16.0.0 or higher
- On Linux, `libsecret` is required for the credential store. On Debian/Ubuntu run:
  ```bash
  sudo apt-get install libsecret-1-dev
  ```

---

## Installation

Install globally to make the `cryptenv` command available:

```bash
npm install -g cryptenv-cli
```

Or use without installing:

```bash
npx cryptenv-cli <command>
```

Verify the installation:

```bash
cryptenv --version
```

---

## Quick Start

### 1. Create an account

Register once from the command line:

```bash
cryptenv register
```

You will be prompted for your email, username, password, first name, and last name.

You can also sign up through the web dashboard:
https://cryptenv-backend.onrender.com

### 2. Initialize your project

Run this inside your project directory to create a `.cryptenv.json` file:

```bash
cd my-project
cryptenv init
```

You will be asked for:
- CryptEnv API URL (default: `https://cryptenv-backend.onrender.com/api`)
- Workspace name (default: your project directory name)

The `.cryptenv.json` file contains only the API URL and workspace name. It does not contain any secrets. It is safe to commit this file to version control so all team members use the same workspace setting.

### 3. Log in

```bash
cryptenv login
```

Enter your email and password. The authentication token is stored in your operating system's secure credential store (macOS Keychain, Windows Credential Manager, or Linux Secret Service).

### 4. Store a secret

```bash
cryptenv secrets set DATABASE_URL "postgresql://user:pass@db.example:5432/mydb"
```

### 5. Run your app with secrets injected

```bash
cryptenv run -- npm start
```

All secrets stored in CryptEnv for your workspace are injected directly into the child process as environment variables.

---

## Available Commands

### Account and Session

#### `cryptenv register`

Create a new CryptEnv account from the command line.

```bash
cryptenv register
```

#### `cryptenv login`

Authenticate and store the session token in the OS credential store.

```bash
cryptenv login
```

#### `cryptenv logout`

Remove the stored session token from the OS credential store.

```bash
cryptenv logout
```

#### `cryptenv profile`

Display the current user's profile information.

```bash
cryptenv profile
```

---

### Project Setup

#### `cryptenv init`

Create a `.cryptenv.json` configuration file in the current directory.

```bash
cryptenv init
```

---

### Secret Management

#### `cryptenv secrets set <KEY> <VALUE>`

Create or update a secret.

```bash
cryptenv secrets set DATABASE_URL "postgresql://user:pass@db.example:5432/mydb"
cryptenv secrets set REDIS_URL "redis://localhost:6379"
cryptenv secrets set STRIPE_KEY "sk_test_<your_key_here>"
```

#### `cryptenv secrets ls`

List all secrets stored for the current workspace. Values are masked.

```bash
cryptenv secrets ls
```

#### `cryptenv secrets get <KEY>`

Display the plaintext value of a single secret.

```bash
cryptenv secrets get DATABASE_URL
```

#### `cryptenv secrets delete <KEY>`

Remove a secret.

```bash
cryptenv secrets delete OLD_API_KEY
```

---

### Runtime Injection

#### `cryptenv run -- <command> [args...]`

Run any command with all CryptEnv secrets injected as environment variables.

```bash
# Node.js application
cryptenv run -- node server.js

# Express dev server
cryptenv run -- npx nodemon app.ts

# Python application
cryptenv run -- python manage.py runserver

# Java / Maven
cryptenv run -- mvn spring-boot:run

# Rails
cryptenv run -- rails server

# Shell command
cryptenv run -- sh -c 'echo "DB is $DATABASE_URL"'
```

The double dash (`--`) separates `cryptenv` arguments from your command arguments.

Existing environment variables are preserved. CryptEnv only adds missing entries. The child process exits with the same exit code as your command.

---

## Authenticating in CI and Non-Interactive Environments

For GitHub Actions, Docker containers, or any non-interactive context, use an API key instead of running `cryptenv login`.

```bash
export CRYPTENV_API_KEY="ce_live_xxxxxxxxxxxxxxxxxxxx"
cryptenv run -- npm start
```

You can view and regenerate your API key in the web dashboard or with `cryptenv profile`.

---

## Configuration

### Environment Variables

| Variable | Purpose |
|---|---|
| `CRYPTENV_API_URL` | Override the backend base URL. Takes priority over `.cryptenv.json`. Default fallback: `https://cryptenv-backend.onrender.com/api` |
| `CRYPTENV_API_KEY` | Authenticate using an API key instead of interactive login. Use for CI/CD and Docker. |

### Project Configuration File (`.cryptenv.json`)

Created by `cryptenv init` in the current working directory:

```json
{
  "apiUrl": "https://cryptenv-backend.onrender.com/api",
  "workspace": "my-project",
  "createdAt": "2025-11-14T06:02:55.768Z"
}
```

- `apiUrl` - Backend URL for self-hosted CryptEnv instances
- `workspace` - Workspace name scope for fetching secrets

---

## Full Workflow Example

```bash
# One-time setup (per machine)
npm install -g cryptenv-cli
cryptenv register
cryptenv login

# Per-project setup
cd ~/code/my-team-api
cryptenv init

# Add secrets
cryptenv secrets set DATABASE_URL "postgresql://dev:dev@localhost:5432/myapi_dev"
cryptenv secrets set REDIS_URL "redis://localhost:6379"
cryptenv secrets set STRIPE_KEY "sk_test_<your_key_here>"

# Develop with secrets injected
cryptenv run -- npm run dev

# Another team member clones the project
git clone git@github.com:team/my-team-api.git
cd my-team-api
cryptenv login
cryptenv secrets ls
cryptenv run -- npm test
```

---

## Command Summary

```bash
cryptenv --version              # Print version
cryptenv --help                 # List all commands

cryptenv init                   # Create .cryptenv.json in current directory
cryptenv register               # Create a new CryptEnv account
cryptenv login                  # Authenticate and store token
cryptenv logout                 # Remove stored authentication token

cryptenv secrets ls             # List all secrets (values masked)
cryptenv secrets get <KEY>      # Print a single secret value
cryptenv secrets set <KEY> <VALUE>   # Create or update a secret
cryptenv secrets delete <KEY>   # Delete a secret

cryptenv run -- <command>       # Run a command with secrets injected

cryptenv profile                # Show current user profile
```

---

## Troubleshooting

| Issue | Cause | Resolution |
|---|---|---|
| Connection error. Please check your API URL. | Backend service is starting up, network is offline, or the URL is wrong. | Wait 30 seconds if using the free tier, check your internet connection, and verify the URL by setting `CRYPTENV_API_URL`. |
| Not authenticated. Please run: cryptenv login | No authentication token found. | Run `cryptenv login`, or export `CRYPTENV_API_KEY`. |
| No secrets found | No secrets have been created in the workspace yet. | Use `cryptenv secrets set` to add secrets. |
| Secret not found | Wrong workspace name or typo in the key name. | Verify the workspace name in `.cryptenv.json` matches the dashboard. Run `cryptenv secrets ls` to see existing keys. |
| Linux: keytar build error on install | Missing libsecret headers. | Install `libsecret-1-dev` using your package manager, then reinstall. |

Report issues at:
https://github.com/maheshshinde9100/CryptEnv/issues

---

## About

CryptEnv lets teams store environment credentials in a central, encrypted service and inject them at runtime without writing `.env` files to disk.

Hosted backend:
https://cryptenv-backend.onrender.com

Dashboard and workspace management:
https://cryptenv-backend.onrender.com

Source repository:
https://github.com/maheshshinde9100/CryptEnv/tree/main/cryptenv-cli

License: MIT

---

## Developer

Mahesh Shinde

GitHub: https://github.com/maheshshinde9100
Email: maheshshinde9100@gmail.com
