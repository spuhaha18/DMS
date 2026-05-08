package com.lab.edms.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "password_history")
public class PasswordHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pw_hash", nullable = false, length = 255)
    private String pwHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public PasswordHistory() {}
    public PasswordHistory(Long userId, String pwHash) {
        this.userId = userId;
        this.pwHash = pwHash;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPwHash() { return pwHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
