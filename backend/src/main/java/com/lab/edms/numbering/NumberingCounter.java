package com.lab.edms.numbering;

import jakarta.persistence.*;

@Entity
@Table(name = "numbering_counters")
public class NumberingCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "scope_key", nullable = false, length = 100)
    private String scopeKey;

    @Column(name = "current_seq", nullable = false)
    private int currentSeq = 0;

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getScopeKey() { return scopeKey; }
    public int getCurrentSeq() { return currentSeq; }

    public void setCategoryId(Long v) { this.categoryId = v; }
    public void setScopeKey(String v) { this.scopeKey = v; }
    public void setCurrentSeq(int v) { this.currentSeq = v; }
}
