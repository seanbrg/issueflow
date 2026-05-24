# AI Usage – IssueFlow

## Model

All code in this project was generated with **Claude Sonnet 4.6** (`claude-sonnet-4-6`), accessed through the **Claude Code** CLI (`claude` in the terminal). Claude Code is Anthropic's official agentic coding tool: it reads, writes, and edits files; runs shell commands; and executes multi-step tasks autonomously within the repo.

---

## How Claude Code Was Used

The entire backend was built through an incremental, prompt-driven workflow. Each prompt was a self-contained instruction that described a single feature or concern. Claude Code read the existing codebase before writing anything, matched the established patterns, and ran `./mvnw test` after significant changes to verify nothing regressed.

The session was structured as a build-up of layers:

1. **Data layer first** — entities and repositories before any business logic
2. **Infrastructure next** — auth, security config, exception handling
3. **Feature-by-feature** — each domain feature (users, projects, tickets, comments, etc.) added in isolation
4. **Cross-cutting concerns last** — audit logging, soft delete, scheduled tasks, then a final consistency review

At each step, Claude Code was given explicit constraints ("do not implement X yet") so that complexity was introduced gradually and each layer could be tested in isolation before the next was added.

Claude Code also handled ancillary tasks beyond code generation:
- Configuring Spring Data query methods and `@Query` annotations
- Writing unit tests (Mockito) and integration tests (`@SpringBootTest` + MockMvc) for every feature
- Diagnosing and fixing its own test failures when they occurred
- Creating `run.md` with setup and build instructions

---

## Prompts

The prompts below are reproduced in the order they were submitted. Each one maps to a distinct commit or set of commits in the git history.

---

### 1. Entity and Enum Classes

```
Create all JPA entity classes and enums for the domain model:
User, Project, Ticket, Comment, AuditLog, TicketDependency, Attachment.
Include all fields, relationships (@ManyToOne, @OneToMany etc.), and Lombok
annotations. Add @Version fields to Ticket and Comment for optimistic locking.
Add softDelete (deletedAt) to Ticket and Project. Do not create any
repositories, services, or controllers yet.
```

Claude created all seven entity classes and their associated enums (`TicketStatus`, `TicketPriority`, `TicketType`, `UserRole`, `ActorType`), applied Lombok `@Data`/`@Builder`, and added `@Version` to `Ticket` and `Comment` for optimistic locking. The explicit "do not create anything else" boundary kept this step focused.

---

### 2. Repository Interfaces

```
Create JpaRepository interfaces for all entities created in the previous step.
Add only the custom query methods that will clearly be needed: findByProjectId,
findByDeletedAtIsNull, findByAssigneeId, etc. Use Spring Data method naming
conventions where possible, @Query only when necessary.
```

Claude generated seven repository interfaces, preferring Spring Data derived query names (e.g. `findAllByProject_IdAndDeletedAtIsNull`) and falling back to `@Query` only for the workload aggregate query that couldn't be expressed by naming convention alone.

---

### 3. Database Configuration

```
Review application.yaml for PostgreSQL using the docker compose.yml already
in the project, and the separate application.yaml in the test module that configures H2 for
test. Make sure it functions correctly and remove any redundancy.
```

Claude audited both YAML files, confirmed the PostgreSQL datasource matched the `compose.yml` credentials, and ensured the test profile correctly isolated itself to H2 so that no Docker dependency was introduced for `./mvnw test`.

---

### 4. JWT Authentication

```
Implement JWT-based authentication. Create:
- POST /auth/login — accepts username + password, returns signed JWT
- POST /auth/logout — server-side token deny-list using an in-memory set
  (we can persist it later)
- GET /auth/me — returns the authenticated user's profile
- A JWT filter that validates the token on every request except /auth/login
- Configure Spring Security to protect all routes

Do not implement role-based authorization yet, just authentication.
```

Claude implemented `JwtTokenProvider`, `JwtAuthenticationFilter`, `TokenDenyList`, `AuthEntryPoint`, and `SecurityConfig`. Using jjwt 0.12.6, the filter validates the Bearer token on every request and rejects deny-listed tokens. Role-based rules were intentionally deferred to a later prompt.

---

### 5. Auth Integration Tests

```
Write an integration test for the auth endpoints using H2. Use
@SpringBootTest and @AutoConfigureMockMvc. Test the following cases:

1. POST /auth/login with valid credentials returns 200 and a JWT token
2. POST /auth/login with wrong password returns 401
3. POST /auth/login with unknown username returns 401
4. GET /auth/me with a valid JWT returns 200 and the user's profile
5. GET /auth/me with no token returns 401
6. GET /auth/me with a malformed token returns 401
7. POST /auth/logout with a valid JWT returns 200, and a subsequent
   GET /auth/me with the same token returns 401

Set up test data by saving a User directly via the UserRepository
in a @BeforeEach method. Use BCryptPasswordEncoder to hash the
password before saving so login works correctly.
```

Claude wrote `AuthControllerTest` covering all seven cases. Each test is independent — `@BeforeEach` wipes and re-seeds the H2 database. The logout test demonstrates that the in-memory deny-list correctly blocks a reused token within the same application context.

---

### 6. Users Feature

```
Implement the full Users feature:
- Service and Controller for GET /users, POST /users, GET /users/:id,
  POST /users/:id, DELETE /users/:id
- Request/response DTOs with Bean Validation annotations
- Global @ControllerAdvice exception handler that returns
  { "error": "...", "message": "..." } for all error cases
- Write at least one unit test for the service layer
```

Claude implemented `UserService`, `UserController`, all user DTOs, and `GlobalExceptionHandler`. The exception handler covers `ResourceNotFoundException` (404), `ConflictException` (409), `BadRequestException` (400), `MethodArgumentNotValidException` (400 with field-level messages), `ObjectOptimisticLockingFailureException` (409), and a catch-all 500.

---

### 7. Projects CRUD

```
Implement Projects CRUD: GET/POST /projects, GET/PATCH/DELETE /projects/:id.
Use the same DTO and error handling patterns established in the Users feature.
Do not implement soft delete or workload yet — just basic CRUD.
Write unit tests for the service layer.
```

Claude followed the established DTO and exception patterns exactly, keeping the implementation consistent with Users. Soft delete and workload were explicitly excluded here so they could be introduced alongside the ticket soft delete in a single focused prompt later.

---

### 8. Ticket CRUD

```
Implement basic Ticket CRUD: GET /tickets?projectId=, POST /tickets,
GET/PATCH/DELETE /tickets/:id.
Enforce these business rules in the service layer:
- Status transitions are forward-only (TODO→IN_PROGRESS→IN_REVIEW→DONE)
- A DONE ticket cannot be updated
- Return 409 on optimistic lock conflict (@Version)
Do not implement auto-assign, dependencies, soft delete, or export yet.
Write unit tests covering the status transition rules.
```

Claude implemented `TicketService` and `TicketController`. The forward-only transition check uses ordinal comparison (`next.ordinal() < current.ordinal()`), making it automatically correct for any future enum reordering. Unit tests cover all valid and invalid transitions.

---

### 9. Comments CRUD

```
Implement Comment CRUD: GET/POST /tickets/:id/comments,
PATCH/DELETE /tickets/:id/comments/:cid.
Enforce optimistic locking (return 409 on conflict).
Do not implement @mention parsing yet.
Write unit tests for the service layer.
```

Claude scoped comments correctly to their parent ticket using `findByIdAndTicket_Id` for all lookups, preventing cross-ticket access. `@mention` parsing was deferred to keep this step small.

---

### 10. Audit Logging

```
Implement the AuditLog feature:
- Review AuditLogService and the method
  log(action, entityType, entityId, actor, performedBy) according to specs
- Inject AuditLogService into the existing User, Project, Ticket, and Comment
  services and add log() calls after every state-changing operation
- Implement GET /audit-logs with optional query param filters:
  entityType, entityId, action, actor
```

Claude added `AuditLogService` with a `JpaSpecification`-based dynamic query for the filtered GET endpoint, then threaded `log()` calls into all four existing services without altering their existing logic. A second overload resolves the current authenticated user from the `SecurityContext` automatically, so callers don't need to pass the performer explicitly.

---

### 11. Soft Delete and Role-Based Authorization

```
Verify that soft delete works as intended for Tickets and Projects:
- DELETE endpoints set deletedAt instead of physically deleting
- All existing GET endpoints must exclude soft-deleted records
  (add deletedAt IS NULL conditions)
- Make GET /tickets/deleted?projectId= and GET /projects/deleted ADMIN only
- Make POST /tickets/:id/restore and POST /projects/:id/restore ADMIN only
- Add role-based authorization to Spring Security for the ADMIN-only endpoints
```

Claude confirmed all GET queries already used `deletedAt IS NULL` variants, added the `/deleted` and `/restore` endpoints, and applied `@PreAuthorize("hasRole('ADMIN')")` to restrict them. Integration tests (`ProjectSoftDeleteControllerTest`, `TicketSoftDeleteControllerTest`) verify the full soft-delete lifecycle including 403 responses for non-admin users.

---

### 12. Auto-Assignment and Workload

```
Add auto-assignment logic to ticket creation only:
- If assigneeId is absent, query all DEVELOPER users in the project
- Assign the one with the fewest non-DONE tickets in that project
- Break ties by earliest createdAt
- If no DEVELOPERs exist, leave assigneeId = null
- Log every auto-assignment to AuditLog with actor=SYSTEM, action=AUTO_ASSIGN
- Implement GET /projects/:id/workload returning
  [{ userId, username, openTicketCount }] sorted ascending
Write unit tests for the tie-breaking and no-developer-found cases.
```

Claude implemented the least-loaded selection using a `Comparator` chain (open ticket count, then `createdAt`). The workload endpoint uses a native aggregate query to count non-DONE tickets per assignee. Unit tests cover tie-breaking and the no-developer edge case.

---

### 13. Ticket Dependencies

```
Implement ticket dependencies:
- POST /tickets/:id/dependencies with body { "blockedBy": <id> }
- GET /tickets/:id/dependencies
- DELETE /tickets/:id/dependencies/:blockerId
- Enforce: both tickets must belong to the same project
- Enforce: a ticket cannot transition to DONE if any blocker is not DONE
Write unit tests for the blocking-DONE constraint.
```

Claude implemented BFS cycle detection in `TicketDependencyService` to prevent circular dependencies, and hooked the blocker check into the existing `TicketService.update()` status transition logic. The composite primary key (`TicketDependencyId`) is handled with `@EmbeddedId` and `@MapsId`.

---

### 14. @Mention Parsing

```
Add @mention parsing to comments:
- On create (POST) and update (PATCH) comment, scan content for @username patterns (case-insensitive)
- Match against real usernames in the DB; ignore unmatched ones
- Persist valid mentions as a MentionedUser many-to-many join
- On update: diff old vs new — create new mentions, delete removed ones
- Include mentionedUsers: [{ id, username, fullName }] in all comment responses
- Implement GET /users/:id/mentions returning comments mentioning that user,
  newest first, paginated
```

Claude added a regex-based mention parser to `CommentService`, a `findByUsernamesLowercase` repository method for case-insensitive batch lookup, and a many-to-many join between `Comment` and `User`. On update, the service diffs old and new mention sets rather than deleting and re-inserting all of them.

---

### 15. Auto-Escalation Scheduler

```
Implement the auto-escalation scheduled task:
- Create a @Scheduled method that runs periodically (every hour)
- For each non-DONE ticket with a dueDate in the past and priority < CRITICAL:
  promote priority one level
- When a ticket hits CRITICAL and is still overdue, set flag is_overdue = true
- Escalation is idempotent: never escalate beyond CRITICAL
- A manual PATCH /tickets/:id that changes priority resets is_overdue to false
- Log each escalation to AuditLog with actor=SYSTEM
Write a unit test that mocks the clock to verify promotion logic.
```

Claude implemented `EscalationScheduler` with a custom `@Query` that selects only eligible tickets (non-DONE, past due date, priority below CRITICAL), making the task efficient regardless of ticket volume. The idempotency guarantee comes from the query itself — CRITICAL tickets are never fetched.

---

### 16. CSV Export and Import

```
Implement ticket CSV export and import using Apache Commons CSV:
- GET /tickets/export?projectId= returns Content-Type: text/csv
- POST /tickets/import accepts multipart/form-data with a CSV file and projectId
- Returns { "created": N, "failed": N, "errors": [...] }
- Handle commas and quotes inside field values correctly
Write a unit test for the import with a sample CSV that includes edge cases.
```

Claude used Apache Commons CSV's `CSVFormat.DEFAULT` (RFC 4180 quoting) for both export and import, ensuring embedded commas and quotes in field values are handled correctly. Import processes each row independently — a bad row is recorded in `errors` and does not abort the remaining rows.

---

### 17. File Attachments

```
Implement file attachments on tickets:
- POST /tickets/:id/attachments — multipart upload, store file bytes in DB
- DELETE /tickets/:id/attachments/:attachmentId
- Reject files over 10 MB with 400
- Reject MIME types other than image/png, image/jpeg, application/pdf,
  text/plain with 400
```

Claude stored attachment bytes as a `@Lob byte[]` column, validated size and MIME type before saving, and scoped all lookups to the parent ticket with `findByIdAndTicket_Id` to prevent cross-ticket access.

---

### 18. Final Consistency Review

```
Review the entire codebase for consistency, with README.md being the single source of truth:
- Make sure every endpoint has input validation with informative error messages
- Make sure every state-changing endpoint writes to AuditLog
- Make sure soft-deleted records are excluded from all standard GET responses
- Write run.md with exact steps to: install dependencies, start Docker DB,
  build the project, run the app, and run the tests
```

Claude performed a full audit across all controllers, services, and DTOs. Findings and fixes:
- Added `@Valid` to the three PATCH/update endpoints that were missing it (`PATCH /projects/:id`, `PATCH /tickets/:id`, `POST /users/update/:id`)
- Added `UPLOAD_ATTACHMENT` and `DELETE_ATTACHMENT` audit log entries to `AttachmentService`
- Added `ADD_DEPENDENCY` and `REMOVE_DEPENDENCY` audit log entries to `TicketDependencyService`
- Updated `AttachmentServiceTest` to mock the newly injected `AuditLogService`
- Created `run.md`
