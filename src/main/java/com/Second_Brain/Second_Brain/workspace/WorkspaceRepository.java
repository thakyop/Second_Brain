
package com.Second_Brain.Second_Brain.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkspaceRepository
        extends JpaRepository<workspace, UUID> {
}