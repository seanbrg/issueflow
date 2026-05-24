package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final TicketDependencyRepository ticketDependencyRepository;

    public List<TicketResponse> getByProject(Long projectId) {
        return ticketRepository.findAllByProject_IdAndDeletedAtIsNull(projectId).stream()
                .map(TicketResponse::from)
                .toList();
    }

    public List<TicketResponse> getDeleted(Long projectId) {
        return ticketRepository.findAllByProject_IdAndDeletedAtIsNotNull(projectId).stream()
                .map(TicketResponse::from)
                .toList();
    }

    public TicketResponse getById(Long id) {
        return TicketResponse.from(findActiveOrThrow(id));
    }

    public TicketResponse create(CreateTicketRequest request) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.getProjectId()));

        User assignee = null;
        boolean autoAssigned = false;
        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getAssigneeId()));
        } else {
            assignee = autoAssign(project.getId());
            autoAssigned = assignee != null;
        }

        Ticket ticket = Ticket.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .priority(request.getPriority())
                .type(request.getType())
                .project(project)
                .assignee(assignee)
                .dueDate(request.getDueDate())
                .overdue(false)
                .build();

        Ticket saved = ticketRepository.save(ticket);
        auditLogService.log("CREATE", "TICKET", saved.getId(), ActorType.USER);
        if (autoAssigned) {
            auditLogService.log("AUTO_ASSIGN", "TICKET", saved.getId(), ActorType.SYSTEM, "SYSTEM");
        }
        return TicketResponse.from(saved);
    }

    public TicketResponse update(Long id, UpdateTicketRequest request) {
        Ticket ticket = findActiveOrThrow(id);

        if (ticket.getStatus() == TicketStatus.DONE) {
            throw new ConflictException("Cannot update a DONE ticket");
        }

        if (request.getStatus() != null) {
            validateTransition(ticket.getStatus(), request.getStatus());
            if (request.getStatus() == TicketStatus.DONE &&
                    ticketDependencyRepository.existsByTicket_IdAndBlockedBy_StatusNot(id, TicketStatus.DONE)) {
                throw new BadRequestException("Cannot transition to DONE: ticket has unresolved blockers");
            }
            ticket.setStatus(request.getStatus());
        }
        if (request.getTitle() != null) ticket.setTitle(request.getTitle());
        if (request.getDescription() != null) ticket.setDescription(request.getDescription());
        if (request.getPriority() != null) ticket.setPriority(request.getPriority());
        if (request.getType() != null) ticket.setType(request.getType());
        if (request.getDueDate() != null) ticket.setDueDate(request.getDueDate());
        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getAssigneeId()));
            ticket.setAssignee(assignee);
        }

        Ticket updated = ticketRepository.save(ticket);
        auditLogService.log("UPDATE", "TICKET", id, ActorType.USER);
        return TicketResponse.from(updated);
    }

    public void softDelete(Long id) {
        Ticket ticket = findActiveOrThrow(id);
        ticket.setDeletedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        auditLogService.log("DELETE", "TICKET", id, ActorType.USER);
    }

    public TicketResponse restore(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        if (ticket.getDeletedAt() == null) {
            throw new ConflictException("Ticket is not deleted: " + id);
        }
        ticket.setDeletedAt(null);
        TicketResponse response = TicketResponse.from(ticketRepository.save(ticket));
        auditLogService.log("RESTORE", "TICKET", id, ActorType.USER);
        return response;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Selects the DEVELOPER with the fewest non-DONE tickets in the given project.
     * Ties are broken by earliest createdAt (oldest registrant first).
     * Returns null if no DEVELOPER users exist.
     * Auto-assignment is only triggered on ticket creation, never on update.
     */
    private User autoAssign(Long projectId) {
        List<User> developers = userRepository.findByRole(UserRole.DEVELOPER);
        if (developers.isEmpty()) return null;

        return developers.stream()
                .min(Comparator.<User, Long>comparing(dev ->
                                ticketRepository.countByAssignee_IdAndProject_IdAndStatusNotAndDeletedAtIsNull(
                                        dev.getId(), projectId, TicketStatus.DONE))
                        .thenComparing(User::getCreatedAt))
                .orElse(null);
    }

    private void validateTransition(TicketStatus current, TicketStatus next) {
        if (next.ordinal() < current.ordinal()) {
            throw new BadRequestException(
                    "Invalid status transition: " + current + " → " + next + ". Transitions are forward-only.");
        }
    }

    private Ticket findActiveOrThrow(Long id) {
        return ticketRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }
}
