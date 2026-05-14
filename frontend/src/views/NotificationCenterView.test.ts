import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNotificationStore } from '../stores/notification';
import * as notificationsApi from '../api/notifications';
import type { Notification } from '../api/notifications';
import { getNotificationLabel } from '../i18n/notificationMessages';

vi.mock('../api/notifications', () => ({
  listNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

function makeNotification(overrides: Partial<Notification> = {}): Notification {
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

// Category derivation logic (mirrors NotificationCenterView)
type Category = 'ALL' | 'WORKFLOW' | 'DELEGATION' | 'SYSTEM';
type ReadFilter = 'ALL' | 'UNREAD' | 'READ';

function getCategory(eventCode: string): Category {
  if (eventCode.startsWith('WORKFLOW_')) return 'WORKFLOW';
  if (eventCode.startsWith('DELEGATION_')) return 'DELEGATION';
  return 'SYSTEM';
}

function filterNotifications(
  items: Notification[],
  activeCategory: Category,
  readFilter: ReadFilter
): Notification[] {
  return items.filter(item => {
    const categoryMatch =
      activeCategory === 'ALL' || getCategory(item.eventCode) === activeCategory;
    const readMatch =
      readFilter === 'ALL' ||
      (readFilter === 'UNREAD' && !item.read) ||
      (readFilter === 'READ' && item.read);
    return categoryMatch && readMatch;
  });
}

describe('NotificationCenterView - getCategory 로직', () => {
  it('WORKFLOW_ 접두사 → WORKFLOW 카테고리', () => {
    expect(getCategory('WORKFLOW_SUBMITTED')).toBe('WORKFLOW');
    expect(getCategory('WORKFLOW_EFFECTIVE')).toBe('WORKFLOW');
  });

  it('DELEGATION_ 접두사 → DELEGATION 카테고리', () => {
    expect(getCategory('DELEGATION_APPROVED')).toBe('DELEGATION');
    expect(getCategory('DELEGATION_EXPIRED')).toBe('DELEGATION');
  });

  it('그 외 → SYSTEM 카테고리', () => {
    expect(getCategory('SYSTEM_ALERT')).toBe('SYSTEM');
    expect(getCategory('UNKNOWN')).toBe('SYSTEM');
  });
});

describe('NotificationCenterView - filteredItems 로직', () => {
  const items: Notification[] = [
    makeNotification({ id: 1, eventCode: 'WORKFLOW_SUBMITTED', read: false }),
    makeNotification({ id: 2, eventCode: 'WORKFLOW_SIGNED', read: true }),
    makeNotification({ id: 3, eventCode: 'DELEGATION_APPROVED', read: false }),
    makeNotification({ id: 4, eventCode: 'DELEGATION_EXPIRED', read: true }),
    makeNotification({ id: 5, eventCode: 'SYSTEM_ALERT', read: false }),
  ];

  it('activeCategory=ALL, readFilter=ALL: 전체 항목 반환', () => {
    expect(filterNotifications(items, 'ALL', 'ALL')).toHaveLength(5);
  });

  it('category filter: WORKFLOW만 필터링', () => {
    const result = filterNotifications(items, 'WORKFLOW', 'ALL');
    expect(result).toHaveLength(2);
    result.forEach(item => expect(item.eventCode).toMatch(/^WORKFLOW_/));
  });

  it('category filter: DELEGATION만 필터링', () => {
    const result = filterNotifications(items, 'DELEGATION', 'ALL');
    expect(result).toHaveLength(2);
    result.forEach(item => expect(item.eventCode).toMatch(/^DELEGATION_/));
  });

  it('read filter: UNREAD만 필터링', () => {
    const result = filterNotifications(items, 'ALL', 'UNREAD');
    expect(result).toHaveLength(3);
    result.forEach(item => expect(item.read).toBe(false));
  });

  it('read filter: READ만 필터링', () => {
    const result = filterNotifications(items, 'ALL', 'READ');
    expect(result).toHaveLength(2);
    result.forEach(item => expect(item.read).toBe(true));
  });

  it('복합 필터: WORKFLOW + UNREAD', () => {
    const result = filterNotifications(items, 'WORKFLOW', 'UNREAD');
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe(1);
  });

  it('매칭 항목 없을 때 빈 배열 반환', () => {
    const result = filterNotifications(items, 'SYSTEM', 'READ');
    expect(result).toHaveLength(0);
  });
});

describe('NotificationCenterView - store 연동', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('로딩 상태: isLoading이 true일 때 store 상태 반영', async () => {
    let resolve!: (v: any) => void;
    (notificationsApi.listNotifications as any).mockReturnValue(
      new Promise(r => { resolve = r; })
    );
    const store = useNotificationStore();
    const fetchPromise = store.fetchList();
    expect(store.isLoading).toBe(true);
    resolve({ content: [], totalElements: 0 });
    await fetchPromise;
    expect(store.isLoading).toBe(false);
  });

  it('빈 상태: items가 비어있을 때 store.items 빈 배열', async () => {
    (notificationsApi.listNotifications as any).mockResolvedValue({ content: [], totalElements: 0 });
    const store = useNotificationStore();
    await store.fetchList();
    expect(store.items).toHaveLength(0);
  });

  it('항목 표시: API에서 받은 items가 store에 저장됨', async () => {
    const mockItems = [
      makeNotification({ id: 1, eventCode: 'WORKFLOW_SUBMITTED' }),
      makeNotification({ id: 2, eventCode: 'DELEGATION_APPROVED' }),
    ];
    (notificationsApi.listNotifications as any).mockResolvedValue({ content: mockItems, totalElements: 2 });
    const store = useNotificationStore();
    await store.fetchList();
    expect(store.items).toHaveLength(2);
  });

  it('markRead: 읽지 않은 항목 클릭 시 markNotificationRead 호출', async () => {
    (notificationsApi.markNotificationRead as any).mockResolvedValue(undefined);
    (notificationsApi.fetchUnreadCount as any).mockResolvedValue({ count: 0 });
    const mockItems = [makeNotification({ id: 1, read: false })];
    (notificationsApi.listNotifications as any).mockResolvedValue({ content: mockItems, totalElements: 1 });
    const store = useNotificationStore();
    await store.fetchList();
    await store.markRead(1);
    expect(notificationsApi.markNotificationRead).toHaveBeenCalledWith(1);
    const target = store.items.find(n => n.id === 1);
    expect(target?.read).toBe(true);
  });

  it('에러 상태: fetchList 실패 시 store.error에 메시지 설정', async () => {
    (notificationsApi.listNotifications as any).mockRejectedValue(new Error('Network Error'));
    const store = useNotificationStore();
    await store.fetchList();
    expect(store.error).toBe('알림을 불러오지 못했습니다.');
    expect(store.isLoading).toBe(false);
  });

  it('에러 상태: 재시도 성공 시 error 초기화', async () => {
    (notificationsApi.listNotifications as any).mockRejectedValueOnce(new Error('Network Error'));
    (notificationsApi.listNotifications as any).mockResolvedValueOnce({ content: [], totalElements: 0 });
    const store = useNotificationStore();
    await store.fetchList();
    expect(store.error).toBe('알림을 불러오지 못했습니다.');
    await store.fetchList();
    expect(store.error).toBeNull();
  });
});

describe('notificationMessages i18n', () => {
  it('알려진 eventCode → 한국어 레이블 반환', () => {
    expect(getNotificationLabel('WORKFLOW_SUBMITTED')).toBe('결재 요청됨');
    expect(getNotificationLabel('DELEGATION_APPROVED')).toBe('위임 승인됨');
  });

  it('알 수 없는 eventCode → 코드 그대로 반환', () => {
    expect(getNotificationLabel('UNKNOWN_CODE')).toBe('UNKNOWN_CODE');
  });
});
