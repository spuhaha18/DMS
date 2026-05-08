# 시크릿 관리 정책
## EDMS — Secrets Management Policy

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-SEC-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| 분류 | 기술 명세 — 보안 정책 |
| 참조 | VAL-DS-001 §9, SOP-INCIDENT-001, SOP-CICD-001 |

---

## 1. 목적

본 정책은 EDMS 운영에 필요한 비밀 정보(credential, 암호화 키, 인증서 등)의 보관·접근·회전·폐기 절차를 정의한다. 시크릿 유출은 데이터 무결성 침해 및 감사 추적 위조로 이어질 수 있으므로, 이를 강력히 통제한다.

---

## 2. 보호 대상 인벤토리

| 시크릿 | 용도 | 민감도 | 저장 위치 |
|---|---|---|---|
| `DB_APP_PASSWORD` | PostgreSQL app_role 연결 비밀번호 | 최고 | Vault / 환경변수 |
| `DB_AUDIT_PASSWORD` | PostgreSQL audit_role 연결 비밀번호 | 최고 | Vault / 환경변수 |
| `DB_READONLY_PASSWORD` | PostgreSQL readonly_role 연결 비밀번호 | 높음 | Vault / 환경변수 |
| `DB_SUPERUSER_PASSWORD` | PostgreSQL 슈퍼유저 (설치·마이그레이션 전용) | 최고 | Vault (운영팀 접근) |
| `MINIO_ROOT_USER` | MinIO Root 계정 ID | 높음 | Vault / 환경변수 |
| `MINIO_ROOT_PASSWORD` | MinIO Root 계정 비밀번호 | 최고 | Vault / 환경변수 |
| `MINIO_ACCESS_KEY` | MinIO 애플리케이션 전용 액세스 키 | 최고 | Vault / 환경변수 |
| `MINIO_SECRET_KEY` | MinIO 애플리케이션 전용 시크릿 키 | 최고 | Vault / 환경변수 |
| `SMTP_PASSWORD` | SMTP Relay 인증 비밀번호 | 높음 | Vault / 환경변수 |
| `TLS_PRIVATE_KEY` | Nginx TLS 개인 키 | 최고 | Nginx 서버 파일 시스템 (restrictive 권한) |
| `SESSION_SECRET` | Spring Session 암호화 키 (선택) | 높음 | Vault / 환경변수 |
| `ADMIN_INITIAL_PASSWORD` | 시스템 초기 Admin 계정 비밀번호 | 최고 | 생성 즉시 폐기, 본인에게만 전달 |

> **NTP shared secret**, **mecab-ko 라이선스 키** 등 정기 회전이 필요한 기타 시크릿도 동일한 절차를 따른다.

---

## 3. 저장 정책

### 3.1 원칙

1. **평문 금지**: 어떤 시크릿도 소스코드, Git 저장소, 컨테이너 이미지, 로그 파일에 평문으로 포함되어서는 안 된다.
2. **단일 위치 보관**: 시크릿은 Vault(또는 sealed-secrets) 또는 서버 환경변수에만 존재한다. 설정 파일, README, 위키에 보관 금지.
3. **최소 복사 원칙**: 시크릿 복사본 수를 최소화한다. 개발 환경 시크릿과 운영/검증 환경 시크릿은 절대로 공유하지 않는다.

### 3.2 환경별 저장 방식

| 환경 | 저장 방식 | 접근 주체 |
|---|---|---|
| 개발(dev) | `.env` 파일 (git ignore 설정 필수) | 개발자 개인 PC |
| 검증(IQ/OQ) | 검증 서버 환경변수 또는 sealed-config | 검증 담당자 + Admin |
| 운영(prod) | **HashiCorp Vault** (강력 권장) 또는 서버 환경변수 | 운영 Admin 2인 이상 |

#### HashiCorp Vault 설정 (운영 권장)

```hcl
# 정책 예시: edms-app 서비스 계정용
path "secret/data/edms/*" {
  capabilities = ["read"]
}
```

Spring Boot 에서 Vault 연동:
```yaml
# application.yml (환경변수에서 Vault 주소만 참조)
spring:
  cloud:
    vault:
      uri: ${VAULT_ADDR}
      token: ${VAULT_TOKEN}   # Kubernetes 환경은 ServiceAccount로 대체
      kv:
        enabled: true
        backend: secret
        default-context: edms
```

#### 환경변수 방식 (Vault 미사용 시)

```bash
# /etc/systemd/system/edms.service
[Service]
EnvironmentFile=/etc/edms/secrets.env   # 600 권한, root 소유
ExecStart=/opt/edms/app.jar
```

```bash
# /etc/edms/secrets.env (600 권한, root 소유, git 추적 없음)
DB_APP_PASSWORD=<value>
MINIO_ACCESS_KEY=<value>
...
```

---

## 4. 접근 통제

### 4.1 인원 제한

| 시크릿 유형 | 접근 가능 인원 |
|---|---|
| 운영 DB 비밀번호 | 운영 Admin (2인 이하), DBA (필요 시 임시) |
| MinIO Root 자격증명 | 운영 Admin (2인 이하) |
| TLS 개인 키 | 운영 Admin (Nginx 서버 관리자) |
| 개발 시크릿 | 개발자 (운영 시크릿과 완전 분리) |

### 4.2 2인 승인 원칙

운영 환경 시크릿 생성·변경·회전은 2인 이상이 참여한다. 단독 변경은 금지한다.

### 4.3 감사 기록

- Vault를 사용하는 경우: Vault audit log가 자동 기록됨 (모든 read/write)
- 환경변수 방식을 사용하는 경우: 변경 일시·변경자·사유를 운영 일지에 수동 기록
- **시크릿 접근 자체(Vault read)는 audit_logs에 기록하지 않는다** — 시크릿 내용이 로그에 유입되는 것을 방지

---

## 5. 회전 주기 및 절차

| 시크릿 | 정기 회전 주기 | 만료 전 알림 |
|---|---|---|
| DB 비밀번호 (app/audit/readonly) | 90일 | 30/14/7일 전 alert |
| MinIO 액세스키/시크릿키 | 90일 | 30/14/7일 전 alert |
| SMTP 비밀번호 | 90일 | 30/14/7일 전 alert |
| TLS 개인 키 / 인증서 | 365일 (사내 CA 발급 주기) | 30/14/7일 전 alert |
| Session secret | 180일 | 30일 전 alert |

### 5.1 회전 절차 (무중단)

1. Vault에서 새 시크릿 생성 (기존 시크릿과 병렬 유지)
2. 애플리케이션 설정 업데이트 → SOP-CHANGE-001 minor change 처리
3. 배포 (rolling restart, 무중단)
4. DB/MinIO에서 기존 자격증명 폐기
5. Vault에서 기존 시크릿 삭제
6. 운영 일지에 회전 완료 기록

> **DB 비밀번호 회전 SQL 예시**:
> ```sql
> -- 새 비밀번호 생성
> ALTER USER app_role PASSWORD 'NewP@ss!';
> -- 애플리케이션 재시작 후 기존 연결 종료 확인
> SELECT pg_terminate_backend(pid) FROM pg_stat_activity
>   WHERE usename='app_role' AND state='idle';
> ```

---

## 6. 시크릿 노출 시 대응

시크릿이 노출되었거나 노출이 의심되는 경우 즉시 다음을 수행한다.

### 6.1 즉시 조치 (1시간 이내)

1. **SOP-INCIDENT-001 발동** (보안사고 Critical 분류)
2. **즉시 회전**: 노출된 시크릿을 즉시 새 값으로 교체 (5.1 절차에서 "무중단" 생략 가능)
3. **접근 차단**: 노출 경로가 된 시스템 또는 계정 즉시 격리
4. **서비스 영향 평가**: 시크릿 노출 기간 동안 비인가 접근이 있었는지 `audit_logs` 조회

### 6.2 사후 조사 (48시간 이내)

1. 노출 경위 조사: git history 스캔, 접근 로그 분석
2. 영향 범위 확정: 노출 기간, 해당 시크릿으로 접근 가능한 시스템 전체
3. 감사추적 무결성 재검증: `audit_logs` 해시체인 검증 (SOP-AUDIT-TRAIL-001 §5 참조)
4. 관리자에게 24시간 내 보고 (SOP-INCIDENT-001 §5.3 기한 준수)

### 6.3 재발 방지

1. 노출 경로 원인 제거 (코드에 평문 포함된 경우 해당 커밋 history 정리 + 강제 rotation)
2. CI/CD 파이프라인에 시크릿 스캔 추가 (git-secrets, trufflesecurity/trufflehog)

---

## 7. 개발자 수칙

1. `.env` 파일은 반드시 `.gitignore`에 포함한다.
2. 소스코드 커밋 전 `git diff`로 시크릿 포함 여부 확인한다.
3. 운영/검증 환경 시크릿은 절대로 개발자 PC에 저장하지 않는다.
4. 임시 비밀번호, API 키를 Slack/이메일에 전송하지 않는다.
5. 화면 공유 시 터미널에 환경변수 출력 금지 (`echo $DB_PASSWORD` 등).

---

## 8. CI/CD 파이프라인 통합

SOP-CICD-001에 따라 CI 파이프라인은 다음을 수행한다.

1. **Pre-commit hook**: `git-secrets` 또는 `detect-secrets`로 평문 시크릿 검출 시 push 차단
2. **Pipeline secret injection**: CI 서버(Jenkins/GitLab CI 등)가 Vault에서 빌드 시 필요한 시크릿만 주입 (환경변수, 파일 마운트). 빌드 아티팩트에 포함되지 않음
3. **Image scan**: 빌드된 Docker 이미지를 trivy로 스캔하여 하드코딩 시크릿 검출

---

## 9. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 보안팀 검토 및 QA Manager 승인 전까지 운영 기준으로 사용할 수 없다.*
