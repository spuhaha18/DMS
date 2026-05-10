package com.lab.edms.security;

import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
public class EdmsPermissionEvaluator implements PermissionEvaluator {

    private final PermissionRepository permissionRepo;
    private final DocumentRepository documentRepo;
    private final DocumentVersionRepository documentVersionRepo;
    private final UserRepository userRepo;
    private final PermissionCache permissionCache;

    public EdmsPermissionEvaluator(PermissionRepository permissionRepo,
                                   DocumentRepository documentRepo,
                                   DocumentVersionRepository documentVersionRepo,
                                   UserRepository userRepo,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   PermissionCache permissionCache) {
        this.permissionRepo = permissionRepo;
        this.documentRepo = documentRepo;
        this.documentVersionRepo = documentVersionRepo;
        this.userRepo = userRepo;
        this.permissionCache = permissionCache;
    }

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (!(permission instanceof String permStr)) return false;

        String userId = auth.getName();

        if (targetDomainObject instanceof Document doc) {
            return checkPermission(userId, doc.getCategoryId(), doc.getDepartment(), permStr);
        }

        if (targetDomainObject instanceof DocumentVersion version) {
            Document doc = documentRepo.findById(version.getDocumentId()).orElse(null);
            if (doc == null) return false;
            return checkPermission(userId, doc.getCategoryId(), doc.getDepartment(), permStr);
        }

        if (targetDomainObject instanceof Long id) {
            // Try as documentId first, then as versionId
            Document doc = documentRepo.findById(id).orElse(null);
            if (doc != null) {
                return checkPermission(userId, doc.getCategoryId(), doc.getDepartment(), permStr);
            }
            DocumentVersion version = documentVersionRepo.findById(id).orElse(null);
            if (version != null) {
                Document vDoc = documentRepo.findById(version.getDocumentId()).orElse(null);
                if (vDoc == null) return false;
                return checkPermission(userId, vDoc.getCategoryId(), vDoc.getDepartment(), permStr);
            }
        }

        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (!(permission instanceof String permStr)) return false;
        if (!(targetId instanceof Long id)) return false;

        String userId = auth.getName();

        return switch (targetType.toUpperCase()) {
            case "DOCUMENT" -> {
                Document doc = documentRepo.findById(id).orElse(null);
                if (doc == null) yield false;
                yield checkPermission(userId, doc.getCategoryId(), doc.getDepartment(), permStr);
            }
            case "DOCUMENT_VERSION" -> {
                DocumentVersion version = documentVersionRepo.findById(id).orElse(null);
                if (version == null) yield false;
                Document doc = documentRepo.findById(version.getDocumentId()).orElse(null);
                if (doc == null) yield false;
                yield checkPermission(userId, doc.getCategoryId(), doc.getDepartment(), permStr);
            }
            default -> false;
        };
    }

    private boolean checkPermission(String userId, Long categoryId, String department, String permission) {
        String flag = mapToColumn(permission);
        if (permissionCache != null) {
            try {
                String cacheKey = userId + ":" + categoryId + ":" + department + ":" + flag;
                return permissionCache.computeIfAbsent(cacheKey,
                    () -> permissionRepo.userHasPermission(userId, categoryId, department, flag));
            } catch (Exception e) {
                // Request scope 밖에서 호출된 경우 (예: 테스트 환경) 직접 쿼리
            }
        }
        return permissionRepo.userHasPermission(userId, categoryId, department, flag);
    }

    private String mapToColumn(String permission) {
        return switch (permission.toUpperCase()) {
            case "REVIEW"     -> "can_review";
            case "APPROVE"    -> "can_approve";
            case "RETIRE"     -> "can_retire";
            case "EDIT_DRAFT" -> "can_edit_draft";
            case "VIEW"       -> "can_view";
            case "DOWNLOAD"   -> "can_download";
            case "CREATE"     -> "can_create";
            default -> throw new IllegalArgumentException("알 수 없는 permission: " + permission);
        };
    }

    public static boolean hasRole(Authentication auth, String roleCode) {
        if (auth == null) return false;
        String wanted = "ROLE_" + roleCode;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (wanted.equals(ga.getAuthority())) return true;
        }
        return false;
    }
}
