
package com.Second_Brain.Second_Brain.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        UUID ownerId,
        Instant createdAt,
        String role
) {
    public static WorkspaceResponse from(
            workspace workspace, String role) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getOwner().getId(),
                workspace.getCreatedAt(),
                role
        );
    }
}