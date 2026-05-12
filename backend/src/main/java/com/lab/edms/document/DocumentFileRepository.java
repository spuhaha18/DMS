package com.lab.edms.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {

    List<DocumentFile> findByVersionIdOrderByUploadedAtDesc(Long versionId);

    List<DocumentFile> findByVersionIdAndFileType(Long versionId, String fileType);

    /** STAMPED rendition 중 가장 높은 step_number를 가진 행 반환 (누적 base 조회용). */
    Optional<DocumentFile> findTopByVersionIdAndFileTypeAndRenditionKindOrderByStepNumberDesc(
            Long versionId, String fileType, String renditionKind);

    /** INITIAL rendition 행 반환 (첫 번째 stamp base 조회용). */
    Optional<DocumentFile> findFirstByVersionIdAndFileTypeAndRenditionKind(
            Long versionId, String fileType, String renditionKind);
}
