package com.Second_Brain.Second_Brain.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        List<ChatSource> sources,
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getSources(),
                message.getCreatedAt());
    }
}
