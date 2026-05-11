package com.lab.edms.signature;

import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
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

    public SignatureQueryController(SignatureManifestRepository manifestRepo,
                                    DocumentVersionRepository versionRepo) {
        this.manifestRepo = manifestRepo;
        this.versionRepo = versionRepo;
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

        List<SignatureManifest> manifests = manifestRepo.findByVersionIdOrderBySignedAtAsc(vid);

        // Privilege check: ADMIN or AUDITOR gets full details
        boolean privileged = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_AUDITOR"));

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
