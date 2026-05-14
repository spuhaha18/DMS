<!-- frontend/src/views/TrainingListView.vue -->
<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useTrainingStore } from '../stores/training';

const router = useRouter();
const store = useTrainingStore();

onMounted(() => store.fetchMyAssignments());

function openDocument(docId: number, versionId: number) {
  router.push({ name: 'document-pdf-view', params: { docId: String(docId), verId: String(versionId) } });
}
</script>

<template>
  <div class="training-list">
    <h1>내 교육 과제</h1>
    <p v-if="store.isLoading">불러오는 중...</p>
    <p v-else-if="store.error" role="alert">{{ store.error }}</p>
    <template v-else>
      <p v-if="store.assignments.length === 0">교육 과제가 없습니다.</p>
      <table v-else class="training-table">
        <thead>
          <tr>
            <th>문서번호</th>
            <th>문서명</th>
            <th>완료 기한</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in store.assignments"
            :key="item.id"
            :class="{ completed: item.completed }"
            style="cursor:pointer"
            tabindex="0"
            @click="openDocument(item.docId, item.versionId)"
            @keydown.enter="openDocument(item.docId, item.versionId)"
          >
            <td>{{ item.docNumber }}</td>
            <td>{{ item.docTitle }}</td>
            <td>{{ item.dueAt ? new Date(item.dueAt).toLocaleDateString('ko-KR') : '-' }}</td>
            <td>
              <span v-if="item.completed" class="badge badge-done">✓ 완료</span>
              <span v-else class="badge badge-pending">미완료</span>
            </td>
          </tr>
        </tbody>
      </table>
    </template>
  </div>
</template>

<style scoped>
.training-list {
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

.training-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.training-table th,
.training-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

.training-table th {
  background: #f9fafb;
  font-weight: 600;
  color: #374151;
}

.training-table tr:hover {
  background: #f3f4f6;
}

.training-table tr.completed {
  color: #6b7280;
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
