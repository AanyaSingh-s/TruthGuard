package com.truthguard.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "document_versions",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_document_version_hash", columnNames = {"document_id", "content_hash"})
       })
public class DocumentVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_identifier", nullable = false, length = 100)
    private String versionIdentifier;

    @Column(name = "upload_timestamp", nullable = false, updatable = false)
    private Instant uploadTimestamp;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "authority_level", length = 50)
    private AuthorityLevel authorityLevel;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    /**
     * Explicit supersession link: The older version that THIS version explicitly supersedes.
     * Note: Never automatically assumed from upload time or effective date.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_version_id")
    private DocumentVersion supersedesVersion;

    /**
     * Explicit supersession link: The newer version that supersedes THIS version.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_id")
    private DocumentVersion supersededBy;

    public DocumentVersion() {
    }

    public DocumentVersion(
            UUID id,
            Document document,
            String versionIdentifier,
            LocalDate effectiveDate,
            AuthorityLevel authorityLevel,
            String contentHash,
            String rawContent
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.document = document;
        this.versionIdentifier = versionIdentifier;
        this.uploadTimestamp = Instant.now();
        this.effectiveDate = effectiveDate;
        this.authorityLevel = authorityLevel;
        this.contentHash = contentHash;
        this.rawContent = rawContent;
        this.processingStatus = ProcessingStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.uploadTimestamp == null) {
            this.uploadTimestamp = Instant.now();
        }
        if (this.processingStatus == null) {
            this.processingStatus = ProcessingStatus.PENDING;
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getVersionIdentifier() {
        return versionIdentifier;
    }

    public void setVersionIdentifier(String versionIdentifier) {
        this.versionIdentifier = versionIdentifier;
    }

    public Instant getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(Instant uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public AuthorityLevel getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(AuthorityLevel authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public DocumentVersion getSupersedesVersion() {
        return supersedesVersion;
    }

    public void setSupersedesVersion(DocumentVersion supersedesVersion) {
        this.supersedesVersion = supersedesVersion;
    }

    public DocumentVersion getSupersededBy() {
        return supersededBy;
    }

    public void setSupersededBy(DocumentVersion supersededBy) {
        this.supersededBy = supersededBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentVersion that = (DocumentVersion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DocumentVersion{" +
                "id=" + id +
                ", versionIdentifier='" + versionIdentifier + '\'' +
                ", uploadTimestamp=" + uploadTimestamp +
                ", effectiveDate=" + effectiveDate +
                ", authorityLevel=" + authorityLevel +
                ", contentHash='" + contentHash + '\'' +
                ", processingStatus=" + processingStatus +
                '}';
    }
}
