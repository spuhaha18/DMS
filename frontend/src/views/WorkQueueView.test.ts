import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useWorkQueueStore } from '../stores/workQueue';
import * as workQueueApi from '../api/workQueue';
import type { WorkQueueItem } from '../api/workQueue';

vi.mock('../api/workQueue', () => ({
  listWorkQueue: vi.fn(),
  fetchWorkQueueCounts: vi.fn(),
  markWorkQueueItemDone: vi.fn(),
  cancelWorkQueueItem: vi.fn(),
}));

const makeItem = (overrides: Partial<WorkQueueItem>): WorkQueueItem => ({
  id: 1,
  kind: 'APPROVAL',
  state: 'OPEN',
  title: '결재 요청',
  priority: 'HIGH',
  assigneeUserId: 1,
  createdAt: '2026-05-14T00:00:00Z',
  ...overrides,
});

// WorkQueueView의 filteredItems computed 로직을 독립적으로 테스트
function filterItems(
  items: WorkQueueItem[],
  activeKind: string | null,
  stateFilter: 'OPEN' | 'DONE' | 'ALL'
): WorkQueueItem[] {
  return items.filter(item =>
    (activeKind === null || item.kind === activeKind) &&
    (stateFilter === 'ALL' || item.state === stateFilter)
  );
}

describe('WorkQueueView - filteredItems logic', () => {
  const items: WorkQueueItem[] = [
    makeItem({ id: 1, kind: 'APPROVAL', state: 'OPEN', title: '결재 요청' }),
    makeItem({ id: 2, kind: 'TRAINING', state: 'DONE', title: '교육 완료' }),
    makeItem({ id: 3, kind: 'APPROVAL', state: 'DONE', title: '결재 완료' }),
    makeItem({ id: 4, kind: 'READACK', state: 'OPEN', title: '열람 확인' }),
    makeItem({ id: 5, kind: 'PERIODIC_REVIEW', state: 'OPEN', title: '정기 검토' }),
  ];

  it('activeKind=null, stateFilter=OPEN: OPEN 항목만 반환', () => {
    const result = filterItems(items, null, 'OPEN');
    expect(result).toHaveLength(3);
    expect(result.every(i => i.state === 'OPEN')).toBe(true);
  });

  it('activeKind=APPROVAL, stateFilter=OPEN: APPROVAL+OPEN 항목만 반환', () => {
    const result = filterItems(items, 'APPROVAL', 'OPEN');
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe(1);
  });

  it('activeKind=APPROVAL, stateFilter=ALL: APPROVAL 전체 반환', () => {
    const result = filterItems(items, 'APPROVAL', 'ALL');
    expect(result).toHaveLength(2);
    expect(result.every(i => i.kind === 'APPROVAL')).toBe(true);
  });

  it('activeKind=null, stateFilter=DONE: DONE 항목만 반환', () => {
    const result = filterItems(items, null, 'DONE');
    expect(result).toHaveLength(2);
    expect(result.every(i => i.state === 'DONE')).toBe(true);
  });

  it('activeKind=null, stateFilter=ALL: 전체 항목 반환', () => {
    const result = filterItems(items, null, 'ALL');
    expect(result).toHaveLength(5);
  });

  it('매칭 항목 없을 때 빈 배열 반환', () => {
    const result = filterItems(items, 'TRAINING', 'OPEN');
    expect(result).toHaveLength(0);
  });
});

describe('WorkQueueView - store 연동', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('로딩 상태: isLoading이 true일 때 store 상태 반영', async () => {
    let resolve!: (v: any) => void;
    (workQueueApi.listWorkQueue as any).mockReturnValue(
      new Promise(r => { resolve = r; })
    );
    const store = useWorkQueueStore();
    const fetchPromise = store.fetchList();
    expect(store.isLoading).toBe(true);
    resolve({ content: [], totalElements: 0 });
    await fetchPromise;
    expect(store.isLoading).toBe(false);
  });

  it('에러 상태: API 실패 시 store.error 설정', async () => {
    (workQueueApi.listWorkQueue as any).mockRejectedValue(new Error('서버 오류'));
    const store = useWorkQueueStore();
    await store.fetchList();
    expect(store.error).toBe('서버 오류');
    expect(store.isLoading).toBe(false);
  });

  it('빈 상태: items가 비어있을 때 store.items 빈 배열', async () => {
    (workQueueApi.listWorkQueue as any).mockResolvedValue({ content: [], totalElements: 0 });
    const store = useWorkQueueStore();
    await store.fetchList();
    expect(store.items).toHaveLength(0);
    expect(store.error).toBeNull();
  });

  it('항목 표시: API에서 받은 items가 store에 저장됨', async () => {
    const mockItems = [
      makeItem({ id: 1, kind: 'APPROVAL', state: 'OPEN' }),
      makeItem({ id: 2, kind: 'TRAINING', state: 'DONE' }),
    ];
    (workQueueApi.listWorkQueue as any).mockResolvedValue({ content: mockItems, totalElements: 2 });
    const store = useWorkQueueStore();
    await store.fetchList();
    expect(store.items).toHaveLength(2);
    expect(store.items[0].kind).toBe('APPROVAL');
    expect(store.items[1].kind).toBe('TRAINING');
  });

  it('markDone 호출 후 counts 업데이트', async () => {
    (workQueueApi.markWorkQueueItemDone as any).mockResolvedValue(undefined);
    (workQueueApi.fetchWorkQueueCounts as any).mockResolvedValue({ open: 1, total: 5 });
    const store = useWorkQueueStore();
    await store.markDone(1);
    expect(workQueueApi.markWorkQueueItemDone).toHaveBeenCalledWith(1);
    expect(store.counts).toEqual({ open: 1, total: 5 });
  });

  it('counts.open이 0보다 클 때 배지 숫자 확인', async () => {
    (workQueueApi.fetchWorkQueueCounts as any).mockResolvedValue({ open: 5, total: 10 });
    const store = useWorkQueueStore();
    await store.fetchCounts();
    expect(store.counts.open).toBe(5);
    expect(store.counts.open > 0).toBe(true);
  });
});
