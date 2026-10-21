package com.truthguard.service;

import com.truthguard.dto.DocumentVersionResponse;
import com.truthguard.exception.*;
import com.truthguard.model.AuthorityLevel;
import com.truthguard.model.Document;
import com.truthguard.model.DocumentVersion;
import com.truthguard.model.ProcessingStatus;
import com.truthguard.model.SourceType;
import com.truthguard.repository.DocumentRepository;
import com.truthguard.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository versionRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private TextExtractionClient extractionClient;

    private DocumentIngestionService ingestionService;

    private Document sampleDocument;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        ingestionService = new DocumentIngestionService(
                documentRepository,
                versionRepository,
                storageService,
                extractionClient
        );
        documentId = UUID.randomUUID();
        sampleDocument = new Document(documentId, "Enterprise Security Standard", SourceType.POLICY, "Security baseline");
    }

    @Test
    @DisplayName("Should successfully ingest valid TXT file, link extracted text, and transition status to PROCESSED")
    void shouldSuccessfullyIngestSupportedTxtFile() {
        byte[] contentBytes = "Access Control: Production systems require multi-factor authentication.".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain", contentBytes);
        String versionIdentifier = "v1.0";
        String contentHash = "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0";
        String extractedText = "Access Control: Production systems require multi-factor authentication.";

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(storageService.validateAndExtractExtension("policy.txt")).thenReturn("txt");
        StorageService.StoredFile storedFile = new StorageService.StoredFile(
                "safe-id-123",
                "policy.txt",
                "txt",
                Paths.get("/tmp/storage/policy.txt"),
                contentBytes.length
        );
        when(storageService.store(documentId, file)).thenReturn(storedFile);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TextExtractionClient.ExtractionResult extractionResult = new TextExtractionClient.ExtractionResult(
                "TXT",
                extractedText,
                extractedText.length(),
                8,
                contentHash
        );
        when(extractionClient.extractText("policy.txt", contentBytes)).thenReturn(extractionResult);
        when(versionRepository.existsByDocumentIdAndContentHash(documentId, contentHash)).thenReturn(false);

        DocumentVersionResponse response = ingestionService.ingestFile(
                documentId,
                file,
                versionIdentifier,
                LocalDate.of(2024, 1, 15),
                AuthorityLevel.OFFICIAL,
                null
        );

        assertThat(response).isNotNull();
        assertThat(response.versionIdentifier()).isEqualTo("v1.0");
        assertThat(response.processingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(response.contentHash()).isEqualTo(contentHash);
        assertThat(response.rawContent()).isEqualTo(extractedText);
        assertThat(response.authorityLevel()).isEqualTo(AuthorityLevel.OFFICIAL);
        assertThat(response.effectiveDate()).isEqualTo(LocalDate.of(2024, 1, 15));

        verify(documentRepository).save(sampleDocument);
    }

    @Test
    @DisplayName("Should successfully ingest PDF file and store extracted text")
    void shouldSuccessfullyIngestSupportedPdfFile() {
        byte[] pdfBytes = "%PDF-1.4 sample pdf content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "guideline.pdf", "application/pdf", pdfBytes);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(storageService.validateAndExtractExtension("guideline.pdf")).thenReturn("pdf");
        StorageService.StoredFile storedFile = new StorageService.StoredFile(
                "safe-uuid-pdf",
                "guideline.pdf",
                "pdf",
                Paths.get("/tmp/guideline.pdf"),
                pdfBytes.length
        );
        when(storageService.store(documentId, file)).thenReturn(storedFile);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TextExtractionClient.ExtractionResult extractionResult = new TextExtractionClient.ExtractionResult(
                "PDF",
                "Parsed PDF text content.",
                24,
                4,
                "pdfhash1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        );
        when(extractionClient.extractText("guideline.pdf", pdfBytes)).thenReturn(extractionResult);
        when(versionRepository.existsByDocumentIdAndContentHash(eq(documentId), anyString())).thenReturn(false);

        DocumentVersionResponse response = ingestionService.ingestFile(
                documentId,
                file,
                "v2.0",
                LocalDate.of(2024, 6, 1),
                AuthorityLevel.EXECUTIVE,
                null
        );

        assertThat(response.processingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(response.rawContent()).isEqualTo("Parsed PDF text content.");
    }

    @Test
    @DisplayName("Should handle explicit supersession during upload and link older version")
    void shouldHandleExplicitSupersessionDuringIngest() {
        byte[] contentBytes = "Superseding policy text".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "policy_v2.txt", "text/plain", contentBytes);

        UUID olderVersionId = UUID.randomUUID();
        DocumentVersion olderVersion = new DocumentVersion(
                olderVersionId,
                sampleDocument,
                "v1.0",
                LocalDate.of(2023, 1, 1),
                AuthorityLevel.OFFICIAL,
                "old-hash",
                "Old policy text"
        );

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(storageService.validateAndExtractExtension("policy_v2.txt")).thenReturn("txt");
        StorageService.StoredFile storedFile = new StorageService.StoredFile("safe-id", "policy_v2.txt", "txt", Paths.get("/tmp/f.txt"), contentBytes.length);
        when(storageService.store(documentId, file)).thenReturn(storedFile);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TextExtractionClient.ExtractionResult result = new TextExtractionClient.ExtractionResult(
                "TXT", "Superseding policy text", 23, 3, "newhash999"
        );
        when(extractionClient.extractText("policy_v2.txt", contentBytes)).thenReturn(result);
        when(versionRepository.existsByDocumentIdAndContentHash(documentId, "newhash999")).thenReturn(false);
        when(versionRepository.findByIdAndDocumentId(olderVersionId, documentId)).thenReturn(Optional.of(olderVersion));

        DocumentVersionResponse response = ingestionService.ingestFile(
                documentId,
                file,
                "v2.0",
                LocalDate.of(2024, 1, 1),
                AuthorityLevel.EXECUTIVE,
                olderVersionId
        );

        assertThat(response.supersedesVersionId()).isEqualTo(olderVersionId);
        assertThat(olderVersion.getSupersededBy()).isNotNull();
        assertThat(olderVersion.getSupersededBy().getVersionIdentifier()).isEqualTo("v2.0");
        verify(versionRepository).save(olderVersion);
    }

    @Test
    @DisplayName("Should reject upload when versionIdentifier is empty or blank")
    void shouldRejectBlankVersionIdentifier() {
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, file, "", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version identifier is required");

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, file, "   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject upload when target document does not exist")
    void shouldRejectNonExistentDocument() {
        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain", "content".getBytes());
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, file, "v1.0", null, null, null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    @DisplayName("Should reject empty (0-byte) file with EmptyFileException before creating version")
    void shouldRejectEmptyFileWithClearError() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, emptyFile, "v1.0", null, null, null))
                .isInstanceOf(EmptyFileException.class)
                .hasMessageContaining("empty");

        verify(versionRepository, never()).save(any(DocumentVersion.class));
    }

    @Test
    @DisplayName("Should reject unsupported format before creating version")
    void shouldRejectUnsupportedFormat() {
        MockMultipartFile exeFile = new MockMultipartFile("file", "app.exe", "application/octet-stream", "MZbinary".getBytes());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        doThrow(new UnsupportedFileFormatException("Unsupported file format '.exe'"))
                .when(storageService).validateAndExtractExtension("app.exe");

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, exeFile, "v1.0", null, null, null))
                .isInstanceOf(UnsupportedFileFormatException.class);

        verify(versionRepository, never()).save(any(DocumentVersion.class));
    }

    @Test
    @DisplayName("Failed processing does NOT leave version falsely marked as PROCESSED: marks status FAILED on extraction failure")
    void shouldMarkStatusFailedWhenExtractionFails() {
        byte[] corruptPdfBytes = "%PDF-1.4 corrupt".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.pdf", "application/pdf", corruptPdfBytes);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(storageService.validateAndExtractExtension("corrupt.pdf")).thenReturn("pdf");
        StorageService.StoredFile storedFile = new StorageService.StoredFile("safe-id", "corrupt.pdf", "pdf", Paths.get("/tmp/c.pdf"), corruptPdfBytes.length);
        when(storageService.store(documentId, file)).thenReturn(storedFile);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(extractionClient.extractText("corrupt.pdf", corruptPdfBytes))
                .thenThrow(new DocumentParsingException("Malformed or corrupt PDF document"));

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, file, "v1.0", null, null, null))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessageContaining("Malformed");

        // Verify version was saved with status FAILED and never PROCESSED
        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionRepository, atLeast(2)).save(versionCaptor.capture());

        DocumentVersion lastSavedVersion = versionCaptor.getAllValues().get(versionCaptor.getAllValues().size() - 1);
        assertThat(lastSavedVersion.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(lastSavedVersion.getRawContent()).isNull();
    }

    @Test
    @DisplayName("Should mark version FAILED when extraction yields empty text")
    void shouldMarkStatusFailedWhenExtractedTextIsEmpty() {
        byte[] bytes = "Some valid bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "scanned.pdf", "application/pdf", bytes);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(storageService.validateAndExtractExtension("scanned.pdf")).thenReturn("pdf");
        StorageService.StoredFile storedFile = new StorageService.StoredFile("safe-id", "scanned.pdf", "pdf", Paths.get("/tmp/s.pdf"), bytes.length);
        when(storageService.store(documentId, file)).thenReturn(storedFile);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TextExtractionClient.ExtractionResult emptyResult = new TextExtractionClient.ExtractionResult(
                "PDF", "   ", 0, 0, "hash"
        );
        when(extractionClient.extractText("scanned.pdf", bytes)).thenReturn(emptyResult);

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, file, "v1.0", null, null, null))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessageContaining("no readable text content");

        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionRepository, atLeast(2)).save(versionCaptor.capture());
        DocumentVersion lastSavedVersion = versionCaptor.getAllValues().get(versionCaptor.getAllValues().size() - 1);
        assertThat(lastSavedVersion.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("Should mark version FAILED when duplicate content hash is detected")
    void shouldMarkStatusFailedWhenDuplicateContentUploaded() {
        byte[] contentBytes = "Existing text".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "duplicate.txt", "text/plain", contentBytes);
        String hash = "existing-hash-12345";

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(storageService.validateAndExtractExtension("duplicate.txt")).thenReturn("txt");
        StorageService.StoredFile storedFile = new StorageService.StoredFile("safe-id", "duplicate.txt", "txt", Paths.get("/tmp/d.txt"), contentBytes.length);
        when(storageService.store(documentId, file)).thenReturn(storedFile);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TextExtractionClient.ExtractionResult result = new TextExtractionClient.ExtractionResult("TXT", "Existing text", 13, 2, hash);
        when(extractionClient.extractText("duplicate.txt", contentBytes)).thenReturn(result);
        when(versionRepository.existsByDocumentIdAndContentHash(documentId, hash)).thenReturn(true);

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, file, "v1.1", null, null, null))
                .isInstanceOf(DuplicateContentException.class)
                .hasMessageContaining(hash);

        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionRepository, atLeast(2)).save(versionCaptor.capture());
        DocumentVersion lastSavedVersion = versionCaptor.getAllValues().get(versionCaptor.getAllValues().size() - 1);
        assertThat(lastSavedVersion.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("Should mark version FAILED when supersedesVersionId does not exist")
    void shouldMarkStatusFailedWhenInvalidSupersessionTargetProvided() {
        byte[] contentBytes = "New content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "valid.txt", "text/plain", contentBytes);
        UUID bogusSupersedesId = UUID.randomUUID();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(storageService.validateAndExtractExtension("valid.txt")).thenReturn("txt");
        StorageService.StoredFile storedFile = new StorageService.StoredFile("safe-id", "valid.txt", "txt", Paths.get("/tmp/v.txt"), contentBytes.length);
        when(storageService.store(documentId, file)).thenReturn(storedFile);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TextExtractionClient.ExtractionResult result = new TextExtractionClient.ExtractionResult("TXT", "New content", 11, 2, "hash-unique");
        when(extractionClient.extractText("valid.txt", contentBytes)).thenReturn(result);
        when(versionRepository.existsByDocumentIdAndContentHash(documentId, "hash-unique")).thenReturn(false);
        when(versionRepository.findByIdAndDocumentId(bogusSupersedesId, documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.ingestFile(documentId, file, "v2.0", null, null, bogusSupersedesId))
                .isInstanceOf(InvalidSupersessionException.class)
                .hasMessageContaining(bogusSupersedesId.toString());

        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionRepository, atLeast(2)).save(versionCaptor.capture());
        DocumentVersion lastSavedVersion = versionCaptor.getAllValues().get(versionCaptor.getAllValues().size() - 1);
        assertThat(lastSavedVersion.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
    }
}
