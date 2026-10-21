package com.truthguard.service;

import com.truthguard.exception.EmptyFileException;
import com.truthguard.exception.UnsupportedFileFormatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

    @TempDir
    Path tempStorageDir;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(tempStorageDir.toString());
    }

    @Test
    @DisplayName("Should store file safely using a random safe identifier rather than raw filename")
    void shouldStoreFileWithSafeIdentifier() {
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../dangerous_name.txt",
                "text/plain",
                "Valid policy content".getBytes()
        );

        StorageService.StoredFile stored = storageService.store(docId, file);

        assertThat(stored.safeIdentifier()).isNotBlank();
        assertThat(stored.extension()).isEqualTo("txt");
        assertThat(stored.absolutePath()).exists();
        // Verifies the stored file name does NOT contain the path traversal
        assertThat(stored.absolutePath().getFileName().toString()).doesNotContain("dangerous_name");
        assertThat(stored.absolutePath().getFileName().toString()).startsWith(stored.safeIdentifier());
    }

    @Test
    @DisplayName("Should reject empty (0-byte) file upload")
    void shouldRejectEmptyFile() {
        UUID docId = UUID.randomUUID();
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> storageService.store(docId, emptyFile))
                .isInstanceOf(EmptyFileException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should reject unsupported file formats")
    void shouldRejectUnsupportedFileFormats() {
        UUID docId = UUID.randomUUID();
        MockMultipartFile exeFile = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "MZbinary".getBytes());

        assertThatThrownBy(() -> storageService.store(docId, exeFile))
                .isInstanceOf(UnsupportedFileFormatException.class)
                .hasMessageContaining(".exe");

        MockMultipartFile noExtFile = new MockMultipartFile("file", "noextension", "text/plain", "content".getBytes());
        assertThatThrownBy(() -> storageService.store(docId, noExtFile))
                .isInstanceOf(UnsupportedFileFormatException.class);
    }
}
