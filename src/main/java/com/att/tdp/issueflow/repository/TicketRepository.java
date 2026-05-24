package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketPriority;
import com.att.tdp.issueflow.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // --- Active ticket lookups ---

    List<Ticket> findAllByProject_IdAndDeletedAtIsNull(Long projectId);

    Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);

    // --- Soft-deleted ticket lookups (ADMIN) ---

    List<Ticket> findAllByProject_IdAndDeletedAtIsNotNull(Long projectId);

    // --- Auto-assignment: count open (non-DONE) tickets for a developer in a project ---

    long countByAssignee_IdAndProject_IdAndStatusNotAndDeletedAtIsNull(
            Long assigneeId, Long projectId, TicketStatus status);

    // --- Auto-escalation: tickets that are overdue, not DONE, and not yet CRITICAL ---

    @Query("""
            SELECT t FROM Ticket t
            WHERE t.dueDate IS NOT NULL
              AND t.dueDate < :today
              AND t.status <> :doneStatus
              AND t.priority <> :criticalPriority
              AND t.deletedAt IS NULL
            """)
    List<Ticket> findEscalationCandidates(
            @Param("today") LocalDate today,
            @Param("doneStatus") TicketStatus doneStatus,
            @Param("criticalPriority") TicketPriority criticalPriority);

    // --- Workload report: open ticket count per assignee in a project ---
    // Returns rows of [assigneeId (Long), username (String), openTicketCount (Long)]

    @Query("""
            SELECT t.assignee.id, t.assignee.username, COUNT(t)
            FROM Ticket t
            WHERE t.project.id = :projectId
              AND t.status <> :doneStatus
              AND t.deletedAt IS NULL
              AND t.assignee IS NOT NULL
            GROUP BY t.assignee.id, t.assignee.username
            ORDER BY COUNT(t) ASC
            """)
    List<Object[]> countOpenTicketsByAssigneeForProject(
            @Param("projectId") Long projectId,
            @Param("doneStatus") TicketStatus doneStatus);
}
