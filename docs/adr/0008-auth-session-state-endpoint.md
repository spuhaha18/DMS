# ADR 0008 — `/auth/session-state` endpoint 도입

| 항목 | 내용 |
|------|------|
| 날짜 | 2026-05-13 |
| 상태 | Accepted |
| 결정자 | IT/QA |

## 맥락

21 CFR Part 11 §11.200(a)는 전자서명 시스템이 사용자에게 비밀번호 변경을 요구하도록 규정한다. 현재 DMS는 첫 로그인 후 비밀번호 변경 완료 여부를 클라이언트가 사전에 알 수 없어서 결재 화면에서 서명 시도 시 422 오류를 받고서야 알게 된다.

M8 SignatureDialog 도입으로 이 UX 문제가 더 중요해졌다: 사용자가 PDF를 읽고 서명 버튼을 눌렀는데 비밀번호 변경 강제 422가 반환되면 흐름이 끊어진다.

### 옵션 검토

| 옵션 | 설명 | 거부 사유 |
|------|------|---------|
| A | 사전 조회 endpoint | **채택** |
| B | 서명 시도 후 422 받고 재시도 | UX 열악, Part 11 §11.200(a) 정신에 반함 |
| C | 항상 ID+PW 재입력 강제 | 이미 변경 완료한 사용자에게 과도한 부담 |

## 결정

**`GET /api/v1/auth/session-state` endpoint 신규 도입 (옵션 A).**

### 응답 스키마

```json
{
  "userId": 42,
  "username": "jsmith",
  "firstSignRequired": true
}
```

`firstSignRequired: true` → 클라이언트가 SignatureDialog 진입 전 비밀번호 변경 화면으로 redirect.

### 구현

- `AuthSessionController.GET /auth/session-state` → `SessionFirstSignTracker.isFirstSignRequired(userId)`
- `SessionFirstSignTracker`에 `boolean isFirstSignRequired(Long userId)` read-only 메서드 추가 (M6 기존 코드에 추가, 회귀 테스트 그린 유지)
- 응답 DTO: `SessionStateDto(userId, username, firstSignRequired)`

### Pinia 캐시 전략

- 앱 초기화 시 `AuthStore.fetchSessionState()` 1회 호출 → `auth.firstSignRequired` 저장
- 비밀번호 변경 완료 후 `AuthStore.markFirstSignDone()` → `firstSignRequired = false`
- **race 방어**: Pinia 캐시가 stale하더라도 백엔드 `SessionFirstSignTracker`가 2차 방어. 서명 요청 시 `firstSignRequired = true`면 API가 `403 FIRST_SIGN_REQUIRED` 반환.

### Part 11 준수

- endpoint는 인증된 세션만 접근 가능 (`@PreAuthorize("isAuthenticated()")`)
- 응답에 `firstSignRequired` 외 민감 정보 없음
- 서버 시각 사용 (클라이언트 시각 신뢰 금지)

## 결과

- `AuthSessionController.java` 신규
- `SessionStateDto.java` 신규
- `SessionFirstSignTracker.isFirstSignRequired(Long userId)` 메서드 추가
- `frontend/src/api/authSession.ts` + Pinia `auth.ts` 갱신
- `AuthSessionControllerIT.java` 통합 테스트
