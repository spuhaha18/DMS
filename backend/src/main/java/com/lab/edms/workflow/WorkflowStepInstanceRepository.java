package com.lab.edms.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface WorkflowStepInstanceRepository extends JpaRepository<WorkflowStepInstance, Long> {

    @Query("SELECT wsi FROM WorkflowStepInstance wsi WHERE wsi.workflowId = :wfId ORDER BY wsi.stepOrder")
    List<WorkflowStepInstance> findOrderedByWorkflow(@Param("wfId") Long wfId);

    @Query(value = """
        SELECT wsi.*
          FROM workflow_step_instances wsi
          JOIN workflow_instances wi ON wi.id = wsi.workflow_id
         WHERE wi.state = 'IN_PROGRESS'
           AND wsi.state = 'IN_PROGRESS'
           AND wsi.assignees @> jsonb_build_array(jsonb_build_object('userIdString', :userIdString))
    """, nativeQuery = true)
    List<WorkflowStepInstance> findMyPending(@Param("userIdString") String userIdString);
}
