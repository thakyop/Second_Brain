package com.Second_Brain.Second_Brain.chat;

import java.time.Instant;
import java.util.UUID;

/** A chat in the list. Its title is the start of its first question. */
public record ChatSessionResponse(
        UUID id,
        String title,
        Instant createdAt
) {

    private static final int TITLE_LENGTH = 80;

    public static ChatSessionResponse from(ChatSessionSummary summary) {
        String title = summary.firstQuestion().strip().replaceAll("\\s+", " ");
        if (title.length() > TITLE_LENGTH) {
            title = title.substring(0, TITLE_LENGTH).strip() + "…";
        }
        return new ChatSessionResponse(summary.id(), title, summary.createdAt());
    }
}
