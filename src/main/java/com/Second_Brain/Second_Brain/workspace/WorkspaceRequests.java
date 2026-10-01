
package com.Second_Brain.Second_Brain.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class WorkspaceRequests {

    private WorkspaceRequests() {}

    public record CreateWorkspaceRequest(
            @NotBlank @Size(max = 150)
            String name
    ) {}
}