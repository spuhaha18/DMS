package com.lab.edms.numbering;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface NumberingCounterRepository extends JpaRepository<NumberingCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM NumberingCounter c WHERE c.categoryId = :catId AND c.scopeKey = :key")
    Optional<NumberingCounter> findForUpdate(@Param("catId") Long catId, @Param("key") String key);

    Optional<NumberingCounter> findByCategoryIdAndScopeKey(Long categoryId, String scopeKey);
}
