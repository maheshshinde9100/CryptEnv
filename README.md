# CryptEnv

A production-grade secrets management platform similar to Doppler, HashiCorp Vault, and AWS Secrets Manager.

## Tech Stack

- **Java 21** - Modern Java with latest features
- **Spring Boot 3.2.0** - Enterprise application framework
- **Spring Security** - Authentication and authorization
- **PostgreSQL** - Relational database
- **Flyway** - Database migration tool
- **JWT** - Token-based authentication
- **Docker Compose** - Container orchestration
- **Swagger/OpenAPI** - API documentation

## Features

- User registration and authentication with JWT
- Workspace management
- Environment management (Development, Staging, Production)
- Member invitation to workspaces
- Clean architecture with SOLID principles
- Global exception handling
- Input validation
- API documentation with Swagger
- Database migrations with Flyway
- Docker support for easy deployment

## Project Structure

```
CryptEnv/
├── cryptenv-core/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/maheshshinde/CryptEnv/
│   │   │   │   ├── config/          # Configuration classes
│   │   │   │   ├── controller/      # REST controllers
│   │   │   │   ├── dto/             # Data Transfer Objects
│   │   │   │   ├── exception/       # Custom exceptions
│   │   │   │   ├── model/           # JPA entities
│   │   │   │   ├── repository/      # Data access layer
│   │   │   │   ├── security/        # Security configuration
│   │   │   │   └── service/         # Business logic
│   │   │   └── resources/
│   │   │       ├── db/migration/    # Flyway migrations
│   │   │       └── application.properties
│   │   └── test/                   # Unit tests
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml
└── README.md
```

## Prerequisites

- Java 21 or higher
- Maven 3.9+
- PostgreSQL 16+ (or use Docker)
- Docker and Docker Compose (optional)

## Setup Instructions

### Option 1: Using Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone <repository-url>
cd CryptEnv
```

2. Start the application with Docker Compose:
```bash
docker-compose up -d
```

3. The application will be available at:
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - PostgreSQL: localhost:5432

### Option 2: Local Development Setup

1. Clone the repository:
```bash
git clone <repository-url>
cd CryptEnv/cryptenv-core
```

2. Install PostgreSQL and create a database:
```sql
CREATE DATABASE cryptenv;
CREATE USER cryptenv WITH PASSWORD 'cryptenv_password';
GRANT ALL PRIVILEGES ON DATABASE cryptenv TO cryptenv;
```

3. Update `src/main/resources/application.properties` with your database credentials if needed.

4. Build the project:
```bash
./mvnw clean install
```

5. Run the application:
```bash
./mvnw spring-boot:run
```

6. Access the application:
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html

## API Documentation

Once the application is running, access the Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

### Authentication Endpoints

- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Authenticate and get JWT token
- `GET /api/auth/me` - Get current authenticated user

### Workspace Endpoints

- `POST /api/workspaces` - Create a new workspace (requires authentication)
- `GET /api/workspaces` - Get all workspaces for current user
- `GET /api/workspaces/{id}` - Get workspace by ID
- `POST /api/workspaces/{id}/members?email={email}` - Invite member to workspace

### Environment Endpoints

- `POST /api/environments` - Create a new environment (requires authentication)
- `GET /api/environments/{id}` - Get environment by ID
- `GET /api/environments/workspace/{workspaceId}` - Get all environments for a workspace
- `PATCH /api/environments/{id}/toggle` - Toggle environment active status

## Example Usage

### Register a User

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "username": "testuser",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }'
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### Create a Workspace (with JWT token)

```bash
curl -X POST http://localhost:8080/api/workspaces \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "name": "my-workspace",
    "description": "My first workspace"
  }'
```

### Create an Environment

```bash
curl -X POST http://localhost:8080/api/environments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "name": "DEVELOPMENT",
    "workspaceId": 1
  }'
```

## Configuration

### Application Properties

Key configuration options in `src/main/resources/application.properties`:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/cryptenv
spring.datasource.username=cryptenv
spring.datasource.password=cryptenv_password

# JWT
jwt.secret=your-secret-key-change-this-in-production-at-least-256-bits
jwt.expiration=86400000
```

**Important:** Change the JWT secret in production environments!

## Running Tests

Run the test suite:

```bash
./mvnw test
```

## Building for Production

Build the JAR file:

```bash
./mvnw clean package -DskipTests
```

The JAR file will be created at: `cryptenv-core/target/CryptEnv-0.0.1-SNAPSHOT.jar`

Run the JAR:

```bash
java -jar cryptenv-core/target/CryptEnv-0.0.1-SNAPSHOT.jar
```

## Security Considerations

- Change the default JWT secret in production
- Use environment variables for sensitive configuration
- Enable HTTPS in production
- Implement rate limiting
- Add role-based access control (RBAC) for enhanced security
- Regularly update dependencies

## Database Migrations

Flyway migrations are located in `src/main/resources/db/migration/`. They are automatically applied on application startup.

Current migrations:
- V1__Create_Users_Table.sql
- V2__Create_Workspaces_Table.sql
- V3__Create_Workspace_Members_Table.sql
- V4__Create_Environments_Table.sql

## Development

### Adding New Features

1. Create entity in `model/` package
2. Create DTOs in `dto/` package
3. Create repository in `repository/` package
4. Create service in `service/` package
5. Create controller in `controller/` package
6. Add Flyway migration if needed
7. Write unit tests

### Code Style

- Follow SOLID principles
- Use clean architecture
- Add proper validation
- Write meaningful commit messages
- Keep methods focused and small

## License

MIT License

## Support

For issues and questions, please open an issue on the repository.
