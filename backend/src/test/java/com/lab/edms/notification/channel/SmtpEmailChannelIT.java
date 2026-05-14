package com.lab.edms.notification.channel;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.lab.edms.notification.NotificationOutbox;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SmtpEmailChannelIT {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    private SmtpEmailChannel channel;
    private UserRepository userRepo;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(greenMail.getSmtp().getPort());
        // No auth for GreenMail test

        channel = new SmtpEmailChannel(mailSender, userRepo, new ObjectMapper());
        // Set @Value fields via reflection
        try {
            var fromField = SmtpEmailChannel.class.getDeclaredField("fromAddress");
            fromField.setAccessible(true);
            fromField.set(channel, "noreply@edms.test");
            var enabledField = SmtpEmailChannel.class.getDeclaredField("emailEnabled");
            enabledField.setAccessible(true);
            enabledField.set(channel, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void deliver_success_receives_email_in_greenmail() throws Exception {
        // Arrange
        User user = new User();
        user.setEmail("reviewer@edms.test");
        when(userRepo.findById(42L)).thenReturn(Optional.of(user));

        NotificationOutbox row = buildOutbox(42L, "WORKFLOW_SUBMITTED",
                "{\"title\":\"결재 요청: 테스트 문서\",\"documentId\":1,\"documentVersionId\":1}");

        // Act
        DeliveryResult result = channel.deliver(row);

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        assertThat(greenMail.getReceivedMessages()[0].getSubject()).contains("결재 요청");
    }

    @Test
    void deliver_fail_when_wrong_host() {
        // Arrange — channel with wrong host (closed port)
        JavaMailSenderImpl badMailSender = new JavaMailSenderImpl();
        badMailSender.setHost("localhost");
        badMailSender.setPort(19999); // wrong port
        badMailSender.getJavaMailProperties().setProperty("mail.smtp.timeout", "500");
        badMailSender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "500");

        SmtpEmailChannel badChannel = new SmtpEmailChannel(badMailSender, userRepo, new ObjectMapper());
        try {
            var fromField = SmtpEmailChannel.class.getDeclaredField("fromAddress");
            fromField.setAccessible(true);
            fromField.set(badChannel, "noreply@edms.test");
            var enabledField = SmtpEmailChannel.class.getDeclaredField("emailEnabled");
            enabledField.setAccessible(true);
            enabledField.set(badChannel, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        User user = new User();
        user.setEmail("reviewer@edms.test");
        when(userRepo.findById(42L)).thenReturn(Optional.of(user));

        NotificationOutbox row = buildOutbox(42L, "WORKFLOW_SUBMITTED",
                "{\"title\":\"테스트\"}");

        // Act
        DeliveryResult result = badChannel.deliver(row);

        // Assert
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
    }

    private NotificationOutbox buildOutbox(Long recipientId, String eventCode, String payload) {
        NotificationOutbox o = new NotificationOutbox();
        o.setRecipientId(recipientId);
        o.setEventCode(eventCode);
        o.setChannel("EMAIL_SMTP");
        o.setPayloadJson(payload);
        o.setStatus("PENDING");
        o.setAttemptCount(0);
        return o;
    }
}
