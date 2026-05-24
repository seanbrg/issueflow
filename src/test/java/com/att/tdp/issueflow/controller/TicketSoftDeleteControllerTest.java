package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TicketSoftDeleteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired TicketRepository ticketRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired UserRepository userRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminToken;
    private String devToken;
    private Project project;
    private Ticket activeTicket;
    private Ticket deletedTicket;

    private static final String ADMIN_USERNAME = "ticket_admin";
    private static final String ADMIN_PASSWORD = "adminpass";
    private static final String DEV_USERNAME = "ticket_dev";
    private static final String DEV_PASSWORD = "devpass";

    @BeforeEach
    void setUp() throws Exception {
        commentRepository.deleteAll();
        auditLogRepository.deleteAll();
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder()
                .username(ADMIN_USERNAME)
                .email("ticket_admin@example.com")
                .fullName("Ticket Admin")
                .role(UserRole.ADMIN)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .build());

        userRepository.save(User.builder()
                .username(DEV_USERNAME)
                .email("ticket_dev@example.com")
                .fullName("Ticket Dev")
                .role(UserRole.DEVELOPER)
                .password(passwordEncoder.encode(DEV_PASSWORD))
                .build());

        project = projectRepository.save(Project.builder()
                .name("Test Project")
                .description("desc")
                .owner(admin)
                .build());

        activeTicket = ticketRepository.save(Ticket.builder()
                .title("Active Ticket")
                .description("still around")
                .status(TicketStatus.TODO)
                .priority(TicketPriority.LOW)
                .type(TicketType.BUG)
                .project(project)
                .overdue(false)
                .build());

        deletedTicket = ticketRepository.save(Ticket.builder()
                .title("Deleted Ticket")
                .description("soft deleted")
                .status(TicketStatus.TODO)
                .priority(TicketPriority.LOW)
                .type(TicketType.BUG)
                .project(project)
                .deletedAt(LocalDateTime.now())
                .overdue(false)
                .build());

        adminToken = obtainToken(ADMIN_USERNAME, ADMIN_PASSWORD);
        devToken = obtainToken(DEV_USERNAME, DEV_PASSWORD);
    }

    private String obtainToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String extracted = body.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(extracted).isNotBlank().isNotEqualTo(body);
        return extracted;
    }

    // ── DELETE sets deletedAt, not physical deletion ──────────────────────────

    @Test
    void delete_setsDeletedAt_notPhysicalDeletion() throws Exception {
        mockMvc.perform(delete("/tickets/" + activeTicket.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Ticket found = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertThat(found.getDeletedAt()).isNotNull();
    }

    // ── GET /tickets excludes soft-deleted ────────────────────────────────────

    @Test
    void getByProject_excludesSoftDeleted() throws Exception {
        mockMvc.perform(get("/tickets?projectId=" + project.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(activeTicket.getId()));
    }

    // ── GET /tickets/{id} excludes soft-deleted ───────────────────────────────

    @Test
    void getById_softDeletedTicket_returns404() throws Exception {
        mockMvc.perform(get("/tickets/" + deletedTicket.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── GET /tickets/deleted — ADMIN only ─────────────────────────────────────

    @Test
    void getDeleted_asAdmin_returnsOnlySoftDeleted() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=" + project.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deletedTicket.getId()));
    }

    @Test
    void getDeleted_asDeveloper_returns403() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=" + project.getId())
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDeleted_noToken_returns401() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=" + project.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /tickets/{id}/restore — ADMIN only ───────────────────────────────

    @Test
    void restore_asAdmin_clearsDeletedAt() throws Exception {
        mockMvc.perform(post("/tickets/" + deletedTicket.getId() + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deletedTicket.getId()));

        Ticket restored = ticketRepository.findById(deletedTicket.getId()).orElseThrow();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void restore_asDeveloper_returns403() throws Exception {
        mockMvc.perform(post("/tickets/" + deletedTicket.getId() + "/restore")
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void restore_noToken_returns401() throws Exception {
        mockMvc.perform(post("/tickets/" + deletedTicket.getId() + "/restore"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restore_activeTicket_returns409() throws Exception {
        mockMvc.perform(post("/tickets/" + activeTicket.getId() + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
