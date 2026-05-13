// M7.5 retention error message i18n mapping
// Keys match backend ProblemDetail.code values from GlobalExceptionHandler
export const RETENTION_ERRORS: Record<string, string> = {
  LIFECYCLE_001: '현재 상태에서는 이 작업이 불가능합니다.',
  RETENTION_001: '보존기간은 단축할 수 없습니다. 시스템이 이미 더 긴 기간으로 잠가놓았습니다.',
  AUTHZ_001: '권한이 없습니다. QA 또는 관리자 계정으로 로그인하세요.',
  AUTHZ_002: '권한이 없습니다. QA 또는 관리자 계정으로 로그인하세요.',
  RSE_404: '해당 연구과제를 찾을 수 없습니다. 새로고침 후 다시 시도하세요.',
  NOT_FOUND: '해당 연구과제를 찾을 수 없습니다. 새로고침 후 다시 시도하세요.',
  VALIDATION_001: '입력값을 확인하세요.',
  VALIDATION_002: '입력값을 확인하세요.',
};

export function translateError(code: string, fallback?: string): string {
  return RETENTION_ERRORS[code] ?? fallback ?? '알 수 없는 오류가 발생했습니다.';
}
