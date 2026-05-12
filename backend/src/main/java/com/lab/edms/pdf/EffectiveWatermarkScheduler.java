package com.lab.edms.pdf;

import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * M7 PR4: EFFECTIVE 날짜 도달 시 자동으로 EFFECTIVE 워터마크를 적용하는 스케줄러.
 *
 * ShedLock leader election을 통해 다중 인스턴스 환경에서도 단 하나의 인스턴스만 실행한다.
 * 매일 00:05 KST에 실행하며, effectiveDate = 오늘 & pdf_status = STAMPED 인 버전을 처리한다.
 */
@Component
public class EffectiveWatermarkScheduler {

    private static final Logger log = LoggerFactory.getLogger(EffectiveWatermarkScheduler.class);

    private final DocumentVersionRepository versionRepo;
    private final PdfRenditionPipeline pipeline;

    public EffectiveWatermarkScheduler(DocumentVersionRepository versionRepo,
                                        PdfRenditionPipeline pipeline) {
        this.versionRepo = versionRepo;
        this.pipeline = pipeline;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "effectiveWatermark", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void run() {
        // KST로 오늘 날짜 계산 — JVM default(UTC)를 쓰면 00:05 KST에 어제 날짜가 반환됨
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // effectiveDate <= today: 누락된 날도 처리 (= 멱등 catch-up)
        List<DocumentVersion> due = versionRepo.findByEffectiveDateLessThanEqualAndDocumentPdfStatus(
                today, PdfStatus.STAMPED.name());
        log.info("[EFFECTIVE] {} versions due for effective watermark on {}", due.size(), today);
        for (DocumentVersion v : due) {
            try {
                pipeline.applyEffectiveWatermark(v.getId());
            } catch (Exception e) {
                log.error("[EFFECTIVE] Failed for versionId={}", v.getId(), e);
            }
        }
    }
}
