package com.lab.edms.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.storage.MinioProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 하루치 audit_logs 를 Merkle root + 앵커 JSON 으로 묶어 MinIO(COMPLIANCE 3650d)에 PUT 한 뒤
 * audit_checkpoints 에 INSERT 한다.
 *
 * 순서: Merkle read → MinIO PUT → DB INSERT.
 * 실패 시: AuditAction.WORM_ANCHOR_FAILED 로그, 예외 throw → 호출자가 다음 catchup 으로 처리.
 */
@Service
public class AnchorService {

    private final AnchorRepository anchorRepo;
    private final MinioClientWrapper minio;
    private final MinioProperties minioProps;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AnchorService(AnchorRepository anchorRepo, MinioClientWrapper minio,
                         MinioProperties minioProps, AuditService auditService,
                         ObjectMapper objectMapper) {
        this.anchorRepo = anchorRepo;
        this.minio = minio;
        this.minioProps = minioProps;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * 한 KST 일자에 대해 앵커 1개를 생성·저장.
     * UNIQUE(checkpoint_date) 가 last-line defense — 중복 호출 시 INSERT 단계에서 실패.
     */
    @Transactional
    public AuditCheckpoint buildAndStore(LocalDate kstDate) {
        // 1. audit_logs 조회 (app_role JDBC)
        AnchorRepository.DayWindow win = anchorRepo.findKstDayWindow(kstDate);

        // 2. Merkle root
        String merkleRoot = MerkleCalculator.root(win.thisHashes());

        // 3. prev_anchor_hash
        String prevAnchorHash = anchorRepo.findLatest()
                .map(AuditCheckpoint::anchorHash)
                .orElse(MerkleCalculator.ANCHOR_GENESIS_HASH);

        // 4. anchor_hash
        String anchorHash = AnchorJson.computeAnchorHash(
                prevAnchorHash, merkleRoot, kstDate, win.count(), win.firstId(), win.lastId());

        // 5. JSON 직렬화
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        AnchorJson json = new AnchorJson(
                kstDate, merkleRoot, win.count(), win.firstId(), win.lastId(),
                prevAnchorHash, anchorHash, generatedAt);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(json);
        } catch (Exception e) {
            recordFailure(kstDate, "JSON serialization failed: " + e.getMessage());
            throw new RuntimeException(e);
        }

        String minioKey = String.format("anchors/%04d/%02d/%04d%02d%02d.json",
                kstDate.getYear(), kstDate.getMonthValue(),
                kstDate.getYear(), kstDate.getMonthValue(), kstDate.getDayOfMonth());

        // 6. MinIO PUT (COMPLIANCE 3650d) — 먼저!
        try {
            minio.uploadWithRetention(minioProps.bucketAnchors(), minioKey, payload, "application/json", 3650);
        } catch (Exception e) {
            recordFailure(kstDate, "MinIO upload failed: " + e.getMessage());
            throw e;
        }

        // 7. DB INSERT (audit_role)
        AuditCheckpoint cp = new AuditCheckpoint(
                null, kstDate, merkleRoot, win.count(), win.firstId(), win.lastId(),
                prevAnchorHash, anchorHash, minioKey, generatedAt);
        try {
            anchorRepo.insert(cp);
        } catch (Exception e) {
            recordFailure(kstDate, "DB insert failed (MinIO 객체는 이미 저장됨, 운영자가 audit_checkpoints 수동 보정 필요): "
                    + e.getMessage());
            throw e;
        }

        // 8. Success audit
        auditService.log(AuditEvent.of("system", AuditAction.WORM_ANCHOR_CREATED)
                .entity("audit_checkpoints", kstDate.toString())
                .after(String.format(
                        "{\"merkle_root\":\"%s\",\"record_count\":%d,\"minio_key\":\"%s\",\"anchor_hash\":\"%s\"}",
                        merkleRoot, win.count(), minioKey, anchorHash))
                .build());

        return cp;
    }

    private void recordFailure(LocalDate kstDate, String reason) {
        try {
            auditService.log(AuditEvent.of("system", AuditAction.WORM_ANCHOR_FAILED)
                    .entity("audit_checkpoints", kstDate.toString())
                    .reason(reason)
                    .build());
        } catch (Exception ignore) {
            // 감사로깅 자체가 실패하더라도 메인 예외를 가리지 않음
        }
    }
}
