// frontend/src/stores/training.ts
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { listMyTraining, acknowledgeTraining } from '../api/training';
import type { TrainingAssignment } from '../api/training';

export const useTrainingStore = defineStore('training', () => {
  const assignments = ref<TrainingAssignment[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function fetchMyAssignments() {
    isLoading.value = true;
    error.value = null;
    try {
      assignments.value = await listMyTraining();
    } catch (e) {
      error.value = e instanceof Error ? e.message : '교육 과제를 불러오지 못했습니다.';
    } finally {
      isLoading.value = false;
    }
  }

  async function acknowledge(assignmentId: number) {
    await acknowledgeTraining(assignmentId);
    const item = assignments.value.find(a => a.id === assignmentId);
    if (item) {
      item.completed = true;
      item.completedAt = new Date().toISOString();
    }
  }

  const pendingCount = computed(() => assignments.value.filter(a => !a.completed).length);

  return { assignments, isLoading, error, fetchMyAssignments, acknowledge, pendingCount };
});
