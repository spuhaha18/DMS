package com.lab.edms.training;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "training_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "version_id"}))
public class TrainingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completion_sig_id")
    private Long completionSigId;

    protected TrainingAssignment() {}

    public TrainingAssignment(Long userId, Long versionId, Long assignedBy, OffsetDateTime dueAt) {
        this.userId = userId;
        this.versionId = versionId;
        this.assignedBy = assignedBy;
        this.dueAt = dueAt;
        this.assignedAt = OffsetDateTime.now();
    }

    public void complete(OffsetDateTime completedAt, Long sigId) {
        this.completedAt = completedAt;
        this.completionSigId = sigId;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getVersionId() { return versionId; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public Long getAssignedBy() { return assignedBy; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public Long getCompletionSigId() { return completionSigId; }
}
