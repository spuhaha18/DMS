<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { departmentsApi } from '../../api/departments';
import type { Department } from '../../types';

const depts = ref<Department[]>([]);
const showForm = ref(false);
const editing = ref<Department | null>(null);
const formCode = ref('');
const formName = ref('');
const aliasForm = ref<{ deptId: number; name: string; locale: string } | null>(null);
const error = ref('');

async function load() {
  depts.value = await departmentsApi.list();
}
onMounted(load);

function openCreate() {
  editing.value = null;
  formCode.value = '';
  formName.value = '';
  showForm.value = true;
  error.value = '';
}

function openEdit(d: Department) {
  editing.value = d;
  formCode.value = d.deptCode;
  formName.value = d.primaryName;
  showForm.value = true;
  error.value = '';
}

async function save() {
  if (!formCode.value.trim() || !formName.value.trim()) {
    error.value = '부서코드와 이름을 입력하세요.';
    return;
  }
  try {
    if (editing.value) {
      await departmentsApi.update(editing.value.id, {
        deptCode: formCode.value, primaryName: formName.value,
      });
    } else {
      await departmentsApi.create({ deptCode: formCode.value, primaryName: formName.value });
    }
    showForm.value = false;
    await load();
  } catch (e: unknown) {
    error.value = (e as { message?: string }).message ?? '저장 오류';
  }
}

async function deactivate(d: Department) {
  if (!confirm(`[${d.deptCode}] 비활성화하시겠습니까?`)) return;
  await departmentsApi.deactivate(d.id);
  await load();
}

function openAlias(deptId: number) {
  aliasForm.value = { deptId, name: '', locale: '' };
}

async function addAlias() {
  if (!aliasForm.value || !aliasForm.value.name.trim()) return;
  await departmentsApi.addAlias(aliasForm.value.deptId, {
    aliasName: aliasForm.value.name,
    locale: aliasForm.value.locale || null,
  });
  aliasForm.value = null;
  await load();
}

async function deleteAlias(deptId: number, aliasId: number) {
  await departmentsApi.deleteAlias(deptId, aliasId);
  await load();
}
</script>

<template>
  <main style="max-width: 1000px; margin: 24px auto; font-family: inherit;">
    <h1>부서 관리</h1>
    <button @click="openCreate" style="margin-bottom: 12px;">+ 부서 추가</button>

    <!-- 부서 목록 -->
    <table style="width: 100%; border-collapse: collapse;">
      <thead>
        <tr style="background: #f0f0f0;">
          <th style="padding: 6px;">코드</th>
          <th>이름</th>
          <th>소스</th>
          <th>상태</th>
          <th>별칭</th>
          <th>작업</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="d in depts" :key="d.id">
          <tr style="border-top: 1px solid #ddd;">
            <td style="padding: 6px;">{{ d.deptCode }}</td>
            <td>{{ d.primaryName }}</td>
            <td>{{ d.source }}</td>
            <td>
              <span :style="{ color: d.active ? 'green' : 'gray' }">
                {{ d.active ? '활성' : '비활성' }}
              </span>
            </td>
            <td>
              <span v-for="a in d.aliases" :key="a.id" style="margin-right: 4px;">
                {{ a.aliasName }}<span v-if="a.locale">({{ a.locale }})</span>
                <button
                  @click="deleteAlias(d.id, a.id)"
                  style="font-size: 10px; margin-left: 2px; padding: 0 3px;"
                >✕</button>
              </span>
              <button @click="openAlias(d.id)" style="font-size: 11px; padding: 1px 6px;">
                + 별칭
              </button>
            </td>
            <td>
              <button @click="openEdit(d)" style="margin-right: 4px;">편집</button>
              <button v-if="d.active" @click="deactivate(d)">비활성화</button>
            </td>
          </tr>
          <!-- 별칭 추가 인라인 폼 -->
          <tr v-if="aliasForm && aliasForm.deptId === d.id" style="background: #fffbea;">
            <td colspan="6" style="padding: 6px;">
              <input v-model="aliasForm.name" placeholder="별칭명" style="margin-right: 6px;" />
              <input v-model="aliasForm.locale" placeholder="locale(ko/en)" style="width: 100px; margin-right: 6px;" />
              <button @click="addAlias">저장</button>
              <button @click="aliasForm = null" style="margin-left: 4px;">취소</button>
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <!-- 부서 생성/편집 모달 -->
    <div
      v-if="showForm"
      style="position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center;"
    >
      <div style="background: white; padding: 24px; border-radius: 8px; min-width: 340px;">
        <h2>{{ editing ? '부서 편집' : '부서 추가' }}</h2>
        <div style="margin-bottom: 10px;">
          <label>부서코드<br/>
            <input v-model="formCode" :disabled="!!editing" placeholder="QC" style="width: 100%;" />
          </label>
        </div>
        <div style="margin-bottom: 10px;">
          <label>이름<br/>
            <input v-model="formName" placeholder="품질관리팀" style="width: 100%;" />
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
