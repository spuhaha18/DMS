import { api } from './client';

export interface Delegation {
  id: number;
  delegatorUserId: number;
  delegateUserId: number;
  scopeKind: 'ALL' | 'APPROVAL_STEP';
  scopeValue?: string;
  reason: string;
  validFrom: string;
  validTo: string;
  state: 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'REVOKED';
  qaApproverUserId?: number;
  qaApprovedAt?: string;
  qaRejectionReason?: string;
  revokedAt?: string;
  revokedByUserId?: number;
  createdAt: string;
}

export interface RequestDelegationBody {
  delegateUserId: number;
  scopeKind: 'ALL' | 'APPROVAL_STEP';
  scopeValue?: string;
  reason: string;
  validFrom: string;
  validTo: string;
}

const suppressToast = { suppressGlobalToast: true } as const;

export async function requestDelegation(body: RequestDelegationBody): Promise<Delegation> {
  return (await api.post<Delegation>('/delegations', body, suppressToast)).data;
}

export async function listDelegations(as?: 'delegator' | 'delegate' | 'qa-pending'): Promise<Delegation[]> {
  const params = as ? { as } : {};
  return (await api.get<Delegation[]>('/delegations', { params, ...suppressToast })).data;
}

export async function approveDelegation(id: number): Promise<void> {
  await api.post<void>(`/delegations/${id}/approve`, {}, suppressToast);
}

export async function rejectDelegation(id: number, reason: string): Promise<void> {
  await api.post<void>(`/delegations/${id}/reject`, { reason }, suppressToast);
}

export async function revokeDelegation(id: number): Promise<void> {
  await api.post<void>(`/delegations/${id}/revoke`, {}, suppressToast);
}
