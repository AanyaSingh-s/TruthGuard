package com.truthguard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truthguard.dto.CreateDocumentRequest;
import com.truthguard.dto.RegisterVersionRequest;
import com.truthguard.dto.SupersedeVersionRequest;
import com.truthguard.model.AuthorityLevel;
import com.truthguard.model.SourceType;
import com.truthguard.exception.DocumentParsingException;
import com.truthguard.service.TextExtractionClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TextExtractionClient extractionClient;

    @Test
    @DisplayName("POST /api/v1/documents should create document and return HTTP 201")
    void shouldCreateDocumentSuccessfully() throws Exception {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "Compliance Framework 2024",
                SourceType.REGULATION,
                "Statutory regulatory compliance baseline"
        );

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Compliance Framework 2024"))
                .andExpect(jsonPath("$.sourceType").value("REGULATION"))
                .andExpect(jsonPath("$.versionCount").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/documents with missing metadata should return HTTP 400 Bad Request")
    void shouldFailWhenDocumentMetadataMissing() throws Exception {
        // Missing title
        String invalidPayloadMissingTitle = """
            {
                "title": "",
                "sourceType": "POLICY"
            }
            """;

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayloadMissingTitle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationFailed"))
                .andExpect(jsonPath("$.validationErrors.title").isNotEmpty());

        // Missing sourceType
        String invalidPayloadMissingSourceType = """
            {
                "title": "Valid Title",
                "sourceType": null
            }
            """;

        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayloadMissingSourceType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationFailed"))
                .andExpect(jsonPath("$.validationErrors.sourceType").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/versions should register version with computed SHA-256")
    void shouldRegisterVersionSuccessfully() throws Exception {
        UUID docId = createTestDocument("Architecture Guide");

        RegisterVersionRequest request = new RegisterVersionRequest(
                "v1.0",
                LocalDate.of(2024, 2, 1),
                AuthorityLevel.OFFICIAL,
                "Microservices communication must use gRPC or HTTPS.",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/documents/" + docId + "/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.versionIdentifier").value("v1.0"))
                .andExpect(jsonPath("$.effectiveDate").value("2024-02-01"))
                .andExpect(jsonPath("$.authorityLevel").value("OFFICIAL"))
                .andExpect(jsonPath("$.authorityRank").value(3))
                .andExpect(jsonPath("$.contentHash").hasJsonPath())
                .andExpect(jsonPath("$.isSuperseded").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/versions should reject duplicate upload with identical content hash")
    void shouldRejectDuplicateVersionUpload() throws Exception {
        UUID docId = createTestDocument("Firewall Rules");
        String content = "Allow inbound port 443; Deny inbound port 80.";

        RegisterVersionRequest request1 = new RegisterVersionRequest(
                "v1.0",
                LocalDate.of(2024, 1, 1),
                AuthorityLevel.OFFICIAL,
                content,
                null,
                null
        );

        // First upload succeeds
        mockMvc.perform(post("/api/v1/documents/" + docId + "/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Second upload with identical content -> HTTP 409 CONFLICT
        RegisterVersionRequest duplicateRequest = new RegisterVersionRequest(
                "v1.1",
                LocalDate.of(2024, 3, 1),
                AuthorityLevel.OFFICIAL,
                content, // Exact same content
                null,
                null
        );

        mockMvc.perform(post("/api/v1/documents/" + docId + "/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DuplicateContent"))
                .andExpect(jsonPath("$.message").value(containsString("already contains an identical version")));
    }

    @Test
    @DisplayName("Version ordering: upload timestamp vs effective date are decoupled and independently orderable")
    void shouldOrderVersionsByUploadTimestampAndEffectiveDateIndependently() throws Exception {
        UUID docId = createTestDocument("Historical Fiscal Policy");

        // Version A: uploaded first, but effective LATER (e.g. 2024-12-01)
        RegisterVersionRequest versionA = new RegisterVersionRequest(
                "vA-future-effective",
                LocalDate.of(2024, 12, 1),
                AuthorityLevel.OFFICIAL,
                "Content for future effective policy A",
                null,
                null
        );
        mockMvc.perform(post("/api/v1/documents/" + docId + "/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(versionA)))
                .andExpect(status().isCreated());

        // Slight delay to guarantee strictly distinct upload timestamp
        Thread.sleep(20);

        // Version B: uploaded second (later), but effective EARLIER (e.g. 2024-01-01)
        RegisterVersionRequest versionB = new RegisterVersionRequest(
                "vB-past-effective",
                LocalDate.of(2024, 1, 1),
                AuthorityLevel.OFFICIAL,
                "Content for past effective policy B",
                null,
                null
        );
        mockMvc.perform(post("/api/v1/documents/" + docId + "/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(versionB)))
                .andExpect(status().isCreated());

        // 1. Order by uploadTimestamp ascending -> [A, B]
        mockMvc.perform(get("/api/v1/documents/" + docId + "/versions?sortBy=uploadTimestamp&direction=asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionIdentifier").value("vA-future-effective"))
                .andExpect(jsonPath("$[1].versionIdentifier").value("vB-past-effective"));

        // 2. Order by effectiveDate ascending -> [B, A]
        mockMvc.perform(get("/api/v1/documents/" + docId + "/versions?sortBy=effectiveDate&direction=asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionIdentifier").value("vB-past-effective"))
                .andExpect(jsonPath("$[1].versionIdentifier").value("vA-future-effective"));
    }

    @Test
    @DisplayName("Explicit supersession: Later upload does not automatically supersede, explicit linking works")
    void shouldHandleExplicitSupersessionProperly() throws Exception {
        UUID docId = createTestDocument("Data Retention Standard");

        // Register v1
        MvcResult res1 = mockMvc.perform(post("/api/v1/documents/" + docId + "/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterVersionRequest(
                        "v1.0", LocalDate.of(2023, 1, 1), AuthorityLevel.OFFICIAL, "Retain logs 30 days", null, null
                ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID v1Id = UUID.fromString(objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asText());

        // Register v2 WITHOUT superseding -> v1 should NOT be superseded
        MvcResult res2 = mockMvc.perform(post("/api/v1/documents/" + docId + "/versions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterVersionRequest(
                        "v2.0", LocalDate.of(2024, 1, 1), AuthorityLevel.EXECUTIVE, "Retain logs 90 days", null, null
                ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID v2Id = UUID.fromString(objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asText());

        // Check v1 is still NOT superseded
        mockMvc.perform(get("/api/v1/documents/" + docId + "/versions/" + v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuperseded").value(false))
                .andExpect(jsonPath("$.supersededById").doesNotExist());

        // Now explicitly mark v1 as superseded by v2
        mockMvc.perform(post("/api/v1/documents/" + docId + "/versions/" + v1Id + "/supersede")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SupersedeVersionRequest(v2Id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v1Id.toString()))
                .andExpect(jsonPath("$.isSuperseded").value(true))
                .andExpect(jsonPath("$.supersededById").value(v2Id.toString()));

        // Verify v1 is now superseded by v2
        mockMvc.perform(get("/api/v1/documents/" + docId + "/versions/" + v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuperseded").value(true))
                .andExpect(jsonPath("$.supersededById").value(v2Id.toString()))
                .andExpect(jsonPath("$.supersededByVersionIdentifier").value("v2.0"));
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/versions/upload should ingest supported TXT and link extracted text")
    void shouldUploadSupportedFileSuccessfullyViaMultipart() throws Exception {
        UUID docId = createTestDocument("Network Security Architecture");
        String sampleText = "All egress traffic must traverse the authenticated web proxy.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "security_policy.txt", "text/plain", sampleText.getBytes()
        );

        when(extractionClient.extractText(anyString(), any())).thenReturn(
                new TextExtractionClient.ExtractionResult("TXT", sampleText, sampleText.length(), 8, "sha256-mock-hash-111")
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/documents/" + docId + "/versions/upload")
                        .file(file)
                        .param("versionIdentifier", "v1.0")
                        .param("effectiveDate", "2024-03-01")
                        .param("authorityLevel", "OFFICIAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(docId.toString()))
                .andExpect(jsonPath("$.versionIdentifier").value("v1.0"))
                .andExpect(jsonPath("$.processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.rawContent").value(sampleText))
                .andExpect(jsonPath("$.contentHash").value("sha256-mock-hash-111"))
                .andExpect(jsonPath("$.authorityLevel").value("OFFICIAL"));
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/versions/upload with empty (0-byte) file should return HTTP 400 Bad Request")
    void shouldRejectUploadOfEmptyFile() throws Exception {
        UUID docId = createTestDocument("Empty Policy Test");
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/documents/" + docId + "/versions/upload")
                        .file(emptyFile)
                        .param("versionIdentifier", "v1.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EmptyFile"))
                .andExpect(jsonPath("$.message").value(containsString("empty")));
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/versions/upload with unsupported file extension should return HTTP 415")
    void shouldRejectUploadOfUnsupportedExtension() throws Exception {
        UUID docId = createTestDocument("Unsupported Extension Test");
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "MZbinary".getBytes()
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/documents/" + docId + "/versions/upload")
                        .file(exeFile)
                        .param("versionIdentifier", "v1.0"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UnsupportedFormat"))
                .andExpect(jsonPath("$.message").value(containsString("Unsupported file format '.exe'")));
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/versions/upload when extraction fails records status as FAILED and returns HTTP 422")
    void shouldHandleExtractionFailureAndRecordStatusFailed() throws Exception {
        UUID docId = createTestDocument("Malformed Document Test");
        MockMultipartFile corruptFile = new MockMultipartFile(
                "file", "corrupt.pdf", "application/pdf", "%PDF-bad-corrupted".getBytes()
        );

        when(extractionClient.extractText(anyString(), any()))
                .thenThrow(new DocumentParsingException("Malformed or corrupt PDF document: stream unreadable"));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/documents/" + docId + "/versions/upload")
                        .file(corruptFile)
                        .param("versionIdentifier", "v1.0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("DocumentParsingError"))
                .andExpect(jsonPath("$.message").value(containsString("Malformed or corrupt PDF")));

        // Verify version was saved with status FAILED and never marked PROCESSED
        mockMvc.perform(get("/api/v1/documents/" + docId + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].versionIdentifier").value("v1.0"))
                .andExpect(jsonPath("$[0].processingStatus").value("FAILED"))
                .andExpect(jsonPath("$[0].rawContent").doesNotExist());
    }

    @Test
    @DisplayName("Upload synthetic sample TXT and PDF files and verify content linking")
    void shouldUploadSyntheticSampleTxtAndPdfFiles() throws Exception {
        UUID docId = createTestDocument("Synthetic Corpora Standard");

        // 1. Upload synthetic TXT
        byte[] txtBytes;
        try (var is = getClass().getClassLoader().getResourceAsStream("sample_documents/sample_policy_v1.txt")) {
            txtBytes = is.readAllBytes();
        }
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "sample_policy_v1.txt", "text/plain", txtBytes
        );

        when(extractionClient.extractText(eq("sample_policy_v1.txt"), any())).thenReturn(
                new TextExtractionClient.ExtractionResult(
                        "TXT", new String(txtBytes), txtBytes.length, 45, "synthetic-txt-hash-v1"
                )
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/documents/" + docId + "/versions/upload")
                        .file(txtFile)
                        .param("versionIdentifier", "v1.0")
                        .param("effectiveDate", "2024-01-15")
                        .param("authorityLevel", "OFFICIAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionIdentifier").value("v1.0"))
                .andExpect(jsonPath("$.processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.rawContent").value(containsString("TruthGuard Enterprise Security Standard")));

        // 2. Upload synthetic PDF
        byte[] pdfBytes;
        try (var is = getClass().getClassLoader().getResourceAsStream("sample_documents/sample_regulation.pdf")) {
            pdfBytes = is.readAllBytes();
        }
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "sample_regulation.pdf", "application/pdf", pdfBytes
        );

        when(extractionClient.extractText(eq("sample_regulation.pdf"), any())).thenReturn(
                new TextExtractionClient.ExtractionResult(
                        "PDF", "TruthGuard Regulatory Compliance Guideline", 42, 4, "synthetic-pdf-hash-v2"
                )
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/documents/" + docId + "/versions/upload")
                        .file(pdfFile)
                        .param("versionIdentifier", "v2.0")
                        .param("effectiveDate", "2024-06-01")
                        .param("authorityLevel", "REGULATORY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionIdentifier").value("v2.0"))
                .andExpect(jsonPath("$.processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.rawContent").value(containsString("Regulatory Compliance Guideline")));
    }

    private UUID createTestDocument(String title) throws Exception {
        CreateDocumentRequest request = new CreateDocumentRequest(title, SourceType.POLICY, "Test policy");
        MvcResult result = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());
    }
}
