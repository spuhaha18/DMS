package com.lab.edms.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.department.Department;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.dto.CreateDocumentRequest;
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
class DocumentControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired RoleRepository roleRepo;
    @Autowired DocumentCategoryRepository catRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired PermissionRepository permRepo;
    @Autowired EntityManager em;

    // The controller tests use @WithMockUser(username = "ctrl_author")
    // We need this user to exist in the DB for DocumentService to load it
    private static final String TEST_USER_ID = "ctrl_author";

    @BeforeEach
    void setUp() {
        // Create department QC if absent
        if (deptRepo.findByDeptCode("QC").isEmpty()) {
            Department qc = new Department();
            qc.setDeptCode("QC");
            qc.setPrimaryName("Quality Control");
            qc.setSource("INTERNAL");
            deptRepo.save(qc);
        }

        // Create test user if absent
        if (userRepo.findByUserId(TEST_USER_ID).isEmpty()) {
            User user = new User();
            user.setUserId(TEST_USER_ID);
            user.setFullName("Controller Author");
            user.setEmail(TEST_USER_ID + "@lab.test");
            user.setDepartment("QC");
            user.setStatus(UserStatus.ACTIVE);
            user.setPasswordHash("irrelevant");
            user.setForceChangePw(false);
            userRepo.save(user);

            Role authorRole = roleRepo.findByRoleCode("AUTHOR").orElseThrow();
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(authorRole);
            ur.setAssignedAt(OffsetDateTime.now());
            em.persist(ur);
        }

        // Grant SOP can_create/can_view for QC dept
        Long sopCatId = catRepo.findByCategoryCode("SOP").orElseThrow().getId();
        Role authorRole = roleRepo.findByRoleCode("AUTHOR").orElseThrow();

        // Check if permission already exists (idempotent)
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
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = "AUTHOR")
    void postDocuments_authenticated_returns201() throws Exception {
        CreateDocumentRequest req = new CreateDocumentRequest(
                "SOP", "QC", null, "Controller Test SOP", false);

        mvc.perform(post("/api/v1/documents").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.docNumber").exists())
                .andExpect(jsonPath("$.state").value("DRAFT"));
    }

    @Test
    void postDocuments_unauthenticated_returns401() throws Exception {
        CreateDocumentRequest req = new CreateDocumentRequest(
                "SOP", "QC", null, "Unauth Test SOP", false);

        mvc.perform(post("/api/v1/documents").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = "AUTHOR")
    void getDocuments_filteredByPermission_returns200() throws Exception {
        // Create a document first
        CreateDocumentRequest req = new CreateDocumentRequest(
                "SOP", "QC", null, "List Test SOP", false);

        mvc.perform(post("/api/v1/documents").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Now list documents
        mvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
