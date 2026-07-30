# CryptEnv

**A secrets management platform for teams and applications** — inspired by tools like Doppler, HashiCorp Vault, and AWS Secrets Manager, built from the ground up in Java.

> ⚠️ **Status: Actively under development.** This project is a work in progress and not yet production-ready. APIs, schemas, and features are subject to change without notice.

---

## Overview

CryptEnv is a centralized platform for managing application secrets, environment variables, and configuration across workspaces and environments (Development, Staging, Production). It's designed around clean architecture and SOLID principles, with a strong focus on security, auditability, and developer experience.

This is an independent, individually-developed project — built and maintained solely by [Mahesh Shinde](https://maheshshinde-dev.vercel.app).

---

## Ecosystem

CryptEnv is being built out as a full platform, not just an API:

| Component | Description | Link |
|---|---|---|
| 🔌 **API / Core Platform** | Java / Spring Boot backend powering auth, workspaces, and environments | — |
| 🖥️ **CLI** | Command-line client for managing secrets and environments from your terminal | [npm](https://www.npmjs.com/package/cryptenv-cli) |
| 🧩 **VS Code Extension** | Manage and sync secrets directly from your editor | [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=maheshshinde9100.cryptenv-vscode) |
| 📊 **Dashboard** | Web console for visual workspace and secret management | Coming soon |

---

## Core Capabilities

- 🔐 **JWT-based authentication** — secure user registration and login
- 🏢 **Workspace management** — organize secrets by team or organization
- 🌍 **Environment management** — isolate secrets across Development, Staging, and Production
- 👥 **Member invitations** — collaborate securely within a workspace
- 🖥️ **CLI access** — manage secrets and environments without leaving the terminal
- 🧩 **Editor integration** — VS Code extension for in-editor secret management
- 🧱 **Clean architecture** — clear separation of concerns across layers
- ✅ **Global exception handling & input validation**
- 📄 **API documentation** — via Swagger / OpenAPI
- 🗄️ **Versioned database migrations** — via Flyway
- 🐳 **Containerized deployment** — via Docker Compose

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Security | Spring Security, JWT |
| Database | PostgreSQL 16+ |
| Migrations | Flyway |
| API Docs | Swagger / OpenAPI |
| Build Tool | Maven |
| Containerization | Docker, Docker Compose |
| CLI | Node.js (npm package) |
| Editor Integration | VS Code Extension API |

---

## Architecture

CryptEnv follows a layered, clean-architecture approach on the backend, with thin clients (CLI, extension, dashboard) consuming a common API surface:

```
CLI / VS Code Extension / Dashboard
              ↓
      Controller  →  Service  →  Repository  →  Database
           ↓             ↓
         DTOs      Business Logic
```

- **Controllers** handle HTTP requests and delegate to services
- **Services** encapsulate business logic and orchestration
- **Repositories** manage data persistence via Spring Data JPA
- **DTOs** decouple internal models from API contracts
- **Global exception handlers** provide consistent, structured error responses
- **Clients** (CLI, extension, dashboard) are independent, versioned consumers of the core API

---

## API Reference (Overview)

Full interactive documentation is available via Swagger UI once the application is running.

### Authentication
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Authenticate and receive a JWT |
| GET | `/api/auth/me` | Get current authenticated user |

### Workspaces
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/workspaces` | Create a workspace |
| GET | `/api/workspaces` | List workspaces for the current user |
| GET | `/api/workspaces/{id}` | Get workspace details |
| POST | `/api/workspaces/{id}/members` | Invite a member |

### Environments
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/environments` | Create an environment |
| GET | `/api/environments/{id}` | Get environment details |
| GET | `/api/environments/workspace/{workspaceId}` | List environments for a workspace |
| PATCH | `/api/environments/{id}/toggle` | Toggle environment status |

---

## Roadmap

- [ ] Secret storage with encryption at rest
- [ ] Role-based access control (RBAC)
- [ ] Audit logging
- [ ] Secret versioning and rollback
- [x] CLI client
- [x] VS Code extension
- [ ] Web dashboard
- [ ] Rate limiting and abuse protection
- [ ] Webhooks / integrations

---

## Security Notes

This project deals with sensitive data by design. Ongoing priorities include:

- Encryption of secrets at rest and in transit
- Strict RBAC and least-privilege access
- Comprehensive audit trails
- Regular dependency and vulnerability review
- Hardened JWT configuration for production use

---

## Project Ownership

This is a **personal, individually-built project**. The source code is not publicly distributed as part of this documentation, and no setup instructions, credentials, or repository access details are included here.

For inquiries, collaboration, or demo requests, please reach out via the contact details on my [portfolio](https://maheshshinde-dev.vercel.app) or [LinkedIn](https://linkedin.com/in/maheshshinde9100).

---

## Author

**Mahesh Shinde**
Full-Stack Developer (Java / Spring Boot specialization)
[GitHub](https://github.com/maheshshinde9100) · [Portfolio](https://maheshshinde-dev.vercel.app) · [LinkedIn](https://linkedin.com/in/maheshshinde9100)

---

## License

All rights reserved © Mahesh Shinde. This project is proprietary and not licensed for redistribution, reuse, or modification without explicit permission.
