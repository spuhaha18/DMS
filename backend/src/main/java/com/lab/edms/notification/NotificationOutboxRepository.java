package com.lab.edms.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query(value = "SELECT * FROM notification_outbox WHERE status IN ('PENDING','FAILED') AND next_attempt_at <= :now ORDER BY created_at ASC LIMIT 100", nativeQuery = true)
    List<NotificationOutbox> findDueForDispatch(@Param("now") OffsetDateTime now);

    List<NotificationOutbox> findByStatus(String status);
}
