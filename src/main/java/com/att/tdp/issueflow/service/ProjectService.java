package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.api.dto.ProjectCreateRequest;
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
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void update(Long projectId, ProjectUpdateRequest request) {
        Project project = getActiveEntity(projectId);
        project.setName(request.name().trim());
        project.setDescription(request.description().trim());
        projectRepository.save(project);
    }

    @Transactional
    public void softDelete(Long projectId) {
        Project project = getActiveEntity(projectId);
        project.setDeleted(true);
        projectRepository.save(project);
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


