package com.lab.edms.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RetentionExtensionWorker {

    private static final Logger log = LoggerFactory.getLogger(RetentionExtensionWorker.class);
    private static final int BATCH = 50;

    private final RetentionExtensionOutboxRepository repo;
    private final RetentionExtensionProcessor processor;
    private final String workerId = "worker-" + UUID.randomUUID();

    public RetentionExtensionWorker(RetentionExtensionOutboxRepository repo,
                                    RetentionExtensionProcessor processor) {
        this.repo = repo;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${retention.outbox.poll-ms:10000}")
    public void processPending() {
        List<Long> claimed = repo.claimBatch(BATCH, workerId);
        if (claimed.isEmpty()) return;
        log.debug("retention.worker claimed={} worker={}", claimed.size(), workerId);
        for (Long id : claimed) {
            processor.processOne(id);
        }
    }
}
