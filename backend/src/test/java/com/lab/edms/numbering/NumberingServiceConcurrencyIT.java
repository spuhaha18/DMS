package com.lab.edms.numbering;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
class NumberingServiceConcurrencyIT {

    static final int THREADS = 10;
    static final int PER_THREAD = 100;
    static final int TOTAL = THREADS * PER_THREAD;

    @Autowired NumberingService numberingService;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long sopCategoryId;

    @BeforeEach
    void setUp() {
        sopCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM document_categories WHERE category_code = 'SOP'",
                Long.class);
        // Reset counter to ensure gap-less assertion starts from 1
        jdbcTemplate.update(
                "DELETE FROM numbering_counters WHERE category_id = ? AND scope_key = ?",
                sopCategoryId, "QC");
    }

    @Test
    void 동시_채번_중복_없음_그리고_갭_없음() throws Exception {
        var executor = Executors.newFixedThreadPool(THREADS);
        var startGate = new CountDownLatch(1);
        var results = new ConcurrentLinkedQueue<String>();
        var futures = new ArrayList<Future<?>>();

        for (int t = 0; t < THREADS; t++) {
            futures.add(executor.submit(() -> {
                startGate.await();
                for (int i = 0; i < PER_THREAD; i++) {
                    String docNumber = numberingService
                            .issue(sopCategoryId, new NumberingService.IssueContext("QC", null))
                            .docNumber();
                    results.add(docNumber);
                }
                return null;
            }));
        }

        long start = System.currentTimeMillis();
        startGate.countDown();
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(results).hasSize(TOTAL);
        assertThat(new HashSet<>(results)).hasSize(TOTAL); // no duplicates

        // gap-less: SOP-QC-001 ~ SOP-QC-TOTAL exactly
        var seqs = results.stream()
                .map(s -> Integer.parseInt(s.substring(s.lastIndexOf('-') + 1)))
                .sorted()
                .toList();
        assertThat(seqs).containsExactlyElementsOf(
                IntStream.rangeClosed(1, TOTAL).boxed().toList());

        // counter in sync
        Integer counterSeq = jdbcTemplate.queryForObject(
                "SELECT current_seq FROM numbering_counters WHERE category_id = ? AND scope_key = ?",
                Integer.class, sopCategoryId, "QC");
        assertThat(counterSeq).isEqualTo(TOTAL);

        // performance guard
        assertThat(elapsed).isLessThan(10_000L);
    }
}
