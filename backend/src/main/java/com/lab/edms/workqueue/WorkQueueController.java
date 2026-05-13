package com.lab.edms.workqueue;

import com.lab.edms.workqueue.dto.WorkQueueCountsDto;
import com.lab.edms.workqueue.dto.WorkQueueItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-queue")
public class WorkQueueController {

    private final WorkQueueService service;

    public WorkQueueController(WorkQueueService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<WorkQueueItemDto>> list(
            @RequestParam(required = false) WorkQueueKind kind,
            @RequestParam(defaultValue = "OPEN") WorkQueueState state,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(service.list(auth.getName(), kind, state, pageable));
    }

    @GetMapping("/counts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkQueueCountsDto> counts(Authentication auth) {
        return ResponseEntity.ok(service.counts(auth.getName()));
    }

    @PostMapping("/{id}/done")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkQueueItemDto> markDone(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(service.markDone(id, auth.getName()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkQueueItemDto> cancel(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(service.cancel(id, auth.getName()));
    }
}
