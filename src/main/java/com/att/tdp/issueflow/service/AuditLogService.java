package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AuditLogResponse;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void log(String action, String entityType, Long entityId, ActorType actor, String performedBy) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .performedBy(performedBy)
                .build());
    }

    /** Overload that auto-resolves the current authenticated user as performer. */
    public void log(String action, String entityType, Long entityId, ActorType actor) {
        log(action, entityType, entityId, actor, currentPerformer());
    }

    public List<AuditLogResponse> getFiltered(String entityType, Long entityId, String action, ActorType actor) {
        Specification<AuditLog> spec = Specification.where(null);
        if (entityType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (actor != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actor"), actor));
        }
        return auditLogRepository.findAll(spec).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private String currentPerformer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "SYSTEM";
        }
        return userRepository.findByUsername(auth.getName())
                .map(u -> String.valueOf(u.getId()))
                .orElse("SYSTEM");
    }
}
