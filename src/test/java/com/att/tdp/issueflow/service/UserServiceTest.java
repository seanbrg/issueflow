package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditLogService auditLogService;
    @InjectMocks UserService userService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .fullName("Full Name")
                .role(role)
                .password("hashed")
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedList() {
        User alice = buildUser(1L, "alice", UserRole.ADMIN);
        User bob   = buildUser(2L, "bob",   UserRole.DEVELOPER);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));

        List<UserResponse> result = userService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
        assertThat(result.get(1).getUsername()).isEqualTo("bob");
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_existingId_returnsResponse() {
        User user = buildUser(1L, "alice", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void getById_missingId_throwsResourceNotFoundException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_withoutPassword_savesUserWithNullPassword() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            u.setCreatedAt(LocalDateTime.now());
            return u;
        });

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@example.com");
        request.setFullName("New User");
        request.setRole(UserRole.DEVELOPER);
        // no password

        UserResponse response = userService.create(request);

        assertThat(response.getUsername()).isEqualTo("newuser");
        assertThat(response.getRole()).isEqualTo("DEVELOPER");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository).save(any());
    }

    @Test
    void create_withPassword_encodesAndSaves() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            u.setCreatedAt(LocalDateTime.now());
            return u;
        });

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@example.com");
        request.setFullName("New User");
        request.setRole(UserRole.DEVELOPER);
        request.setPassword("pass");

        userService.create(request);

        verify(passwordEncoder).encode("pass");
    }

    @Test
    void create_duplicateUsername_throwsConflictException() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("taken");
        request.setEmail("other@example.com");
        request.setFullName("Someone");
        request.setRole(UserRole.DEVELOPER);
        request.setPassword("pass");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_duplicateEmail_throwsConflictException() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("uniqueuser");
        request.setEmail("dup@example.com");
        request.setFullName("Someone");
        request.setRole(UserRole.DEVELOPER);
        request.setPassword("pass");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("dup@example.com");

        verify(userRepository, never()).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_bothFields_updatesUser() {
        User user = buildUser(1L, "alice", UserRole.DEVELOPER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("Alice Updated");
        request.setRole(UserRole.ADMIN);

        UserResponse response = userService.update(1L, request);

        assertThat(response.getFullName()).isEqualTo("Alice Updated");
        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void update_nullFields_leavesUserUnchanged() {
        User user = buildUser(1L, "alice", UserRole.DEVELOPER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Both fields null — nothing should change
        UpdateUserRequest request = new UpdateUserRequest();

        UserResponse response = userService.update(1L, request);

        assertThat(response.getFullName()).isEqualTo("Full Name");
        assertThat(response.getRole()).isEqualTo("DEVELOPER");
    }

    @Test
    void update_withPassword_encodesAndPersistsNewPassword() {
        User user = buildUser(1L, "alice", UserRole.DEVELOPER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("newhashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("newpass");

        userService.update(1L, request);

        verify(passwordEncoder).encode("newpass");
        // Verify the entity passed to save has the encoded password
        verify(userRepository).save(argThat(u -> "newhashed".equals(u.getPassword())));
    }

    @Test
    void update_missingId_throwsResourceNotFoundException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, new UpdateUserRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository, never()).save(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingId_callsDeleteById() {
        User user = buildUser(1L, "alice", UserRole.DEVELOPER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void delete_missingId_throwsResourceNotFoundException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(userRepository, never()).deleteById(anyLong());
    }
}
