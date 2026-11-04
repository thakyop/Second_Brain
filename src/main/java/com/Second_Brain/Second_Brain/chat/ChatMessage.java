package com.Second_Brain.Second_Brain.chat;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A question (USER) or an answer (ASSISTANT) in a chat session. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public enum Role {
        USER,
        ASSISTANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /*
     * The passages an answer was based on, stored in the JSONB column.
     * Hibernate converts the list to and from JSON (with Jackson).
     * Always empty for questions.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ChatSource> sources;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {}

    /**
     * `createdAt` is passed in: a question is stamped when it was asked and
     * its answer when it was ready, so they always sort in the right order.
     */
    public ChatMessage(ChatSession session, Role role, String content, List<ChatSource> sources, Instant createdAt) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.sources = List.copyOf(sources);
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public ChatSession getSession() {
        return session;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<ChatSource> getSources() {
        return sources;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
