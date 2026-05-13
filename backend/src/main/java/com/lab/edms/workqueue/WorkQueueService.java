package com.lab.edms.workqueue;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.workqueue.dto.WorkQueueCountsDto;
import com.lab.edms.workqueue.dto.WorkQueueItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class WorkQueueService {

    private final WorkQueueRepository repo;
    private final UserRepository userRepo;
    private final AuditService auditService;

    public WorkQueueService(WorkQueueRepository repo,
                            UserRepository userRepo,
                            AuditService auditService) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    public Page<WorkQueueItemDto> list(String currentUserId, WorkQueueKind kind,
                                       WorkQueueState state, Pageable pageable) {
        Long uid = resolveUserId(currentUserId);
        Page<WorkQueueItem> page;
        if (kind != null) {
            page = repo.findByAssigneeUserIdAndKindAndStateOrderByCreatedAtDesc(uid, kind, state, pageable);
        } else {
            page = repo.findByAssigneeUserIdAndStateOrderByCreatedAtDesc(uid, state, pageable);
        }
        return page.map(WorkQueueItemDto::from);
    }

    public WorkQueueCountsDto counts(String currentUserId) {
        Long uid = resolveUserId(currentUserId);
        long open = repo.countByAssigneeUserIdAndState(uid, WorkQueueState.OPEN);
        long total = repo.countByAssigneeUserIdAndState(uid, WorkQueueState.OPEN)
                + repo.countByAssigneeUserIdAndState(uid, WorkQueueState.DONE)
                + repo.countByAssigneeUserIdAndState(uid, WorkQueueState.CANCELLED)
                + repo.countByAssigneeUserIdAndState(uid, WorkQueueState.EXPIRED);
        return new WorkQueueCountsDto(open, total);
    }

    @Transactional
    public WorkQueueItemDto markDone(Long itemId, String currentUserId) {
        Long uid = resolveUserId(currentUserId);
        WorkQueueItem item = repo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("work_queue item not found: " + itemId));

        if (!item.getAssigneeUserId().equals(uid)) {
            throw new ForbiddenException("본인 항목만 완료 처리할 수 있습니다");
        }
        if (item.getState() != WorkQueueState.OPEN) {
            return WorkQueueItemDto.from(item);
        }

        item.markDone(uid, OffsetDateTime.now());
        repo.save(item);

        auditService.log(AuditEvent.of(currentUserId, AuditAction.WORK_QUEUE_ITEM_DONE)
                .entity("work_queue", String.valueOf(itemId))
                .build());
        return WorkQueueItemDto.from(item);
    }

    @Transactional
    public WorkQueueItemDto cancel(Long itemId, String currentUserId) {
        Long uid = resolveUserId(currentUserId);
        WorkQueueItem item = repo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("work_queue item not found: " + itemId));

        if (!item.getAssigneeUserId().equals(uid)) {
            throw new ForbiddenException("본인 항목만 취소할 수 있습니다");
        }
        if (item.getState() != WorkQueueState.OPEN) {
            return WorkQueueItemDto.from(item);
        }

        item.cancel(uid, OffsetDateTime.now());
        repo.save(item);

        auditService.log(AuditEvent.of(currentUserId, AuditAction.WORK_QUEUE_ITEM_CANCELLED)
                .entity("work_queue", String.valueOf(itemId))
                .build());
        return WorkQueueItemDto.from(item);
    }

    private Long resolveUserId(String userIdString) {
        User user = userRepo.findByUserId(userIdString)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없음: " + userIdString));
        return user.getId();
    }
}
