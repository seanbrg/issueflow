package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.TicketDependencySummaryResponse;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.TicketDependencyId;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class TicketDependencyService {

    private final TicketDependencyRepository dependencyRepository;
    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;

    public TicketDependencySummaryResponse addDependency(Long ticketId, Long blockedById) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        Ticket blocker = ticketRepository.findByIdAndDeletedAtIsNull(blockedById)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + blockedById));

        if (ticketId.equals(blockedById)) {
            throw new BadRequestException("A ticket cannot block itself");
        }
        if (!ticket.getProject().getId().equals(blocker.getProject().getId())) {
            throw new BadRequestException("Both tickets must belong to the same project");
        }
        if (dependencyRepository.existsByTicket_IdAndBlockedBy_Id(ticketId, blockedById)) {
            throw new ConflictException("Dependency already exists");
        }
        if (wouldCreateCycle(ticketId, blockedById)) {
            throw new BadRequestException("Adding this dependency would create a circular dependency");
        }

        dependencyRepository.save(
                new TicketDependency(new TicketDependencyId(ticketId, blockedById), ticket, blocker));

        auditLogService.log("ADD_DEPENDENCY", "TICKET", ticketId, ActorType.USER);
        return TicketDependencySummaryResponse.from(blocker);
    }

    public List<TicketDependencySummaryResponse> getDependencies(Long ticketId) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        return dependencyRepository.findAllByTicket_Id(ticketId).stream()
                .map(dep -> TicketDependencySummaryResponse.from(dep.getBlockedBy()))
                .toList();
    }

    public void removeDependency(Long ticketId, Long blockerId) {
        TicketDependency dep = dependencyRepository.findByTicket_IdAndBlockedBy_Id(ticketId, blockerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dependency not found: ticket " + ticketId + " blocked by " + blockerId));
        dependencyRepository.delete(dep);
        auditLogService.log("REMOVE_DEPENDENCY", "TICKET", ticketId, ActorType.USER);
    }

    /**
     * BFS from {@code newBlockerId} following its own blockers.
     * If we reach {@code ticketId}, adding the new edge would create a cycle.
     */
    private boolean wouldCreateCycle(Long ticketId, Long newBlockerId) {
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(newBlockerId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(ticketId)) return true;
            if (!visited.add(current)) continue;
            dependencyRepository.findAllByTicket_Id(current)
                    .forEach(dep -> queue.add(dep.getId().getBlockedById()));
        }
        return false;
    }
}
