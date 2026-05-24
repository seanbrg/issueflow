package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String token;

    private static final String USERNAME = "audituser";
    private static final String PASSWORD = "auditpass";

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username(USERNAME)
                .email("audit@example.com")
                .fullName("Audit User")
                .role(UserRole.ADMIN)
                .password(passwordEncoder.encode(PASSWORD))
                .build());
        token = obtainToken();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String obtainToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String extracted = body.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(extracted).isNotBlank().isNotEqualTo(body);
        return extracted;
    }

    private AuditLog saveLog(String action, String entityType, Long entityId,
                             ActorType actor, String performedBy) {
        return auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .actor(actor)
                .performedBy(performedBy)
                .build());
    }

    // ── auth guard ────────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_noToken_returns401() throws Exception {
        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    // ── no filters ────────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_noFilters_returnsAllLogs() throws Exception {
        saveLog("CREATE", "TICKET", 1L, ActorType.USER, "1");
        saveLog("UPDATE", "PROJECT", 2L, ActorType.USER, "1");
        saveLog("DELETE", "COMMENT", 3L, ActorType.SYSTEM, "SYSTEM");

        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getAuditLogs_noLogs_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── entityType filter ─────────────────────────────────────────────────────

    @Test
    void getAuditLogs_filterByEntityType_returnsOnlyMatching() throws Exception {
        saveLog("CREATE", "TICKET", 1L, ActorType.USER, "1");
        saveLog("CREATE", "PROJECT", 2L, ActorType.USER, "1");
        saveLog("UPDATE", "TICKET", 3L, ActorType.USER, "1");

        mockMvc.perform(get("/audit-logs?entityType=TICKET")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].entityType").value("TICKET"))
                .andExpect(jsonPath("$[1].entityType").value("TICKET"));
    }

    @Test
    void getAuditLogs_filterByEntityType_noMatch_returnsEmpty() throws Exception {
        saveLog("CREATE", "TICKET", 1L, ActorType.USER, "1");

        mockMvc.perform(get("/audit-logs?entityType=USER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── entityId filter ───────────────────────────────────────────────────────

    @Test
    void getAuditLogs_filterByEntityId_returnsOnlyMatching() throws Exception {
        saveLog("CREATE", "TICKET", 5L, ActorType.USER, "1");
        saveLog("UPDATE", "TICKET", 5L, ActorType.USER, "1");
        saveLog("CREATE", "TICKET", 99L, ActorType.USER, "1");

        mockMvc.perform(get("/audit-logs?entityId=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].entityId").value(5))
                .andExpect(jsonPath("$[1].entityId").value(5));
    }

    // ── action filter ─────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_filterByAction_returnsOnlyMatching() throws Exception {
        saveLog("CREATE", "TICKET", 1L, ActorType.USER, "1");
        saveLog("UPDATE", "TICKET", 1L, ActorType.USER, "1");
        saveLog("DELETE", "TICKET", 1L, ActorType.USER, "1");

        mockMvc.perform(get("/audit-logs?action=CREATE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("CREATE"));
    }

    // ── actor filter ──────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_filterByActor_returnsOnlyMatching() throws Exception {
        saveLog("CREATE", "TICKET", 1L, ActorType.USER, "1");
        saveLog("AUTO_ASSIGN", "TICKET", 1L, ActorType.SYSTEM, "SYSTEM");
        saveLog("UPDATE", "TICKET", 2L, ActorType.USER, "1");

        mockMvc.perform(get("/audit-logs?actor=SYSTEM")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].actor").value("SYSTEM"));
    }

    // ── combined filters ──────────────────────────────────────────────────────

    @Test
    void getAuditLogs_filterByEntityTypeAndAction_returnsIntersection() throws Exception {
        saveLog("CREATE", "TICKET", 1L, ActorType.USER, "1");
        saveLog("CREATE", "PROJECT", 2L, ActorType.USER, "1");
        saveLog("UPDATE", "TICKET", 3L, ActorType.USER, "1");

        mockMvc.perform(get("/audit-logs?entityType=TICKET&action=CREATE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityType").value("TICKET"))
                .andExpect(jsonPath("$[0].action").value("CREATE"));
    }

    @Test
    void getAuditLogs_allFilters_returnsOnlyExactMatch() throws Exception {
        saveLog("CREATE", "TICKET", 7L, ActorType.USER, "3");
        saveLog("CREATE", "TICKET", 7L, ActorType.SYSTEM, "SYSTEM");
        saveLog("CREATE", "PROJECT", 7L, ActorType.USER, "3");

        mockMvc.perform(get("/audit-logs?entityType=TICKET&entityId=7&action=CREATE&actor=USER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityType").value("TICKET"))
                .andExpect(jsonPath("$[0].entityId").value(7))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].actor").value("USER"));
    }

    // ── response shape ────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_responseShape_hasAllSpecFields() throws Exception {
        saveLog("CREATE", "TICKET", 5L, ActorType.USER, "2");

        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].entityType").value("TICKET"))
                .andExpect(jsonPath("$[0].entityId").value(5))
                .andExpect(jsonPath("$[0].performedBy").value(2))
                .andExpect(jsonPath("$[0].actor").value("USER"))
                .andExpect(jsonPath("$[0].timestamp").isString())
                .andExpect(jsonPath("$[0].timestamp").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")));
    }

    // ── invalid input ─────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_invalidActorValue_returns400() throws Exception {
        mockMvc.perform(get("/audit-logs?actor=BOGUS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
