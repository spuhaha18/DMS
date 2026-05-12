package com.lab.edms.document;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    // 병렬 서명 해시체인 경쟁 조건 방지용 SELECT FOR UPDATE
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM DocumentVersion v WHERE v.id = :id")
    java.util.Optional<DocumentVersion> lockForUpdate(@Param("id") Long id);

    // M7 PR3: PdfRenditionPipeline.applyStampForStep 동시 결재 race 가드
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM DocumentVersion v WHERE v.id = :id")
    Optional<DocumentVersion> findByIdForUpdate(@Param("id") Long id);

    // in-flight 버전 조회 (T-06 단일 in-flight 가드용)
    @Query("SELECT v FROM DocumentVersion v WHERE v.documentId = :docId AND v.state IN ('DRAFT','UNDER_REVIEW','UNDER_APPROVAL','UNDER_REVISION') AND v.id != :excludeId")
    List<DocumentVersion> findInFlightByDocumentIdExcluding(@Param("docId") Long docId, @Param("excludeId") Long excludeId);

    // T-07: 신규 EFFECTIVE 진입 시 기존 EFFECTIVE(→UNDER_REVISION을 통해 온) 버전 찾기
    @Query("SELECT v FROM DocumentVersion v WHERE v.documentId = :docId AND v.state = 'EFFECTIVE' AND v.id != :excludeId")
    Optional<DocumentVersion> findEffectiveByDocumentIdExcluding(@Param("docId") Long docId, @Param("excludeId") Long excludeId);

    /**
     * M7 PR4: EffectiveWatermarkScheduler — 유효일이 오늘이고 Documents.pdf_status = STAMPED 인 버전 조회.
     * 스케줄러가 EFFECTIVE 워터마크를 적용할 대상 버전을 찾는다.
     */
    @Query("""
      SELECT v FROM DocumentVersion v
       JOIN Document d ON d.id = v.documentId
       WHERE v.effectiveDate = :effectiveDate
         AND d.pdfStatus = :pdfStatus
    """)
    List<DocumentVersion> findByEffectiveDateAndDocumentPdfStatus(
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("pdfStatus") String pdfStatus);

    /**
     * M7 버그픽스: effectiveDate &lt;= today 조건 (catch-up 처리).
     * EffectiveWatermarkScheduler가 누락 날짜까지 처리하기 위해 사용.
     */
    @Query("""
      SELECT v FROM DocumentVersion v
       JOIN Document d ON d.id = v.documentId
       WHERE v.effectiveDate <= :effectiveDate
         AND d.pdfStatus = :pdfStatus
    """)
    List<DocumentVersion> findByEffectiveDateLessThanEqualAndDocumentPdfStatus(
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("pdfStatus") String pdfStatus);
}
