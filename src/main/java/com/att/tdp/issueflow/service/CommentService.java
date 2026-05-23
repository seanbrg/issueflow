package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CommentResponse;
import com.att.tdp.issueflow.dto.CreateCommentRequest;
import com.att.tdp.issueflow.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public List<CommentResponse> getByTicket(Long ticketId) {
        findTicketOrThrow(ticketId);
        return commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    public CommentResponse create(Long ticketId, CreateCommentRequest request) {
        Ticket ticket = findTicketOrThrow(ticketId);
        User author = userRepository.findById(request.getAuthorId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getAuthorId()));

        Comment comment = Comment.builder()
                .ticket(ticket)
                .author(author)
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(comment);
        auditLogService.log("CREATE", "COMMENT", saved.getId(), ActorType.USER, String.valueOf(author.getId()));
        return CommentResponse.from(saved);
    }

    public CommentResponse update(Long ticketId, Long commentId, UpdateCommentRequest request) {
        Comment comment = findCommentForTicketOrThrow(commentId, ticketId);
        comment.setContent(request.getContent());
        Comment saved = commentRepository.save(comment);
        auditLogService.log("UPDATE", "COMMENT", commentId, ActorType.USER,
                String.valueOf(comment.getAuthor().getId()));
        return CommentResponse.from(saved);
    }

    public void delete(Long ticketId, Long commentId) {
        Comment comment = findCommentForTicketOrThrow(commentId, ticketId);
        String authorId = String.valueOf(comment.getAuthor().getId());
        commentRepository.delete(comment);
        auditLogService.log("DELETE", "COMMENT", commentId, ActorType.USER, authorId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Ticket findTicketOrThrow(Long ticketId) {
        return ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
    }

    private Comment findCommentForTicketOrThrow(Long commentId, Long ticketId) {
        findTicketOrThrow(ticketId);
        return commentRepository.findByIdAndTicket_Id(commentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comment not found: " + commentId + " on ticket: " + ticketId));
    }
}
