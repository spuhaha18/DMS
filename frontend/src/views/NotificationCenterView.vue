<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useNotificationStore } from '../stores/notification';
import { getNotificationLabel } from '../i18n/notificationMessages';
import type { Notification } from '../api/notifications';

const store = useNotificationStore();
const router = useRouter();

type Category = 'ALL' | 'WORKFLOW' | 'DELEGATION' | 'SYSTEM';
type ReadFilter = 'ALL' | 'UNREAD' | 'READ';

const CATEGORIES: { value: Category; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'WORKFLOW', label: '결재' },
  { value: 'DELEGATION', label: '위임' },
  { value: 'SYSTEM', label: '시스템' },
];

const activeCategory = ref<Category>('ALL');
const readFilter = ref<ReadFilter>('ALL');

function getCategory(eventCode: string): Category {
  if (eventCode.startsWith('WORKFLOW_')) return 'WORKFLOW';
  if (eventCode.startsWith('DELEGATION_')) return 'DELEGATION';
  return 'SYSTEM';
}

const filteredItems = computed(() =>
  store.items.filter((item: Notification) => {
    const categoryMatch =
      activeCategory.value === 'ALL' ||
      getCategory(item.eventCode) === activeCategory.value;
    const readMatch =
      readFilter.value === 'ALL' ||
      (readFilter.value === 'UNREAD' && !item.read) ||
      (readFilter.value === 'READ' && item.read);
    return categoryMatch && readMatch;
  })
);

function formatDate(iso: string): string {
  return iso.slice(0, 10);
}

async function handleClick(item: Notification) {
  if (!item.read) {
    try {
      await store.markRead(item.id);
    } catch {
      // mark-read 실패는 네비게이션을 막지 않음
    }
  }
  if (item.linkPath) {
    router.push(item.linkPath);
  }
}

onMounted(() => {
  store.fetchList();
});
</script>

<template>
  <div class="nc-view">
    <h1 class="nc-title">알림</h1>

    <div class="nc-tabs" role="tablist">
      <button
        v-for="cat in CATEGORIES"
        :key="cat.value"
        role="tab"
        :aria-selected="activeCategory === cat.value"
        class="nc-tab"
        :class="{ active: activeCategory === cat.value }"
        @click="activeCategory = cat.value"
      >{{ cat.label }}</button>
    </div>

    <div class="nc-filters">
      <label>
        <select v-model="readFilter" class="nc-select">
          <option value="ALL">전체</option>
          <option value="UNREAD">안읽음</option>
          <option value="READ">읽음</option>
        </select>
      </label>
    </div>

    <div v-if="store.isLoading" class="nc-loading">로딩 중...</div>

    <div v-else-if="store.error" class="nc-error" role="alert">{{ store.error }}</div>

    <ul v-else-if="filteredItems.length > 0" class="nc-list">
      <li
        v-for="item in filteredItems"
        :key="item.id"
        class="nc-item"
        :class="{ unread: !item.read }"
        @click="handleClick(item)"
      >
        <div class="nc-item-header">
          <span
            class="nc-severity"
            :class="{
              'severity-urgent': item.severity === 'URGENT',
              'severity-warn': item.severity === 'WARN',
              'severity-info': item.severity === 'INFO',
            }"
          >{{ item.severity }}</span>
          <span class="nc-event-label">{{ getNotificationLabel(item.eventCode) }}</span>
          <span class="nc-date">{{ formatDate(item.createdAt) }}</span>
        </div>
        <div class="nc-item-title">{{ item.title }}</div>
        <div v-if="item.body" class="nc-item-body">{{ item.body }}</div>
      </li>
    </ul>

    <div v-else class="nc-empty">알림이 없습니다.</div>
  </div>
</template>

<style scoped>
.nc-view { max-width: 800px; margin: 2rem auto; padding: 0 1rem; }
.nc-title { font-size: 1.5rem; font-weight: 700; margin-bottom: 1.5rem; }

.nc-tabs { display: flex; gap: 0.5rem; margin-bottom: 1rem; border-bottom: 2px solid #e5e7eb; }
.nc-tab { padding: 0.5rem 1rem; border: none; background: transparent; cursor: pointer; color: #6b7280; font-size: 0.875rem; }
.nc-tab.active { color: #1a56db; border-bottom: 2px solid #1a56db; margin-bottom: -2px; font-weight: 600; }

.nc-filters { margin-bottom: 1rem; }
.nc-select { padding: 0.375rem 0.75rem; border: 1px solid #d1d5db; border-radius: 0.375rem; font-size: 0.875rem; }

.nc-loading, .nc-empty { padding: 2rem; text-align: center; color: #6b7280; }
.nc-error { padding: 2rem; text-align: center; color: #991b1b; }

.nc-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 0.5rem; }

.nc-item {
  padding: 1rem;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  cursor: pointer;
}
.nc-item:hover { background: #f9fafb; }
.nc-item.unread { border-left: 3px solid #1a56db; background: #eff6ff; }

.nc-item-header { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.375rem; }

.nc-severity {
  font-size: 0.7rem; font-weight: 700;
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
}
.severity-urgent { background: #fef2f2; color: #991b1b; }
.severity-warn { background: #fffbeb; color: #92400e; }
.severity-info { background: #f3f4f6; color: #374151; }

.nc-event-label { font-size: 0.75rem; color: #6b7280; }
.nc-date { margin-left: auto; font-size: 0.75rem; color: #9ca3af; }

.nc-item-title { font-size: 0.875rem; font-weight: 500; color: #111827; }
.nc-item-body { font-size: 0.8125rem; color: #6b7280; margin-top: 0.25rem; }
</style>
