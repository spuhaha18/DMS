package com.lab.edms.signature;

import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.document.DocumentService;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents/{docId}/versions/{vid}/signatures")
public class SignatureQueryController {

    private final SignatureManifestRepository manifestRepo;
    private final DocumentVersionRepository versionRepo;
    private final DocumentService documentService;

    public SignatureQueryController(SignatureManifestRepository manifestRepo,
                                    DocumentVersionRepository versionRepo,
                                    DocumentService documentService) {
        this.manifestRepo = manifestRepo;
        this.versionRepo = versionRepo;
        this.documentService = documentService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<? extends SignatureSummaryDto>> getSignatures(
            @PathVariable Long docId,
            @PathVariable Long vid,
            Authentication auth) {

        // IDOR guard: verify version belongs to docId
        DocumentVersion version = versionRepo.findById(vid)
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없음: " + vid));
        if (!version.getDocumentId().equals(docId)) {
            throw new ForbiddenException("해당 버전은 이 문서에 속하지 않습니다");
        }

        // Privilege check: ADMIN or AUDITOR gets full details and bypasses visibility
        boolean privileged = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_AUDITOR"));

        // Visibility guard: category/dept/confidential access (mirrors DocumentService.getById)
        // ADMIN/AUDITOR have unrestricted access and skip this check
        if (!privileged) {
            documentService.assertViewable(docId, auth.getName());
        }

        List<SignatureManifest> manifests = manifestRepo.findByVersionIdOrderBySignedAtAsc(vid);

        if (privileged) {
            return ResponseEntity.ok(manifests.stream()
                    .map(SignatureDetailDto::new)
                    .toList());
        } else {
            return ResponseEntity.ok(manifests.stream()
                    .map(SignatureSummaryDto::new)
                    .toList());
        }
    }
}
