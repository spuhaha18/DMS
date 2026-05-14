import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  requestDelegation,
  listDelegations,
  approveDelegation,
  rejectDelegation,
  revokeDelegation,
} from '../api/delegations';
import type { Delegation, RequestDelegationBody } from '../api/delegations';

export const useDelegationStore = defineStore('delegation', () => {
  const items = ref<Delegation[]>([]);
  const isLoading = ref(false);

  async function fetchList(as?: 'delegator' | 'delegate' | 'qa-pending') {
    isLoading.value = true;
    try {
      items.value = await listDelegations(as);
    } finally {
      isLoading.value = false;
    }
  }

  async function request(body: RequestDelegationBody): Promise<Delegation> {
    const delegation = await requestDelegation(body);
    items.value = [delegation, ...items.value];
    return delegation;
  }

  async function approve(id: number) {
    await approveDelegation(id);
    const target = items.value.find((d) => d.id === id);
    if (target) {
      target.state = 'APPROVED';
    }
  }

  async function reject(id: number, reason: string) {
    await rejectDelegation(id, reason);
    const target = items.value.find((d) => d.id === id);
    if (target) {
      target.state = 'REJECTED';
    }
  }

  async function revoke(id: number) {
    await revokeDelegation(id);
    const target = items.value.find((d) => d.id === id);
    if (target) {
      target.state = 'REVOKED';
    }
  }

  return { items, isLoading, fetchList, request, approve, reject, revoke };
});
