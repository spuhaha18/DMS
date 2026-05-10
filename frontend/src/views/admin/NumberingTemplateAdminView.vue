<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { numberingApi } from '../../api/numbering';
import { categoriesApi } from '../../api/categories';
import type { NumberingTemplate, DocumentCategory } from '../../types';

const templates = ref<NumberingTemplate[]>([]);
const categories = ref<DocumentCategory[]>([]);
const showForm = ref(false);
const editing = ref<NumberingTemplate | null>(null);
const form = ref({ categoryId: 0, formatPattern: '', counterScope: 'PER_DEPT' as NumberingTemplate['counterScope'] });
const error = ref('');

// Preview
const previewCategoryId = ref<number | null>(null);
const previewDept = ref('');
const previewProject = ref('');
const previewResult = ref('');

const SCOPES: NumberingTemplate['counterScope'][] = ['PER_DEPT', 'PER_PRODUCT', 'PER_YEAR', 'GLOBAL'];
const PLACEHOLDERS = ['{TYPE}', '{DEPT}', '{PROD}', '{YEAR}', '{SEQ:3}', '{SEQ:4}'];

async function load() {
  [templates.value, categories.value] = await Promise.all([
    numberingApi.list(),
    categoriesApi.list(),
  ]);
  if (!previewCategoryId.value && categories.value.length) {
    previewCategoryId.value = categories.value[0].id;
  }
}
onMounted(load);

function openCreate() {
  editing.value = null;
  form.value = { categoryId: categories.value[0]?.id ?? 0, formatPattern: '', counterScope: 'PER_DEPT' };
  showForm.value = true;
  error.value = '';
}

function openEdit(t: NumberingTemplate) {
  editing.value = t;
  form.value = { categoryId: t.categoryId, formatPattern: t.formatPattern, counterScope: t.counterScope };
  showForm.value = true;
  error.value = '';
}

function insertPlaceholder(ph: string) {
  form.value.formatPattern += ph;
}

async function save() {
  if (!form.value.formatPattern.trim()) {
    error.value = '패턴을 입력하세요.';
    return;
  }
  try {
    if (editing.value) {
      await numberingApi.update(editing.value.id, form.value);
    } else {
      await numberingApi.create(form.value);
    }
    showForm.value = false;
    await load();
  } catch (e: unknown) {
    error.value = (e as { message?: string }).message ?? '저장 오류';
  }
}

async function runPreview() {
  if (!previewCategoryId.value) return;
  previewResult.value = '조회 중...';
  try {
    const res = await numberingApi.preview({
      categoryId: previewCategoryId.value,
      department: previewDept.value || null,
      projectCode: previewProject.value || null,
    });
    previewResult.value = `다음 번호: ${res.nextDocNumber}  (seq=${res.nextSeq})`;
  } catch (e: unknown) {
    previewResult.value = `오류: ${(e as { message?: string }).message ?? '알 수 없음'}`;
  }
}

function categoryLabel(id: number) {
  return categories.value.find((c) => c.id === id)?.categoryCode ?? String(id);
}
</script>

<template>
  <main style="max-width: 1000px; margin: 24px auto; font-family: inherit;">
    <h1>채번 템플릿 관리</h1>

    <!-- 미리보기 패널 -->
    <div style="border: 1px solid #ccc; border-radius: 6px; padding: 16px; margin-bottom: 20px;">
      <strong>채번 미리보기</strong>
      <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; align-items: flex-end;">
        <label>카테고리<br/>
          <select v-model.number="previewCategoryId">
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.categoryCode }}</option>
          </select>
        </label>
        <label>부서코드<br/>
          <input v-model="previewDept" placeholder="QC" style="width: 80px;" />
        </label>
        <label>프로젝트코드<br/>
          <input v-model="previewProject" placeholder="PROD123" style="width: 100px;" />
        </label>
        <button @click="runPreview">미리보기</button>
        <span v-if="previewResult" style="color: #0066cc; font-weight: bold;">{{ previewResult }}</span>
      </div>
    </div>

    <button @click="openCreate" style="margin-bottom: 12px;">+ 템플릿 추가</button>

    <table style="width: 100%; border-collapse: collapse;">
      <thead>
        <tr style="background: #f0f0f0;">
          <th style="padding: 6px;">카테고리</th>
          <th>패턴</th>
          <th>범위</th>
          <th>최근 수정</th>
          <th>작업</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="t in templates" :key="t.id" style="border-top: 1px solid #ddd;">
          <td style="padding: 6px;">{{ t.categoryCode }}</td>
          <td><code>{{ t.formatPattern }}</code></td>
          <td>{{ t.counterScope }}</td>
          <td>{{ t.updatedAt ? new Date(t.updatedAt).toLocaleDateString('ko-KR') : '-' }}</td>
          <td>
            <button @click="openEdit(t)">편집</button>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- 생성/편집 모달 -->
    <div
      v-if="showForm"
      style="position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center;"
    >
      <div style="background: white; padding: 24px; border-radius: 8px; min-width: 420px;">
        <h2>{{ editing ? '템플릿 편집' : '템플릿 추가' }}</h2>
        <div style="margin-bottom: 10px;">
          <label>카테고리<br/>
            <select v-model.number="form.categoryId" :disabled="!!editing" style="width: 100%;">
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.categoryCode }} — {{ c.categoryName }}</option>
            </select>
          </label>
        </div>
        <div style="margin-bottom: 6px;">
          <label>카운터 범위<br/>
            <select v-model="form.counterScope" style="width: 100%;">
              <option v-for="s in SCOPES" :key="s" :value="s">{{ s }}</option>
            </select>
          </label>
        </div>
        <div style="margin-bottom: 6px;">
          <label>패턴<br/>
            <input v-model="form.formatPattern" placeholder="{TYPE}-{DEPT}-{SEQ:3}" style="width: 100%;" />
          </label>
        </div>
        <div style="margin-bottom: 10px;">
          <small>플레이스홀더:</small><br/>
          <button
            v-for="ph in PLACEHOLDERS"
            :key="ph"
            @click="insertPlaceholder(ph)"
            style="margin: 2px; font-size: 11px; padding: 2px 6px;"
          >{{ ph }}</button>
        </div>
        <p v-if="form.categoryId" style="color: #555; font-size: 12px;">
          카테고리: {{ categoryLabel(form.categoryId) }}
        </p>
        <p v-if="error" style="color: red;">{{ error }}</p>
        <div style="display: flex; gap: 8px; justify-content: flex-end;">
          <button @click="showForm = false">취소</button>
          <button @click="save">저장</button>
        </div>
      </div>
    </div>
  </main>
</template>
