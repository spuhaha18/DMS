package com.lab.edms.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RetentionExtensionOutboxRepository extends JpaRepository<RetentionExtensionOutbox, Long> {

    /**
     * 원자적으로 PENDING 행을 PROCESSING으로 클레임.
     * FOR UPDATE SKIP LOCKED: 다중 워커 인스턴스 간 중복 처리 방지.
     */
    @Modifying
    @Query(value = """
        UPDATE retention_extension_outbox
        SET status = 'PROCESSING', locked_at = now(), locked_by = :worker
        WHERE id IN (
            SELECT id FROM retention_extension_outbox
            WHERE status = 'PENDING' AND attempts < 5
            ORDER BY id
            LIMIT :batch
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id
        """, nativeQuery = true)
    List<Long> claimBatch(@Param("batch") int batch, @Param("worker") String worker);
}
