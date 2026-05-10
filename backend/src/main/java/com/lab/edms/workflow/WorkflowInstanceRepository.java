package com.lab.edms.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {

    @Query("SELECT wi FROM WorkflowInstance wi WHERE wi.versionId = :verId AND wi.state = 'IN_PROGRESS'")
    Optional<WorkflowInstance> findActiveByVersion(@Param("verId") Long verId);
}
