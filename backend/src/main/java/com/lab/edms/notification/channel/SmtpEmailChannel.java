package com.lab.edms.notification.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.notification.NotificationOutbox;
import com.lab.edms.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true", matchIfMissing = false)
public class SmtpEmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailChannel.class);

    private final JavaMailSender mailSender;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.username:noreply@edms.local}")
    private String fromAddress;

    @Value("${notification.email.enabled:false}")
    private boolean emailEnabled;

    public SmtpEmailChannel(JavaMailSender mailSender,
                             UserRepository userRepo,
                             ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() { return "EMAIL_SMTP"; }

    @Override
    public boolean enabled() { return emailEnabled; }

    @Override
    public DeliveryResult deliver(NotificationOutbox row) {
        try {
            // Resolve recipient email
            var user = userRepo.findById(row.getRecipientId()).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return DeliveryResult.fail("Recipient not found or has no email: " + row.getRecipientId());
            }

            // Parse payload
            Map<String, Object> payload = objectMapper.readValue(
                    row.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
            String title = (String) payload.getOrDefault("title", "EDMS 알림");
            String body = (String) payload.getOrDefault("body", "");
            String linkPath = (String) payload.getOrDefault("linkPath", "");

            // Build HTML content
            String html = buildHtml(title, body, linkPath);

            // Send
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(user.getEmail());
            helper.setSubject("[EDMS] " + title);
            helper.setText(html, true);
            mailSender.send(message);

            return DeliveryResult.ok();
        } catch (Exception e) {
            log.warn("SMTP delivery failed for outbox={}: {}", row.getId(), e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }

    private String buildHtml(String title, String body, String linkPath) {
        String linkSection = (linkPath != null && !linkPath.isBlank())
                ? "<p><a href=\"" + linkPath + "\" style=\"color:#1a73e8;\">바로가기</a></p>"
                : "";
        return "<!DOCTYPE html><html lang=\"ko\"><body style=\"font-family:sans-serif;color:#333;max-width:600px;margin:0 auto;\">" +
               "<h2 style=\"color:#1a73e8;\">" + escapeHtml(title) + "</h2>" +
               (body != null && !body.isBlank() ? "<p>" + escapeHtml(body) + "</p>" : "") +
               linkSection +
               "<hr style=\"margin-top:32px;\"/>" +
               "<small style=\"color:#999;\">본 메일은 EDMS 시스템에서 자동으로 발송되었습니다. 회신하지 마세요.</small>" +
               "</body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }
}
