# SOP-CICD-001
# EDMS 소스 관리 및 CI/CD 절차 (Source Control & CI/CD SOP)

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | SOP-CICD-001 |
| 버전 | 1.0 |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 승인 전 |
| 분류 | GxP SOP — 컴퓨터화 시스템 변경 관리 |
| 작성자 | TBD |
| 검토자 | TBD (개발 리드) |
| 승인자 | TBD (QA Manager) |
| 다음 정기검토일 | 승인일 + 2년 |

---

## 1. 목적

본 SOP는 EDMS의 소스코드 관리, 코드 검토, 빌드 파이프라인, 배포 절차를 정의한다. 모든 운영 배포는 본 SOP와 SOP-CHANGE-001 변경 통제를 거쳐야 하며, 검증 환경(IQ/OQ) 재현 가능성을 보장한다. Part 11 §11.10(k) — 운영 시스템 문서 통제 요건을 충족한다.

---

## 2. 범위

본 SOP는 EDMS 백엔드(Spring Boot), 프론트엔드(Vue 3), 인프라 설정(Ansible/Docker), 데이터베이스 마이그레이션(Flyway)을 포함한 모든 EDMS 소스 산출물에 적용된다. 제3자 라이브러리 자체는 범위 외.

---

## 3. 용어 정의

| 용어 | 정의 |
|---|---|
| 브랜치 | Git 개발 라인 단위 (main, develop, feature/*, hotfix/*) |
| PR (Pull Request) | 코드 변경을 기준 브랜치에 병합하기 위한 검토 요청 |
| 파이프라인 | 코드 변경 후 자동으로 실행되는 빌드·테스트·검사 단계 |
| Artifact | 빌드 결과물 (JAR, Docker 이미지, dist 번들). SHA-256 해시로 식별 |
| IQ 환경 | 설치적격성 검증 환경. IQ baseline = 검증된 Artifact 버전 |

---

## 4. 책임

| 역할 | 책임 |
|---|---|
| 개발자 | 기능 개발, PR 작성, 코드 리뷰 참여 |
| 개발 리드 | PR 최종 승인, Critical 기능 리뷰 |
| QA | Critical 기능 변경 시 PR 리뷰 참여, 배포 후 smoke test 확인 |
| 시스템 관리자 | 검증/운영 환경 배포 실행, 배포 기록 |
| CISO/보안 | SAST 결과 주요 취약점 확인 |

---

## 5. 절차

### 5.1 소스 관리 — Git 브랜치 전략

```
main ──────────────────────────────── (운영 배포 기준, protected)
  └── release/1.0 ─────────────────── (릴리즈 후보 브랜치)
        └── develop ──────────────── (통합 개발 브랜치)
              ├── feature/auth-mfa
              ├── feature/search-improvement
              └── hotfix/OQ-SIG-003-fix (긴급 수정)
```

**보호 규칙 (Branch Protection)**:
- `main` 브랜치: 직접 push 금지. PR 필수. CI 파이프라인 통과 필수. 2인 승인 필수.
- `develop` 브랜치: 직접 push 금지. PR 필수. 1인 승인 필수.
- `feature/*`: 개발자 자유롭게 사용.
- `hotfix/*`: 긴급 수정 전용. SOP-CHANGE-001 Emergency 경로 적용.

**커밋 컨벤션**:
```
<type>(<scope>): <subject>

type: feat | fix | test | refactor | chore | docs | sec
scope: auth | doc | lifecycle | sig | audit | search | infra
subject: 50자 이내, 동사 원형 시작 (영문)

예: feat(sig): add first-sign ID+PW enforcement (Part 11 §11.200)
    fix(audit): prevent hash chain break on concurrent insert
```

태그: 릴리즈는 `v1.0.0` 형식으로 `main` 브랜치에 tag.

---

### 5.2 코드 리뷰 절차

#### 5.2.1 PR 작성 기준

PR 작성자는 다음을 포함한다:
- 변경 목적 및 관련 요구사항 (URS/FS ID 또는 OQ 케이스 ID)
- 테스트 결과 (단위테스트 통과, 통합테스트 통과)
- 위험 수준 분류: `risk:low / risk:medium / risk:high`
- Critical 기능 여부: `critical:yes / critical:no` (Part 11 직접 관련 기능)

#### 5.2.2 리뷰어 지정 기준

| 변경 유형 | 최소 리뷰어 | QA 리뷰어 필요 |
|---|---|---|
| 일반 기능 (비 Critical) | 1인 (동료 개발자) | No |
| Major 기능 (UI, 워크플로) | 2인 (개발자 + 리드) | No |
| **Critical 기능** (인증, 전자서명, 감사로그, 권한) | 2인 (개발자 + 리드) | **Yes** |
| 보안 관련 (인증, 세션, 암호화) | 2인 + CISO 확인 | **Yes** |
| DB 스키마 마이그레이션 | 2인 + DBA 확인 | No |

> **Critical 기능 예시**: LifecycleStateMachine, SignatureManifest 해시체인, AuditService INSERT-only 보장, PermissionEvaluator, 비밀번호 검증, 세션 관리.

#### 5.2.3 리뷰 체크리스트

- [ ] 기능이 FS/URS 요구사항을 충족하는가
- [ ] 보안 취약점이 없는가 (SQL Injection, XSS, CSRF, 권한 우회)
- [ ] 로직 오류·경계 조건 누락이 없는가
- [ ] 감사로그가 모든 관련 이벤트에서 기록되는가
- [ ] 단위·통합 테스트가 충분한가 (Critical 기능은 90% 이상)
- [ ] 시크릿 평문 포함 여부 없는가

---

### 5.3 빌드 파이프라인

모든 PR 및 `develop`/`main` 브랜치 push는 다음 파이프라인을 자동 실행한다:

```
Step 1: Lint & Format
  Backend:  ./gradlew checkstyleMain spotbugsMain
  Frontend: npm run lint (ESLint + Prettier)
  결과: 오류 0건이어야 병합 가능

Step 2: Unit Tests (+ Testcontainers)
  Backend:  ./gradlew test
    - PostgreSQL 16 Testcontainer (실제 DB 사용)
    - MinIO mock 또는 Testcontainer
    - 핵심: LifecycleStateMachine, HashChainService, PermissionEvaluator
  Frontend: npm run test (Vitest)
  결과: 전체 통과

Step 3: Security Scan (SAST)
  ./gradlew dependencyCheckAnalyze  (OWASP Dependency-Check)
  SpotBugs security-specific rules
  Frontend: npm audit --audit-level=high
  결과: CVSS 9.0+ 취약점 0건이어야 배포 가능

Step 4: Build Artifact
  Backend:  ./gradlew bootJar → edms-api-{version}.jar
  Frontend: npm run build → dist/
  Docker:   docker build -t edms:{version} .

Step 5: Artifact Integrity
  sha256sum edms-api-{version}.jar > edms-api-{version}.jar.sha256
  Artifact + 해시를 Nexus Repository에 보관
  (배포 시 해시 재검증으로 무결성 확인)
```

---

### 5.4 검증(IQ/OQ) 환경 배포

IQ/OQ 환경은 **검증된 Artifact(IQ baseline)** 을 기준으로 운영된다. Artifact 변경 시 다음을 수행한다.

1. 신규 Artifact의 SHA-256 해시를 IQ protocol 기록에 업데이트한다 (IQ-HW-001 해당 항목).
2. **IQ 재실행 필요 여부 판단**: 핵심 인프라(PG, MinIO, LibreOffice, OS) 변경 → IQ 전체 재실행. App code만 변경 → IQ 생략 가능, OQ 재실행 여부는 Impact Assessment로 결정.
3. 변경 통제: SOP-CHANGE-001 Minor 경로 처리 (검증 환경은 운영 전 단계이므로 Pre-approval 불필요, 사후 보고).
4. 배포 절차:
   ```bash
   # 사전: 현재 IQ env 스냅샷 백업
   pg_dump edms_iq > backup/iq_$(date +%Y%m%d).sql
   
   # Artifact 배포
   ansible-playbook deploy.yml -e "env=iq version=1.0.3"
   
   # 해시 검증
   sha256sum -c edms-api-1.0.3.jar.sha256
   
   # 서비스 재시작
   systemctl restart edms
   ```
5. 배포 결과를 시스템 관리자가 배포 기록서(부록 A)에 서명한다.

---

### 5.5 운영(Production) 배포

**운영 배포는 반드시 SOP-CHANGE-001 변경 통제를 완료한 후 실행한다.**

#### 5.5.1 사전 요건

- [ ] Change Request (CR) 승인 완료
- [ ] 검증 환경(IQ/OQ)에서 해당 Artifact 테스트 완료
- [ ] 배포 직전 DB 전체 백업 완료 (SOP-BACKUP-001 §5.1 참조)
- [ ] Rollback 계획 수립 완료 (부록 B)

#### 5.5.2 배포 절차

```
1. 배포 시작 전 공지: 시스템 점검 알림 (사용자에게 30분 전 공지)

2. 사전 백업:
   pg_basebackup -D /backup/prod_$(date +%Y%m%d_%H%M) -Ft -z -P
   mc mirror edms-documents-original backup/minio/

3. Artifact 무결성 검증:
   sha256sum -c edms-api-{version}.jar.sha256  # PASS 확인

4. 배포 실행:
   ansible-playbook deploy.yml -e "env=prod version={version}"

5. 서비스 재시작:
   systemctl restart edms
   systemctl restart nginx  # 필요 시

6. Smoke Test (배포 직후 5분):
   - 로그인 동작 확인
   - 문서 목록 조회 확인
   - PDF 뷰어 동작 확인
   - 알림 스트림 연결 확인

7. 결과 기록:
   audit_logs에 배포 이벤트 기록:
   action=SYSTEM_DEPLOYED, entity_type=SYSTEM, 
   after_value={version, sha256, deployed_by, CR_id}
```

#### 5.5.3 배포 실패 시 롤백

```bash
# 이전 버전으로 즉시 롤백
ansible-playbook deploy.yml -e "env=prod version={이전_버전}"

# DB 롤백이 필요한 경우 (Flyway 마이그레이션 문제):
# SOP-BACKUP-001 §5.2 PITR 절차 적용
# 주의: audit_logs INSERT-only 특성상 DB 복원 후 감사로그 연속성 검증 필요
```

---

### 5.6 감사 기록

모든 배포 이벤트(IQ/OQ/운영)는 `audit_logs` 테이블에 아래 형식으로 기록된다:

| 필드 | 값 |
|---|---|
| action | `SYSTEM_DEPLOYED` |
| entity_type | `SYSTEM` |
| entity_id | `EDMS` |
| after_value | `{"version":"1.0.3","sha256":"...","env":"prod","change_request_id":"CR-2026-003"}` |
| reason | CR 번호 및 배포 목적 |
| actor_user_id | 배포 실행자 ID |

> 자동 배포 시에도 서비스 계정 ID(`system-deploy`)를 사용하여 로그를 기록한다.

---

## 6. 양식

### 부록 A — 배포 기록서

```
배포 일시:          환경: ☐ DEV  ☐ IQ/OQ  ☐ PROD
배포 버전:          이전 버전:
Artifact SHA-256:
Change Request ID (운영만):

[사전 확인]
☐ Artifact 무결성 검증 완료
☐ 사전 백업 완료 (운영만)
☐ QA 승인 확인 (운영만)

[배포 결과]
☐ 정상 배포  ☐ 롤백 수행
문제점/특이사항:

배포자:                서명:                 날짜:
확인자:                서명:                 날짜:
```

### 부록 B — 롤백 계획 템플릿

```
배포 버전: {new_version}
롤백 대상 버전: {prev_version}
DB 마이그레이션 포함: ☐ Yes  ☐ No
롤백 예상 소요 시간:
롤백 실행 기준 (Trigger):
  - 로그인 불가
  - PDF 뷰어 오작동
  - 서명 오류 발생
  - 기타:
```

### 부록 C — Branch Protection Rules (참고)

```yaml
# GitHub/GitLab 설정 예시 (main 브랜치)
required_status_checks:
  - ci/lint
  - ci/test
  - ci/security-scan
required_pull_request_reviews:
  required_approving_review_count: 2
  dismiss_stale_reviews: true
  require_code_owner_reviews: true  # CODEOWNERS에 QA 지정
enforce_admins: true
restrict_pushes: true
```

---

## 7. 참조 문서

| 문서 번호 | 제목 |
|---|---|
| SOP-CHANGE-001 | EDMS 변경 통제 절차 |
| SOP-BACKUP-001 | 백업 및 복구 절차 |
| SOP-DR-001 | 재해복구 절차 |
| VAL-DS-001 | Design Specification (§3.2 패키지 구조, §8 핵심 알고리즘) |
| VAL-IQ-001 | IQ Protocol (§5 설치 체크리스트) |
| VAL-SEC-001 | 시크릿 관리 정책 |

---

## 8. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 1.0 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 QA Manager 승인 전까지 운영 절차로 사용할 수 없다.*
