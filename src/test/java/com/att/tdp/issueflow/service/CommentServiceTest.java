package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CommentResponse;
import com.att.tdp.issueflow.dto.CreateCommentRequest;
import com.att.tdp.issueflow.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock TicketRepository ticketRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks CommentService commentService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .email("user" + id + "@example.com")
                .fullName("User " + id)
                .role(UserRole.DEVELOPER)
                .password("hashed")
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }

    private Ticket buildTicket(Long id) {
        User owner = buildUser(1L);
        Project project = Project.builder()
                .id(1L).name("P1").owner(owner)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
        return Ticket.builder()
                .id(id)
                .title("Ticket " + id)
                .description("desc")
                .status(TicketStatus.TODO)
                .priority(TicketPriority.MEDIUM)
                .type(TicketType.BUG)
                .project(project)
                .overdue(false)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .version(0L)
                .build();
    }

    private Comment buildComment(Long id, Ticket ticket, User author) {
        return Comment.builder()
                .id(id)
                .ticket(ticket)
                .author(author)
                .content("Comment " + id)
                .createdAt(LocalDateTime.of(2024, 2, 1, 0, 0))
                .version(0L)
                .build();
    }

    // ── getByTicket ───────────────────────────────────────────────────────────

    @Test
    void getByTicket_existingTicket_returnsMappedComments() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(buildComment(10L, ticket, author), buildComment(11L, ticket, author)));

        List<CommentResponse> result = commentService.getByTicket(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CommentResponse::getId).containsExactly(10L, 11L);
        assertThat(result.get(0).getTicketId()).isEqualTo(1L);
        assertThat(result.get(0).getAuthorId()).isEqualTo(2L);
    }

    @Test
    void getByTicket_ticketNotFound_throws() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getByTicket(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(commentRepository, never()).findAllByTicket_IdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    void getByTicket_noComments_returnsEmptyList() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        List<CommentResponse> result = commentService.getByTicket(1L);

        assertThat(result).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validRequest_savesAndReturns() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(LocalDateTime.now());
            c.setVersion(0L);
            return c;
        });

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Hello world");
        request.setAuthorId(2L);

        CommentResponse response = commentService.create(1L, request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getContent()).isEqualTo("Hello world");
        assertThat(response.getTicketId()).isEqualTo(1L);
        assertThat(response.getAuthorId()).isEqualTo(2L);
        assertThat(response.getMentionedUsers()).isEmpty();
        verify(commentRepository).save(any());
        verify(auditLogService).log(eq("CREATE"), eq("COMMENT"), eq(10L), eq(ActorType.USER), eq("2"));
    }

    @Test
    void create_ticketNotFound_throws() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Hello");
        request.setAuthorId(1L);

        assertThatThrownBy(() -> commentService.create(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(commentRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void create_authorNotFound_throws() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Hello");
        request.setAuthorId(99L);

        assertThatThrownBy(() -> commentService.create(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(commentRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_existingComment_updatesContentAndLogs() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        Comment comment = buildComment(10L, ticket, author);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByIdAndTicket_Id(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Updated content");

        CommentResponse response = commentService.update(1L, 10L, request);

        assertThat(response.getContent()).isEqualTo("Updated content");
        assertThat(response.getId()).isEqualTo(10L);
        verify(commentRepository).save(argThat(c -> "Updated content".equals(c.getContent())));
        verify(auditLogService).log(eq("UPDATE"), eq("COMMENT"), eq(10L), eq(ActorType.USER), eq("2"));
    }

    @Test
    void update_ticketNotFound_throws() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("x");

        assertThatThrownBy(() -> commentService.update(99L, 10L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(commentRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void update_commentNotFoundOnTicket_throws() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByIdAndTicket_Id(anyLong(), anyLong())).thenReturn(Optional.empty());

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("x");

        assertThatThrownBy(() -> commentService.update(1L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(commentRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void update_optimisticLockConflict_propagatesException() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        Comment comment = buildComment(10L, ticket, author);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByIdAndTicket_Id(10L, 1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Comment.class, 10L));

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Concurrent edit");

        assertThatThrownBy(() -> commentService.update(1L, 10L, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingComment_deletesAndLogs() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        Comment comment = buildComment(10L, ticket, author);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByIdAndTicket_Id(10L, 1L)).thenReturn(Optional.of(comment));

        commentService.delete(1L, 10L);

        verify(commentRepository).delete(comment);
        verify(auditLogService).log(eq("DELETE"), eq("COMMENT"), eq(10L), eq(ActorType.USER), eq("2"));
    }

    @Test
    void delete_ticketNotFound_throws() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(commentRepository, never()).delete(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void delete_commentNotFoundOnTicket_throws() {
        Ticket ticket = buildTicket(1L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByIdAndTicket_Id(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(commentRepository, never()).delete(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    // ── @mention parsing — create ─────────────────────────────────────────────

    @Test
    void create_contentWithKnownMention_persistsMentionedUser() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        User mentioned = buildUser(3L);  // username = "user3"
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(userRepository.findByUsernamesLowercase(Set.of("user3"))).thenReturn(List.of(mentioned));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(LocalDateTime.now());
            c.setVersion(0L);
            return c;
        });

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Hey @user3 please review");
        request.setAuthorId(2L);

        CommentResponse response = commentService.create(1L, request);

        assertThat(response.getMentionedUsers()).hasSize(1);
        assertThat(response.getMentionedUsers().get(0).getId()).isEqualTo(3L);
        assertThat(response.getMentionedUsers().get(0).getUsername()).isEqualTo("user3");
    }

    @Test
    void create_contentWithUnknownMention_mentionedUsersIsEmpty() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(userRepository.findByUsernamesLowercase(Set.of("ghost"))).thenReturn(List.of());
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(LocalDateTime.now());
            c.setVersion(0L);
            return c;
        });

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Hey @ghost!");
        request.setAuthorId(2L);

        CommentResponse response = commentService.create(1L, request);

        assertThat(response.getMentionedUsers()).isEmpty();
    }

    @Test
    void create_mentionIsCaseInsensitive_matchesUser() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        User mentioned = buildUser(3L);  // username = "user3"
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        // Content uses uppercase; service lowercases before lookup
        when(userRepository.findByUsernamesLowercase(Set.of("user3"))).thenReturn(List.of(mentioned));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(LocalDateTime.now());
            c.setVersion(0L);
            return c;
        });

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Hey @USER3!");
        request.setAuthorId(2L);

        CommentResponse response = commentService.create(1L, request);

        assertThat(response.getMentionedUsers()).hasSize(1);
        assertThat(response.getMentionedUsers().get(0).getId()).isEqualTo(3L);
    }

    @Test
    void create_noMentions_doesNotQueryUsernames() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(LocalDateTime.now());
            c.setVersion(0L);
            return c;
        });

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("No mentions here");
        request.setAuthorId(2L);

        commentService.create(1L, request);

        verify(userRepository, never()).findByUsernamesLowercase(any());
    }

    // ── @mention parsing — update ─────────────────────────────────────────────

    @Test
    void update_addsMentionOnEdit() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        User mentioned = buildUser(3L);
        Comment comment = buildComment(10L, ticket, author); // no existing mentions
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByIdAndTicket_Id(10L, 1L)).thenReturn(Optional.of(comment));
        when(userRepository.findByUsernamesLowercase(Set.of("user3"))).thenReturn(List.of(mentioned));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Now mentioning @user3");

        CommentResponse response = commentService.update(1L, 10L, request);

        assertThat(response.getMentionedUsers()).hasSize(1);
        assertThat(response.getMentionedUsers().get(0).getId()).isEqualTo(3L);
    }

    @Test
    void update_removesMentionWhenNoLongerInContent() {
        Ticket ticket = buildTicket(1L);
        User author = buildUser(2L);
        User previouslyMentioned = buildUser(3L);
        Comment comment = buildComment(10L, ticket, author);
        comment.getMentionedUsers().add(previouslyMentioned); // pre-existing mention
        when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByIdAndTicket_Id(10L, 1L)).thenReturn(Optional.of(comment));
        // New content has no mentions → parseMentions returns empty (tokens empty, no DB call)
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("No mentions anymore");

        CommentResponse response = commentService.update(1L, 10L, request);

        assertThat(response.getMentionedUsers()).isEmpty();
        verify(userRepository, never()).findByUsernamesLowercase(any());
    }
}
