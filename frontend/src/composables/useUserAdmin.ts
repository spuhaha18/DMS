import { ref } from 'vue';
import { usersApi } from '../api/admin';
import type { PageResponse, User } from '../types';

export function useUserAdmin() {
  const users = ref<PageResponse<User> | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const status = ref('');
  const department = ref('');
  const page = ref(0);

  async function load(p = 0) {
    loading.value = true;
    error.value = null;
    try {
      page.value = p;
      users.value = await usersApi.list({
        status: status.value || undefined,
        department: department.value || undefined,
        page: p,
        size: 20,
      });
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '사용자 목록을 불러오지 못했습니다.';
    } finally {
      loading.value = false;
    }
  }

  async function exportCsv() {
    loading.value = true;
    error.value = null;
    try {
      const csv = await usersApi.exportCsv();
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'access-review.csv';
      a.click();
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'CSV 다운로드에 실패했습니다.';
    } finally {
      loading.value = false;
    }
  }

  async function disableUser(userId: string, reason: string) {
    loading.value = true;
    error.value = null;
    try {
      await usersApi.disable(userId, { reason });
      await load(users.value?.page ?? 0);
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '사용자 비활성화에 실패했습니다.';
    } finally {
      loading.value = false;
    }
  }

  return {
    users,
    loading,
    error,
    status,
    department,
    page,
    load,
    exportCsv,
    disableUser,
  };
}
