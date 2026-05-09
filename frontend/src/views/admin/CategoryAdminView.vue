<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { categoriesApi } from '../../api/categories';
import type { DocumentCategory } from '../../types';

const cats = ref<DocumentCategory[]>([]);
const showForm = ref(false);
const editing = ref<DocumentCategory | null>(null);
const form = ref({
  categoryCode: '', categoryName: '', description: '',
  reviewPeriodMonths: 24, qaMandatory: true, active: true,
});
const error = ref('');

async function load() {
  cats.value = await categoriesApi.list();
}
onMounted(load);

function openCreate() {
  editing.value = null;
  form.value = { categoryCode: '', categoryName: '', description: '', reviewPeriodMonths: 24, qaMandatory: true, active: true };
  showForm.value = true;
  error.value = '';
}

function openEdit(c: DocumentCategory) {
  editing.value = c;
  form.value = {
    categoryCode: c.categoryCode,
    categoryName: c.categoryName,
    description: c.description ?? '',
    reviewPeriodMonths: c.reviewPeriodMonths,
    qaMandatory: c.qaMandatory,
    active: c.active,
  };
  showForm.value = true;
  error.value = '';
}

async function save() {
  if (!form.value.categoryCode.trim() || !form.value.categoryName.trim()) {
    error.value = '코드와 이름을 입력하세요.';
    return;
  }
  try {
    const req = {
      ...form.value,
      description: form.value.description || null,
    };
    if (editing.value) {
      await categoriesApi.update(editing.value.id, req);
    } else {
      await categoriesApi.create(req);
    }
    showForm.value = false;
    await load();
  } catch (e: unknown) {
    error.value = (e as { message?: string }).message ?? '저장 오류';
  }
}
</script>

<template>
  <main style="max-width: 900px; margin: 24px auto; font-family: inherit;">
    <h1>문서 카테고리 관리</h1>
    <button @click="openCreate" style="margin-bottom: 12px;">+ 카테고리 추가</button>

    <table style="width: 100%; border-collapse: collapse;">
      <thead>
        <tr style="background: #f0f0f0;">
          <th style="padding: 6px;">코드</th>
          <th>이름</th>
          <th>검토주기(월)</th>
          <th>QA필수</th>
          <th>상태</th>
          <th>작업</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in cats" :key="c.id" style="border-top: 1px solid #ddd;">
          <td style="padding: 6px;">{{ c.categoryCode }}</td>
          <td>{{ c.categoryName }}</td>
          <td style="text-align: center;">{{ c.reviewPeriodMonths }}</td>
          <td style="text-align: center;">{{ c.qaMandatory ? '✓' : '' }}</td>
          <td>
            <span :style="{ color: c.active ? 'green' : 'gray' }">
              {{ c.active ? '활성' : '비활성' }}
            </span>
          </td>
          <td>
            <button @click="openEdit(c)">편집</button>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- 생성/편집 모달 -->
    <div
      v-if="showForm"
      style="position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center;"
    >
      <div style="background: white; padding: 24px; border-radius: 8px; min-width: 360px;">
        <h2>{{ editing ? '카테고리 편집' : '카테고리 추가' }}</h2>
        <div style="margin-bottom: 10px;">
          <label>카테고리코드<br/>
            <input v-model="form.categoryCode" :disabled="!!editing" placeholder="SOP" style="width: 100%;" />
          </label>
        </div>
        <div style="margin-bottom: 10px;">
          <label>카테고리명<br/>
            <input v-model="form.categoryName" placeholder="표준작업절차서" style="width: 100%;" />
          </label>
        </div>
        <div style="margin-bottom: 10px;">
          <label>설명(선택)<br/>
            <input v-model="form.description" placeholder="" style="width: 100%;" />
          </label>
        </div>
        <div style="display: flex; gap: 12px; margin-bottom: 10px;">
          <label>검토주기(월)<br/>
            <input v-model.number="form.reviewPeriodMonths" type="number" min="1" style="width: 80px;" />
          </label>
          <label style="align-self: flex-end;">
            <input type="checkbox" v-model="form.qaMandatory" /> QA 필수
          </label>
          <label style="align-self: flex-end;">
            <input type="checkbox" v-model="form.active" /> 활성
          </label>
        </div>
        <p v-if="error" style="color: red;">{{ error }}</p>
        <div style="display: flex; gap: 8px; justify-content: flex-end;">
          <button @click="showForm = false">취소</button>
          <button @click="save">저장</button>
        </div>
      </div>
    </div>
  </main>
</template>
