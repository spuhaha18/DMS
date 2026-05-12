<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from './layouts/AppShell.vue';

const route = useRoute();

// Login page (and any other unauthenticated route) does NOT render the AppShell chrome.
const showShell = computed(() => route.meta?.requiresAuth === true);
</script>

<template>
  <AppShell v-if="showShell">
    <router-view />
  </AppShell>
  <router-view v-else />
</template>
