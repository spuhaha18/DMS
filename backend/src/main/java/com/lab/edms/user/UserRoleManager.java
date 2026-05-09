package com.lab.edms.user;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

@Component
public class UserRoleManager {

    private final EntityManager em;

    public UserRoleManager(EntityManager em) {
        this.em = em;
    }

    /**
     * Assigns a set of roles to a newly-created user. Flushes and refreshes the user.
     */
    public void assignRoles(User user, Set<Role> roles) {
        for (Role r : roles) {
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(r);
            ur.setAssignedAt(OffsetDateTime.now());
            em.persist(ur);
        }
        em.flush();
        em.refresh(user);
    }

    /**
     * Computes delta vs current roles on the user, performs the em.remove/persist/flush,
     * and returns a RoleDelta describing which role codes were added and removed.
     */
    public RoleDelta applyRoleDelta(User user, Set<Role> targetRoles) {
        Set<String> targetCodes = new TreeSet<>();
        for (Role r : targetRoles) targetCodes.add(r.getRoleCode());

        Set<String> existing = new TreeSet<>();
        for (UserRole ur : user.getRoles()) existing.add(ur.getRole().getRoleCode());

        Set<String> removed = new TreeSet<>();
        Iterator<UserRole> it = user.getRoles().iterator();
        while (it.hasNext()) {
            UserRole ur = it.next();
            String code = ur.getRole().getRoleCode();
            if (!targetCodes.contains(code)) {
                em.remove(ur);
                it.remove();
                removed.add(code);
            }
        }

        Set<String> added = new TreeSet<>();
        for (Role r : targetRoles) {
            if (!existing.contains(r.getRoleCode())) {
                UserRole ur = new UserRole();
                ur.setUser(user);
                ur.setRole(r);
                ur.setAssignedAt(OffsetDateTime.now());
                em.persist(ur);
                added.add(r.getRoleCode());
            }
        }

        em.flush();
        return new RoleDelta(added, removed);
    }
}
