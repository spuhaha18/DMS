package com.lab.edms.document;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.dto.CreateDocumentRequest;
import com.lab.edms.document.dto.CreateDocumentResponse;
import com.lab.edms.document.dto.DocumentFileDto;
import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.storage.MinioProperties;
import com.lab.edms.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class DocumentFileServiceIT {

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",        TestcontainersConfig.MINIO::getS3URL);
        registry.add("minio.access-key",      TestcontainersConfig.MINIO::getUserName);
        registry.add("minio.secret-key",      TestcontainersConfig.MINIO::getPassword);
        registry.add("minio.bucket-original", () -> "test-edms-documents-original");
        registry.add("minio.bucket-rendition",() -> "test-edms-documents-rendition");
    }

    @Autowired DocumentFileService fileService;
    @Autowired DocumentService documentService;
    @Autowired DocumentRepository docRepo;
    @Autowired DocumentVersionRepository versionRepo;
    @Autowired DocumentFileRepository fileRepo;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired EntityManager em;
    @Autowired MinioClientWrapper minioWrapper;
    @Autowired MinioProperties minioProps;

    private String authorUserId;
    private Long docId;
    private Long verId;

    // Minimal valid DOCX: starts with PK\x03\x04, padded to 64 bytes
    private static byte[] buildMinimalDocx(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = 'P';
        bytes[1] = 'K';
        bytes[2] = 0x03;
        bytes[3] = 0x04;
        return bytes;
    }

    @BeforeEach
    void setUp() {
        // Create dept QC
        if (deptRepo.findByDeptCode("QC").isEmpty()) {
            Department qc = new Department();
            qc.setDeptCode("QC");
            qc.setPrimaryName("Quality Control");
            qc.setSource("INTERNAL");
            deptRepo.save(qc);
        }

        // Create author user
        authorUserId = "file_author_" + System.nanoTime();
        User user = new User();
        user.setUserId(authorUserId);
        user.setFullName("File Author");
        user.setEmail(authorUserId + "@lab.test");
        user.setDepartment("QC");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("irrelevant");
        user.setForceChangePw(false);
        userRepo.save(user);

        // Assign AUTHOR role
        Role authorRole = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(authorRole);
        ur.setAssignedAt(OffsetDateTime.now());
        em.persist(ur);
        em.flush();

        // Grant can_create/can_view permission for SOP/QC
        Long sopCatId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();
        if (permRepo.findByRoleIdAndCategoryIdAndDepartment(authorRole.getId(), sopCatId, "QC").isEmpty()) {
            Permission p = new Permission();
            p.setRoleId(authorRole.getId());
            p.setCategoryId(sopCatId);
            p.setDepartment("QC");
            p.setCanView(true);
            p.setCanCreate(true);
            permRepo.save(p);
        }

        em.flush();
        em.clear();

        // Create a document + DRAFT version
        CreateDocumentRequest req = new CreateDocumentRequest("SOP", "QC", null, "File Upload Test SOP", false);
        CreateDocumentResponse resp = documentService.create(req, authorUserId);
        docId = resp.docId();
        verId = resp.versionId();

        em.flush();
        em.clear();
    }

    @Test
    void happyPath_uploadDocx_returns201AndPersists() throws Exception {
        byte[] content = buildMinimalDocx(1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content);

        DocumentFileDto dto = fileService.upload(docId, verId, file, authorUserId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNotNull();
        assertThat(dto.sha256Hash()).isNotBlank();
        assertThat(dto.sha256Hash()).hasSize(64);
        assertThat(dto.fileType()).isEqualTo("DOCX");
        assertThat(dto.fileName()).isEqualTo("test-document.docx");
        assertThat(dto.fileSizeBytes()).isEqualTo(1024L);

        // Verify document_files row inserted
        List<DocumentFile> files = fileRepo.findByVersionIdOrderByUploadedAtDesc(verId);
        assertThat(files).hasSize(1);

        // Verify version.source_file_key updated
        em.flush();
        em.clear();
        DocumentVersion version = versionRepo.findById(verId).orElseThrow();
        assertThat(version.getSourceFileKey()).isNotBlank();
        assertThat(version.getSourceFileKey()).contains("test-document.docx");

        // Verify object exists in MinIO
        InputStream stream = minioWrapper.openStream(minioProps.bucketOriginal(), version.getSourceFileKey());
        assertThat(stream).isNotNull();
        stream.close();
    }

    @Test
    void reUpload_toSameVersion_twoRowsExistLatestKeyUsed() throws Exception {
        byte[] content = buildMinimalDocx(512);

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "first-upload.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content);
        DocumentFileDto dto1 = fileService.upload(docId, verId, file1, authorUserId);

        MockMultipartFile file2 = new MockMultipartFile(
                "file", "second-upload.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content);
        DocumentFileDto dto2 = fileService.upload(docId, verId, file2, authorUserId);

        // 2 document_files rows should exist
        List<DocumentFile> files = fileRepo.findByVersionIdOrderByUploadedAtDesc(verId);
        assertThat(files).hasSize(2);

        // version.source_file_key should point to second upload
        em.flush();
        em.clear();
        DocumentVersion version = versionRepo.findById(verId).orElseThrow();
        assertThat(version.getSourceFileKey()).isEqualTo(dto2.minioKey());
    }

    @Test
    void nonDraftVersion_throws422() {
        // Force the version to UNDER_REVIEW state
        DocumentVersion version = versionRepo.findById(verId).orElseThrow();
        version.setState("UNDER_REVIEW");
        versionRepo.save(version);
        em.flush();
        em.clear();

        byte[] content = buildMinimalDocx(256);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content);

        assertThatThrownBy(() -> fileService.upload(docId, verId, file, authorUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
