package com.truthguard.service;

import com.truthguard.exception.EmptyFileException;
import com.truthguard.exception.UnsupportedFileFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "pdf", "docx");
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private final Path rootStorageLocation;

    public StorageService(@Value("${truthguard.storage.upload-dir:./storage/uploads}") String uploadDir) {
        this.rootStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        init();
    }

    private void init() {
        try {
            Files.createDirectories(rootStorageLocation);
            log.info("Initialized secure file storage directory at: {}", rootStorageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize storage directory: " + rootStorageLocation, e);
        }
    }

    public record StoredFile(
        String safeIdentifier,
        String originalFilename,
        String extension,
        Path absolutePath,
        long sizeBytes
    ) {}

    public StoredFile store(UUID documentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyFileException("Uploaded file cannot be null or empty (0 bytes)");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum permitted limit of 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = validateAndExtractExtension(originalFilename);

        // Generate safe internal identifier (never trust original filename for file system operations)
        String safeIdentifier = UUID.randomUUID().toString();
        String safeFileName = safeIdentifier + "." + extension;

        Path documentDir = rootStorageLocation.resolve(documentId.toString()).normalize();
        if (!documentDir.startsWith(rootStorageLocation)) {
            throw new SecurityException("Directory traversal attack detected in storage resolution");
        }

        try {
            Files.createDirectories(documentDir);
            Path destination = documentDir.resolve(safeFileName).normalize();
            if (!destination.startsWith(documentDir)) {
                throw new SecurityException("Directory traversal attempt in file storage path");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Stored file safely at '{}' (original: '{}', size: {} bytes)",
                    destination, originalFilename, file.getSize());

            return new StoredFile(safeIdentifier, originalFilename, extension, destination, file.getSize());

        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file: " + originalFilename, e);
        }
    }

    public void deleteFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
                log.info("Cleaned up file at: {}", path);
            } catch (IOException e) {
                log.warn("Failed to delete file at {}: {}", path, e.getMessage());
            }
        }
    }

    public String validateAndExtractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new UnsupportedFileFormatException("File must have a valid filename with an extension");
        }

        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == originalFilename.length() - 1) {
            throw new UnsupportedFileFormatException("File '" + originalFilename + "' has no extension. Supported: .txt, .pdf, .docx");
        }

        String extension = originalFilename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedFileFormatException(
                "Unsupported file format '." + extension + "'. Supported formats are: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }

        return extension;
    }
}
