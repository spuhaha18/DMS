package com.lab.edms.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@Profile("!smtp")
public class LogEmailNotificationService implements EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LogEmailNotificationService.class);

    @Override
    public void sendInitialPassword(String toEmail, String userId, String tempPassword, boolean forceChangePw) {
        log.info("EMAIL[INITIAL_PW]: to={}, userId={}, forceChangePw={}, tempLen={}",
                toEmail, userId, forceChangePw, tempPassword == null ? 0 : tempPassword.length());
    }

    @Override
    public void sendAuditorExpiryWarning(String toEmail, String userId, LocalDate validUntil) {
        log.info("EMAIL[AUDITOR_EXPIRY_WARNING]: to={}, userId={}, validUntil={}",
                toEmail, userId, validUntil);
    }

    @Override
    public void sendPasswordReset(String toEmail, String userId, String tempPassword) {
        log.info("EMAIL[PW_RESET]: to={}, userId={}, tempLen={}",
                toEmail, userId, tempPassword == null ? 0 : tempPassword.length());
    }
}
