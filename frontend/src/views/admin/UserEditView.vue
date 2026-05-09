<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { usersApi, rolesApi } from '../../api/admin';
import type { Role, User } from '../../types';

const route = useRoute();
const router = useRouter();
const user = ref<User | null>(null);
const roles = ref<Role[]>([]);
const selectedRoles = ref<string[]>([]);
const error = ref('');

onMounted(async () => {
  const userPk = Number(route.params.userPk);
  user.value = await usersApi.get(userPk);
  roles.value = await rolesApi.list();
  selectedRoles.value = [...user.value.role_codes];
});

async function save() {
  if (!user.value) return;
  error.value = '';
  try {
    await usersApi.update(user.value.id, {
      full_name: user.value.full_name,
      email: user.value.email,
      department: user.value.department,
      title: user.value.title ?? undefined,
      valid_from: user.value.valid_from,
      valid_until: user.value.valid_until,
    });
    await usersApi.updateRoles(user.value.id, { role_codes: selectedRoles.value });
    router.push({ name: 'admin-users' });
  } catch (e: any) { error.value = e.response?.data?.message ?? '수정 실패'; }
}

async function pwReset() {
  if (!user.value) return;
  if (!confirm('임시 비밀번호 발급 (이메일 발송)?')) return;
  await usersApi.passwordReset(user.value.user_id);
  alert('완료. 사용자에게 이메일이 발송되었습니다.');
}
</script>

<template>
  <main v-if="user" style="max-width: 540px; margin: 24px auto;">
    <h1>사용자 수정 — {{ user.user_id }}</h1>
    <form @submit.prevent="save" style="display: flex; flex-direction: column; gap: 8px;">
      <label>이름<input v-model="user.full_name" required /></label>
      <label>이메일<input v-model="user.email" type="email" required /></label>
      <label>부서<input v-model="user.department" required /></label>
      <label>직책<input v-model="user.title" /></label>
      <label>유효 시작<input v-model="user.valid_from" type="date" /></label>
      <label>유효 종료<input v-model="user.valid_until" type="date" /></label>
      <fieldset>
        <legend>역할</legend>
        <label v-for="r in roles" :key="r.id" style="display: block;">
          <input type="checkbox" :value="r.role_code" v-model="selectedRoles" />
          {{ r.role_code }} ({{ r.role_name }})
        </label>
      </fieldset>
      <p v-if="error" style="color: #c00;">{{ error }}</p>
      <div style="display: flex; gap: 8px;">
        <button type="submit">저장</button>
        <button type="button" @click="pwReset">비밀번호 재설정</button>
      </div>
    </form>
  </main>
</template>
