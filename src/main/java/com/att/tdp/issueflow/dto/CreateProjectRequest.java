package com.att.tdp.issueflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProjectRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Long ownerId;
}
