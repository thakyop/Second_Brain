
package com.Second_Brain.Second_Brain.workspace;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody
            WorkspaceRequests.CreateWorkspaceRequest request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return workspaceService.create(userId, request);
    }

    @GetMapping
    public List<WorkspaceResponse> list(
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());
        return workspaceService.listForUser(userId);
    }
}