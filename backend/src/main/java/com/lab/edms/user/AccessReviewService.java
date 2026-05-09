package com.lab.edms.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class AccessReviewService {

    private final JdbcTemplate jdbc;

    public AccessReviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public String exportCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("user_id,full_name,email,department,title,status,valid_from,valid_until,last_login_at,role_codes\n");

        jdbc.query("""
                SELECT user_id, full_name, email, department, title, status,
                       valid_from, valid_until, last_login_at, role_codes
                  FROM v_access_review
                 ORDER BY user_id
                """, rs -> {
            sb.append(csv(rs.getString("user_id"))).append(',')
              .append(csv(rs.getString("full_name"))).append(',')
              .append(csv(rs.getString("email"))).append(',')
              .append(csv(rs.getString("department"))).append(',')
              .append(csv(rs.getString("title"))).append(',')
              .append(csv(rs.getString("status"))).append(',')
              .append(csv(stringOf(rs, "valid_from"))).append(',')
              .append(csv(stringOf(rs, "valid_until"))).append(',')
              .append(csv(stringOf(rs, "last_login_at"))).append(',')
              .append(csv(rs.getString("role_codes")))
              .append('\n');
        });
        return sb.toString();
    }

    private static String stringOf(ResultSet rs, String col) throws SQLException {
        Object v = rs.getObject(col);
        return v == null ? "" : v.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
