package com.lab.edms.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import com.lab.edms.department.DepartmentRepository;
import com.lab.edms.document.dto.*;
import com.lab.edms.numbering.NumberingService;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private final DocumentRepository docRepo;
    private final DocumentVersionRepository versionRepo;
    private final DocumentCategoryRepository catRepo;
    private final DepartmentRepository deptRepo;
    private final NumberingService numberingService;
    private final PermissionResolver permissionResolver;
    private final AuditService audit;
    private final UserRepository userRepo;
    private final ObjectMapper json = new ObjectMapper();

    public DocumentService(DocumentRepository docRepo,
                           DocumentVersionRepository versionRepo,
                           DocumentCategoryRepository catRepo,
                           DepartmentRepository deptRepo,
                           NumberingService numberingService,
                           PermissionResolver permissionResolver,
                           AuditService audit,
                           UserRepository userRepo) {
        this.docRepo = docRepo;
        this.versionRepo = versionRepo;
        this.catRepo = catRepo;
        this.deptRepo = deptRepo;
        this.numberingService = numberingService;
        this.permissionResolver = permissionResolver;
        this.audit = audit;
        this.userRepo = userRepo;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateDocumentResponse create(CreateDocumentRequest req, String actorUserId) {
        // 1. Load actor user
        User actor = userRepo.findByUserId(actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "actor not found"));

        // 2. Load category by categoryCode (404 if not found)
        DocumentCategory category = catRepo.findByCategoryCode(req.categoryCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "category not found: " + req.categoryCode()));

        // 3. Check create permission (403 if not authorized)
        if (!permissionResolver.canCreate(actorUserId, category.getId(), req.department())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "no create permission for category=" + req.categoryCode()
                            + " dept=" + req.department());
        }

        // 4. Verify dept_code exists (422 if not found)
        deptRepo.findByDeptCode(req.department())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "DEPT_NOT_FOUND"));

        // 5. Issue document number
        NumberingService.IssueResult issued = numberingService.issue(
                category.getId(),
                new NumberingService.IssueContext(req.department(), req.projectCode()));

        // 6. INSERT documents
        Document doc = new Document();
        doc.setDocNumber(issued.docNumber());
        doc.setCategoryId(category.getId());
        doc.setDepartment(req.department());
        doc.setProjectCode(req.projectCode());
        doc.setTitle(req.title());
        doc.setOwnerId(actor.getId());
        doc.setConfidential(req.confidential());
        doc.setCreatedBy(actor.getId());
        docRepo.save(doc);

        // 7. INSERT document_versions (state=DRAFT, pdfStatus=PENDING, revision=null)
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(doc.getId());
        version.setRevision(null);
        version.setState("DRAFT");
        version.setPdfStatus("PENDING");
        version.setCreatedBy(actor.getId());
        version.setUpdatedBy(actor.getId());
        versionRepo.save(version);

        // 8. Audit log
        String snapshot = jsonOf(Map.of(
                "docNumber", doc.getDocNumber(),
                "categoryCode", req.categoryCode(),
                "department", req.department(),
                "title", req.title(),
                "confidential", req.confidential()
        ));
        audit.log(new AuditEvent(actorUserId, AuditAction.DOCUMENT_CREATED, "Document",
                String.valueOf(doc.getId()), null, snapshot,
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));

        // 9. Return response
        return new CreateDocumentResponse(doc.getId(), version.getId(), doc.getDocNumber(), version.getState());
    }

    @Transactional(readOnly = true)
    public DocumentDto getById(Long docId, String actorUserId) {
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "document not found: " + docId));

        // Check visibility
        checkVisibility(doc, actorUserId);

        // Log DOCUMENT_VIEWED
        audit.log(new AuditEvent(actorUserId, AuditAction.DOCUMENT_VIEWED, "Document",
                String.valueOf(doc.getId()), null, null,
                null, null, OffsetDateTime.now(ZoneOffset.UTC)));

        String catCode = catRepo.findById(doc.getCategoryId())
                .map(DocumentCategory::getCategoryCode).orElse("?");
        return DocumentDto.fromEntity(doc, catCode);
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> list(String actorUserId, Pageable pageable) {
        User actor = userRepo.findByUserId(actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "actor not found"));

        VisibilityScope scope = permissionResolver.resolveViewable(actorUserId);

        // Build a category code cache for the results
        Page<Document> docs = docRepo.searchVisible(
                scope.categoryIds().isEmpty() ? null : scope.categoryIds(),
                scope.deptCodes(),
                scope.allDepts(),
                actor.getId(),
                pageable);

        return docs.map(doc -> {
            String catCode = catRepo.findById(doc.getCategoryId())
                    .map(DocumentCategory::getCategoryCode).orElse("?");
            return DocumentDto.fromEntity(doc, catCode);
        });
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionDto> listVersions(Long docId, String actorUserId) {
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "document not found: " + docId));
        checkVisibility(doc, actorUserId);

        return versionRepo.findByDocumentIdOrderByCreatedAtDesc(docId)
                .stream()
                .map(DocumentVersionDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentVersionDto getVersion(Long docId, Long verId, String actorUserId) {
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "document not found: " + docId));
        checkVisibility(doc, actorUserId);

        DocumentVersion version = versionRepo.findById(verId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "version not found: " + verId));

        if (!version.getDocumentId().equals(docId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "version not found for document");
        }

        return DocumentVersionDto.fromEntity(version);
    }

    private void checkVisibility(Document doc, String actorUserId) {
        User actor = userRepo.findByUserId(actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "actor not found"));

        VisibilityScope scope = permissionResolver.resolveViewable(actorUserId);

        // Check category access
        if (!scope.categoryIds().contains(doc.getCategoryId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no view permission");
        }

        // Check dept access
        if (!scope.allDepts() && !scope.deptCodes().contains(doc.getDepartment())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no view permission for department");
        }

        // Check confidential
        if (doc.isConfidential() && !doc.getOwnerId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "document is confidential");
        }
    }

    private String jsonOf(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }
}
