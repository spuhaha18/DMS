package com.lab.edms.workflow;

import com.lab.edms.document.Document;
import com.lab.edms.permission.PermissionRepository;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AssigneeResolver {

    private final PermissionRepository permissionRepo;
    private final UserRepository userRepo;

    public AssigneeResolver(PermissionRepository permissionRepo,
                            UserRepository userRepo) {
        this.permissionRepo = permissionRepo;
        this.userRepo = userRepo;
    }

    /**
     * 각 step에 대해 assignee 후보 풀을 계산합니다.
     *
     * @param steps                      워크플로 템플릿 step 목록 (stepOrder 순)
     * @param document                   대상 문서
     * @param manualAssigneesByStepOrder auto_assign=false인 step의 stepOrder → userIdString 목록
     * @param fixedBy                    배정을 확정한 사용자 (actorUserId)
     * @param fixedAt                    배정 확정 시각
     * @return step 인덱스별 AssigneeRef 리스트 (steps와 같은 순서)
     */
    public List<List<AssigneeRef>> resolveAll(
            List<WorkflowTemplateStep> steps,
            Document document,
            Map<Integer, List<String>> manualAssigneesByStepOrder,
            String fixedBy,
            Instant fixedAt) {

        List<List<AssigneeRef>> result = new ArrayList<>();

        for (WorkflowTemplateStep step : steps) {
            List<AssigneeRef> candidates;

            if (step.isAutoAssign()) {
                // 자동 배정: role_code + 카테고리 + 부서 권한 매칭
                candidates = resolveAutoAssignees(step, document, fixedBy, fixedAt);
            } else {
                // 수동 배정: 입력된 userIdString 목록 사용 + 권한 검증
                List<String> manualIds = manualAssigneesByStepOrder
                        .getOrDefault(step.getStepOrder(), List.of());
                candidates = resolveManualAssignees(step, document, manualIds, fixedBy, fixedAt);
            }

            if (candidates.size() < step.getMinSigners()) {
                throw new IllegalStateException(String.format(
                        "결재 가능 사용자 부족: step %d (role=%s), 필요=%d, 가용=%d",
                        step.getStepOrder(), step.getRoleCode(),
                        step.getMinSigners(), candidates.size()
                ));
            }

            result.add(candidates);
        }

        return result;
    }

    private List<AssigneeRef> resolveAutoAssignees(
            WorkflowTemplateStep step,
            Document document,
            String fixedBy,
            Instant fixedAt) {

        String permFlag = "REVIEW".equalsIgnoreCase(step.getStepType()) ? "can_review" : "can_approve";

        // qa_required=true 이면 role_code='QA'만 대상
        String roleCodeFilter = step.isQaRequired() ? "QA" : step.getRoleCode();

        // native query는 id 목록만 반환 (User 엔티티 직접 매핑 시 @Audited 컨버터 문제 회피)
        List<Long> userIds = permissionRepo.findUserIdsByRoleCodeAndPermission(
                roleCodeFilter,
                document.getCategoryId(),
                document.getDepartment(),
                permFlag
        );

        return userRepo.findAllById(userIds).stream()
                .map(u -> new AssigneeRef(u.getId(), u.getUserId(), fixedAt, fixedBy))
                .toList();
    }

    private List<AssigneeRef> resolveManualAssignees(
            WorkflowTemplateStep step,
            Document document,
            List<String> userIdStrings,
            String fixedBy,
            Instant fixedAt) {

        List<AssigneeRef> refs = new ArrayList<>();
        String permFlag = "REVIEW".equalsIgnoreCase(step.getStepType()) ? "can_review" : "can_approve";

        for (String userIdStr : userIdStrings) {
            User user = userRepo.findByUserId(userIdStr)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없음: " + userIdStr));

            boolean hasPermission = permissionRepo.userHasPermission(
                    userIdStr,
                    document.getCategoryId(),
                    document.getDepartment(),
                    permFlag
            );
            if (!hasPermission) {
                throw new IllegalArgumentException(
                        String.format("사용자 %s은 이 step에 필요한 권한이 없습니다", userIdStr)
                );
            }
            refs.add(new AssigneeRef(user.getId(), userIdStr, fixedAt, fixedBy));
        }
        return refs;
    }
}
