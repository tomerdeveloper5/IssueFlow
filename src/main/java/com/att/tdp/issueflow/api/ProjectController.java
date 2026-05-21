package com.att.tdp.issueflow.api;

import com.att.tdp.issueflow.api.dto.DeletedProjectResponse;
import com.att.tdp.issueflow.api.dto.ProjectCreateRequest;
import com.att.tdp.issueflow.api.dto.ProjectResponse;
import com.att.tdp.issueflow.api.dto.ProjectUpdateRequest;
import com.att.tdp.issueflow.api.dto.WorkloadResponse;
import com.att.tdp.issueflow.service.ProjectService;
import com.att.tdp.issueflow.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;
    private final TicketService ticketService;

    @GetMapping
    public List<ProjectResponse> getProjects() {
        return projectService.getAll();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable Long projectId) {
        return projectService.getById(projectId);
    }

    @PostMapping
    public ProjectResponse createProject(@Valid @RequestBody ProjectCreateRequest request) {
        return projectService.create(request);
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<Void> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectUpdateRequest request
    ) {
        projectService.update(projectId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        projectService.softDelete(projectId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DeletedProjectResponse> getDeletedProjects() {
        return projectService.getDeleted();
    }

    @PostMapping("/{projectId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreProject(@PathVariable Long projectId) {
        projectService.restore(projectId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{projectId}/workload")
    public List<WorkloadResponse> getWorkload(@PathVariable Long projectId) {
        return ticketService.getProjectWorkload(projectId);
    }
}


