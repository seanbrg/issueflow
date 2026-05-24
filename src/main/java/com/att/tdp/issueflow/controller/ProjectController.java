package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.WorkloadResponse;
import com.att.tdp.issueflow.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAll() {
        return ResponseEntity.ok(projectService.getAll());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/deleted")
    public ResponseEntity<List<ProjectResponse>> getDeleted() {
        return ResponseEntity.ok(projectService.getDeleted());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(projectService.create(request));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getById(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getById(projectId));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(@PathVariable Long projectId,
                                                  @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.update(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> softDelete(@PathVariable Long projectId) {
        projectService.softDelete(projectId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{projectId}/restore")
    public ResponseEntity<ProjectResponse> restore(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.restore(projectId));
    }

    @GetMapping("/{projectId}/workload")
    public ResponseEntity<List<WorkloadResponse>> getWorkload(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getWorkload(projectId));
    }
}
