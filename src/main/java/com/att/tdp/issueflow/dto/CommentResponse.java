package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.Comment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CommentResponse {

    private Long id;
    private Long ticketId;
    private Long authorId;
    private String content;
    private List<MentionedUserResponse> mentionedUsers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static CommentResponse from(Comment comment) {
        List<MentionedUserResponse> mentions = comment.getMentionedUsers().stream()
                .map(MentionedUserResponse::from)
                .toList();

        return CommentResponse.builder()
                .id(comment.getId())
                .ticketId(comment.getTicket().getId())
                .authorId(comment.getAuthor().getId())
                .content(comment.getContent())
                .mentionedUsers(mentions)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .version(comment.getVersion())
                .build();
    }
}
