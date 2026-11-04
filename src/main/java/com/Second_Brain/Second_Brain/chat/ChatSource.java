package com.Second_Brain.Second_Brain.chat;

import java.util.UUID;

/**
 * One passage an answer was based on. `number` is the [n] the answer uses to
 * cite it. Stored in chat_messages.sources and returned to the client as is.
 */
public record ChatSource(
        int number,
        UUID documentId,
        String filename,
        int chunkIndex,
        String excerpt
) {
}
