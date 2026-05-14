<script setup lang="ts">
import { watch, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useSearchStore } from '../stores/search';

const route = useRoute();
const router = useRouter();
const store = useSearchStore();

function runSearch(page = 0) {
  const q = String(route.query.q ?? '').trim();
  if (q.length < 2) return;
  store.search({ q, page, size: 20 });
}

onMounted(() => runSearch());
watch(() => route.query.q, () => runSearch());

function goToDocument(docId: number, versionId: number) {
  router.push({ name: 'document-pdf-view', params: { docId, verId: versionId } });
}
</script>

<template>
  <div class="search-view">
    <h1>검색 결과: "{{ route.query.q }}"</h1>
    <p v-if="store.isLoading">로딩 중...</p>
    <p v-else-if="store.error" role="alert">{{ store.error }}</p>
    <p v-else-if="store.results.length === 0">검색 결과가 없습니다.</p>
    <template v-else>
      <p>총 {{ store.totalElements }}건</p>
      <ul class="result-list">
        <li
          v-for="item in store.results"
          :key="item.versionId"
          class="result-item"
          style="cursor:pointer"
          tabindex="0"
          @click="goToDocument(item.documentId, item.versionId)"
          @keydown.enter="goToDocument(item.documentId, item.versionId)"
        >
          <span class="doc-number">{{ item.docNumber }}</span>
          <span class="doc-title">{{ item.title }}</span>
          <span class="doc-state">{{ item.state }}</span>
          <span class="doc-dept">{{ item.department }}</span>
        </li>
      </ul>
    </template>
  </div>
</template>
