package com.lab.edms.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SearchRepositoryImpl implements SearchRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<SearchResultDto> search(
            String query,
            String categoryCode,
            String department,
            String state,
            OffsetDateTime from,
            OffsetDateTime to,
            List<String> permittedRoles,
            Long userId,
            Pageable pageable
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, dv.id, d.doc_number, dv.title, dv.state,
                       c.category_code, d.department, dv.effective_date,
                       u.user_id,
                       ts_rank(dv.search_vector, plainto_tsquery('simple', :query)) AS rank
                FROM documents d
                JOIN document_versions dv ON dv.document_id = d.id
                JOIN document_categories c ON c.id = d.category_id
                JOIN users u ON u.id = d.created_by
                JOIN permissions p ON p.role_id IN (
                    SELECT r.id FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                    WHERE ur.user_id = :userId
                )
                WHERE dv.search_vector @@ plainto_tsquery('simple', :query)
                  AND (p.category_id = d.category_id OR p.category_id IS NULL)
                  AND (p.department = d.department OR p.department IS NULL)
                """);

        if (categoryCode != null && !categoryCode.isBlank()) {
            sql.append("  AND c.category_code = :categoryCode\n");
        }
        if (department != null && !department.isBlank()) {
            sql.append("  AND d.department = :department\n");
        }
        if (state != null && !state.isBlank()) {
            sql.append("  AND dv.state = :state\n");
        }
        if (from != null) {
            sql.append("  AND dv.effective_date >= :from\n");
        }
        if (to != null) {
            sql.append("  AND dv.effective_date <= :to\n");
        }

        sql.append("ORDER BY rank DESC");

        Query dataQuery = em.createNativeQuery(sql.toString());
        setCommonParams(dataQuery, query, userId, categoryCode, department, state, from, to);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        List<SearchResultDto> results = new ArrayList<>();
        for (Object[] row : rows) {
            Long documentId   = ((Number) row[0]).longValue();
            Long versionId    = ((Number) row[1]).longValue();
            String docNumber  = (String) row[2];
            String title      = (String) row[3];
            String stateVal   = (String) row[4];
            String categoryCodeVal = (String) row[5];
            String dept       = (String) row[6];
            OffsetDateTime effectiveDate = toOffsetDateTime(row[7]);
            String authorUserId = (String) row[8];
            float rank = row[9] instanceof Number n ? n.floatValue() : 0f;

            results.add(new SearchResultDto(
                    documentId, versionId, docNumber, title, stateVal,
                    categoryCodeVal, dept, effectiveDate, authorUserId, rank
            ));
        }

        // Count query (deduplicated by document_id + version_id)
        StringBuilder countSql = new StringBuilder("""
                SELECT COUNT(*) FROM (
                    SELECT d.id, dv.id
                    FROM documents d
                    JOIN document_versions dv ON dv.document_id = d.id
                    JOIN document_categories c ON c.id = d.category_id
                    JOIN users u ON u.id = d.created_by
                    JOIN permissions p ON p.role_id IN (
                        SELECT r.id FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                        WHERE ur.user_id = :userId
                    )
                    WHERE dv.search_vector @@ plainto_tsquery('simple', :query)
                      AND (p.category_id = d.category_id OR p.category_id IS NULL)
                      AND (p.department = d.department OR p.department IS NULL)
                """);

        if (categoryCode != null && !categoryCode.isBlank()) {
            countSql.append("  AND c.category_code = :categoryCode\n");
        }
        if (department != null && !department.isBlank()) {
            countSql.append("  AND d.department = :department\n");
        }
        if (state != null && !state.isBlank()) {
            countSql.append("  AND dv.state = :state\n");
        }
        if (from != null) {
            countSql.append("  AND dv.effective_date >= :from\n");
        }
        if (to != null) {
            countSql.append("  AND dv.effective_date <= :to\n");
        }
        countSql.append(") sub");

        Query countQuery = em.createNativeQuery(countSql.toString());
        setCommonParams(countQuery, query, userId, categoryCode, department, state, from, to);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }

    private void setCommonParams(Query q, String query, Long userId,
                                 String categoryCode, String department,
                                 String state, OffsetDateTime from, OffsetDateTime to) {
        q.setParameter("query", query);
        q.setParameter("userId", userId);
        if (categoryCode != null && !categoryCode.isBlank()) {
            q.setParameter("categoryCode", categoryCode);
        }
        if (department != null && !department.isBlank()) {
            q.setParameter("department", department);
        }
        if (state != null && !state.isBlank()) {
            q.setParameter("state", state);
        }
        if (from != null) {
            q.setParameter("from", java.sql.Date.valueOf(from.toLocalDate()));
        }
        if (to != null) {
            q.setParameter("to", java.sql.Date.valueOf(to.toLocalDate()));
        }
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp t) {
            return t.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
