package com.lab.edms.workflow;

import com.lab.edms.signature.SignatureManifest;
import com.lab.edms.signature.SignatureService;
import com.lab.edms.workflow.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final SignatureService signatureService;
    private final WorkflowInstanceRepository wfInstanceRepo;
    private final WorkflowStepInstanceRepository wfStepRepo;

    public WorkflowController(WorkflowService workflowService,
                               SignatureService signatureService,
                               WorkflowInstanceRepository wfInstanceRepo,
                               WorkflowStepInstanceRepository wfStepRepo) {
        this.workflowService = workflowService;
        this.signatureService = signatureService;
        this.wfInstanceRepo = wfInstanceRepo;
        this.wfStepRepo = wfStepRepo;
    }

    @PostMapping("/documents/{docId}/versions/{verId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubmitResponse> submit(
            @PathVariable Long docId,
            @PathVariable Long verId,
            @RequestBody SubmitRequest req,
            Authentication auth) {
        SubmitResponse resp = workflowService.submit(docId, verId, req, auth);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/documents/{docId}/versions/{verId}/sign")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SignatureManifest> sign(
            @PathVariable Long docId,
            @PathVariable Long verId,
            @RequestBody SignRequest req,
            Authentication auth,
            HttpSession session,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        SignatureManifest manifest = signatureService.sign(
                docId, verId, req.stepInstanceId(),
                req.password(), req.meaning(),
                auth, session, clientIp);
        return ResponseEntity.ok(manifest);
    }

    @PostMapping("/documents/{docId}/versions/{verId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> reject(
            @PathVariable Long docId,
            @PathVariable Long verId,
            @RequestBody RejectRequest req,
            Authentication auth,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        workflowService.reject(docId, verId, req.stepInstanceId(),
                req.reason(), req.password(), auth, clientIp);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/{docId}/versions/{verId}/workflow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkflowInstanceDto> getWorkflow(
            @PathVariable Long docId,
            @PathVariable Long verId) {
        WorkflowInstance wf = wfInstanceRepo.findActiveByVersion(verId)
                .orElseGet(() -> {
                    // 완료/거부된 워크플로도 반환 (가장 최근)
                    return wfInstanceRepo.findLatestByVersionId(verId).orElse(null);
                });
        if (wf == null) {
            return ResponseEntity.notFound().build();
        }
        List<WorkflowStepInstance> stepList = wfStepRepo.findOrderedByWorkflow(wf.getId());
        List<WorkflowStepInstanceDto> stepDtos = stepList.stream()
                .map(s -> new WorkflowStepInstanceDto(
                        s.getId(), s.getStepOrder(), s.getStepType(), s.getRoleCode(),
                        s.getMinSigners(), s.isParallel(), s.isQaRequired(), s.getState(),
                        s.getAssignees(), s.getSigned()))
                .collect(Collectors.toList());
        WorkflowInstanceDto dto = new WorkflowInstanceDto(
                wf.getId(), wf.getState(), wf.getCurrentStep(), stepDtos);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/workflow/my-pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PendingTaskDto>> getMyPending(Authentication auth) {
        List<PendingTaskDto> result = workflowService.getMyPending(auth.getName());
        return ResponseEntity.ok(result);
    }
}
