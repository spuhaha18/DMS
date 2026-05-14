package com.lab.edms.training;

import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.common.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/training")
public class TrainingController {

    private final TrainingService trainingService;
    private final UserRepository userRepository;

    public TrainingController(TrainingService trainingService, UserRepository userRepository) {
        this.trainingService = trainingService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/v1/training
     * 현재 사용자의 교육 과제 목록을 반환.
     */
    @GetMapping
    public ResponseEntity<List<TrainingAssignmentDto>> listForCurrentUser(Authentication auth) {
        String userId = auth.getName();
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        List<TrainingAssignmentDto> result = trainingService.listForUser(user.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/training/status/{versionId}
     * 특정 버전에 대한 교육 현황 반환 (관리자용).
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'QA_MANAGER', 'DEPT_MANAGER')")
    @GetMapping("/status/{versionId}")
    public ResponseEntity<List<TrainingStatusDto>> statusByVersion(@PathVariable Long versionId) {
        List<TrainingStatusDto> result = trainingService.statusByVersion(versionId);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/training/{assignmentId}/acknowledge
     * 교육 이수 확인.
     */
    @PostMapping("/{assignmentId}/acknowledge")
    public ResponseEntity<TrainingAssignmentDto> acknowledge(
            @PathVariable Long assignmentId,
            Authentication auth,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        TrainingAssignmentDto result = trainingService.acknowledge(assignmentId, auth, clientIp);
        return ResponseEntity.ok(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
