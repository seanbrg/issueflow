package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.Ticket;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TicketResponse {

    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String type;
    private Long projectId;
    private Long assigneeId;
    private LocalDate dueDate;
    @JsonProperty("isOverdue")
    private boolean overdue;
    private LocalDateTime createdAt;
    private Long version;

    public static TicketResponse from(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus().name())
                .priority(ticket.getPriority().name())
                .type(ticket.getType().name())
                .projectId(ticket.getProject().getId())
                .assigneeId(ticket.getAssignee() != null ? ticket.getAssignee().getId() : null)
                .dueDate(ticket.getDueDate())
                .overdue(ticket.isOverdue())
                .createdAt(ticket.getCreatedAt())
                .version(ticket.getVersion())
                .build();
    }
}
