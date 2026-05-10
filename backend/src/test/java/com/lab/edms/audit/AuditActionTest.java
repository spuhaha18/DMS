package com.lab.edms.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionTest {

    @Test
    void M4_액션_직렬화_라운드트립() {
        for (AuditAction action : AuditAction.values()) {
            assertThat(AuditAction.valueOf(action.name())).isEqualTo(action);
        }
    }
}
