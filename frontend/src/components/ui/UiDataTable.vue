<script setup lang="ts" generic="T extends Record<string, unknown>">
export interface UiDataTableColumn<T> {
  key: keyof T & string;
  label: string;
  align?: 'left' | 'center' | 'right';
  width?: string;
}

defineProps<{
  columns: UiDataTableColumn<T>[];
  rows: T[];
  rowKey?: keyof T & string;
  loading?: boolean;
  emptyMessage?: string;
}>();
</script>

<template>
  <div class="ui-data-table-wrapper">
    <table class="ui-data-table">
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            :style="col.width ? { width: col.width } : {}"
            :class="`ui-data-table-th--${col.align ?? 'left'}`"
          >
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td :colspan="columns.length" class="ui-data-table-state">
            불러오는 중…
          </td>
        </tr>
        <tr v-else-if="rows.length === 0">
          <td :colspan="columns.length" class="ui-data-table-state">
            {{ emptyMessage ?? '항목이 없습니다.' }}
          </td>
        </tr>
        <tr
          v-else
          v-for="(row, rowIdx) in rows"
          :key="rowKey ? String(row[rowKey]) : rowIdx"
          class="ui-data-table-row"
        >
          <td
            v-for="col in columns"
            :key="col.key"
            :class="`ui-data-table-td--${col.align ?? 'left'}`"
          >
            <slot :name="col.key" :row="row" :value="row[col.key]">
              {{ row[col.key] }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.ui-data-table-wrapper {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
}

.ui-data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  font-family: var(--font-base, 'Inter', system-ui, sans-serif);
  color: #111827;
}

.ui-data-table thead tr {
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.ui-data-table th {
  padding: 0.625rem 0.875rem;
  font-weight: 600;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  white-space: nowrap;
}

.ui-data-table-th--left  { text-align: left; }
.ui-data-table-th--center { text-align: center; }
.ui-data-table-th--right { text-align: right; }

.ui-data-table-row {
  border-top: 1px solid #f3f4f6;
  transition: background 0.1s;
}

.ui-data-table-row:hover {
  background: #f9fafb;
}

.ui-data-table td {
  padding: 0.75rem 0.875rem;
  vertical-align: middle;
}

.ui-data-table-td--left  { text-align: left; }
.ui-data-table-td--center { text-align: center; }
.ui-data-table-td--right { text-align: right; }

.ui-data-table-state {
  padding: 2rem;
  text-align: center;
  color: #6b7280;
}
</style>
