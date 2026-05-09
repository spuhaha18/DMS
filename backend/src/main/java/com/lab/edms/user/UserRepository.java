package com.lab.edms.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles ur LEFT JOIN FETCH ur.role WHERE u.userId = :userId")
    Optional<User> findByUserIdWithRoles(@Param("userId") String userId);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUserId(String userId);

    @Query("""
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:department IS NULL OR u.department = :department)
            ORDER BY u.userId
            """)
    Page<User> searchAdmin(@Param("status") UserStatus status,
                           @Param("department") String department,
                           Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.validUntil IS NOT NULL AND u.validUntil < CURRENT_DATE AND u.status = 'ACTIVE'")
    List<User> findExpiredAuditors();

    @Query("SELECT u FROM User u WHERE u.validUntil IS NOT NULL AND u.validUntil = :targetDate AND u.status = 'ACTIVE'")
    List<User> findUsersExpiringOn(@Param("targetDate") java.time.LocalDate targetDate);
}
