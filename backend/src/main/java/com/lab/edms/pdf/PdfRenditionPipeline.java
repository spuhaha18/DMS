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
 * M7 PR2: 단계별 STAMPED RENDITION 누적 파이프라인.
 *
 * 상태 전이:
 *   (null / PENDING_CONVERSION) → [enqueueInitialConversion]
 *       → PENDING_CONVERSION  (트랜잭션 커밋 후 비동기 실행)
 *       → CONVERTED           (변환·업로드 성공)
 *       → CONVERSION_FAILED   (UnsupportedFormatException 또는 기타 오류)
 *
 *   CONVERTED → [applyStampForStep]
 *       → STAMPING            (작업 시작)
 *       → STAMPED             (도장 적용·업로드 성공)
 *       → STAMP_FAILED        (오류)
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
    private final PdfStampService pdfStampService;

    // Self-injection to ensure @Async goes through the Spring AOP proxy, not this.method()
    @Lazy @Autowired
    private PdfRenditionPipeline self;

    public PdfRenditionPipeline(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentFileRepository documentFileRepository,
            MinioClientWrapper minio,
            GotenbergClient gotenberg,
            PdfStampService pdfStampService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentFileRepository = documentFileRepository;
        this.minio = minio;
        this.gotenberg = gotenberg;
        this.pdfStampService = pdfStampService;
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
    // Stamp accumulation (PR2) — STAMPING → STAMPED
    // -----------------------------------------------------------------------

    /**
     * 결재 단계 서명 후 호출 — 도장을 누적하고 STAMPED RENDITION 행을 추가한다.
     * 직전 RENDITION(INITIAL 또는 마지막 STAMPED)을 base로 stamp를 누적.
     *
     * @Async: 호출자 트랜잭션과 분리되어 별도 스레드에서 실행된다.
     * @Transactional(REQUIRES_NEW): 실패 시 독립 롤백.
     */
    @Async("pdfWorkerExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyStampForStep(Long versionId, StampPayload payload) {
        // 1. DocumentVersion → Document 조회
        DocumentVersion version = documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No DocumentVersion found: " + versionId));
        Long documentId = version.getDocumentId();

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document disappeared: " + documentId));

        // 2. pdf_status → STAMPING
        doc.setPdfStatus(PdfStatus.STAMPING.name());
        doc.setPdfStatusUpdatedAt(Instant.now());
        doc.setPdfStatusReason(null);
        documentRepository.save(doc);

        log.info("[PDF] document={} versionId={} STAMPING step={}", documentId, versionId,
                payload.stepNumber());

        try {
            // 3. 최신 RENDITION base 결정:
            //    STAMPED(최대 step_number) 존재 시 그것을 base, 없으면 INITIAL
            DocumentFile baseFile = documentFileRepository
                    .findTopByVersionIdAndFileTypeAndRenditionKindOrderByStepNumberDesc(
                            versionId, RENDITION_FILE_TYPE, RenditionKind.STAMPED.name())
                    .orElseGet(() -> documentFileRepository
                            .findFirstByVersionIdAndFileTypeAndRenditionKind(
                                    versionId, RENDITION_FILE_TYPE, RenditionKind.INITIAL.name())
                            .orElseThrow(() -> new IllegalStateException(
                                    "No RENDITION base found for versionId=" + versionId)));

            // 4. MinIO에서 base PDF bytes 로드
            byte[] baseBytes;
            try (InputStream is = minio.openStream(baseFile.getMinioBucket(), baseFile.getMinioKey())) {
                baseBytes = is.readAllBytes();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to read base rendition: " + baseFile.getMinioKey(), e);
            }

            // 5. PdfStampService로 도장 적용
            byte[] stampedBytes = pdfStampService.applyStamp(baseBytes, payload);

            // 6. MinIO에 새 STAMPED rendition 업로드
            String newKey = renditionKey(version, RenditionKind.STAMPED, payload.stepNumber());
            MinioClientWrapper.UploadResult uploaded = minio.uploadWithRetention(
                    minio.getBucketRendition(),
                    newKey,
                    stampedBytes,
                    "application/pdf",
                    3650   // 10년 COMPLIANCE retention (M7 정책)
            );

            // 7. document_files INSERT (kind=STAMPED, step_number=payload.stepNumber())
            DocumentFile stampedFile = new DocumentFile();
            stampedFile.setVersionId(versionId);
            stampedFile.setFileType(RENDITION_FILE_TYPE);
            stampedFile.setMinioBucket(uploaded.bucket());
            stampedFile.setMinioKey(uploaded.key());
            stampedFile.setFileName("stamped-step" + payload.stepNumber() + ".pdf");
            stampedFile.setFileSizeBytes(uploaded.sizeBytes());
            stampedFile.setContentType("application/pdf");
            stampedFile.setSha256Hash(uploaded.sha256());
            stampedFile.setUploadedBy(version.getCreatedBy() != null ? version.getCreatedBy() : 0L);
            stampedFile.setRenditionKind(RenditionKind.STAMPED.name());
            stampedFile.setStepNumber(payload.stepNumber());
            documentFileRepository.save(stampedFile);

            // 8. pdf_status → STAMPED
            doc.setPdfStatus(PdfStatus.STAMPED.name());
            doc.setPdfStatusUpdatedAt(Instant.now());
            doc.setPdfStatusReason(null);
            documentRepository.save(doc);

            log.info("[PDF] document={} STAMPED step={} renditionKey={}",
                    documentId, payload.stepNumber(), newKey);

        } catch (Exception e) {
            log.error("[PDF] document={} STAMP_FAILED step={}", documentId,
                    payload.stepNumber(), e);
            doc.setPdfStatus(PdfStatus.STAMP_FAILED.name());
            doc.setPdfStatusUpdatedAt(Instant.now());
            doc.setPdfStatusReason(abbreviate(e.getMessage(), 64));
            documentRepository.save(doc);
        }
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
