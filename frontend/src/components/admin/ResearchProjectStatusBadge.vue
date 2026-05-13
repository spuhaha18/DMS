<script setup lang="ts">
import UiBadge from '../ui/UiBadge.vue';
import type { ResearchProjectStatus } from '../../types';

const props = defineProps<{ status: ResearchProjectStatus }>();

const badgeConfig: Record<ResearchProjectStatus, { variant: 'info' | 'success' | 'warning' | 'danger' | 'default'; label: string; title: string }> = {
  ACTIVE:     { variant: 'info',    label: '진행중 (임시 30년)',  title: '품목허가/중단 시 SOP 표 기준 N년으로 자동 연장됩니다.' },
  APPROVED:   { variant: 'success', label: '품목허가 완료',       title: '보존기간이 확정되었습니다.' },
  TERMINATED: { variant: 'warning', label: '중단/종료',           title: '보존기간이 확정되었습니다.' },
};
</script>

<template>
  <UiBadge
    :variant="badgeConfig[status].variant"
    :title="badgeConfig[status].title"
  >
    {{ badgeConfig[status].label }}
  </UiBadge>
</template>
