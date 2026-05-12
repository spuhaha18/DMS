package com.lab.edms.pdf;

import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 애플리케이션 시작 시 pdf_status=STAMPING 상태로 남은 orphan 레코드를 재처리한다.
 * sign() 커밋 후 afterCommit 훅 실행 전 크래시 시 발생하는 F5 시나리오 대응.
 *
 * 현재는 STAMP_FAILED로 마킹만 수행한다.
 * PR3에서 sign_intents 기반 재처리로 교체 예정.
 */
@Component
public class StampWorkerReaper {

    private static final Logger log = LoggerFactory.getLogger(StampWorkerReaper.class);

    private final DocumentRepository documentRepository;
    private final PdfRenditionPipeline pipeline;

    public StampWorkerReaper(DocumentRepository documentRepository, PdfRenditionPipeline pipeline) {
        this.documentRepository = documentRepository;
        this.pipeline = pipeline;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reapOrphanStamping() {
        List<Document> orphans = documentRepository.findByPdfStatus(PdfStatus.STAMPING.name());
        if (orphans.isEmpty()) {
            return;
        }
        log.warn("[StampWorkerReaper] Found {} orphan STAMPING documents at startup", orphans.size());
        for (Document doc : orphans) {
            try {
                log.info("[StampWorkerReaper] Reaping orphan document={}", doc.getId());
                // STAMP_FAILED로 마킹 — PR3에서 sign_intents 기반 재처리로 교체
                doc.setPdfStatus(PdfStatus.STAMP_FAILED.name());
                doc.setPdfStatusReason("ORPHAN_REAPER");
                documentRepository.save(doc);
            } catch (Exception e) {
                log.error("[StampWorkerReaper] Failed to reap document={}", doc.getId(), e);
            }
        }
    }
}
