package com.lab.edms.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("""
      SELECT d FROM Document d
       WHERE (:catIds IS NULL OR d.categoryId IN :catIds)
         AND (:allDepts = TRUE OR d.department IN :depts)
         AND (d.confidential = FALSE OR d.ownerId = :userId)
    """)
    Page<Document> searchVisible(
            @Param("catIds") Collection<Long> catIds,
            @Param("depts") Collection<String> depts,
            @Param("allDepts") boolean allDepts,
            @Param("userId") Long userId,
            Pageable pageable);
}
