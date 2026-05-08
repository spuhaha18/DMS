# 네트워크 아키텍처 명세
## EDMS — Network Architecture

---

| 항목 | 내용 |
|---|---|
| 문서 번호 | VAL-NET-001 |
| 버전 | 0.1 (초안) |
| 작성일 | 2026-05-08 |
| 상태 | 초안 — 검토 전 |
| 분류 | 기술 명세 — 인프라 보안 |
| 참조 | VAL-DS-001 §10, SOP-DR-001, SOP-BACKUP-001 |

---

## 1. 목적

본 문서는 EDMS가 배포되는 사내 폐쇄망의 네트워크 구성, Zone 경계, 포트 허용 목록, 방화벽 정책을 정의한다. 네트워크 변경 시 SOP-CHANGE-001 변경 통제가 적용된다.

---

## 2. Zone 정의

EDMS는 인터넷 연결이 없는 **완전 폐쇄망(Air-gapped)**으로 운영된다. 4개 Zone으로 분리한다.

| Zone | VLAN ID (예시) | 용도 | 주요 호스트 |
|---|---|---|---|
| **User-LAN** | 100 | 연구소 사용자 PC | 일반 PC, 노트북 |
| **App-Zone** | 200 | 애플리케이션 서버, Nginx | edms-app.lab.internal, edms-nginx.lab.internal |
| **Storage-Zone** | 300 | 데이터베이스, 오브젝트 스토리지 | edms-pg.lab.internal, edms-minio.lab.internal |
| **DR-Zone** | 400 | 재해복구 복제 서버 (별도 사이트) | edms-pg-dr.labdr.internal, edms-minio-dr.labdr.internal |

> **DMZ 없음**: 외부 인터넷 연결이 없으므로 DMZ를 별도 구성하지 않는다.

---

## 3. 네트워크 다이어그램

```
╔══════════════════════════════════════════════════════════════════════════╗
║                     사내망 (Closed Network)                              ║
║                                                                          ║
║  ┌─────────────────────────────────────────────────────────┐             ║
║  │  User-LAN (VLAN 100)                                     │             ║
║  │  사용자 PC / 노트북                                       │             ║
║  │  10.10.100.0/24                                          │             ║
║  └──────────────────────────┬────────────────────────────┘             ║
║                             │ HTTPS:443 → edms-nginx                    ║
║                             ▼                                            ║
║  ┌─────────────────────────────────────────────────────────┐             ║
║  │  App-Zone (VLAN 200)                          10.10.200.0/24          ║
║  │                                                          │             ║
║  │  ┌───────────────────────────┐                          │             ║
║  │  │ Nginx 1.26                │                          │             ║
║  │  │ edms-nginx.lab.internal   │  TLS 종단 (사내 CA)      │             ║
║  │  │ 10.10.200.10              │  → 정적 파일 서빙 (SPA)  │             ║
║  │  │                           │  → /api → Spring Boot    │             ║
║  │  └────────────┬──────────────┘                          │             ║
║  │               │ HTTP:8080                                │             ║
║  │               ▼                                          │             ║
║  │  ┌───────────────────────────┐                          │             ║
║  │  │ Spring Boot 3 API         │                          │             ║
║  │  │ edms-app.lab.internal     │                          │             ║
║  │  │ 10.10.200.20:8080         │                          │             ║
║  │  └──┬──────────┬─────────┬───┘                          │             ║
║  │     │          │         │                              │             ║
║  └─────┼──────────┼─────────┼──────────────────────────────┘             ║
║        │          │         │                                            ║
║        │ DB:5432  │ S3:9000 │ LibreOffice call                           ║
║        ▼          ▼         ▼                                            ║
║  ┌─────────────────────────────────────────────────────────┐             ║
║  │  Storage-Zone (VLAN 300)                  10.10.300.0/24 │             ║
║  │                                                          │             ║
║  │  ┌─────────────────────┐  ┌─────────────────────┐       │             ║
║  │  │ PostgreSQL 16        │  │ MinIO               │       │             ║
║  │  │ edms-pg.lab.internal │  │ edms-minio          │       │             ║
║  │  │ 10.10.300.10:5432   │  │ .lab.internal       │       │             ║
║  │  │                     │  │ 10.10.300.20:9000   │       │             ║
║  │  └──────────┬──────────┘  └─────────────────────┘       │             ║
║  │             │ Logical Replication :5432                  │             ║
║  └─────────────┼──────────────────────────────────────────┘             ║
║                │                                                         ║
║                │ WAN Link (사내 전용선, 암호화 필수)                       ║
║                ▼                                                         ║
║  ┌─────────────────────────────────────────────────────────┐             ║
║  │  DR-Zone / 별도 사이트 (VLAN 400)        10.20.0.0/24   │             ║
║  │                                                          │             ║
║  │  ┌─────────────────────┐  ┌─────────────────────┐       │             ║
║  │  │ PG Replica (DR)     │  │ MinIO DR Mirror      │       │             ║
║  │  │ edms-pg-dr          │  │ edms-minio-dr        │       │             ║
║  │  │ .labdr.internal     │  │ .labdr.internal      │       │             ║
║  │  └─────────────────────┘  └─────────────────────┘       │             ║
║  └─────────────────────────────────────────────────────────┘             ║
║                                                                          ║
║  공유 인프라 (All Zones 접근):                                             ║
║  ┌──────────────────────┐  ┌────────────────┐  ┌────────────────────┐   ║
║  │ NTP ntp.internal.lab │  │ PKI CA         │  │ SMTP Relay         │   ║
║  │ 10.10.1.10:123       │  │ ca.lab.internal│  │ smtp.lab.internal  │   ║
║  └──────────────────────┘  └────────────────┘  │ 10.10.1.30:25      │   ║
║                             10.10.1.20:443      └────────────────────┘   ║
║                                                                          ║
║  패키지 미러 (App-Zone 접근):                                              ║
║  ┌──────────────────────────────────────┐                                ║
║  │ Nexus Repository Manager             │  Maven/npm/Docker 미러         ║
║  │ nexus.lab.internal / 10.10.1.40:8081 │                                ║
║  └──────────────────────────────────────┘                                ║
╚══════════════════════════════════════════════════════════════════════════╝
```

---

## 4. 포트 매트릭스

### 4.1 인바운드 (사용자 → EDMS)

| Source Zone | Destination Host | Port | Protocol | 용도 |
|---|---|---|---|---|
| User-LAN | edms-nginx | 443 | HTTPS/TLS | 웹 접근 (SPA + API) |
| User-LAN | edms-nginx | 80 | HTTP | → 443 리다이렉트만 허용 |

### 4.2 내부 통신 (App-Zone → Storage-Zone)

| Source | Destination | Port | Protocol | 용도 |
|---|---|---|---|---|
| edms-app | edms-pg | 5432 | TCP | PostgreSQL 연결 (app_role, audit_role, readonly_role) |
| edms-app | edms-minio | 9000 | HTTP (사내망) | MinIO S3 API (원본/PDF 버킷) |
| edms-app | edms-minio | 9001 | HTTP (사내망) | MinIO Console (Admin만) |

> MinIO 9000/9001은 Storage-Zone 내 비암호화 통신. App-Zone 방화벽이 비인가 호스트 접근 차단.

### 4.3 리플리케이션 (Primary → DR)

| Source | Destination | Port | Protocol | 용도 |
|---|---|---|---|---|
| edms-pg (Primary) | edms-pg-dr | 5432 | TCP + TLS | PostgreSQL Logical Replication |
| edms-minio (Primary) | edms-minio-dr | 9000 | HTTP over VPN | MinIO Site Replication |

> Primary → DR 전용선은 사내 VPN 터널 또는 MPLS 전용선. 평문 전송 금지.

### 4.4 공유 인프라 접근 (All Zones)

| Source Zone | Destination | Port | Protocol | 용도 |
|---|---|---|---|---|
| All | ntp.internal.lab | 123 | UDP | NTP 시간 동기화 |
| edms-app | ca.lab.internal | 443 | HTTPS | CRL/OCSP (인증서 검증) |
| edms-app | smtp.lab.internal | 25 | SMTP | 이메일 발송 |
| App-Zone | nexus.lab.internal | 8081 | HTTPS | Maven/npm 패키지 다운로드 (CI/CD) |

---

## 5. 방화벽 정책

### 5.1 기본 정책
- **Default: DENY ALL** — 명시적으로 허용된 트래픽만 통과
- Zone 간 이동은 방화벽 룰에 의해서만 가능

### 5.2 인바운드 허용 룰 (User-LAN → App-Zone)

| 순번 | Source | Destination | Port | Action | 비고 |
|---|---|---|---|---|---|
| 1 | 10.10.100.0/24 | 10.10.200.10 | 443/TCP | ALLOW | HTTPS (웹) |
| 2 | 10.10.100.0/24 | 10.10.200.10 | 80/TCP | ALLOW (→Redirect) | HTTP→HTTPS |
| 999 | ANY | ANY | ANY | DENY | Default |

### 5.3 내부 허용 룰 (App-Zone → Storage-Zone)

| 순번 | Source | Destination | Port | Action | 비고 |
|---|---|---|---|---|---|
| 1 | 10.10.200.20 | 10.10.300.10 | 5432/TCP | ALLOW | App → PG |
| 2 | 10.10.200.20 | 10.10.300.20 | 9000/TCP | ALLOW | App → MinIO API |
| 3 | 10.10.1.40 | 10.10.200.20 | 8080/TCP | ALLOW | Nexus 빌드 서버 → App (CI/CD deploy) |
| 999 | ANY | ANY | ANY | DENY | Default |

### 5.4 관리자 접근 (제한)

| 순번 | Source | Destination | Port | Action | 비고 |
|---|---|---|---|---|---|
| 1 | 관리자 PC (지정 IP) | edms-pg | 5432/TCP | ALLOW | DBA 직접 접근 (감사 기록 필수) |
| 2 | 관리자 PC (지정 IP) | edms-minio | 9001/TCP | ALLOW | MinIO Console |
| 3 | edms-app | edms-pg | 5432/TCP | ALLOW | 앱 연결 |
| 999 | ANY | ANY | ANY | DENY | Default |

### 5.5 Egress 차단 (인터넷 아웃바운드 전면 차단)

| 순번 | Source | Destination | Action | 비고 |
|---|---|---|---|---|
| 1 | Any (10.10.0.0/16) | 인터넷 (0.0.0.0/0) | DENY | 전면 차단 |
| 2 | Any (10.10.0.0/16) | 사내 허용 IP 목록 | ALLOW | NTP, CA, SMTP, Nexus 등 명시적 허용 |

> **폐쇄망 보장**: 외부 패키지 다운로드, 외부 API 호출 등 모든 인터넷 outbound 차단. 운영 배포 시 Nexus 미러에서만 패키지 취득.

---

## 6. TLS 구성

### 6.1 인증서

| 용도 | 발급처 | 갱신 주기 | 비고 |
|---|---|---|---|
| Nginx HTTPS | 사내 PKI CA (ca.lab.internal) | 1년 | CN=edms.lab.internal |
| PG Logical Replication | 사내 PKI CA | 1년 | 서버+클라이언트 상호 인증 |
| MinIO Site Replication | 사내 PKI CA | 1년 | 터널 암호화 |

### 6.2 Nginx TLS 설정 (핵심)

```nginx
ssl_protocols       TLSv1.2 TLSv1.3;
ssl_ciphers         ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:...;
ssl_prefer_server_ciphers on;
ssl_session_cache   shared:SSL:10m;
ssl_session_timeout 1d;

# HSTS
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

# 인증서
ssl_certificate     /etc/nginx/certs/edms.crt;
ssl_certificate_key /etc/nginx/certs/edms.key;
ssl_trusted_certificate /etc/nginx/certs/lab-ca-chain.crt;
```

---

## 7. NTP 동기화

모든 EDMS 관련 서버(App, PG Primary, PG DR, MinIO Primary, MinIO DR)는 사내 NTP 서버(`ntp.internal.lab`)에서 동기화한다.

```
/etc/chrony.conf (모든 서버 공통):
  server ntp.internal.lab iburst
  makestep 1.0 3
  rtcsync
  logdir /var/log/chrony
```

**서명 타임스탬프 보장**: Spring Boot API 서버는 `Instant.now()`(서버 시각)를 사용. 클라이언트 제공 시각 무시.

**드리프트 허용치**: ±1초. chrony alert 설정 — 드리프트 >1s 시 syslog 경고, >10s 시 API 서버 서명 거부 (시스템 시계 신뢰 불가).

---

## 8. 변경 관리

방화벽 룰, Zone 구성, 포트 허용 목록 변경은 반드시 **SOP-CHANGE-001**에 따라 변경통제를 거쳐야 한다. 방화벽 룰 변경 이력은 망 관리 시스템 또는 방화벽 장비 로그로 보관한다.

---

## 9. 변경 이력

| 버전 | 날짜 | 변경 내용 | 작성자 |
|---|---|---|---|
| 0.1 | 2026-05-08 | 최초 작성 | TBD |

---

*본 문서는 초안(Draft) 상태이며 인프라 팀 및 보안팀 검토 전까지 운영 기준으로 사용할 수 없다.*
