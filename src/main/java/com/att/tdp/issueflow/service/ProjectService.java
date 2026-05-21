package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.ProjectCreateRequest;
import com.att.tdp.issueflow.api.dto.DeletedProjectResponse;
import com.att.tdp.issueflow.api.dto.ProjectResponse;
import com.att.tdp.issueflow.api.dto.ProjectUpdateRequest;
import com.att.tdp.issueflow.domain.Project;
import com.att.tdp.issueflow.exception.NotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAll() {
        return projectRepository.findByDeletedFalse().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long projectId) {
        return toResponse(getActiveEntity(projectId));
    }

    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description().trim());
        project.setOwner(userService.getEntity(request.ownerId()));
        project.setDeleted(false);
        Project saved = projectRepository.save(project);
        auditLogService.logUserAction("CREATE", "PROJECT", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void update(Long projectId, ProjectUpdateRequest request) {
        Project project = getActiveEntity(projectId);
        project.setName(request.name().trim());
        project.setDescription(request.description().trim());
        projectRepository.save(project);
        auditLogService.logUserAction("UPDATE", "PROJECT", projectId);
    }

    @Transactional
    public void softDelete(Long projectId) {
        Project project = getActiveEntity(projectId);
        project.setDeleted(true);
        projectRepository.save(project);
        auditLogService.logUserAction("DELETE", "PROJECT", projectId);
    }

    @Transactional(readOnly = true)
    public List<DeletedProjectResponse> getDeleted() {
        return projectRepository.findByDeletedTrue().stream()
                .map(project -> new DeletedProjectResponse(
                        project.getId(),
                        project.getName(),
                        project.getDescription(),
                        project.getOwner().getId()
                ))
                .toList();
    }

    @Transactional
    public void restore(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        if (!project.isDeleted()) {
            throw new NotFoundException("Project not found: " + projectId);
        }
        project.setDeleted(false);
        projectRepository.save(project);
        auditLogService.logUserAction("RESTORE", "PROJECT", projectId);
    }

    @Transactional(readOnly = true)
    public Project getActiveEntity(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
        if (project.isDeleted()) {
            throw new NotFoundException("Project not found: " + id);
        }
        return project;
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId()
        );
    }
}


