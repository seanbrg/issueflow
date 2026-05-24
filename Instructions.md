```
Read CLAUDE.md. Create all JPA entity classes and enums for the domain model:
User, Project, Ticket, Comment, AuditLog, TicketDependency, Attachment.
Include all fields, relationships (@ManyToOne, @OneToMany etc.), and Lombok
annotations. Add @Version fields to Ticket and Comment for optimistic locking.
Add softDelete (deletedAt) to Ticket and Project. Do not create any
repositories, services, or controllers yet.
```

```
Create JpaRepository interfaces for all entities created in the previous step.
Add only the custom query methods that will clearly be needed: findByProjectId,
findByDeletedAtIsNull, findByAssigneeId, etc. Use Spring Data method naming
conventions where possible, @Query only when necessary.
```

```
Review application.yaml for PostgreSQL using the docker compose.yml already
in the project, and the separate application.yaml in the test module that configures H2 for
test. Make sure it functions correctly and remove any redundancy.
```

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

```
Implement the full Users feature:
- Service and Controller for GET /users, POST /users, GET /users/:id,
  POST /users/:id, DELETE /users/:id
- Request/response DTOs with Bean Validation annotations
- Global @ControllerAdvice exception handler that returns
  { "error": "...", "message": "..." } for all error cases
- Write at least one unit test for the service layer
```

```
Implement Projects CRUD: GET/POST /projects, GET/PATCH/DELETE /projects/:id.
Use the same DTO and error handling patterns established in the Users feature.
Do not implement soft delete or workload yet — just basic CRUD.
Write unit tests for the service layer.
```

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

```
Implement Comment CRUD: GET/POST /tickets/:id/comments,
PATCH/DELETE /tickets/:id/comments/:cid.
Enforce optimistic locking (return 409 on conflict).
Do not implement @mention parsing yet.
Write unit tests for the service layer.
```

```
Implement the AuditLog feature:
- Review AuditLogService and the method
  log(action, entityType, entityId, actor, performedBy) according to specs
- Inject AuditLogService into the existing User, Project, Ticket, and Comment
  services and add log() calls after every state-changing operation
- Implement GET /audit-logs with optional query param filters:
  entityType, entityId, action, actor
```

```
Verify that soft delete works as intended for Tickets and Projects:
- DELETE endpoints set deletedAt instead of physically deleting
- All existing GET endpoints must exclude soft-deleted records
  (add deletedAt IS NULL conditions)
- Make GET /tickets/deleted?projectId= and GET /projects/deleted ADMIN only: add role constraints by checking the user entity's role field
- Make POST /tickets/:id/restore and POST /projects/:id/restore ADMIN only
- Add role-based authorization to Spring Security for the ADMIN-only endpoints
```

```
Add auto-assignment logic to ticket creation only:
- If assigneeId is absent, query all DEVELOPER users in the project
- Assign the one with the fewest non-DONE tickets in that project
- Break ties by earliest createdAt
- If no DEVELOPERs exist, leave assigneeId = null
- Log every auto-assignment to AuditLog with actor=SYSTEM, action=AUTO_ASSIGN
- Implement GET /projects/:id/workload returning 
  [{ userId, username, openTicketCount }] sorted ascending, with openTicketCount = count of non DONE tickets with assigneeId = userId
- Implement forbidding auto-assignment by explicitly providing assigneeId in a PATCH /tickets/{id} request.
Write unit tests for the tie-breaking and no-developer-found cases.
```

```
Implement ticket dependencies:
- POST /tickets/:id/dependencies with body { "blockedBy": <id> }
- GET /tickets/:id/dependencies
- DELETE /tickets/:id/dependencies/:blockerId
- Enforce: both tickets must belong to the same project
- Enforce: a ticket cannot transition to DONE if any blocker is not DONE
  (add this check to the existing status transition logic in TicketService)
Write unit tests for the blocking-DONE constraint.
```

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