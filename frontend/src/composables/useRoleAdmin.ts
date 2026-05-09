import { ref } from 'vue';
import { rolesApi } from '../api/admin';
import type { Role } from '../types';

export function useRoleAdmin() {
  const roles = ref<Role[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      roles.value = await rolesApi.list();
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '역할 목록을 불러오지 못했습니다.';
    } finally {
      loading.value = false;
    }
  }

  async function saveRole(role: Role) {
    loading.value = true;
    error.value = null;
    try {
      await rolesApi.update(role.id, {
        role_name: role.role_name,
        description: role.description ?? undefined,
      });
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '역할 저장에 실패했습니다.';
    } finally {
      loading.value = false;
    }
  }

  return {
    roles,
    loading,
    error,
    load,
    saveRole,
  };
}
