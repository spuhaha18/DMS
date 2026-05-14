package com.lab.edms.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainingAssignmentRepository extends JpaRepository<TrainingAssignment, Long> {

    List<TrainingAssignment> findByUserId(Long userId);

    List<TrainingAssignment> findByVersionId(Long versionId);

    Optional<TrainingAssignment> findByUserIdAndVersionId(Long userId, Long versionId);

    boolean existsByUserIdAndVersionId(Long userId, Long versionId);

    @Query("SELECT COUNT(t) FROM TrainingAssignment t WHERE t.versionId = :versionId")
    long countByVersionId(@Param("versionId") Long versionId);

    @Query("SELECT COUNT(t) FROM TrainingAssignment t WHERE t.versionId = :versionId AND t.completedAt IS NOT NULL")
    long countCompletedByVersionId(@Param("versionId") Long versionId);
}
