package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.entity.ActorType;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public List<ProjectResponse> getAll() {
        return projectRepository.findAllByDeletedAtIsNull().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public List<ProjectResponse> getDeleted() {
        return projectRepository.findAllByDeletedAtIsNotNull().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public ProjectResponse getById(Long id) {
        return ProjectResponse.from(findActiveOrThrow(id));
    }

    public ProjectResponse create(CreateProjectRequest request) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getOwnerId()));
        Project saved = projectRepository.save(Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build());
        auditLogService.log("CREATE", "PROJECT", saved.getId(), ActorType.USER, "SYSTEM");
        return ProjectResponse.from(saved);
    }

    public ProjectResponse update(Long id, UpdateProjectRequest request) {
        Project project = findActiveOrThrow(id);
        if (request.getName() != null) {
            project.setName(request.getName());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        ProjectResponse response = ProjectResponse.from(projectRepository.save(project));
        auditLogService.log("UPDATE", "PROJECT", id, ActorType.USER, "SYSTEM");
        return response;
    }

    public void softDelete(Long id) {
        Project project = findActiveOrThrow(id);
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
        auditLogService.log("DELETE", "PROJECT", id, ActorType.USER, "SYSTEM");
    }

    public ProjectResponse restore(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getDeletedAt() == null) {
            throw new ConflictException("Project is not deleted: " + id);
        }
        project.setDeletedAt(null);
        ProjectResponse response = ProjectResponse.from(projectRepository.save(project));
        auditLogService.log("RESTORE", "PROJECT", id, ActorType.USER, "SYSTEM");
        return response;
    }

    private Project findActiveOrThrow(Long id) {
        return projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }
}
