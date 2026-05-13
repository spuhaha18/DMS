<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { listProjects, approveProject, terminateProject } from '../../api/researchProjects';
import type { ResearchProject } from '../../types';
import UiPageHeader from '../../components/ui/UiPageHeader.vue';
import UiLoadingState from '../../components/ui/UiLoadingState.vue';
import UiErrorState from '../../components/ui/UiErrorState.vue';
import UiEmptyState from '../../components/ui/UiEmptyState.vue';
import UiDataTable from '../../components/ui/UiDataTable.vue';
import UiBanner from '../../components/ui/UiBanner.vue';
import ResearchProjectStatusBadge from '../../components/admin/ResearchProjectStatusBadge.vue';
import ApproveProjectDialog from '../../components/admin/ApproveProjectDialog.vue';
import TerminateProjectDialog from '../../components/admin/TerminateProjectDialog.vue';

const projects = ref<ResearchProject[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const approveTarget = ref<ResearchProject | null>(null);
const terminateTarget = ref<ResearchProject | null>(null);
const actionError = ref<string | null>(null);

async function load() {
  loading.value = true;
  error.value = null;
  try {
    projects.value = await listProjects();
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

async function onApproveConfirmed({ approvalDate }: { approvalDate: string }) {
  if (!approveTarget.value) return;
  try {
    const updated = await approveProject(approveTarget.value.projectCode, approvalDate);
    const idx = projects.value.findIndex(p => p.projectCode === updated.projectCode);
    if (idx !== -1) projects.value[idx] = updated;
    approveTarget.value = null;
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : '품목허가 처리에 실패했습니다.';
    approveTarget.value = null;
  }
}

async function onTerminateConfirmed({ terminationDate }: { terminationDate: string }) {
  if (!terminateTarget.value) return;
  try {
    const updated = await terminateProject(terminateTarget.value.projectCode, terminationDate);
    const idx = projects.value.findIndex(p => p.projectCode === updated.projectCode);
    if (idx !== -1) projects.value[idx] = updated;
    terminateTarget.value = null;
  } catch (e: unknown) {
    actionError.value = e instanceof Error ? e.message : '중단/종료 처리에 실패했습니다.';
    terminateTarget.value = null;
  }
}

onMounted(load);
</script>

<template>
  <div style="max-width: 1100px; margin: 24px auto;">
    <UiPageHeader title="연구과제 관리" />

    <UiBanner v-if="actionError" type="error" :dismissible="true" style="margin-bottom: 1rem;">
      {{ actionError }}
    </UiBanner>

    <UiLoadingState v-if="loading" message="목록을 불러오는 중…" />
    <UiErrorState v-else-if="error" :error="error" :retry="load" />
    <UiEmptyState
      v-else-if="!projects.length"
      message="등록된 연구과제가 없습니다."
      icon="📂"
    />
    <UiDataTable
      v-else
      :columns="[
        { key: 'projectCode', label: '과제코드' },
        { key: 'projectName', label: '과제명' },
        { key: 'typeNameKr', label: '시험 종류' },
        { key: 'status', label: '상태' },
        { key: '_actions', label: '작업' },
      ]"
      :rows="(projects as unknown as Record<string, unknown>[])"
      row-key="projectCode"
    >
      <template #status="{ row }">
        <ResearchProjectStatusBadge :status="(row as unknown as ResearchProject).status" />
      </template>
      <template #_actions="{ row }">
        <div style="display: flex; gap: 6px;">
          <button
            v-if="(row as unknown as ResearchProject).status === 'ACTIVE'"
            @click="approveTarget = (row as unknown as ResearchProject)"
            class="action-btn"
          >품목허가</button>
          <button
            v-if="(row as unknown as ResearchProject).status === 'ACTIVE'"
            @click="terminateTarget = (row as unknown as ResearchProject)"
            class="action-btn action-btn--danger"
          >중단/종료</button>
        </div>
      </template>
    </UiDataTable>

    <ApproveProjectDialog
      v-if="approveTarget"
      :project="approveTarget"
      :model-value="true"
      @update:model-value="approveTarget = null"
      @confirmed="onApproveConfirmed"
    />

    <TerminateProjectDialog
      v-if="terminateTarget"
      :project="terminateTarget"
      :model-value="true"
      @update:model-value="terminateTarget = null"
      @confirmed="onTerminateConfirmed"
    />
  </div>
</template>

<style scoped>
.action-btn {
  padding: 0.25rem 0.625rem;
  font-size: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  background: #fff;
  cursor: pointer;
  color: #374151;
  font-family: inherit;
  white-space: nowrap;
}
.action-btn:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}
.action-btn--danger {
  color: #b91c1c;
  border-color: #fca5a5;
}
.action-btn--danger:hover {
  background: #fef2f2;
  border-color: #f87171;
}
</style>
