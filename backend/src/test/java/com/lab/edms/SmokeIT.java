package com.lab.edms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SmokeIT {

    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
