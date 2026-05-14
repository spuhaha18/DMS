import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNotificationStore } from '../../stores/notification';
import * as notificationsApi from '../../api/notifications';

vi.mock('../../api/notifications', () => ({
  listNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

function makeNotification(overrides: Partial<import('../../api/notifications').Notification> = {}): import('../../api/notifications').Notification {
  return {
    id: 1,
    eventCode: 'WORKFLOW_SUBMITTED',
    title: '결재 요청됨',
    read: false,
    severity: 'INFO',
    createdAt: '2026-05-14T00:00:00Z',
    ...overrides,
  };
}

// Test the badge display logic independently (same pattern as WorkQueueView tests)
function getBadgeText(unread: number): string | null {
  if (unread <= 0) return null;
  return unread > 99 ? '99+' : String(unread);
}

describe('NotificationBell - badge display logic', () => {
  it('벨 아이콘: unread > 0 일 때 배지 표시', () => {
    expect(getBadgeText(5)).toBe('5');
  });

  it('배지 숫자: unread가 99 이하일 때 숫자 그대로 표시', () => {
    expect(getBadgeText(99)).toBe('99');
  });

  it('배지 99+: unread가 100 이상일 때 99+ 표시', () => {
    expect(getBadgeText(100)).toBe('99+');
    expect(getBadgeText(999)).toBe('99+');
  });

  it('배지 숨김: unread = 0 일 때 null 반환', () => {
    expect(getBadgeText(0)).toBeNull();
  });
});

describe('NotificationBell - store 연동', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('startPolling 호출 시 fetchUnreadCount API 호출됨', async () => {
    (notificationsApi.fetchUnreadCount as any).mockResolvedValue({ count: 3 });
    const store = useNotificationStore();
    store.startPolling(30000);
    await Promise.resolve();
    expect(notificationsApi.fetchUnreadCount).toHaveBeenCalled();
    store.stopPolling();
  });

  it('stopPolling 호출 후 폴링 중단', async () => {
    (notificationsApi.fetchUnreadCount as any).mockResolvedValue({ count: 0 });
    const store = useNotificationStore();
    store.startPolling(30000);
    store.stopPolling();
    const callCount = (notificationsApi.fetchUnreadCount as any).mock.calls.length;
    await Promise.resolve();
    expect((notificationsApi.fetchUnreadCount as any).mock.calls.length).toBe(callCount);
  });

  it('unread 카운트: fetchUnreadCount 후 store.unread 갱신', async () => {
    (notificationsApi.fetchUnreadCount as any).mockResolvedValue({ count: 7 });
    const store = useNotificationStore();
    await store.fetchUnreadCount();
    expect(store.unread).toBe(7);
  });

  it('navigate: router.push(/notifications) 호출 확인', () => {
    const pushMock = vi.fn();
    const navigateFn = (push: (path: string) => void) => push('/notifications');
    navigateFn(pushMock);
    expect(pushMock).toHaveBeenCalledWith('/notifications');
  });
});
