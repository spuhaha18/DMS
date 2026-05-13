package com.lab.edms.signature;

import com.lab.edms.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * 세션당 첫 번째 서명 여부를 추적합니다.
 * Part 11 / Annex 11 요건: 세션에서 첫 서명 시 별도 의미 부여(session_first=true).
 */
@Component
public class SessionFirstSignTracker {

    private static final String ATTR = "edms.firstSignAt";

    private final UserRepository userRepo;

    public SessionFirstSignTracker(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public boolean isFirstInSession(HttpSession session) {
        return session.getAttribute(ATTR) == null;
    }

    public void markSigned(HttpSession session) {
        session.setAttribute(ATTR, java.time.Instant.now().toString());
    }

    public void unmarkSigned(HttpSession session) {
        session.removeAttribute(ATTR);
    }

    /** force_change_pw 컬럼 기반 첫 서명 필요 여부 (읽기 전용). */
    public boolean isFirstSignRequired(Long userId) {
        return userRepo.findById(userId)
                .map(u -> u.isForceChangePw())
                .orElse(false);
    }
}
