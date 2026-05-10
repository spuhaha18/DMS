<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { documentsApi } from '../../api/documents';
import { useMasterDataStore } from '../../stores/masterData';
import type { NumberingTemplate } from '../../types';
import { numberingApi } from '../../api/numbering';

const router = useRouter();
const masterData = useMasterDataStore();

const selectedCategory = ref<number | null>(null);
const selectedCategoryCode = ref('');
const selectedDept = ref('');
const creatableDepts = ref<string[]>([]);
const title = ref('');
const projectCode = ref('');
const confidential = ref(false);
const templates = ref<NumberingTemplate[]>([]);
const error = ref('');
const loading = ref(false);

onMounted(async () => {
  await Promise.all([masterData.loadCategories(), masterData.loadDepartments()]);
  templates.value = await numberingApi.list();
});

function currentTemplate() {
  return templates.value.find((t) => t.categoryId === selectedCategory.value) ?? null;
}

function isPerProduct() {
  return currentTemplate()?.counterScope === 'PER_PRODUCT';
}

watch(selectedCategory, async (catId) => {
  selectedCategoryCode.value = masterData.categories.find((c) => c.id === catId)?.categoryCode ?? '';
  selectedDept.value = '';
  creatableDepts.value = [];
  if (catId) {
    try {
      creatableDepts.value = await documentsApi.listCreatableDepts(catId);
    } catch {
      // fallback: show all active departments
      creatableDepts.value = masterData.departments.filter((d) => d.active).map((d) => d.deptCode);
    }
  }
});

async function submit() {
  if (!selectedCategoryCode.value || !selectedDept.value || !title.value.trim()) {
    error.value = '카테고리, 부서, 제목을 모두 입력하세요.';
    return;
  }
  if (isPerProduct() && !projectCode.value.trim()) {
    error.value = 'PER_PRODUCT 카테고리는 프로젝트 코드가 필요합니다.';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const res = await documentsApi.create({
      categoryCode: selectedCategoryCode.value,
      department: selectedDept.value,
      projectCode: projectCode.value || null,
      title: title.value,
      confidential: confidential.value,
    });
    // Navigate to detail page — upload dialog is triggered there
    router.push({ name: 'document-detail', params: { docId: res.docId }, query: { upload: res.versionId.toString() } });
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } }; message?: string }).response?.data?.message
      ?? (e as { message?: string }).message ?? '오류가 발생했습니다.';
    loading.value = false;
  }
}
</script>

<template>
  <main style="max-width: 600px; margin: 40px auto; font-family: inherit;">
    <h1>새 문서 등록</h1>

    <div style="margin-bottom: 12px;">
      <label>카테고리<br/>
        <select v-model.number="selectedCategory" style="width: 100%;">
          <option :value="null">-- 선택 --</option>
          <option v-for="c in masterData.categories" :key="c.id" :value="c.id">
            {{ c.categoryCode }} — {{ c.categoryName }}
          </option>
        </select>
      </label>
    </div>

    <div style="margin-bottom: 12px;">
      <label>부서<br/>
        <select v-model="selectedDept" :disabled="!selectedCategory" style="width: 100%;">
          <option value="">-- 선택 --</option>
          <option v-for="dc in creatableDepts" :key="dc" :value="dc">{{ dc }}</option>
        </select>
      </label>
      <small v-if="creatableDepts.length === 0 && selectedCategory" style="color: orange;">
        이 카테고리에 대한 생성 권한이 있는 부서가 없습니다.
      </small>
    </div>

    <div v-if="isPerProduct()" style="margin-bottom: 12px;">
      <label>프로젝트 코드<br/>
        <input v-model="projectCode" placeholder="PROD123" style="width: 100%;" />
      </label>
    </div>

    <div style="margin-bottom: 12px;">
      <label>제목<br/>
        <input v-model="title" placeholder="문서 제목" style="width: 100%;" />
      </label>
    </div>

    <div style="margin-bottom: 16px;">
      <label><input type="checkbox" v-model="confidential" /> 기밀 문서</label>
    </div>

    <p v-if="error" style="color: red;">{{ error }}</p>

    <div style="display: flex; gap: 8px;">
      <button @click="router.back()">취소</button>
      <button @click="submit" :disabled="loading">
        {{ loading ? '처리 중...' : '문서 등록' }}
      </button>
    </div>
  </main>
</template>
