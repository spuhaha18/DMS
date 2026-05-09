<script setup lang="ts">
import { onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();

onMounted(async () => {
  if (!auth.me) await auth.fetchMe();
});

async function logout() {
  await auth.logout();
  location.href = '/login';
}
</script>

<template>
  <main style="max-width: 720px; margin: 40px auto;">
    <h1>EDMS 대시보드</h1>
    <p v-if="auth.me">{{ auth.me.fullName }}님 환영합니다 ({{ auth.me.roles.join(', ') }})</p>
    <nav v-if="auth.hasRole('ADMIN')" style="margin: 16px 0; display: flex; flex-wrap: wrap; gap: 8px;">
      <a href="/admin/users">사용자</a> |
      <a href="/admin/roles">역할</a> |
      <a href="/admin/permissions">권한</a> |
      <a href="/admin/departments">부서</a> |
      <a href="/admin/categories">카테고리</a> |
      <a href="/admin/numbering-templates">채번 템플릿</a>
    </nav>
    <button @click="logout">로그아웃</button>
  </main>
</template>
