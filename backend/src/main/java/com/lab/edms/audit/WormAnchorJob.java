package com.lab.edms.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 매일 01:00 KST 실행. 마지막 checkpoint_date 다음 날부터 어제(KST)까지 누락된 일자를 순차 catchup.
 *
 * 이중 실행 방지: pg_try_advisory_lock(WORM_ANCHOR_LOCK_KEY).
 * 실패 시: 인-함수 재시도 없음, 다음 cron 틱에서 catchup.
 */
@Component
public class WormAnchorJob {

    private static final Logger log = LoggerFactory.getLogger(WormAnchorJob.class);
    static final long WORM_ANCHOR_LOCK_KEY = 8_888_888_888L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;
    private final AnchorRepository anchorRepo;
    private final AnchorService anchorService;

    public WormAnchorJob(JdbcTemplate jdbcTemplate, AnchorRepository anchorRepo, AnchorService anchorService) {
        this.jdbcTemplate = jdbcTemplate;
        this.anchorRepo = anchorRepo;
        this.anchorService = anchorService;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void runDailyTick() {
        runCatchup();
    }

    /** 패키지-가시성 — 테스트에서 수동 트리거. */
    void runCatchup() {
        Boolean got = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)", Boolean.class, WORM_ANCHOR_LOCK_KEY);
        if (got == null || !got) {
            log.info("WormAnchorJob: another instance holds the advisory lock, skipping tick");
            return;
        }
        try {
            LocalDate kstYesterday = LocalDate.now(KST).minusDays(1);
            Optional<LocalDate> startOpt = computeStartDate();
            if (startOpt.isEmpty()) {
                log.info("WormAnchorJob: no audit_logs yet, nothing to anchor");
                return;
            }
            LocalDate cursor = startOpt.get();
            while (!cursor.isAfter(kstYesterday)) {
                try {
                    anchorService.buildAndStore(cursor);
                    log.info("WormAnchorJob: anchored {}", cursor);
                } catch (Exception e) {
                    log.error("WormAnchorJob: failed to anchor {} — will retry next tick", cursor, e);
                    return;
                }
                cursor = cursor.plusDays(1);
            }
        } finally {
            jdbcTemplate.execute("SELECT pg_advisory_unlock(" + WORM_ANCHOR_LOCK_KEY + ")");
        }
    }

    /**
     * 다음 catchup 시작 일자.
     * - 기존 checkpoint 가 있으면 MAX(checkpoint_date) + 1
     * - 없으면 audit_logs 의 가장 이른 KST 일자
     * - audit_logs 도 비어 있으면 Optional.empty (catchup 없음)
     */
    private Optional<LocalDate> computeStartDate() {
        Optional<AuditCheckpoint> latest = anchorRepo.findLatest();
        if (latest.isPresent()) return Optional.of(latest.get().checkpointDate().plusDays(1));
        return anchorRepo.findEarliestKstAuditDate();
    }
}
