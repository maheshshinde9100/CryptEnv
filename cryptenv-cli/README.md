﻿# cryptenv-cli

Version: 1.1.3

CryptEnv CLI is the command-line interface for the CryptEnv secret management platform. It replaces unencrypted `.env` files with a secure, centralized workflow: store credentials in CryptEnv, fetch them at startup, and inject them directly into any running process as environment variables.

Secrets are encrypted at rest, transmitted over HTTPS, and never written to disk inside your project. Authentication tokens are held in your operating system's native credential store.

---

## Requirements

- Node.js 16.0.0 or later
- On Linux, the credential store requires `libsecret`. On Debian/Ubuntu and APT-based distributions, install it before installing the CLI:
  ```bash
  sudo apt-get install libsecret-1-dev
  ```

---

## Installation

Install globally to make the `cryptenv` command available in every terminal session.

```bash
npm install -g cryptenv-cli
```

Or run it ad-hoc for a single invocation without global installation:

```bash
npx cryptenv-cli <command>
```

Verify the installation:

```bash
cryptenv --version
```

---

## Quick Start

Follow these five steps on each development machine.

### 1. Register an account

Run the interactive registration prompt once per CryptEnv account:

```bash
cryptenv register
```

You will be asked for your email, username, password, first name, and last name.

You can also sign up through the CryptEnv web dashboard, where additional workspace and team management features are available.

### 2. Initialize a project

From any project directory, create a `.cryptenv.json` configuration file:

```bash
cd my-project
cryptenv init
```

The wizard prompts for two values:
- CryptEnv API URL — the base URL of your CryptEnv backend instance. For the hosted service, accept the suggested default. For a self-hosted or private deployment, enter your own endpoint (for example, `https://cryptenv.example.com/api`).
- Workspace name — defaults to the project directory name. Each project should use a distinct workspace so that secrets are properly scoped.

The generated `.cryptenv.json` file contains only the API URL and workspace name. It does not hold secrets or credentials. It is safe and intended to commit to version control so that every teammate on the project uses the same workspace scope.

### 3. Sign in

```bash
cryptenv login
```

Enter your email and password. The resulting session token is saved in the operating system's secure credential store — macOS Keychain on Apple systems, Windows Credential Manager on Windows, and Secret Service via libsecret on Linux.

### 4. Store a secret

```bash
cryptenv secrets set DATABASE_URL "postgresql://dev:dev@localhost:5432/myapi_dev"
```

The first argument is the environment variable name. The second argument is the value. Wrap values that contain whitespace or shell metacharacters in quotes.

### 5. Run your process with secrets injected

```bash
cryptenv run -- npm start
```

Every secret stored in CryptEnv for the selected workspace is fetched, decrypted, and injected into the child process as an environment variable. Your application code reads them with the standard `process.env`, `os.environ`, `System.getenv()`, or equivalent — no SDK or refactoring required.

---

## Available Commands

### Account and Session

#### `cryptenv register`

Create a new CryptEnv account interactively from the terminal.

```bash
cryptenv register
```

#### `cryptenv login`

Authenticate with email and password and persist the session token.

```bash
cryptenv login
```

#### `cryptenv logout`

Remove the stored session token from the operating system credential store.

```bash
cryptenv logout
```

#### `cryptenv profile`

Print the signed-in user's profile information, including a preview of their API key.

```bash
cryptenv profile
```

---

### Project Setup

#### `cryptenv init`

Create a `.cryptenv.json` configuration file in the current working directory, using values from an interactive prompt.

```bash
cryptenv init
```

---

### Secret Management

#### `cryptenv secrets set <KEY> <VALUE>`

Create a new secret or overwrite the value of an existing one.

```bash
cryptenv secrets set DATABASE_URL "postgresql://dev:dev@localhost:5432/myapi_dev"
cryptenv secrets set REDIS_URL "redis://localhost:6379"
cryptenv secrets set STRIPE_KEY "sk_test_<replace_with_your_key>"
```

#### `cryptenv secrets ls`

List every secret key in the current workspace. Values are masked so that secrets can be inspected safely in shared screens and recorded sessions.

```bash
cryptenv secrets ls
```

#### `cryptenv secrets get <KEY>`

Print the plaintext value of a single secret.

```bash
cryptenv secrets get DATABASE_URL
```

#### `cryptenv secrets delete <KEY>`

Permanently remove a secret from the workspace.

```bash
cryptenv secrets delete OLD_API_KEY
```

---

### Runtime Injection

#### `cryptenv run -- <command> [args...]`

Launch any process with the full set of workspace secrets injected as environment variables.

```bash
# Node.js application
cryptenv run -- node server.js

# Express dev server with TypeScript
cryptenv run -- npx nodemon app.ts

# Python Django or Flask application
cryptenv run -- python manage.py runserver

# Spring Boot via Maven
cryptenv run -- mvn spring-boot:run

# Ruby on Rails
cryptenv run -- rails server

# Subshell or script
cryptenv run -- sh -c 'echo "Database host is $DATABASE_URL"'
```

The double-dash (`--`) separator is required. It tells `cryptenv` where its own arguments end and your command begins so that flags are forwarded correctly to your process.

Already-set environment variables are preserved as-is; CryptEnv adds only the entries that it manages. The child process exits with the same status code as your command, which makes `cryptenv run` safe for use in CI pipelines and shell scripts.

---

## Non-Interactive Authentication

For CI pipelines, Docker containers, GitHub Actions, cron jobs, and every other non-interactive context, use a CryptEnv API key instead of `cryptenv login`.

Export the key before invoking `cryptenv`:

```bash
export CRYPTENV_API_KEY="ce_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
cryptenv run -- npm start
```

On Windows PowerShell:

```powershell
$env:CRYPTENV_API_KEY = "ce_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
cryptenv run -- npm start
```

View and regenerate your API key at any time from the web dashboard, or from the terminal with `cryptenv profile`. Treat API keys with the same care as any other long-lived credential.

---

## Configuration

### Environment Variables

| Variable | Purpose |
|---|---|
| `CRYPTENV_API_URL` | Overrides the backend base URL. Takes precedence over the `.cryptenv.json` file. Use this to switch between hosted and self-hosted instances, or to point ephemeral environments at a regional endpoint. |
| `CRYPTENV_API_KEY` | Authenticates using an API key instead of an interactive login. Use this for CI/CD, containers, and automated jobs. |

### Project Configuration File (`.cryptenv.json`)

Generated in the current directory by `cryptenv init`:

```json
{
  "apiUrl": "<your CryptEnv API endpoint>",
  "workspace": "my-project",
  "createdAt": "2025-11-14T06:02:55.768Z"
}
```

- `apiUrl` — the base URL of the CryptEnv backend. Leave the default for the hosted service, or set it to your private endpoint for self-hosted deployments.
- `workspace` — the name scope that secrets are fetched from. Each project typically maps to one workspace.

Resolution order for the API URL is, from highest to lowest priority:
1. `CRYPTENV_API_URL` environment variable
2. `apiUrl` in the nearest `.cryptenv.json`
3. Default hosted service endpoint

---

## Example Workflow

```bash
# Per-machine, one-time setup
npm install -g cryptenv-cli
cryptenv register
cryptenv login

# Per-project setup
cd ~/code/my-team-api
cryptenv init

# Store credentials for the project
cryptenv secrets set DATABASE_URL "postgresql://dev:dev@localhost:5432/myapi_dev"
cryptenv secrets set REDIS_URL "redis://localhost:6379"
cryptenv secrets set STRIPE_KEY "sk_test_<replace_with_your_key>"

# Start the local dev server with secrets injected
cryptenv run -- npm run dev

# A teammate clones the project and gets the same scope
git clone git@github.com:team/my-team-api.git
cd my-team-api
cryptenv login
cryptenv secrets ls
cryptenv run -- npm test
```

---

## Command Summary

```bash
cryptenv --version                # Print the CLI version
cryptenv --help                   # Show the full command reference

cryptenv init                     # Create .cryptenv.json in the current directory
cryptenv register                 # Create a new CryptEnv account
cryptenv login                    # Authenticate and persist the session token
cryptenv logout                   # Remove the stored session token

cryptenv secrets ls               # List all secrets in the workspace (values masked)
cryptenv secrets get <KEY>        # Print a single secret value
cryptenv secrets set <KEY> <VALUE> # Create or update a secret
cryptenv secrets delete <KEY>     # Permanently delete a secret

cryptenv run -- <command>         # Launch a command with secrets injected

cryptenv profile                  # Show the current user profile and API key preview
```

---

## Troubleshooting

| Symptom | Likely Cause | Action |
|---|---|---|
| `Connection error. Please check your API URL.` | Backend endpoint unreachable, invalid URL, or cold start on a hosted tier. | Verify your network connection, confirm the API URL with `CRYPTENV_API_URL` or `cryptenv init`, and retry. |
| `Not authenticated. Please run: cryptenv login` | No session token or API key is available. | Run `cryptenv login`, or export `CRYPTENV_API_KEY` for non-interactive use. |
| `No secrets found` | The workspace exists but contains no secrets yet. | Create secrets with `cryptenv secrets set`, or verify that you are targeting the intended workspace in `.cryptenv.json`. |
| `Secret not found` | Typo in the key name, or the secret belongs to a different workspace. | Run `cryptenv secrets ls` to list keys, and confirm the `workspace` field in `.cryptenv.json` matches the dashboard. |
| Linux: `keytar` build failure during `npm install` | Missing `libsecret-1-dev` development headers. | Install `libsecret-1-dev` via your package manager and re-run `npm install -g cryptenv-cli`. |

Report issues and browse existing discussions in the project repository at:
https://github.com/maheshshinde9100/CryptEnv/issues

---

## About

CryptEnv is a secret management platform built for engineering teams that want to replace scattered `.env` files with a single, auditable source of truth. The hosted service provides a dashboard for workspace, environment, and team management, while the CLI and VS Code extension deliver credentials directly to the processes and editors that need them.

Source repository:
https://github.com/maheshshinde9100/CryptEnv/tree/main/cryptenv-cli

Related tools in the same project:
- CryptEnv for Visual Studio Code — manage secrets directly from the editor
- CryptEnv Core — the open-source backend, suitable for self-hosting

License: MIT

---

## Developer

Mahesh Shinde

GitHub: https://github.com/maheshshinde9100

Email: maheshshinde9100@gmail.com
