package com.lab.edms.notification;

public interface EmailNotificationService {
    void sendInitialPassword(String toEmail, String userId, String tempPassword, boolean forceChangePw);
    void sendAuditorExpiryWarning(String toEmail, String userId, java.time.LocalDate validUntil);
    void sendPasswordReset(String toEmail, String userId, String tempPassword);
}
