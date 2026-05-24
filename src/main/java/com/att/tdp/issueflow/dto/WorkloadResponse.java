package com.att.tdp.issueflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkloadResponse {
    private Long userId;
    private String username;
    private long openTicketCount;
}
