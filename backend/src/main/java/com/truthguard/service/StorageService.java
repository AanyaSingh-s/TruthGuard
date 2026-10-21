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
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private final Path rootStorageLocation;
    private final long maxFileSizeBytes;

    @org.springframework.beans.factory.annotation.Autowired
    public StorageService(@Value("${truthguard.storage.upload-dir:./storage/uploads}") String uploadDir) {
        this(uploadDir, DEFAULT_MAX_FILE_SIZE_BYTES);
    }

    public StorageService(String uploadDir, long maxFileSizeBytes) {
        this.rootStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
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

        if (file.getSize() > this.maxFileSizeBytes) {
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(this.maxFileSizeBytes);
        }

        String rawFilename = file.getOriginalFilename();
        String sanitizedFilename = sanitizeFilename(rawFilename);
        String extension = validateAndExtractExtension(sanitizedFilename);

        // Security Hardening: Validate magic bytes to prevent file extension spoofing
        validateMagicBytes(file, extension);

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

            log.info("Stored file safely at '{}' (sanitized original: '{}', size: {} bytes)",
                    destination, sanitizedFilename, file.getSize());

            return new StoredFile(safeIdentifier, sanitizedFilename, extension, destination, file.getSize());

        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file: " + sanitizedFilename, e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }
        // Strip any directory traversal or path prefixes (Windows and Unix)
        String clean = Paths.get(filename).getFileName().toString();
        // Disallow null bytes and non-printable characters
        clean = clean.replaceAll("[\\x00-\\x1F\\x7F]", "");
        return clean.trim();
    }

    private void validateMagicBytes(MultipartFile file, String extension) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 4) {
                return; // Let parser handle very short files
            }

            if ("pdf".equals(extension)) {
                // PDF magic: %PDF (0x25 0x50 0x44 0x46)
                if (header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
                    throw new UnsupportedFileFormatException("Invalid PDF format: file header does not match PDF specification (magic bytes mismatch)");
                }
            } else if ("docx".equals(extension)) {
                // DOCX (ZIP archive) magic: PK\x03\x04 (0x50 0x4B 0x03 0x04)
                if (header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                    throw new UnsupportedFileFormatException("Invalid DOCX format: file header does not match ZIP/DOCX specification (magic bytes mismatch)");
                }
            } else if ("txt".equals(extension)) {
                // Reject binary executable formats masquerading as txt (e.g. ELF 0x7F 'E' 'L' 'F', PE 'M' 'Z')
                if ((header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46) ||
                    (header[0] == 0x4D && header[1] == 0x5A)) {
                    throw new UnsupportedFileFormatException("Binary executable format detected in plain text file");
                }
            }
        } catch (UnsupportedFileFormatException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Could not read file header for magic byte check: {}", e.getMessage());
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
