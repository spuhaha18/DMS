<script setup lang="ts">
import { ref, onMounted } from 'vue';
import UiPageHeader from '../../components/ui/UiPageHeader.vue';
import UiLoadingState from '../../components/ui/UiLoadingState.vue';
import UiErrorState from '../../components/ui/UiErrorState.vue';
import ResearchProjectStatusBadge from '../../components/admin/ResearchProjectStatusBadge.vue';
import ApproveProjectDialog from '../../components/admin/ApproveProjectDialog.vue';
import TerminateProjectDialog from '../../components/admin/TerminateProjectDialog.vue';
import { listProjects, approveProject, terminateProject } from '../../api/researchProjects';
import type { ResearchProject } from '../../types';

const loading = ref(false);
const error = ref('');
const projects = ref<ResearchProject[]>([]);
const approveTarget = ref<ResearchProject | null>(null);
const terminateTarget = ref<ResearchProject | null>(null);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    projects.value = await listProjects();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '불러오기 실패';
  } finally {
    loading.value = false;
  }
}

async function onApproveConfirmed(payload: { approvalDate: string }) {
  if (!approveTarget.value) return;
  try {
    await approveProject(approveTarget.value.projectCode, payload.approvalDate);
    approveTarget.value = null;
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '처리 실패';
    approveTarget.value = null;
  }
}

async function onTerminateConfirmed(payload: { terminationDate: string }) {
  if (!terminateTarget.value) return;
  try {
    await terminateProject(terminateTarget.value.projectCode, payload.terminationDate);
    terminateTarget.value = null;
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '처리 실패';
    terminateTarget.value = null;
  }
}

onMounted(load);
</script>

<template>
  <main style="max-width: 1100px; margin: 24px auto; font-family: inherit;">
    <UiPageHeader title="연구 과제 관리" />

    <UiLoadingState v-if="loading" message="불러오는 중…" />
    <UiErrorState v-else-if="error" :error="error" :retry="load" />

    <table v-else style="width: 100%; border-collapse: collapse;">
      <thead>
        <tr style="background: #f0f0f0;">
          <th style="padding: 6px; text-align: left;">과제코드</th>
          <th style="padding: 6px; text-align: left;">과제명</th>
          <th style="padding: 6px; text-align: left;">유형</th>
          <th style="padding: 6px; text-align: left;">상태</th>
          <th style="padding: 6px;"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in projects" :key="p.projectCode" style="border-top: 1px solid #ddd;">
          <td style="padding: 6px; font-family: monospace;">{{ p.projectCode }}</td>
          <td style="padding: 6px;">{{ p.projectName }}</td>
          <td style="padding: 6px;">{{ p.typeNameKr }}</td>
          <td style="padding: 6px;">
            <ResearchProjectStatusBadge :status="p.status" />
          </td>
          <td style="padding: 6px; display: flex; gap: 6px;">
            <button
              v-if="p.status === 'ACTIVE'"
              @click="approveTarget = p"
              style="font-size: 12px;"
            >품목허가</button>
            <button
              v-if="p.status === 'ACTIVE'"
              @click="terminateTarget = p"
              style="font-size: 12px;"
            >중단/종료</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!loading && !error && projects.length === 0" style="color: gray;">
      등록된 연구 과제가 없습니다.
    </p>

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
  </main>
</template>
