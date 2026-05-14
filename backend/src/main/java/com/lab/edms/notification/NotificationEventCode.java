package com.lab.edms.notification;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

/**
 * notification_event_codes 참조 테이블.
 * defaultChannels: DB에서 TEXT[]로 저장되나 JPA/Hibernate에서 String으로 읽어 파싱.
 * 예: "IN_APP,EMAIL_SMTP" → Arrays.asList(...)
 *
 * Hibernate 6 네이티브 array 타입을 사용하지 않고 단순 String 직렬화 방식으로 처리.
 * DB 칼럼은 columnDefinition="TEXT[]"이지만 JDBC 레벨에서 배열을 String으로 변환하는 대신
 * array_to_string / string_to_array를 사용하는 Repository 쿼리나 서비스 레이어에서 처리한다.
 *
 * 주의: 단순 조회(findById)는 Hibernate가 TEXT[]를 String으로 자동 매핑하지 못하므로
 * defaultChannels 필드는 String[] 타입으로 매핑한다.
 */
@Entity
@Table(name = "notification_event_codes")
public class NotificationEventCode {

    @Id
    @Column(name = "code", length = 60)
    private String code;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "default_severity", nullable = false, length = 20)
    private String defaultSeverity = "INFO";

    /**
     * PostgreSQL TEXT[] 칼럼.
     * Hibernate 6 기본 지원: String[] → text[] 매핑 사용.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "default_channels", columnDefinition = "TEXT[]", nullable = false)
    private String[] defaultChannels = new String[]{"IN_APP"};

    @Column(name = "template_key", nullable = false, length = 80)
    private String templateKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    // Getters
    public String getCode() { return code; }
    public String getCategory() { return category; }
    public String getDefaultSeverity() { return defaultSeverity; }
    public String[] getDefaultChannels() { return defaultChannels; }
    public String getTemplateKey() { return templateKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setCode(String code) { this.code = code; }
    public void setCategory(String category) { this.category = category; }
    public void setDefaultSeverity(String defaultSeverity) { this.defaultSeverity = defaultSeverity; }
    public void setDefaultChannels(String[] defaultChannels) { this.defaultChannels = defaultChannels; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
