package com.lab.edms.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.audit.dto.VerifyResponse;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.storage.MinioProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 4종 검증 합산:
 *   (1) 앵커 체인       : audit_checkpoints.prev_anchor_hash 가 이전 행의 anchor_hash 와 일치
 *   (2) audit_logs 체인 : 각 행의 this_hash == SHA-256(prev_hash || canonical payload)
 *   (3) Merkle 재계산   : 각 일자의 this_hash 들로 Merkle root 재계산 후 audit_checkpoints.merkle_root 일치
 *   (4) MinIO 객체 SHA  : 각 일자의 MinIO 파일 다운로드 → anchor_hash 재계산 일치
 *
 * 첫 깨진 지점에서 단락 (first_broken_id 채움).
 */
@Component
public class AuditChainVerifier {

    private final AnchorRepository anchorRepo;
    private final JdbcTemplate jdbcTemplate;
    private final MinioClientWrapper minio;
    private final MinioProperties minioProps;
    private final ObjectMapper objectMapper;

    public AuditChainVerifier(AnchorRepository anchorRepo, JdbcTemplate jdbcTemplate,
                              MinioClientWrapper minio, MinioProperties minioProps,
                              ObjectMapper objectMapper) {
        this.anchorRepo = anchorRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.minio = minio;
        this.minioProps = minioProps;
        this.objectMapper = objectMapper;
    }

    public VerifyResponse verify(LocalDate from, LocalDate to) {
        List<AuditCheckpoint> checkpoints = anchorRepo.findByRange(from, to);
        if (checkpoints.isEmpty()) {
            return new VerifyResponse(false, 0, null,
                    "범위 내 audit_checkpoints 없음: " + from + " ~ " + to);
        }

        long totalChecked = 0;

        String expectedPrevAnchor = checkpoints.get(0).prevAnchorHash();
        Long checkpointBeforeRangeId = jdbcTemplate.query(
                "SELECT id FROM audit_checkpoints WHERE checkpoint_date < ? "
              + "ORDER BY checkpoint_date DESC LIMIT 1",
                (rs, rn) -> rs.getLong("id"), from)
                .stream().findFirst().orElse(null);
        if (checkpointBeforeRangeId == null && !expectedPrevAnchor.equals(MerkleCalculator.ANCHOR_GENESIS_HASH)) {
            return new VerifyResponse(false, 0, checkpoints.get(0).id(),
                    "범위 첫 앵커의 prev_anchor_hash 가 ANCHOR_GENESIS 가 아님 (범위 이전 앵커 없음)");
        }

        for (int i = 0; i < checkpoints.size(); i++) {
            AuditCheckpoint cp = checkpoints.get(i);

            // (1) 앵커 체인
            if (i > 0) {
                AuditCheckpoint prev = checkpoints.get(i - 1);
                if (!cp.prevAnchorHash().equals(prev.anchorHash())) {
                    return new VerifyResponse(false, totalChecked, cp.id(),
                            "앵커 체인 단절: " + cp.checkpointDate() + ".prev_anchor_hash ≠ "
                                    + prev.checkpointDate() + ".anchor_hash");
                }
            }
            // anchor_hash 자체 재계산
            String recomputedAnchor = AnchorJson.computeAnchorHash(
                    cp.prevAnchorHash(), cp.merkleRoot(), cp.checkpointDate(),
                    cp.recordCount(), cp.firstLogId(), cp.lastLogId());
            if (!recomputedAnchor.equals(cp.anchorHash())) {
                return new VerifyResponse(false, totalChecked, cp.id(),
                        "anchor_hash 재계산 불일치 — " + cp.checkpointDate() + " 의 메타데이터 변조 의심");
            }

            // (2) audit_logs 체인 + (3) Merkle 재계산
            AnchorRepository.DayWindow win = anchorRepo.findKstDayWindow(cp.checkpointDate());
            if (win.count() != cp.recordCount()) {
                return new VerifyResponse(false, totalChecked, cp.id(),
                        "record_count 불일치: DB=" + win.count() + ", anchor=" + cp.recordCount());
            }
            String recomputedMerkle = MerkleCalculator.root(win.thisHashes());
            if (!recomputedMerkle.equals(cp.merkleRoot())) {
                return new VerifyResponse(false, totalChecked, cp.id(),
                        "Merkle root 불일치 — " + cp.checkpointDate() + " 의 audit_logs 변조 의심");
            }

            Long firstBrokenLogId = verifyLogChain(cp.checkpointDate());
            if (firstBrokenLogId != null) {
                return new VerifyResponse(false, totalChecked, firstBrokenLogId,
                        "audit_logs 행 단위 해시 불일치 (id=" + firstBrokenLogId + ")");
            }

            // (4) MinIO 객체 검증
            String minioErr = verifyMinioObject(cp);
            if (minioErr != null) {
                return new VerifyResponse(false, totalChecked, cp.id(), minioErr);
            }

            totalChecked += cp.recordCount();
        }

        return new VerifyResponse(true, totalChecked, null,
                "검증 통과 — " + checkpoints.size() + "일, " + totalChecked + " 레코드");
    }

    /** audit_logs 각 행의 this_hash 를 prev_hash + canonical payload 재계산해 비교. 첫 실패 id 반환. */
    private Long verifyLogChain(LocalDate kstDate) {
        List<Object[]> rows = jdbcTemplate.query(
                "SELECT id, actor_user_id, action, entity_type, entity_id, "
              + "       before_value::text, after_value::text, reason, client_ip, "
              + "       server_ts, prev_hash, this_hash "
              + "FROM audit_logs "
              + "WHERE server_ts >= (?::date)::timestamp AT TIME ZONE 'Asia/Seoul' "
              + "  AND server_ts <  ((?::date) + INTERVAL '1 day')::timestamp AT TIME ZONE 'Asia/Seoul' "
              + "ORDER BY id",
                (rs, rn) -> new Object[]{
                        rs.getLong("id"),
                        rs.getString("actor_user_id"),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        rs.getString("before_value"),
                        rs.getString("after_value"),
                        rs.getString("reason"),
                        rs.getString("client_ip"),
                        rs.getTimestamp("server_ts"),
                        rs.getString("prev_hash"),
                        rs.getString("this_hash")
                },
                kstDate.toString(), kstDate.toString());

        for (Object[] row : rows) {
            Long id = (Long) row[0];
            AuditEvent e = new AuditEvent(
                    (String) row[1],
                    AuditAction.valueOf((String) row[2]),
                    (String) row[3],
                    (String) row[4],
                    (String) row[5],
                    (String) row[6],
                    (String) row[7],
                    (String) row[8],
                    ((java.sql.Timestamp) row[9]).toInstant().atOffset(ZoneOffset.UTC)
            );
            String payload = HashChainSerializer.payload((String) row[10], e);
            String recomputed = HashChainSerializer.sha256Hex(payload);
            if (!recomputed.equals(row[11])) return id;
        }
        return null;
    }

    /** MinIO 객체를 다운로드해 anchor_hash 가 동일한지 확인. null = OK. */
    private String verifyMinioObject(AuditCheckpoint cp) {
        try (InputStream in = minio.openStream(minioProps.bucketAnchors(), cp.minioKey())) {
            byte[] bytes = in.readAllBytes();
            AnchorJson json = objectMapper.readValue(bytes, AnchorJson.class);
            if (!json.anchorHash().equals(cp.anchorHash())) {
                return "MinIO anchor.json 의 anchor_hash 가 DB 와 불일치 — " + cp.checkpointDate();
            }
            String recomputed = AnchorJson.computeAnchorHash(
                    json.prevAnchorHash(), json.merkleRoot(), json.date(),
                    json.recordCount(), json.firstId(), json.lastId());
            if (!recomputed.equals(json.anchorHash())) {
                return "MinIO anchor.json 내부 anchor_hash 재계산 실패 — " + cp.checkpointDate();
            }
            return null;
        } catch (Exception e) {
            return "MinIO 객체 읽기 실패 (" + cp.minioKey() + "): " + e.getMessage();
        }
    }
}
