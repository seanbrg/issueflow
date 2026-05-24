package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks TicketService ticketService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id) {
        return User.builder().id(id).username("user" + id).email("u" + id + "@x.com")
                .fullName("User " + id).role(UserRole.DEVELOPER).password("h")
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
    }

    private Project buildProject(Long id) {
        return Project.builder().id(id).name("P" + id).owner(buildUser(1L))
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
    }

    private Ticket buildTicket(Long id, TicketStatus status) {
        return Ticket.builder()
                .id(id)
                .title("Ticket " + id)
                .description("desc")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .type(TicketType.BUG)
                .project(buildProject(1L))
                .overdue(false)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .version(0L)
                .build();
    }

    private UpdateTicketRequest statusRequest(TicketStatus status) {
        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(status);
        return req;
    }

    private User buildDeveloper(Long id, LocalDateTime createdAt) {
        return User.builder().id(id).username("dev" + id).email("dev" + id + "@x.com")
                .fullName("Dev " + id).role(UserRole.DEVELOPER).password("h")
                .createdAt(createdAt).build();
    }

    private CreateTicketRequest buildCreateRequest(Long projectId) {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("T");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.LOW);
        req.setType(TicketType.BUG);
        req.setProjectId(projectId);
        return req;
    }

    // ── getByProject ──────────────────────────────────────────────────────────

    @Test
    void getByProject_returnsMappedTickets() {
        when(ticketRepository.findAllByProject_IdAndDeletedAtIsNull(1L))
                .thenReturn(List.of(buildTicket(1L, TicketStatus.TODO), buildTicket(2L, TicketStatus.IN_PROGRESS)));

        List<TicketResponse> result = ticketService.getByProject(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TicketResponse::getStatus).containsExactly("TODO", "IN_PROGRESS");
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_found_returnsResponse() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildTicket(1L, TicketStatus.TODO)));

        TicketResponse response = ticketService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("TODO");
    }

    @Test
    void getById_notFound_throws() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesAndReturns() {
        Project project = buildProject(1L);
        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(10L);
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("Fix bug");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.HIGH);
        req.setType(TicketType.BUG);
        req.setProjectId(1L);

        TicketResponse response = ticketService.create(req);

        assertThat(response.getTitle()).isEqualTo("Fix bug");
        assertThat(response.getStatus()).isEqualTo("TODO");
        assertThat(response.getProjectId()).isEqualTo(1L);
        verify(ticketRepository).save(any());
    }

    @Test
    void create_unknownProject_throws() {
        when(projectRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("T");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.LOW);
        req.setType(TicketType.BUG);
        req.setProjectId(99L);

        assertThatThrownBy(() -> ticketService.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(ticketRepository, never()).save(any());
    }

    // ── auto-assignment ───────────────────────────────────────────────────────

    @Test
    void create_noDeveloper_assigneeIsNullAndNoAutoAssignLog() {
        Project project = buildProject(1L);
        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(project));
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of());
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(10L);
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        TicketResponse response = ticketService.create(buildCreateRequest(1L));

        assertThat(response.getAssigneeId()).isNull();
        verify(auditLogService, never()).log(eq("AUTO_ASSIGN"), any(), any(), any(), any());
    }

    @Test
    void create_tieBreaking_assignsEarliestCreatedDeveloper() {
        Project project = buildProject(1L);
        User dev1 = buildDeveloper(1L, LocalDateTime.of(2024, 3, 1, 0, 0)); // newer
        User dev2 = buildDeveloper(2L, LocalDateTime.of(2024, 1, 1, 0, 0)); // older — should win

        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(project));
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of(dev1, dev2));
        when(ticketRepository.countByAssignee_IdAndProject_IdAndStatusNotAndDeletedAtIsNull(
                1L, 1L, TicketStatus.DONE)).thenReturn(0L);
        when(ticketRepository.countByAssignee_IdAndProject_IdAndStatusNotAndDeletedAtIsNull(
                2L, 1L, TicketStatus.DONE)).thenReturn(0L);
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(10L);
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        ticketService.create(buildCreateRequest(1L));

        verify(ticketRepository).save(argThat(t -> t.getAssignee() != null && t.getAssignee().getId().equals(2L)));
        verify(auditLogService).log(eq("AUTO_ASSIGN"), eq("TICKET"), eq(10L), eq(ActorType.SYSTEM), eq("SYSTEM"));
    }

    @Test
    void create_leastLoadedDeveloper_isAssigned() {
        Project project = buildProject(1L);
        User dev1 = buildDeveloper(1L, LocalDateTime.of(2024, 1, 1, 0, 0));
        User dev2 = buildDeveloper(2L, LocalDateTime.of(2024, 1, 2, 0, 0));

        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(project));
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of(dev1, dev2));
        when(ticketRepository.countByAssignee_IdAndProject_IdAndStatusNotAndDeletedAtIsNull(
                1L, 1L, TicketStatus.DONE)).thenReturn(3L); // dev1 is busy
        when(ticketRepository.countByAssignee_IdAndProject_IdAndStatusNotAndDeletedAtIsNull(
                2L, 1L, TicketStatus.DONE)).thenReturn(1L); // dev2 has fewer — should be picked
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(10L);
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        ticketService.create(buildCreateRequest(1L));

        verify(ticketRepository).save(argThat(t -> t.getAssignee() != null && t.getAssignee().getId().equals(2L)));
        verify(auditLogService).log(eq("AUTO_ASSIGN"), eq("TICKET"), eq(10L), eq(ActorType.SYSTEM), eq("SYSTEM"));
    }

    @Test
    void create_explicitAssigneeId_skipsAutoAssignment() {
        Project project = buildProject(1L);
        User assignee = buildDeveloper(5L, LocalDateTime.now());

        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(project));
        when(userRepository.findById(5L)).thenReturn(Optional.of(assignee));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(10L);
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        CreateTicketRequest req = buildCreateRequest(1L);
        req.setAssigneeId(5L);
        ticketService.create(req);

        verify(userRepository, never()).findByRole(any());
        verify(auditLogService, never()).log(eq("AUTO_ASSIGN"), any(), any(), any(), any());
    }

    // ── status transitions — forward (allowed) ────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "TODO,        IN_PROGRESS",
            "IN_PROGRESS, IN_REVIEW",
            "IN_REVIEW,   DONE",
            "TODO,        IN_REVIEW",   // skipping a step is still forward
            "TODO,        DONE",
            "IN_PROGRESS, DONE",
    })
    void update_forwardTransition_succeeds(TicketStatus from, TicketStatus to) {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildTicket(1L, from)));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = ticketService.update(1L, statusRequest(to));

        assertThat(response.getStatus()).isEqualTo(to.name());
    }

    // ── status transitions — backward (rejected) ──────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "IN_PROGRESS, TODO",
            "IN_REVIEW,   IN_PROGRESS",
            "IN_REVIEW,   TODO",
            "DONE,        IN_REVIEW",
            "DONE,        IN_PROGRESS",
            "DONE,        TODO",
    })
    void update_backwardTransition_throwsBadRequest(TicketStatus from, TicketStatus to) {
        // DONE→* is blocked by the DONE-lock check first; the others hit validateTransition
        Ticket ticket = buildTicket(1L, from);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.update(1L, statusRequest(to)))
                .isInstanceOf(RuntimeException.class); // ConflictException (DONE) or BadRequestException

        verify(ticketRepository, never()).save(any());
    }

    // ── DONE ticket cannot be updated ────────────────────────────────────────

    @Test
    void update_doneTicket_throwsConflict() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildTicket(1L, TicketStatus.DONE)));

        // Even a non-status field change is rejected
        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setTitle("New title");

        assertThatThrownBy(() -> ticketService.update(1L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DONE");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void update_doneTicket_statusChange_throwsConflict() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildTicket(1L, TicketStatus.DONE)));

        assertThatThrownBy(() -> ticketService.update(1L, statusRequest(TicketStatus.IN_REVIEW)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DONE");

        verify(ticketRepository, never()).save(any());
    }

    // ── same-status is a no-op (allowed) ─────────────────────────────────────

    @Test
    void update_sameStatus_succeeds() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildTicket(1L, TicketStatus.IN_PROGRESS)));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = ticketService.update(1L, statusRequest(TicketStatus.IN_PROGRESS));

        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
    }

    // ── backward transition specifically throws BadRequestException ───────────

    @Test
    void update_backwardTransition_throwsBadRequestException() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildTicket(1L, TicketStatus.IN_REVIEW)));

        assertThatThrownBy(() -> ticketService.update(1L, statusRequest(TicketStatus.TODO)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("IN_REVIEW")
                .hasMessageContaining("TODO");
    }

    // ── softDelete ────────────────────────────────────────────────────────────

    @Test
    void softDelete_setsDeletedAtAndSavesAndLogs() {
        Ticket ticket = buildTicket(1L, TicketStatus.TODO);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ticketService.softDelete(1L);

        verify(ticketRepository, never()).deleteById(anyLong());
        verify(ticketRepository).save(argThat(t -> t.getDeletedAt() != null));
        verify(auditLogService).log(eq("DELETE"), eq("TICKET"), eq(1L), any(ActorType.class));
    }

    @Test
    void softDelete_notFound_throws() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.softDelete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(ticketRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Test
    void restore_clearsDeletedAtAndSavesAndLogs() {
        Ticket ticket = buildTicket(1L, TicketStatus.TODO);
        ticket.setDeletedAt(LocalDateTime.of(2024, 6, 1, 0, 0));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = ticketService.restore(1L);

        assertThat(response.getTitle()).isEqualTo("Ticket 1");
        verify(ticketRepository).save(argThat(t -> t.getDeletedAt() == null));
        verify(auditLogService).log(eq("RESTORE"), eq("TICKET"), eq(1L), any(ActorType.class));
    }

    @Test
    void restore_notFound_throws() {
        when(ticketRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.restore(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(ticketRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void restore_ticketNotDeleted_throwsConflict() {
        Ticket ticket = buildTicket(1L, TicketStatus.TODO); // deletedAt is null — already active
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.restore(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not deleted");

        verify(ticketRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    // ── getDeleted ────────────────────────────────────────────────────────────

    @Test
    void getDeleted_returnsOnlyDeletedTicketsForProject() {
        Ticket deleted = buildTicket(5L, TicketStatus.IN_PROGRESS);
        deleted.setDeletedAt(LocalDateTime.of(2024, 6, 1, 0, 0));
        when(ticketRepository.findAllByProject_IdAndDeletedAtIsNotNull(1L)).thenReturn(List.of(deleted));

        List<TicketResponse> result = ticketService.getDeleted(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(5L);
    }
}
