<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { listProjectTypes } from '../../api/researchProjects';
import type { ResearchProjectType } from '../../types';
import UiPageHeader from '../../components/ui/UiPageHeader.vue';
import UiLoadingState from '../../components/ui/UiLoadingState.vue';
import UiErrorState from '../../components/ui/UiErrorState.vue';
import UiDataTable from '../../components/ui/UiDataTable.vue';
import UiBadge from '../../components/ui/UiBadge.vue';

const types = ref<ResearchProjectType[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

async function load() {
  loading.value = true;
  error.value = null;
  try {
    types.value = await listProjectTypes();
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '목록을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div style="max-width: 1100px; margin: 24px auto;">
    <UiPageHeader
      title="시험 종류 마스터"
      subtitle="SOP §6.2.2 Table 1 기반 보존정책"
    />

    <UiLoadingState v-if="loading" message="목록을 불러오는 중…" />
    <UiErrorState v-else-if="error" :error="error" :retry="load" />
    <UiDataTable
      v-else
      :columns="[
        { key: 'typeCode', label: '종류 코드' },
        { key: 'typeNameKr', label: '시험 종류 (한국어)' },
        { key: 'retentionYears', label: '보존기간' },
        { key: '_note', label: '비고' },
      ]"
      :rows="(types as unknown as Record<string, unknown>[])"
      row-key="typeCode"
    >
      <template #retentionYears="{ row }">
        <span v-if="(row as unknown as ResearchProjectType).perpetual">영구 (99년)</span>
        <span v-else>{{ (row as unknown as ResearchProjectType).retentionYears }}년</span>
      </template>
      <template #_note="{ row }">
        <span>{{ (row as unknown as ResearchProjectType).note }}</span>
        <UiBadge
          v-if="(row as unknown as ResearchProjectType).note?.startsWith('TBD')"
          variant="warning"
          title="QA 확인 대기 중 — 임시값입니다"
          style="margin-left: 0.375rem;"
        >⚠️ 임시값</UiBadge>
      </template>
    </UiDataTable>
  </div>
</template>
