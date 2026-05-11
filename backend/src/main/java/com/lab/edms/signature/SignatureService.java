package com.lab.edms.signature;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.auth.LocalAuthProvider;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.common.TooManyRequestsException;
import com.lab.edms.common.UnauthorizedException;
import com.lab.edms.common.UnprocessableEntityException;
import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentFile;
import com.lab.edms.document.DocumentFileRepository;
import com.lab.edms.document.DocumentRepository;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.document.DocumentVersionRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import com.lab.edms.workflow.SignedRef;
import com.lab.edms.workflow.WorkflowService;
import com.lab.edms.workflow.WorkflowStepInstance;
import com.lab.edms.workflow.WorkflowStepInstanceRepository;
import com.lab.edms.workflow.WorkflowInstance;
import com.lab.edms.workflow.WorkflowInstanceRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 전자 서명 비즈니스 로직.
 * Part 11 / Annex 11 요건: PW 재인증 + SHA-256 해시체인.
 */
@Service
public class SignatureService {

    private static final String GENESIS_HASH = sha256hex("GENESIS");

    private static String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private final SignatureManifestRepository manifestRepo;
    private final WorkflowStepInstanceRepository wfStepRepo;
    private final WorkflowInstanceRepository wfInstanceRepo;
    private final DocumentVersionRepository documentVersionRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SessionFirstSignTracker sessionTracker;
    private final WorkflowService workflowService;
    private final DocumentRepository documentRepo;
    private final DocumentFileRepository documentFileRepo;
    private final LocalAuthProvider localAuthProvider;
    private final SignatureRateLimiter rateLimiter;

    public SignatureService(SignatureManifestRepository manifestRepo,
                            WorkflowStepInstanceRepository wfStepRepo,
                            WorkflowInstanceRepository wfInstanceRepo,
                            DocumentVersionRepository documentVersionRepo,
                            UserRepository userRepo,
                            AuditService auditService,
                            BCryptPasswordEncoder passwordEncoder,
                            SessionFirstSignTracker sessionTracker,
                            WorkflowService workflowService,
                            DocumentRepository documentRepo,
                            DocumentFileRepository documentFileRepo,
                            LocalAuthProvider localAuthProvider,
                            SignatureRateLimiter rateLimiter) {
        this.manifestRepo = manifestRepo;
        this.wfStepRepo = wfStepRepo;
        this.wfInstanceRepo = wfInstanceRepo;
        this.documentVersionRepo = documentVersionRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.sessionTracker = sessionTracker;
        this.workflowService = workflowService;
        this.documentRepo = documentRepo;
        this.documentFileRepo = documentFileRepo;
        this.localAuthProvider = localAuthProvider;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public SignatureManifest sign(Long docId, Long verId, Long stepInstanceId,
                                   String password, String meaningStr,
                                   String signingUserId,
                                   Authentication auth, HttpSession session,
                                   String clientIp) {
        String actorUserId = auth.getName();

        // 0. Rate limit: 5회/분 per userId+IP (브루트포스 1차 방어선)
        if (!rateLimiter.tryConsume(actorUserId, clientIp)) {
            throw new TooManyRequestsException("서명 요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
        }

        // 1. PW 재인증 (BCrypt verify)
        if (!verifyPassword(actorUserId, password)) {
            localAuthProvider.recordFailureInNewTransaction(actorUserId);
            auditService.log(AuditEvent.of(actorUserId, AuditAction.USER_LOGIN_FAIL)
                    .entity("USER", actorUserId)
                    .ip(clientIp)
                    .build());
            throw new UnauthorizedException("비밀번호가 올바르지 않습니다");
        }

        // 2. SessionFirstSignTracker — READ ONLY at entry, mark AFTER manifest INSERT
        boolean sessionFirst = sessionTracker.isFirstInSession(session);

        // Part 11 §11.200(a) — session-first requires ID+PW
        if (sessionFirst) {
            if (signingUserId == null || signingUserId.isBlank()) {
                throw new UnprocessableEntityException("SIGNATURE_002",
                        "첫 번째 서명 시 사용자 ID가 필요합니다");
            }
            if (!signingUserId.equals(actorUserId)) {
                throw new ForbiddenException("SIGNATURE_003: 서명자 ID가 일치하지 않습니다");
            }
        }

        // 3. WorkflowStepInstance 조회 + IDOR 가드
        WorkflowStepInstance step = wfStepRepo.findById(stepInstanceId)
                .orElseThrow(() -> new NotFoundException("결재 단계를 찾을 수 없음: " + stepInstanceId));

        WorkflowInstance wf = wfInstanceRepo.findById(step.getWorkflowId())
                .orElseThrow(() -> new NotFoundException("워크플로를 찾을 수 없음: " + step.getWorkflowId()));

        // IDOR 가드: workflow → version → document = docId 검증
        if (!wf.getVersionId().equals(verId)) {
            throw new ForbiddenException("버전이 일치하지 않습니다");
        }
        DocumentVersion version = documentVersionRepo.findById(verId)
                .orElseThrow(() -> new NotFoundException("버전을 찾을 수 없음: " + verId));
        if (!version.getDocumentId().equals(docId)) {
            throw new ForbiddenException("해당 버전은 이 문서에 속하지 않습니다");
        }

        // 4. 가드: step.state == 'IN_PROGRESS'
        if (!"IN_PROGRESS".equals(step.getState())) {
            throw new UnprocessableEntityException("WORKFLOW_010", "이미 처리된 단계입니다");
        }

        // Actor 조회
        User actor = userRepo.findByUserId(actorUserId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없음: " + actorUserId));

        // 5. 가드: actor가 step.assignees에 있는지 확인
        boolean isAssignee = step.getAssignees().stream()
                .anyMatch(a -> actorUserId.equals(a.userIdString()));
        if (!isAssignee) {
            throw new ForbiddenException("결재 권한이 없습니다");
        }

        // 6. 이미 서명한 사용자인지 확인
        boolean alreadySigned = step.getSigned().stream()
                .anyMatch(s -> actor.getId().equals(s.userId()));
        if (alreadySigned) {
            throw new UnprocessableEntityException("WORKFLOW_011", "이미 서명하셨습니다");
        }

        // source_file_sha256 조회 (M3 계약: ORIGINAL 1행 필수)
        List<DocumentFile> originals = documentFileRepo
                .findByVersionIdAndFileType(verId, "ORIGINAL");
        if (originals.isEmpty()) {
            throw new UnprocessableEntityException("SIGNATURE_001", "원본 파일이 없습니다");
        }
        String sourceFileSha256 = originals.get(0).getSha256Hash();

        Document doc = documentRepo.findById(docId)
                .orElseThrow(() -> new NotFoundException("문서를 찾을 수 없음: " + docId));

        // 7. 해시체인 계산 — 병렬 서명 경쟁 조건 방지: version 행 락 후 prevHash 읽기
        documentVersionRepo.lockForUpdate(verId);
        Instant signedAt = Instant.now();
        SignatureMeaning meaning = SignatureMeaning.valueOf(meaningStr);

        String prevHash = manifestRepo.findLatestByVersionId(verId)
                .map(SignatureManifest::getThisHash)
                .orElse(GENESIS_HASH);

        String canonicalPayload = buildCanonicalPayload(
                version, doc, actor.getId(), meaningStr, signedAt, sourceFileSha256);
        String thisHash = calculateHash(prevHash, canonicalPayload);

        // 8. INSERT signature_manifests
        SignatureManifest manifest = new SignatureManifest();
        manifest.setVersionId(verId);
        manifest.setWorkflowStepId(stepInstanceId);
        manifest.setSignerId(actor.getId());
        manifest.setSignerUserId(actorUserId);
        manifest.setSignerName(actor.getFullName());
        manifest.setMeaning(meaning);
        manifest.setSignedAt(OffsetDateTime.now());
        manifest.setClientIp(clientIp);
        manifest.setCanonicalPayload(canonicalPayload);
        manifest.setPrevHash(prevHash);
        manifest.setThisHash(thisHash);
        manifest.setSessionFirst(sessionFirst);
        manifest.setAlgorithmVersion("v2");
        manifest = manifestRepo.save(manifest);

        // first flag: mark now, but undo on rollback — if wfStepRepo/workflowService/auditService
        // later fail and roll back the transaction, the session flag is reset so a retry
        // still requires ID+PW (Part 11 §11.200(a))
        if (sessionFirst) {
            sessionTracker.markSigned(session);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        sessionTracker.unmarkSigned(session);
                    }
                }
            });
        }

        // 9. step.signed에 SignedRef 추가 (JSONB append)
        if (step.getSigned() == null) {
            step.setSigned(new ArrayList<>());
        }
        step.getSigned().add(new SignedRef(actor.getId(), actorUserId, signedAt, manifest.getId()));
        wfStepRepo.save(step);

        // 10. 통과 평가: step.signed.size() >= step.minSigners
        if (step.getSigned().size() >= step.getMinSigners()) {
            step.setState("COMPLETED");
            step.setCompletedAt(OffsetDateTime.now());
            wfStepRepo.save(step);

            // WorkflowService.advance() 호출
            workflowService.advance(wf.getId());
        }

        // 11. Audit: WORKFLOW_STEP_SIGNED
        auditService.log(AuditEvent.of(actorUserId, AuditAction.WORKFLOW_STEP_SIGNED)
                .entity("workflow_step_instance", String.valueOf(stepInstanceId))
                .ip(clientIp)
                .build());

        return manifest;
    }

    private boolean verifyPassword(String userId, String rawPassword) {
        User user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없음: " + userId));
        // Part 11 §11.300 — LOCKED / DISABLED 계정은 올바른 PW라도 인증 불가 (OQ-SIG-008)
        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DISABLED) {
            throw new UnauthorizedException("계정이 잠겨 있거나 비활성화되어 있습니다");
        }
        if (user.getPasswordHash() == null) return false;
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    private String calculateHash(String prevHash, String canonicalPayload) {
        return sha256hex(prevHash + canonicalPayload);
    }

    /**
     * v2 canonical payload: 8-field pipe via SignatureCanonicalSerializer
     */
    private String buildCanonicalPayload(DocumentVersion version, Document doc,
                                          Long signerId, String meaning,
                                          Instant signedAt, String sourceFileSha256) {
        int revision = version.getRevision() != null ? version.getRevision() : 0;
        return SignatureCanonicalSerializer.serialize(
                signerId, meaning, signedAt,
                version.getId(), doc.getDocNumber(), revision,
                version.getState(), sourceFileSha256);
    }
}
