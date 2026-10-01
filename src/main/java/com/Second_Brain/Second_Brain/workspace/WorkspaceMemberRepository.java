
package com.Second_Brain.Second_Brain.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkspaceMemberRepository
        extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

    @Query("""
        select m.workspace
        from WorkspaceMember m
        where m.user.id = :userId
        order by m.workspace.createdAt desc
        """)
    List<workspace> findWorkspacesForUser(
            @Param("userId") UUID userId);
}