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
class ProjectSoftDeleteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProjectRepository projectRepository;
    @Autowired UserRepository userRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminToken;
    private String devToken;
    private Project activeProject;
    private Project deletedProject;

    private static final String ADMIN_USERNAME = "proj_admin";
    private static final String ADMIN_PASSWORD = "adminpass";
    private static final String DEV_USERNAME = "proj_dev";
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
                .email("proj_admin@example.com")
                .fullName("Project Admin")
                .role(UserRole.ADMIN)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .build());

        userRepository.save(User.builder()
                .username(DEV_USERNAME)
                .email("proj_dev@example.com")
                .fullName("Project Dev")
                .role(UserRole.DEVELOPER)
                .password(passwordEncoder.encode(DEV_PASSWORD))
                .build());

        activeProject = projectRepository.save(Project.builder()
                .name("Active Project")
                .description("still around")
                .owner(admin)
                .build());

        deletedProject = projectRepository.save(Project.builder()
                .name("Deleted Project")
                .description("soft deleted")
                .owner(admin)
                .deletedAt(LocalDateTime.now())
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
        mockMvc.perform(delete("/projects/" + activeProject.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Project found = projectRepository.findById(activeProject.getId()).orElseThrow();
        assertThat(found.getDeletedAt()).isNotNull();
    }

    // ── GET /projects excludes soft-deleted ───────────────────────────────────

    @Test
    void getAll_excludesSoftDeleted() throws Exception {
        mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(activeProject.getId()));
    }

    // ── GET /projects/{id} excludes soft-deleted ──────────────────────────────

    @Test
    void getById_softDeletedProject_returns404() throws Exception {
        mockMvc.perform(get("/projects/" + deletedProject.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── GET /projects/deleted — ADMIN only ────────────────────────────────────

    @Test
    void getDeleted_asAdmin_returnsOnlySoftDeleted() throws Exception {
        mockMvc.perform(get("/projects/deleted")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deletedProject.getId()));
    }

    @Test
    void getDeleted_asDeveloper_returns403() throws Exception {
        mockMvc.perform(get("/projects/deleted")
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDeleted_noToken_returns401() throws Exception {
        mockMvc.perform(get("/projects/deleted"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /projects/{id}/restore — ADMIN only ──────────────────────────────

    @Test
    void restore_asAdmin_clearsDeletedAt() throws Exception {
        mockMvc.perform(post("/projects/" + deletedProject.getId() + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deletedProject.getId()));

        Project restored = projectRepository.findById(deletedProject.getId()).orElseThrow();
        assertThat(restored.getDeletedAt()).isNull();
    }

    @Test
    void restore_asDeveloper_returns403() throws Exception {
        mockMvc.perform(post("/projects/" + deletedProject.getId() + "/restore")
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void restore_noToken_returns401() throws Exception {
        mockMvc.perform(post("/projects/" + deletedProject.getId() + "/restore"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restore_activeProject_returns409() throws Exception {
        mockMvc.perform(post("/projects/" + activeProject.getId() + "/restore")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
