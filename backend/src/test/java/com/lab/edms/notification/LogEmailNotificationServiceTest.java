package com.lab.edms.notification;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

class LogEmailNotificationServiceTest {

    @Test
    void sendInitialPassword_logsAtInfoLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(LogEmailNotificationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        new LogEmailNotificationService().sendInitialPassword(
                "ann@lab.test", "ann", "Tmp!2026Pwd9876", true);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage()).contains("ann@lab.test");
            assertThat(e.getFormattedMessage()).doesNotContain("Tmp!2026Pwd9876");
        });
    }
}
