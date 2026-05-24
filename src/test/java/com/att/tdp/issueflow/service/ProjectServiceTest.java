package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.entity.UserRole;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks ProjectService projectService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildOwner(Long id) {
        return User.builder()
                .id(id)
                .username("owner" + id)
                .email("owner" + id + "@example.com")
                .fullName("Owner " + id)
                .role(UserRole.ADMIN)
                .password("hashed")
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }

    private Project buildProject(Long id, String name, User owner) {
        return Project.builder()
                .id(id)
                .name(name)
                .description("A description")
                .owner(owner)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsOnlyActiveProjects() {
        User owner = buildOwner(1L);
        when(projectRepository.findAllByDeletedAtIsNull())
                .thenReturn(List.of(buildProject(1L, "Alpha", owner), buildProject(2L, "Beta", owner)));

        List<ProjectResponse> result = projectService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProjectResponse::getName).containsExactly("Alpha", "Beta");
    }

    // ── getDeleted ────────────────────────────────────────────────────────────

    @Test
    void getDeleted_returnsOnlyDeletedProjects() {
        User owner = buildOwner(1L);
        Project deleted = buildProject(3L, "Archived", owner);
        deleted.setDeletedAt(LocalDateTime.of(2024, 6, 1, 0, 0));
        when(projectRepository.findAllByDeletedAtIsNotNull()).thenReturn(List.of(deleted));

        List<ProjectResponse> result = projectService.getDeleted();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Archived");
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_existingProject_returnsResponse() {
        User owner = buildOwner(1L);
        when(projectRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(buildProject(10L, "Alpha", owner)));

        ProjectResponse response = projectService.getById(10L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Alpha");
        assertThat(response.getOwnerId()).isEqualTo(1L);
    }

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        when(projectRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validOwner_savesAndLogsAndReturnsResponse() {
        User owner = buildOwner(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(projectRepository.save(any())).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(5L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("New Project");
        request.setDescription("Desc");
        request.setOwnerId(1L);

        ProjectResponse response = projectService.create(request);

        assertThat(response.getName()).isEqualTo("New Project");
        assertThat(response.getOwnerId()).isEqualTo(1L);
        verify(projectRepository).save(any());
        verify(auditLogService).log(eq("CREATE"), eq("PROJECT"), eq(5L), any(ActorType.class));
    }

    @Test
    void create_unknownOwnerId_throwsResourceNotFoundException() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("New Project");
        request.setOwnerId(99L);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(projectRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_bothFields_updatesAndLogs() {
        User owner = buildOwner(1L);
        when(projectRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildProject(1L, "Old Name", owner)));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("New Name");
        request.setDescription("New Desc");

        ProjectResponse response = projectService.update(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getDescription()).isEqualTo("New Desc");
        verify(auditLogService).log(eq("UPDATE"), eq("PROJECT"), eq(1L), any(ActorType.class));
    }

    @Test
    void update_nullFields_leavesProjectUnchanged() {
        User owner = buildOwner(1L);
        when(projectRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(buildProject(1L, "Old Name", owner)));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.update(1L, new UpdateProjectRequest());

        assertThat(response.getName()).isEqualTo("Old Name");
        assertThat(response.getDescription()).isEqualTo("A description");
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        when(projectRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.update(99L, new UpdateProjectRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(projectRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    // ── softDelete ────────────────────────────────────────────────────────────

    @Test
    void softDelete_setsDeletedAtAndSavesAndLogs() {
        User owner = buildOwner(1L);
        Project project = buildProject(1L, "Alpha", owner);
        when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.softDelete(1L);

        verify(projectRepository, never()).deleteById(anyLong());
        verify(projectRepository).save(argThat(p -> p.getDeletedAt() != null));
        verify(auditLogService).log(eq("DELETE"), eq("PROJECT"), eq(1L), any(ActorType.class));
    }

    @Test
    void softDelete_notFound_throwsResourceNotFoundException() {
        when(projectRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.softDelete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(projectRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Test
    void restore_clearsDeletedAtAndSavesAndLogs() {
        User owner = buildOwner(1L);
        Project project = buildProject(1L, "Alpha", owner);
        project.setDeletedAt(LocalDateTime.of(2024, 6, 1, 0, 0));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.restore(1L);

        assertThat(response.getName()).isEqualTo("Alpha");
        verify(projectRepository).save(argThat(p -> p.getDeletedAt() == null));
        verify(auditLogService).log(eq("RESTORE"), eq("PROJECT"), eq(1L), any(ActorType.class));
    }

    @Test
    void restore_notFound_throwsResourceNotFoundException() {
        when(projectRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.restore(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(projectRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void restore_projectNotDeleted_throwsConflictException() {
        User owner = buildOwner(1L);
        Project project = buildProject(1L, "Alpha", owner); // deletedAt is null — already active
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.restore(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not deleted");

        verify(projectRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }
}
