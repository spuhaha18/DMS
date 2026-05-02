# 결재 페이지 placeholder 작성 가이드

EDMS는 사용자가 업로드한 .docx 양식의 결재 페이지에 결재자 이름과 서명일시를 자동으로 채워 PDF로 발급합니다. 양식 작성자는 결재 페이지의 셀에 다음 placeholder를 넣어 두면 됩니다.

## 변수표

| 변수 | 의미 | 예시 |
|---|---|---|
| `{{ document_number }}` | 문서번호 | P001-AM-001 |
| `{{ document_title }}` | 문서 제목 | 분석법 검증 SOP |
| `{{ revision }}` | 리비전 번호 | 2 |
| `{{ effective_date }}` | 발효일 (PDF 발급일) | 2026-05-01 |
| `{{ approvers[i].name }}` | i번째 결재자 이름 | 김 앨리스 |
| `{{ approvers[i].meaning }}` | 결재 의미 | 검토 |
| `{{ approvers[i].signed_at }}` | 서명일시 | 2026-05-01 14:23 |
| `{{ approver_N_name }}` | N단계 결재자 이름 (고정 칸 방식) | 김 앨리스 |
| `{{ approver_N_signed_at }}` | N단계 서명일시 (고정 칸 방식) | 2026-05-01 14:23 |

## 권장 — 결재선 길이 가변 (반복 행)

결재 표 행 자체에 docxtpl의 `{%tr ... %}` 태그를 한 번만 배치합니다 (셀이 아닌 행에).

```
{%tr for a in approvers %}
| {{ a.order }} | {{ a.meaning }} | {{ a.name }} | {{ a.signed_at }} |
{%tr endfor %}
```

## 대안 — 고정 칸

양식이 결재 단계 수를 고정한 경우:

```
| 검토 | {{ approver_1_name }} | {{ approver_1_signed_at }} |
| 승인 | {{ approver_2_name }} | {{ approver_2_signed_at }} |
```

## 주의사항

- placeholder가 없는 양식도 변환은 정상 진행됩니다 (호환). 결재 정보만 채워지지 않을 뿐.
- Word에서 `{{ ... }}` 입력 후 자동 서식이 영문 폰트로 바뀌지 않도록 한글 폰트(맑은 고딕 등)를 유지하세요.
- 결재 페이지 외에는 `{{` 문자를 본문에 사용하지 마세요 (Jinja 구분자와 충돌).
- 서명/도장 이미지는 사용하지 않습니다 (이름·일시만 표시).

## 기존 데이터 정정 (선택)

결재 의미(`signature_meaning`)가 영어로 저장된 기존 운영 데이터가 있다면 admin에서 한국어로 1회 정정하세요. SQL 일괄 정정은 다국어 운영 가능성 때문에 자동화하지 않습니다.

```sql
-- 예시 (수동 검토 후 적용)
UPDATE approvals_approvalroutestep SET signature_meaning = '검토' WHERE signature_meaning = 'Review';
UPDATE approvals_approvalroutestep SET signature_meaning = '승인' WHERE signature_meaning = 'Approval';
```
