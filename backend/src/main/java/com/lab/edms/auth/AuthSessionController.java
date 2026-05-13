package com.lab.edms.auth;

import com.lab.edms.auth.dto.SessionStateDto;
import com.lab.edms.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    private final UserRepository userRepo;

    public AuthSessionController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/session-state")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionStateDto> sessionState(Authentication auth) {
        return userRepo.findByUserId(auth.getName())
                .map(u -> ResponseEntity.ok(new SessionStateDto(
                        u.getUserId(),
                        u.getFullName(),
                        u.isForceChangePw())))
                .orElse(ResponseEntity.notFound().build());
    }
}
