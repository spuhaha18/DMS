<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { documentsApi } from '../../api/documents';
import { useMasterDataStore } from '../../stores/masterData';
import type { DocumentSummary, PageResponse } from '../../types';

const router = useRouter();
const masterData = useMasterDataStore();
const page = ref<PageResponse<DocumentSummary> | null>(null);
const filterCat = ref('');
const filterDept = ref('');
const filterState = ref('');
const currentPage = ref(0);

async function load(p = 0) {
  currentPage.value = p;
  page.value = await documentsApi.list({
    categoryCode: filterCat.value || undefined,
    department: filterDept.value || undefined,
    state: filterState.value || undefined,
    page: p, size: 20,
  });
}

onMounted(async () => {
  await masterData.loadCategories();
  await load(0);
});
</script>

<template>
  <main style="max-width: 1100px; margin: 24px auto; font-family: inherit;">
    <h1>문서 목록</h1>
    <div style="display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: flex-end;">
      <select v-model="filterCat" @change="load(0)">
        <option value="">전체 카테고리</option>
        <option v-for="c in masterData.categories" :key="c.id" :value="c.categoryCode">
          {{ c.categoryCode }}
        </option>
      </select>
      <input v-model="filterDept" placeholder="부서" @change="load(0)" style="width: 80px;" />
      <select v-model="filterState" @change="load(0)">
        <option value="">전체 상태</option>
        <option value="DRAFT">DRAFT</option>
      </select>
      <button @click="router.push({ name: 'document-create' })">+ 문서 등록</button>
    </div>

    <table v-if="page" style="width: 100%; border-collapse: collapse;">
      <thead>
        <tr style="background: #f0f0f0;">
          <th style="padding: 6px;">문서번호</th><th>제목</th><th>카테고리</th>
          <th>부서</th><th>상태</th><th>등록일</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="d in page.content" :key="d.id" style="border-top: 1px solid #ddd;">
          <td style="padding: 6px; font-family: monospace;">{{ d.docNumber }}</td>
          <td>{{ d.title }}</td>
          <td>{{ d.categoryCode }}</td>
          <td>{{ d.department }}</td>
          <td><span style="font-size: 12px; background: #e8e8e8; padding: 2px 6px; border-radius: 4px;">DRAFT</span></td>
          <td>{{ new Date(d.createdAt).toLocaleDateString('ko-KR') }}</td>
          <td><button @click="router.push({ name: 'document-detail', params: { docId: d.id } })">상세</button></td>
        </tr>
      </tbody>
    </table>
    <p v-if="page && page.content.length === 0" style="color: gray;">조회 결과가 없습니다.</p>

    <div v-if="page && page.totalPages > 1" style="margin-top: 12px; display: flex; gap: 8px;">
      <button :disabled="currentPage === 0" @click="load(currentPage - 1)">◀ 이전</button>
      <span>{{ currentPage + 1 }} / {{ page.totalPages }}</span>
      <button :disabled="currentPage >= page.totalPages - 1" @click="load(currentPage + 1)">다음 ▶</button>
    </div>
  </main>
</template>
