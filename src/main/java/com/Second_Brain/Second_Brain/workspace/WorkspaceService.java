
package com.Second_Brain.Second_Brain.workspace;

import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.User.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public WorkspaceResponse create(
            UUID currentUserId,
            WorkspaceRequests.CreateWorkspaceRequest request) {

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        workspace workspace = workspaceRepository.save(
                new workspace(request.name().trim(), owner));

        memberRepository.save(new WorkspaceMember(
                workspace, owner, WorkspaceMember.Role.OWNER));

        return WorkspaceResponse.from(workspace, "OWNER");
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listForUser(UUID currentUserId) {
        return memberRepository.findWorkspacesForUser(currentUserId)
                .stream()
                .map(workspace -> WorkspaceResponse.from(
                        workspace,
                        workspace.getOwner().getId().equals(currentUserId)
                                ? "OWNER" : "MEMBER"))
                .toList();
    }
}