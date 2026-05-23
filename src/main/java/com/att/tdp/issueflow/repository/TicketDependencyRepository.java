package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.TicketDependencyId;
import com.att.tdp.issueflow.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, TicketDependencyId> {

    // All blockers for a given ticket (GET /tickets/:id/dependencies)
    List<TicketDependency> findAllByTicket_Id(Long ticketId);

    // Specific dependency lookup — used for DELETE /tickets/:id/dependencies/:blockerId
    Optional<TicketDependency> findByTicket_IdAndBlockedBy_Id(Long ticketId, Long blockedById);

    // Guard for DONE transition: true if any blocker is not yet DONE
    boolean existsByTicket_IdAndBlockedBy_StatusNot(Long ticketId, TicketStatus status);

    // Duplicate-dependency guard before inserting
    boolean existsByTicket_IdAndBlockedBy_Id(Long ticketId, Long blockedById);
}
