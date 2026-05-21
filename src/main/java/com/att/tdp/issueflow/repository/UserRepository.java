package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.domain.User;
import com.att.tdp.issueflow.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);

    List<User> findByRoleOrderByCreatedAtAsc(UserRole role);

    @Query("""
            select distinct u
            from User u
            where u.id = (
                select p.owner.id
                from Project p
                where p.id = :projectId
            )
            or u.id in (
                select distinct t.assignee.id
                from Ticket t
                where t.project.id = :projectId
                  and t.deleted = false
                  and t.assignee is not null
            )
            """)
    List<User> findLinkedUsersByProjectId(@Param("projectId") Long projectId);
}


