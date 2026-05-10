package com.lab.edms.numbering;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NumberingTemplateRepository extends JpaRepository<NumberingTemplate, Long> {
    Optional<NumberingTemplate> findByCategoryId(Long categoryId);
}
