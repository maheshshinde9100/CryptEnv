# CryptEnv - Setup and Run Guide

Complete guide to set up and run the CryptEnv application including the backend API, CLI tool, and React dashboard.

## Prerequisites

- Java 17 or higher
- Node.js 16 or higher
- Maven
- PostgreSQL (or use the provided Docker setup)
- Git

## Project Structure

```
CryptEnv/
├── cryptenv-core/          # Spring Boot Backend API
├── cryptenv-cli/           # Node.js CLI Tool
├── cryptenv-dashboard/     # React Dashboard
└── docker-compose.yml      # Database and infrastructure
```

## Quick Start with Docker

### 1. Start Infrastructure

```bash
# Start PostgreSQL database
docker-compose up -d

# Check status
docker-compose ps
```

### 2. Start Backend API

```bash
cd cryptenv-core
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`

### 3. Start CLI Tool

```bash
cd cryptenv-cli
npm install
npm link  # For global installation
# Or use directly: node bin/cryptenv.js
```

### 4. Start Dashboard

```bash
cd cryptenv-dashboard
npm install
npm run dev
```

The dashboard will be available at `http://localhost:3000`

## Detailed Setup

### Backend API (cryptenv-core)

#### Setup

```bash
cd cryptenv-core

# Install dependencies (Maven handles this automatically)
./mvnw clean install

# Configure database
# Edit src/main/resources/application.properties if needed
```

#### Run

```bash
# Development mode
./mvnw spring-boot:run

# Production build
./mvnw clean package
java -jar target/cryptenv-core-1.0.0.jar
```

#### API Endpoints

- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login
- `GET /api/secrets` - List secrets
- `POST /api/secrets` - Create secret
- `GET /api/secrets/{key}` - Get secret
- `PUT /api/secrets/{key}` - Update secret
- `DELETE /api/secrets/{key}` - Delete secret
- `GET /api/workspaces` - List workspaces
- `POST /api/workspaces` - Create workspace
- `GET /api/audit-logs` - View audit logs

### CLI Tool (cryptenv-cli)

#### Setup

```bash
cd cryptenv-cli

# Install dependencies
npm install

# Install globally (optional)
npm link
```

#### Run

```bash
# Initialize configuration
cryptenv init

# Login
cryptenv login

# Manage secrets
cryptenv secrets ls
cryptenv secrets get DATABASE_URL
cryptenv secrets set API_KEY "your-key"
cryptenv secrets delete OLD_KEY

# Run commands with injected secrets
cryptenv run npm start
cryptenv run python app.py

# View profile
cryptenv profile

# Logout
cryptenv logout
```

#### Configuration

Create `.cryptenv.json` in your project directory:

```json
{
  "apiUrl": "http://localhost:8080/api",
  "workspace": "my-project",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

Or set environment variable:

```bash
export CRYPTENV_API_URL="http://localhost:8080/api"
```

### React Dashboard (cryptenv-dashboard)

#### Setup

```bash
cd cryptenv-dashboard

# Install dependencies
npm install
```

#### Run

```bash
# Development mode
npm run dev

# Production build
npm run build
npm run preview
```

#### Features

- **Dashboard**: Overview of secrets and workspaces
- **Secrets Management**: Create, view, edit, delete secrets
- **Workspace**: Manage workspaces and environments
- **Members**: Team member management
- **Settings**: Profile, security, and API keys
- **Dark Mode**: Toggle between light/dark themes

## Development Workflow

### 1. Start Database

```bash
docker-compose up -d postgres
```

### 2. Start Backend

```bash
cd cryptenv-core
./mvnw spring-boot:run
```

### 3. Start Dashboard (New Terminal)

```bash
cd cryptenv-dashboard
npm run dev
```

### 4. Use CLI (New Terminal)

```bash
cd cryptenv-cli
node bin/cryptenv.js init
node bin/cryptenv.js login
```

## Testing

### Backend Tests

```bash
cd cryptenv-core
./mvnw test
```

### CLI Tests

```bash
cd cryptenv-cli
npm test
```

### Dashboard Tests

```bash
cd cryptenv-dashboard
npm test
```

## Environment Variables

### Backend (cryptenv-core)

```properties
# application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/cryptenv
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
jwt.secret=your-secret-key-here
jwt.expiration=86400000
```

### CLI

```bash
export CRYPTENV_API_URL="http://localhost:8080/api"
```

### Dashboard

```bash
# Vite proxy handles this automatically
# API calls go to /api which proxies to http://localhost:8080/api
```

## Troubleshooting

### Database Connection Issues

```bash
# Check if PostgreSQL is running
docker-compose ps

# Restart database
docker-compose restart postgres

# View logs
docker-compose logs postgres
```

### Backend Port Already in Use

```bash
# Find process using port 8080
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill process or change port in application.properties
server.port=8081
```

### Dashboard Proxy Issues

Ensure backend is running on port 8080. The dashboard proxies `/api` requests to `http://localhost:8080/api`.

### CLI Authentication Issues

```bash
# Clear stored credentials
# macOS: Keychain Access
# Linux: ~/.local/share/keytar/
# Windows: Windows Credential Manager

# Or re-login
cryptenv logout
cryptenv login
```

## Production Deployment

### Backend

```bash
cd cryptenv-core
./mvnw clean package -Pprod
java -jar target/cryptenv-core-1.0.0.jar
```

### CLI

```bash
cd cryptenv-cli
npm pack
npm install -g cryptenv-cli-1.0.0.tgz
```

### Dashboard

```bash
cd cryptenv-dashboard
npm run build
# Serve dist/ with nginx or similar
```

## Security Notes

- Change default database passwords in production
- Use strong JWT secrets
- Enable HTTPS in production
- Configure CORS properly
- Use environment variables for sensitive data
- Enable audit logging for compliance

## Support

For issues or questions, please refer to the project documentation or create an issue in the repository.
