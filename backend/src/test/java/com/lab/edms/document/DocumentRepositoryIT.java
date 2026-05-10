package com.lab.edms.document;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD: M4 repository 메서드 검증.
 * 1. lockForUpdate()           — SELECT FOR UPDATE, 동일 entity 반환
 * 2. findInFlightByDocumentIdExcluding() — excludeId 제외 in-flight 1개만 반환
 * 3. findEffectiveByDocumentIdExcluding() — excludeId 일치 시 empty, 다른 ID 제외 시 반환
 */
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class DocumentRepositoryIT {

    @Autowired DocumentRepository documentRepo;
    @Autowired DocumentVersionRepository versionRepo;
    @Autowired JdbcTemplate jdbc;

    private Long adminUserId;
    private Long sopCategoryId;
    private String adminDeptCode;

    @BeforeEach
    void setUp() {
        adminUserId = jdbc.queryForObject(
                "SELECT id FROM users WHERE user_id = 'admin'", Long.class);
        sopCategoryId = jdbc.queryForObject(
                "SELECT id FROM document_categories WHERE category_code = 'SOP'", Long.class);
        // admin 사용자의 department가 V11에서 departments 테이블에 등록되어 있음
        adminDeptCode = jdbc.queryForObject(
                "SELECT department FROM users WHERE user_id = 'admin'", String.class);
    }

    // --- 헬퍼 ---

    private Document saveDocument(String docNumber) {
        Document d = new Document();
        d.setDocNumber(docNumber);
        d.setCategoryId(sopCategoryId);
        d.setDepartment(adminDeptCode);
        d.setTitle("Test Document " + docNumber);
        d.setOwnerId(adminUserId);
        d.setCreatedBy(adminUserId);
        d.setConfidential(false);
        return documentRepo.save(d);
    }

    private DocumentVersion saveVersion(Long documentId, String state) {
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(documentId);
        v.setState(state);
        v.setTitle("Version for doc " + documentId);
        v.setCreatedBy(adminUserId);
        v.setUpdatedBy(adminUserId);
        return versionRepo.save(v);
    }

    // --- 테스트 1: lockForUpdate ---

    @Test
    void lockForUpdate_존재하는_Document_조회_동일_entity_반환() {
        // RED→GREEN: lockForUpdate가 저장한 document와 동일한 id 반환
        Document saved = saveDocument("SOP-IT-LOCK-001");

        Optional<Document> found = documentRepo.lockForUpdate(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getDocNumber()).isEqualTo("SOP-IT-LOCK-001");
    }

    @Test
    void lockForUpdate_존재하지_않는_id_empty_반환() {
        Optional<Document> found = documentRepo.lockForUpdate(Long.MAX_VALUE);

        assertThat(found).isEmpty();
    }

    // --- 테스트 2: findInFlightByDocumentIdExcluding ---

    @Test
    void findInFlightByDocumentIdExcluding_같은_문서에_DRAFT_2개_excludeId_지정시_1개만_반환() {
        // 동일 document에 DRAFT 버전 2개 생성
        Document doc = saveDocument("SOP-IT-INFLIGHT-001");
        DocumentVersion v1 = saveVersion(doc.getId(), "DRAFT");
        DocumentVersion v2 = saveVersion(doc.getId(), "DRAFT");

        // v1 기준으로 exclude → v2 1개만 반환
        List<DocumentVersion> result = versionRepo
                .findInFlightByDocumentIdExcluding(doc.getId(), v1.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(v2.getId());
    }

    @Test
    void findInFlightByDocumentIdExcluding_EFFECTIVE_상태는_포함하지_않음() {
        Document doc = saveDocument("SOP-IT-INFLIGHT-002");
        DocumentVersion draft  = saveVersion(doc.getId(), "DRAFT");
        DocumentVersion effective = saveVersion(doc.getId(), "EFFECTIVE");

        // draft 를 exclude → EFFECTIVE 는 in-flight 아님 → empty
        List<DocumentVersion> result = versionRepo
                .findInFlightByDocumentIdExcluding(doc.getId(), draft.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findInFlightByDocumentIdExcluding_UNDER_REVIEW_포함() {
        Document doc = saveDocument("SOP-IT-INFLIGHT-003");
        DocumentVersion v1 = saveVersion(doc.getId(), "UNDER_REVIEW");
        DocumentVersion v2 = saveVersion(doc.getId(), "DRAFT");

        // v2 exclude → UNDER_REVIEW 상태인 v1 반환
        List<DocumentVersion> result = versionRepo
                .findInFlightByDocumentIdExcluding(doc.getId(), v2.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(v1.getId());
    }

    // --- 테스트 3: findEffectiveByDocumentIdExcluding ---

    @Test
    void findEffectiveByDocumentIdExcluding_excludeId_동일_EFFECTIVE_이면_empty() {
        Document doc = saveDocument("SOP-IT-EFFECTIVE-001");
        DocumentVersion effective = saveVersion(doc.getId(), "EFFECTIVE");

        // 자기 자신을 exclude → empty
        Optional<DocumentVersion> result = versionRepo
                .findEffectiveByDocumentIdExcluding(doc.getId(), effective.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findEffectiveByDocumentIdExcluding_다른_버전_exclude시_EFFECTIVE_반환() {
        Document doc = saveDocument("SOP-IT-EFFECTIVE-002");
        DocumentVersion effective = saveVersion(doc.getId(), "EFFECTIVE");
        DocumentVersion draft     = saveVersion(doc.getId(), "DRAFT");

        // draft를 exclude → EFFECTIVE 버전 반환
        Optional<DocumentVersion> result = versionRepo
                .findEffectiveByDocumentIdExcluding(doc.getId(), draft.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(effective.getId());
    }

    @Test
    void findEffectiveByDocumentIdExcluding_EFFECTIVE_없으면_empty() {
        Document doc = saveDocument("SOP-IT-EFFECTIVE-003");
        DocumentVersion draft = saveVersion(doc.getId(), "DRAFT");

        Optional<DocumentVersion> result = versionRepo
                .findEffectiveByDocumentIdExcluding(doc.getId(), draft.getId());

        assertThat(result).isEmpty();
    }
}
