package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.Comment;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CommentResponse {

    private Long id;
    private Long ticketId;
    private Long authorId;
    private String content;
    private List<MentionedUserResponse> mentionedUsers;

    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .ticketId(comment.getTicket().getId())
                .authorId(comment.getAuthor().getId())
                .content(comment.getContent())
                .mentionedUsers(comment.getMentionedUsers().stream()
                        .map(MentionedUserResponse::from)
                        .toList())
                .build();
    }
}
