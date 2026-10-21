package com.truthguard.repository;

import com.truthguard.model.DocumentVersion;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentId(UUID documentId, Sort sort);

    boolean existsByDocumentIdAndContentHash(UUID documentId, String contentHash);

    Optional<DocumentVersion> findByDocumentIdAndVersionIdentifier(UUID documentId, String versionIdentifier);

    Optional<DocumentVersion> findByIdAndDocumentId(UUID versionId, UUID documentId);
}
