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
import com.lab.edms.pdf.PdfStatus;
import com.lab.edms.project.ResearchProjectRepository;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ResearchProjectRepository projectRepo;
    private final ObjectMapper json = new ObjectMapper();

    public DocumentService(DocumentRepository docRepo,
                           DocumentVersionRepository versionRepo,
                           DocumentCategoryRepository catRepo,
                           DepartmentRepository deptRepo,
                           NumberingService numberingService,
                           PermissionResolver permissionResolver,
                           AuditService audit,
                           UserRepository userRepo,
                           ResearchProjectRepository projectRepo) {
        this.docRepo = docRepo;
        this.versionRepo = versionRepo;
        this.catRepo = catRepo;
        this.deptRepo = deptRepo;
        this.numberingService = numberingService;
        this.permissionResolver = permissionResolver;
        this.audit = audit;
        this.userRepo = userRepo;
        this.projectRepo = projectRepo;
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
        if (req.projectCode() != null && !req.projectCode().isBlank()) {
            projectRepo.findById(req.projectCode()).ifPresent(doc::setProject);
        }
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
    public Page<DocumentDto> list(String actorUserId,
                                   String categoryCode, String department, String state,
                                   Pageable pageable) {
        User actor = userRepo.findByUserId(actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "actor not found"));

        VisibilityScope scope = permissionResolver.resolveViewable(actorUserId);

        // Apply user-requested category filter on top of permission scope
        Collection<Long> catIds = scope.categoryIds().isEmpty() ? null : scope.categoryIds();
        if (categoryCode != null && !categoryCode.isBlank()) {
            var cat = catRepo.findByCategoryCode(categoryCode);
            if (cat.isEmpty()) return org.springframework.data.domain.Page.empty(pageable);
            Long filterCatId = cat.get().getId();
            if (catIds != null && !catIds.contains(filterCatId))
                return org.springframework.data.domain.Page.empty(pageable);
            catIds = Set.of(filterCatId);
        }

        // Apply user-requested department filter on top of permission scope
        Collection<String> depts = scope.deptCodes();
        boolean allDepts = scope.allDepts();
        if (department != null && !department.isBlank()) {
            if (!allDepts && !depts.contains(department))
                return org.springframework.data.domain.Page.empty(pageable);
            depts = Set.of(department);
            allDepts = false;
        }

        String stateFilter = (state != null && !state.isBlank()) ? state : null;

        Page<Document> docs = docRepo.searchVisible(catIds, depts, allDepts, actor.getId(), stateFilter, pageable);

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

    @Transactional(readOnly = true)
    public void assertViewable(Long docId, String actorUserId) {
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "document not found: " + docId));
        checkVisibility(doc, actorUserId);
    }

    /**
     * pdf_status가 처리 중(STAMPING, CONVERSION_FAILED, STAMP_FAILED)인 경우 423 LOCKED 예외.
     * WorkflowService.advance() 진입점에서 호출하여 PDF 처리 완료 전 단계 전진을 차단.
     *
     * null 또는 PENDING_CONVERSION / CONVERTED / STAMPED / EFFECTIVE_STAMPED 는 허용.
     */
    @Transactional(readOnly = true)
    public void assertNextStepAllowed(Long documentId) {
        Document doc = docRepo.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found"));
        String status = doc.getPdfStatus();
        if (status != null && (PdfStatus.STAMPING.name().equals(status)
                || PdfStatus.CONVERSION_FAILED.name().equals(status)
                || PdfStatus.STAMP_FAILED.name().equals(status))) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "PDF processing not complete (status=" + status + ")");
        }
        // null 또는 PENDING_CONVERSION / CONVERTED / STAMPED / EFFECTIVE_STAMPED 는 허용
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
