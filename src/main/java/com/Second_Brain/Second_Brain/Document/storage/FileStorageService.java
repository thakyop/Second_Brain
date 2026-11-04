package com.Second_Brain.Second_Brain.Document.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public interface FileStorageService {

    String store(
            UUID workspaceId,
            UUID documentId,
            MultipartFile file
    ) throws IOException;

    /**
     * Turns a storage path returned by {@link #store} (relative to the
     * storage root) back into the file's actual location.
     */
    Path resolve(String storagePath);

    void delete(String storagePath) throws IOException;
}