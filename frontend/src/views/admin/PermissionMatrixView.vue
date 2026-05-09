<script setup lang="ts">
import { onMounted } from 'vue';
import { usePermissionMatrix } from '../../composables/usePermissionMatrix';
import type { Permission } from '../../types';

const {
  roles, permissions, filterRole, loading, error,
  load, loadRoles, togglePermission, removePermission,
} = usePermissionMatrix();

onMounted(async () => {
  await loadRoles();
  await load();
});

async function handleRemove(id: number) {
  if (!confirm('삭제하시겠습니까?')) return;
  await removePermission(id);
}
</script>

<template>
  <main style="max-width: 1300px; margin: 24px auto;">
    <h1>권한 매트릭스</h1>
    <div v-if="loading">로딩 중...</div>
    <div v-if="error" style="color: red;">{{ error }}</div>
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
        <tr v-for="p in permissions" :key="p.id" style="border-top: 1px solid #ddd;">
          <td>{{ p.role_code }}</td>
          <td>{{ p.category_code }}</td>
          <td>{{ p.department ?? '(전체)' }}</td>
          <td><input type="checkbox" :checked="p.can_view" @change="togglePermission(p, 'can_view')" /></td>
          <td><input type="checkbox" :checked="p.can_download" @change="togglePermission(p, 'can_download')" /></td>
          <td><input type="checkbox" :checked="p.can_create" @change="togglePermission(p, 'can_create')" /></td>
          <td><input type="checkbox" :checked="p.can_edit_draft" @change="togglePermission(p, 'can_edit_draft')" /></td>
          <td><input type="checkbox" :checked="p.can_review" @change="togglePermission(p, 'can_review')" /></td>
          <td><input type="checkbox" :checked="p.can_approve" @change="togglePermission(p, 'can_approve')" /></td>
          <td><input type="checkbox" :checked="p.can_retire" @change="togglePermission(p, 'can_retire')" /></td>
          <td><button @click="handleRemove(p.id)">삭제</button></td>
        </tr>
      </tbody>
    </table>
  </main>
</template>
