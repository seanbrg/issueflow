package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.CommentResponse;
import com.att.tdp.issueflow.dto.CreateCommentRequest;
import com.att.tdp.issueflow.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getByTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(commentService.getByTicket(ticketId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(@PathVariable Long ticketId,
                                                  @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(commentService.create(ticketId, request));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> update(@PathVariable Long ticketId,
                                                  @PathVariable Long commentId,
                                                  @Valid @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(commentService.update(ticketId, commentId, request));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long ticketId,
                                       @PathVariable Long commentId) {
        commentService.delete(ticketId, commentId);
        return ResponseEntity.ok().build();
    }
}
