package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.workspace.workspace;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One conversation: a user's questions and answers in one workspace.
 * Only that user can see it, even though others may share the workspace.
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatSession() {}

    public ChatSession(workspace workspace, User user) {
        this.workspace = workspace;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public workspace getWorkspace() {
        return workspace;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
