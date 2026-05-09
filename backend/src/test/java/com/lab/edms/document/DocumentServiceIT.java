package com.lab.edms.document;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class DocumentServiceIT {

    @Autowired DocumentService documentService;
    @Autowired DocumentRepository docRepo;
    @Autowired DocumentVersionRepository versionRepo;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private String authorUserId;
    private Long sopCategoryId;

    @BeforeEach
    void setUp() {
        // Create a test department "QC" if it doesn't exist
        if (deptRepo.findByDeptCode("QC").isEmpty()) {
            Department qc = new Department();
            qc.setDeptCode("QC");
            qc.setPrimaryName("Quality Control");
            qc.setSource("INTERNAL");
            deptRepo.save(qc);
        }

        // Create a test user
        authorUserId = "doc_author_" + System.nanoTime();
        User user = new User();
        user.setUserId(authorUserId);
        user.setFullName("Test Author");
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

        // Load SOP category id
        sopCategoryId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();

        // Grant can_create permission for SOP/QC
        Permission p = new Permission();
        p.setRoleId(authorRole.getId());
        p.setCategoryId(sopCategoryId);
        p.setDepartment("QC");
        p.setCanView(true);
        p.setCanCreate(true);
        permRepo.save(p);

        em.flush();
        em.clear();
    }

    @Test
    void happyPath_createSopQcDocument_returnsCorrectDocNumber() {
        CreateDocumentRequest req = new CreateDocumentRequest(
                "SOP", "QC", null, "Test SOP Document", false);

        CreateDocumentResponse resp = documentService.create(req, authorUserId);

        assertThat(resp).isNotNull();
        assertThat(resp.docId()).isNotNull();
        assertThat(resp.versionId()).isNotNull();
        assertThat(resp.docNumber()).startsWith("SOP-QC-");
        assertThat(resp.state()).isEqualTo("DRAFT");

        // Verify document was persisted
        Document doc = docRepo.findById(resp.docId()).orElseThrow();
        assertThat(doc.getDocNumber()).isEqualTo(resp.docNumber());
        assertThat(doc.getDepartment()).isEqualTo("QC");

        // Verify version was persisted
        DocumentVersion version = versionRepo.findById(resp.versionId()).orElseThrow();
        assertThat(version.getState()).isEqualTo("DRAFT");
        assertThat(version.getPdfStatus()).isEqualTo("PENDING");
        assertThat(version.getRevision()).isNull();
    }

    @Test
    void missingCategory_throws404() {
        CreateDocumentRequest req = new CreateDocumentRequest(
                "NONEXISTENT", "QC", null, "Title", false);

        assertThatThrownBy(() -> documentService.create(req, authorUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unknownDeptCode_throws422() {
        // Grant permission for any dept (null dept means all)
        Role authorRole = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
        Long formCatId = catRepo.findByCategoryCode("FORM").orElseThrow().getId();
        Permission p = new Permission();
        p.setRoleId(authorRole.getId());
        p.setCategoryId(formCatId);
        p.setDepartment(null);
        p.setCanView(true);
        p.setCanCreate(true);
        permRepo.save(p);
        em.flush();
        em.clear();

        CreateDocumentRequest req = new CreateDocumentRequest(
                "FORM", "DOES_NOT_EXIST", null, "Title", false);

        assertThatThrownBy(() -> documentService.create(req, authorUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void noCreatePermission_throws403() {
        // Use METHOD category — no permission granted for that
        CreateDocumentRequest req = new CreateDocumentRequest(
                "METHOD", "QC", null, "Method Doc", false);

        assertThatThrownBy(() -> documentService.create(req, authorUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
