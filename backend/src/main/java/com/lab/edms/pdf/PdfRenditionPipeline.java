package com.lab.edms.pdf;

import com.lab.edms.document.*;
import com.lab.edms.storage.MinioClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;

/**
 * M7 PR1: INITIAL PDF 변환 파이프라인.
 *
 * 상태 전이:
 *   (null / PENDING_CONVERSION) → [enqueueInitialConversion]
 *       → PENDING_CONVERSION  (트랜잭션 커밋 후 비동기 실행)
 *       → CONVERTED           (변환·업로드 성공)
 *       → CONVERSION_FAILED   (UnsupportedFormatException 또는 기타 오류)
 */
@Service
public class PdfRenditionPipeline {

    private static final Logger log = LoggerFactory.getLogger(PdfRenditionPipeline.class);

    /** PDF rendition 저장 버킷 */
    private static final String RENDITION_FILE_TYPE = "RENDITION";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentFileRepository documentFileRepository;
    private final MinioClientWrapper minio;
    private final GotenbergClient gotenberg;

    // Self-injection to ensure @Async goes through the Spring AOP proxy, not this.method()
    @Lazy @Autowired
    private PdfRenditionPipeline self;

    public PdfRenditionPipeline(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentFileRepository documentFileRepository,
            MinioClientWrapper minio,
            GotenbergClient gotenberg) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentFileRepository = documentFileRepository;
        this.minio = minio;
        this.gotenberg = gotenberg;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * 문서의 최신 버전을 PENDING_CONVERSION으로 세팅하고 비동기 변환을 enqueue한다.
     * 호출자 트랜잭션에서 커밋된 후 비동기 워커가 실행되도록 트랜잭션 내부에서 상태를 기록한다.
     */
    @Transactional
    public void enqueueInitialConversion(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        doc.setPdfStatus(PdfStatus.PENDING_CONVERSION.name());
        doc.setPdfStatusUpdatedAt(Instant.now());
        doc.setPdfStatusReason(null);
        documentRepository.save(doc);

        log.info("[PDF] document={} PENDING_CONVERSION enqueued", documentId);

        // self 프록시를 통해 호출해야 @Async AOP가 적용됨 (this.method() 는 프록시 우회)
        self.runConversionAsync(documentId);
    }

    // -----------------------------------------------------------------------
    // Async worker
    // -----------------------------------------------------------------------

    @Async("pdfWorkerExecutor")
    public void runConversionAsync(Long documentId) {
        try {
            convertAndStore(documentId);
        } catch (GotenbergClient.UnsupportedFormatException e) {
            log.warn("[PDF] document={} unsupported format: {}", documentId, e.getMessage());
            markFailed(documentId, "UNSUPPORTED_FORMAT");
        } catch (Exception e) {
            log.error("[PDF] document={} conversion error", documentId, e);
            markFailed(documentId, abbreviate(e.getMessage(), 64));
        }
    }

    // -----------------------------------------------------------------------
    // Core conversion (REQUIRES_NEW: 실패 시 독립 롤백)
    // -----------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void convertAndStore(Long documentId) {
        // 1. 최신 버전 조회
        DocumentVersion version = documentVersionRepository
                .findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "No DocumentVersion found for document: " + documentId));

        String sourceFileKey = version.getSourceFileKey();
        if (sourceFileKey == null || sourceFileKey.isBlank()) {
            throw new IllegalStateException(
                    "DocumentVersion " + version.getId() + " has no sourceFileKey");
        }

        // 2. 원본 파일 읽기 (MinIO)
        String sourceBucket = minio.getOriginalBucket(version);
        byte[] sourceBytes;
        try (InputStream is = minio.openStream(sourceBucket, sourceFileKey)) {
            sourceBytes = is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read source file: " + sourceFileKey, e);
        }

        // 3. Gotenberg 변환
        String fileName = fileNameFrom(sourceFileKey);
        byte[] pdfBytes = gotenberg.convertOfficeToPdf(fileName, sourceBytes);

        // 4. MinIO 업로드 (rendition 버킷)
        String renditionKey = renditionKey(version, RenditionKind.INITIAL, null);
        MinioClientWrapper.UploadResult uploaded = minio.uploadWithRetention(
                minio.getBucketRendition(),
                renditionKey,
                pdfBytes,
                "application/pdf",
                3650   // 10년 COMPLIANCE retention (M7 정책)
        );

        // 5. DocumentFile(RENDITION/INITIAL) 저장
        DocumentFile renditionFile = new DocumentFile();
        renditionFile.setVersionId(version.getId());
        renditionFile.setFileType(RENDITION_FILE_TYPE);
        renditionFile.setMinioBucket(uploaded.bucket());
        renditionFile.setMinioKey(uploaded.key());
        renditionFile.setFileName(fileNameWithoutExt(fileName) + ".pdf");
        renditionFile.setFileSizeBytes(uploaded.sizeBytes());
        renditionFile.setContentType("application/pdf");
        renditionFile.setSha256Hash(uploaded.sha256());
        renditionFile.setUploadedBy(version.getCreatedBy() != null ? version.getCreatedBy() : 0L);
        renditionFile.setRenditionKind(RenditionKind.INITIAL.name());
        renditionFile.setStepNumber(null);   // INITIAL은 step_number NULL
        documentFileRepository.save(renditionFile);

        // 6. Document pdf_status → CONVERTED
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document disappeared: " + documentId));
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        doc.setPdfStatusUpdatedAt(Instant.now());
        doc.setPdfStatusReason(null);
        documentRepository.save(doc);

        log.info("[PDF] document={} CONVERTED (renditionKey={})", documentId, renditionKey);
    }

    // -----------------------------------------------------------------------
    // Failure handler (REQUIRES_NEW: 메인 트랜잭션과 독립)
    // -----------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long documentId, String reason) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setPdfStatus(PdfStatus.CONVERSION_FAILED.name());
            doc.setPdfStatusUpdatedAt(Instant.now());
            doc.setPdfStatusReason(reason);
            documentRepository.save(doc);
            log.warn("[PDF] document={} CONVERSION_FAILED reason={}", documentId, reason);
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * rendition 오브젝트 키를 생성한다.
     * 예) renditions/42/initial.pdf  /  renditions/42/stamped-step3.pdf
     */
    static String renditionKey(DocumentVersion v, RenditionKind kind, Integer step) {
        return "renditions/" + v.getId() + "/" + kind.name().toLowerCase()
                + (step != null ? "-step" + step : "") + ".pdf";
    }

    /** MinIO 키(경로)에서 파일명만 추출. */
    private static String fileNameFrom(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }

    /** 확장자 제거. */
    private static String fileNameWithoutExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** reason이 너무 길면 잘라낸다 (pdf_status_reason VARCHAR(64)). */
    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "UNKNOWN";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
