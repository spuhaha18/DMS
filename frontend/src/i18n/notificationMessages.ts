export const notificationMessages: Record<string, string> = {
  WORKFLOW_SUBMITTED: '결재 요청됨',
  WORKFLOW_SIGNED: '서명됨',
  WORKFLOW_REJECTED: '결재 반려됨',
  WORKFLOW_EFFECTIVE: '문서 발효됨',
  DELEGATION_REQUESTED: '위임 요청됨',
  DELEGATION_APPROVED: '위임 승인됨',
  DELEGATION_REJECTED: '위임 거부됨',
  DELEGATION_EXPIRED: '위임 만료됨',
};

export function getNotificationLabel(eventCode: string): string {
  return notificationMessages[eventCode] ?? eventCode;
}
