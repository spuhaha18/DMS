package com.lab.edms.document;

import com.github.f4b6a3.ulid.UlidCreator;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.document.dto.DocumentFileDto;
import com.lab.edms.storage.MinioClientWrapper;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class DocumentFileService {

    private final DocumentRepository docRepo;
    private final DocumentVersionRepository versionRepo;
    private final DocumentFileRepository fileRepo;
    private final MinioClientWrapper minio;
    private final FileTypeValidator fileTypeValidator;
    private final AuditService audit;
    private final UserRepository userRepo;

    public DocumentFileService(DocumentRepository docRepo,
                               DocumentVersionRepository versionRepo,
                               DocumentFileRepository fileRepo,
                               MinioClientWrapper minio,
                               FileTypeValidator fileTypeValidator,
                               AuditService audit,
                               UserRepository userRepo) {
        this.docRepo = docRepo;
        this.versionRepo = versionRepo;
        this.fileRepo = fileRepo;
        this.minio = minio;
        this.fileTypeValidator = fileTypeValidator;
        this.audit = audit;
        this.userRepo = userRepo;
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentFileDto upload(Long docId, Long verId, MultipartFile file, String actorUserId) {
        // 1. Load actor
        User actor = userRepo.findByUserId(actorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "actor not found"));

        // 2. Load document (404 if not found), then authorize (403 if not owner)
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "document not found: " + docId));
        if (!doc.getOwnerId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "only the document owner may upload files");
        }

        // 3. Load document version (must belong to docId, 404 if not)
        DocumentVersion version = versionRepo.findById(verId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "version not found: " + verId));

        if (!version.getDocumentId().equals(docId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "version not found for document");
        }

        // 4. Verify version.state == "DRAFT" → 422 if not
        if (!"DRAFT".equals(version.getState())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "File upload only allowed for DRAFT versions; current state: " + version.getState());
        }

        // 5. Validate file type via FileTypeValidator
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : file.getName();
        byte[] firstBytes;
        try {
            byte[] allBytes = file.getBytes();
            firstBytes = allBytes.length >= 8
                    ? java.util.Arrays.copyOf(allBytes, 8)
                    : allBytes;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read file bytes");
        }

        FileTypeValidator.AllowedFileType fileType =
                fileTypeValidator.validate(originalFilename, file.getContentType(), firstBytes);

        // 6. Generate MinIO key: "documents/{docId}/v{verId}/{ULID}-{sanitizedFilename}"
        String sanitized = sanitizeFilename(originalFilename);
        String ulid = UlidCreator.getUlid().toString();
        String key = String.format("documents/%d/v%d/%s-%s", docId, verId, ulid, sanitized);

        // 7. Upload to MinIO — M7 cutover 이후 생성된 버전은 GOVERNANCE 버킷으로 라우팅
        String targetBucket = minio.getOriginalBucket(version);
        MinioClientWrapper.UploadResult uploadResult;
        try {
            uploadResult = minio.uploadStreaming(
                    targetBucket,
                    key,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read uploaded file");
        }

        // 8. INSERT document_files
        DocumentFile docFile = new DocumentFile();
        docFile.setVersionId(verId);
        docFile.setFileType("ORIGINAL");   // "ORIGINAL" vs "RENDITION" — this is the original source file
        docFile.setMinioBucket(targetBucket);
        docFile.setMinioKey(key);
        docFile.setFileName(originalFilename);
        docFile.setFileSizeBytes(uploadResult.sizeBytes());
        docFile.setContentType(file.getContentType());
        docFile.setSha256Hash(uploadResult.sha256());
        docFile.setUploadedBy(actor.getId());
        fileRepo.save(docFile);

        // 9. UPDATE document_versions SET source_file_key=key, updated_by=actorId
        version.setSourceFileKey(key);
        version.setUpdatedBy(actor.getId());
        versionRepo.save(version);

        // 10. Audit log
        audit.log(new AuditEvent(
                actorUserId,
                AuditAction.DOCUMENT_FILE_UPLOADED,
                "DocumentFile",
                String.valueOf(docFile.getId()),
                null,
                "{\"minioKey\":\"" + key + "\",\"fileName\":\"" + originalFilename + "\"}",
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        ));

        // 11. Return DTO (fileType in DTO = file format like DOCX/PDF, not DB file_type role)
        return new DocumentFileDto(
                docFile.getId(),
                docFile.getVersionId(),
                fileType.name(),      // DOCX, XLSX, PPTX, PDF
                docFile.getMinioKey(),
                docFile.getFileName(),
                docFile.getFileSizeBytes(),
                docFile.getContentType(),
                docFile.getSha256Hash(),
                docFile.getUploadedAt() != null ? docFile.getUploadedAt().toString() : null,
                docFile.getRenditionKind(),  // M7.1: PDF rendition kind (INITIAL/STAMPED/EFFECTIVE) or null for ORIGINAL
                docFile.getStepNumber()      // M7.1: non-null only for STAMPED renditions
        );
    }

    /**
     * Sanitizes filename: replaces spaces with _, keeps only alphanumeric + -_.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        return filename.replace(' ', '_').replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
