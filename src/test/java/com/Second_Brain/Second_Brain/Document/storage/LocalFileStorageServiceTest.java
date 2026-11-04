package com.Second_Brain.Second_Brain.Document.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path storageRoot;

    private LocalFileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageService(storageRoot.toString());
    }

    @Test
    void storeReturnsARelativePathThatResolveFinds() throws IOException {
        String storagePath = store("notes.txt", "hello");

        assertThat(Path.of(storagePath).isAbsolute()).isFalse();
        assertThat(storage.resolve(storagePath))
                .isEqualTo(storageRoot.resolve(storagePath))
                .hasContent("hello");
    }

    @Test
    void storeKeepsOnlyTheFileNameOfTheUpload() throws IOException {
        String storagePath = store("../../evil.txt", "hello");

        assertThat(Path.of(storagePath).getFileName()).hasToString("evil.txt");
        assertThat(storage.resolve(storagePath)).hasContent("hello");
    }

    @Test
    void resolveRejectsPathsOutsideTheStorageRoot() {
        assertThatThrownBy(() -> storage.resolve("../outside.txt"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> storage.resolve("workspace/../../outside.txt"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> storage.resolve("/etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void resolveRejectsASiblingDirectoryWithTheSamePrefix() {
        // A plain string prefix check would wrongly treat <tmp>/storage-backup
        // as being inside <tmp>/storage.
        LocalFileStorageService nested =
                new LocalFileStorageService(storageRoot.resolve("storage").toString());

        assertThatThrownBy(() -> nested.resolve("../storage-backup/secret.txt"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void deleteRemovesTheStoredFile() throws IOException {
        String storagePath = store("notes.txt", "hello");

        storage.delete(storagePath);

        assertThat(storage.resolve(storagePath)).doesNotExist();
    }

    @Test
    void deleteRefusesPathsOutsideTheStorageRoot() throws IOException {
        Path outside = Files.writeString(storageRoot.resolve("outside.txt"), "keep me");
        LocalFileStorageService nested = new LocalFileStorageService(
                Files.createDirectories(storageRoot.resolve("storage")).toString());

        assertThatThrownBy(() -> nested.delete("../outside.txt"))
                .isInstanceOf(SecurityException.class);
        assertThat(outside).exists();
    }

    private String store(String filename, String content) throws IOException {
        return storage.store(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new MockMultipartFile("file", filename, "text/plain",
                        content.getBytes(StandardCharsets.UTF_8)));
    }
}
