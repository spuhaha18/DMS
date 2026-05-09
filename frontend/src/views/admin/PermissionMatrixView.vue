<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { permissionsApi, rolesApi } from '../../api/admin';
import type { Permission, Role } from '../../types';

const roles = ref<Role[]>([]);
const perms = ref<Permission[]>([]);
const filterRole = ref<number | undefined>();

async function load() {
  perms.value = await permissionsApi.list({ role_id: filterRole.value });
}

onMounted(async () => {
  roles.value = await rolesApi.list();
  await load();
});

async function toggle(p: Permission, field: keyof Permission) {
  (p as any)[field] = !(p as any)[field];
  await permissionsApi.upsert({
    role_id: p.role_id, category_id: p.category_id, department: p.department ?? null,
    can_view: p.can_view, can_download: p.can_download, can_create: p.can_create,
    can_edit_draft: p.can_edit_draft, can_review: p.can_review,
    can_approve: p.can_approve, can_retire: p.can_retire,
  });
}

async function remove(id: number) {
  if (!confirm('삭제하시겠습니까?')) return;
  await permissionsApi.delete(id);
  await load();
}
</script>

<template>
  <main style="max-width: 1300px; margin: 24px auto;">
    <h1>권한 매트릭스</h1>
    <label>역할 필터:
      <select v-model="filterRole" @change="load">
        <option :value="undefined">전체</option>
        <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.role_code }}</option>
      </select>
    </label>
    <table style="width: 100%; border-collapse: collapse; margin-top: 12px;">
      <thead><tr style="background: #f0f0f0;">
        <th>역할</th><th>카테고리</th><th>부서</th>
        <th>view</th><th>download</th><th>create</th><th>edit_draft</th>
        <th>review</th><th>approve</th><th>retire</th><th></th>
      </tr></thead>
      <tbody>
        <tr v-for="p in perms" :key="p.id" style="border-top: 1px solid #ddd;">
          <td>{{ p.role_code }}</td>
          <td>{{ p.category_code }}</td>
          <td>{{ p.department ?? '(전체)' }}</td>
          <td><input type="checkbox" :checked="p.can_view" @change="toggle(p, 'can_view')" /></td>
          <td><input type="checkbox" :checked="p.can_download" @change="toggle(p, 'can_download')" /></td>
          <td><input type="checkbox" :checked="p.can_create" @change="toggle(p, 'can_create')" /></td>
          <td><input type="checkbox" :checked="p.can_edit_draft" @change="toggle(p, 'can_edit_draft')" /></td>
          <td><input type="checkbox" :checked="p.can_review" @change="toggle(p, 'can_review')" /></td>
          <td><input type="checkbox" :checked="p.can_approve" @change="toggle(p, 'can_approve')" /></td>
          <td><input type="checkbox" :checked="p.can_retire" @change="toggle(p, 'can_retire')" /></td>
          <td><button @click="remove(p.id)">삭제</button></td>
        </tr>
      </tbody>
    </table>
  </main>
</template>
