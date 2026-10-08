package com.Second_Brain.Second_Brain.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID workspaceId,
        UUID uploadedBy,
        String filename,
        String contentType,
        long sizeBytes,
        String status,
        Instant createdAt
) {

    public static DocumentResponse from(Document document) {

        return new DocumentResponse(
                document.getId(),
                document.getWorkspace().getId(),
                document.getUploadedBy().getId(),
                document.getFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getCreatedAt()
        );
    }
}