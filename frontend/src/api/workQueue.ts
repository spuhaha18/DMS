import { api } from './client';

export interface WorkQueueItem {
  id: number;
  kind: 'APPROVAL' | 'TRAINING' | 'PERIODIC_REVIEW' | 'READACK';
  state: 'OPEN' | 'DONE' | 'CANCELLED' | 'EXPIRED';
  title: string;
  summary?: string;
  linkPath?: string;
  priority: string;
  relatedDocumentId?: number;
  relatedVersionId?: number;
  assigneeUserId: number;
  delegatedFromUserId?: number;
  createdAt: string;
  completedAt?: string;
}

export interface WorkQueueCounts {
  open: number;
  total: number;
}

export interface WorkQueueListResponse {
  content: WorkQueueItem[];
  totalElements: number;
}

const suppressToast = { suppressGlobalToast: true } as const;

export async function listWorkQueue(params: {
  kind?: string;
  state?: string;
} = {}): Promise<WorkQueueListResponse> {
  return (await api.get<WorkQueueListResponse>('/work-queue', { params, ...suppressToast })).data;
}

export async function fetchWorkQueueCounts(): Promise<WorkQueueCounts> {
  return (await api.get<WorkQueueCounts>('/work-queue/counts', suppressToast)).data;
}

export async function markWorkQueueItemDone(id: number): Promise<void> {
  await api.post<void>(`/work-queue/${id}/done`, {}, suppressToast);
}

export async function cancelWorkQueueItem(id: number): Promise<void> {
  await api.post<void>(`/work-queue/${id}/cancel`, {}, suppressToast);
}
