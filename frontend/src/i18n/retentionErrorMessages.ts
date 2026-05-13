// M7.5 retention error message i18n mapping
export const RETENTION_ERRORS: Record<string, string> = {
  retention_shortened: '보존기간은 단축할 수 없습니다. 시스템이 이미 더 긴 기간으로 잠가놓았습니다.',
  project_not_found: '해당 연구과제를 찾을 수 없습니다. 새로고침 후 다시 시도하세요.',
  invalid_state_transition: '현재 상태에서는 이 작업이 불가능합니다.',
  forbidden: '권한이 없습니다. QA 또는 관리자 계정으로 로그인하세요.',
  outbox_dead_letter: '보존기간 연장 작업이 5회 재시도 후 실패했습니다. IT 운영자에게 문의하세요.',
  feature_disabled: '이 기능은 아직 활성화되지 않았습니다. 운영자에게 문의하세요.',
  validation_failed: '입력값을 확인하세요.',
};

export function translateError(code: string, fallback?: string): string {
  return RETENTION_ERRORS[code] ?? fallback ?? '알 수 없는 오류가 발생했습니다.';
}
