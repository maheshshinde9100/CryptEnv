# CryptEnv CLI

Cross-platform CLI for runtime secret injection in development environments.

## Installation

```bash
npm install -g cryptenv-cli
```

Or use directly with npx:

```bash
npx cryptenv-cli <command>
```

## Features

- **Authentication**: Secure login/logout with token storage
- **Secret Management**: List, get, set, and delete secrets
- **Runtime Injection**: Run commands with injected secrets (no .env files)
- **Profile Management**: View user profile information
- **Configuration**: Initialize project-specific settings

## Usage

### Initialize CryptEnv

Initialize CryptEnv in your project directory:

```bash
cryptenv init
```

This creates a `.cryptenv.json` configuration file with your API URL and workspace settings.

### Authentication

Login to authenticate with the CryptEnv server:

```bash
cryptenv login
```

Logout when done:

```bash
cryptenv logout
```

### Secret Management

List all secrets:

```bash
cryptenv secrets ls
```

Get a specific secret value:

```bash
cryptenv secrets get DATABASE_URL
```

Set a new secret:

```bash
cryptenv secrets set API_KEY "your-secret-key"
```

Delete a secret:

```bash
cryptenv secrets delete API_KEY
```

### Runtime Secret Injection

Run any command with secrets injected as environment variables:

```bash
cryptenv run npm start
```

```bash
cryptenv run python app.py
```

```bash
cryptenv run node server.js
```

**Important**: Secrets are injected directly into the child process environment and never written to disk or logged.

### Profile

View your user profile:

```bash
cryptenv profile
```

## Configuration

CryptEnv can be configured via:

1. **Environment Variable**: Set `CRYPTENV_API_URL` to override the default API URL
2. **Project Config**: `.cryptenv.json` file created by `cryptenv init`

Default API URL: `http://localhost:8080/api`

## Security

- Tokens are stored securely using the system keychain (keytar)
- Secrets are never written to disk
- Secrets are never logged to console
- Secrets are decrypted in memory only
- Environment variables are injected directly into child processes

## Examples

### Development Workflow

```bash
# Initialize project
cryptenv init

# Login
cryptenv login

# Set secrets
cryptenv secrets set DATABASE_URL "postgresql://user:pass@localhost/db"
cryptenv secrets set JWT_SECRET "your-jwt-secret"

# Run application with injected secrets
cryptenv run npm run dev

# List secrets
cryptenv secrets ls

# Logout when done
cryptenv logout
```

### Multi-Environment Setup

```bash
# Development
cryptenv secrets set NODE_ENV development
cryptenv run npm start

# Production
cryptenv secrets set NODE_ENV production
cryptenv run npm start
```

## API Integration

The CLI integrates with the CryptEnv backend API. Ensure your API server is running before using the CLI.

## Troubleshooting

**Connection Error**: Ensure the CryptEnv API server is running and accessible at the configured URL.

**Authentication Failed**: Check your credentials and verify the API URL is correct.

**Secret Not Found**: Ensure the secret exists and you have proper permissions.

## License

MIT
