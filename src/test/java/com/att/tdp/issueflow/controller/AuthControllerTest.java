package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
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
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String USERNAME = "jdoe";
    private static final String RAW_PASSWORD = "s3cr3t!";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username(USERNAME)
                .email("jdoe@example.com")
                .fullName("John Doe")
                .role(UserRole.DEVELOPER)
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .build());
    }

    // ── 1. Valid credentials → 200 + token ───────────────────────────────────

    @Test
    void loginWithValidCredentials_returns200AndToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdoe","password":"s3cr3t!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    // ── 2. Wrong password → 401 ───────────────────────────────────────────────

    @Test
    void loginWithWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdoe","password":"wrongpassword"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── 3. Unknown username → 401 ─────────────────────────────────────────────

    @Test
    void loginWithUnknownUsername_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","password":"whatever"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── 4. Valid JWT → GET /auth/me returns 200 + profile ────────────────────

    @Test
    void meWithValidToken_returns200AndProfile() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value("jdoe@example.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    // ── 5. No token → GET /auth/me returns 401 ───────────────────────────────

    @Test
    void meWithNoToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── 6. Malformed token → GET /auth/me returns 401 ────────────────────────

    @Test
    void meWithMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ── 7. Logout denies the token; subsequent /me returns 401 ───────────────

    @Test
    void afterLogout_sameTokenIsRejected() throws Exception {
        String token = obtainToken();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String obtainToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"jdoe","password":"s3cr3t!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Extract accessToken from {"accessToken":"<value>",...}
        String token = body.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(token).isNotBlank().isNotEqualTo(body);
        return token;
    }
}
