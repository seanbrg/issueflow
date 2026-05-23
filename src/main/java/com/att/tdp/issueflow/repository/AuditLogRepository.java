package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaSpecificationExecutor enables dynamic multi-param filtering for GET /audit-logs
// (entityType, entityId, action, actor are all optional and combinable)
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {
}
