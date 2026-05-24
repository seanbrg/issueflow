package com.att.tdp.issueflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddDependencyRequest {

    @NotNull(message = "blockedBy is required")
    private Long blockedBy;
}
