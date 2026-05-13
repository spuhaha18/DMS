package com.lab.edms.project;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;

@Entity
@Table(name = "research_project_types")
@Audited
public class ResearchProjectType {

    @Id
    @Column(name = "type_code", length = 50)
    private String typeCode;

    @Column(name = "type_name_kr", nullable = false, length = 200)
    private String typeNameKr;

    @Column(name = "type_name_en", length = 200)
    private String typeNameEn;

    @Column(name = "retention_years")
    private Integer retentionYears;

    @Column(name = "is_perpetual", nullable = false)
    private boolean perpetual;

    @Column(name = "sop_table_row", length = 50)
    private String sopTableRow;

    @Column(name = "note")
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getTypeCode() { return typeCode; }
    public String getTypeNameKr() { return typeNameKr; }
    public String getTypeNameEn() { return typeNameEn; }
    public Integer getRetentionYears() { return retentionYears; }
    public boolean isPerpetual() { return perpetual; }
    public String getSopTableRow() { return sopTableRow; }
    public String getNote() { return note; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setTypeCode(String v) { this.typeCode = v; }
    public void setTypeNameKr(String v) { this.typeNameKr = v; }
    public void setTypeNameEn(String v) { this.typeNameEn = v; }
    public void setRetentionYears(Integer v) { this.retentionYears = v; }
    public void setPerpetual(boolean v) { this.perpetual = v; }
    public void setSopTableRow(String v) { this.sopTableRow = v; }
    public void setNote(String v) { this.note = v; }
    public void setActive(boolean v) { this.active = v; }
}
