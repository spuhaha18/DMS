package com.lab.edms.notification;

import com.lab.edms.common.NotFoundException;
import com.lab.edms.notification.dto.NotificationDto;
import com.lab.edms.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepo;

    public NotificationController(NotificationService notificationService,
                                  UserRepository userRepo) {
        this.notificationService = notificationService;
        this.userRepo = userRepo;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationDto>> list(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = resolveUserId(auth.getName());
        Page<Notification> items = notificationService.listForUser(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(items.map(NotificationDto::from));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication auth) {
        Long userId = resolveUserId(auth.getName());
        long count = notificationService.unreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth.getName());
        notificationService.markRead(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Long resolveUserId(String userId) {
        return userRepo.findByUserId(userId)
                .map(u -> u.getId())
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }
}
