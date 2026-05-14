import { api } from './client';

export interface Notification {
  id: number;
  eventCode: string;
  title: string;
  body?: string;
  read: boolean;
  readAt?: string;
  linkPath?: string;
  relatedDocumentId?: number;
  relatedVersionId?: number;
  severity: 'INFO' | 'WARN' | 'URGENT';
  createdAt: string;
}

export interface NotificationListResponse {
  content: Notification[];
  totalElements: number;
}

export interface UnreadCountResponse {
  count: number;
}

const suppressToast = { suppressGlobalToast: true } as const;

export async function listNotifications(params: {
  page?: number;
  size?: number;
} = {}): Promise<NotificationListResponse> {
  return (await api.get<NotificationListResponse>('/notifications', { params, ...suppressToast })).data;
}

export async function fetchUnreadCount(): Promise<UnreadCountResponse> {
  return (await api.get<UnreadCountResponse>('/notifications/unread-count', suppressToast)).data;
}

export async function markNotificationRead(id: number): Promise<void> {
  await api.put<void>(`/notifications/${id}/read`, {}, suppressToast);
}
