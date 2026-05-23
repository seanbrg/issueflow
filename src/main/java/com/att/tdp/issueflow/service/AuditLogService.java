package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String entityType, Long entityId, ActorType actor, String performedBy) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .performedBy(performedBy)
                .build());
    }
}
