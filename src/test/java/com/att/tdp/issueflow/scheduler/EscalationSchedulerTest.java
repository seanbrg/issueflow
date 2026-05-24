package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalationSchedulerTest {

    @Mock TicketRepository ticketRepository;
    @Mock AuditLogService auditLogService;

    // Fixed clock: 2026-05-24 UTC — all due dates in tests are before this
    private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private final Clock fixedClock = Clock.fixed(NOW, UTC);

    private EscalationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EscalationScheduler(ticketRepository, auditLogService, fixedClock);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Ticket buildTicket(Long id, TicketPriority priority) {
        Project project = Project.builder()
                .id(1L).name("P").owner(buildUser(1L))
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
        return Ticket.builder()
                .id(id)
                .title("Ticket " + id)
                .description("desc")
                .status(TicketStatus.IN_PROGRESS)
                .priority(priority)
                .type(TicketType.BUG)
                .project(project)
                .dueDate(LocalDate.of(2026, 5, 20)) // 4 days before the fixed clock
                .overdue(false)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .version(0L)
                .build();
    }

    private User buildUser(Long id) {
        return User.builder().id(id).username("user" + id).email("u" + id + "@x.com")
                .fullName("User " + id).role(UserRole.DEVELOPER).password("h")
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
    }

    private void stubCandidates(Ticket... tickets) {
        when(ticketRepository.findEscalationCandidates(
                LocalDate.now(fixedClock), TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of(tickets));
        if (tickets.length > 0) {
            when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }
    }

    // ── priority promotion ────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → next level, isOverdue stays false")
    @EnumSource(value = TicketPriority.class, names = {"LOW", "MEDIUM"})
    void escalate_nonCriticalPromotion_doesNotSetOverdue(TicketPriority priority) {
        Ticket ticket = buildTicket(1L, priority);
        stubCandidates(ticket);

        scheduler.escalate();

        TicketPriority expected = TicketPriority.values()[priority.ordinal() + 1];
        assertThat(ticket.getPriority()).isEqualTo(expected);
        assertThat(ticket.isOverdue()).isFalse();
        verify(ticketRepository).save(ticket);
        verify(auditLogService).log("ESCALATE", "TICKET", 1L, ActorType.SYSTEM, "SYSTEM");
    }

    @Test
    void escalate_low_promotedToMedium() {
        Ticket ticket = buildTicket(1L, TicketPriority.LOW);
        stubCandidates(ticket);

        scheduler.escalate();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(ticket.isOverdue()).isFalse();
    }

    @Test
    void escalate_medium_promotedToHigh() {
        Ticket ticket = buildTicket(1L, TicketPriority.MEDIUM);
        stubCandidates(ticket);

        scheduler.escalate();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(ticket.isOverdue()).isFalse();
    }

    @Test
    void escalate_high_promotedToCriticalAndOverdueFlagSet() {
        Ticket ticket = buildTicket(1L, TicketPriority.HIGH);
        stubCandidates(ticket);

        scheduler.escalate();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.isOverdue()).isTrue();
        verify(ticketRepository).save(ticket);
        verify(auditLogService).log("ESCALATE", "TICKET", 1L, ActorType.SYSTEM, "SYSTEM");
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void escalate_noCandidates_nothingSavedOrLogged() {
        stubCandidates(); // empty list

        scheduler.escalate();

        verify(ticketRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void escalate_criticalTicketsNotReturnedByCandidateQuery() {
        // The repository query excludes CRITICAL tickets — verify we pass the right args
        when(ticketRepository.findEscalationCandidates(
                LocalDate.now(fixedClock), TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());

        scheduler.escalate();

        verify(ticketRepository).findEscalationCandidates(
                LocalDate.of(2026, 5, 24), TicketStatus.DONE, TicketPriority.CRITICAL);
        verify(ticketRepository, never()).save(any());
    }

    // ── multiple tickets ──────────────────────────────────────────────────────

    @Test
    void escalate_multipleTickets_eachPromotedAndLogged() {
        Ticket low = buildTicket(1L, TicketPriority.LOW);
        Ticket medium = buildTicket(2L, TicketPriority.MEDIUM);
        Ticket high = buildTicket(3L, TicketPriority.HIGH);
        stubCandidates(low, medium, high);

        scheduler.escalate();

        assertThat(low.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(low.isOverdue()).isFalse();

        assertThat(medium.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(medium.isOverdue()).isFalse();

        assertThat(high.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(high.isOverdue()).isTrue();

        verify(ticketRepository, times(3)).save(any());
        verify(auditLogService).log("ESCALATE", "TICKET", 1L, ActorType.SYSTEM, "SYSTEM");
        verify(auditLogService).log("ESCALATE", "TICKET", 2L, ActorType.SYSTEM, "SYSTEM");
        verify(auditLogService).log("ESCALATE", "TICKET", 3L, ActorType.SYSTEM, "SYSTEM");
    }

    // ── clock is used ─────────────────────────────────────────────────────────

    @Test
    void escalate_passesFixedClockDateToRepository() {
        when(ticketRepository.findEscalationCandidates(any(), any(), any())).thenReturn(List.of());

        scheduler.escalate();

        // Verifies the clock drives the date, not LocalDate.now()
        verify(ticketRepository).findEscalationCandidates(
                LocalDate.of(2026, 5, 24), TicketStatus.DONE, TicketPriority.CRITICAL);
    }
}
