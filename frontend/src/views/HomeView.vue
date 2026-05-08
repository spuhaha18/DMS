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
    <button @click="logout">로그아웃</button>
  </main>
</template>
