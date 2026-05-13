package com.lab.edms.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** STAMPED rendition 중 특정 step_number를 가진 행 반환 (step별 stamp 조회용). */
    Optional<DocumentFile> findFirstByVersionIdAndRenditionKindAndStepNumber(
            Long versionId, String renditionKind, Integer stepNumber);

    /** M7.5: 연구과제 코드로 DocumentFile 페이지 단위 조회 — outbox 발급용. */
    @Query(value = """
        SELECT f.* FROM document_files f
        JOIN document_versions dv ON dv.id = f.version_id
        JOIN documents d ON d.id = dv.document_id
        WHERE d.project_code = :code
        ORDER BY f.id
        """, nativeQuery = true)
    Page<DocumentFile> findByProjectCode(@Param("code") String code, Pageable pageable);
}
