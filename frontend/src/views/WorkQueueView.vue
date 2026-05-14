<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useWorkQueueStore } from '../stores/workQueue';

const store = useWorkQueueStore();

const KINDS = [
  { value: 'APPROVAL', label: '결재' },
  { value: 'TRAINING', label: '교육' },
  { value: 'PERIODIC_REVIEW', label: '정기검토' },
  { value: 'READACK', label: '열람확인' },
];

const activeKind = ref<string | null>(null);  // null = all kinds
const stateFilter = ref<'OPEN' | 'DONE' | 'ALL'>('OPEN');

const filteredItems = computed(() =>
  store.items.filter(item =>
    (activeKind.value === null || item.kind === activeKind.value) &&
    (stateFilter.value === 'ALL' || item.state === stateFilter.value)
  )
);

async function load() {
  await store.fetchList({
    kind: activeKind.value ?? undefined,
    state: stateFilter.value === 'ALL' ? undefined : stateFilter.value,
  });
}

onMounted(load);
watch([activeKind, stateFilter], load);

async function handleDone(id: number) {
  await store.markDone(id);
  await load();
}
</script>

<template>
  <div class="wq-view">
    <h1 class="wq-title">내 할 일</h1>

    <!-- Tabs by kind -->
    <div class="wq-tabs" role="tablist">
      <button
        role="tab"
        :aria-selected="activeKind === null"
        class="wq-tab"
        :class="{ active: activeKind === null }"
        @click="activeKind = null"
      >전체</button>
      <button
        v-for="k in KINDS"
        :key="k.value"
        role="tab"
        :aria-selected="activeKind === k.value"
        class="wq-tab"
        :class="{ active: activeKind === k.value }"
        @click="activeKind = k.value"
      >{{ k.label }}</button>
    </div>

    <!-- State filter -->
    <div class="wq-filters">
      <label>
        <select v-model="stateFilter" class="wq-select">
          <option value="OPEN">처리 중</option>
          <option value="DONE">완료</option>
          <option value="ALL">전체</option>
        </select>
      </label>
    </div>

    <!-- Loading -->
    <div v-if="store.isLoading" class="wq-loading" aria-live="polite">로딩 중...</div>

    <!-- Error -->
    <div v-else-if="store.error" class="wq-error" role="alert">
      {{ store.error }}
    </div>

    <!-- Empty -->
    <div v-else-if="filteredItems.length === 0" class="wq-empty">
      {{ stateFilter === 'OPEN' ? '처리할 항목이 없습니다.' : '항목이 없습니다.' }}
    </div>

    <!-- Items -->
    <ul v-else class="wq-list">
      <li
        v-for="item in filteredItems"
        :key="item.id"
        class="wq-item"
        :class="{ 'is-done': item.state === 'DONE' }"
      >
        <div class="wq-item-content">
          <span class="wq-kind-badge">{{ KINDS.find(k => k.value === item.kind)?.label ?? item.kind }}</span>
          <a v-if="item.linkPath" :href="item.linkPath" class="wq-item-title">{{ item.title }}</a>
          <span v-else class="wq-item-title">{{ item.title }}</span>
          <span class="wq-item-date">{{ item.createdAt.slice(0, 10) }}</span>
        </div>
        <div class="wq-item-actions" v-if="item.state === 'OPEN'">
          <button class="btn-done" @click="handleDone(item.id)">완료</button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.wq-view { max-width: 800px; margin: 2rem auto; padding: 0 1rem; }
.wq-title { font-size: 1.5rem; font-weight: 700; margin-bottom: 1.5rem; }
.wq-tabs { display: flex; gap: 0.5rem; margin-bottom: 1rem; border-bottom: 2px solid #e5e7eb; }
.wq-tab { padding: 0.5rem 1rem; border: none; background: transparent; cursor: pointer; color: #6b7280; font-size: 0.875rem; }
.wq-tab.active { color: #1a56db; border-bottom: 2px solid #1a56db; margin-bottom: -2px; font-weight: 600; }
.wq-filters { margin-bottom: 1rem; }
.wq-select { padding: 0.375rem 0.75rem; border: 1px solid #d1d5db; border-radius: 0.375rem; font-size: 0.875rem; }
.wq-loading, .wq-empty { padding: 2rem; text-align: center; color: #6b7280; }
.wq-error { padding: 1rem; background: #fef2f2; color: #991b1b; border-radius: 0.375rem; }
.wq-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.wq-item { display: flex; justify-content: space-between; align-items: center; padding: 1rem; background: white; border: 1px solid #e5e7eb; border-radius: 0.5rem; }
.wq-item.is-done { opacity: 0.6; }
.wq-item-content { display: flex; align-items: center; gap: 0.75rem; flex: 1; min-width: 0; }
.wq-kind-badge { font-size: 0.75rem; padding: 0.125rem 0.5rem; background: #eff6ff; color: #1a56db; border-radius: 9999px; white-space: nowrap; }
.wq-item-title { font-size: 0.875rem; font-weight: 500; color: #111827; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; }
a.wq-item-title { color: #1a56db; text-decoration: none; }
.wq-item-date { font-size: 0.75rem; color: #9ca3af; white-space: nowrap; }
.wq-item-actions { display: flex; gap: 0.5rem; }
.btn-done { padding: 0.25rem 0.75rem; background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0; border-radius: 0.375rem; cursor: pointer; font-size: 0.75rem; }
</style>
