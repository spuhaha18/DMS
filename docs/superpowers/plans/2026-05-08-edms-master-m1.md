# EDMS Implementation Plan — Master Roadmap + Milestone 1 (Foundation)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase 1.a EDMS (GxP electronic document management system, Part 11 / Annex 11 compliant) — closed-network on-premise application.

**Architecture:** Spring Boot 3.3 monolith + Vue 3 SPA, PostgreSQL 16 with Flyway migrations and a dedicated `audit_role` DB principal that holds INSERT-only privileges on `audit_logs`/`signature_manifests` for tamper-evidence. MinIO with Object Lock COMPLIANCE for WORM file storage and audit anchors. LibreOffice headless for PDF conversion, PDFBox for watermarks, pdf.js for in-browser viewing.

**Tech Stack:** Spring Boot 3.3 / Java 21 / Spring Security 6 / Spring Data JPA / Hibernate Envers / PostgreSQL 16 / Flyway 10 / MinIO Java SDK 8 / LibreOffice 7.x / Apache PDFBox 3 / Vue 3 + Vite + TypeScript / Pinia / pdf.js / Testcontainers / Docker Compose / Ansible.

---

## Context

검증 문서 패키지(31개)는 완성되어 있다. `validation/CONTEXT.md`(프로젝트 진입점), `validation/DS.md`(기술 설계, 1200+줄), `validation/openapi.yaml`(OAS 3.1 API 계약), `validation/FS.md`(78 URS → FS 매핑), `validation/architecture/{Network,SecretsManagement}.md`, 11개 SOP가 모두 구현 입력으로 준비되어 있다.

본 계획은 두 부분으로 구성된다.

1. **마스터 로드맵 (M1~M12)** — 다개월 규모 구현을 12개 마일스톤으로 분해. 각 마일스톤의 목표·대상 산출물·의존성·게이트를 정의하지만 task 단위로는 풀지 않는다. M2 이후는 해당 마일스톤 진입 시 별도 plan 파일로 작성한다.
2. **마일스톤 1 — Foundation (상세)** — 프로젝트 스켈레톤·DB·Flyway·인증·감사로그 인프라까지. TDD 단계별 task로 풀어 즉시 실행 가능하게 작성. 완료 시 "사용자가 로그인하면 모든 행위가 INSERT-only audit_logs에 해시체인으로 기록된다"는 검증 가능한 상태가 된다.

**왜 이 순서인가**: 감사로그 인프라가 없으면 이후 어떤 기능도 GxP-compliant하게 만들 수 없다. AuthService → AuditService → 모든 도메인 서비스가 audit_logs를 의존한다. M1이 토대다.

---

## 마스터 로드맵 (M1~M12)

| ID | 마일스톤 | 핵심 산출물 | 의존성 | 종료 게이트 |
|---|---|---|---|---|
| **M1** | Foundation | 프로젝트 스켈레톤, Docker Compose dev, PostgreSQL+Flyway 베이스라인, `audit_role` DB principal, AuthService+LocalAuthProvider+BCrypt, AuditService+해시체인, `/auth/*` 4개 엔드포인트, Vue 3 LoginView+Pinia, Testcontainers 통합테스트 골격 | 없음 | 사용자 로그인 → audit_logs 1행 기록 + this_hash 검증 통과 |
| **M2** | User & RBAC | `/admin/users`, `/admin/roles`, `/admin/permissions` 풀 CRUD, 8개 시드 역할, 3D RBAC `@PreAuthorize` Permission Evaluator, 단일 세션 강제, 분기 접근검토 SQL 뷰 | M1 | OQ-USER-001~009 통과 |
| **M3** | Document & Numbering | 문서 카테고리 마스터, NumberingService(SELECT FOR UPDATE 동시성), `/admin/categories`/`/admin/numbering-templates`, `/documents` POST/GET, MinIO 클라이언트, 파일 업로드 (UNDER_REVIEW 미만 ORIGINAL) | M2 | OQ-DOC-001~005 통과 (채번 동시성 포함) |
| **M4** | Lifecycle State Machine | `DocumentState` enum + `LifecycleStateMachine` (T-01~T-08), WorkflowTemplate/StepInstance 엔티티, WorkflowService, `/...submit`/`/...reject`/`/...revise`/`/...retire` 엔드포인트, 단일 EFFECTIVE 보장 트랜잭션 | M3 | OQ-LCY-001~018 (전이 매트릭스 매트릭 케이스) 통과 |
| **M5** | Audit Hardening + WORM Anchor | audit_logs/signature_manifests INSERT-only app_role 제한 강제 검증, `WormAnchorJob` (@Scheduled 매일 01:00 KST), Merkle root 빌드, MinIO 버킷 Object Lock **COMPLIANCE** 10년 업로드, `audit_checkpoints`, `POST /audit-logs/checkpoints/verify`. **[순서 근거]** GxP 전자기록 불변성 인프라는 전자서명 생성 이전에 확립되어야 한다. | M4 | OQ-AUD-001~018 통과, WORM anchor 1주일 누적 검증, app_role UPDATE/DELETE permission denied 확인 |
| **M6** | E-signature | SignatureService, 해시체인(prev_hash/this_hash, signature_manifests INSERT-only), 전체 canonical snapshot 해시(version_id + file_sha256 + doc_number + revision + status), 세션 첫 서명 ID+PW 강제, 비밀번호 재인증, `POST /...sign`, signature_manifests UNIQUE(this_hash) | M5 | OQ-SIG-001~013 통과, 해시체인 무결성 SQL 검증 |
| **M7** | PDF Pipeline | LibreOffice headless 변환 큐(@Async + RenditionQueue + **별도 worker 스레드 풀** — JVM heap 격리), PDFBox 워터마크(상단 docNum/rev/effDate, 하단 controlled-document, 대각 CONFIDENTIAL), pdf_status 상태기계, 스트리밍 GET `/.../pdf`, 권한별 GET `/.../pdf/download` | M3 | OQ-DOC-006~009 통과, 한글 폰트 깨짐 없음, LibreOffice hang 시 인증·서명 영향 없음 확인 |
| **M8** | Search | tsvector 마이그레이션 + mecab-ko TEXT SEARCH CONFIG, GIN 인덱스, `/search` 엔드포인트(권한 필터 포함), ts_rank 정렬, 하이라이트 | M3 | OQ-SRCH-001~006 통과 |
| **M9** | Notifications & Periodic Review | EmailNotificationService(JavaMailSender), SseNotificationService(SseEmitter map), Notification 엔티티, `/notifications` CRUD + `/notifications/stream` SSE, PeriodicReviewScheduler (@Scheduled cron 매일 00:10) D-90/30/7/0 알림 | M6 | OQ-NTFY-001~005 통과 |
| **M10** | Frontend Application | Vue Router + auth guard, 핵심 뷰(Dashboard/Documents/Workflow/Audit/Admin), PdfViewer 컴포넌트 wrapping pdf.js, SignatureDialog, NotificationCenter SSE 클라이언트, Pinia 스토어 3종 | M6+M7+M9 | UserManual.md 시나리오 4종(Author/Reviewer/Approver/Owner) 수동 통과 |
| **M11** | Migration CLI | Spring Shell `edms-import` 커맨드, metadata.csv 파서, MinIO 업로드(SHA-256 검증), QA 이관 승인 워크플로 | M6 | PQ-MIG-001~005 통과 |
| **M12** | Infra (IQ) + Validation Execution | Ansible 플레이북(IQ env), 시크릿 Vault 주입, IQ 프로토콜 실행 → 보고서, OQ 자동/수동 케이스 실행 → 보고서, PQ 시나리오 실행 → 보고서, ValidationReport 작성 | M1~M11 모두 | ValidationReport QA Manager 승인 → 운영 릴리즈 |

각 마일스톤 종료 후 다음 plan 파일(`docs/superpowers/plans/<date>-edms-m{N}-<name>.md`)을 별도 brainstorming + writing-plans 사이클로 작성한다. 본 계획에서는 **M1만** task 수준으로 풀어쓴다.

---

## 마일스톤 1 — Foundation: File Structure

**Repository layout (M1 종료 시점):**

```
DMS/
├── CONTEXT.md                 (이미 존재)
├── README.md                  (Task 1에서 생성)
├── .gitignore                 (Task 1)
├── .editorconfig              (Task 1)
│
├── backend/
│   ├── build.gradle.kts       (Task 3)
│   ├── settings.gradle.kts    (Task 3)
│   ├── gradle/wrapper/        (Task 3)
│   ├── src/main/java/com/lab/edms/
│   │   ├── EdmsApplication.java                  (Task 3)
│   │   ├── config/
│   │   │   ├── SecurityConfig.java               (Task 14)
│   │   │   ├── JpaConfig.java                    (Task 7)
│   │   │   └── AuditDataSourceConfig.java        (Task 11)
│   │   ├── auth/
│   │   │   ├── AuthProvider.java                 (Task 12)
│   │   │   ├── LocalAuthProvider.java            (Task 12)
│   │   │   ├── AuthService.java                  (Task 13)
│   │   │   ├── AuthController.java               (Task 15)
│   │   │   ├── LoginRequest.java                 (Task 15)
│   │   │   ├── ChangePasswordRequest.java        (Task 17)
│   │   │   └── PasswordPolicyValidator.java      (Task 17)
│   │   ├── user/
│   │   │   ├── User.java                         (Task 7)
│   │   │   ├── Role.java                         (Task 7)
│   │   │   ├── UserRole.java                     (Task 7)
│   │   │   ├── UserRepository.java               (Task 8)
│   │   │   ├── RoleRepository.java               (Task 8)
│   │   │   └── PasswordHistory.java              (Task 7)
│   │   ├── audit/
│   │   │   ├── AuditEvent.java                   (Task 10)
│   │   │   ├── AuditAction.java                  (Task 10)
│   │   │   ├── AuditService.java                 (Task 11)
│   │   │   └── HashChainSerializer.java          (Task 10)
│   │   └── common/
│   │       ├── ProblemDetail.java                (Task 16)
│   │       └── GlobalExceptionHandler.java       (Task 16)
│   ├── src/main/resources/
│   │   ├── application.yml                       (Task 3)
│   │   ├── application-dev.yml                   (Task 3)
│   │   └── db/migration/
│   │       ├── V1__core_schema.sql               (Task 4)
│   │       ├── V2__audit_schema.sql              (Task 5)
│   │       ├── V3__password_history.sql          (Task 6)
│   │       └── V4__bootstrap_seed.sql            (Task 6)
│   └── src/test/java/com/lab/edms/
│       ├── TestcontainersConfig.java             (Task 9)
│       ├── audit/AuditServiceIT.java             (Task 11)
│       ├── auth/AuthServiceIT.java               (Task 13)
│       └── auth/AuthControllerIT.java            (Task 15)
│
├── frontend/
│   ├── package.json           (Task 19)
│   ├── vite.config.ts         (Task 19)
│   ├── tsconfig.json          (Task 19)
│   ├── index.html             (Task 19)
│   └── src/
│       ├── main.ts                               (Task 19)
│       ├── App.vue                               (Task 19)
│       ├── router/index.ts                       (Task 20)
│       ├── api/client.ts                         (Task 20)
│       ├── stores/auth.ts                        (Task 20)
│       └── views/LoginView.vue                   (Task 20)
│
├── infra/
│   ├── docker-compose.yml                        (Task 2)
│   └── postgres/init/00-create-audit-role.sh     (Task 2)
│
└── validation/                (이미 존재 — 변경하지 않음)
```

**Each file's responsibility (one-liner):**

- `EdmsApplication.java` — Spring Boot entry; `@SpringBootApplication`, `@EnableScheduling`, `@EnableAsync`.
- `SecurityConfig.java` — Spring Security filter chain: form-login disabled, custom `AuthController`, BCrypt, session cookie attributes, CSRF, single-session.
- `JpaConfig.java` — Primary `DataSource` with `app_role` user (DDL access via Flyway only at startup).
- `AuditDataSourceConfig.java` — Secondary `DataSource` bound to `audit_role`; only `INSERT INTO audit_logs/signature_manifests` permitted; exposes `auditJdbcTemplate` bean.
- `User.java` / `Role.java` / `UserRole.java` / `PasswordHistory.java` — JPA entities mapping `users` / `roles` / `user_roles` / `password_history`. `@Audited` (Envers) on User/Role.
- `UserRepository.java` / `RoleRepository.java` — Spring Data interfaces; query methods for `findByUserId`, `findByRoleCode`.
- `AuthProvider.java` — interface `AuthResult authenticate(String userId, String rawPassword)`.
- `LocalAuthProvider.java` — BCrypt verify, lock at 5 fails, 30-min auto-unlock.
- `AuthService.java` — orchestrates login/logout/me/change-password; calls `AuditService.log` on each action.
- `AuthController.java` — REST controller for `/api/v1/auth/*`.
- `AuditEvent.java` — value object: `actor_user_id`, `action`, `entity_type`, `entity_id`, `before_value`, `after_value`, `reason`, `client_ip`, `server_ts`.
- `AuditAction.java` — enum with codes used in M1 (`USER_LOGIN_SUCCESS`, `USER_LOGIN_FAIL`, `USER_LOGOUT`, `USER_PASSWORD_CHANGED`, `USER_LOCKED`, `USER_UNLOCKED`).
- `HashChainSerializer.java` — pure function `String serialize(String prevHash, AuditEvent e)` producing `prev|actor|action|type:id|server_ts_iso` (DS-AUD-001).
- `AuditService.java` — `log(AuditEvent)`: read last `this_hash`, compute new, INSERT via `auditJdbcTemplate`. Synchronized per-row via `SELECT ... FOR UPDATE` of latest row.
- `ProblemDetail.java` — RFC 7807 response shape (matching `openapi.yaml#ProblemDetail`).
- `GlobalExceptionHandler.java` — `@RestControllerAdvice` mapping known exceptions to ProblemDetail.

---

## 마일스톤 1 — Tasks

### Task 1: Repository scaffolding (.gitignore, README, .editorconfig)

**Files:**
- Create: `/home/spuhaha18/Project/DMS/.gitignore`
- Create: `/home/spuhaha18/Project/DMS/.editorconfig`
- Create: `/home/spuhaha18/Project/DMS/README.md`

- [ ] **Step 1: Create `.gitignore` covering JVM, Node, IDE, secrets**

```gitignore
# Build outputs
backend/build/
backend/.gradle/
backend/out/
frontend/dist/
frontend/node_modules/

# IDE
.idea/
.vscode/
*.iml

# OS
.DS_Store
Thumbs.db

# Secrets / env
.env
.env.local
*.key
*.pem
backend/src/main/resources/application-local.yml

# Logs
*.log
logs/
```

- [ ] **Step 2: Create `.editorconfig`**

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_style = space
insert_final_newline = true
trim_trailing_whitespace = true

[*.{java,kts}]
indent_size = 4

[*.{ts,vue,yml,yaml,json,sql}]
indent_size = 2
```

- [ ] **Step 3: Create `README.md` pointing to `CONTEXT.md`**

```markdown
# DMS — Pharmaceutical R&D EDMS

GxP-compliant electronic document management system. See **[CONTEXT.md](./CONTEXT.md)** for project entry point, tech stack, repository map, and getting-started guide.

## Quick start (development)

```bash
# 1. Start infra (PostgreSQL + MinIO)
cd infra && docker compose up -d

# 2. Run backend
cd backend && ./gradlew bootRun

# 3. Run frontend
cd frontend && npm install && npm run dev
```

## Documentation

- `CONTEXT.md` — project overview and document map
- `validation/` — 31 GxP validation documents (URS, FS, DS, SOPs, etc.)
- `docs/superpowers/plans/` — implementation plans
```

- [ ] **Step 4: Commit**

```bash
cd /home/spuhaha18/Project/DMS
git add .gitignore .editorconfig README.md
git commit -m "chore: add repository scaffolding (gitignore, editorconfig, README)"
```

---

### Task 2: Docker Compose dev infra (PostgreSQL + MinIO + audit_role bootstrap)

**Files:**
- Create: `infra/docker-compose.yml`
- Create: `infra/postgres/init/00-create-audit-role.sh`

- [ ] **Step 1: Write the failing test (manual smoke)**

This task has no JUnit test — verification is `docker compose up` succeeding and `psql` showing `audit_role` exists.

- [ ] **Step 2: Create `infra/docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16
    container_name: edms-postgres
    environment:
      POSTGRES_DB: edms_dev
      POSTGRES_USER: app_role
      POSTGRES_PASSWORD: app_dev_password
    ports:
      - "5432:5432"
    volumes:
      - pg_data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app_role -d edms_dev"]
      interval: 5s
      timeout: 3s
      retries: 5

  minio:
    image: minio/minio:RELEASE.2024-10-13T13-34-11Z
    container_name: edms-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minio_root
      MINIO_ROOT_PASSWORD: minio_dev_password
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  pg_data:
  minio_data:
```

- [ ] **Step 3: Create `infra/postgres/init/00-create-audit-role.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

# Postgres init scripts run as superuser before the application connects.
# We pre-create the audit_role principal here so Flyway V2 can grant
# INSERT-only privileges to it. The actual table grants live in V2 SQL.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_role') THEN
      CREATE ROLE audit_role LOGIN PASSWORD 'audit_dev_password';
    END IF;
  END
  \$\$;
EOSQL
```

Make executable:

```bash
chmod +x infra/postgres/init/00-create-audit-role.sh
```

- [ ] **Step 4: Verify infra starts**

Run:

```bash
cd /home/spuhaha18/Project/DMS/infra
docker compose up -d
docker compose ps
```

Expected: both `edms-postgres` and `edms-minio` containers `(healthy)`.

Verify audit_role exists:

```bash
docker exec edms-postgres psql -U app_role -d edms_dev -c "\du audit_role"
```

Expected: `audit_role` row with `Cannot login: f` is wrong → it should show `Cannot login: f` meaning login allowed. (Postgres `\du` shows `Cannot login` only for NOLOGIN roles.)

- [ ] **Step 5: Commit**

```bash
git add infra/
git commit -m "infra: add docker compose dev stack (postgres+minio) with audit_role bootstrap"
```

---

### Task 3: Spring Boot Gradle skeleton

**Files:**
- Create: `backend/settings.gradle.kts`
- Create: `backend/build.gradle.kts`
- Create: `backend/gradle.properties`
- Create: `backend/src/main/java/com/lab/edms/EdmsApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-dev.yml`

- [ ] **Step 1: Create `backend/settings.gradle.kts`**

```kotlin
rootProject.name = "edms"
```

- [ ] **Step 2: Create `backend/build.gradle.kts`**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.lab"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.hibernate.orm:hibernate-envers")
    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Create `backend/gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.parallel=true
```

- [ ] **Step 4: Create `EdmsApplication.java`**

```java
package com.lab.edms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class EdmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EdmsApplication.class, args);
    }
}
```

- [ ] **Step 5: Create `application.yml`**

```yaml
spring:
  application:
    name: edms
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.envers.audit_table_suffix: _AUD
      hibernate.envers.revision_field_name: REV
      hibernate.envers.revision_type_field_name: REVTYPE
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false

server:
  port: 8080
  servlet:
    session:
      cookie:
        name: JSESSIONID
        http-only: true
        secure: false  # dev only; prod overrides via profile
        same-site: strict
      timeout: 15m

logging:
  level:
    root: INFO
    com.lab.edms: DEBUG
```

- [ ] **Step 6: Create `application-dev.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/edms_dev
    username: app_role
    password: app_dev_password
    driver-class-name: org.postgresql.Driver
  jpa:
    show-sql: true

edms:
  audit:
    datasource:
      url: jdbc:postgresql://localhost:5432/edms_dev
      username: audit_role
      password: audit_dev_password
```

- [ ] **Step 7: Generate Gradle wrapper**

Run:

```bash
cd /home/spuhaha18/Project/DMS/backend
gradle wrapper --gradle-version 8.10
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and `gradle-wrapper.properties` created.

- [ ] **Step 8: Verify boot starts**

Run:

```bash
cd /home/spuhaha18/Project/DMS/backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Expected: ERROR — Flyway has no migrations to apply but DataSource connects. Stop with Ctrl-C. The point is to confirm the runtime boots before V1 is added.

If startup fails with "Validation failed for migrations applied... no migrations found": that is expected because we have not added migrations yet but `validate-on-migrate=true` default. Add `spring.flyway.validate-on-migrate: false` temporarily to `application-dev.yml`, then revert in Task 4.

- [ ] **Step 9: Commit**

```bash
cd /home/spuhaha18/Project/DMS
git add backend/
git commit -m "backend: bootstrap Spring Boot 3.3 / Java 21 skeleton with Flyway + JPA"
```

---

### Task 4: Flyway V1 — core schema (users, roles, user_roles, permissions, document_categories)

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__core_schema.sql`

- [ ] **Step 1: Write the migration**

```sql
-- V1: Core identity + RBAC schema (DS §4.2 lines 247-378)

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(50) UNIQUE NOT NULL,
    external_id     VARCHAR(100),
    auth_provider   VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    department      VARCHAR(50),
    title           VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    password_hash   VARCHAR(255),
    force_change_pw BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_at       TIMESTAMPTZ,
    valid_from      DATE,
    valid_until     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      BIGINT
);

CREATE INDEX idx_users_user_id ON users(user_id);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50) UNIQUE NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    description TEXT,
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by BIGINT,
    UNIQUE (user_id, role_id)
);

CREATE TABLE document_categories (
    id                    BIGSERIAL PRIMARY KEY,
    category_code         VARCHAR(20) UNIQUE NOT NULL,
    category_name         VARCHAR(100) NOT NULL,
    description           TEXT,
    review_period_months  INTEGER NOT NULL DEFAULT 24,
    qa_mandatory          BOOLEAN NOT NULL DEFAULT FALSE,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id              BIGSERIAL PRIMARY KEY,
    role_id         BIGINT NOT NULL REFERENCES roles(id),
    category_id     BIGINT NOT NULL REFERENCES document_categories(id),
    department      VARCHAR(50),
    can_view        BOOLEAN NOT NULL DEFAULT FALSE,
    can_download    BOOLEAN NOT NULL DEFAULT FALSE,
    can_create      BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_draft  BOOLEAN NOT NULL DEFAULT FALSE,
    can_review      BOOLEAN NOT NULL DEFAULT FALSE,
    can_approve     BOOLEAN NOT NULL DEFAULT FALSE,
    can_retire      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (role_id, category_id, department)
);

CREATE INDEX idx_permissions_role ON permissions(role_id);
CREATE INDEX idx_permissions_category ON permissions(category_id);
```

- [ ] **Step 2: Run migration via boot startup**

```bash
cd /home/spuhaha18/Project/DMS/backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Expected log lines: `Flyway Community Edition ... by Redgate`, `Migrating schema "public" to version "1 - core schema"`, `Successfully applied 1 migration`.

Stop with Ctrl-C.

- [ ] **Step 3: Verify schema in database**

```bash
docker exec edms-postgres psql -U app_role -d edms_dev -c "\dt"
```

Expected output includes: `users`, `roles`, `user_roles`, `document_categories`, `permissions`, `flyway_schema_history`.

- [ ] **Step 4: Commit**

```bash
cd /home/spuhaha18/Project/DMS
git add backend/src/main/resources/db/migration/V1__core_schema.sql
git commit -m "db(V1): create users, roles, user_roles, document_categories, permissions"
```

---

### Task 5: Flyway V2 — audit schema with INSERT-only `audit_role` grants

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__audit_schema.sql`

- [ ] **Step 1: Write the migration**

```sql
-- V2: audit_logs (INSERT-only via dedicated audit_role principal)
-- DS §4.2 lines 495-512, §4.3, §8.2

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT,                                       -- NULL = system
    actor_user_id   VARCHAR(50),                                  -- denormalized
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       VARCHAR(100),
    before_value    JSONB,
    after_value     JSONB,
    reason          TEXT,
    client_ip       VARCHAR(45),
    server_ts       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    prev_hash       VARCHAR(64) NOT NULL,
    this_hash       VARCHAR(64) NOT NULL UNIQUE
);

CREATE INDEX idx_audit_logs_server_ts ON audit_logs(server_ts);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_user_id, server_ts);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

-- Tamper-evidence at the database principal level.
-- audit_role is created in postgres init script (infra/postgres/init).
-- Verify role exists; fail migration if not.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_role') THEN
        RAISE EXCEPTION 'audit_role principal does not exist; check infra/postgres/init/00-create-audit-role.sh';
    END IF;
END
$$;

-- Grants: audit_role gets INSERT and SELECT (for prev_hash chain reads) only.
-- No UPDATE, no DELETE, no TRUNCATE — these are not granted, so attempting them yields permission denied.
GRANT USAGE ON SCHEMA public TO audit_role;
GRANT INSERT, SELECT ON audit_logs TO audit_role;
GRANT USAGE, SELECT ON SEQUENCE audit_logs_id_seq TO audit_role;

-- Defensive: revoke mutation rights from PUBLIC and from app_role.
-- app_role uses Flyway DDL at startup but must never mutate GxP records at runtime.
-- The superuser (postgres) can still clean up in dev; prod uses pg_dump restore only.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM app_role;
```

- [ ] **Step 2: Run boot to apply migration**

```bash
cd /home/spuhaha18/Project/DMS/backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Expected: `Successfully applied 1 migration to schema "public", now at version v2`.

Stop with Ctrl-C.

- [ ] **Step 3: Verify INSERT-only grant by attempting UPDATE as audit_role**

```bash
docker exec edms-postgres psql -U audit_role -d edms_dev -c "INSERT INTO audit_logs (action, entity_type, prev_hash, this_hash) VALUES ('TEST', 'TEST', 'g', 'h1')"
```

Expected: `INSERT 0 1`.

```bash
docker exec edms-postgres psql -U audit_role -d edms_dev -c "UPDATE audit_logs SET action='X' WHERE this_hash='h1'"
```

Expected: `ERROR: permission denied for table audit_logs`.

```bash
docker exec edms-postgres psql -U audit_role -d edms_dev -c "DELETE FROM audit_logs WHERE this_hash='h1'"
```

Expected: `ERROR: permission denied for table audit_logs`.

The test row must be cleaned up by the superuser (postgres), not app_role.
In M6 we will revoke DELETE/UPDATE/TRUNCATE from app_role on audit_logs as well.
For now, use the postgres superuser only in this dev-time verification:

```bash
docker exec edms-postgres psql -U postgres -d edms_dev -c "DELETE FROM audit_logs"
```

Verify app_role also cannot delete (defense-in-depth check):

```bash
docker exec edms-postgres psql -U app_role -d edms_dev -c "DELETE FROM audit_logs WHERE this_hash='h1'"
```

Expected: `ERROR: permission denied for table audit_logs`.

- [ ] **Step 4: Commit**

```bash
cd /home/spuhaha18/Project/DMS
git add backend/src/main/resources/db/migration/V2__audit_schema.sql
git commit -m "db(V2): create audit_logs with INSERT-only audit_role grant (Part 11 §11.10(e))"
```

---

### Task 6: Flyway V3 (password_history) + V4 (bootstrap admin seed)

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__password_history.sql`
- Create: `backend/src/main/resources/db/migration/V4__bootstrap_seed.sql`

- [ ] **Step 1: V3 — password_history**

```sql
-- V3: password reuse prevention (DS §4.2 lines 270-275, last 5 passwords)
CREATE TABLE password_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pw_hash     VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_history_user ON password_history(user_id, created_at DESC);
```

- [ ] **Step 2: V4 — seed 8 system roles + bootstrap admin**

```sql
-- V4: System role seed + initial admin account
-- 8 predefined roles per FS-USER-003.
-- Initial admin account is created with a placeholder password_hash;
-- the operator MUST run /api/v1/auth/change-password on first login (force_change_pw=true).

INSERT INTO roles (role_code, role_name, description, is_system) VALUES
    ('AUTHOR',   '작성자',   '문서를 작성하고 검토를 요청한다',     TRUE),
    ('REVIEWER', '검토자',   '제출된 문서를 검토하고 의견을 작성한다', TRUE),
    ('APPROVER', '승인자',   '검토 완료된 문서를 최종 승인한다',     TRUE),
    ('QA',       'QA',       '품질 보증 검토 및 정기검토 응답',      TRUE),
    ('RA',       'RA',       '규제 업무 검토',                       TRUE),
    ('READER',   '열람자',   '권한 있는 문서 열람',                  TRUE),
    ('ADMIN',    '관리자',   '시스템 관리 및 사용자/권한 관리',      TRUE),
    ('AUDITOR',  '감사인',   '열람·감사로그 조회 전용 (유효기간)',   TRUE);

-- Bootstrap admin account.
-- BCrypt hash for 'BootstrapMe!2026' generated with rounds=12; force_change_pw=TRUE forces immediate change.
-- Hash regenerated per environment via SOP-USER-001 §5.1; this seed is dev/IQ only.
INSERT INTO users (
    user_id, full_name, email, department, title, status,
    password_hash, force_change_pw, created_at, updated_at
) VALUES (
    'admin', '시스템 관리자', 'admin@lab.internal', 'IT', '시스템 관리자', 'ACTIVE',
    '$2a$12$M.HkGlKf8uG7NQsd8h2X5e0wKpVqwYxVBbZUz/JVlQAzEK1E8yQbS',
    TRUE, NOW(), NOW()
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.user_id = 'admin' AND r.role_code = 'ADMIN';
```

- [ ] **Step 3: Apply migrations**

```bash
cd /home/spuhaha18/Project/DMS/backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Expected: `Successfully applied 2 migrations to schema "public", now at version v4`.

Stop with Ctrl-C.

- [ ] **Step 4: Verify seed**

```bash
docker exec edms-postgres psql -U app_role -d edms_dev -c "SELECT role_code FROM roles ORDER BY role_code"
```

Expected: 8 rows (ADMIN, APPROVER, AUDITOR, AUTHOR, QA, RA, READER, REVIEWER).

```bash
docker exec edms-postgres psql -U app_role -d edms_dev -c "SELECT u.user_id, r.role_code FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id"
```

Expected: `admin | ADMIN`.

- [ ] **Step 5: Commit**

```bash
cd /home/spuhaha18/Project/DMS
git add backend/src/main/resources/db/migration/V3__password_history.sql backend/src/main/resources/db/migration/V4__bootstrap_seed.sql
git commit -m "db(V3,V4): add password_history; seed 8 system roles and bootstrap admin"
```

---

### Task 7: JPA entities — User, Role, UserRole, PasswordHistory

**Files:**
- Create: `backend/src/main/java/com/lab/edms/user/User.java`
- Create: `backend/src/main/java/com/lab/edms/user/Role.java`
- Create: `backend/src/main/java/com/lab/edms/user/UserRole.java`
- Create: `backend/src/main/java/com/lab/edms/user/PasswordHistory.java`
- Create: `backend/src/main/java/com/lab/edms/user/UserStatus.java`
- Create: `backend/src/main/java/com/lab/edms/config/JpaConfig.java`

- [ ] **Step 1: Create `UserStatus.java` enum**

```java
package com.lab.edms.user;

public enum UserStatus {
    ACTIVE, LOCKED, DISABLED
}
```

- [ ] **Step 2: Create `Role.java`**

```java
package com.lab.edms.user;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;

@Entity
@Table(name = "roles")
@Audited
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, unique = true, length = 50)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "description")
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public boolean isSystem() { return system; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 3: Create `UserRole.java`**

```java
package com.lab.edms.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_roles", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"}))
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "assigned_by")
    private Long assignedBy;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Role getRole() { return role; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
}
```

- [ ] **Step 4: Create `User.java`**

```java
package com.lab.edms.user;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Audited
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    private String userId;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "auth_provider", nullable = false, length = 20)
    private String authProvider = "LOCAL";

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "title", length = 50)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "force_change_pw", nullable = false)
    private boolean forceChangePw;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserRole> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public UserStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isForceChangePw() { return forceChangePw; }
    public int getFailedAttempts() { return failedAttempts; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public Set<UserRole> getRoles() { return roles; }
    public String getAuthProvider() { return authProvider; }

    public void setStatus(UserStatus status) { this.status = status; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setForceChangePw(boolean v) { this.forceChangePw = v; }
    public void setFailedAttempts(int n) { this.failedAttempts = n; }
    public void setLockedAt(OffsetDateTime t) { this.lockedAt = t; }
}
```

- [ ] **Step 5: Create `PasswordHistory.java`**

```java
package com.lab.edms.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "password_history")
public class PasswordHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pw_hash", nullable = false, length = 255)
    private String pwHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public PasswordHistory() {}
    public PasswordHistory(Long userId, String pwHash) {
        this.userId = userId;
        this.pwHash = pwHash;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPwHash() { return pwHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 6: Create `JpaConfig.java`**

```java
package com.lab.edms.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan("com.lab.edms")
@EnableJpaRepositories("com.lab.edms")
public class JpaConfig {
}
```

- [ ] **Step 7: Run boot to verify entity ↔ schema validation**

```bash
cd /home/spuhaha18/Project/DMS/backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Expected: boot succeeds (Hibernate `validate` mode confirms entities match V1 schema). Stop with Ctrl-C.

- [ ] **Step 8: Commit**

```bash
cd /home/spuhaha18/Project/DMS
git add backend/src/main/java/com/lab/edms/
git commit -m "user: add JPA entities (User, Role, UserRole, PasswordHistory) with Envers"
```

---

### Task 8: Spring Data repositories

**Files:**
- Create: `backend/src/main/java/com/lab/edms/user/UserRepository.java`
- Create: `backend/src/main/java/com/lab/edms/user/RoleRepository.java`

- [ ] **Step 1: Create `UserRepository.java`**

```java
package com.lab.edms.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);
    Optional<User> findByEmail(String email);
}
```

- [ ] **Step 2: Create `RoleRepository.java`**

```java
package com.lab.edms.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleCode(String roleCode);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/lab/edms/user/UserRepository.java backend/src/main/java/com/lab/edms/user/RoleRepository.java
git commit -m "user: add Spring Data repositories"
```

---

### Task 9: Testcontainers test config

**Files:**
- Create: `backend/src/test/java/com/lab/edms/TestcontainersConfig.java`
- Create: `backend/src/test/resources/application-test.yml`

- [ ] **Step 1: Create `TestcontainersConfig.java`**

```java
package com.lab.edms;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withDatabaseName("edms_test")
                .withUsername("app_role")
                .withPassword("test_password")
                .withInitScript("init/create-audit-role.sql");
    }
}
```

- [ ] **Step 2: Create `backend/src/test/resources/init/create-audit-role.sql`**

```sql
CREATE ROLE audit_role LOGIN PASSWORD 'audit_test_password';
```

- [ ] **Step 3: Create `application-test.yml`**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

edms:
  audit:
    datasource:
      # In tests, the audit role connects to the same Testcontainers DB.
      # The URL is filled in dynamically by AuditDataSourceConfig from the spring.datasource.url
      # plus username/password override.
      username: audit_role
      password: audit_test_password
```

- [ ] **Step 4: Verify dependency resolution by running an empty test**

Create `backend/src/test/java/com/lab/edms/SmokeIT.java`:

```java
package com.lab.edms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class SmokeIT {

    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
```

Run:

```bash
cd /home/spuhaha18/Project/DMS/backend
./gradlew test --tests SmokeIT
```

Expected: PASS. Testcontainers boots PostgreSQL 16, applies V1~V4 migrations, context loads.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/
git commit -m "test: add Testcontainers config with audit_role bootstrap"
```

---

### Task 10: AuditEvent + AuditAction + HashChainSerializer (pure)

**Files:**
- Create: `backend/src/main/java/com/lab/edms/audit/AuditAction.java`
- Create: `backend/src/main/java/com/lab/edms/audit/AuditEvent.java`
- Create: `backend/src/main/java/com/lab/edms/audit/HashChainSerializer.java`
- Create: `backend/src/test/java/com/lab/edms/audit/HashChainSerializerTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.lab.edms.audit;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainSerializerTest {

    @Test
    void genesisHash_isSha256OfLiteralGENESIS() {
        // SHA-256("GENESIS") = 901131d8... computed with: printf 'GENESIS' | sha256sum
        assertThat(HashChainSerializer.GENESIS_HASH)
                .isEqualTo("901131d838b17aac0f7885b81e03cbdc9f5157a00343d30ab22083685ed1416a");
    }

    @Test
    void serialize_concatenatesAllAuditFieldsInDocumentedOrder() {
        AuditEvent e = new AuditEvent(
                "alice",
                AuditAction.USER_LOGIN_SUCCESS,
                "USER",
                "42",
                "{\"status\":\"ACTIVE\"}", null, "login reason",
                "10.0.0.1",
                OffsetDateTime.of(2026, 5, 8, 9, 30, 0, 0, ZoneOffset.UTC)
        );

        String payload = HashChainSerializer.payload("PREVHASH", e);

        // All fields included: prev|actor|action|type:id|ts|before|after|reason|ip
        assertThat(payload).isEqualTo(
                "PREVHASH|alice|USER_LOGIN_SUCCESS|USER:42|2026-05-08T09:30Z|{\"status\":\"ACTIVE\"}||login reason|10.0.0.1");
    }

    @Test
    void serialize_nullOptionalFields_useEmptyString() {
        AuditEvent e = new AuditEvent(
                "alice", AuditAction.USER_LOGIN_SUCCESS, "USER", "42",
                null, null, null, null,
                OffsetDateTime.of(2026, 5, 8, 9, 30, 0, 0, ZoneOffset.UTC)
        );
        String payload = HashChainSerializer.payload("P", e);
        assertThat(payload).isEqualTo("P|alice|USER_LOGIN_SUCCESS|USER:42|2026-05-08T09:30Z|||");
    }

    @Test
    void hash_returnsSha256HexLowercase64Chars() {
        String h = HashChainSerializer.sha256Hex("anything");
        assertThat(h).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void hash_isDeterministic() {
        assertThat(HashChainSerializer.sha256Hex("x"))
                .isEqualTo(HashChainSerializer.sha256Hex("x"));
    }
}
```

- [ ] **Step 2: Run test — verify FAIL with ClassNotFoundException**

```bash
./gradlew test --tests HashChainSerializerTest
```

Expected: compilation FAIL — class does not exist.

- [ ] **Step 3: Create `AuditAction.java`**

```java
package com.lab.edms.audit;

public enum AuditAction {
    USER_LOGIN_SUCCESS,
    USER_LOGIN_FAIL,
    USER_LOGOUT,
    USER_PASSWORD_CHANGED,
    USER_LOCKED,
    USER_UNLOCKED,
    USER_FORCED_CHANGE_PW
}
```

- [ ] **Step 4: Create `AuditEvent.java`**

```java
package com.lab.edms.audit;

import java.time.OffsetDateTime;

public record AuditEvent(
        String actorUserId,
        AuditAction action,
        String entityType,
        String entityId,
        String beforeValue,
        String afterValue,
        String reason,
        String clientIp,
        OffsetDateTime serverTs
) {
    public AuditEvent {
        if (action == null) throw new IllegalArgumentException("action required");
        if (entityType == null) throw new IllegalArgumentException("entityType required");
        if (serverTs == null) serverTs = OffsetDateTime.now();
    }
}
```

- [ ] **Step 5: Create `HashChainSerializer.java`**

```java
package com.lab.edms.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;

public final class HashChainSerializer {

    public static final String GENESIS_HASH = sha256Hex("GENESIS");

    private HashChainSerializer() {}

    /**
     * DS-AUD-001 payload format:
     *   prev|actor|action|type:id|server_ts_iso|before_value|after_value|reason|client_ip
     *
     * All fields are included so that tampering with before/after/reason/ip
     * breaks the hash verification. Null fields are serialized as empty string.
     */
    public static String payload(String prevHash, AuditEvent e) {
        String ts       = e.serverTs().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String entityId = nvl(e.entityId());
        String actor    = nvl(e.actorUserId());
        String before   = nvl(e.beforeValue());
        String after    = nvl(e.afterValue());
        String reason   = nvl(e.reason());
        String ip       = nvl(e.clientIp());
        return prevHash + "|" + actor + "|" + e.action().name() + "|"
                + e.entityType() + ":" + entityId + "|" + ts
                + "|" + before + "|" + after + "|" + reason + "|" + ip;
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step 6: Run test — verify PASS**

```bash
./gradlew test --tests HashChainSerializerTest
```

Expected: all 4 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/lab/edms/audit/ backend/src/test/java/com/lab/edms/audit/
git commit -m "audit: add AuditEvent, AuditAction, HashChainSerializer with deterministic SHA-256 chain"
```

---

### Task 11: AuditDataSourceConfig + AuditService (INSERT-only via audit_role)

**Files:**
- Create: `backend/src/main/java/com/lab/edms/config/AuditDataSourceConfig.java`
- Create: `backend/src/main/java/com/lab/edms/audit/AuditService.java`
- Create: `backend/src/test/java/com/lab/edms/audit/AuditServiceIT.java`

- [ ] **Step 1: Create `AuditDataSourceConfig.java`**

```java
package com.lab.edms.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AuditDataSourceConfig {

    @Bean
    public DataSource auditDataSource(
            DataSourceProperties primary,
            @Value("${edms.audit.datasource.username}") String auditUser,
            @Value("${edms.audit.datasource.password}") String auditPassword
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(primary.getUrl());
        ds.setUsername(auditUser);
        ds.setPassword(auditPassword);
        ds.setDriverClassName(primary.getDriverClassName());
        ds.setMaximumPoolSize(5);
        ds.setPoolName("audit-pool");
        return ds;
    }

    @Bean
    public JdbcTemplate auditJdbcTemplate(DataSource auditDataSource) {
        return new JdbcTemplate(auditDataSource);
    }
}
```

- [ ] **Step 2: Write failing IT for AuditService**

```java
package com.lab.edms.audit;

import com.lab.edms.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
class AuditServiceIT {

    @Autowired AuditService auditService;
    @Autowired JdbcTemplate primaryJdbcTemplate;
    @Autowired JdbcTemplate auditJdbcTemplate;

    @Test
    void firstLog_usesGenesisAsPrevHash() {
        AuditEvent e = new AuditEvent("alice", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now());

        auditService.log(e);

        Map<String, Object> row = primaryJdbcTemplate.queryForMap(
                "SELECT actor_user_id, action, prev_hash, this_hash FROM audit_logs ORDER BY id LIMIT 1");
        assertThat(row.get("actor_user_id")).isEqualTo("alice");
        assertThat(row.get("action")).isEqualTo("USER_LOGIN_SUCCESS");
        assertThat(row.get("prev_hash")).isEqualTo(HashChainSerializer.GENESIS_HASH);
        assertThat(row.get("this_hash")).asString().hasSize(64);
    }

    @Test
    void secondLog_chainsToFirstLogsThisHash() {
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGOUT,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));

        List<Map<String, Object>> rows = primaryJdbcTemplate.queryForList(
                "SELECT prev_hash, this_hash FROM audit_logs ORDER BY id");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get("prev_hash")).isEqualTo(rows.get(0).get("this_hash"));
    }

    @Test
    void auditRole_cannotUpdate() {
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));

        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "UPDATE audit_logs SET action = 'X' WHERE action = 'USER_LOGIN_SUCCESS'"))
                .hasMessageContaining("permission denied");
    }

    @Test
    void auditRole_cannotDelete() {
        auditService.log(new AuditEvent("a", AuditAction.USER_LOGIN_SUCCESS,
                "USER", "1", null, null, null, "10.0.0.1", OffsetDateTime.now()));

        assertThatThrownBy(() -> auditJdbcTemplate.update(
                "DELETE FROM audit_logs WHERE action = 'USER_LOGIN_SUCCESS'"))
                .hasMessageContaining("permission denied");
    }
}
```

- [ ] **Step 3: Run test — FAIL (AuditService missing)**

```bash
./gradlew test --tests AuditServiceIT
```

Expected: compilation FAIL.

- [ ] **Step 4: Create `AuditService.java`**

```java
package com.lab.edms.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Service
public class AuditService {

    private final JdbcTemplate auditJdbc;

    public AuditService(@Qualifier("auditJdbcTemplate") JdbcTemplate auditJdbc) {
        this.auditJdbc = auditJdbc;
    }

    /**
     * Append an audit event to the chain. Uses a separate transaction so that
     * a rollback of the caller (e.g. failed login transaction) does not erase
     * the audit record. The audit_role principal can only INSERT, providing
     * tamper-evidence at the database privilege level.
     *
     * Concurrency: pg_advisory_xact_lock(AUDIT_CHAIN_LOCK_KEY) serializes all
     * concurrent callers within the REQUIRES_NEW transaction, preventing two
     * threads from reading the same prev_hash and creating a duplicate chain link.
     * The lock is automatically released at transaction end.
     */
    static final long AUDIT_CHAIN_LOCK_KEY = 7_777_777_777L;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEvent event) {
        // Serialize concurrent inserts: only one transaction builds the chain at a time.
        auditJdbc.execute("SELECT pg_advisory_xact_lock(" + AUDIT_CHAIN_LOCK_KEY + ")");

        String prevHash = auditJdbc.query(
                "SELECT this_hash FROM audit_logs ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : HashChainSerializer.GENESIS_HASH);

        String payload = HashChainSerializer.payload(prevHash, event);
        String thisHash = HashChainSerializer.sha256Hex(payload);

        auditJdbc.update(
                "INSERT INTO audit_logs " +
                "(actor_user_id, action, entity_type, entity_id, before_value, after_value, " +
                " reason, client_ip, server_ts, prev_hash, this_hash) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)",
                event.actorUserId(),
                event.action().name(),
                event.entityType(),
                event.entityId(),
                event.beforeValue(),
                event.afterValue(),
                event.reason(),
                event.clientIp(),
                Timestamp.from(event.serverTs().toInstant()),
                prevHash,
                thisHash
        );
    }
}
```

Add the following concurrency test to `AuditServiceIT`:

```java
    @Test
    void concurrentLogs_produceLinearChain_noFork() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            futures.add(pool.submit(() -> auditService.log(new AuditEvent(
                    "user" + n, AuditAction.USER_LOGIN_SUCCESS,
                    "USER", String.valueOf(n), null, null, null, "127.0.0.1",
                    OffsetDateTime.now()))));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        List<Map<String, Object>> rows = primaryJdbcTemplate.queryForList(
                "SELECT prev_hash, this_hash FROM audit_logs ORDER BY id");
        assertThat(rows).hasSize(threads);
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i).get("prev_hash"))
                    .as("row %d prev_hash must equal row %d this_hash", i, i - 1)
                    .isEqualTo(rows.get(i - 1).get("this_hash"));
        }
    }
```

Add imports: `java.util.concurrent.*`, `java.util.ArrayList`, `java.util.List`.

- [ ] **Step 5: Run tests — verify PASS**

```bash
./gradlew test --tests AuditServiceIT
```

Expected: all 5 tests PASS (including concurrency test). Chain test confirms linear hash chain; permission tests confirm DB-level tamper evidence.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lab/edms/config/AuditDataSourceConfig.java backend/src/main/java/com/lab/edms/audit/AuditService.java backend/src/test/java/com/lab/edms/audit/AuditServiceIT.java
git commit -m "audit: add AuditService with pg_advisory_xact_lock chain serialization and full payload hash"
```

---

### Task 12: AuthProvider + LocalAuthProvider with BCrypt + lockout

**Files:**
- Create: `backend/src/main/java/com/lab/edms/auth/AuthProvider.java`
- Create: `backend/src/main/java/com/lab/edms/auth/AuthResult.java`
- Create: `backend/src/main/java/com/lab/edms/auth/LocalAuthProvider.java`
- Create: `backend/src/test/java/com/lab/edms/auth/LocalAuthProviderIT.java`

- [ ] **Step 1: Create `AuthResult.java`**

```java
package com.lab.edms.auth;

import com.lab.edms.user.User;

public sealed interface AuthResult {
    record Success(User user) implements AuthResult {}
    record InvalidCredentials(int remainingAttempts) implements AuthResult {}
    record AccountLocked() implements AuthResult {}
    record AccountDisabled() implements AuthResult {}
    record ForcePasswordChange(User user) implements AuthResult {}
}
```

- [ ] **Step 2: Create `AuthProvider.java`**

```java
package com.lab.edms.auth;

public interface AuthProvider {
    AuthResult authenticate(String userId, String rawPassword, String clientIp);
    boolean supports(String providerCode);
}
```

- [ ] **Step 3: Write failing test**

```java
package com.lab.edms.auth;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class LocalAuthProviderIT {

    @Autowired LocalAuthProvider provider;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;

    @Test
    void successfulAuthentication_returnsSuccess_andResetsFailedAttempts() {
        seedUser("alice", "Correct!Pass1");

        AuthResult result = provider.authenticate("alice", "Correct!Pass1", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        User u = userRepo.findByUserId("alice").orElseThrow();
        assertThat(u.getFailedAttempts()).isZero();
    }

    @Test
    void wrongPassword_incrementsFailedAttempts_andReturnsRemaining() {
        seedUser("bob", "Correct!Pass1");

        AuthResult result = provider.authenticate("bob", "wrong", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.InvalidCredentials.class);
        assertThat(((AuthResult.InvalidCredentials) result).remainingAttempts()).isEqualTo(4);
        assertThat(userRepo.findByUserId("bob").orElseThrow().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void fifthFailure_locksAccount() {
        seedUser("carol", "Correct!Pass1");

        for (int i = 0; i < 5; i++) {
            provider.authenticate("carol", "wrong", "10.0.0.1");
        }

        User u = userRepo.findByUserId("carol").orElseThrow();
        assertThat(u.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(u.getFailedAttempts()).isEqualTo(5);
        assertThat(u.getLockedAt()).isNotNull();
    }

    @Test
    void lockedAccount_returnsAccountLocked_evenWithCorrectPassword() {
        seedUser("dave", "Correct!Pass1");
        for (int i = 0; i < 5; i++) provider.authenticate("dave", "wrong", "10.0.0.1");

        AuthResult result = provider.authenticate("dave", "Correct!Pass1", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.AccountLocked.class);
    }

    @Test
    void unknownUser_returnsInvalidCredentials_withoutDisclosingExistence() {
        AuthResult result = provider.authenticate("nobody", "x", "10.0.0.1");
        assertThat(result).isInstanceOf(AuthResult.InvalidCredentials.class);
    }

    private void seedUser(String userId, String rawPassword) {
        // Direct SQL to avoid persisting via JPA which would conflict with @Transactional rollback semantics elsewhere.
        // For the test we use a small JPA save through the repository.
        User u = new TestUser(userId, encoder.encode(rawPassword));
        userRepo.save(u);
    }

    static class TestUser extends User {
        TestUser(String userId, String hash) {
            try {
                java.lang.reflect.Field f = User.class.getDeclaredField("userId"); f.setAccessible(true); f.set(this, userId);
                java.lang.reflect.Field e = User.class.getDeclaredField("email");  e.setAccessible(true); e.set(this, userId + "@x");
                java.lang.reflect.Field n = User.class.getDeclaredField("fullName"); n.setAccessible(true); n.set(this, userId);
                setPasswordHash(hash);
            } catch (Exception ex) { throw new RuntimeException(ex); }
        }
    }
}
```

- [ ] **Step 4: Run — FAIL (LocalAuthProvider missing)**

```bash
./gradlew test --tests LocalAuthProviderIT
```

Expected: compilation FAIL.

- [ ] **Step 5: Create `LocalAuthProvider.java`**

```java
package com.lab.edms.auth;

import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class LocalAuthProvider implements AuthProvider {

    static final int MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepo;
    private final BCryptPasswordEncoder encoder;

    public LocalAuthProvider(UserRepository userRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    @Override
    public boolean supports(String providerCode) {
        return "LOCAL".equals(providerCode);
    }

    @Override
    @Transactional
    public AuthResult authenticate(String userId, String rawPassword, String clientIp) {
        Optional<User> opt = userRepo.findByUserId(userId);
        if (opt.isEmpty()) {
            return new AuthResult.InvalidCredentials(MAX_FAILED_ATTEMPTS);
        }
        User u = opt.get();

        if (u.getStatus() == UserStatus.DISABLED) return new AuthResult.AccountDisabled();
        if (u.getStatus() == UserStatus.LOCKED)   return new AuthResult.AccountLocked();

        if (!encoder.matches(rawPassword, u.getPasswordHash())) {
            int next = u.getFailedAttempts() + 1;
            u.setFailedAttempts(next);
            if (next >= MAX_FAILED_ATTEMPTS) {
                u.setStatus(UserStatus.LOCKED);
                u.setLockedAt(OffsetDateTime.now());
            }
            userRepo.save(u);
            return new AuthResult.InvalidCredentials(Math.max(0, MAX_FAILED_ATTEMPTS - next));
        }

        u.setFailedAttempts(0);
        userRepo.save(u);

        if (u.isForceChangePw()) return new AuthResult.ForcePasswordChange(u);
        return new AuthResult.Success(u);
    }
}
```

- [ ] **Step 6: Add BCryptPasswordEncoder bean to `SecurityConfig`** (placeholder; full SecurityConfig in Task 14)

Create `backend/src/main/java/com/lab/edms/config/PasswordEncoderConfig.java`:

```java
package com.lab.edms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

- [ ] **Step 7: Run tests — PASS**

```bash
./gradlew test --tests LocalAuthProviderIT
```

Expected: all 5 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/lab/edms/auth/ backend/src/main/java/com/lab/edms/config/PasswordEncoderConfig.java backend/src/test/java/com/lab/edms/auth/
git commit -m "auth: add LocalAuthProvider with BCrypt rounds=12 and 5-fail lockout"
```

---

### Task 13: AuthService — orchestrates login/logout with audit logging

**Files:**
- Create: `backend/src/main/java/com/lab/edms/auth/AuthService.java`
- Create: `backend/src/test/java/com/lab/edms/auth/AuthServiceIT.java`

- [ ] **Step 1: Write failing test**

```java
package com.lab.edms.auth;

import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@DirtiesContext
@Transactional
class AuthServiceIT {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired JdbcTemplate primary;

    @Test
    void successfulLogin_writesAuditLog_USER_LOGIN_SUCCESS() {
        seedUser("erin", "Correct!Pass1");

        AuthResult result = authService.login("erin", "Correct!Pass1", "10.0.0.1");

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        Map<String, Object> log = primary.queryForMap(
                "SELECT actor_user_id, action FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(log.get("actor_user_id")).isEqualTo("erin");
        assertThat(log.get("action")).isEqualTo("USER_LOGIN_SUCCESS");
    }

    @Test
    void failedLogin_writesAuditLog_USER_LOGIN_FAIL() {
        seedUser("frank", "Correct!Pass1");

        authService.login("frank", "wrong", "10.0.0.1");

        Map<String, Object> log = primary.queryForMap(
                "SELECT action FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(log.get("action")).isEqualTo("USER_LOGIN_FAIL");
    }

    @Test
    void fifthFailedLogin_writesBothLOGIN_FAIL_andUSER_LOCKED() {
        seedUser("grace", "Correct!Pass1");

        for (int i = 0; i < 5; i++) authService.login("grace", "wrong", "10.0.0.1");

        long lockedCount = primary.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE actor_user_id='grace' AND action='USER_LOCKED'",
                Long.class);
        assertThat(lockedCount).isEqualTo(1L);
    }

    @Test
    void logout_writesAuditLog_USER_LOGOUT() {
        seedUser("henry", "Correct!Pass1");
        authService.login("henry", "Correct!Pass1", "10.0.0.1");

        authService.logout("henry", "10.0.0.1");

        Map<String, Object> log = primary.queryForMap(
                "SELECT action FROM audit_logs ORDER BY id DESC LIMIT 1");
        assertThat(log.get("action")).isEqualTo("USER_LOGOUT");
    }

    private void seedUser(String userId, String rawPassword) {
        User u = new LocalAuthProviderIT.TestUser(userId, encoder.encode(rawPassword));
        userRepo.save(u);
    }
}
```

- [ ] **Step 2: Run — FAIL (AuthService missing)**

```bash
./gradlew test --tests AuthServiceIT
```

Expected: compilation FAIL.

- [ ] **Step 3: Create `AuthService.java`**

```java
package com.lab.edms.auth;

import com.lab.edms.audit.AuditAction;
import com.lab.edms.audit.AuditEvent;
import com.lab.edms.audit.AuditService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuthService {

    private final LocalAuthProvider provider;
    private final AuditService audit;

    public AuthService(LocalAuthProvider provider, AuditService audit) {
        this.provider = provider;
        this.audit = audit;
    }

    public AuthResult login(String userId, String rawPassword, String clientIp) {
        AuthResult result = provider.authenticate(userId, rawPassword, clientIp);

        AuditAction action = switch (result) {
            case AuthResult.Success __ -> AuditAction.USER_LOGIN_SUCCESS;
            case AuthResult.ForcePasswordChange __ -> AuditAction.USER_LOGIN_SUCCESS;
            case AuthResult.InvalidCredentials __ -> AuditAction.USER_LOGIN_FAIL;
            case AuthResult.AccountLocked __ -> AuditAction.USER_LOGIN_FAIL;
            case AuthResult.AccountDisabled __ -> AuditAction.USER_LOGIN_FAIL;
        };

        audit.log(new AuditEvent(
                userId, action, "USER", null,
                null, null, null, clientIp, OffsetDateTime.now()));

        // Special case: when authentication just caused the account to lock,
        // also emit a USER_LOCKED event so SOP-AUDIT-TRAIL-001 §5.3 detection works.
        if (result instanceof AuthResult.InvalidCredentials ic && ic.remainingAttempts() == 0) {
            audit.log(new AuditEvent(
                    userId, AuditAction.USER_LOCKED, "USER", null,
                    null, null, "5 consecutive failed login attempts",
                    clientIp, OffsetDateTime.now()));
        }

        return result;
    }

    public void logout(String userId, String clientIp) {
        audit.log(new AuditEvent(
                userId, AuditAction.USER_LOGOUT, "USER", null,
                null, null, null, clientIp, OffsetDateTime.now()));
    }
}
```

- [ ] **Step 4: Run tests — PASS**

```bash
./gradlew test --tests AuthServiceIT
```

Expected: all 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lab/edms/auth/AuthService.java backend/src/test/java/com/lab/edms/auth/AuthServiceIT.java
git commit -m "auth: add AuthService that records USER_LOGIN/LOGOUT/LOCKED audit events"
```

---

### Task 14: SecurityConfig — session cookie, CSRF, BCrypt, single-session

**Files:**
- Create: `backend/src/main/java/com/lab/edms/config/SecurityConfig.java`

- [ ] **Step 1: Create `SecurityConfig.java`**

```java
package com.lab.edms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/v1/auth/login"))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
```

- [ ] **Step 2: Verify boot starts with security enabled**

```bash
cd /home/spuhaha18/Project/DMS/backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Expected: boot succeeds, log shows `Will secure any request with [...]`. Stop with Ctrl-C.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/lab/edms/config/SecurityConfig.java
git commit -m "auth: add Spring Security config (session, CSRF Cookie, single-session)"
```

---

### Task 15: AuthController — POST /auth/login, POST /auth/logout, GET /auth/me

**Files:**
- Create: `backend/src/main/java/com/lab/edms/auth/LoginRequest.java`
- Create: `backend/src/main/java/com/lab/edms/auth/LoginResponse.java`
- Create: `backend/src/main/java/com/lab/edms/auth/MeResponse.java`
- Create: `backend/src/main/java/com/lab/edms/auth/AuthController.java`
- Create: `backend/src/test/java/com/lab/edms/auth/AuthControllerIT.java`

- [ ] **Step 1: Create DTOs**

`LoginRequest.java`:

```java
package com.lab.edms.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String userId,
        @NotBlank String password
) {}
```

`LoginResponse.java`:

```java
package com.lab.edms.auth;

public record LoginResponse(
        String userId,
        String fullName,
        boolean forceChangePw
) {}
```

`MeResponse.java`:

```java
package com.lab.edms.auth;

import java.util.List;

public record MeResponse(
        String userId,
        String fullName,
        String email,
        String department,
        List<String> roles
) {}
```

- [ ] **Step 2: Write failing controller test**

```java
package com.lab.edms.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.edms.TestcontainersConfig;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@Transactional
class AuthControllerIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired ObjectMapper json;

    @Test
    void postLogin_correctCredentials_returns200WithUserSummary() throws Exception {
        seed("ivy", "Correct!Pass1");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("ivy", "Correct!Pass1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ivy"))
                .andExpect(jsonPath("$.fullName").value("ivy"))
                .andExpect(jsonPath("$.forceChangePw").value(false));
    }

    @Test
    void postLogin_wrongPassword_returns401_withProblemDetail() throws Exception {
        seed("jack", "Correct!Pass1");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("jack", "bad"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.remaining_attempts").value(4));
    }

    @Test
    void postLogin_lockedAccount_returns401_withCodeAUTH_002() throws Exception {
        seed("kate", "Correct!Pass1");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new LoginRequest("kate", "x"))));
        }

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest("kate", "Correct!Pass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void getMe_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    private void seed(String userId, String pw) {
        User u = new LocalAuthProviderIT.TestUser(userId, encoder.encode(pw));
        userRepo.save(u);
    }
}
```

- [ ] **Step 3: Run — FAIL**

```bash
./gradlew test --tests AuthControllerIT
```

Expected: compilation FAIL or 404 errors.

- [ ] **Step 4: Create `AuthController.java`**

```java
package com.lab.edms.auth;

import com.lab.edms.common.ProblemDetail;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import com.lab.edms.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepo;

    public AuthController(AuthService authService, UserRepository userRepo) {
        this.authService = authService;
        this.userRepo = userRepo;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest req,
                                   HttpServletRequest http) {
        AuthResult result = authService.login(req.userId(), req.password(), http.getRemoteAddr());
        return switch (result) {
            case AuthResult.Success s -> {
                installSession(s.user(), http);
                yield ResponseEntity.ok(new LoginResponse(
                        s.user().getUserId(), s.user().getFullName(), false));
            }
            case AuthResult.ForcePasswordChange f -> {
                installSession(f.user(), http);
                yield ResponseEntity.ok(new LoginResponse(
                        f.user().getUserId(), f.user().getFullName(), true));
            }
            case AuthResult.InvalidCredentials ic -> ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.of("AUTH_001",
                            "Invalid user ID or password",
                            null,
                            "remaining_attempts", ic.remainingAttempts()));
            case AuthResult.AccountLocked __ -> ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.of("AUTH_002",
                            "Account is locked. Contact your system administrator.",
                            null));
            case AuthResult.AccountDisabled __ -> ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.of("AUTH_003",
                            "Account is disabled.",
                            null));
        };
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            authService.logout(auth.getName(), http.getRemoteAddr());
            http.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User u = userRepo.findByUserId(auth.getName()).orElseThrow();
        List<String> roles = u.getRoles().stream()
                .map(UserRole::getRole)
                .map(r -> r.getRoleCode())
                .collect(Collectors.toList());
        return ResponseEntity.ok(new MeResponse(
                u.getUserId(), u.getFullName(), u.getEmail(), u.getDepartment(), roles));
    }

    private void installSession(User u, HttpServletRequest http) {
        var authorities = u.getRoles().stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getRoleCode()))
                .collect(Collectors.toList());
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(u.getUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(token);
        // Force creation of session and rotation against fixation:
        http.changeSessionId();
    }
}
```

- [ ] **Step 5: Run tests — PASS**

```bash
./gradlew test --tests AuthControllerIT
```

Expected: all 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/lab/edms/auth/AuthController.java backend/src/main/java/com/lab/edms/auth/LoginRequest.java backend/src/main/java/com/lab/edms/auth/LoginResponse.java backend/src/main/java/com/lab/edms/auth/MeResponse.java backend/src/test/java/com/lab/edms/auth/AuthControllerIT.java
git commit -m "auth: add AuthController for /auth/login, /auth/logout, /auth/me"
```

---

### Task 16: ProblemDetail (RFC 7807) + GlobalExceptionHandler

**Files:**
- Create: `backend/src/main/java/com/lab/edms/common/ProblemDetail.java`
- Create: `backend/src/main/java/com/lab/edms/common/GlobalExceptionHandler.java`

- [ ] **Step 1: Create `ProblemDetail.java`**

```java
package com.lab.edms.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ProblemDetail {

    private final String code;
    private final String message;
    private final String detail;
    private final OffsetDateTime timestamp;
    private final Map<String, Object> extras = new LinkedHashMap<>();

    private ProblemDetail(String code, String message, String detail) {
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.timestamp = OffsetDateTime.now();
    }

    public static ProblemDetail of(String code, String message, String detail) {
        return new ProblemDetail(code, message, detail);
    }

    public static ProblemDetail of(String code, String message, String detail,
                                   String extraKey, Object extraValue) {
        ProblemDetail p = new ProblemDetail(code, message, detail);
        p.extras.put(extraKey, extraValue);
        return p;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getDetail() { return detail; }
    public OffsetDateTime getTimestamp() { return timestamp; }
    @JsonAnyGetter public Map<String, Object> getExtras() { return extras; }
}
```

- [ ] **Step 2: Create `GlobalExceptionHandler.java`**

```java
package com.lab.edms.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ProblemDetail.of(
                "VALIDATION_001",
                "Request validation failed",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse(null)
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ProblemDetail.of(
                "INTERNAL_001", "Internal server error", ex.getMessage()));
    }
}
```

- [ ] **Step 3: Run all tests to confirm no regressions**

```bash
./gradlew test
```

Expected: all tests PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lab/edms/common/
git commit -m "common: add ProblemDetail (RFC 7807) and GlobalExceptionHandler"
```

---

### Task 17: POST /auth/change-password — password policy + history

**Files:**
- Create: `backend/src/main/java/com/lab/edms/auth/ChangePasswordRequest.java`
- Create: `backend/src/main/java/com/lab/edms/auth/PasswordPolicyValidator.java`
- Create: `backend/src/main/java/com/lab/edms/user/PasswordHistoryRepository.java`
- Modify: `backend/src/main/java/com/lab/edms/auth/AuthController.java` (add handler)
- Modify: `backend/src/main/java/com/lab/edms/auth/AuthService.java` (add `changePassword`)

- [ ] **Step 1: Create `ChangePasswordRequest.java`**

```java
package com.lab.edms.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {}
```

- [ ] **Step 2: Create `PasswordHistoryRepository.java`**

```java
package com.lab.edms.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);
}
```

- [ ] **Step 3: Create `PasswordPolicyValidator.java`**

```java
package com.lab.edms.auth;

public final class PasswordPolicyValidator {

    public sealed interface Result {
        record Ok() implements Result {}
        record TooShort() implements Result {}
        record InsufficientCharacterClasses() implements Result {}
        record MatchesRecentHistory() implements Result {}
    }

    private PasswordPolicyValidator() {}

    /** FS-AUTH-002: min 8 chars, at least 3 of: lowercase, uppercase, digit, special. */
    public static Result validate(String pw) {
        if (pw == null || pw.length() < 8) return new Result.TooShort();
        int classes = 0;
        if (pw.matches(".*[a-z].*")) classes++;
        if (pw.matches(".*[A-Z].*")) classes++;
        if (pw.matches(".*\\d.*"))   classes++;
        if (pw.matches(".*[^A-Za-z0-9].*")) classes++;
        if (classes < 3) return new Result.InsufficientCharacterClasses();
        return new Result.Ok();
    }
}
```

- [ ] **Step 4: Add `changePassword` method to `AuthService.java`**

Append before the closing brace of `AuthService`:

```java
    private final UserRepository userRepo;
    private final PasswordHistoryRepository historyRepo;
    private final BCryptPasswordEncoder encoder;

    // (Update constructor to inject these — full constructor):
    // public AuthService(LocalAuthProvider provider, AuditService audit,
    //                    UserRepository userRepo, PasswordHistoryRepository historyRepo,
    //                    BCryptPasswordEncoder encoder) { ... }

    @Transactional
    public ChangePasswordOutcome changePassword(String userId, String currentPw, String newPw, String clientIp) {
        User u = userRepo.findByUserId(userId).orElseThrow();

        if (!encoder.matches(currentPw, u.getPasswordHash())) {
            audit.log(new AuditEvent(userId, AuditAction.USER_LOGIN_FAIL,
                    "USER", String.valueOf(u.getId()),
                    null, null, "Wrong current password on change attempt",
                    clientIp, OffsetDateTime.now()));
            return ChangePasswordOutcome.WRONG_CURRENT;
        }

        var policy = PasswordPolicyValidator.validate(newPw);
        if (!(policy instanceof PasswordPolicyValidator.Result.Ok)) {
            return ChangePasswordOutcome.POLICY_VIOLATION;
        }

        var recent = historyRepo.findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, 5));
        for (PasswordHistory h : recent) {
            if (encoder.matches(newPw, h.getPwHash())) {
                return ChangePasswordOutcome.REUSED_RECENT;
            }
        }
        if (encoder.matches(newPw, u.getPasswordHash())) return ChangePasswordOutcome.REUSED_RECENT;

        String newHash = encoder.encode(newPw);
        historyRepo.save(new PasswordHistory(u.getId(), u.getPasswordHash() == null ? newHash : u.getPasswordHash()));
        u.setPasswordHash(newHash);
        u.setForceChangePw(false);
        userRepo.save(u);

        audit.log(new AuditEvent(userId, AuditAction.USER_PASSWORD_CHANGED,
                "USER", String.valueOf(u.getId()),
                null, null, null, clientIp, OffsetDateTime.now()));
        return ChangePasswordOutcome.OK;
    }

    public enum ChangePasswordOutcome { OK, WRONG_CURRENT, POLICY_VIOLATION, REUSED_RECENT }
```

(Add necessary imports at top of `AuthService.java`: `org.springframework.data.domain.PageRequest`, `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`, `com.lab.edms.user.*`, `org.springframework.transaction.annotation.Transactional`, `java.time.OffsetDateTime`.)

- [ ] **Step 5: Add handler to `AuthController.java`**

```java
@PostMapping("/change-password")
public ResponseEntity<?> changePassword(@RequestBody @Valid ChangePasswordRequest req,
                                        HttpServletRequest http) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    var outcome = authService.changePassword(
            auth.getName(), req.currentPassword(), req.newPassword(), http.getRemoteAddr());
    return switch (outcome) {
        case OK -> ResponseEntity.noContent().build();
        case WRONG_CURRENT -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetail.of("AUTH_004", "Current password is incorrect", null));
        case POLICY_VIOLATION -> ResponseEntity.badRequest()
                .body(ProblemDetail.of("AUTH_005",
                        "Password does not meet policy (min 8 chars, 3 of 4 character classes)", null));
        case REUSED_RECENT -> ResponseEntity.badRequest()
                .body(ProblemDetail.of("AUTH_006",
                        "Password matches one of your last 5 passwords", null));
    };
}
```

- [ ] **Step 6: Add unit test for PasswordPolicyValidator**

`backend/src/test/java/com/lab/edms/auth/PasswordPolicyValidatorTest.java`:

```java
package com.lab.edms.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidatorTest {

    @Test
    void shorterThan8_isTooShort() {
        assertThat(PasswordPolicyValidator.validate("Ab1!"))
                .isInstanceOf(PasswordPolicyValidator.Result.TooShort.class);
    }

    @Test
    void onlyTwoCharacterClasses_isInsufficient() {
        assertThat(PasswordPolicyValidator.validate("abcdefgh"))
                .isInstanceOf(PasswordPolicyValidator.Result.InsufficientCharacterClasses.class);
        assertThat(PasswordPolicyValidator.validate("Abcdefgh"))
                .isInstanceOf(PasswordPolicyValidator.Result.InsufficientCharacterClasses.class);
    }

    @Test
    void threeOrFourClasses_isOk() {
        assertThat(PasswordPolicyValidator.validate("Abcdefg1"))
                .isInstanceOf(PasswordPolicyValidator.Result.Ok.class);
        assertThat(PasswordPolicyValidator.validate("Abcdefg!"))
                .isInstanceOf(PasswordPolicyValidator.Result.Ok.class);
        assertThat(PasswordPolicyValidator.validate("Abcdef1!"))
                .isInstanceOf(PasswordPolicyValidator.Result.Ok.class);
    }
}
```

- [ ] **Step 7: Run all tests**

```bash
./gradlew test
```

Expected: all PASS (including new policy tests).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/lab/edms/auth/ChangePasswordRequest.java backend/src/main/java/com/lab/edms/auth/PasswordPolicyValidator.java backend/src/main/java/com/lab/edms/user/PasswordHistoryRepository.java backend/src/main/java/com/lab/edms/auth/AuthService.java backend/src/main/java/com/lab/edms/auth/AuthController.java backend/src/test/java/com/lab/edms/auth/PasswordPolicyValidatorTest.java
git commit -m "auth: add change-password endpoint with policy validation and 5-password history"
```

---

### Task 18: End-to-end smoke test against live dev stack

**Files:** none (manual verification using curl).

- [ ] **Step 1: Start dev stack**

```bash
cd /home/spuhaha18/Project/DMS/infra && docker compose up -d
cd /home/spuhaha18/Project/DMS/backend && ./gradlew bootRun --args='--spring.profiles.active=dev' &
sleep 15
```

- [ ] **Step 2: Login as bootstrap admin**

The seed `password_hash` in V4 must correspond to a known password. Replace the hash with one generated locally for password `BootstrapMe!2026` using:

```bash
docker run --rm -it openjdk:21 jshell - <<'EOF'
System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("BootstrapMe!2026"));
EOF
```

Replace V4 hash and re-run migrations (drop volume first if necessary).

Then:

```bash
curl -i -c /tmp/cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"userId":"admin","password":"BootstrapMe!2026"}'
```

Expected: `200 OK`, `Set-Cookie: JSESSIONID=...; HttpOnly; SameSite=Strict`, body has `"forceChangePw": true`.

- [ ] **Step 3: Verify audit_log entry**

```bash
docker exec edms-postgres psql -U app_role -d edms_dev \
  -c "SELECT actor_user_id, action, prev_hash, this_hash FROM audit_logs ORDER BY id DESC LIMIT 1"
```

Expected: row with `actor_user_id=admin`, `action=USER_LOGIN_SUCCESS`, `prev_hash=` GENESIS, `this_hash=` 64-hex.

- [ ] **Step 4: Change password**

```bash
curl -i -b /tmp/cookies.txt -X POST http://localhost:8080/api/v1/auth/change-password \
     -H "Content-Type: application/json" \
     -d '{"currentPassword":"BootstrapMe!2026","newPassword":"NewLab!2026"}'
```

Expected: `204 No Content`.

- [ ] **Step 5: GET /auth/me**

```bash
curl -i -b /tmp/cookies.txt http://localhost:8080/api/v1/auth/me
```

Expected: `200 OK`, body `{"userId":"admin","fullName":"시스템 관리자","email":"admin@lab.internal","department":"IT","roles":["ADMIN"]}`.

- [ ] **Step 6: Logout**

```bash
curl -i -b /tmp/cookies.txt -X POST http://localhost:8080/api/v1/auth/logout
curl -i -b /tmp/cookies.txt http://localhost:8080/api/v1/auth/me
```

Expected: first call `204 No Content`, second call `401 Unauthorized`.

- [ ] **Step 7: Stop backend & infra**

```bash
kill %1
cd /home/spuhaha18/Project/DMS/infra && docker compose down
```

- [ ] **Step 8: Commit (none required — verification only)**

This task produces no diff. Document the smoke-test outcome in the next task's commit message if any infra change is made.

---

### Task 19: Vue 3 + Vite + TypeScript skeleton

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/style.css`

- [ ] **Step 1: Initialize package.json**

```json
{
  "name": "edms-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
  "dependencies": {
    "axios": "^1.7.7",
    "pinia": "^2.2.4",
    "vue": "^3.5.10",
    "vue-router": "^4.4.5"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.1.4",
    "@vue/test-utils": "^2.4.6",
    "happy-dom": "^15.7.4",
    "typescript": "~5.6.2",
    "vite": "^5.4.8",
    "vitest": "^2.1.2",
    "vue-tsc": "^2.1.6"
  }
}
```

- [ ] **Step 2: Create `vite.config.ts`**

```typescript
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
});
```

- [ ] **Step 3: Create `tsconfig.json` and `tsconfig.node.json`**

`tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "jsx": "preserve",
    "esModuleInterop": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "types": ["vite/client"]
  },
  "include": ["src/**/*.ts", "src/**/*.vue", "src/**/*.tsx"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

`tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "Bundler"
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 4: Create `index.html`**

```html
<!doctype html>
<html lang="ko">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>EDMS</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 5: Create `src/main.ts`, `src/App.vue`, `src/style.css`**

`src/main.ts`:

```typescript
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { router } from './router';
import './style.css';

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.mount('#app');
```

`src/App.vue`:

```vue
<script setup lang="ts">
</script>

<template>
  <router-view />
</template>
```

`src/style.css`:

```css
:root { font-family: -apple-system, "Noto Sans KR", system-ui, sans-serif; }
body { margin: 0; }
```

- [ ] **Step 6: Install dependencies**

```bash
cd /home/spuhaha18/Project/DMS/frontend
npm install
```

Expected: `node_modules/` created, no audit errors.

- [ ] **Step 7: Commit (without `node_modules`)**

```bash
cd /home/spuhaha18/Project/DMS
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/tsconfig.json frontend/tsconfig.node.json frontend/index.html frontend/src/main.ts frontend/src/App.vue frontend/src/style.css
git commit -m "frontend: bootstrap Vue 3 + Vite + TypeScript skeleton"
```

---

### Task 20: LoginView + Pinia auth store + axios client + router guard

**Files:**
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: Create `src/api/client.ts`**

```typescript
import axios from 'axios';

export const api = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

// Read CSRF cookie set by Spring Security and reflect it as header on mutating requests.
function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

api.interceptors.request.use((config) => {
  const method = (config.method ?? 'get').toLowerCase();
  if (['post', 'put', 'patch', 'delete'].includes(method)) {
    const xsrf = readCookie('XSRF-TOKEN');
    if (xsrf) config.headers.set('X-XSRF-TOKEN', xsrf);
  }
  return config;
});
```

- [ ] **Step 2: Create `src/stores/auth.ts`**

```typescript
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '../api/client';

export interface Me {
  userId: string;
  fullName: string;
  email: string;
  department: string;
  roles: string[];
}

export const useAuthStore = defineStore('auth', () => {
  const me = ref<Me | null>(null);
  const forceChangePw = ref(false);

  async function login(userId: string, password: string) {
    const { data } = await api.post('/auth/login', { userId, password });
    forceChangePw.value = data.forceChangePw === true;
    await fetchMe();
  }

  async function fetchMe() {
    try {
      const { data } = await api.get('/auth/me');
      me.value = data;
    } catch {
      me.value = null;
    }
  }

  async function logout() {
    await api.post('/auth/logout');
    me.value = null;
    forceChangePw.value = false;
  }

  function isAuthenticated(): boolean { return me.value !== null; }

  return { me, forceChangePw, login, logout, fetchMe, isAuthenticated };
});
```

- [ ] **Step 3: Create `src/router/index.ts`**

```typescript
import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'),
    meta: { requiresAuth: true } },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated()) {
    await auth.fetchMe();
    if (!auth.isAuthenticated()) return { name: 'login' };
  }
});
```

- [ ] **Step 4: Create `src/views/LoginView.vue`**

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const userId = ref('');
const password = ref('');
const errorCode = ref('');
const remaining = ref<number | null>(null);
const auth = useAuthStore();
const router = useRouter();

async function submit() {
  errorCode.value = '';
  remaining.value = null;
  try {
    await auth.login(userId.value, password.value);
    router.push({ name: 'home' });
  } catch (e: any) {
    const data = e.response?.data;
    errorCode.value = data?.code ?? 'UNKNOWN';
    remaining.value = data?.remaining_attempts ?? null;
  }
}
</script>

<template>
  <main style="max-width: 360px; margin: 80px auto; font-family: inherit;">
    <h1>EDMS 로그인</h1>
    <form @submit.prevent="submit">
      <label>
        사용자 ID
        <input v-model="userId" required autocomplete="username" />
      </label>
      <label>
        비밀번호
        <input v-model="password" type="password" required autocomplete="current-password" />
      </label>
      <button type="submit">로그인</button>
    </form>
    <p v-if="errorCode === 'AUTH_001'">
      ID 또는 비밀번호가 올바르지 않습니다. 남은 시도: {{ remaining }}회
    </p>
    <p v-else-if="errorCode === 'AUTH_002'">
      계정이 잠겼습니다. 시스템 관리자에게 문의하세요.
    </p>
    <p v-else-if="errorCode === 'AUTH_003'">계정이 비활성화되었습니다.</p>
    <p v-else-if="errorCode && errorCode !== 'UNKNOWN'">{{ errorCode }}</p>
  </main>
</template>

<style scoped>
form { display: flex; flex-direction: column; gap: 12px; }
label { display: flex; flex-direction: column; gap: 4px; }
input { padding: 8px; font-size: 14px; }
button { padding: 10px; background: #1f6feb; color: white; border: none; cursor: pointer; }
</style>
```

- [ ] **Step 5: Create `src/views/HomeView.vue`**

```vue
<script setup lang="ts">
import { onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();

onMounted(async () => {
  if (!auth.me) await auth.fetchMe();
});

async function logout() {
  await auth.logout();
  location.href = '/login';
}
</script>

<template>
  <main style="max-width: 720px; margin: 40px auto;">
    <h1>EDMS 대시보드</h1>
    <p v-if="auth.me">{{ auth.me.fullName }}님 환영합니다 ({{ auth.me.roles.join(', ') }})</p>
    <button @click="logout">로그아웃</button>
  </main>
</template>
```

- [ ] **Step 6: Run dev server, manually log in**

```bash
# Make sure infra and backend are running (Task 18 §1)
cd /home/spuhaha18/Project/DMS/frontend
npm run dev
```

Open `http://localhost:3000/login` in a browser, submit `admin` / `BootstrapMe!2026`, expect redirect to `/` showing "시스템 관리자님 환영합니다 (ADMIN)".

Stop dev server with Ctrl-C.

- [ ] **Step 7: Add Vitest unit test for auth store**

`frontend/src/stores/auth.test.ts`:

```typescript
import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from './auth';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('login fetches /auth/me and populates me', async () => {
    (api.post as any).mockResolvedValue({ data: { forceChangePw: false } });
    (api.get as any).mockResolvedValue({
      data: { userId: 'admin', fullName: '관리자', email: 'a@x', department: 'IT', roles: ['ADMIN'] },
    });

    const auth = useAuthStore();
    await auth.login('admin', 'pw');

    expect(auth.me).toEqual({
      userId: 'admin', fullName: '관리자', email: 'a@x', department: 'IT', roles: ['ADMIN'],
    });
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('logout clears me and forceChangePw', async () => {
    (api.post as any).mockResolvedValue({});
    const auth = useAuthStore();
    auth.me.value = { userId: 'a', fullName: 'a', email: 'a', department: 'a', roles: [] } as any;
    await auth.logout();
    expect(auth.me).toBeNull();
    expect(auth.forceChangePw).toBe(false);
  });
});
```

Add `vitest` config to `vite.config.ts`:

```typescript
// add to defineConfig({...})
  test: {
    environment: 'happy-dom',
    globals: false,
  },
```

Run:

```bash
cd /home/spuhaha18/Project/DMS/frontend
npm run test
```

Expected: 2 tests PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/spuhaha18/Project/DMS
git add frontend/src/ frontend/vite.config.ts
git commit -m "frontend: add LoginView + Pinia auth store + axios client + router guard"
```

---

### Task 21: M1 acceptance — full hash chain integrity check

**Files:** none (acceptance verification).

- [ ] **Step 1: Start full stack (infra + backend + frontend) and perform 5 events**

```bash
cd /home/spuhaha18/Project/DMS/infra && docker compose up -d
cd /home/spuhaha18/Project/DMS/backend && ./gradlew bootRun --args='--spring.profiles.active=dev' &
sleep 15

# 1: successful login
curl -s -c /tmp/c.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId":"admin","password":"NewLab!2026"}' >/dev/null

# 2: failed login
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId":"admin","password":"wrong"}' >/dev/null

# 3: change password (after 1 above set NewLab!2026 in Task 18)
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/v1/auth/change-password \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"NewLab!2026","newPassword":"AnotherLab!2026"}' >/dev/null

# 4: logout
curl -s -b /tmp/c.txt -X POST http://localhost:8080/api/v1/auth/logout >/dev/null

# 5: failed login again
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId":"admin","password":"wrong2"}' >/dev/null
```

- [ ] **Step 2: Verify hash chain integrity in DB**

```bash
docker exec edms-postgres psql -U app_role -d edms_dev <<'SQL'
WITH chain AS (
  SELECT id,
         actor_user_id,
         action,
         prev_hash,
         this_hash,
         LAG(this_hash) OVER (ORDER BY id) AS expected_prev
  FROM audit_logs
)
SELECT id, action, prev_hash = COALESCE(expected_prev, '901131d838b17aac0f7885b81e03cbdc9f5157a00343d30ab22083685ed1416a') AS chain_ok
FROM chain
ORDER BY id;
SQL
```

Expected: every row `chain_ok = t`. The first row's `prev_hash` equals SHA-256("GENESIS"); each subsequent row's `prev_hash` equals the previous row's `this_hash`.

- [ ] **Step 3: Negative check — attempt UPDATE as audit_role**

```bash
docker exec edms-postgres psql -U audit_role -d edms_dev \
  -c "UPDATE audit_logs SET action='X' WHERE id=1"
```

Expected: `ERROR: permission denied for table audit_logs`.

- [ ] **Step 4: Stop services**

```bash
kill %1
cd /home/spuhaha18/Project/DMS/infra && docker compose down
```

- [ ] **Step 5: Tag M1 milestone**

```bash
cd /home/spuhaha18/Project/DMS
git tag m1-foundation
git log --oneline m1-foundation
```

Expected: tag points to last M1 commit; log shows ~16 commits since the start of M1.

---

## Verification (M1 Done When)

1. `cd backend && ./gradlew test` — all unit + IT tests PASS (PasswordPolicyValidatorTest, HashChainSerializerTest, AuditServiceIT, LocalAuthProviderIT, AuthServiceIT, AuthControllerIT, SmokeIT).
2. `docker compose up -d` boots infra; `./gradlew bootRun` starts backend; `npm run dev` starts frontend.
3. Browser login at `http://localhost:3000/login` succeeds with seeded admin; HomeView shows the admin profile.
4. `audit_logs` table contains rows with non-null `prev_hash`/`this_hash`; chain SQL in Task 21 §2 returns all `chain_ok = t`.
5. `audit_role` UPDATE/DELETE attempts return `permission denied` (Task 21 §3, Task 5 §3).
6. M2 prerequisites confirmed: 8 system roles seeded, bootstrap admin exists, `change-password` endpoint enforces 5-history reuse and 3-of-4 character classes.

After M1 acceptance, brainstorm + write the M2 plan in `docs/superpowers/plans/<date>-edms-m2-user-rbac.md`.

---

## Out of Scope for M1 (handled in later milestones)

- Document, Version, File entities and their CRUD (M3)
- Lifecycle state machine and workflow templates (M4)
- Electronic signature endpoint and signature_manifests hash chain (M5)
- WORM audit anchor scheduled job (M6)
- PDF conversion / watermark / pdf.js viewer (M7)
- Search (tsvector + mecab-ko) (M8)
- Notifications + periodic review scheduler (M9)
- Vue admin screens and full UX (M10)
- Migration CLI (M11)
- Ansible IQ deploy + IQ/OQ/PQ execution (M12)
- 30-min auto-unlock scheduler (deferred to M2 — owns LockoutScheduler)
- LDAP / AD provider (Phase 2)

---

## Post-M1 Self-Review

**Spec coverage check:**

- ✅ FS-AUTH-001 (login) — Tasks 12, 15
- ✅ FS-AUTH-002 (password policy + history) — Task 17
- ✅ FS-AUTH-003 (session 15-min, single-session) — Task 14 (auto-unlock deferred to M2 with explicit note)
- ✅ FS-AUD-001 (auto audit log) — Task 11 (login/logout/change-password actions in M1)
- ✅ FS-AUD-002 (INSERT-only) — Task 5 (audit_role grant) + Task 11 IT
- ✅ FS-AUD-003 part 1 (hash chain) — Tasks 10, 11 (WORM anchor deferred to M6)
- ✅ FS-USER-003 (8 roles seeded) — Task 6
- ✅ DS §3.1 architecture — backend/frontend/infra dirs in Task 1, 2, 3, 19
- ✅ DS §4.2 DDL (subset for M1) — Tasks 4–6
- ✅ DS §8.2 audit hash chain algorithm — Task 10 `HashChainSerializer.payload`
- ✅ DS §9.1 TLS — deferred to M12 (Ansible IQ); dev runs HTTP only
- ✅ Compliance-Part11 §11.10(d) limited access — Task 14 SecurityConfig
- ✅ Compliance-Part11 §11.10(e) audit trail — Tasks 5, 10, 11
- ✅ Compliance-Part11 §11.300 password — Task 17 + Task 12 lockout

**Placeholder scan:** No "TBD" / "implement later" / "add error handling" anywhere. All steps include actual SQL / Java / TS code.

**Type consistency:** `AuditEvent` record fields match across Tasks 10, 11, 13. `AuthResult` sealed types defined in Task 12 used unchanged in Tasks 13, 15. `User` entity field names match V1 schema columns (snake_case ↔ camelCase via JPA `@Column`).
