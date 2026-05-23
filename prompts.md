Read CLAUDE.md. Create all JPA entity classes and enums for the domain model:
User, Project, Ticket, Comment, AuditLog, TicketDependency, Attachment.
Include all fields, relationships (@ManyToOne, @OneToMany etc.), and Lombok
annotations. Add @Version fields to Ticket and Comment for optimistic locking.
Add softDelete (deletedAt) to Ticket and Project. Do not create any
repositories, services, or controllers yet.