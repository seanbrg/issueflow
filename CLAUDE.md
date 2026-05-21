# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Commands

```bash
# Start the PostgreSQL database (required before running the app)
docker compose up -d

# Build
./mvnw clean package

# Run the app
./mvnw spring-boot:run

# Run all tests (uses H2 in-memory DB, no Docker required)
./mvnw test

# Run a single test class
./mvnw test -Dtest=TicketServiceTest

# Run a single test method
./mvnw test -Dtest=TicketServiceTest#shouldCreateTicket
```

---

## Architecture

This is a **Spring Boot 3 / Java 21** REST API backed by **PostgreSQL** (via Spring Data JPA / Hibernate). The app runs on port **8080**. `ddl-auto: update` means Hibernate manages schema migrations automatically. `schema.sql` and `data.sql` in `src/main/resources/` run on every startup (`sql.init.mode: always`).

The base package is `com.att.tdp.issueflow`. Layer structure:

- `entity/` — JPA `@Entity` classes (one per domain object)
- `repository/` — `JpaRepository` interfaces
- `service/` — business logic (`@Service`)
- `controller/` — `@RestController` classes mapped to API routes
- `dto/` — request/response POJOs (use Lombok `@Data` / `@Builder`)
- `security/` — JWT filter, token provider, auth entry point
- `scheduler/` — `@Scheduled` tasks (auto-escalation)
- `exception/` — global exception handler (`@ControllerAdvice`)

**Available libraries:**
- **Lombok** — use for entities and DTOs to avoid boilerplate
- **Apache Commons CSV** (`commons-csv 1.10.0`) — ticket export/import
- **Bean Validation** (`spring-boot-starter-validation`) — `@Valid` on request bodies
- **Spring Security** — JWT-based authentication
- **H2** — in-memory DB used only during tests (no Docker needed for `./mvnw test`)

---

## Domain Model

### User
Fields: `id`, `username`, `email`, `fullName`, `role` (enum: `ADMIN`, `DEVELOPER`), `password`, `createdAt`

Constraints:
- `role` must be `ADMIN` or `DEVELOPER`
- `username` and `email` must be unique
- Used as identity for ticket assignments, comments, and audit log entries

### Project
Fields: `id`, `name`, `description`, `ownerId` (FK → User), `deletedAt`, `createdAt`

Constraints:
- Supports **soft delete** — `deletedAt` is set instead of physical deletion
- Soft-deleted projects are excluded from standard list/get responses
- Only `ADMIN` users can view or restore soft-deleted projects

### Ticket
Fields: `id`, `title`, `description`, `status` (enum), `priority` (enum), `type` (enum), `projectId` (FK), `assigneeId` (FK → User, nullable), `dueDate` (ISO-8601, optional), `isOverdue` (computed/stored), `deletedAt`, `createdAt`, `version` (optimistic locking)

Enums:
- `status`: `TODO` → `IN_PROGRESS` → `IN_REVIEW` → `DONE` (forward-only)
- `priority`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `type`: `BUG`, `FEATURE`, `TECHNICAL`

Constraints:
- Belongs to exactly one project
- Status transitions are forward-only; backward transitions must be rejected
- A ticket with status `DONE` cannot be updated
- Two users cannot update the same ticket simultaneously — use optimistic locking (`@Version`)
- Supports **soft delete** (same pattern as Project)
- `isOverdue` flag is visible in all GET responses

### Comment
Fields: `id`, `ticketId` (FK), `authorId` (FK → User), `content`, `createdAt`, `updatedAt`, `version` (optimistic locking)

Constraints:
- Two users cannot edit the same comment simultaneously — use optimistic locking
- `@username` mentions in content are parsed, matched case-insensitively against real usernames, and stored as a many-to-many `mentionedUsers` relationship
- On comment update, mention list is re-evaluated: newly added mentions are created, removed mentions are deleted
- Each comment response includes `mentionedUsers: [{ id, username, fullName }]`

### AuditLog
Fields: `id`, `action`, `entityType`, `entityId`, `performedBy` (userId or `"SYSTEM"`), `actor` (enum: `USER`, `SYSTEM`), `timestamp`

Constraints:
- Append-only — never update or delete audit records
- Written on every state-changing API call (create, update, delete, restore, status change, auto-assign, etc.)
- Auto-assignment writes actor = `SYSTEM`, action = `AUTO_ASSIGN`

### TicketDependency
Fields: `ticketId` (FK), `blockedById` (FK → Ticket)

Constraints:
- Both tickets must exist and belong to the **same project**
- A ticket cannot transition to `DONE` if it has any unresolved (non-DONE) blockers
- Circular dependencies should be prevented or handled gracefully

### Attachment
Fields: `id`, `ticketId` (FK), `filename`, `contentType`, `data` (bytes or file path), `uploadedAt`

Constraints:
- Maximum file size: **10 MB** — reject anything larger
- Allowed MIME types: `image/png`, `image/jpeg`, `application/pdf`, `text/plain` — reject all others
- Uploaded via `multipart/form-data`

---

## API Surface

All endpoints return `200 OK` on success unless otherwise noted. Soft-deleted records are excluded from normal list/get responses. All endpoints (except `POST /auth/login`) require a valid JWT in the `Authorization: Bearer <token>` header.

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | Accepts `{ username, password }`, returns signed JWT |
| POST | `/auth/logout` | Invalidates current token (server-side deny-list or stateless expiry) |
| GET | `/auth/me` | Returns profile of the currently authenticated user |

### Users
| Method | Path | Description |
|--------|------|-------------|
| GET | `/users` | Fetch all users |
| POST | `/users` | Register new user (`username`, `email`, `fullName`, `role`, `password`) |
| GET | `/users/:id` | Fetch user by id |
| PATCH | `/users/:id` | Update `fullName` or `role` |
| DELETE | `/users/:id` | Delete user |
| GET | `/users/:id/mentions` | All comments mentioning this user, newest first (paginated) |

### Projects
| Method | Path | Description |
|--------|------|-------------|
| GET | `/projects` | Fetch all active projects |
| POST | `/projects` | Create project (`name`, `description`, `ownerId`) |
| GET | `/projects/:id` | Fetch project by id |
| PATCH | `/projects/:id` | Update `name` or `description` |
| DELETE | `/projects/:id` | Soft-delete project |
| GET | `/projects/deleted` | List soft-deleted projects (ADMIN only) |
| POST | `/projects/:id/restore` | Restore soft-deleted project (ADMIN only) |
| GET | `/projects/:id/workload` | `[{ userId, username, openTicketCount }]` sorted ascending |

### Tickets
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets?projectId=` | Fetch all active tickets for a project |
| POST | `/tickets` | Create ticket (`title`, `description`, `status`, `priority`, `type`, `projectId`, optional `assigneeId`, optional `dueDate`) |
| GET | `/tickets/:id` | Fetch ticket by id |
| PATCH | `/tickets/:id` | Update ticket fields (cannot update DONE tickets; status forward-only) |
| DELETE | `/tickets/:id` | Soft-delete ticket |
| GET | `/tickets/deleted?projectId=` | List soft-deleted tickets (ADMIN only) |
| POST | `/tickets/:id/restore` | Restore soft-deleted ticket (ADMIN only) |
| GET | `/tickets/export?projectId=` | Export tickets as CSV (`id, title, description, status, priority, type, assigneeId`) |
| POST | `/tickets/import` | Import tickets from CSV (`multipart/form-data` with `projectId` field). Returns `{ created, failed, errors }` |

### Comments
| Method | Path | Description |
|--------|------|-------------|
| GET | `/tickets/:id/comments` | Fetch all comments for a ticket |
| POST | `/tickets/:id/comments` | Add comment (`content`, `authorId`) |
| PATCH | `/tickets/:id/comments/:cid` | Update comment content; re-evaluates @mentions |
| DELETE | `/tickets/:id/comments/:cid` | Delete comment |

### Audit Logs
| Method | Path | Description |
|--------|------|-------------|
| GET | `/audit-logs` | Fetch all logs. Filterable by `entityType`, `entityId`, `action`, `actor` query params |

### Ticket Dependencies
| Method | Path | Description |
|--------|------|-------------|
| POST | `/tickets/:id/dependencies` | Add dependency: `{ "blockedBy": <ticketId> }` |
| GET | `/tickets/:id/dependencies` | List all tickets blocking this ticket |
| DELETE | `/tickets/:id/dependencies/:blockerId` | Remove a specific dependency |

### Attachments
| Method | Path | Description |
|--------|------|-------------|
| POST | `/tickets/:id/attachments` | Upload file (multipart, max 10 MB, allowed types only) |
| DELETE | `/tickets/:id/attachments/:attachmentId` | Delete attachment |

---

## Business Logic Rules

### Status Transition
- Allowed: `TODO → IN_PROGRESS → IN_REVIEW → DONE`
- Backward transitions must be rejected with a `400` error
- A `DONE` ticket cannot be updated at all

### Concurrent Update Protection
- Tickets and Comments use **optimistic locking** (`@Version` field)
- On conflict (two simultaneous updates), return `409 Conflict`

### Auto-Assignment (on ticket creation only)
- Triggered when `assigneeId` is absent in the create request
- Queries all `DEVELOPER` role users in the project
- Selects the one with the fewest non-DONE tickets in that project
- Ties broken by earliest `createdAt` (oldest registrant first)
- If no `DEVELOPER` exists in the project, ticket is created with `assigneeId = null` (no error)
- Every auto-assignment is written to AuditLog with `actor = SYSTEM`, `action = AUTO_ASSIGN`
- Auto-assignment is **not** triggered on ticket update

### Auto-Escalation (scheduled)
- A `@Scheduled` task runs periodically to check overdue tickets
- For each ticket where `dueDate` has passed and `priority < CRITICAL`: promote priority one level (`LOW → MEDIUM → HIGH → CRITICAL`)
- When a ticket reaches `CRITICAL` and is still overdue: set `isOverdue = true`
- Escalation is **idempotent**: `CRITICAL` tickets are never escalated further
- Only applies to tickets with `dueDate` set
- A manual `PATCH /tickets/:id` that changes `priority` resets escalation state: `isOverdue` is cleared, next cycle re-evaluates from new priority
- Escalation does **not** change `status`, only `priority` and `isOverdue`

### Ticket Dependencies (blocking)
- A ticket cannot transition to `DONE` if any of its blockers have status ≠ `DONE`
- Both tickets in a dependency must belong to the same project

### Soft Delete
- Soft-deleted records have `deletedAt` set to the deletion timestamp
- All standard GET endpoints exclude soft-deleted records
- `/deleted` endpoints and restore endpoints are restricted to `ADMIN` role

### @Mention Parsing
- When a comment is created or updated, scan `content` for `@username` patterns
- Match case-insensitively against existing usernames
- Persist valid mentions as a many-to-many relationship
- On update: diff old vs new mentions — create newly added, delete removed
- Each comment response includes `mentionedUsers: [{ id, username, fullName }]`

### Ticket CSV Export/Import
- Export: `GET /tickets/export?projectId=` returns `Content-Type: text/csv` with fields `id, title, description, status, priority, type, assigneeId`
- Import: `POST /tickets/import` accepts `multipart/form-data` with a CSV file and `projectId` form field
- CSV must correctly handle commas and quotes inside field values (use Apache Commons CSV)
- Import returns `{ "created": N, "failed": N, "errors": [...] }`

---

## Authentication & Security

- All endpoints except `POST /auth/login` require `Authorization: Bearer <token>`
- JWT is signed server-side; validate on every request via a filter
- Logout: maintain a server-side token deny-list, or rely on short expiry
- `GET /auth/me` returns the authenticated user's profile from the token subject

---

## Input Validation & Error Handling

- Reject invalid enum values (status, priority, type, role) with `400 Bad Request`
- Reject missing required fields with `400 Bad Request`
- Return informative error bodies: `{ "error": "...", "message": "..." }`
- Use `@Valid` + Bean Validation annotations on all request DTOs
- Use a `@ControllerAdvice` global exception handler to standardize error responses
- File upload violations (wrong type, too large) return `400`
- Optimistic lock conflicts return `409 Conflict`
- Auth failures return `401 Unauthorized`; authorization failures return `403 Forbidden`

---

## Background Behaviors

| Behavior | Trigger | Notes |
|----------|---------|-------|
| Auto-Escalation | `@Scheduled` periodic task | Bumps priority of overdue tickets; sets `isOverdue` flag |
| Auto-Assignment | Ticket creation without `assigneeId` | Assigns least-loaded `DEVELOPER`; logs to AuditLog as SYSTEM |

---

## Testing

- Tests use **H2** in-memory datasource — no Docker needed
- Cover key behaviors: status transitions, concurrent update rejection, auto-assign, auto-escalation, dependency blocking, soft delete/restore, CSV import/export, mention parsing
- Spring Boot test docs: https://docs.spring.io/spring-boot/reference/testing/index.html

---

## Deliverables Checklist

- [ ] `run.md` — exact steps to install, start DB, build, run, and test
- [ ] `prompts.md` — key AI prompts used, with model name stated explicitly
- [ ] Instruction files / skills added to repo
- [ ] All files committed and repo is public