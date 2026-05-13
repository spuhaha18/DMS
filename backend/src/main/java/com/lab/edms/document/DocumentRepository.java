package com.lab.edms.document;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    // SELECT FOR UPDATE (단일 EFFECTIVE 보장 + T-06 race 차단용)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> lockForUpdate(@Param("id") Long id);

    /** 부팅 시 orphan STAMPING 재처리(StampWorkerReaper)용. */
    List<Document> findByPdfStatus(String pdfStatus);

    @Query("""
      SELECT d FROM Document d
       WHERE (:catIds IS NULL OR d.categoryId IN :catIds)
         AND (:allDepts = TRUE OR d.department IN :depts)
         AND (d.confidential = FALSE OR d.ownerId = :userId)
         AND (:state IS NULL OR EXISTS (
               SELECT 1 FROM DocumentVersion v WHERE v.documentId = d.id AND v.state = :state
             ))
    """)
    Page<Document> searchVisible(
            @Param("catIds") Collection<Long> catIds,
            @Param("depts") Collection<String> depts,
            @Param("allDepts") boolean allDepts,
            @Param("userId") Long userId,
            @Param("state") String state,
            Pageable pageable);

    /** M7.5: 연구과제 코드로 페이지 단위 조회 — outbox 발급용 bulk 처리. */
    Page<Document> findByProject_ProjectCode(String projectCode, Pageable pageable);
}
