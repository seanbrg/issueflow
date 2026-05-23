Read CLAUDE.md. Create all JPA entity classes and enums for the domain model:
User, Project, Ticket, Comment, AuditLog, TicketDependency, Attachment.
Include all fields, relationships (@ManyToOne, @OneToMany etc.), and Lombok
annotations. Add @Version fields to Ticket and Comment for optimistic locking.
Add softDelete (deletedAt) to Ticket and Project. Do not create any
repositories, services, or controllers yet.

Create JpaRepository interfaces for all entities created in the previous step.
Add only the custom query methods that will clearly be needed: findByProjectId,
findByDeletedAtIsNull, findByAssigneeId, etc. Use Spring Data method naming
conventions where possible, @Query only when necessary.

Review application.yaml for PostgreSQL using the docker compose.yml already
in the project, and the separate application.yaml in the test module that configures H2 for
test. Make sure it functions correctly and remove any redundancy.