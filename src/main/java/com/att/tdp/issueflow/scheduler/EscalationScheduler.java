package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.TicketPriority;
import com.att.tdp.issueflow.entity.TicketStatus;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.service.AuditLogService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class EscalationScheduler {

    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void escalate() {
        LocalDate today = LocalDate.now(clock);

        ticketRepository.findEscalationCandidates(today, TicketStatus.DONE, TicketPriority.CRITICAL)
                .forEach(ticket -> {
                    TicketPriority promoted = TicketPriority.values()[ticket.getPriority().ordinal() + 1];
                    ticket.setPriority(promoted);
                    if (promoted == TicketPriority.CRITICAL) {
                        ticket.setOverdue(true);
                    }
                    ticketRepository.save(ticket);
                    auditLogService.log("ESCALATE", "TICKET", ticket.getId(), ActorType.SYSTEM, "SYSTEM");
                });
    }
}
