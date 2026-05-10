import { defineStore } from 'pinia';
import { ref } from 'vue';
import { documentsApi } from '../api/documents';
import type { DocumentSummary, PageResponse } from '../types';

export const useDocumentsStore = defineStore('documents', () => {
  const page = ref<PageResponse<DocumentSummary> | null>(null);

  async function load(params: { categoryCode?: string; department?: string; state?: string; page?: number; size?: number } = {}) {
    page.value = await documentsApi.list({ size: 20, ...params });
  }

  return { page, load };
});
