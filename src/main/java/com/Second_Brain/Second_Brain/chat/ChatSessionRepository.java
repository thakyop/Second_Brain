package com.Second_Brain.Second_Brain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository
        extends JpaRepository<ChatSession, UUID> {

    /** The session, only if it belongs to this user and this workspace. */
    Optional<ChatSession> findByIdAndWorkspaceIdAndUserId(
            UUID id, UUID workspaceId, UUID userId);

    /*
     * The user's chats in a workspace, newest first. There is no title
     * column, so each chat is described by its first question.
     */
    @Query("""
        select new com.Second_Brain.Second_Brain.chat.ChatSessionSummary(
            s.id, s.createdAt, m.content)
        from ChatMessage m
        join m.session s
        where s.workspace.id = :workspaceId
          and s.user.id = :userId
          and m.role = com.Second_Brain.Second_Brain.chat.ChatMessage.Role.USER
          and m.createdAt = (
              select min(first.createdAt)
              from ChatMessage first
              where first.session = s
                and first.role = com.Second_Brain.Second_Brain.chat.ChatMessage.Role.USER)
        order by s.createdAt desc
        """)
    List<ChatSessionSummary> findSummaries(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId);
}
