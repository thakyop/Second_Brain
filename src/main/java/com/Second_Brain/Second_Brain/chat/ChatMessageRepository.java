package com.Second_Brain.Second_Brain.chat;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /** The newest messages first; reverse them for the conversation order. */
    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(UUID sessionId, Limit limit);
}
