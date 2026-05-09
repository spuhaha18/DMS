package com.lab.edms.auth;

import com.lab.edms.common.ProblemDetail;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepo;
    private final SessionRegistry sessionRegistry;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService, UserRepository userRepo,
                          SessionRegistry sessionRegistry) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.sessionRegistry = sessionRegistry;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest req,
                                   HttpServletRequest http,
                                   HttpServletResponse response) {
        AuthResult result = authService.login(req.userId(), req.password(), http.getRemoteAddr());
        return switch (result) {
            case AuthResult.Success s -> {
                installSession(s.user(), http, response);
                yield ResponseEntity.ok(new LoginResponse(
                        s.user().getUserId(), s.user().getFullName(), false));
            }
            case AuthResult.ForcePasswordChange f -> {
                installSession(f.user(), http, response);
                yield ResponseEntity.ok(new LoginResponse(
                        f.user().getUserId(), f.user().getFullName(), true));
            }
            case AuthResult.InvalidCredentials ic -> ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.of("AUTH_001",
                            "Invalid user ID or password",
                            null,
                            "remaining_attempts", ic.remainingAttempts()));
            case AuthResult.AccountLocked __ -> ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.of("AUTH_002",
                            "Account is locked. Contact your system administrator.",
                            null));
            case AuthResult.AccountDisabled __ -> ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.of("AUTH_003",
                            "Account is disabled.",
                            null));
        };
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            authService.logout(auth.getName(), http.getRemoteAddr());
            var session = http.getSession(false);
            if (session != null) session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User u = userRepo.findByUserIdWithRoles(auth.getName()).orElseThrow();
        List<String> roles = u.getRoles().stream()
                .map(UserRole::getRole)
                .map(r -> r.getRoleCode())
                .collect(Collectors.toList());
        return ResponseEntity.ok(new MeResponse(
                u.getUserId(), u.getFullName(), u.getEmail(), u.getDepartment(), roles));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody @Valid ChangePasswordRequest req,
                                            HttpServletRequest http) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var outcome = authService.changePassword(
                auth.getName(), req.currentPassword(), req.newPassword(), http.getRemoteAddr());
        return switch (outcome) {
            case OK -> ResponseEntity.noContent().build();
            case WRONG_CURRENT -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.of("AUTH_004", "Current password is incorrect", null));
            case POLICY_VIOLATION -> ResponseEntity.badRequest()
                    .body(ProblemDetail.of("AUTH_005",
                            "Password does not meet policy (min 8 chars, 3 of 4 character classes)", null));
            case REUSED_RECENT -> ResponseEntity.badRequest()
                    .body(ProblemDetail.of("AUTH_006",
                            "Password matches one of your last 5 passwords", null));
        };
    }

    private void installSession(User u, HttpServletRequest request, HttpServletResponse response) {
        var authorities = u.getRoles().stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getRoleCode()))
                .collect(Collectors.toList());
        var token = new UsernamePasswordAuthenticationToken(u.getUserId(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        sessionRegistry.registerNewSession(session.getId(), u.getUserId());
        securityContextRepository.saveContext(context, request, response);
    }
}
