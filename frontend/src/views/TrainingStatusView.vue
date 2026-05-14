<!-- frontend/src/views/TrainingStatusView.vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { getTrainingStatus } from '../api/training';
import type { TrainingStatus } from '../api/training';

const route = useRoute();
const statuses = ref<TrainingStatus[]>([]);
const isLoading = ref(false);
const error = ref<string | null>(null);

const versionId = computed(() => Number(route.params.versionId));
const completionRate = computed(() => {
  if (statuses.value.length === 0) return 0;
  const completed = statuses.value.filter(s => s.completed).length;
  return Math.round((completed / statuses.value.length) * 100);
});

onMounted(async () => {
  isLoading.value = true;
  try {
    statuses.value = await getTrainingStatus(versionId.value);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '이수 현황을 불러오지 못했습니다.';
  } finally {
    isLoading.value = false;
  }
});
</script>

<template>
  <div class="training-status">
    <h1>교육 이수 현황</h1>
    <p v-if="isLoading">불러오는 중...</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <template v-else>
      <p class="completion-rate">이수율: <strong>{{ completionRate }}%</strong></p>
      <p v-if="statuses.length === 0">이수 현황 데이터가 없습니다.</p>
      <table v-else class="status-table">
        <thead>
          <tr>
            <th>사용자</th>
            <th>배정일</th>
            <th>완료일</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in statuses" :key="s.assignmentId">
            <td>{{ s.userDisplayName }}</td>
            <td>{{ new Date(s.assignedAt).toLocaleDateString('ko-KR') }}</td>
            <td>{{ s.completedAt ? new Date(s.completedAt).toLocaleDateString('ko-KR') : '-' }}</td>
            <td>
              <span v-if="s.completed" class="badge badge-done">✓ 완료</span>
              <span v-else class="badge badge-pending">미완료</span>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
  </div>
</template>

<style scoped>
.training-status {
  max-width: 960px;
  margin: 24px auto;
  padding: 0 16px 32px;
}

h1 {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 16px;
}

.completion-rate {
  font-size: 15px;
  color: #374151;
  margin-bottom: 12px;
}

.status-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.status-table th,
.status-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

.status-table th {
  background: #f9fafb;
  font-weight: 600;
  color: #374151;
}

.badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}

.badge-done {
  background: #dcfce7;
  color: #166534;
}

.badge-pending {
  background: #fef3c7;
  color: #92400e;
}
</style>
