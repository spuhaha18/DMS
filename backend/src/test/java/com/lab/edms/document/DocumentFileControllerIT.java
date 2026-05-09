package com.lab.edms.document;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.dto.CreateDocumentRequest;
import com.lab.edms.document.dto.CreateDocumentResponse;
import com.lab.edms.permission.Permission;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@Transactional
class DocumentFileControllerIT {

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint",        TestcontainersConfig.MINIO::getS3URL);
        registry.add("minio.access-key",      TestcontainersConfig.MINIO::getUserName);
        registry.add("minio.secret-key",      TestcontainersConfig.MINIO::getPassword);
        registry.add("minio.bucket-original", () -> "test-edms-documents-original");
        registry.add("minio.bucket-rendition",() -> "test-edms-documents-rendition");
    }

    @Autowired MockMvc mvc;
    @Autowired DocumentService documentService;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired EntityManager em;

    private static final String TEST_USER_ID = "file_ctrl_author";
    private Long docId;
    private Long verId;

    // Minimal valid DOCX: PK\x03\x04 magic
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

        // Create test user
        if (userRepo.findByUserId(TEST_USER_ID).isEmpty()) {
            User user = new User();
            user.setUserId(TEST_USER_ID);
            user.setFullName("File Ctrl Author");
            user.setEmail(TEST_USER_ID + "@lab.test");
            user.setDepartment("QC");
            user.setStatus(UserStatus.ACTIVE);
            user.setPasswordHash("irrelevant");
            user.setForceChangePw(false);
            userRepo.save(user);

            Role authorRole = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
            UserRole ur = new UserRole();
            ur.setUser(userRepo.findByUserId(TEST_USER_ID).orElseThrow());
            ur.setRole(authorRole);
            ur.setAssignedAt(OffsetDateTime.now());
            em.persist(ur);
        }

        // Grant permission for SOP/QC
        Role authorRole = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
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

        // Create a document
        CreateDocumentRequest req = new CreateDocumentRequest("SOP", "QC", null, "Controller File Test SOP", false);
        CreateDocumentResponse resp = documentService.create(req, TEST_USER_ID);
        docId = resp.docId();
        verId = resp.versionId();

        em.flush();
        em.clear();
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = "AUTHOR")
    void uploadValidDocx_returns201() throws Exception {
        byte[] content = buildMinimalDocx(1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-sop.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content);

        mvc.perform(multipart("/api/v1/documents/{docId}/versions/{verId}/files", docId, verId)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sha256Hash").isNotEmpty())
                .andExpect(jsonPath("$.fileType").value("DOCX"));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = "AUTHOR")
    void uploadTxtFile_returns415() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "readme.txt",
                "text/plain",
                "Hello World".getBytes());

        mvc.perform(multipart("/api/v1/documents/{docId}/versions/{verId}/files", docId, verId)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadUnauthenticated_returns401() throws Exception {
        byte[] content = buildMinimalDocx(256);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content);

        mvc.perform(multipart("/api/v1/documents/{docId}/versions/{verId}/files", docId, verId)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
