<script setup lang="ts">
const auditRows = [
  {
    timestamp: '2023-10-24T14:32:01.442Z',
    actor: '사라 젠킨스 박사',
    role: '책임 연구자',
    action: '메타데이터 변경',
    resource: 'DOC-VAL-2023-091',
    origin: '10.42.7.18',
    selected: true,
  },
  {
    timestamp: '2023-10-24T14:31:55.120Z',
    actor: '시스템 관리자',
    role: '시스템 운영',
    action: '문서 열람',
    resource: 'DOC-QA-2023-118',
    origin: '10.42.7.21',
    selected: false,
  },
  {
    timestamp: '2023-10-24T14:30:12.883Z',
    actor: 'QA 책임자',
    role: 'QA 관리자',
    action: '전자서명',
    resource: 'DOC-SOP-2023-044',
    origin: '10.42.7.9',
    selected: false,
  },
  {
    timestamp: '2023-10-24T14:28:45.002Z',
    actor: '마크 톰슨',
    role: '사용자',
    action: '인증 성공',
    resource: 'SESSION-8A91',
    origin: 'VPN / 서울',
    selected: false,
  },
  {
    timestamp: '2023-10-24T14:25:01.114Z',
    actor: '사라 젠킨스 박사',
    role: '책임 연구자',
    action: '객체 잠금 해제',
    resource: 'DOC-VAL-2023-091',
    origin: '10.42.7.18',
    selected: false,
  },
];

const metrics = [
  { label: '지연된 검토', value: '14', note: '기한 초과 항목' },
  { label: '대기 중인 서명', value: '08', note: '책임자 승인 대기' },
  { label: '최근 30일 발효 문서', value: '31', note: '관리 대상 문서' },
];
</script>

<template>
  <main class="audit-screen" aria-label="감사 추적 탐색기">
    <aside class="audit-sidebar">
      <div class="brand-block">
        <div class="brand-mark">P</div>
        <div>
          <strong>제약 R&amp;D</strong>
          <span>GxP 환경</span>
        </div>
      </div>

      <nav class="audit-nav" aria-label="감사 작업 영역">
        <a href="/">대시보드</a>
        <a href="/documents">문서</a>
        <a class="active" href="/audit-trail" aria-current="page">감사 추적</a>
        <a href="/admin/research-projects">관리자</a>
      </nav>

      <a class="security-link" href="/admin/permissions">보안</a>
    </aside>

    <section class="audit-canvas">
      <header class="audit-topbar">
        <a href="/audit-trail" aria-current="page">EDMS 클라우드 감사</a>
        <span>시스템 상태</span>
        <span>검증기 상태</span>
      </header>

      <section class="audit-header">
        <div>
          <h1>감사 추적 탐색기</h1>
          <p>GxP CFR Part 11을 준수하는 변경 불가능한 거래 이력입니다.</p>
        </div>
        <div class="audit-actions">
          <label class="search-field">
            <span class="sr-only">감사 추적 검색</span>
            <input type="search" placeholder="해시, 행위자, 리소스 ID로 검색..." />
          </label>
          <button type="button" class="secondary-button">고급 검색</button>
          <button type="button" class="primary-button">인증 로그 내보내기</button>
        </div>
      </section>

      <section class="audit-workspace">
        <div class="audit-table-card">
          <table>
            <thead>
              <tr>
                <th>거래 시각</th>
                <th>주요 행위자</th>
                <th>작업 분류</th>
                <th>리소스 식별자</th>
                <th>클라이언트 IP / 출처</th>
                <th>무결성 상태</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in auditRows" :key="row.timestamp" :class="{ selected: row.selected }">
                <td class="mono">{{ row.timestamp }}</td>
                <td>
                  <span class="actor">{{ row.actor }}</span>
                  <span class="role-chip">{{ row.role }}</span>
                </td>
                <td><span class="action-chip">{{ row.action }}</span></td>
                <td>{{ row.resource }}</td>
                <td>{{ row.origin }}</td>
                <td><span class="verified">검증됨</span></td>
              </tr>
            </tbody>
          </table>

          <div class="metrics-row">
            <article v-for="metric in metrics" :key="metric.label" class="metric-tile">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
              <small>{{ metric.note }}</small>
            </article>
            <article class="metric-tile compliance">
              <span>준수 지표</span>
              <strong>통과</strong>
              <small>마지막 감사: 14일 전. 시스템 검증 완료.</small>
            </article>
          </div>
        </div>

        <aside class="evidence-panel" aria-label="거래 증거">
          <header>
            <div>
              <span class="panel-kicker">거래 증거</span>
              <strong>550e8400-e29b-41d4-a716-446655440000</strong>
            </div>
            <button type="button" aria-label="증거 패널 닫기">x</button>
          </header>

          <section class="hash-box">
            <span>내부 UUID</span>
            <code>550e8400-e29b-41d4-a716-446655440000</code>
            <span>머클 증명 해시 (SHA-256)</span>
            <code>8a92f32c...b5d1</code>
          </section>

          <section class="delta-section">
            <div class="section-title">
              <strong>데이터 변경 내역 분석</strong>
              <span>차이 형식: RFC 6902</span>
            </div>
            <div class="code-diff">
              <p class="removed-label">이전 버전 (비식별 처리)</p>
              <pre>{
  "resource_id": "DOC-VAL-2023-091",
- "study_phase": "2상",
- "participant_count": 120,
  "last_review": "2023-05-12"
}</pre>
              <p class="added-label">현재 버전</p>
              <pre>{
  "resource_id": "DOC-VAL-2023-091",
+ "study_phase": "3상",
+ "participant_count": 450,
  "last_review": "2023-10-24"
}</pre>
            </div>
          </section>

          <section class="timeline">
            <h2>부인 방지 체인</h2>
            <article>
              <strong>개시 및 전자서명</strong>
              <span>책임자: 사라 젠킨스 박사 (책임 연구자)</span>
              <small>2023-10-24 14:31:02.001Z | CID: 8829</small>
            </article>
            <article>
              <strong>준수 엔진 검증</strong>
              <span>행위자: 자동 GxP 검증기 (시스템)</span>
              <small>규칙: 21-CFR-PART-11 통과</small>
            </article>
            <article class="pending">
              <strong>최종 감독 검토 (필수)</strong>
              <span>대기 중: 시스템 관리자 그룹</span>
            </article>
          </section>

          <footer>
            <button type="button" class="primary-button">항목 인증</button>
            <button type="button" class="secondary-button">인증서 인쇄</button>
          </footer>
        </aside>
      </section>

      <footer class="audit-status">
        <span>색인 범위: 1,420,000 - 1,420,005 / 14,202,331</span>
        <strong>무결성 검증됨</strong>
      </footer>
    </section>
  </main>
</template>

<style scoped>
.audit-screen {
  --audit-bg: #f8f9ff;
  --audit-surface: #ffffff;
  --audit-line: #c3c6d2;
  --audit-muted: #515f74;
  --audit-text: #0b1c30;
  --audit-primary: #103d7d;
  --audit-primary-2: #2f5596;
  --audit-green: #004741;
  --audit-danger: #ba1a1a;

  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  min-width: 0;
  min-height: 100vh;
  overflow-x: hidden;
  color: var(--audit-text);
  background: var(--audit-bg);
  font-family: "IBM Plex Sans", "Noto Sans KR", system-ui, sans-serif;
}

.audit-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 16px 8px;
  background: #eff4ff;
  border-right: 1px solid var(--audit-line);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px 24px;
}

.brand-mark {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 2px;
  color: #ffffff;
  background: var(--audit-primary);
  font-weight: 800;
}

.brand-block strong,
.brand-block span {
  display: block;
  line-height: 1.35;
}

.brand-block strong {
  font-size: 14px;
}

.brand-block span {
  color: var(--audit-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.55px;
}

.audit-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  padding-top: 4px;
}

.audit-nav a,
.security-link {
  display: flex;
  align-items: center;
  min-height: 34px;
  padding: 8px;
  border-radius: 4px;
  color: #434750;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.55px;
  text-decoration: none;
}

.audit-nav a.active {
  color: #d8e5ff;
  background: var(--audit-primary-2);
}

.security-link {
  border-top: 1px solid var(--audit-line);
  padding-top: 20px;
}

.audit-canvas {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 100vh;
  flex-direction: column;
  overflow: hidden;
}

.audit-topbar {
  display: flex;
  align-items: center;
  gap: 36px;
  height: 56px;
  padding: 0 24px;
  border-bottom: 1px solid var(--audit-line);
  background: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  white-space: nowrap;
}

.audit-topbar a {
  color: var(--audit-primary);
  font-weight: 800;
  text-decoration: none;
}

.audit-topbar span {
  color: var(--audit-muted);
}

.audit-header {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  justify-content: space-between;
  padding: 24px;
}

.audit-header h1 {
  margin: 0;
  font-size: 24px;
  line-height: 32px;
}

.audit-header p {
  margin: 0;
  color: var(--audit-muted);
  font-size: 13px;
}

.audit-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  min-width: 0;
}

.search-field input {
  width: min(256px, 100%);
  height: 35px;
  padding: 0 9px;
  border: 1px solid #747781;
  border-radius: 2px;
  color: var(--audit-text);
  background: #ffffff;
  font: inherit;
  font-size: 12px;
}

button {
  height: 34px;
  border-radius: 2px;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  border: 1px solid var(--audit-primary);
  color: #ffffff;
  background: var(--audit-primary);
}

.secondary-button {
  border: 1px solid #747781;
  color: var(--audit-text);
  background: #ffffff;
}

.audit-workspace {
  position: relative;
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) minmax(420px, 40vw);
  gap: 0;
  min-height: 0;
  padding: 0 24px 56px;
}

.audit-table-card {
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--audit-line);
  border-radius: 2px;
  background: var(--audit-surface);
}

table {
  width: max(100%, 900px);
  border-collapse: collapse;
  table-layout: fixed;
}

th {
  height: 28px;
  padding: 6px 12px;
  border-bottom: 1px solid #747781;
  color: #434750;
  background: #eff4ff;
  font-size: 11px;
  letter-spacing: 0.55px;
  text-align: left;
}

td {
  height: 32px;
  padding: 6px 12px;
  border-bottom: 1px solid #e3e7f1;
  color: var(--audit-text);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

tr.selected td {
  background: rgba(47, 85, 150, 0.05);
}

.mono,
code,
pre {
  font-family: "JetBrains Mono", "Liberation Mono", monospace;
}

.actor {
  font-weight: 800;
}

.role-chip,
.action-chip,
.verified {
  display: inline-flex;
  align-items: center;
  height: 17px;
  margin-left: 6px;
  padding: 0 5px;
  border: 1px solid var(--audit-line);
  border-radius: 2px;
  color: var(--audit-muted);
  font-size: 10px;
  vertical-align: middle;
}

.action-chip {
  margin-left: 0;
  color: var(--audit-primary);
  background: #e4efff;
  font-weight: 800;
}

.verified {
  margin-left: 0;
  color: var(--audit-green);
  background: #e5f2ef;
  font-weight: 800;
  text-transform: uppercase;
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 292px;
  padding: 0 16px;
  min-width: 900px;
}

.metric-tile {
  height: 72px;
  padding: 9px 12px;
  border: 1px solid var(--audit-line);
  border-radius: 2px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(16, 61, 125, 0.06);
}

.metric-tile span,
.metric-tile small {
  display: block;
  color: var(--audit-muted);
  font-size: 11px;
}

.metric-tile strong {
  display: inline-block;
  margin-top: 4px;
  font-size: 28px;
  line-height: 1;
}

.metric-tile.compliance strong {
  color: var(--audit-green);
  font-size: 20px;
}

.evidence-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  margin-left: -1px;
  min-width: 0;
  border-left: 1px solid var(--audit-line);
  background: #ffffff;
  box-shadow: -32px 0 64px rgba(11, 28, 48, 0.16);
}

.evidence-panel > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--audit-line);
}

.panel-kicker {
  display: block;
  color: var(--audit-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1.6px;
}

.evidence-panel header strong {
  display: block;
  margin-top: 10px;
  font-size: 10px;
}

.evidence-panel header button {
  width: 32px;
  border: 0;
  color: var(--audit-text);
  background: transparent;
  font-size: 22px;
}

.hash-box {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 12px;
  margin: 12px 16px;
  padding: 10px 12px;
  border: 1px solid var(--audit-line);
  border-radius: 2px;
  background: #eff4ff;
}

.hash-box span {
  color: var(--audit-muted);
  font-size: 9px;
  font-weight: 800;
}

.hash-box code {
  color: var(--audit-text);
  font-size: 9px;
}

.delta-section,
.timeline {
  padding: 0 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0;
  font-size: 11px;
}

.section-title span {
  color: var(--audit-muted);
  font-family: "JetBrains Mono", "Liberation Mono", monospace;
  font-size: 10px;
}

.code-diff {
  padding: 12px;
  border: 1px solid #111827;
  border-radius: 2px;
  color: #d4d4d4;
  background: #1e1e1e;
}

.code-diff p {
  margin: 0 0 6px;
  font-family: "JetBrains Mono", "Liberation Mono", monospace;
  font-size: 10px;
}

.removed-label {
  color: var(--audit-danger);
}

.added-label {
  margin-top: 14px !important;
  color: var(--audit-green);
}

.code-diff pre {
  margin: 0;
  padding: 8px 10px;
  border-left: 2px solid currentColor;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.22);
  font-size: 11px;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre;
}

.timeline {
  margin-top: 16px;
}

.timeline h2 {
  margin: 0 0 8px;
  font-size: 11px;
}

.timeline article {
  position: relative;
  margin-left: 10px;
  padding: 9px 10px;
  border: 1px solid var(--audit-line);
  border-radius: 2px;
  background: #ffffff;
}

.timeline article + article {
  margin-top: 13px;
}

.timeline article::before {
  position: absolute;
  left: -24px;
  top: 0;
  display: grid;
  width: 14px;
  height: 14px;
  place-items: center;
  border-radius: 50%;
  color: #ffffff;
  background: var(--audit-green);
  content: "";
}

.timeline article::after {
  position: absolute;
  left: -18px;
  top: 14px;
  width: 1px;
  height: calc(100% + 13px);
  background: #747781;
  content: "";
}

.timeline article:last-child::after {
  display: none;
}

.timeline article.pending {
  border-style: dashed;
}

.timeline article.pending::before {
  border: 1px solid #747781;
  background: #ffffff;
}

.timeline strong,
.timeline span,
.timeline small {
  display: block;
  font-size: 11px;
}

.timeline span {
  margin-top: 5px;
  color: var(--audit-muted);
  font-size: 10px;
}

.timeline small {
  margin-top: 4px;
  color: var(--audit-primary);
  font-family: "JetBrains Mono", "Liberation Mono", monospace;
  font-size: 9px;
}

.evidence-panel footer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  margin-top: auto;
  padding: 13px 16px 16px;
  border-top: 1px solid var(--audit-line);
  background: #eff4ff;
}

.evidence-panel footer button {
  height: 36px;
  padding: 0 16px;
  font-size: 12px;
  letter-spacing: 0.6px;
}

.audit-status {
  position: absolute;
  right: 536px;
  bottom: 0;
  left: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 16px;
  border: 1px solid var(--audit-line);
  border-top: 0;
  color: var(--audit-muted);
  background: #eff4ff;
  font-family: "JetBrains Mono", "Liberation Mono", monospace;
  font-size: 10px;
}

.audit-status strong {
  color: var(--audit-green);
  font-size: 10px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

@media (max-width: 1180px) {
  .audit-screen {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .audit-sidebar {
    padding-inline: 6px;
  }

  .audit-header {
    flex-direction: column;
  }

  .audit-actions {
    justify-content: flex-start;
    width: 100%;
  }

  .audit-workspace {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
    overflow-y: auto;
  }

  .evidence-panel {
    margin-left: 0;
    border: 1px solid var(--audit-line);
    box-shadow: none;
  }

  .audit-status {
    right: 24px;
  }
}

@media (max-width: 760px) {
  .audit-screen {
    display: block;
    overflow-x: hidden;
  }

  .audit-sidebar {
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--audit-line);
  }

  .brand-block {
    padding-bottom: 12px;
  }

  .audit-nav {
    flex: none;
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .audit-nav a,
  .security-link {
    flex: 0 0 auto;
  }

  .security-link {
    display: none;
  }

  .audit-topbar {
    gap: 16px;
    overflow-x: auto;
  }

  .audit-header,
  .audit-workspace {
    padding-inline: 16px;
  }

  .search-field,
  .search-field input,
  .audit-actions button {
    width: 100%;
  }

  .hash-box {
    grid-template-columns: 1fr;
  }

  .evidence-panel footer {
    grid-template-columns: 1fr;
  }

  .audit-status {
    position: static;
    margin: 0 16px 16px;
  }
}
</style>
