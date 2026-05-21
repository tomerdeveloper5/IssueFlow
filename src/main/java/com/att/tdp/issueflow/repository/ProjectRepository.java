package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByDeletedFalse();

    List<Project> findByDeletedTrue();
}


