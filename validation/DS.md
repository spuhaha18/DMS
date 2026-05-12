# Design Specification (DS)
## Electronic Document Management System (EDMS)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-DS-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| URS 참조 | VAL-URS-001 v0.3 |
| FS 참조 | VAL-FS-001 v0.1 |
| 작성자 | TBD |
| 검토자 | TBD |
| 승인자 | TBD (QA) |

---

## 목차

1. [목적 및 범위](#1-목적-및-범위)
2. [참조 문서](#2-참조-문서)
3. [시스템 아키텍처](#3-시스템-아키텍처)
4. [데이터베이스 설계](#4-데이터베이스-설계)
5. [백엔드 컴포넌트 설계](#5-백엔드-컴포넌트-설계)
6. [API 설계](#6-api-설계)
7. [프론트엔드 설계](#7-프론트엔드-설계)
8. [핵심 알고리즘 설계](#8-핵심-알고리즘-설계)
9. [보안 설계](#9-보안-설계)
10. [인프라 및 배포 설계](#10-인프라-및-배포-설계)
11. [FS-DS 추적성 매트릭스](#11-fs-ds-추적성-매트릭스)
12. [변경 이력](#12-변경-이력)

---

## 1. 목적 및 범위

본 문서는 VAL-FS-001에 정의된 기능 요구사항을 구현하기 위한 기술 설계를 기술한다. 데이터베이스 스키마, 시스템 컴포넌트, API 명세, 핵심 알고리즘, 인프라 구성을 포함한다.

본 DS는 개발팀의 구현 기준이 되며, IQ(설치적격성) 프로토콜의 직접 입력이 된다. DS 변경 시 Change Control SOP에 따라 영향 평가 후 버전을 갱신한다.

**범위**: Phase 1.a — SOP, Method, Specification, Form 문서 카테고리 대상.

**기술 스택**:
- Backend: Spring Boot 3.3, Java 21, Spring Security 6, Spring Data JPA, Hibernate Envers
- Database: PostgreSQL 16
- Object Storage: MinIO (Object Lock 지원 버전)
- PDF 변환: LibreOffice 7.x headless
- PDF 렌디션: Apache PDFBox 3.x (워터마크)
- Frontend: Vue 3 (Composition API) + TypeScript + Vite
- PDF 뷰어: pdf.js (Mozilla)
- 빌드/배포: Docker Compose (개발), Ansible (운영)
- DB 마이그레이션: Flyway 10

---

## 2. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| VAL-URS-001 v0.3 | User Requirements Specification |
| VAL-FS-001 v0.1 | Functional Specification |
| VAL-VP-001 | Validation Plan |
| 21 CFR Part 11 | Electronic Records; Electronic Signatures |
| EU GMP Annex 11 | Computerised Systems |
| GAMP 5 (2nd Ed.) | Risk-Based Approach to GxP Computerized Systems |

---

## 3. 시스템 아키텍처

### 3.1 전체 컴포넌트 구성

```
┌─────────────────────────────────────────────────────────────────┐
│                     사내망 (Closed Network)                      │
│                                                                  │
│  ┌──────────────────────┐                                        │
│  │  사용자 브라우저       │  Chrome / Edge (사내 PC)              │
│  │  Vue 3 + pdf.js      │                                        │
│  └──────────┬───────────┘                                        │
│             │ HTTPS (사내 CA 인증서, TLS 1.2+)                   │
│             ▼                                                    │
│  ┌──────────────────────┐                                        │
│  │  Nginx 1.26          │  TLS 종단, SPA 정적파일 서빙,           │
│  │  (Reverse Proxy)     │  /api → Spring Boot 프록시             │
│  └──────────┬───────────┘                                        │
│             │ HTTP (사내망 내부)                                  │
│             ▼                                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   Spring Boot 3 API Server               │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐  │   │
│  │  │  auth/   │  │ document/│  │lifecycle/│  │  sig/   │  │   │
│  │  │ session  │  │ version  │  │  state   │  │  hash   │  │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └─────────┘  │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐  │   │
│  │  │  audit/  │  │ search/  │  │  notify/ │  │  admin/ │  │   │
│  │  │  envers  │  │ tsvector │  │ SSE+SMTP │  │ master  │  │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └─────────┘  │   │
│  └──────┬──────────────┬─────────────┬──────────────┬───────┘   │
│         │              │             │              │            │
│         ▼              ▼             ▼              ▼            │
│  ┌────────────┐ ┌──────────┐ ┌────────────┐ ┌──────────────┐   │
│  │PostgreSQL16│ │  MinIO   │ │LibreOffice │ │  SMTP Relay  │   │
│  │(메타·감사)  │ │(원본·PDF │ │ 7.x        │ │ (사내       │   │
│  │audit role  │ │Object    │ │ headless   │ │  Exchange)   │   │
│  │INSERT-only │ │Lock WORM)│ │ (PDF 변환) │ │              │   │
│  └──────┬─────┘ └──────────┘ └────────────┘ └──────────────┘   │
│         │ Logical Replication                                    │
│         ▼                                                        │
│  ┌────────────┐                                                  │
│  │  DR Replica│  (별도 사이트, RPO 1h / RTO 4h)                 │
│  └────────────┘                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 패키지 구조 (Backend)

```
com.lab.edms/
├── auth/
│   ├── AuthProvider.java              # 인증 공급자 인터페이스
│   ├── LocalAuthProvider.java         # Phase 1 구현체 (DB 기반)
│   ├── AuthService.java
│   ├── SessionManager.java
│   └── dto/LoginRequest.java
├── user/
│   ├── User.java                      # @Entity, @Audited
│   ├── Role.java                      # @Entity
│   ├── Permission.java                # @Entity (역할×카테고리×범위)
│   ├── UserService.java
│   └── UserController.java
├── document/
│   ├── Document.java                  # @Entity (채번 단위)
│   ├── DocumentVersion.java           # @Entity
│   ├── DocumentFile.java              # @Entity (MinIO 참조)
│   ├── DocumentService.java
│   ├── DocumentController.java
│   ├── NumberingService.java          # 채번 템플릿 적용
│   └── lifecycle/
│       ├── DocumentState.java         # Enum: DRAFT, UNDER_REVIEW, ...
│       ├── LifecycleStateMachine.java
│       ├── WorkflowTemplate.java      # @Entity
│       ├── WorkflowTemplateStep.java  # @Entity
│       ├── WorkflowInstance.java      # @Entity
│       ├── WorkflowStepInstance.java  # @Entity
│       └── WorkflowService.java
├── signature/
│   ├── SignatureManifest.java         # @Entity, INSERT-only
│   ├── HashChainService.java          # SHA-256 해시체인
│   └── SignatureController.java
├── audit/
│   ├── AuditLog.java                  # @Entity, INSERT-only
│   ├── AuditService.java
│   ├── AuditCheckpoint.java           # @Entity (일별 앵커)
│   ├── AuditController.java
│   └── config/EnversConfig.java
├── rendition/
│   ├── PdfConversionService.java      # LibreOffice 호출
│   ├── WatermarkService.java          # PDFBox 워터마크
│   └── RenditionQueue.java            # 비동기 변환 큐
├── storage/
│   ├── MinioStorageService.java       # MinIO Java SDK
│   └── StoragePolicy.java             # Object Lock 정책
├── search/
│   ├── SearchService.java             # tsvector 검색
│   └── SearchController.java
├── notification/
│   ├── NotificationService.java
│   ├── SseNotificationService.java    # Server-Sent Events
│   ├── EmailNotificationService.java  # JavaMailSender
│   └── PeriodicReviewScheduler.java   # @Scheduled
├── admin/
│   ├── CategoryService.java
│   ├── NumberingTemplateService.java
│   ├── WorkflowTemplateService.java
│   └── AdminController.java
├── migration/
│   ├── BulkImportService.java
│   └── MigrationController.java       # CLI 진입점 (Spring Shell)
└── config/
    ├── SecurityConfig.java            # Spring Security 설정
    ├── JpaConfig.java
    ├── MinioConfig.java
    └── AuditConfig.java               # INSERT-only role 강제
```

### 3.3 프론트엔드 구조

```
frontend/src/
├── views/
│   ├── LoginView.vue
│   ├── DashboardView.vue
│   ├── documents/
│   │   ├── DocumentListView.vue
│   │   ├── DocumentDetailView.vue
│   │   └── DocumentUploadView.vue
│   ├── workflow/
│   │   ├── ReviewView.vue
│   │   └── ApprovalView.vue
│   ├── audit/
│   │   └── AuditLogView.vue
│   └── admin/
│       ├── UserManagementView.vue
│       ├── RoleManagementView.vue
│       ├── CategoryManagementView.vue
│       ├── WorkflowTemplateView.vue
│       └── NumberingTemplateView.vue
├── components/
│   ├── PdfViewer/
│   │   ├── PdfViewer.vue             # pdf.js wrapper
│   │   ├── usePdfViewer.ts           # ArrayBuffer 유지, 페이지 네비게이션
│   │   └── VerifyButton.vue          # Web Crypto SHA-256 무결성 검증 (M7.1)
│   ├── SignatureDialog.vue
│   ├── NotificationCenter.vue        # SSE 수신
│   └── SessionWarning.vue            # 세션 만료 경고
├── stores/
│   ├── auth.ts                       # 세션 상태 (Pinia)
│   ├── notification.ts
│   └── document.ts
├── api/
│   ├── client.ts                     # Axios 인스턴스
│   └── endpoints/                    # API 호출 함수
└── router/index.ts                   # Vue Router (인증 가드)
```

---

## 4. 데이터베이스 설계

### 4.1 설계 원칙

- 모든 테이블은 `created_at`, `created_by` 컬럼을 포함하여 생성 이력을 보존한다.
- `audit_logs`, `signature_manifests`, `audit_checkpoints` 테이블은 `UPDATE`, `DELETE` 권한이 없는 전용 DB 역할(`audit_role`)만 `INSERT`를 수행한다.
- 소프트 삭제(soft delete) 대신 비활성화 플래그(`status` 컬럼)를 사용한다. 물리 삭제는 허용하지 않는다.
- 모든 타임스탬프는 `TIMESTAMPTZ` (UTC 저장, 표시 시 KST 변환)를 사용한다.
- 마이그레이션은 Flyway로 관리하며, 스크립트 파일명 형식: `V{순번}__{설명}.sql`

### 4.2 DDL

```sql
-- =============================================================
-- 인증·사용자 영역
-- =============================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(50)  NOT NULL UNIQUE,   -- 로그인 ID
    external_id     VARCHAR(255),                    -- AD/LDAP UID (Phase 2)
    auth_provider   VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',  -- LOCAL | LDAP
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    department      VARCHAR(100) NOT NULL,
    title           VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|LOCKED|DISABLED
    password_hash   VARCHAR(255),                    -- BCrypt
    force_change_pw BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_at       TIMESTAMPTZ,
    valid_from      DATE,
    valid_until     DATE,
    remarks         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50)  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(50)  NOT NULL
);

CREATE TABLE password_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    pw_hash     VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,  -- AUTHOR|REVIEWER|...
    role_name   VARCHAR(100) NOT NULL,
    description TEXT,
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,  -- 시스템 기본 역할 여부
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(50)  NOT NULL
);

CREATE TABLE user_roles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    role_id     BIGINT      NOT NULL REFERENCES roles(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by VARCHAR(50) NOT NULL,
    UNIQUE (user_id, role_id)
);

-- =============================================================
-- 문서 카테고리·채번·워크플로 마스터
-- =============================================================

CREATE TABLE document_categories (
    id              BIGSERIAL PRIMARY KEY,
    category_code   VARCHAR(20)  NOT NULL UNIQUE,  -- SOP|METHOD|SPEC|FORM
    category_name   VARCHAR(100) NOT NULL,
    review_period_months INTEGER NOT NULL DEFAULT 24,  -- 정기검토 주기
    qa_mandatory    BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50) NOT NULL
);

CREATE TABLE numbering_templates (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES document_categories(id) UNIQUE,
    format_pattern  VARCHAR(200) NOT NULL,
    -- 예: "{TYPE}-{DEPT}-{SEQ:3}" | "{TYPE}-{PROD}-{SEQ:3}" | "{DEPT}-F-{SEQ:3}"
    counter_scope   VARCHAR(20) NOT NULL,
    -- PER_DEPT | PER_PRODUCT | PER_YEAR | GLOBAL
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(50) NOT NULL
);

CREATE TABLE numbering_counters (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES document_categories(id),
    scope_key       VARCHAR(100) NOT NULL,
    -- PER_DEPT: 부서코드 / PER_PRODUCT: 제품코드 / PER_YEAR: 연도 / GLOBAL: '__global__'
    current_seq     INTEGER     NOT NULL DEFAULT 0,
    UNIQUE (category_id, scope_key)
);

CREATE TABLE workflow_templates (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES document_categories(id) UNIQUE,
    template_name   VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(50) NOT NULL
);

CREATE TABLE workflow_template_steps (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT      NOT NULL REFERENCES workflow_templates(id),
    step_order      INTEGER     NOT NULL,
    step_type       VARCHAR(20) NOT NULL,  -- REVIEW | APPROVAL
    role_code       VARCHAR(50) NOT NULL,
    min_signers     INTEGER     NOT NULL DEFAULT 1,
    parallel        BOOLEAN     NOT NULL DEFAULT FALSE,
    auto_assign     BOOLEAN     NOT NULL DEFAULT FALSE,
    qa_required     BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (template_id, step_order)
);

-- =============================================================
-- 접근 권한 매트릭스
-- =============================================================

CREATE TABLE permissions (
    id              BIGSERIAL PRIMARY KEY,
    role_id         BIGINT      NOT NULL REFERENCES roles(id),
    category_id     BIGINT      NOT NULL REFERENCES document_categories(id),
    department      VARCHAR(100),  -- NULL = 전 부서
    can_view        BOOLEAN NOT NULL DEFAULT FALSE,
    can_download    BOOLEAN NOT NULL DEFAULT FALSE,
    can_create      BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_draft  BOOLEAN NOT NULL DEFAULT FALSE,
    can_review      BOOLEAN NOT NULL DEFAULT FALSE,
    can_approve     BOOLEAN NOT NULL DEFAULT FALSE,
    can_retire      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(50) NOT NULL,
    UNIQUE (role_id, category_id, department)
);

-- =============================================================
-- 문서·버전·파일
-- =============================================================

CREATE TABLE documents (
    id              BIGSERIAL PRIMARY KEY,
    doc_number      VARCHAR(50)  NOT NULL UNIQUE,   -- 채번 결과 (불변)
    category_id     BIGINT       NOT NULL REFERENCES document_categories(id),
    department      VARCHAR(100) NOT NULL,
    project_code    VARCHAR(100),
    title           VARCHAR(500) NOT NULL,
    owner_id        BIGINT       NOT NULL REFERENCES users(id),
    confidential    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50)  NOT NULL
);
-- doc_number는 채번 후 변경 불가 (애플리케이션 레벨 강제)

CREATE TABLE document_versions (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT       NOT NULL REFERENCES documents(id),
    revision        INTEGER,     -- NULL = 미승인(DRAFT), 승인 시 채번 (0,1,2,...)
    state           VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    -- DRAFT|UNDER_REVIEW|UNDER_APPROVAL|EFFECTIVE|UNDER_REVISION|SUPERSEDED|RETIRED
    title           VARCHAR(500),
    change_summary  TEXT,
    reason_for_change TEXT,
    source_file_key VARCHAR(500),  -- MinIO object key (원본)
    pdf_file_key    VARCHAR(500),  -- MinIO object key (변환 PDF)
    pdf_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING|CONVERTING|DONE|FAILED
    effective_date  DATE,
    expiry_date     DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50)  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(50)  NOT NULL
);

CREATE TABLE document_files (
    id              BIGSERIAL PRIMARY KEY,
    version_id      BIGINT       NOT NULL REFERENCES document_versions(id),
    file_type       VARCHAR(20)  NOT NULL,  -- ORIGINAL | RENDITION
    minio_bucket    VARCHAR(100) NOT NULL,
    minio_key       VARCHAR(500) NOT NULL,
    file_name       VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT,
    content_type    VARCHAR(100),
    sha256_hash     VARCHAR(64),
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    uploaded_by     VARCHAR(50)  NOT NULL
);

-- =============================================================
-- 워크플로 인스턴스
-- =============================================================

CREATE TABLE workflow_instances (
    id              BIGSERIAL PRIMARY KEY,
    version_id      BIGINT       NOT NULL REFERENCES document_versions(id) UNIQUE,
    template_id     BIGINT       NOT NULL REFERENCES workflow_templates(id),
    state           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    -- IN_PROGRESS | COMPLETED | REJECTED | CANCELLED
    current_step    INTEGER      NOT NULL DEFAULT 1,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_by      VARCHAR(50)  NOT NULL,
    completed_at    TIMESTAMPTZ,
    completed_by    VARCHAR(50)
);

CREATE TABLE workflow_step_instances (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT       NOT NULL REFERENCES workflow_instances(id),
    step_order      INTEGER      NOT NULL,
    step_type       VARCHAR(20)  NOT NULL,
    role_code       VARCHAR(50)  NOT NULL,
    min_signers     INTEGER      NOT NULL,
    parallel        BOOLEAN      NOT NULL,
    qa_required     BOOLEAN      NOT NULL,
    state           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | IN_PROGRESS | COMPLETED | REJECTED
    assignees       JSONB,
    -- [{"user_id": 5, "assigned_at": "...", "assigned_by": "..."}]
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    rejection_reason TEXT,
    UNIQUE (workflow_id, step_order)
);

-- =============================================================
-- 전자서명 (INSERT-only — audit_role 전용)
-- =============================================================

CREATE TABLE signature_manifests (
    id              BIGSERIAL PRIMARY KEY,
    version_id      BIGINT       NOT NULL REFERENCES document_versions(id),
    workflow_step_id BIGINT      REFERENCES workflow_step_instances(id),
    signer_id       BIGINT       NOT NULL REFERENCES users(id),
    signer_user_id  VARCHAR(50)  NOT NULL,  -- 비정규화 (불변 이력)
    signer_name     VARCHAR(100) NOT NULL,
    meaning         VARCHAR(30)  NOT NULL,
    -- REVIEWED | APPROVED | QA_APPROVED | ACKNOWLEDGED | RETIRED
    comment         TEXT,
    signed_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    client_ip       VARCHAR(45)  NOT NULL,
    prev_hash       VARCHAR(64)  NOT NULL,
    this_hash       VARCHAR(64)  NOT NULL UNIQUE
);
-- REVOKE UPDATE, DELETE ON signature_manifests FROM app_role;
-- GRANT INSERT, SELECT ON signature_manifests TO audit_role;

-- =============================================================
-- 감사로그 (INSERT-only — audit_role 전용)
-- =============================================================

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT       REFERENCES users(id),  -- NULL = 시스템
    actor_user_id   VARCHAR(50)  NOT NULL,
    action          VARCHAR(100) NOT NULL,
    -- LOGIN_SUCCESS | LOGIN_FAIL | DOCUMENT_CREATE | STATE_TRANSITION | ...
    entity_type     VARCHAR(50),
    entity_id       BIGINT,
    before_value    JSONB,
    after_value     JSONB,
    reason          TEXT,
    client_ip       VARCHAR(45),
    server_ts       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    prev_hash       VARCHAR(64)  NOT NULL,
    this_hash       VARCHAR(64)  NOT NULL UNIQUE
);
-- REVOKE UPDATE, DELETE ON audit_logs FROM app_role;
-- GRANT INSERT, SELECT ON audit_logs TO audit_role;

CREATE TABLE audit_checkpoints (
    id              BIGSERIAL PRIMARY KEY,
    checkpoint_date DATE         NOT NULL UNIQUE,
    merkle_root     VARCHAR(64)  NOT NULL,
    record_count    INTEGER      NOT NULL,
    minio_anchor_key VARCHAR(500) NOT NULL,  -- WORM anchor 파일 경로
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- REVOKE UPDATE, DELETE ON audit_checkpoints FROM app_role;

-- =============================================================
-- 교육 관리
-- =============================================================

CREATE TABLE training_assignments (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id),
    version_id      BIGINT       NOT NULL REFERENCES document_versions(id),
    assigned_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    assigned_by     VARCHAR(50)  NOT NULL,
    due_at          TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    completion_sig_id BIGINT     REFERENCES signature_manifests(id),
    UNIQUE (user_id, version_id)
);

-- =============================================================
-- 알림
-- =============================================================

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    BIGINT       NOT NULL REFERENCES users(id),
    type            VARCHAR(50)  NOT NULL,
    -- WORKFLOW_ASSIGNED | DOCUMENT_EFFECTIVE | REVIEW_DUE | ...
    title           VARCHAR(300) NOT NULL,
    body            TEXT,
    entity_type     VARCHAR(50),
    entity_id       BIGINT,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =============================================================
-- 전문 검색 벡터 (PG tsvector)
-- =============================================================

ALTER TABLE documents
    ADD COLUMN search_vector TSVECTOR;

ALTER TABLE document_versions
    ADD COLUMN search_vector TSVECTOR;

CREATE INDEX idx_documents_search  ON documents  USING GIN(search_vector);
CREATE INDEX idx_docversions_search ON document_versions USING GIN(search_vector);

-- mecab-ko 기반 커스텀 텍스트 검색 설정 (설치 후 적용)
-- CREATE TEXT SEARCH CONFIGURATION korean (...);

-- =============================================================
-- 주요 인덱스
-- =============================================================

CREATE INDEX idx_users_user_id         ON users(user_id);
CREATE INDEX idx_documents_category    ON documents(category_id);
CREATE INDEX idx_documents_dept        ON documents(department);
CREATE INDEX idx_docversions_docid     ON document_versions(document_id);
CREATE INDEX idx_docversions_state     ON document_versions(state);
CREATE INDEX idx_audit_logs_actor      ON audit_logs(actor_user_id);
CREATE INDEX idx_audit_logs_ts         ON audit_logs(server_ts);
CREATE INDEX idx_audit_logs_entity     ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_notifications_recip   ON notifications(recipient_id, is_read);
CREATE INDEX idx_training_user         ON training_assignments(user_id);
CREATE INDEX idx_sig_version           ON signature_manifests(version_id);
```

#### §4.2.1 Deviation — last_login_at column

EDMS adds `last_login_at TIMESTAMPTZ` to the `users` table to support quarterly access review (SOP-USER-001 §5.4 reports). Updated by `LocalAuthProvider` on successful login. Indexed via `idx_users_last_login_at`. Envers `users_aud` mirrors the column. No PII beyond what's already audited.

#### §4.2.2 Deviation — permissions UNIQUE NULLS NOT DISTINCT

PG16 `UNIQUE (role_id, category_id, department) NULLS NOT DISTINCT` (V8) treats NULL `department` as equal so only ONE org-wide row per (role,category) is permitted. This matches the FS-ACC-001 model where a NULL department row means "applies to all departments."

### 4.3 DB 역할(Role) 분리

```sql
-- 애플리케이션 일반 역할
CREATE ROLE app_role LOGIN PASSWORD '...';
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO app_role;
-- 단, 아래 테이블은 INSERT만 허용
REVOKE UPDATE, DELETE ON audit_logs         FROM app_role;
REVOKE UPDATE, DELETE ON signature_manifests FROM app_role;
REVOKE UPDATE, DELETE ON audit_checkpoints  FROM app_role;

-- 감사 전용 역할 (INSERT 전용 — 애플리케이션 내 별도 DataSource 사용)
CREATE ROLE audit_role LOGIN PASSWORD '...';
GRANT INSERT, SELECT ON audit_logs          TO audit_role;
GRANT INSERT, SELECT ON signature_manifests TO audit_role;
GRANT INSERT, SELECT ON audit_checkpoints   TO audit_role;

-- 읽기 전용 역할 (DR, 보고서)
CREATE ROLE readonly_role LOGIN PASSWORD '...';
GRANT SELECT ON ALL TABLES IN SCHEMA public TO readonly_role;
```

### 4.4 Flyway 마이그레이션 순서

```
V001__create_users.sql
V002__create_roles_permissions.sql
V003__create_categories_numbering.sql
V004__create_workflow_templates.sql
V005__create_documents_versions.sql
V006__create_workflow_instances.sql
V007__create_signature_manifests.sql
V008__create_audit_logs.sql
V009__create_training_notifications.sql
V010__create_indexes.sql
V011__insert_default_roles.sql
V012__insert_default_categories.sql
V013__configure_db_roles.sql
```

---

## 5. 백엔드 컴포넌트 설계

### 5.1 인증 컴포넌트

**AuthProvider 인터페이스**:
```java
public interface AuthProvider {
    AuthResult authenticate(String userId, String rawPassword);
    boolean supportsProvider(String providerCode);
}
```

`LocalAuthProvider` 구현:
1. `users` 테이블에서 `user_id`로 사용자 조회
2. 계정 상태 확인 (ACTIVE, 유효기간, 잠금 여부)
3. `BCryptPasswordEncoder.matches(rawPassword, storedHash)` 검증
4. 실패 시 `failed_attempts` 증가, 5회 시 `status = LOCKED`, `locked_at` 기록
5. 성공 시 `failed_attempts = 0`

**SessionManager**:
- Spring Session + Redis 대신 Spring Security의 인메모리 세션 사용 (폐쇄망 단순화)
- 세션 ID: `SecureRandom` 128비트 난수 생성
- 단일 세션 강제: `maximumSessions(1).maxSessionsPreventsLogin(false)` (기존 세션 만료 방식)
- 비활성 타임아웃: 15분 (`server.servlet.session.timeout=15m`)
- 잠금 해제 스케줄러: 1분 주기로 `locked_at < NOW() - 30분` 계정 자동 해제

### 5.2 문서 채번 서비스 (NumberingService)

```
NumberingService.issue(categoryId, context):
  1. category_id로 NumberingTemplate 조회
  2. context에서 scope_key 결정:
     - PER_DEPT    → context.department
     - PER_PRODUCT → context.projectCode
     - PER_YEAR    → String.valueOf(LocalDate.now().getYear())
     - GLOBAL      → "__global__"
  3. SELECT ... FOR UPDATE on numbering_counters (scope 잠금)
  4. current_seq 증가 (UPDATE)
  5. format_pattern에 플레이스홀더 치환:
     - {TYPE} → category_code
     - {DEPT} → context.department
     - {PROD} → context.projectCode
     - {YEAR} → 현재 연도 4자리
     - {SEQ:N} → zero-padded(current_seq, N)
  6. 결과 문자열 반환 (트랜잭션 커밋 시 확정)
```

### 5.3 라이프사이클 상태머신 (LifecycleStateMachine)

| 전이 ID | FROM | TO | 조건 |
|---|---|---|---|
| T-01 | 초안 | 검토중 | Author가 제출, 워크플로 인스턴스 생성 |
| T-02 | 검토중 | 승인중 | 모든 검토 단계 서명 완료 |
| T-03 | 승인중 | 시행중 | 모든 승인 단계 서명 완료 (qa_mandatory 충족) |
| T-04 | 검토중 | 초안 | Reviewer 반려 |
| T-05 | 승인중 | 초안 | Approver 반려 |
| T-06 | 시행중 | 개정중 | Author 개정 시작 (신규 버전 DRAFT 생성) |
| T-07 | 개정중 | 대체됨 | 신규 버전 시행중 전환 시 기존 버전 자동 대체됨 |
| T-08 | 시행중 | 폐기됨 | QA가 폐기 서명 |
| T-09 | 대체됨 | 폐기됨 | QA가 폐기 서명 |

상태 전이 시 `AuditService.log(STATE_TRANSITION, ...)` 호출은 필수이다.

### 5.4 PDF 변환 서비스 (PdfConversionService)

```
비동기 흐름:
1. 파일 업로드 완료 → RenditionQueue에 작업 추가
2. 워커 스레드:
   a. MinIO에서 원본 파일 임시 디렉터리로 다운로드
   b. LibreOffice 호출:
      soffice --headless --convert-to pdf:writer_pdf_Export
              --outdir /tmp/rendition/ input.docx
   c. 변환 결과 PDF를 WatermarkService에 전달
   d. 워터마크 적용 완료 PDF를 MinIO rendition 버킷에 업로드
   e. document_versions.pdf_file_key 업데이트, pdf_status = DONE
   f. 임시 파일 삭제
3. 실패 시: pdf_status = FAILED, 관리자 알림 발송
```

**WatermarkService** (PDFBox):
- 헤더 영역: `[문서번호] [개정번호] [시행일]`
- 푸터 영역: `Controlled Document — Uncontrolled When Printed`
- 다운로드 비허가 사용자용 PDF에 반투명 대각선 워터마크 추가: `CONFIDENTIAL`

### 5.5 감사 서비스 (AuditService)

감사로그는 별도의 `auditJdbcTemplate`(audit_role 계정)으로만 INSERT된다.

```java
@Service
public class AuditService {
    // app_role DataSource: 일반 읽기
    // audit_role DataSource: INSERT 전용

    public void log(AuditEvent event) {
        String prevHash = getLastHash();  // audit_logs에서 최신 this_hash
        String thisHash = sha256(prevHash + event.serialize());
        auditJdbcTemplate.update(
            "INSERT INTO audit_logs (..., prev_hash, this_hash) VALUES (...)",
            ...thisHash
        );
    }
}
```

### 5.6 SSE 알림 서비스 (SseNotificationService)

```
연결: GET /api/notifications/stream
  → SseEmitter 생성 (timeout = 5분, 만료 시 클라이언트 자동 재연결)
  → emitters 맵에 사용자 ID로 저장

발송: NotificationService.push(userId, notification)
  → notifications 테이블에 INSERT
  → emitters 맵에서 해당 사용자 Emitter 조회
  → emitter.send(SseEmitter.event().data(notificationDto))
```

### 5.7 정기검토 스케줄러 (PeriodicReviewScheduler)

```
매일 00:10 KST 실행 (@Scheduled cron = "0 10 0 * * *"):
  SELECT dv.* FROM document_versions dv
  JOIN documents d ON d.id = dv.document_id
  JOIN document_categories dc ON dc.id = d.category_id
  WHERE dv.state = 'EFFECTIVE'
    AND dv.effective_date + (dc.review_period_months || ' months')::INTERVAL
        - INTERVAL 'N days' <= NOW()
  
  N ∈ {90, 30, 7, 0}에 해당하는 문서를 각각 다른 알림 타입으로 발송
  N = 0: "정기검토 기한 초과" 알림 (매일 반복, 최대 30일)
```

---

## 6. API 설계

### 6.1 공통 규칙

- 기본 경로: `/api/v1`
- 인증: 서버 세션 쿠키 (`JSESSIONID`, `HttpOnly`, `Secure`, `SameSite=Strict`)
- 응답 형식: `Content-Type: application/json; charset=UTF-8`
- 에러 응답 구조:
  ```json
  { "code": "AUTH_001", "message": "자격증명이 올바르지 않습니다.", "timestamp": "..." }
  ```
- 페이지네이션: `?page=0&size=20&sort=createdAt,desc`
- 타임스탬프: ISO 8601 UTC (`2026-05-08T10:30:00Z`)

### 6.2 인증 API

| Method | Path | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/api/v1/auth/login` | 로그인 | No |
| POST | `/api/v1/auth/logout` | 로그아웃 | Yes |
| GET | `/api/v1/auth/me` | 현재 세션 사용자 정보 | Yes |
| POST | `/api/v1/auth/change-password` | 비밀번호 변경 | Yes |

**POST /api/v1/auth/login**
```
Request:  { "userId": "string", "password": "string" }
Response 200: {
  "userId": "jsmith",
  "fullName": "홍길동",
  "roles": ["AUTHOR", "REVIEWER"],
  "forceChangePw": false
}
Response 401: { "code": "AUTH_001", "remainingAttempts": 3 }
Response 423: { "code": "AUTH_002", "lockedUntil": "2026-05-08T10:45:00Z" }
```

### 6.3 문서 API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/documents` | 문서 목록 (권한 필터 적용) |
| POST | `/api/v1/documents` | 문서 생성 (채번 + 초안 버전 생성) |
| GET | `/api/v1/documents/{docId}` | 문서 상세 |
| GET | `/api/v1/documents/{docId}/versions` | 버전 목록 |
| GET | `/api/v1/documents/{docId}/versions/{verId}` | 버전 상세 |
| POST | `/api/v1/documents/{docId}/versions/{verId}/files` | 파일 업로드 |
| GET | `/api/v1/documents/{docId}/versions/{verId}/pdf` | PDF 스트리밍 (뷰어용) |
| GET | `/api/v1/documents/{docId}/versions/{verId}/pdf/download` | PDF 다운로드 (권한 확인) |

**POST /api/v1/documents** — 문서 생성
```
Request: {
  "categoryCode": "SOP",
  "department": "QC",
  "title": "원료 수탁시험 절차",
  "projectCode": null,
  "confidential": false,
  "changeReason": "신규 작성"
}
Response 201: {
  "docId": 42,
  "docNumber": "SOP-QC-001",
  "versionId": 101,
  "state": "DRAFT"
}
```

### 6.4 라이프사이클 API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/documents/{docId}/versions/{verId}/submit` | 검토 요청 (T-01) |
| POST | `/api/v1/documents/{docId}/versions/{verId}/reject` | 반려 (T-04, T-05) |
| POST | `/api/v1/documents/{docId}/versions/{verId}/revise` | 개정 시작 (T-06) |
| POST | `/api/v1/documents/{docId}/versions/{verId}/retire` | 폐기 (T-08, T-09) |
| GET | `/api/v1/documents/{docId}/versions/{verId}/workflow` | 워크플로 현황 |

### 6.5 전자서명 API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/documents/{docId}/versions/{verId}/sign` | 전자서명 |
| GET | `/api/v1/documents/{docId}/versions/{verId}/signatures` | 서명 목록 (2-tier) |

**POST /api/v1/documents/{docId}/versions/{verId}/sign**
```
Request: {
  "password": "string",          -- 재인증 (암호화 전송)
  "meaning": "REVIEWED",
  "comment": "검토 완료",
  "signing_user_id": "string"    -- 세션 첫 서명 시 필수 (Part 11 §11.200(a))
                                 -- session_first && signing_user_id == null → 422 SIGNATURE_002
                                 -- signing_user_id 불일치 → 403 SIGNATURE_003
}
Response 201: {
  "signatureId": 55,
  "signedAt": "2026-05-08T10:30:00Z",
  "thisHash": "a3f9..."
}
Response 401: { "code": "SIGNATURE_001", "message": "비밀번호가 올바르지 않습니다." }
Response 403: { "code": "SIGNATURE_003", "message": "서명자 ID 불일치 또는 계정 잠금" }
Response 422: { "code": "SIGNATURE_002", "message": "첫 서명 시 signing_user_id 필수" }
Response 429: { "code": "RATE_LIMIT_001", "message": "요청 한도 초과 (5req/min)" }
```

**세션 첫 서명 판별 (session_first)**:
- `session_first = true`: 동일 HttpSession 객체에서 최초 서명 성공 시
- `markSigned()` 호출 시점: `signature_manifests` INSERT 성공 직후 (롤백 시 마커 미기록)
- Tomcat 30분 idle timeout 후 세션 만료 → 재로그인 시 다시 `session_first = true`

**계정 상태 검증 (verifyPassword 내)**:
- LOCKED 또는 DISABLED 계정: 비밀번호 일치 여부와 무관하게 403 거부
- PW 실패 시 lockout 카운터 증가: REQUIRES_NEW 독립 트랜잭션 (서명 롤백과 독립)

**Rate Limiting**:
- Bucket4j: 5 req/min per (userId + clientIP)
- 초과 시 429 RATE_LIMIT_001

**GET /api/v1/documents/{docId}/versions/{verId}/signatures**
```
Response 200: [SignatureSummaryDto | SignatureDetailDto]
  -- Reviewer 이상: public 필드 (signer_name, meaning, signed_at, comment)
  -- Admin/Auditor: detail 필드 추가 (signer_user_id, client_ip, this_hash,
                   prev_hash, algorithm_version, session_first, canonical_payload)
  -- 정렬: signed_at ASC (서명 순서)
```

#### §6.5.1 session_first 정의

동일 `HttpSession` 객체에서의 첫 번째 서명. 판별 기준:
- HttpSession에 서명 완료 마커(`SESSION_SIGNED_FLAG`)가 없으면 `session_first = true`
- `markSigned()` 호출(마커 기록)은 `signature_manifests` INSERT 커밋 이후에만 실행
- Tomcat 기본 idle timeout 30분과 동기화 — 세션 만료 후 재로그인 시 초기화

### 6.6 감사로그 API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/audit-logs` | 감사로그 조회 (Auditor/Admin) |
| GET | `/api/v1/audit-logs/export` | CSV 내보내기 |
| GET | `/api/v1/audit-logs/checkpoints` | 일별 체크포인트 목록 |
| POST | `/api/v1/audit-logs/checkpoints/verify` | 해시체인 무결성 검증 |

### 6.7 관리자 API

| Method | Path | 설명 |
|---|---|---|
| GET/POST/PUT | `/api/v1/admin/users` | 사용자 관리 |
| GET/POST/PUT | `/api/v1/admin/roles` | 역할 관리 |
| GET/POST/PUT | `/api/v1/admin/permissions` | 권한 매트릭스 |
| GET/POST/PUT | `/api/v1/admin/categories` | 카테고리 관리 |
| GET/POST/PUT | `/api/v1/admin/numbering-templates` | 채번 템플릿 |
| GET/POST/PUT | `/api/v1/admin/workflow-templates` | 워크플로 템플릿 |

#### §6.7.1 v_access_review SQL view

Read-only view joining `users`, `user_roles`, `roles` for SOP-USER-001 §5.4 quarterly access review CSV export. Granted `SELECT` to `app_role`. The view aggregates `role_codes` via `string_agg(... ORDER BY role_code)` for deterministic output. Definition: see Flyway V7.

### 6.8 알림 API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/notifications` | 알림 목록 |
| PUT | `/api/v1/notifications/{id}/read` | 읽음 처리 |
| GET | `/api/v1/notifications/stream` | SSE 스트림 |

### 6.9 검색 API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/search?q=&category=&dept=&state=&from=&to=` | 통합 검색 |

```sql
-- 검색 쿼리 (PostgreSQL tsvector)
SELECT d.*, dv.*
FROM documents d
JOIN document_versions dv ON dv.document_id = d.id
WHERE dv.state = 'EFFECTIVE'
  AND (d.search_vector @@ plainto_tsquery('korean', :query)
       OR dv.search_vector @@ plainto_tsquery('korean', :query))
ORDER BY ts_rank(dv.search_vector, plainto_tsquery('korean', :query)) DESC;
```

---

## 7. 프론트엔드 설계

### 7.1 상태 관리 (Pinia)

```typescript
// stores/auth.ts
interface AuthState {
  user: UserInfo | null;
  sessionExpiresAt: Date | null;
  warningShown: boolean;
}

// stores/notification.ts
interface NotificationState {
  items: Notification[];
  unreadCount: number;
  sseConnection: EventSource | null;
}
```

### 7.2 라우터 인증 가드

```typescript
router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && !authStore.user) {
    return { name: 'Login', query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresRole && !authStore.hasRole(to.meta.requiresRole)) {
    return { name: 'Forbidden' };
  }
});
```

### 7.3 PDF 뷰어 (pdf.js)

```typescript
// 다운로드·인쇄 비활성화 설정
const pdfjsConfig = {
  disableRange: false,
  disableStream: false,
  disableFontFace: false,
};

// toolbar 설정 (다운로드 권한 없는 사용자)
toolbar.download.hidden = !canDownload;
toolbar.print.hidden = !canDownload;
```

PDF 스트리밍은 `/api/v1/documents/{id}/versions/{vid}/pdf` 엔드포인트에서  
`Range` 헤더를 지원하는 `StreamingResponseBody`로 응답한다.

#### 쿼리 파라미터 (M7.1)

| 파라미터 | 타입 | 허용값 | 설명 |
|---|---|---|---|
| `kind` | string | `INITIAL` \| `STAMPED` \| `EFFECTIVE` | 렌디션 종류 지정. 미지정 시 역할·권한 기반 자동 선택 |
| `step` | Integer | 1 이상 정수 | `kind=STAMPED`일 때 특정 결재 단계 본 지정 |

**자동 선택 규칙**: `kind` 파라미터 미지정 시

- 활성 단계 assignee → `STAMPED` (최신 step)
- AUDITOR → `STAMPED` (모든 step 열람 가능)
- 일반 열람 권한 → `EFFECTIVE` (EFFECTIVE_STAMPED 상태일 때만)
- Author (본인 DRAFT) → `INITIAL`

**응답 헤더**:

| 헤더 | 값 예시 | 설명 |
|---|---|---|
| `X-Rendition-Kind` | `STAMPED` | 실제 서빙된 렌디션 종류 |
| `X-Rendition-Step` | `2` | STAMPED 렌디션의 결재 단계 |
| `Cache-Control` | `no-store` | PDF 캐시 금지 (보안) |

**보안 설정** (isEvalSupported: false):

```typescript
// pdf.js 보안 옵션
const PDFJS_OPTIONS = {
  isEvalSupported: false,  // eval() 비활성화 (CSP 준수)
  disableRange: false,
  disableStream: false,
  disableFontFace: false,
};
```

### 7.4 전자서명 다이얼로그 (SignatureDialog.vue)

```
화면 구성:
┌─────────────────────────────────────────┐
│  전자서명 — [문서번호] [개정번호]         │
├─────────────────────────────────────────┤
│  서명 의미: [REVIEWED ▼]                │
│  비밀번호: [____________]               │
│  의견(선택): [                        ] │
├─────────────────────────────────────────┤
│  ■ 본인은 [사용자명]으로서 위 의미에 따라 │
│    전자적으로 서명함을 확인합니다.         │
│  서명 일시: 2026-05-08 10:30:00 KST     │
├─────────────────────────────────────────┤
│             [취소]  [서명 확인]          │
└─────────────────────────────────────────┘
```

---

## 8. 핵심 알고리즘 설계

### 8.1 전자서명 해시체인 (DS-SIG-001)

```
입력:
  prev_hash             : 직전 signature_manifests 레코드의 this_hash
                          (최초 레코드: SHA-256("GENESIS"))
  signer_id             : users.id (Long)
  meaning               : 서명 의미 코드 (REVIEWED / APPROVED / ACKNOWLEDGED)
  signed_at_iso         : ISO 8601 UTC 문자열 (예: 2026-05-08T01:30:00Z)
  document_version_id   : document_versions.id (Long)
  doc_number            : documents.doc_number (문자열, 예: SOP-QA-001)
  revision              : document_versions.revision (예: Rev 0)
  doc_status            : document_versions.status (예: UNDER_REVIEW)
  source_file_sha256    : DocumentFile의 SHA-256 헥스 (원본 파일 해시)

직렬화:
  payload = prev_hash
           + "|" + signer_id
           + "|" + meaning
           + "|" + signed_at_iso
           + "|" + document_version_id
           + "|" + doc_number
           + "|" + revision
           + "|" + doc_status
           + "|" + source_file_sha256

산출:
  this_hash = HEX(SHA-256(UTF-8(payload)))

저장:
  signature_manifests.prev_hash = prev_hash
  signature_manifests.this_hash = this_hash
```

검증: `this_hash == HEX(SHA-256(payload))` — payload에 파일 해시와 doc_number가 포함되므로 파일 교체나 메타데이터 변경 시 서명 무효화가 감지된다 (Part 11 §11.70 준수).

> **설계 근거**: `version_id`만으로는 DB의 `document_versions` 행이 수정되거나 파일이 교체되었을 때 서명이 여전히 유효해 보이는 취약점이 있다. canonical snapshot(파일 SHA-256 + 문서번호 + 리비전 + 상태)을 payload에 포함함으로써 서명과 특정 전자기록의 불분리 연결을 보장한다.

### 8.2 감사로그 해시체인 (DS-AUD-001)

서명 해시체인과 동일한 구조. payload 직렬화:
```
payload = prev_hash
         + "|" + actor_user_id
         + "|" + action
         + "|" + entity_type + ":" + entity_id
         + "|" + server_ts_iso
         + "|" + before_value      (NULL → 빈 문자열)
         + "|" + after_value       (NULL → 빈 문자열)
         + "|" + reason            (NULL → 빈 문자열)
         + "|" + client_ip         (NULL → 빈 문자열)
```

> **설계 근거**: before_value / after_value / reason / client_ip를 해시에 포함시켜야 이 필드 변조 시 체인 검증이 실패한다. 포함하지 않으면 변조 탐지가 작동하지 않는다.

### 8.3 일별 WORM 앵커링 (DS-AUD-002)

```
매일 01:00 KST 실행:
1. 전날(T-1) audit_logs 전체 this_hash 목록 조회 (ordered by id)
2. Merkle 트리 생성:
   - 리프 노드: 각 레코드의 this_hash
   - 부모 노드: SHA-256(left_hash + right_hash)
   - root = Merkle root
3. 앵커 파일 생성:
   {
     "date": "2026-05-07",
     "merkle_root": "...",
     "record_count": 1234,
     "first_id": 10001,
     "last_id": 11234,
     "generated_at": "2026-05-08T16:00:00Z"
   }
4. MinIO에 업로드:
   bucket: edms-audit-anchors
   key:    anchors/2026/05/20260507.json
   Object Lock: COMPLIANCE, 3650일 (10년)
5. audit_checkpoints INSERT (audit_role)
```

### 8.4 비밀번호 해시 (DS-AUTH-001)

```
저장: BCrypt(rounds=12, rawPassword) → password_hash
검증: BCryptPasswordEncoder.matches(rawPassword, storedHash)
이전 비밀번호 이력: password_history 테이블, 최근 5개 유지
  - 신규 해시 저장 시 5개 초과분 삭제 (가장 오래된 것부터)
```

### 8.5 문서 파일 SHA-256 무결성 (DS-DOC-001)

```
업로드 시:
  sha256 = DigestUtils.sha256Hex(inputStream)
  MinIO 업로드 (ETag = MD5)
  document_files.sha256_hash = sha256

열람/다운로드 시:
  MinIO에서 파일 스트리밍
  (백그라운드 검증: 주기적으로 MinIO 파일의 SHA256 재계산 → 저장값과 비교)
```

---

## 9. 보안 설계

### 9.1 전송 보안

- Nginx에서 TLS 1.2 이상만 허용 (`ssl_protocols TLSv1.2 TLSv1.3`)
- 사내 CA 발급 인증서 사용
- HSTS 헤더 설정: `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- 모든 API는 HTTPS 전용. HTTP → HTTPS 리다이렉트 설정

### 9.2 세션 보안

```
Set-Cookie: JSESSIONID=...; HttpOnly; Secure; SameSite=Strict; Path=/
```
- 세션 ID 재생성: 로그인 성공 후 `invalidateHttpSession(true)` → 새 세션 ID 발급 (Session Fixation 방지)
- CSRF: SameSite=Strict + Spring Security CSRF 토큰 (Double Submit Cookie 방식)

### 9.3 입력 검증 및 출력 인코딩

- Spring Validation (`@Valid`) — 모든 Controller 입력
- SQL Injection: JPA/JPQL Parameterized Query 전용, Native Query에서는 `PreparedStatement`만 허용
- XSS: Thymeleaf 미사용 (REST API + Vue), Vue의 `v-html` 미사용 (또는 DOMPurify 적용)
- 파일 업로드: Content-Type 화이트리스트 (`.docx`, `.xlsx`, `.pptx`, `.pdf`), 최대 100MB

### 9.4 권한 강제

```java
// 메서드 레벨 권한 강제
@PreAuthorize("@permissionEvaluator.canView(authentication, #versionId)")
public DocumentVersionDto getVersion(Long versionId) { ... }
```

`PermissionEvaluator`는 `permissions` 테이블 기반 3차원 RBAC를 평가한다.

### 9.5 보안 헤더 (Nginx)

```nginx
add_header X-Content-Type-Options   "nosniff";
add_header X-Frame-Options          "DENY";
add_header Content-Security-Policy  "default-src 'self'; ...";
add_header Referrer-Policy          "no-referrer";
```

---

## 10. 인프라 및 배포 설계

### 10.1 서버 구성 (운영)

| 역할 | 사양 (권장) | OS |
|---|---|---|
| App + Nginx | CPU 8코어, RAM 16GB, SSD 100GB | Rocky Linux 9 |
| PostgreSQL Primary | CPU 8코어, RAM 32GB, SSD 500GB | Rocky Linux 9 |
| PostgreSQL Replica (DR) | 동일 사양 (별도 사이트) | Rocky Linux 9 |
| MinIO | CPU 4코어, RAM 16GB, HDD 10TB (RAID6) | Rocky Linux 9 |
| MinIO DR Mirror | 동일 사양 (별도 사이트) | Rocky Linux 9 |

### 10.2 MinIO 버킷 구성

| 버킷 | 용도 | Object Lock |
|---|---|---|
| `edms-documents-original` | 업로드 원본 파일 | GOVERNANCE, 10년 |
| `edms-documents-rendition` | 변환 PDF | GOVERNANCE, 10년 |
| `edms-audit-anchors` | 감사 WORM 앵커 | COMPLIANCE, 10년 |

Object Lock 버킷은 생성 시 `--with-lock` 옵션 필수. 이후 변경 불가.

### 10.3 백업 전략

```
PostgreSQL:
  - 매일 00:00: pg_basebackup (full)
  - 매일 증분: WAL 아카이빙 → 별도 백업 스토리지
  - 보관 주기: 1년

MinIO:
  - Site Replication: Primary → DR (실시간 동기화)
  - 별도 테이프/콜드 스토리지: 분기 1회 전체 스냅샷

백업 복구 테스트: 반기 1회 DR 복구 훈련 (RTO 4h 목표)
```

### 10.4 Docker Compose (개발 환경)

```yaml
# infra/docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: edms_dev
      POSTGRES_USER: app_role
      POSTGRES_PASSWORD: ${DB_APP_PASSWORD}
    volumes:
      - pg_data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - minio_data:/data

  libreoffice:
    image: linuxserver/libreoffice:latest
    # headless 변환 전용 — 네트워크 격리

  app:
    build: ../backend
    depends_on: [postgres, minio, libreoffice]
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_URL: jdbc:postgresql://postgres:5432/edms_dev
    ports:
      - "8080:8080"

  frontend:
    build: ../frontend
    ports:
      - "3000:3000"
```

### 10.5 NTP 동기화

모든 서버는 사내 NTP 서버와 동기화:
```
/etc/chrony.conf:
  server ntp.internal.lab iburst
  makestep 1.0 3
  rtcsync
```

서명 타임스탬프는 반드시 서버 시각(`Instant.now()`)을 사용하며, 클라이언트 제공 시각은 무시한다.

**M6 운영 이관 결정**:
- 서버 코드는 `Instant.now()`만 사용 — NTP 동기화 상태를 애플리케이션에서 직접 검증하지 않는다.
- chrony 동기화 상태 감시는 **운영 책임** (모니터링 SOP, Nagios/Prometheus chrony_offset 알림).
- NTP 오차 >10초 거부 로직은 운영 점검으로 이관 — M6 코드 범위 외.
- 관련 OQ 케이스 OQ-SIG-011: N/A (운영 책임 항목으로 이관).

---

## 11. FS-DS 추적성 매트릭스

| FS 항목 | DS 설계 참조 | 비고 |
|---|---|---|
| FS-AUTH-001 (로그인) | §5.1 LocalAuthProvider, §8.4, §6.2 | BCrypt + 잠금 로직 |
| FS-AUTH-002 (비밀번호 정책) | §5.1 SessionManager, §4.2 password_history | 5개 이력 |
| FS-AUTH-003 (세션 관리) | §5.1 SessionManager, §9.2 | 15분 타임아웃 |
| FS-AUTH-004 (인증 추상화) | §5.1 AuthProvider 인터페이스 | Phase 2 LDAP swap |
| FS-USER-001 (계정 생성) | §4.2 users DDL, §6.7 /admin/users | |
| FS-USER-002 (계정 수정) | §4.2 users DDL, §6.7 | 소프트 비활성화 |
| FS-USER-003 (역할 관리) | §4.2 roles, user_roles, §6.7 | 8개 기본 역할 |
| FS-USER-004 (Auditor 기간) | §4.2 valid_from/valid_until, §5.1 | |
| FS-ACC-001 (RBAC 매트릭스) | §4.2 permissions DDL, §9.4 PermissionEvaluator | |
| FS-ACC-002 (기밀 격리) | §4.2 documents.confidential, §9.4 | |
| FS-ACC-003 (다운로드 정책) | §7.3 PDF 뷰어, §6.3 /pdf/download | |
| FS-ACC-004 (권한 즉시 적용) | §5.1 역할 캐시 미사용 | |
| FS-DOC-001 (문서 생성·채번) | §5.2 NumberingService, §4.2 numbering_*, §6.3 | SELECT FOR UPDATE |
| FS-DOC-002 (파일 업로드) | §5.4 PdfConversionService, §4.2 document_files | |
| FS-DOC-003 (PDF 변환) | §5.4 LibreOffice 호출 흐름 | 폰트팩 설치 필수 |
| FS-DOC-004 (워터마크) | §5.4 WatermarkService, §8.5 | PDFBox |
| FS-DOC-005 (열람 제어) | §7.3 pdf.js 설정, §6.3 | 스트리밍 응답 |
| FS-DOC-006 (버전 관리) | §4.2 document_versions, revision 컬럼 | |
| FS-DOC-007 (개정) | §5.3 T-06 전이, §6.4 /revise | |
| FS-DOC-008 (메타데이터 편집) | §4.2 document_versions, §6.3 PUT | |
| FS-DOC-009 (파일 형식 제한) | §9.3 업로드 화이트리스트 | |
| FS-DOC-010 (문서 목록) | §6.3 GET /documents, §4.2 permissions | |
| FS-LCY-001 (상태머신) | §5.3 LifecycleStateMachine 전이 테이블 | T-01~T-09 |
| FS-LCY-002 (워크플로 템플릿) | §4.2 workflow_template*, §6.7 | |
| FS-LCY-003 (워크플로 인스턴스) | §4.2 workflow_instances, §5.3 | auto_assign 로직 |
| FS-LCY-004 (검토 서명) | §5.3 T-02, §6.5 /sign | |
| FS-LCY-005 (승인 서명) | §5.3 T-03, §6.5 /sign | qa_mandatory |
| FS-LCY-006 (반려) | §5.3 T-04, T-05, §6.4 /reject | |
| FS-LCY-007 (워크플로 템플릿 설정) | §4.2 workflow_template_steps | qa_required 컬럼 |
| FS-LCY-008 (상태 표시) | §3.2 상태 코드, Frontend 배지 컴포넌트 | |
| FS-LCY-009 (병렬 검토) | §4.2 parallel 컬럼, §5.3 인스턴스 로직 | |
| FS-SIG-001 (서명 다이얼로그) | §7.4 SignatureDialog.vue, §6.5 | |
| FS-SIG-002 (해시체인) | §8.1 해시체인 알고리즘, §4.2 signature_manifests | |
| FS-SIG-003 (첫 서명 ID+PW) | §6.5 meaning=FIRST_SIGN 판별 로직 | Part 11 §11.200(a) |
| FS-SIG-004 (재인증) | §6.5 POST /sign — password 필드 검증 | |
| FS-SIG-005 (서명 매니페스트) | §4.2 signature_manifests DDL | |
| FS-SIG-006 (서명 블록 stamp) | §5.4 WatermarkService — 서명 정보 PDF 임베딩 | |
| FS-SIG-007 (서명 조회) | §6.5 GET /signatures | |
| FS-SIG-008 (서명 불가) | §5.3 워크플로 단계 권한 검증 | |
| FS-AUD-001 (자동 로깅) | §5.5 AuditService, §4.2 audit_logs | |
| FS-AUD-002 (INSERT-only) | §4.3 DB 역할 분리, §5.5 audit DataSource | |
| FS-AUD-003 (WORM 앵커링) | §8.3 일별 앵커링 알고리즘, §10.2 MinIO | |
| FS-AUD-004 (해시체인 검증) | §6.6 POST /audit-logs/checkpoints/verify | |
| FS-AUD-005 (감사로그 조회) | §6.6 GET /audit-logs, §4.2 인덱스 | |
| FS-AUD-006 (Envers 자동 감사) | §3.2 EnversConfig, @Audited 어노테이션 | |
| FS-AUD-007 (10년 보관) | §10.2 MinIO COMPLIANCE, §4.2 DDL 메모 | |
| FS-SRCH-001 (통합 검색) | §8.5(검색), §6.9 /search, §4.2 tsvector | |
| FS-SRCH-002 (한국어 검색) | §4.2 mecab-ko TEXT SEARCH CONFIG | |
| FS-SRCH-003 (메타데이터 필터) | §6.9 쿼리 파라미터 | |
| FS-SRCH-004 (검색 권한) | §6.9 권한 필터 적용 | |
| FS-SRCH-005 (검색 결과) | §6.9 응답 구조 | |
| FS-NTFY-001 (이메일) | §5.6 EmailNotificationService | JavaMailSender |
| FS-NTFY-002 (앱 내 알림) | §5.6 SseNotificationService, §6.8 | SSE |
| FS-NTFY-003 (알림 읽음) | §6.8 PUT /notifications/{id}/read | |
| FS-NTFY-004 (정기검토 알림) | §5.7 PeriodicReviewScheduler | 90/30/7/0일 |
| FS-TRN-001 (교육 과제) | §4.2 training_assignments, §6.x (추가 예정) | |
| FS-TRN-002 (열람 확인 서명) | §4.2 completion_sig_id, §6.5 | ACKNOWLEDGED |
| FS-TRN-003 (교육 완료 조건) | §4.2 training_assignments.completed_at | |
| FS-TRN-004 (미완료 알림) | §5.7 due_at 기반 알림 | |
| FS-ADMIN-001 (마스터데이터) | §6.7 Admin API, §4.2 categories | |
| FS-ADMIN-002 (채번 템플릿) | §6.7, §5.2 NumberingService | |
| FS-ADMIN-003 (워크플로 템플릿) | §6.7, §4.2 workflow_template_steps | |
| FS-PERF-001 (응답 시간) | §3.1 아키텍처, §4.2 인덱스 | 목표: API < 2s |
| FS-PERF-002 (동시 사용자) | §3.1 단일 App 서버 (500명 기준) | |
| FS-PERF-003 (PDF 변환) | §5.4 비동기 큐, 목표: 5분 내 | |
| FS-SEC-001 (전송 암호화) | §9.1 TLS 설정 | |
| FS-SEC-002 (비밀번호 해시) | §8.4 BCrypt rounds=12 | |
| FS-SEC-003 (세션 보안) | §9.2 쿠키 속성 | |
| FS-SEC-004 (입력 검증) | §9.3 Spring Validation | |
| FS-SEC-005 (보안 헤더) | §9.5 Nginx 헤더 | |
| FS-BKP-001 (DB 백업) | §10.3 pg_basebackup + WAL | |
| FS-BKP-002 (MinIO 백업) | §10.3 Site Replication | |
| FS-BKP-003 (복구 테스트) | §10.3 반기 DR 훈련 | |
| FS-BKP-004 (백업 무결성) | §10.3 SHA-256 검증 (백업 복구 테스트 시) | |
| FS-MIG-001 (이관 도구) | §3.2 migration/, Spring Shell CLI | |
| FS-MIG-002 (메타데이터 형식) | §4.2 DDL 컬럼과 매핑 | |
| FS-MIG-003 (원본 파일 업로드) | §5.4 MinIO 업로드, §8.5 SHA-256 | |
| FS-MIG-004 (QA 승인) | §5.3 migration_approval 워크플로 | |

**FS→DS 추적성 완결**: FS 전체 항목(78개 FS 기능 항목) DS 설계 참조 확인 완료.

---

## 12. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 초안 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA 검토·승인 전까지 구현에 직접 사용할 수 없다.*
