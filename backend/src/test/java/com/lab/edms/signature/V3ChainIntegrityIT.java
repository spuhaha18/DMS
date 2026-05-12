package com.lab.edms.signature;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V22 마이그레이션 스모크 테스트:
 * v_signature_chain_integrity 뷰가 정상 생성되었고, v1/v2/v3 컬럼이 존재함을 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class V3ChainIntegrityIT {

    @Autowired
    DataSource dataSource;

    @Test
    void viewExists() throws Exception {
        try (var conn = dataSource.getConnection();
             var rs = conn.createStatement().executeQuery(
                 "SELECT COUNT(*) FROM v_signature_chain_integrity")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void viewHasV3Column() throws Exception {
        try (var conn = dataSource.getConnection();
             var rs = conn.createStatement().executeQuery(
                 "SELECT v3_rows FROM v_signature_chain_integrity LIMIT 1")) {
            // 쿼리 자체가 오류 없이 실행되면 v3_rows 컬럼 존재 확인
            assertThat(rs).isNotNull();
        }
    }

    @Test
    void shedlockTableExists() throws Exception {
        try (var conn = dataSource.getConnection();
             var rs = conn.createStatement().executeQuery(
                 "SELECT COUNT(*) FROM shedlock")) {
            assertThat(rs.next()).isTrue();
        }
    }
}
