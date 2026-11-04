package com.Second_Brain.Second_Brain.Document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path storageRoot;

    public LocalFileStorageService(
            @Value("${app.storage.root:./storage}") String storageRoot
    ) {
        this.storageRoot = Paths.get(storageRoot)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String store(
            UUID workspaceId,
            UUID documentId,
            MultipartFile file
    ) throws IOException {

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        String safeFilename = Paths.get(originalFilename)
                .getFileName()
                .toString();

        if (safeFilename.isBlank()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        Path documentDirectory = storageRoot
                .resolve(workspaceId.toString())
                .resolve(documentId.toString())
                .normalize();

        if (!documentDirectory.startsWith(storageRoot)) {
            throw new SecurityException("Invalid storage path");
        }

        Files.createDirectories(documentDirectory);

        Path target = documentDirectory
                .resolve(safeFilename)
                .normalize();

        if (!target.startsWith(documentDirectory)) {
            throw new SecurityException("Invalid filename");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target);
        }

        return storageRoot.relativize(target)
                .toString();
    }

    @Override
    public Path resolve(String storagePath) {

        Path target = storageRoot
                .resolve(storagePath)
                .normalize();

        if (!target.startsWith(storageRoot)) {
            throw new SecurityException("Invalid storage path");
        }

        return target;
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(resolve(storagePath));
    }
}