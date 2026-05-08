package com.lab.edms.user;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Audited
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    private String userId;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "auth_provider", nullable = false, length = 20)
    private String authProvider = "LOCAL";

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "title", length = 50)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "force_change_pw", nullable = false)
    private boolean forceChangePw;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserRole> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public UserStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isForceChangePw() { return forceChangePw; }
    public int getFailedAttempts() { return failedAttempts; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public Set<UserRole> getRoles() { return roles; }
    public String getAuthProvider() { return authProvider; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(UserStatus status) { this.status = status; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setForceChangePw(boolean v) { this.forceChangePw = v; }
    public void setFailedAttempts(int n) { this.failedAttempts = n; }
    public void setLockedAt(OffsetDateTime t) { this.lockedAt = t; }
}
