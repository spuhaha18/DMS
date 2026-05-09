<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { usersApi } from '../../api/admin';

const r = useRouter();
const userId = ref('');
const fullName = ref('');
const email = ref('');
const department = ref('');
const title = ref('');
const rolesCsv = ref('AUTHOR');
const validFrom = ref('');
const validUntil = ref('');
const error = ref('');

async function submit() {
  error.value = '';
  try {
    await usersApi.create({
      user_id: userId.value,
      full_name: fullName.value,
      email: email.value,
      department: department.value,
      title: title.value || undefined,
      role_codes: rolesCsv.value.split(',').map((s) => s.trim()).filter(Boolean),
      valid_from: validFrom.value || null,
      valid_until: validUntil.value || null,
    });
    r.push({ name: 'admin-users' });
  } catch (e: any) {
    error.value = e.response?.data?.message ?? '생성 실패';
  }
}
</script>

<template>
  <main style="max-width: 540px; margin: 24px auto; font-family: inherit;">
    <h1>신규 사용자</h1>
    <form @submit.prevent="submit" style="display: flex; flex-direction: column; gap: 8px;">
      <label>user_id<input v-model="userId" required pattern="^[a-zA-Z0-9._\-]{2,50}$" /></label>
      <label>이름<input v-model="fullName" required /></label>
      <label>이메일<input v-model="email" type="email" required /></label>
      <label>부서<input v-model="department" required /></label>
      <label>직책<input v-model="title" /></label>
      <label>역할(쉼표 구분)<input v-model="rolesCsv" required placeholder="AUTHOR,READER" /></label>
      <label>유효 시작일<input v-model="validFrom" type="date" /></label>
      <label>유효 종료일<input v-model="validUntil" type="date" /></label>
      <p v-if="error" style="color: #c00;">{{ error }}</p>
      <button type="submit">생성</button>
    </form>
  </main>
</template>
