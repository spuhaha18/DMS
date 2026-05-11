package com.lab.edms.audit;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.storage.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.lab.edms.audit.MerkleCalculator.ANCHOR_GENESIS_HASH;
import static com.lab.edms.audit.MerkleCalculator.EMPTY_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class AnchorServiceIT {

    // @DynamicPropertySource 직접 선언 — @Import-ed TestConfiguration 의 DPS 는
    // @DirtiesContext 컨텍스트 재생성 시 MinIO 속성을 신뢰성 있게 전파하지 않음 (Spring Boot 3.3 제한)
    @DynamicPropertySource
    static void minioProps(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",       TestcontainersConfig.MINIO::getS3URL);
        registry.add("minio.access-key",     TestcontainersConfig.MINIO::getUserName);
        registry.add("minio.secret-key",     TestcontainersConfig.MINIO::getPassword);
        registry.add("minio.bucket-anchors", () -> "test-edms-audit-anchors");
    }

    @Autowired AnchorService anchorService;
    @Autowired AnchorRepository anchorRepo;
    @Autowired AuditService auditService;
    @Autowired MinioClient minioClient;
    @Autowired MinioProperties minioProps;
    @Autowired MinioClientWrapper minio;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired @Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbcTemplate;
    @Autowired WormAnchorJob wormAnchorJob;

    @BeforeEach
    void truncateAuditTables() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs RESTART IDENTITY");
        jdbcTemplate.execute("TRUNCATE TABLE audit_checkpoints RESTART IDENTITY");
        minio.ensureBuckets();
    }

    /** OQ-AUD-006 자동화: 단일 앵커 생성 — MinIO 객체 + audit_checkpoints 행 + WORM_ANCHOR_CREATED audit */
    @Test
    void buildAndStore_singleDay_writesMinioAndDb() {
        LocalDate kstDate = LocalDate.of(2026, 1, 15);
        OffsetDateTime kstNoonUtc = kstDate.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        auditService.log(new AuditEvent("alice", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", kstNoonUtc));

        AuditCheckpoint cp = anchorService.buildAndStore(kstDate);

        assertThat(cp.checkpointDate()).isEqualTo(kstDate);
        assertThat(cp.recordCount()).isEqualTo(1L);
        assertThat(cp.merkleRoot()).hasSize(64);
        assertThat(cp.prevAnchorHash()).isEqualTo(ANCHOR_GENESIS_HASH);
        assertThat(cp.anchorHash()).hasSize(64);
        assertThat(cp.minioKey()).isEqualTo("anchors/2026/01/20260115.json");

        // MinIO 객체 존재
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProps.bucketAnchors()).object(cp.minioKey()).build())) {
            byte[] bytes = in.readAllBytes();
            assertThat(bytes.length).isGreaterThan(0);
            assertThat(new String(bytes)).contains("\"merkle_root\"");
        } catch (Exception e) {
            throw new AssertionError("MinIO 객체 미존재", e);
        }

        // WORM_ANCHOR_CREATED audit log
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'WORM_ANCHOR_CREATED'", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    /** 0건 일자: EMPTY merkle, count=0, first/last NULL, 앵커 생성됨 */
    @Test
    void buildAndStore_emptyDay_stillCreatesAnchor() {
        LocalDate kstDate = LocalDate.of(2026, 2, 10);

        AuditCheckpoint cp = anchorService.buildAndStore(kstDate);

        assertThat(cp.recordCount()).isEqualTo(0L);
        assertThat(cp.merkleRoot()).isEqualTo(EMPTY_HASH);
        assertThat(cp.firstLogId()).isNull();
        assertThat(cp.lastLogId()).isNull();
    }

    /** KST 일자 경계: 직전·다음 일자 자정 인근 레코드가 올바른 일자로 분류 */
    @Test
    void kstBoundary_classification() {
        LocalDate dayA = LocalDate.of(2026, 3, 1);
        LocalDate dayB = LocalDate.of(2026, 3, 2);
        OffsetDateTime endOfA = dayA.atTime(23, 59, 59).atZone(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime startOfB = dayB.atStartOfDay(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS, "USER", "1",
                null, null, null, "10.0.0.1", endOfA));
        auditService.log(new AuditEvent("b", AuditAction.USER_LOGIN_SUCCESS, "USER", "2",
                null, null, null, "10.0.0.1", startOfB));

        AuditCheckpoint cpA = anchorService.buildAndStore(dayA);
        AuditCheckpoint cpB = anchorService.buildAndStore(dayB);

        assertThat(cpA.recordCount()).isEqualTo(1L);
        assertThat(cpB.recordCount()).isEqualTo(1L);
    }

    /** 앵커끼리 체인: dayB.prev_anchor_hash == dayA.anchor_hash */
    @Test
    void anchorChain_isLinked() {
        LocalDate dayA = LocalDate.of(2026, 4, 1);
        LocalDate dayB = LocalDate.of(2026, 4, 2);

        AuditCheckpoint cpA = anchorService.buildAndStore(dayA);
        AuditCheckpoint cpB = anchorService.buildAndStore(dayB);

        assertThat(cpA.prevAnchorHash()).isEqualTo(ANCHOR_GENESIS_HASH);
        assertThat(cpB.prevAnchorHash()).isEqualTo(cpA.anchorHash());
    }

    /** 중복 호출 → UNIQUE(checkpoint_date) 충돌 */
    @Test
    void duplicate_call_violates_unique() {
        LocalDate kstDate = LocalDate.of(2026, 5, 5);
        anchorService.buildAndStore(kstDate);

        assertThatThrownBy(() -> anchorService.buildAndStore(kstDate))
                .isInstanceOfAny(DataIntegrityViolationException.class, RuntimeException.class)
                .hasMessageContaining("checkpoint_date");
    }

    /** WormAnchorJob.runCatchup: 빈 상태에서 audit_log 가 있으면 어제(KST)까지 모두 catchup */
    @Test
    void wormAnchorJob_catchup_fillsAllMissingDays() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        LocalDate yesterday = LocalDate.now(kst).minusDays(1);
        LocalDate dayBefore = yesterday.minusDays(1);
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS, "USER", "1",
                null, null, null, "10",
                dayBefore.atTime(12, 0).atZone(kst).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()));
        auditService.log(new AuditEvent("b", AuditAction.USER_LOGIN_SUCCESS, "USER", "2",
                null, null, null, "10",
                yesterday.atTime(12, 0).atZone(kst).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()));

        wormAnchorJob.runCatchup();

        Optional<AuditCheckpoint> dayBeforeCp = anchorRepo.findByDate(dayBefore);
        Optional<AuditCheckpoint> yesterdayCp = anchorRepo.findByDate(yesterday);
        assertThat(dayBeforeCp).isPresent();
        assertThat(yesterdayCp).isPresent();
        assertThat(yesterdayCp.get().prevAnchorHash()).isEqualTo(dayBeforeCp.get().anchorHash());
    }

    /** runCatchup 중복 호출 — 두 번 연속 호출해도 예외 없이 통과 */
    @Test
    void runCatchup_advisory_lock_prevents_double() {
        wormAnchorJob.runCatchup();
        wormAnchorJob.runCatchup();
    }
}
