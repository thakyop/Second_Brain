package com.Second_Brain.Second_Brain.Document.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface FileStorageService {

    String store(
            UUID workspaceId,
            UUID documentId,
            MultipartFile file
    ) throws IOException;

    void delete(String storagePath) throws IOException;
}