<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoleAdmin } from '../../composables/useRoleAdmin';

const { roles, loading, error, load, saveRole } = useRoleAdmin();

onMounted(load);

async function save(r: import('../../types').Role) {
  await saveRole(r);
  if (!error.value) alert('저장됨');
}
</script>

<template>
  <main style="max-width: 800px; margin: 24px auto;">
    <h1>역할 관리</h1>
    <div v-if="loading">로딩 중...</div>
    <div v-if="error" style="color: red;">{{ error }}</div>
    <table style="width: 100%; border-collapse: collapse;">
      <thead><tr style="background: #f0f0f0;">
        <th>코드</th><th>이름</th><th>설명</th><th>System</th><th></th>
      </tr></thead>
      <tbody>
        <tr v-for="r in roles" :key="r.id" style="border-top: 1px solid #ddd;">
          <td style="padding: 6px;">{{ r.role_code }}</td>
          <td><input v-model="r.role_name" /></td>
          <td><input v-model="r.description" /></td>
          <td>{{ r.is_system ? 'YES' : 'NO' }}</td>
          <td><button @click="save(r)">저장</button></td>
        </tr>
      </tbody>
    </table>
  </main>
</template>
