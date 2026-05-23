package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private LocalDateTime createdAt;

    public static UserProfileResponse from(User user) {
        UserProfileResponse r = new UserProfileResponse();
        r.id = user.getId();
        r.username = user.getUsername();
        r.email = user.getEmail();
        r.fullName = user.getFullName();
        r.role = user.getRole().name();
        r.createdAt = user.getCreatedAt();
        return r;
    }
}
