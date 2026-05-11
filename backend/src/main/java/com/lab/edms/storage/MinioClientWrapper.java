package com.lab.edms.storage;

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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MinioClientWrapper {

    private final MinioClient minio;
    private final MinioProperties props;
    private final AtomicBoolean bucketsEnsured = new AtomicBoolean(false);

    public MinioClientWrapper(MinioClient minio, MinioProperties props) {
        this.minio = minio;
        this.props = props;
    }

    /**
     * Ensures document buckets (no lock) + anchor bucket (COMPLIANCE 10y).
     * Lazy on first use — avoids eager MinIO connection at context startup.
     */
    public void ensureBuckets() {
        if (bucketsEnsured.compareAndSet(false, true)) {
            ensureBucket(props.bucketOriginal());
            ensureBucket(props.bucketRendition());
            ensureLockedBucket(props.bucketAnchors(), RetentionMode.COMPLIANCE, 3650);
        }
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

    public InputStream openStream(String bucket, String key) {
        ensureBuckets();
        try {
            return minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO getObject failed: " + key, e);
        }
    }
}
