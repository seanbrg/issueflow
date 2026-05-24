package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.entity.AuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {

    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private Long performedBy;
    private String actor;
    private LocalDateTime timestamp;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .performedBy("SYSTEM".equals(log.getPerformedBy()) ? null : Long.parseLong(log.getPerformedBy()))
                .actor(log.getActor().name())
                .timestamp(log.getTimestamp())
                .build();
    }
}
