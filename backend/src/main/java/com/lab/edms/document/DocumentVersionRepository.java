package com.lab.edms.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
