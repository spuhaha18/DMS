package com.lab.edms.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query("SELECT o FROM NotificationOutbox o WHERE o.status IN ('PENDING','FAILED') AND o.nextAttemptAt <= :now ORDER BY o.createdAt ASC LIMIT 100")
    List<NotificationOutbox> findDueForDispatch(@Param("now") OffsetDateTime now);

    List<NotificationOutbox> findByStatus(String status);
}
