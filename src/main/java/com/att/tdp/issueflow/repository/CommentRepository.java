package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // All comments on a ticket, oldest first
    List<Comment> findAllByTicket_IdOrderByCreatedAtAsc(Long ticketId);

    // Lookup a single comment scoped to a specific ticket (validates ownership)
    Optional<Comment> findByIdAndTicket_Id(Long id, Long ticketId);

    // All comments mentioning a user — paginated, newest first (GET /users/:id/mentions)
    Page<Comment> findAllByMentionedUsers_Id(Long userId, Pageable pageable);
}
