package com.lab.edms.auth;

import com.lab.edms.common.ProblemDetail;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepo;

    public AuthController(AuthService authService, UserRepository userRepo) {
        this.authService = authService;
        this.userRepo = userRepo;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest req,
                                   HttpServletRequest http) {
        AuthResult result = authService.login(req.userId(), req.password(), http.getRemoteAddr());
        return switch (result) {
            case AuthResult.Success s -> {
                installSession(s.user(), http);
                yield ResponseEntity.ok(new LoginResponse(
                        s.user().getUserId(), s.user().getFullName(), false));
            }
            case AuthResult.ForcePasswordChange f -> {
                installSession(f.user(), http);
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
        User u = userRepo.findByUserId(auth.getName()).orElseThrow();
        List<String> roles = u.getRoles().stream()
                .map(UserRole::getRole)
                .map(r -> r.getRoleCode())
                .collect(Collectors.toList());
        return ResponseEntity.ok(new MeResponse(
                u.getUserId(), u.getFullName(), u.getEmail(), u.getDepartment(), roles));
    }

    private void installSession(User u, HttpServletRequest http) {
        var authorities = u.getRoles().stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getRoleCode()))
                .collect(Collectors.toList());
        var token = new UsernamePasswordAuthenticationToken(u.getUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(token);
        // Ensure a session exists before changing ID (session fixation protection)
        http.getSession(true);
        http.changeSessionId();
    }
}
