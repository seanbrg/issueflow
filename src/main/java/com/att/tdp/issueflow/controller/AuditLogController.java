package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.AuditLogResponse;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public List<AuditLogResponse> getAll(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) ActorType actor) {
        return auditLogService.getFiltered(entityType, entityId, action, actor);
    }
}
