package com.lab.edms.storage;

import io.minio.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
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
     * Ensures both buckets exist. Called lazily on first use to avoid
     * eager connection failures at context startup (e.g. in tests that
     * don't exercise MinIO but load the full application context).
     */
    public void ensureBuckets() {
        if (bucketsEnsured.compareAndSet(false, true)) {
            ensureBucket(props.bucketOriginal());
            ensureBucket(props.bucketRendition());
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

    public InputStream openStream(String bucket, String key) {
        ensureBuckets();
        try {
            return minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO getObject failed: " + key, e);
        }
    }
}
