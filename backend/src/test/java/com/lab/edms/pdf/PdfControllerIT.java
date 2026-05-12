package com.lab.edms.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentFile;
import com.lab.edms.document.DocumentFileRepository;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.document.DocumentService;
import com.lab.edms.document.dto.CreateDocumentRequest;
import com.lab.edms.document.dto.CreateDocumentResponse;
import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M7.1 PR1 — integration tests for {@link PdfController}.
 *
 * <p>Verifies wire behaviour end-to-end against Testcontainers Postgres + MinIO:</p>
 * <ul>
 *   <li>GET /pdf (EFFECTIVE) — 200 + headers + body</li>
 *   <li>GET /pdf/download — Content-Disposition: attachment with/without can_download</li>
 *   <li>409 NOT_READY when pdf_status is in-flight</li>
 *   <li>404 NOT_FOUND when unauthorized (IDOR-safe)</li>
 *   <li>audit_logs INSERT for PDF_VIEWED, PDF_DOWNLOAD_DENIED</li>
 *   <li>POST /pdf/verify-report → 204 + PDF_VERIFIED audit row</li>
 * </ul>
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@Transactional
class PdfControllerIT {

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",        TestcontainersConfig.MINIO::getS3URL);
        registry.add("minio.access-key",      TestcontainersConfig.MINIO::getUserName);
        registry.add("minio.secret-key",      TestcontainersConfig.MINIO::getPassword);
        registry.add("minio.bucket-original", () -> "test-edms-documents-original");
        registry.add("minio.bucket-rendition",() -> "test-edms-documents-rendition");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentService documentService;
    @Autowired DocumentRepository docRepo;
    @Autowired DocumentVersionRepository verRepo;
    @Autowired DocumentFileRepository fileRepo;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired EntityManager em;
    @Autowired MinioClientWrapper minio;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbc;

    private static final String OWNER_ID = "pdf_owner";
    private static final String GENERAL_USER = "pdf_general";
    private static final String STRANGER = "pdf_stranger";
    private static final String DOWNLOADER = "pdf_downloader";

    private Long docId;
    private Long verId;
    private static final byte[] PDF_BYTES = ("%PDF-1.4\n%fake pdf content for IT\n%%EOF").getBytes();

    @BeforeEach
    void setUp() {
        // Department
        if (deptRepo.findByDeptCode("QC").isEmpty()) {
            Department qc = new Department();
            qc.setDeptCode("QC");
            qc.setPrimaryName("Quality Control");
            qc.setSource("INTERNAL");
            deptRepo.save(qc);
        }

        // Users — stranger gets QA role, which has no permissions seeded for SOP/QC in this test
        // (so even with @WithMockUser ROLE_QA it lacks row-level can_view).
        seedUser(OWNER_ID, "AUTHOR");
        seedUser(GENERAL_USER, "READER");
        seedUser(STRANGER, "QA");
        seedUser(DOWNLOADER, "READER");

        // AUTHOR perms for SOP/QC (so OWNER_ID can create the doc)
        Long sopCatId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();
        Role authorRole = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
        if (permRepo.findByRoleIdAndCategoryIdAndDepartment(authorRole.getId(), sopCatId, "QC").isEmpty()) {
            Permission p = new Permission();
            p.setRoleId(authorRole.getId());
            p.setCategoryId(sopCatId);
            p.setDepartment("QC");
            p.setCanView(true);
            p.setCanCreate(true);
            permRepo.save(p);
        }

        // READER perms for SOP/QC — can_view + can_download (downloader uses this).
        Role readerRole = roleRepo.findByRoleCode("READER").orElseThrow();
        if (permRepo.findByRoleIdAndCategoryIdAndDepartment(readerRole.getId(), sopCatId, "QC").isEmpty()) {
            Permission p = new Permission();
            p.setRoleId(readerRole.getId());
            p.setCategoryId(sopCatId);
            p.setDepartment("QC");
            p.setCanView(true);
            p.setCanDownload(true);
            permRepo.save(p);
        } else {
            permRepo.findByRoleIdAndCategoryIdAndDepartment(readerRole.getId(), sopCatId, "QC")
                    .ifPresent(p -> { p.setCanDownload(true); permRepo.save(p); });
        }

        em.flush();
        em.clear();

        // Document + version (owner = pdf_owner)
        CreateDocumentRequest req = new CreateDocumentRequest("SOP", "QC", null, "PDF IT Doc", false);
        CreateDocumentResponse resp = documentService.create(req, OWNER_ID);
        docId = resp.docId();
        verId = resp.versionId();

        // Upload a fake PDF to MinIO and INSERT a RENDITION row (kind=EFFECTIVE).
        // MinIO uploads are NOT rolled back by the test transaction — that's OK because keys
        // include the (unique) versionId, so cross-test collisions can't happen.
        String renditionKey = "renditions/" + verId + "/effective.pdf";
        MinioClientWrapper.UploadResult uploaded = minio.uploadWithRetention(
                minio.getBucketRendition(), renditionKey, PDF_BYTES, "application/pdf", 3650);

        DocumentFile rendition = new DocumentFile();
        rendition.setVersionId(verId);
        rendition.setFileType("RENDITION");
        rendition.setMinioBucket(uploaded.bucket());
        rendition.setMinioKey(uploaded.key());
        rendition.setFileName("effective.pdf");
        rendition.setFileSizeBytes(uploaded.sizeBytes());
        rendition.setContentType("application/pdf");
        rendition.setSha256Hash(uploaded.sha256());
        rendition.setUploadedBy(userRepo.findByUserId(OWNER_ID).orElseThrow().getId());
        rendition.setRenditionKind(RenditionKind.EFFECTIVE.name());
        rendition.setStepNumber(null);
        fileRepo.save(rendition);

        // Flip document.pdf_status to EFFECTIVE_STAMPED so the rendition is visible per policy.
        Document doc = docRepo.findById(docId).orElseThrow();
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        docRepo.save(doc);

        em.flush();
        em.clear();
    }

    private void seedUser(String userId, String roleCode) {
        if (userRepo.findByUserId(userId).isPresent()) return;
        User u = new User();
        u.setUserId(userId);
        u.setFullName(userId);
        u.setEmail(userId + "@lab.test");
        u.setDepartment("QC");
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash("irrelevant");
        u.setForceChangePw(false);
        userRepo.save(u);

        Role r = roleRepo.findByRoleCode(roleCode).orElseThrow();
        UserRole ur = new UserRole();
        ur.setUser(userRepo.findByUserId(userId).orElseThrow());
        ur.setRole(r);
        ur.setAssignedAt(OffsetDateTime.now());
        em.persist(ur);
    }

    // -----------------------------------------------------------------------
    // GET /pdf — happy path, EFFECTIVE rendition
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = OWNER_ID, roles = "AUTHOR")
    void getPdf_owner_returns200_andHeaders() throws Exception {
        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf", docId, verId)
                        .param("kind", "EFFECTIVE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Rendition-Kind", "EFFECTIVE"))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("ETag", notNullValue()))
                .andExpect(header().string("X-File-Sha256", notNullValue()))
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    @WithMockUser(username = OWNER_ID, roles = "AUTHOR")
    void getPdf_unknownKind_falls_back_to_404_or_default() throws Exception {
        // ORIGINAL is explicitly not supported here.
        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf", docId, verId)
                        .param("kind", "ORIGINAL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // -----------------------------------------------------------------------
    // GET /pdf/download — attachment + can_download semantics
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = DOWNLOADER, roles = "READER")
    void downloadPdf_withCanDownload_returns200_withAttachment() throws Exception {
        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf/download", docId, verId)
                        .param("kind", "EFFECTIVE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("X-Rendition-Kind", "EFFECTIVE"));
    }

    @Test
    @WithMockUser(username = GENERAL_USER, roles = "READER")
    void downloadPdf_withoutCanDownload_returns404_andLogsDownloadDenied() throws Exception {
        // Revoke can_download for the shared READER row so the general user truly lacks it.
        // Use plain SQL (transactional via JdbcTemplate's DataSource tx) — no JPA needed.
        Long readerRoleId = roleRepo.findByRoleCode("READER").orElseThrow().getId();
        Long sopCatId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();
        jdbc.update("UPDATE permissions SET can_download = FALSE WHERE role_id = ? AND category_id = ? AND department = 'QC'",
                readerRoleId, sopCatId);

        long before = countAuditAction("PDF_DOWNLOAD_DENIED");

        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf/download", docId, verId)
                        .param("kind", "EFFECTIVE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        long after = countAuditAction("PDF_DOWNLOAD_DENIED");
        assertThat(after).isGreaterThan(before);
    }

    // -----------------------------------------------------------------------
    // 409 NOT_READY when pdf_status is in-flight
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = OWNER_ID, roles = "AUTHOR")
    void getPdf_inFlight_returns409_notReady() throws Exception {
        // Mutate pdf_status mid-flight (JdbcTemplate uses its own DataSource tx, no JPA needed).
        jdbc.update("UPDATE documents SET pdf_status='PENDING_CONVERSION' WHERE id=?", docId);

        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf", docId, verId)
                        .param("kind", "EFFECTIVE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_READY"))
                .andExpect(jsonPath("$.retryAfterSeconds").exists());
    }

    // -----------------------------------------------------------------------
    // 404 NOT_FOUND for unauthorized (IDOR-safe) + audit
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = STRANGER, roles = "QA")
    void getPdf_unauthorized_returns404_andLogsViewDenied() throws Exception {
        long before = countAuditAction("PDF_VIEW_DENIED");

        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf", docId, verId)
                        .param("kind", "EFFECTIVE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        long after = countAuditAction("PDF_VIEW_DENIED");
        assertThat(after).isGreaterThan(before);
    }

    // -----------------------------------------------------------------------
    // PDF_VIEWED audit row inserted on successful view
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = OWNER_ID, roles = "AUTHOR")
    void getPdf_success_insertsPdfViewedAudit() throws Exception {
        long before = countAuditAction("PDF_VIEWED");

        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf", docId, verId)
                        .param("kind", "EFFECTIVE"))
                .andExpect(status().isOk());

        long after = countAuditAction("PDF_VIEWED");
        assertThat(after).isGreaterThan(before);
    }

    // -----------------------------------------------------------------------
    // verify-report → 204 + audit row
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = OWNER_ID, roles = "AUTHOR")
    void verifyReport_validPayload_returns204_andLogsPdfVerified() throws Exception {
        long before = countAuditAction("PDF_VERIFIED");

        Map<String, Object> body = Map.of(
                "renditionKind", "EFFECTIVE",
                "stepNumber", "",
                "verifyResult", "PASS",
                "expectedSha256", "abc123",
                "actualSha256", "abc123",
                "manifestSha256", "def456"
        );

        mvc.perform(post("/api/v1/documents/{d}/versions/{v}/pdf/verify-report", docId, verId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        long after = countAuditAction("PDF_VERIFIED");
        assertThat(after).isGreaterThan(before);
    }

    @Test
    @WithMockUser(username = OWNER_ID, roles = "AUTHOR")
    void verifyReport_missingVerifyResult_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "renditionKind", "EFFECTIVE",
                "verifyResult", ""
        );

        mvc.perform(post("/api/v1/documents/{d}/versions/{v}/pdf/verify-report", docId, verId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"));
    }

    // -----------------------------------------------------------------------
    // Unauthenticated → 401
    // -----------------------------------------------------------------------

    @Test
    void getPdf_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/documents/{d}/versions/{v}/pdf", docId, verId))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private long countAuditAction(String action) {
        Long n = auditJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ?", Long.class, action);
        return n != null ? n : 0L;
    }
}
