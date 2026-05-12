package com.lab.edms.storage;

import com.lab.edms.document.DocumentVersion;
import io.minio.*;
import io.minio.messages.ObjectLockConfiguration;
import io.minio.messages.Retention;
import io.minio.messages.RetentionDurationDays;
import io.minio.messages.RetentionMode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MinioClientWrapper {

    /**
     * M7 컷오버 기준 시각: 이 시각 이후 생성된 버전은 bucketOriginalV2 로 라우팅.
     * TODO: application.yml 의 minio.m7-cutover-instant 로 오버라이드 권장.
     */
    public static final Instant M7_CUTOVER_INSTANT = Instant.parse("2026-05-12T00:00:00Z");

    private final MinioClient minio;
    private final MinioProperties props;
    private final AtomicBoolean bucketsEnsured = new AtomicBoolean(false);

    public MinioClientWrapper(MinioClient minio, MinioProperties props) {
        this.minio = minio;
        this.props = props;
    }

    /**
     * Ensures all buckets:
     *   - bucketOriginal   : legacy (no lock, read-only after M7 cutover)
     *   - bucketOriginalV2 : M7 신규 GOVERNANCE 본문 (10년 = 3650일)
     *   - bucketRendition  : M7 신규 GOVERNANCE PDF rendition (10년 = 3650일)
     *   - bucketAnchors    : M5 COMPLIANCE 10년 앵커
     * Lazy on first use — avoids eager MinIO connection at context startup.
     */
    public void ensureBuckets() {
        if (bucketsEnsured.compareAndSet(false, true)) {
            ensureBucket(props.bucketOriginal());
            ensureLockedBucket(props.bucketOriginalV2(), RetentionMode.GOVERNANCE, 3650);
            ensureLockedBucket(props.bucketRendition(), RetentionMode.GOVERNANCE, 3650);
            ensureLockedBucket(props.bucketAnchors(), RetentionMode.COMPLIANCE, 3650);
        }
    }

    /**
     * M7 컷오버 기준으로 업로드 대상 버킷을 결정한다.
     * - version == null 또는 createdAt == null : legacy 버킷
     * - createdAt 이 M7_CUTOVER_INSTANT 이전 : legacy 버킷
     * - 그 외 : bucketOriginalV2
     */
    public String getOriginalBucket(DocumentVersion version) {
        if (version == null || version.getCreatedAt() == null) {
            return props.bucketOriginal();
        }
        Instant createdAtInstant = version.getCreatedAt().toInstant();
        if (createdAtInstant.isBefore(M7_CUTOVER_INSTANT)) {
            return props.bucketOriginal();
        }
        return props.bucketOriginalV2();
    }

    private void ensureBucket(String name) {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(name).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(name).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot ensure MinIO bucket: " + name, e);
        }
    }

    /**
     * Ensures a bucket exists with Object Lock enabled in the given mode.
     * Object Lock must be enabled at bucket creation — it cannot be added later.
     * If the bucket already exists, verifies its lock configuration (fail-fast).
     */
    public void ensureLockedBucket(String name, RetentionMode mode, int defaultRetentionDays) {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(name).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(name).objectLock(true).build());
                minio.setObjectLockConfiguration(
                        SetObjectLockConfigurationArgs.builder()
                                .bucket(name)
                                .config(new ObjectLockConfiguration(mode, new RetentionDurationDays(defaultRetentionDays)))
                                .build());
            } else {
                verifyLockConfiguration(name, mode);
            }
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot ensure locked MinIO bucket: " + name, e);
        }
    }

    /**
     * Fails fast if the bucket does not have Object Lock enabled in the expected mode.
     * Guards against misconfigured buckets entering production.
     */
    public void verifyLockConfiguration(String name, RetentionMode expectedMode) {
        try {
            ObjectLockConfiguration cfg = minio.getObjectLockConfiguration(
                    GetObjectLockConfigurationArgs.builder().bucket(name).build());
            if (cfg == null || cfg.mode() == null) {
                throw new IllegalStateException("Bucket " + name + " has no Object Lock configuration");
            }
            if (cfg.mode() != expectedMode) {
                throw new IllegalStateException("Bucket " + name + " Object Lock mode is "
                        + cfg.mode() + ", expected " + expectedMode);
            }
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot read Object Lock configuration on bucket: " + name
                  + " (created without --with-lock?)", e);
        }
    }

    public record UploadResult(String bucket, String key, long sizeBytes, String sha256) {}

    /**
     * Streams src to MinIO while computing SHA-256 via DigestInputStream.
     * Does NOT close src — caller's responsibility.
     */
    public UploadResult uploadStreaming(String bucket, String key, InputStream src,
                                        long size, String contentType) {
        ensureBuckets();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream dis = new DigestInputStream(src, digest);
            minio.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(dis, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return new UploadResult(bucket, key, size, sb.toString());
        } catch (Exception e) {
            throw new RuntimeException("MinIO upload failed: " + key, e);
        }
    }

    /**
     * Uploads bytes to a locked bucket with explicit per-object COMPLIANCE retention.
     * Returns SHA-256 of the uploaded bytes for later verification.
     */
    public UploadResult uploadWithRetention(String bucket, String key, byte[] data,
                                            String contentType, int retentionDays) {
        ensureBuckets();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));

            ZonedDateTime until = ZonedDateTime.now(ZoneOffset.UTC).plusDays(retentionDays);

            minio.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType != null ? contentType : "application/json")
                            .retention(new Retention(RetentionMode.COMPLIANCE, until))
                            .build()
            );
            return new UploadResult(bucket, key, data.length, sb.toString());
        } catch (Exception e) {
            throw new RuntimeException("MinIO locked upload failed: " + key, e);
        }
    }

    /** PDF rendition 버킷 이름을 반환한다 (M7 신규 GOVERNANCE 버킷). */
    public String getBucketRendition() {
        return props.bucketRendition();
    }

    public InputStream openStream(String bucket, String key) {
        ensureBuckets();
        try {
            return minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO getObject failed: " + key, e);
        }
    }
}
