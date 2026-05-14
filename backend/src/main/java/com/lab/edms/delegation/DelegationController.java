package com.lab.edms.delegation;

import com.lab.edms.common.NotFoundException;
import com.lab.edms.delegation.dto.DelegationDto;
import com.lab.edms.delegation.dto.DelegationRejectBody;
import com.lab.edms.delegation.dto.DelegationRequestBody;
import com.lab.edms.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/delegations")
public class DelegationController {

    private final DelegationService delegationService;
    private final DelegationRepository delegationRepo;
    private final UserRepository userRepo;

    public DelegationController(DelegationService delegationService,
                                DelegationRepository delegationRepo,
                                UserRepository userRepo) {
        this.delegationService = delegationService;
        this.delegationRepo = delegationRepo;
        this.userRepo = userRepo;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DelegationDto> request(
            @RequestBody DelegationRequestBody body,
            Authentication auth) {
        Long delegatorId = resolveUserId(auth.getName());
        Delegation d = delegationService.request(
                delegatorId,
                body.delegateUserId(),
                body.scopeKind(),
                body.scopeValue(),
                body.reason(),
                OffsetDateTime.parse(body.validFrom()),
                OffsetDateTime.parse(body.validTo())
        );
        return ResponseEntity.status(201).body(DelegationDto.from(d));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DelegationDto>> list(
            @RequestParam(defaultValue = "delegator") String as,
            Authentication auth) {
        Long userId = resolveUserId(auth.getName());
        List<Delegation> items = switch (as) {
            case "delegator"   -> delegationRepo.findByDelegatorUserIdOrderByCreatedAtDesc(userId);
            case "delegate"    -> delegationRepo.findByDelegateUserIdOrderByCreatedAtDesc(userId);
            case "qa-pending"  -> delegationRepo.findByStateOrderByCreatedAtDesc("REQUESTED");
            default            -> delegationRepo.findByDelegatorUserIdOrderByCreatedAtDesc(userId);
        };
        return ResponseEntity.ok(items.stream().map(DelegationDto::from).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('QA_MANAGER')")
    public ResponseEntity<DelegationDto> approve(
            @PathVariable Long id,
            Authentication auth) {
        Long qaId = resolveUserId(auth.getName());
        Delegation d = delegationService.approve(id, qaId);
        return ResponseEntity.ok(DelegationDto.from(d));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('QA_MANAGER')")
    public ResponseEntity<DelegationDto> reject(
            @PathVariable Long id,
            @RequestBody DelegationRejectBody body,
            Authentication auth) {
        Long qaId = resolveUserId(auth.getName());
        Delegation d = delegationService.reject(id, body.reason(), qaId);
        return ResponseEntity.ok(DelegationDto.from(d));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DelegationDto> revoke(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = resolveUserId(auth.getName());
        Delegation d = delegationService.revoke(id, userId);
        return ResponseEntity.ok(DelegationDto.from(d));
    }

    private Long resolveUserId(String userId) {
        return userRepo.findByUserId(userId)
                .map(u -> u.getId())
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }
}
