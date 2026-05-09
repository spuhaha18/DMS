package com.lab.edms.user;

import com.lab.edms.common.PageResponse;
import com.lab.edms.user.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminService userSvc;
    private final AccessReviewService reviewSvc;

    public UserAdminController(UserAdminService userSvc, AccessReviewService reviewSvc) {
        this.userSvc = userSvc;
        this.reviewSvc = reviewSvc;
    }

    @GetMapping
    public PageResponse<UserDto> list(@RequestParam(required = false) UserStatus status,
                                      @RequestParam(required = false) String department,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      Authentication auth) {
        return PageResponse.of(userSvc.list(status, department, page, size, auth.getName()));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "csv") String format) {
        if (!"csv".equalsIgnoreCase(format)) {
            return ResponseEntity.badRequest().body("only csv supported");
        }
        String csv = reviewSvc.exportCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"access-review.csv\"")
                .body(csv);
    }

    @GetMapping("/{userPk}")
    public UserDto get(@PathVariable Long userPk) {
        return userSvc.get(userPk);
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody @Valid CreateUserRequest req,
                                          Authentication auth, HttpServletRequest http) {
        UserDto created = userSvc.create(req, auth.getName(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{userPk}")
    public UserDto update(@PathVariable Long userPk,
                          @RequestBody @Valid UpdateUserRequest req,
                          Authentication auth, HttpServletRequest http) {
        return userSvc.update(userPk, req, auth.getName(), http.getRemoteAddr());
    }

    @PutMapping("/{userPk}/roles")
    public UserDto updateRoles(@PathVariable Long userPk,
                               @RequestBody @Valid UpdateRolesRequest req,
                               Authentication auth, HttpServletRequest http) {
        return userSvc.updateRoles(userPk, req.roleCodes(), auth.getName(), http.getRemoteAddr());
    }

    @PostMapping("/{userId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String userId,
                        @RequestBody @Valid DisableUserRequest req,
                        Authentication auth, HttpServletRequest http) {
        userSvc.disable(userId, req.reason(), auth.getName(), http.getRemoteAddr());
    }

    @PostMapping("/{userId}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void passwordReset(@PathVariable String userId,
                              Authentication auth, HttpServletRequest http) {
        userSvc.resetPassword(userId, auth.getName(), http.getRemoteAddr());
    }
}
