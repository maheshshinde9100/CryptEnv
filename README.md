# CryptEnv

CryptEnv is an enterprise-grade platform for secure runtime secret injection and environment management. It securely delivers secrets to applications without the need for static `.env` files or hardcoded credentials.

## Overview

CryptEnv consists of four core components:

1. **CryptEnv Core (Backend)**
   - Built with Java, Spring Boot 3.2, and PostgreSQL.
   - Provides secure REST APIs protected by JWT and API Keys.
   - Handles AES-256 GCM encryption of all secrets at rest.
   - Maintains an immutable audit trail of all access and administrative actions.

2. **CryptEnv Dashboard (Frontend)**
   - A modern React application built with Vite and Tailwind CSS.
   - Offers workspace and environment isolation.
   - Provides administrative controls for secret rotation, access reviews, and security health analytics.

3. **CryptEnv CLI**
   - A Node.js command-line tool for developers and CI/CD pipelines.
   - Injects secrets directly into running processes (e.g., `cryptenv run -- node app.js`).
   - Supports listing, fetching, creating, and deleting secrets directly from the terminal.

4. **CryptEnv SDK (Java)**
   - A client library for Java applications (Spring Boot, Quarkus, etc.).
   - Allows native, programmatic retrieval of secrets at runtime using API Keys.

## Architecture & Security

- **Database Migrations:** Managed reliably using Flyway.
- **Authentication:** Dual-mode authentication supporting short-lived JWTs for the web dashboard and long-lived API Keys for CLI/SDK access.
- **Encryption:** All stored secrets are encrypted. Only authenticated requests can trigger decryption before delivery.

## Local Setup

### Prerequisites
- Java 17
- Node.js (v18+)
- Maven
- PostgreSQL

### 1. Database Configuration
Ensure PostgreSQL is running on the default port (5432). The backend application will automatically create the schema on startup. Ensure your credentials are appropriately set in the backend configuration.

### 2. Running the Backend
Navigate to the `cryptenv-core` directory and start the Spring Boot application:
```bash
cd cryptenv-core
mvn clean install
mvn spring-boot:run
```
The API server will listen on `http://localhost:8080`.

### 3. Running the Dashboard
Navigate to the `cryptenv-dashboard` directory and start the development server:
```bash
cd cryptenv-dashboard
npm install
npm run dev
```
Access the dashboard via `http://localhost:3000`.

### 4. Installing the CLI
To test the CLI locally:
```bash
cd cryptenv-cli
npm install
npm link
```
You can now use the `cryptenv` command globally on your system.

### 5. Building the Java SDK
To make the Java SDK available to your local Maven projects:
```bash
cd cryptenv-sdk/java
mvn clean install
```

## Usage Flow

1. Register a new user account through the Dashboard.
2. Create a Workspace and an Environment (e.g., Development).
3. Navigate to Settings and generate an API Key.
4. From your terminal, run `cryptenv login` to authenticate.
5. Create secrets via the Dashboard or the CLI (`cryptenv secrets set <KEY> <VALUE>`).
6. Run applications securely without `.env` files using `cryptenv run -- <your command>`.

## License

This project is licensed under the MIT License.
