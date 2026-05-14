<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from './layouts/AppShell.vue';

const route = useRoute();

// Login page and fullscreen product surfaces do not render the AppShell chrome.
const showShell = computed(() => route.meta?.requiresAuth === true && route.meta?.layout !== 'fullscreen');
</script>

<template>
  <AppShell v-if="showShell">
    <router-view />
  </AppShell>
  <router-view v-else />
</template>
