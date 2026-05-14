import { defineStore } from 'pinia';
import { ref } from 'vue';
import { searchDocuments } from '../api/search';
import type { SearchResult, SearchParams } from '../api/search';

export const useSearchStore = defineStore('search', () => {
  const results = ref<SearchResult[]>([]);
  const totalElements = ref(0);
  const currentPage = ref(0);
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  const lastParams = ref<SearchParams | null>(null);

  async function search(params: SearchParams) {
    isLoading.value = true;
    error.value = null;
    lastParams.value = params;
    try {
      const page = await searchDocuments(params);
      results.value = page.content;
      totalElements.value = page.totalElements;
      currentPage.value = page.number;
    } catch (e) {
      error.value = e instanceof Error ? e.message : '검색 실패';
    } finally {
      isLoading.value = false;
    }
  }

  function reset() {
    results.value = [];
    totalElements.value = 0;
    currentPage.value = 0;
    lastParams.value = null;
  }

  return { results, totalElements, currentPage, isLoading, error, lastParams, search, reset };
});
