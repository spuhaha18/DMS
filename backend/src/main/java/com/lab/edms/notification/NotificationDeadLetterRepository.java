package com.lab.edms.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeadLetterRepository extends JpaRepository<NotificationDeadLetter, Long> {
}
