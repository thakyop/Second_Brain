
package com.Second_Brain.Second_Brain.workspace;

import com.Second_Brain.Second_Brain.User.User;
import jakarta.persistence.*;

@Entity
@Table(name = "workspace_members")
public class WorkspaceMember {

    public enum Role {
        OWNER, MEMBER
    }

    @EmbeddedId
    private WorkspaceMemberId id;

    @MapsId("workspaceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private workspace workspace;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected WorkspaceMember() {}

    public WorkspaceMember(
            workspace workspace, User user, Role role) {
        this.id = new WorkspaceMemberId(workspace.getId(), user.getId());
        this.workspace = workspace;
        this.user = user;
        this.role = role;
    }

    public WorkspaceMemberId getId() {
        return id;
    }

    public workspace getWorkspace() {
        return workspace;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }
}