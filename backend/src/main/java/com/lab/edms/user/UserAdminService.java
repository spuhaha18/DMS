package com.lab.edms.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import com.lab.edms.common.ConflictException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.common.UnprocessableEntityException;
import com.lab.edms.notification.EmailNotificationService;
import com.lab.edms.user.dto.*;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class UserAdminService {

    private static final String TEMP_PW_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final EntityManager em;
    private final AuditService audit;
    private final BCryptPasswordEncoder encoder;
    private final EmailNotificationService email;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper json = new ObjectMapper();

    public UserAdminService(UserRepository userRepo, RoleRepository roleRepo,
                            EntityManager em, AuditService audit,
                            BCryptPasswordEncoder encoder,
                            EmailNotificationService email,
                            SessionRegistry sessionRegistry) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.em = em;
        this.audit = audit;
        this.encoder = encoder;
        this.email = email;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> list(UserStatus status, String department, int page, int size, String actor) {
        Page<User> p = userRepo.searchAdmin(status, department, PageRequest.of(page, size));
        p.getContent().forEach(u -> u.getRoles().size());
        return p.map(UserDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long userPk) {
        User u = userRepo.findById(userPk).orElseThrow(() -> new NotFoundException("user not found"));
        u.getRoles().size();
        return UserDto.fromEntity(u);
    }

    @Transactional
    public UserDto create(CreateUserRequest req, String actorUserId, String clientIp) {
        if (userRepo.existsByUserId(req.userId())) {
            throw new ConflictException("USER_001", "user_id already exists");
        }
        if (userRepo.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("USER_002", "email already exists");
        }

        Set<Role> roles = resolveRoles(req.roleCodes());

        String temp = generateTempPassword(16);

        User u = new User();
        u.setUserId(req.userId());
        u.setFullName(req.fullName());
        u.setEmail(req.email());
        u.setDepartment(req.department());
        u.setTitle(req.title());
        u.setValidFrom(req.validFrom());
        u.setValidUntil(req.validUntil());
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash(encoder.encode(temp));
        u.setForceChangePw(true);
        userRepo.save(u);

        for (Role r : roles) {
            UserRole ur = new UserRole();
            ur.setUser(u);
            ur.setRole(r);
            ur.setAssignedAt(OffsetDateTime.now());
            em.persist(ur);
        }
        em.flush();
        em.refresh(u);

        audit.log(new AuditEvent(actorUserId, AuditAction.USER_CREATED, "USER",
                String.valueOf(u.getId()), null, jsonOf(UserDto.fromEntity(u)),
                null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));

        for (Role r : roles) {
            audit.log(new AuditEvent(actorUserId, AuditAction.ROLE_ASSIGNED, "USER_ROLE",
                    u.getUserId() + ":" + r.getRoleCode(),
                    null, jsonOf(Map.of("role_code", r.getRoleCode())),
                    null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
        }

        email.sendInitialPassword(u.getEmail(), u.getUserId(), temp, true);
        return UserDto.fromEntity(u);
    }

    @Transactional
    public UserDto update(Long userPk, UpdateUserRequest req, String actorUserId, String clientIp) {
        User u = userRepo.findById(userPk).orElseThrow(() -> new NotFoundException("user not found"));
        String before = jsonOf(UserDto.fromEntity(u));

        if (!u.getEmail().equalsIgnoreCase(req.email()) &&
                userRepo.existsByEmailIgnoreCase(req.email())) {
            throw new ConflictException("USER_002", "email already exists");
        }

        u.setFullName(req.fullName());
        u.setEmail(req.email());
        u.setDepartment(req.department());
        u.setTitle(req.title());
        u.setValidFrom(req.validFrom());
        u.setValidUntil(req.validUntil());
        userRepo.save(u);

        audit.log(new AuditEvent(actorUserId, AuditAction.USER_UPDATED, "USER",
                String.valueOf(u.getId()), before, jsonOf(UserDto.fromEntity(u)),
                null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
        return UserDto.fromEntity(u);
    }

    @Transactional
    public UserDto updateRoles(Long userPk, List<String> roleCodes, String actorUserId, String clientIp) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new UnprocessableEntityException("USER_004", "at least one role required");
        }
        User u = userRepo.findById(userPk).orElseThrow(() -> new NotFoundException("user not found"));
        Set<Role> target = resolveRoles(roleCodes);
        Set<String> targetCodes = new TreeSet<>();
        for (Role r : target) targetCodes.add(r.getRoleCode());

        Set<String> existing = new TreeSet<>();
        for (UserRole ur : u.getRoles()) existing.add(ur.getRole().getRoleCode());

        Iterator<UserRole> it = u.getRoles().iterator();
        while (it.hasNext()) {
            UserRole ur = it.next();
            if (!targetCodes.contains(ur.getRole().getRoleCode())) {
                String code = ur.getRole().getRoleCode();
                em.remove(ur);
                it.remove();
                audit.log(new AuditEvent(actorUserId, AuditAction.ROLE_REVOKED, "USER_ROLE",
                        u.getUserId() + ":" + code, null, null,
                        null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
            }
        }
        for (Role r : target) {
            if (!existing.contains(r.getRoleCode())) {
                UserRole ur = new UserRole();
                ur.setUser(u);
                ur.setRole(r);
                ur.setAssignedAt(OffsetDateTime.now());
                em.persist(ur);
                audit.log(new AuditEvent(actorUserId, AuditAction.ROLE_ASSIGNED, "USER_ROLE",
                        u.getUserId() + ":" + r.getRoleCode(),
                        null, jsonOf(Map.of("role_code", r.getRoleCode())),
                        null, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));
            }
        }
        em.flush();
        terminateSessions(u.getUserId());
        return UserDto.fromEntity(u);
    }

    @Transactional
    public void disable(String targetUserId, String reason, String actorUserId, String clientIp) {
        if (Objects.equals(targetUserId, actorUserId)) {
            throw new UnprocessableEntityException("USER_005", "you cannot disable your own account (self-disable)");
        }
        User u = userRepo.findByUserId(targetUserId).orElseThrow(() -> new NotFoundException("user not found"));
        u.setStatus(UserStatus.DISABLED);
        userRepo.save(u);

        audit.log(new AuditEvent(actorUserId, AuditAction.USER_DISABLED, "USER",
                String.valueOf(u.getId()),
                jsonOf(Map.of("status", "ACTIVE")),
                jsonOf(Map.of("status", "DISABLED")),
                reason, clientIp, OffsetDateTime.now(ZoneOffset.UTC)));

        terminateSessions(u.getUserId());
    }

    @Transactional
    public void resetPassword(String targetUserId, String actorUserId, String clientIp) {
        User u = userRepo.findByUserId(targetUserId).orElseThrow(() -> new NotFoundException("user not found"));
        String temp = generateTempPassword(16);
        u.setPasswordHash(encoder.encode(temp));
        u.setForceChangePw(true);
        u.setFailedAttempts(0);
        if (u.getStatus() == UserStatus.LOCKED) u.setStatus(UserStatus.ACTIVE);
        u.setLockedAt(null);
        userRepo.save(u);

        audit.log(new AuditEvent(actorUserId, AuditAction.USER_PASSWORD_RESET, "USER",
                String.valueOf(u.getId()), null, null,
                "admin password reset", clientIp, OffsetDateTime.now(ZoneOffset.UTC)));

        email.sendPasswordReset(u.getEmail(), u.getUserId(), temp);
    }

    private Set<Role> resolveRoles(List<String> roleCodes) {
        Set<Role> out = new LinkedHashSet<>();
        for (String code : roleCodes) {
            Role r = roleRepo.findByRoleCode(code).orElseThrow(() ->
                    new UnprocessableEntityException("USER_003", "unknown role: " + code));
            out.add(r);
        }
        return out;
    }

    private void terminateSessions(String userId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal == null) continue;
            String name = principal.toString();
            if (name.equals(userId)) {
                for (SessionInformation si : sessionRegistry.getAllSessions(principal, false)) {
                    si.expireNow();
                }
            }
        }
    }

    static String generateTempPassword(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(TEMP_PW_ALPHABET.charAt(RNG.nextInt(TEMP_PW_ALPHABET.length())));
        return sb.toString();
    }

    private String jsonOf(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }
}
