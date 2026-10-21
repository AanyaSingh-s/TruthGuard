package com.truthguard.service;

import com.truthguard.dto.CreateDocumentRequest;
import com.truthguard.dto.DocumentResponse;
import com.truthguard.dto.DocumentVersionResponse;
import com.truthguard.dto.RegisterVersionRequest;
import com.truthguard.exception.DuplicateContentException;
import com.truthguard.exception.InvalidSupersessionException;
import com.truthguard.model.AuthorityLevel;
import com.truthguard.model.Document;
import com.truthguard.model.DocumentVersion;
import com.truthguard.model.SourceType;
import com.truthguard.repository.DocumentRepository;
import com.truthguard.repository.DocumentVersionRepository;
import com.truthguard.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository versionRepository;

    private DocumentService documentService;

    private Document sampleDocument;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, versionRepository);
        documentId = UUID.randomUUID();
        sampleDocument = new Document(documentId, "Remote Work Policy", SourceType.POLICY, "Employee remote guidelines");
    }

    @Test
    @DisplayName("Should create document successfully")
    void shouldCreateDocument() {
        CreateDocumentRequest request = new CreateDocumentRequest("Security Standard", SourceType.SPECIFICATION, "Desc");
        when(documentRepository.existsByTitle("Security Standard")).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = documentService.createDocument(request);

        assertThat(response.title()).isEqualTo("Security Standard");
        assertThat(response.sourceType()).isEqualTo(SourceType.SPECIFICATION);
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("Should register version and compute SHA-256 hash when raw content is provided")
    void shouldRegisterVersionWithComputedHash() {
        String rawContent = "Remote work allowed up to 2 days per week.";
        String expectedHash = HashUtils.sha256Hex(rawContent);

        RegisterVersionRequest request = new RegisterVersionRequest(
                "v1.0",
                LocalDate.of(2024, 1, 15),
                AuthorityLevel.OFFICIAL,
                rawContent,
                null,
                null
        );

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(versionRepository.existsByDocumentIdAndContentHash(documentId, expectedHash)).thenReturn(false);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentVersionResponse response = documentService.registerVersion(documentId, request);

        assertThat(response.versionIdentifier()).isEqualTo("v1.0");
        assertThat(response.contentHash()).isEqualTo(expectedHash);
        assertThat(response.authorityLevel()).isEqualTo(AuthorityLevel.OFFICIAL);
        assertThat(response.effectiveDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(response.isSuperseded()).isFalse();
    }

    @Test
    @DisplayName("Should reject duplicate upload with identical content hash")
    void shouldRejectDuplicateUploadWithIdenticalContentHash() {
        String content = "Identical policy text.";
        String hash = HashUtils.sha256Hex(content);

        RegisterVersionRequest request = new RegisterVersionRequest(
                "v1.1",
                LocalDate.now(),
                AuthorityLevel.OFFICIAL,
                content,
                null,
                null
        );

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(versionRepository.existsByDocumentIdAndContentHash(documentId, hash)).thenReturn(true);

        assertThatThrownBy(() -> documentService.registerVersion(documentId, request))
                .isInstanceOf(DuplicateContentException.class)
                .hasMessageContaining(hash);

        verify(versionRepository, never()).save(any(DocumentVersion.class));
    }

    @Test
    @DisplayName("Later upload timestamp does NOT automatically supersede older document version")
    void laterUploadDoesNotAutomaticallySupersedeOlderVersion() {
        // Version 1 uploaded at T1
        DocumentVersion v1 = new DocumentVersion(
                UUID.randomUUID(), sampleDocument, "v1.0",
                LocalDate.of(2023, 1, 1), AuthorityLevel.OFFICIAL, "hash-1", "Content 1"
        );
        v1.setUploadTimestamp(Instant.parse("2024-01-01T10:00:00Z"));

        // Version 2 uploaded at T2 (later) with NO explicit supersession specified
        RegisterVersionRequest requestV2 = new RegisterVersionRequest(
                "v2.0-proposal",
                LocalDate.of(2024, 6, 1),
                AuthorityLevel.DRAFT,
                "Content 2 with later date",
                null,
                null // supersedesVersionId is NULL
        );

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(versionRepository.existsByDocumentIdAndContentHash(eq(documentId), anyString())).thenReturn(false);
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentVersionResponse responseV2 = documentService.registerVersion(documentId, requestV2);

        // Core principle verification: v1 is NOT superseded, and v2 does NOT supersede anything
        assertThat(v1.getSupersededBy()).isNull();
        assertThat(responseV2.supersedesVersionId()).isNull();
        assertThat(responseV2.isSuperseded()).isFalse();
    }

    @Test
    @DisplayName("Explicit supersession links both versions bidirectionally")
    void explicitSupersessionLinksBothVersions() {
        UUID v1Id = UUID.randomUUID();
        DocumentVersion v1 = new DocumentVersion(
                v1Id, sampleDocument, "v1.0",
                LocalDate.of(2023, 1, 1), AuthorityLevel.OFFICIAL, "hash-1", "Old policy"
        );

        RegisterVersionRequest requestV2 = new RegisterVersionRequest(
                "v2.0",
                LocalDate.of(2024, 1, 1),
                AuthorityLevel.EXECUTIVE,
                "New officially superseding policy",
                null,
                v1Id // explicitly superseding v1Id
        );

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(versionRepository.existsByDocumentIdAndContentHash(eq(documentId), anyString())).thenReturn(false);
        when(versionRepository.findByIdAndDocumentId(v1Id, documentId)).thenReturn(Optional.of(v1));
        when(versionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentVersionResponse responseV2 = documentService.registerVersion(documentId, requestV2);

        // Verify explicit link was set
        assertThat(responseV2.supersedesVersionId()).isEqualTo(v1Id);
        assertThat(v1.getSupersededBy()).isNotNull();
        assertThat(v1.getSupersededBy().getVersionIdentifier()).isEqualTo("v2.0");
        verify(versionRepository, times(1)).save(v1);
    }

    @Test
    @DisplayName("Should prevent version from superseding itself")
    void shouldPreventSelfSupersession() {
        UUID versionId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));

        assertThatThrownBy(() -> documentService.explicitlySupersede(documentId, versionId, versionId))
                .isInstanceOf(InvalidSupersessionException.class)
                .hasMessageContaining("A version cannot supersede itself");
    }

    @Test
    @DisplayName("Should list versions sorted by effectiveDate")
    void shouldListVersionsSortedByEffectiveDate() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(sampleDocument));
        when(versionRepository.findByDocumentId(eq(documentId), any(Sort.class)))
                .thenReturn(List.of());

        List<DocumentVersionResponse> list = documentService.listVersions(documentId, "effectiveDate", "asc");

        assertThat(list).isNotNull();
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(versionRepository).findByDocumentId(eq(documentId), sortCaptor.capture());

        Sort capturedSort = sortCaptor.getValue();
        Sort.Order order = capturedSort.getOrderFor("effectiveDate");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
