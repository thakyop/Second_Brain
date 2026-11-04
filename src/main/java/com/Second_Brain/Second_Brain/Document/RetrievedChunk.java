package com.Second_Brain.Second_Brain.Document;

import java.util.UUID;

/**
 * A chunk found by a similarity search, with its document's name and its
 * cosine distance from the question: 0 means the same meaning, larger
 * values mean less related.
 */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String filename,
        int chunkIndex,
        String content,
        Double distance
) {
}
