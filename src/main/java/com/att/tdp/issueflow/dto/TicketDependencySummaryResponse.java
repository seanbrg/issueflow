package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.Ticket;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketDependencySummaryResponse {

    private Long id;
    private String title;
    private String status;

    public static TicketDependencySummaryResponse from(Ticket ticket) {
        return TicketDependencySummaryResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .status(ticket.getStatus().name())
                .build();
    }
}
