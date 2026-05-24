package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AuditLogResponse;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;
    @InjectMocks AuditLogService auditLogService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AuditLog buildLog(Long id, String action, String entityType, Long entityId,
                              ActorType actor, String performedBy) {
        return AuditLog.builder()
                .id(id)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .performedBy(performedBy)
                .timestamp(LocalDateTime.of(2024, 3, 15, 10, 0))
                .build();
    }

    private void setAuthenticatedUser(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList()));
    }

    // ── log (5-arg) ───────────────────────────────────────────────────────────

    @Test
    void log_fiveArgs_savesEntityWithAllFields() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.log("CREATE", "TICKET", 5L, ActorType.USER, "2");

        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getEntityType()).isEqualTo("TICKET");
        assertThat(saved.getEntityId()).isEqualTo(5L);
        assertThat(saved.getActor()).isEqualTo(ActorType.USER);
        assertThat(saved.getPerformedBy()).isEqualTo("2");
    }

    @Test
    void log_fiveArgs_systemActor_savesSystemPerformer() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.log("AUTO_ASSIGN", "TICKET", 3L, ActorType.SYSTEM, "SYSTEM");

        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo(ActorType.SYSTEM);
        assertThat(saved.getPerformedBy()).isEqualTo("SYSTEM");
    }

    // ── log (4-arg) — currentPerformer resolution ─────────────────────────────

    @Test
    void log_fourArgs_withAuthenticatedUser_resolvesPerformerToUserId() {
        User user = User.builder().id(7L).username("jdoe").email("jdoe@x.com")
                .fullName("John Doe").role(UserRole.DEVELOPER).password("h")
                .createdAt(LocalDateTime.now()).build();
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        setAuthenticatedUser("jdoe");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.log("UPDATE", "PROJECT", 1L, ActorType.USER);

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("7");
        assertThat(captor.getValue().getActor()).isEqualTo(ActorType.USER);
    }

    @Test
    void log_fourArgs_withNoAuthentication_fallsBackToSystem() {
        SecurityContextHolder.clearContext();

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.log("DELETE", "USER", 9L, ActorType.USER);

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("SYSTEM");
    }

    @Test
    void log_fourArgs_withUnknownUsername_fallsBackToSystem() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        setAuthenticatedUser("ghost");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.log("CREATE", "COMMENT", 2L, ActorType.USER);

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("SYSTEM");
    }

    @Test
    void log_fourArgs_anonymousPrincipal_fallsBackToSystem() {
        // SecurityContextHolder has an anonymous auth token (set by Spring Security's default)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList()));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditLogService.log("CREATE", "PROJECT", 4L, ActorType.USER);

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("SYSTEM");
    }

    // ── getFiltered ───────────────────────────────────────────────────────────

    @Test
    void getFiltered_noFilters_returnsMappedResponses() {
        AuditLog log1 = buildLog(1L, "CREATE", "TICKET", 5L, ActorType.USER, "2");
        AuditLog log2 = buildLog(2L, "DELETE", "PROJECT", 3L, ActorType.USER, "1");
        when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>any()))
                .thenReturn(List.of(log1, log2));

        List<AuditLogResponse> result = auditLogService.getFiltered(null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAction()).isEqualTo("CREATE");
        assertThat(result.get(1).getAction()).isEqualTo("DELETE");
    }

    @Test
    void getFiltered_repositoryEmpty_returnsEmptyList() {
        when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>any()))
                .thenReturn(List.of());

        List<AuditLogResponse> result = auditLogService.getFiltered("TICKET", 5L, "CREATE", ActorType.USER);

        assertThat(result).isEmpty();
        verify(auditLogRepository).findAll(ArgumentMatchers.<Specification<AuditLog>>any());
    }

    @Test
    void getFiltered_responseShape_mapsAllFields() {
        LocalDateTime ts = LocalDateTime.of(2024, 3, 15, 10, 0);
        AuditLog log = AuditLog.builder()
                .id(1L).action("CREATE").entityType("TICKET").entityId(5L)
                .actor(ActorType.USER).performedBy("2").timestamp(ts)
                .build();
        when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>any()))
                .thenReturn(List.of(log));

        AuditLogResponse response = auditLogService.getFiltered(null, null, null, null).get(0);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getAction()).isEqualTo("CREATE");
        assertThat(response.getEntityType()).isEqualTo("TICKET");
        assertThat(response.getEntityId()).isEqualTo(5L);
        assertThat(response.getActor()).isEqualTo("USER");
        assertThat(response.getPerformedBy()).isEqualTo(2L);
        assertThat(response.getTimestamp()).isEqualTo(ts);
    }

    @Test
    void getFiltered_withFilters_callsRepositoryWithSpecification() {
        when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AuditLog>>any()))
                .thenReturn(List.of());

        auditLogService.getFiltered("PROJECT", 10L, "UPDATE", ActorType.SYSTEM);

        verify(auditLogRepository).findAll(ArgumentMatchers.<Specification<AuditLog>>any());
    }
}
