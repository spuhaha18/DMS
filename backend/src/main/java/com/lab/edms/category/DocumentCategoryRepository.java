package com.lab.edms.category;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {
    Optional<DocumentCategory> findByCategoryCode(String categoryCode);
}
