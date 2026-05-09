<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserAdmin } from '../../composables/useUserAdmin';

const router = useRouter();
const { users, loading, error, status, department, load, exportCsv, disableUser } = useUserAdmin();

onMounted(() => load(0));

async function handleDisable(userId: string) {
  const reason = prompt('Disable reason (≥5 chars)');
  if (!reason || reason.length < 5) return;
  await disableUser(userId, reason);
}
</script>

<template>
  <main style="max-width: 1100px; margin: 24px auto; font-family: inherit;">
    <h1>사용자 관리</h1>
    <div v-if="loading">로딩 중...</div>
    <div v-if="error" style="color: red;">{{ error }}</div>
    <div style="display: flex; gap: 8px; margin-bottom: 12px;">
      <select v-model="status" @change="load(0)">
        <option value="">전체 상태</option>
        <option value="ACTIVE">ACTIVE</option>
        <option value="LOCKED">LOCKED</option>
        <option value="DISABLED">DISABLED</option>
      </select>
      <input v-model="department" placeholder="부서" @change="load(0)" />
      <button @click="router.push({ name: 'admin-user-create' })">신규 사용자</button>
      <button @click="exportCsv">CSV 다운로드</button>
    </div>
    <table v-if="users" style="width: 100%; border-collapse: collapse;">
      <thead>
        <tr style="background: #f0f0f0;">
          <th style="padding: 6px;">ID</th><th>이름</th><th>이메일</th>
          <th>부서</th><th>상태</th><th>역할</th><th>최근 로그인</th><th>작업</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in users.content" :key="u.id" style="border-top: 1px solid #ddd;">
          <td style="padding: 6px;">{{ u.user_id }}</td>
          <td>{{ u.full_name }}</td>
          <td>{{ u.email }}</td>
          <td>{{ u.department }}</td>
          <td>{{ u.status }}</td>
          <td>{{ u.role_codes.join(', ') }}</td>
          <td>{{ u.last_login_at ?? '-' }}</td>
          <td>
            <button @click="router.push({ name: 'admin-user-edit', params: { userPk: u.id } })">수정</button>
            <button v-if="u.status !== 'DISABLED'" @click="handleDisable(u.user_id)">비활성화</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="users" style="margin-top: 12px;">
      <button :disabled="users.page === 0" @click="load(users.page - 1)">이전</button>
      <span style="margin: 0 8px;">{{ users.page + 1 }} / {{ users.totalPages }}</span>
      <button :disabled="users.page + 1 >= users.totalPages" @click="load(users.page + 1)">다음</button>
    </div>
  </main>
</template>
