package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.UserRole;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String fullName;
    private UserRole role;
    private String password; // optional — if present, will be BCrypt-encoded and saved
}
