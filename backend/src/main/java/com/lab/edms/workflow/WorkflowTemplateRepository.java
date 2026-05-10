package com.lab.edms.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, Long> {
    Optional<WorkflowTemplate> findByCategoryId(Long categoryId);
    Optional<WorkflowTemplate> findByCategoryIdAndActiveTrue(Long categoryId);
}
