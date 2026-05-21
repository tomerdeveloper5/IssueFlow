# IssueFlow Run Guide

## Prerequisites

- Java 21 (or Java 25)
- Docker Desktop (for PostgreSQL)
- Git Bash or PowerShell

## 1) Install dependencies

No global Maven installation is required. The project uses the Maven wrapper.

## 2) Start PostgreSQL

From the repository root:

```bash
docker compose -f compose.yml up -d
```

Default DB credentials are already configured in `src/main/resources/application.yaml`:

- database: `issueflow`
- username: `issueflow`
- password: `issueflow`
- port: `5432`

## 3) Build the project

```bash
./mvnw clean package
```

On Windows PowerShell:

```powershell
.\mvnw.cmd clean package
```

## 4) Run the application

Using Maven:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

### JWT secret configuration

The app supports a local development fallback JWT secret so cloned repositories run out of the box.

- `JWT_SECRET` (optional for local/test): if missing, the app uses a local fallback value.
- `JWT_EXPIRATION_SECONDS` (optional): defaults to `3600`.

To override explicitly on Windows PowerShell before running:

```powershell
$env:JWT_SECRET="replace-with-a-long-random-secret"
$env:JWT_EXPIRATION_SECONDS="3600"
.\mvnw.cmd spring-boot:run
```

For production/CI, always set `JWT_SECRET` via environment or secret manager and do not rely on the fallback.

Using packaged JAR:

```bash
java -jar target/issueflow-0.0.1-SNAPSHOT.jar
```

The service runs on `http://localhost:8080`.

## 5) Run tests

```bash
./mvnw test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd test
```

## Note

- All API endpoints are JWT-protected except `POST /auth/login` and `POST /users` (registration).

