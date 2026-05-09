import { ref } from 'vue';
import { permissionsApi, rolesApi } from '../api/admin';
import type { Permission, Role } from '../types';

export function usePermissionMatrix() {
  const roles = ref<Role[]>([]);
  const permissions = ref<Permission[]>([]);
  const filterRole = ref<number | undefined>(undefined);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      permissions.value = await permissionsApi.list({ role_id: filterRole.value });
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '권한 목록을 불러오지 못했습니다.';
    } finally {
      loading.value = false;
    }
  }

  async function loadRoles() {
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

  async function togglePermission(p: Permission, field: keyof Permission) {
    loading.value = true;
    error.value = null;
    try {
      (p as unknown as Record<string, unknown>)[field as string] = !(p as unknown as Record<string, unknown>)[field as string];
      await permissionsApi.upsert({
        role_id: p.role_id,
        category_id: p.category_id,
        department: p.department ?? null,
        can_view: p.can_view,
        can_download: p.can_download,
        can_create: p.can_create,
        can_edit_draft: p.can_edit_draft,
        can_review: p.can_review,
        can_approve: p.can_approve,
        can_retire: p.can_retire,
      });
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '권한 변경에 실패했습니다.';
    } finally {
      loading.value = false;
    }
  }

  async function removePermission(id: number) {
    loading.value = true;
    error.value = null;
    try {
      await permissionsApi.delete(id);
      await load();
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '권한 삭제에 실패했습니다.';
    } finally {
      loading.value = false;
    }
  }

  return {
    roles,
    permissions,
    filterRole,
    loading,
    error,
    load,
    loadRoles,
    togglePermission,
    removePermission,
  };
}
