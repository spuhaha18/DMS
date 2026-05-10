package com.lab.edms.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkflowTemplateStepRepository extends JpaRepository<WorkflowTemplateStep, Long> {
    List<WorkflowTemplateStep> findByTemplateIdOrderByStepOrder(Long templateId);
}
