<script setup lang="ts">
import { ref, onMounted } from 'vue';
import UiPageHeader from '../../components/ui/UiPageHeader.vue';
import UiLoadingState from '../../components/ui/UiLoadingState.vue';
import UiErrorState from '../../components/ui/UiErrorState.vue';
import { listProjectTypes } from '../../api/researchProjects';
import type { ResearchProjectType } from '../../types';

const loading = ref(false);
const error = ref('');
const types = ref<ResearchProjectType[]>([]);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    types.value = await listProjectTypes();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '불러오기 실패';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <main style="max-width: 1100px; margin: 24px auto; font-family: inherit;">
    <UiPageHeader title="연구 과제 유형 관리" />

    <UiLoadingState v-if="loading" message="불러오는 중…" />
    <UiErrorState v-else-if="error" :error="error" :retry="load" />

    <table v-else style="width: 100%; border-collapse: collapse;">
      <thead>
        <tr style="background: #f0f0f0;">
          <th style="padding: 6px; text-align: left;">유형코드</th>
          <th style="padding: 6px; text-align: left;">유형명 (한)</th>
          <th style="padding: 6px; text-align: left;">유형명 (영)</th>
          <th style="padding: 6px; text-align: left;">보존연한</th>
          <th style="padding: 6px; text-align: left;">영구보존</th>
          <th style="padding: 6px; text-align: left;">SOP 행</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="t in types" :key="t.typeCode" style="border-top: 1px solid #ddd;">
          <td style="padding: 6px; font-family: monospace;">{{ t.typeCode }}</td>
          <td style="padding: 6px;">{{ t.typeNameKr }}</td>
          <td style="padding: 6px;">{{ t.typeNameEn ?? '-' }}</td>
          <td style="padding: 6px;">{{ t.retentionYears != null ? `${t.retentionYears}년` : '-' }}</td>
          <td style="padding: 6px;">{{ t.perpetual ? '영구' : '-' }}</td>
          <td style="padding: 6px;">{{ t.sopTableRow ?? '-' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!loading && !error && types.length === 0" style="color: gray;">
      등록된 유형이 없습니다.
    </p>
  </main>
</template>
