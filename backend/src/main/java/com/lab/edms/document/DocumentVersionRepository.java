package com.lab.edms.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);

    // state로 버전 조회 (예: "EFFECTIVE" 버전 찾기)
    Optional<DocumentVersion> findByDocumentIdAndState(Long documentId, String state);

    // 최대 revision 번호 (T-03 채번용)
    @Query("SELECT MAX(v.revision) FROM DocumentVersion v WHERE v.documentId = :docId")
    Optional<Integer> findMaxRevisionByDocumentId(@Param("docId") Long docId);

    // in-flight 버전 조회 (T-06 단일 in-flight 가드용)
    @Query("SELECT v FROM DocumentVersion v WHERE v.documentId = :docId AND v.state IN ('DRAFT','UNDER_REVIEW','UNDER_APPROVAL','UNDER_REVISION') AND v.id != :excludeId")
    List<DocumentVersion> findInFlightByDocumentIdExcluding(@Param("docId") Long docId, @Param("excludeId") Long excludeId);

    // T-07: 신규 EFFECTIVE 진입 시 기존 EFFECTIVE(→UNDER_REVISION을 통해 온) 버전 찾기
    @Query("SELECT v FROM DocumentVersion v WHERE v.documentId = :docId AND v.state = 'EFFECTIVE' AND v.id != :excludeId")
    Optional<DocumentVersion> findEffectiveByDocumentIdExcluding(@Param("docId") Long docId, @Param("excludeId") Long excludeId);
}
