package com.lab.edms.document;

import com.lab.edms.document.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<CreateDocumentResponse> create(
            @RequestBody CreateDocumentRequest req,
            Authentication auth) {
        CreateDocumentResponse resp = documentService.create(req, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public Page<DocumentDto> list(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String state,
            Pageable pageable,
            Authentication auth) {
        return documentService.list(auth.getName(), categoryCode, department, state, pageable);
    }

    @GetMapping("/{docId}")
    public DocumentDto getById(@PathVariable Long docId, Authentication auth) {
        return documentService.getById(docId, auth.getName());
    }

    @GetMapping("/{docId}/versions")
    public List<DocumentVersionDto> listVersions(
            @PathVariable Long docId,
            Authentication auth) {
        return documentService.listVersions(docId, auth.getName());
    }

    @GetMapping("/{docId}/versions/{verId}")
    public DocumentVersionDto getVersion(
            @PathVariable Long docId,
            @PathVariable Long verId,
            Authentication auth) {
        return documentService.getVersion(docId, verId, auth.getName());
    }
}
