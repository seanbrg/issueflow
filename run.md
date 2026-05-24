# IssueFlow – Setup, Build, and Run

## Prerequisites

- **Java 21** (or Java 25)
- **Maven** (via the included wrapper — no separate install needed)
- **Docker** and **Docker Compose** (for the PostgreSQL database)

---

## 1. Install Dependencies

No manual dependency installation is required. The Maven wrapper (`./mvnw`) downloads all dependencies declared in `pom.xml` on first build.

---

## 2. Start the Database

The app requires a running PostgreSQL instance. Start it with Docker Compose:

```bash
docker compose up -d
```

This starts a PostgreSQL container with:
- **Host:** `localhost:5432`
- **Database:** `issueflow`
- **Username:** `issueflow`
- **Password:** `issueflow`

To stop the database:

```bash
docker compose down
```

---

## 3. Build the Project

```bash
./mvnw clean package -DskipTests
```

This compiles the source and produces a runnable JAR in `target/`.

---

## 4. Run the Application

```bash
./mvnw spring-boot:run
```

Or run the packaged JAR directly:

```bash
java -jar target/issueflow-*.jar
```

The API is available at **http://localhost:8080**.

> **Note:** The database must be running before starting the app (step 2).

---

## 5. Run the Tests

Tests use an in-memory **H2** database — no Docker or running app is required.

```bash
./mvnw test
```

Run a single test class:

```bash
./mvnw test -Dtest=TicketServiceTest
```

Run a single test method:

```bash
./mvnw test -Dtest=TicketServiceTest#shouldCreateTicket
```

---

## Quick Reference

```bash
# Full cycle (clean build + run)
docker compose up -d && ./mvnw spring-boot:run

# Tests only (no Docker needed)
./mvnw test
```
