package com.Second_Brain.Second_Brain.chat;

import java.time.Instant;
import java.util.UUID;

/** A chat in a list: its id, when it started and its first question. */
public record ChatSessionSummary(
        UUID id,
        Instant createdAt,
        String firstQuestion
) {
}
