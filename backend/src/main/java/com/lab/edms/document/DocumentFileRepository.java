package com.lab.edms.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {

    List<DocumentFile> findByVersionIdOrderByUploadedAtDesc(Long versionId);
}
