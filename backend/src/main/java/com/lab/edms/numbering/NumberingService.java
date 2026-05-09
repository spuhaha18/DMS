package com.lab.edms.numbering;

import com.lab.edms.category.DocumentCategory;
import com.lab.edms.category.DocumentCategoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NumberingService {

    private static final Pattern SEQ_PATTERN = Pattern.compile("\\{SEQ:(\\d+)\\}");
    private static final String GLOBAL_SCOPE = "__global__";

    private final NumberingTemplateRepository templateRepo;
    private final NumberingCounterRepository counterRepo;
    private final DocumentCategoryRepository categoryRepo;
    private final JdbcTemplate jdbc;

    public NumberingService(NumberingTemplateRepository templateRepo,
                             NumberingCounterRepository counterRepo,
                             DocumentCategoryRepository categoryRepo,
                             JdbcTemplate jdbc) {
        this.templateRepo = templateRepo;
        this.counterRepo = counterRepo;
        this.categoryRepo = categoryRepo;
        this.jdbc = jdbc;
    }

    public record IssueContext(String department, String projectCode) {}
    public record IssueResult(String docNumber, int seq) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IssueResult issue(Long categoryId, IssueContext ctx) {
        NumberingTemplate template = templateRepo.findByCategoryId(categoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No numbering template for category: " + categoryId));

        DocumentCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        String scopeKey = computeScopeKey(template.getCounterScope(), ctx);

        // SELECT FOR UPDATE — blocks if another tx holds the lock
        NumberingCounter counter = counterRepo.findForUpdate(categoryId, scopeKey)
                .orElseGet(() -> {
                    // Native SQL INSERT IGNORE — avoids JPA flush exception on conflict
                    jdbc.update(
                            "INSERT INTO numbering_counters (category_id, scope_key, current_seq) " +
                            "VALUES (?, ?, 0) ON CONFLICT (category_id, scope_key) DO NOTHING",
                            categoryId, scopeKey);
                    // Re-query with lock after insert-or-ignore
                    return counterRepo.findForUpdate(categoryId, scopeKey)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Counter not found after insert for: " + categoryId + "/" + scopeKey));
                });

        int nextSeq = counter.getCurrentSeq() + 1;
        counter.setCurrentSeq(nextSeq);
        counterRepo.save(counter);

        String docNumber = applyPlaceholders(
                template.getFormatPattern(), category.getCategoryCode(), ctx, nextSeq);

        return new IssueResult(docNumber, nextSeq);
    }

    /** Preview — reads current_seq without locking or incrementing. */
    public IssueResult peek(Long categoryId, IssueContext ctx) {
        NumberingTemplate template = templateRepo.findByCategoryId(categoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No numbering template for category: " + categoryId));

        DocumentCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        String scopeKey = computeScopeKey(template.getCounterScope(), ctx);
        int currentSeq = counterRepo.findByCategoryIdAndScopeKey(categoryId, scopeKey)
                .map(NumberingCounter::getCurrentSeq).orElse(0);

        int nextSeq = currentSeq + 1;
        String docNumber = applyPlaceholders(
                template.getFormatPattern(), category.getCategoryCode(), ctx, nextSeq);
        return new IssueResult(docNumber, nextSeq);
    }

    private String computeScopeKey(String scope, IssueContext ctx) {
        return switch (scope) {
            case "PER_DEPT" -> {
                if (ctx.department() == null || ctx.department().isBlank())
                    throw new IllegalArgumentException("PER_DEPT scope requires department");
                yield ctx.department();
            }
            case "PER_PRODUCT" -> {
                if (ctx.projectCode() == null || ctx.projectCode().isBlank())
                    throw new IllegalArgumentException("PER_PRODUCT scope requires projectCode");
                yield ctx.projectCode();
            }
            case "PER_YEAR" ->
                    String.valueOf(LocalDate.now(ZoneId.of("Asia/Seoul")).getYear());
            case "GLOBAL" -> GLOBAL_SCOPE;
            default -> throw new IllegalArgumentException("Unknown counter_scope: " + scope);
        };
    }

    private String applyPlaceholders(String pattern, String typeCode, IssueContext ctx, int seq) {
        String result = pattern
                .replace("{TYPE}", typeCode)
                .replace("{DEPT}", ctx.department() != null ? ctx.department() : "")
                .replace("{PROD}", ctx.projectCode() != null ? ctx.projectCode() : "")
                .replace("{YEAR}", String.valueOf(LocalDate.now(ZoneId.of("Asia/Seoul")).getYear()));

        Matcher matcher = SEQ_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            int width = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sb, String.format("%0" + width + "d", seq));
        }
        if (!found) {
            throw new IllegalArgumentException("Pattern has no {SEQ:N} placeholder: " + pattern);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
