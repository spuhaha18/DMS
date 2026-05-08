<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const userId = ref('');
const password = ref('');
const errorCode = ref('');
const remaining = ref<number | null>(null);
const auth = useAuthStore();
const router = useRouter();

async function submit() {
  errorCode.value = '';
  remaining.value = null;
  try {
    await auth.login(userId.value, password.value);
    router.push({ name: 'home' });
  } catch (e: any) {
    const data = e.response?.data;
    errorCode.value = data?.code ?? 'UNKNOWN';
    remaining.value = data?.remaining_attempts ?? null;
  }
}
</script>

<template>
  <main style="max-width: 360px; margin: 80px auto; font-family: inherit;">
    <h1>EDMS 로그인</h1>
    <form @submit.prevent="submit">
      <label>
        사용자 ID
        <input v-model="userId" required autocomplete="username" />
      </label>
      <label>
        비밀번호
        <input v-model="password" type="password" required autocomplete="current-password" />
      </label>
      <button type="submit">로그인</button>
    </form>
    <p v-if="errorCode === 'AUTH_001'">
      ID 또는 비밀번호가 올바르지 않습니다. 남은 시도: {{ remaining }}회
    </p>
    <p v-else-if="errorCode === 'AUTH_002'">
      계정이 잠겼습니다. 시스템 관리자에게 문의하세요.
    </p>
    <p v-else-if="errorCode === 'AUTH_003'">계정이 비활성화되었습니다.</p>
    <p v-else-if="errorCode && errorCode !== 'UNKNOWN'">{{ errorCode }}</p>
  </main>
</template>

<style scoped>
form { display: flex; flex-direction: column; gap: 12px; }
label { display: flex; flex-direction: column; gap: 4px; }
input { padding: 8px; font-size: 14px; }
button { padding: 10px; background: #1f6feb; color: white; border: none; cursor: pointer; }
</style>
