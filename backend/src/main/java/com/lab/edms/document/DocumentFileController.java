package com.lab.edms.document;

import com.lab.edms.document.dto.DocumentFileDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentFileController {

    private final DocumentFileService fileService;

    public DocumentFileController(DocumentFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/{docId}/versions/{verId}/files")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentFileDto upload(
            @PathVariable Long docId,
            @PathVariable Long verId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        return fileService.upload(docId, verId, file, auth.getName());
    }
}
