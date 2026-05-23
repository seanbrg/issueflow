package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentionedUserResponse {

    private Long id;
    private String username;
    private String fullName;

    public static MentionedUserResponse from(User user) {
        return MentionedUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .build();
    }
}
