package com.lab.edms.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.ProblemDetail;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentFile;
import com.lab.edms.document.DocumentFileRepository;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.storage.MinioClientWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M7.1 PR1 — PDF viewer / download / verify-report endpoints.
 *
 * <p>End-points:</p>
 * <ul>
 *   <li>{@code GET  /api/v1/documents/{docId}/versions/{verId}/pdf} — streaming view (no attachment)</li>
 *   <li>{@code GET  /api/v1/documents/{docId}/versions/{verId}/pdf/download} — Content-Disposition: attachment</li>
 *   <li>{@code POST /api/v1/documents/{docId}/versions/{verId}/pdf/verify-report} — records a PDF_VERIFIED audit event</li>
 * </ul>
 *
 * <p>{@code kind=ORIGINAL} is intentionally NOT handled here — the original
 * source file (docx/hwp) lives under {@code DocumentFileController}.</p>
 *
 * <p>All read/download/verify/deny actions are logged to {@code audit_logs}
 * (§11.10(e)). Dedup window for {@code PDF_VIEWED}: same (user, version, kind)
 * within 5 minutes logs once.</p>
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "PDF", description = "PDF rendition view / download / verify endpoints (M7.1)")
public class PdfController {

    private static final Logger log = LoggerFactory.getLogger(PdfController.class);

    /** PDF rendition file_type discriminator in document_files. */
    private static final String RENDITION_FILE_TYPE = "RENDITION";

    /** Dedup window for PDF_VIEWED audit events. */
    private static final long VIEW_AUDIT_DEDUP_WINDOW_MS = 5 * 60 * 1000L;

    /** Default retry-after hint when pipeline is in-flight. */
    private static final int NOT_READY_RETRY_AFTER_SECONDS = 5;

    private final DocumentRepository documentRepo;
    private final DocumentVersionRepository versionRepo;
    private final DocumentFileRepository fileRepo;
    private final PdfAccessPolicy accessPolicy;
    private final MinioClientWrapper minio;
    private final AuditService auditService;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    /**
     * In-memory dedup cache for PDF_VIEWED. Bounded by natural traffic patterns
     * (single instance: O(active users × visible versions × 3 kinds)); JVM restart
     * resets the window which is acceptable per the requirement "log only once
     * per window".
     */
    private final Map<String, Long> viewAuditDedupCache = new ConcurrentHashMap<>();

    public PdfController(DocumentRepository documentRepo,
                        DocumentVersionRepository versionRepo,
                        DocumentFileRepository fileRepo,
                        PdfAccessPolicy accessPolicy,
                        MinioClientWrapper minio,
                        AuditService auditService,
                        ObjectMapper json,
                        JdbcTemplate jdbc) {
        this.documentRepo = documentRepo;
        this.versionRepo = versionRepo;
        this.fileRepo = fileRepo;
        this.accessPolicy = accessPolicy;
        this.minio = minio;
        this.auditService = auditService;
        this.json = json;
        this.jdbc = jdbc;
    }

    // -----------------------------------------------------------------------
    // GET /pdf — streaming view
    // -----------------------------------------------------------------------

    @Operation(summary = "Stream a PDF rendition for viewing",
            description = "Returns the requested rendition (INITIAL/STAMPED/EFFECTIVE) as a streaming application/pdf "
                    + "response with Cache-Control: no-store. Supports HTTP Range requests for large PDFs.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF stream",
                content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "206", description = "Partial PDF stream (Range request)",
                content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "404", description = "Not found or unauthorized (IDOR-safe)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Pipeline still in flight (PENDING_CONVERSION/STAMPING/WATERMARKING)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{docId}/versions/{verId}/pdf")
    public ResponseEntity<?> viewPdf(
            @Parameter(description = "Document id") @PathVariable Long docId,
            @Parameter(description = "Document version id") @PathVariable Long verId,
            @Parameter(description = "Rendition kind (INITIAL/STAMPED/EFFECTIVE). Omit to auto-select.")
                @RequestParam(required = false) String kind,
            @Parameter(description = "Workflow step number (required when kind=STAMPED)")
                @RequestParam(required = false) Integer step,
            Authentication auth,
            HttpServletRequest request,
            HttpServletResponse response) {
        return serve(docId, verId, kind, step, auth, request, response, /*download*/ false);
    }

    // -----------------------------------------------------------------------
    // GET /pdf/download — attachment
    // -----------------------------------------------------------------------

    @Operation(summary = "Download a PDF rendition",
            description = "Same content as /pdf but with Content-Disposition: attachment. "
                    + "Requires can_download on the (category, department) row.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF download",
                content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "404", description = "Not found or unauthorized (IDOR-safe)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Pipeline still in flight",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{docId}/versions/{verId}/pdf/download")
    public ResponseEntity<?> downloadPdf(
            @PathVariable Long docId,
            @PathVariable Long verId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer step,
            Authentication auth,
            HttpServletRequest request,
            HttpServletResponse response) {
        return serve(docId, verId, kind, step, auth, request, response, /*download*/ true);
    }

    // -----------------------------------------------------------------------
    // POST /pdf/verify-report — frontend-validated checksum result
    // -----------------------------------------------------------------------

    @Operation(summary = "Record a frontend PDF integrity verification result",
            description = "Persists the PDF_VERIFIED audit event with verify_result, expected/actual SHA-256, and "
                    + "the manifest SHA-256 for compliance. Returns 204.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Verification recorded"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Not found or unauthorized",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{docId}/versions/{verId}/pdf/verify-report")
    public ResponseEntity<?> verifyReport(
            @PathVariable Long docId,
            @PathVariable Long verId,
            @RequestBody VerifyReportRequest body,
            Authentication auth,
            HttpServletRequest request) {

        if (body == null || body.verifyResult() == null || body.verifyResult().isBlank()) {
            return ResponseEntity.badRequest().body(ProblemDetail.of("VALIDATION_001",
                    "verifyResult is required", null));
        }

        // Resolve document + version (404 if missing or unauthorized to avoid leaking existence).
        Optional<Document> docOpt = documentRepo.findById(docId);
        Optional<DocumentVersion> verOpt = versionRepo.findById(verId);
        if (docOpt.isEmpty() || verOpt.isEmpty()
                || !verOpt.get().getDocumentId().equals(docId)) {
            return notFound();
        }
        Document document = docOpt.get();
        DocumentVersion version = verOpt.get();

        RenditionKind kind = parseKindOrDefault(body.renditionKind(), RenditionKind.EFFECTIVE);
        PdfAccessPolicy.Decision decision = accessPolicy.canView(auth, document, version, kind);
        if (!decision.allowed()) {
            // Verify-report against a pipeline-in-flight artifact is meaningless — treat as 404.
            return notFound();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("verify_result", body.verifyResult());
        payload.put("expected_sha256", body.expectedSha256());
        payload.put("actual_sha256", body.actualSha256());
        payload.put("manifest_sha256", body.manifestSha256());
        payload.put("rendition_kind", kind.name());
        payload.put("step_number", body.stepNumber());

        auditService.log(AuditEvent.of(auth.getName(), AuditAction.PDF_VERIFIED)
                .entity("DocumentVersion", String.valueOf(verId))
                .after(jsonOf(payload))
                .ip(clientIp(request))
                .build());

        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Verify-report request body
    // -----------------------------------------------------------------------

    /** Verify-report request body (M7.1 PR1). */
    public record VerifyReportRequest(
            String renditionKind,
            Integer stepNumber,
            String verifyResult,
            String expectedSha256,
            String actualSha256,
            String manifestSha256
    ) {}

    // -----------------------------------------------------------------------
    // Shared core
    // -----------------------------------------------------------------------

    private ResponseEntity<?> serve(Long docId, Long verId, String kindParam, Integer stepParam,
                                    Authentication auth, HttpServletRequest request,
                                    HttpServletResponse response, boolean download) {

        // ORIGINAL is not served by this controller — explicit 404 so callers route through DocumentFileController.
        if (kindParam != null && "ORIGINAL".equalsIgnoreCase(kindParam)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetail.of(
                    "NOT_FOUND",
                    "ORIGINAL files are served via DocumentFileController, not PdfController",
                    null));
        }

        Optional<Document> docOpt = documentRepo.findById(docId);
        Optional<DocumentVersion> verOpt = versionRepo.findById(verId);
        if (docOpt.isEmpty() || verOpt.isEmpty()
                || !verOpt.get().getDocumentId().equals(docId)) {
            return notFound();
        }
        Document document = docOpt.get();
        DocumentVersion version = verOpt.get();

        // Pipeline-in-flight gate: surface 409 NOT_READY before even resolving the rendition.
        if (accessPolicy.isInFlight(document.getPdfStatus())) {
            return notReady();
        }

        // Resolve the actual rendition kind & step (auto-select when kindParam is null).
        ResolvedRendition resolved = resolveRendition(document, version, auth, kindParam, stepParam);
        if (resolved == null || resolved.file == null) {
            // No matching rendition file present — audit the deny event then return 404 (IDOR-safe).
            auditDeny(auth, request, verId, null, download);
            return notFound();
        }

        // Permission check happens against the *resolved* kind.
        PdfAccessPolicy.Decision decision = download
                ? accessPolicy.canDownload(auth, document, version, resolved.kind)
                : accessPolicy.canView(auth, document, version, resolved.kind);

        if (decision.isNotReady()) {
            return notReady();
        }
        if (decision.denied()) {
            auditDeny(auth, request, verId, resolved, download);
            return notFound();
        }

        // -------- Allowed: write directly to HttpServletResponse --------
        // Rationale: returning ResponseEntity<StreamingResponseBody> via a wildcard generic
        // confuses Spring's HttpEntityMethodProcessor (it tries to JSON-serialize the lambda).
        // Direct response writes avoid that ambiguity and let us cleanly mix error ResponseEntity
        // with a streaming success path.
        DocumentFile file = resolved.file;
        long size = file.getFileSizeBytes() != null ? file.getFileSizeBytes() : 0L;
        String etag = "\"" + file.getSha256Hash() + "\"";
        String displayName = file.getFileName() != null ? file.getFileName() : "rendition.pdf";

        // Range parsing — supports single-range "bytes=start-end".
        Range range = parseRangeHeader(request.getHeader(HttpHeaders.RANGE), size);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.ETAG, etag);
        response.setHeader("X-Rendition-Kind", resolved.kind.name());
        if (resolved.stepNumber != null) response.setHeader("X-Rendition-Step", String.valueOf(resolved.stepNumber));
        response.setHeader("X-File-Sha256", file.getSha256Hash());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

        String safeName = displayName.replaceAll("[\\r\\n\"\\\\;=]", "_");
        if (download) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"");
        } else {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeName + "\"");
        }

        // Audit BEFORE streaming so the event commits even if the client aborts mid-stream.
        auditViewOrDownload(auth, request, verId, resolved, download);

        try {
            if (range != null) {
                long contentLength = range.length();
                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                if (contentLength <= Integer.MAX_VALUE) {
                    response.setContentLengthLong(contentLength);
                }
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        "bytes " + range.start + "-" + range.end + "/" + size);
                streamRange(file, range.start, contentLength, response.getOutputStream());
            } else {
                response.setStatus(HttpStatus.OK.value());
                if (size > 0) response.setContentLengthLong(size);
                streamFull(file, response.getOutputStream());
            }
            response.flushBuffer();
        } catch (IOException ioe) {
            // Client aborted or network error mid-stream — log and swallow. Headers are already sent.
            log.debug("PDF stream aborted: docId={} verId={} kind={} err={}",
                    docId, verId, resolved.kind, ioe.getMessage());
        }
        // Returning null signals the response has been fully handled.
        return null;
    }

    private void streamFull(DocumentFile file, OutputStream out) throws IOException {
        try (InputStream in = minio.openStream(file.getMinioBucket(), file.getMinioKey())) {
            in.transferTo(out);
        }
    }

    private void streamRange(DocumentFile file, long offset, long length, OutputStream out) throws IOException {
        try (InputStream in = minio.openStream(file.getMinioBucket(), file.getMinioKey(), offset, length)) {
            in.transferTo(out);
        }
    }

    // -----------------------------------------------------------------------
    // Rendition resolution
    // -----------------------------------------------------------------------

    /** Resolved rendition: which DocumentFile we'll stream and what (kind, step) it represents. */
    private record ResolvedRendition(RenditionKind kind, Integer stepNumber, DocumentFile file) {}

    /**
     * Resolves the rendition the caller asked for, or auto-selects per the policy when {@code kindParam} is null:
     * <ol>
     *   <li>active-step assignee → that step's STAMPED;</li>
     *   <li>general user → EFFECTIVE if available;</li>
     *   <li>fallback → latest STAMPED → INITIAL.</li>
     * </ol>
     * ORIGINAL is never auto-selected.
     */
    private ResolvedRendition resolveRendition(Document document, DocumentVersion version,
                                              Authentication auth, String kindParam, Integer stepParam) {
        Long verId = version.getId();
        // Explicit kind: respect it.
        if (kindParam != null && !kindParam.isBlank()) {
            RenditionKind requested;
            try {
                requested = RenditionKind.valueOf(kindParam.toUpperCase());
            } catch (IllegalArgumentException iae) {
                return null;
            }
            return switch (requested) {
                case INITIAL -> fileRepo.findFirstByVersionIdAndFileTypeAndRenditionKind(
                                verId, RENDITION_FILE_TYPE, RenditionKind.INITIAL.name())
                        .map(f -> new ResolvedRendition(RenditionKind.INITIAL, null, f))
                        .orElse(null);
                case EFFECTIVE -> fileRepo.findFirstByVersionIdAndFileTypeAndRenditionKind(
                                verId, RENDITION_FILE_TYPE, RenditionKind.EFFECTIVE.name())
                        .map(f -> new ResolvedRendition(RenditionKind.EFFECTIVE, null, f))
                        .orElse(null);
                case STAMPED -> resolveStamped(verId, stepParam);
            };
        }

        // Auto-select.
        // (a) active-step assignee → step's STAMPED
        if (auth != null && accessPolicy.isActiveStepAssignee(auth, verId)) {
            Integer activeStep = findActiveStepNumber(verId, auth.getName());
            if (activeStep != null) {
                Optional<DocumentFile> stamped = findStampedByStep(verId, activeStep);
                if (stamped.isPresent()) {
                    return new ResolvedRendition(RenditionKind.STAMPED, activeStep, stamped.get());
                }
            }
        }

        // (b) general user → EFFECTIVE if available
        Optional<DocumentFile> effective = fileRepo.findFirstByVersionIdAndFileTypeAndRenditionKind(
                verId, RENDITION_FILE_TYPE, RenditionKind.EFFECTIVE.name());
        if (effective.isPresent()) {
            return new ResolvedRendition(RenditionKind.EFFECTIVE, null, effective.get());
        }

        // (c) fallback latest STAMPED → INITIAL
        Optional<DocumentFile> latestStamped = fileRepo.findTopByVersionIdAndFileTypeAndRenditionKindOrderByStepNumberDesc(
                verId, RENDITION_FILE_TYPE, RenditionKind.STAMPED.name());
        if (latestStamped.isPresent()) {
            return new ResolvedRendition(RenditionKind.STAMPED, latestStamped.get().getStepNumber(), latestStamped.get());
        }

        Optional<DocumentFile> initial = fileRepo.findFirstByVersionIdAndFileTypeAndRenditionKind(
                verId, RENDITION_FILE_TYPE, RenditionKind.INITIAL.name());
        return initial.map(f -> new ResolvedRendition(RenditionKind.INITIAL, null, f)).orElse(null);
    }

    private ResolvedRendition resolveStamped(Long versionId, Integer step) {
        if (step != null) {
            return findStampedByStep(versionId, step)
                    .map(f -> new ResolvedRendition(RenditionKind.STAMPED, step, f))
                    .orElse(null);
        }
        // No step provided → return latest STAMPED.
        return fileRepo.findTopByVersionIdAndFileTypeAndRenditionKindOrderByStepNumberDesc(
                versionId, RENDITION_FILE_TYPE, RenditionKind.STAMPED.name())
                .map(f -> new ResolvedRendition(RenditionKind.STAMPED, f.getStepNumber(), f))
                .orElse(null);
    }

    private Optional<DocumentFile> findStampedByStep(Long versionId, Integer step) {
        return fileRepo.findFirstByVersionIdAndRenditionKindAndStepNumber(
                versionId, RenditionKind.STAMPED.name(), step);
    }

    /**
     * Looks up the workflow_step_instances row that is currently IN_PROGRESS for the version, with
     * the requesting user listed in {@code assignees[]}, and returns its step_order. Used during
     * auto-select of the rendition kind for active-step assignees.
     */
    private Integer findActiveStepNumber(Long versionId, String userIdString) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT wsi.step_order
                      FROM workflow_step_instances wsi
                      JOIN workflow_instances wi ON wi.id = wsi.workflow_id
                     WHERE wi.version_id = ?
                       AND wi.state = 'IN_PROGRESS'
                       AND wsi.state = 'IN_PROGRESS'
                       AND wsi.assignees @> jsonb_build_array(jsonb_build_object('userIdString', ?::text))
                     ORDER BY wsi.step_order ASC
                     LIMIT 1
                    """,
                    Integer.class, versionId, userIdString);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Audit helpers
    // -----------------------------------------------------------------------

    private void auditViewOrDownload(Authentication auth, HttpServletRequest request, Long verId,
                                    ResolvedRendition resolved, boolean download) {
        String userId = auth != null ? auth.getName() : null;
        AuditAction action = download ? AuditAction.PDF_DOWNLOADED : AuditAction.PDF_VIEWED;

        // 5-minute dedup for PDF_VIEWED only (downloads are always recorded for compliance).
        if (!download && userId != null) {
            String dedupKey = userId + "|" + verId + "|" + resolved.kind.name() + "|" + resolved.stepNumber;
            long now = System.currentTimeMillis();
            Long last = viewAuditDedupCache.get(dedupKey);
            if (last != null && (now - last) < VIEW_AUDIT_DEDUP_WINDOW_MS) {
                return;
            }
            viewAuditDedupCache.put(dedupKey, now);
            // Light-touch cleanup: purge any stale entries while we're here.
            viewAuditDedupCache.entrySet().removeIf(e -> (now - e.getValue()) > VIEW_AUDIT_DEDUP_WINDOW_MS);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rendition_kind", resolved.kind.name());
        payload.put("step_number", resolved.stepNumber);
        payload.put("sha256", resolved.file.getSha256Hash());

        auditService.log(AuditEvent.of(userId, action)
                .entity("DocumentVersion", String.valueOf(verId))
                .after(jsonOf(payload))
                .ip(clientIp(request))
                .build());
    }

    private void auditDeny(Authentication auth, HttpServletRequest request, Long verId,
                          ResolvedRendition resolved, boolean download) {
        AuditAction action = download ? AuditAction.PDF_DOWNLOAD_DENIED : AuditAction.PDF_VIEW_DENIED;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rendition_kind", resolved != null ? resolved.kind.name() : null);
        payload.put("step_number", resolved != null ? resolved.stepNumber : null);

        auditService.log(AuditEvent.of(auth != null ? auth.getName() : null, action)
                .entity("DocumentVersion", String.valueOf(verId))
                .after(jsonOf(payload))
                .ip(clientIp(request))
                .build());
    }

    // -----------------------------------------------------------------------
    // Response helpers
    // -----------------------------------------------------------------------

    private ResponseEntity<ProblemDetail> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetail.of(
                "NOT_FOUND",
                "PDF rendition not available",
                null));
    }

    private ResponseEntity<ProblemDetail> notReady() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", String.valueOf(NOT_READY_RETRY_AFTER_SECONDS))
                .body(ProblemDetail.of(
                        "NOT_READY",
                        "PDF pipeline is still in flight",
                        null,
                        "retryAfterSeconds", NOT_READY_RETRY_AFTER_SECONDS));
    }

    // -----------------------------------------------------------------------
    // Misc helpers
    // -----------------------------------------------------------------------

    private RenditionKind parseKindOrDefault(String s, RenditionKind defaultKind) {
        if (s == null || s.isBlank()) return defaultKind;
        try {
            return RenditionKind.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultKind;
        }
    }

    private String jsonOf(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    /** Parsed Range header — start/end inclusive byte offsets, or null when the header is absent/invalid. */
    private record Range(long start, long end) {
        long length() { return end - start + 1; }
    }

    private Range parseRangeHeader(String header, long totalSize) {
        if (header == null || !header.startsWith("bytes=") || totalSize <= 0) return null;
        String spec = header.substring("bytes=".length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        try {
            String left = spec.substring(0, dash).trim();
            String right = spec.substring(dash + 1).trim();
            long start;
            long end;
            if (left.isEmpty()) {
                // suffix range: -N means last N bytes
                long n = Long.parseLong(right);
                if (n <= 0) return null;
                start = Math.max(0, totalSize - n);
                end = totalSize - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? (totalSize - 1) : Long.parseLong(right);
            }
            if (start < 0 || end < start || end >= totalSize) return null;
            return new Range(start, end);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
