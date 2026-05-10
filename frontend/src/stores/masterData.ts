import { defineStore } from 'pinia';
import { ref } from 'vue';
import { categoriesApi } from '../api/categories';
import { departmentsApi } from '../api/departments';
import type { DocumentCategory, Department } from '../types';

const TTL_MS = 5 * 60 * 1000;

export const useMasterDataStore = defineStore('masterData', () => {
  const categories = ref<DocumentCategory[]>([]);
  const departments = ref<Department[]>([]);
  let catsLoadedAt = 0;
  let deptsLoadedAt = 0;

  async function loadCategories(force = false) {
    if (!force && categories.value.length && Date.now() - catsLoadedAt < TTL_MS) return;
    categories.value = await categoriesApi.listPublic();
    catsLoadedAt = Date.now();
  }

  async function loadDepartments(force = false) {
    if (!force && departments.value.length && Date.now() - deptsLoadedAt < TTL_MS) return;
    departments.value = await departmentsApi.listPublic();
    deptsLoadedAt = Date.now();
  }

  return { categories, departments, loadCategories, loadDepartments };
});
