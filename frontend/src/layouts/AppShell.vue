<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useToast } from '../components/Toast/useToast';
import Toast from '../components/Toast/Toast.vue';
import WorkQueueIcon from '../components/workQueue/WorkQueueIcon.vue';
import NotificationBell from '../components/notification/NotificationBell.vue';
import SearchBar from '../components/SearchBar.vue';

const auth = useAuthStore();
const router = useRouter();
const { toasts, dismissToast } = useToast();

async function onLogout() {
  try {
    await auth.logout();
  } finally {
    router.push({ name: 'login' });
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand">EDMS</div>
      <nav class="nav">
        <router-link to="/">홈</router-link>
        <router-link to="/documents">문서 목록</router-link>
        <router-link
          v-if="auth.hasRole('QA') || auth.hasRole('ADMIN')"
          to="/admin/research-projects"
        >연구과제 관리</router-link>
        <router-link
          v-if="auth.hasRole('QA') || auth.hasRole('ADMIN')"
          to="/admin/research-project-types"
        >시험 종류 마스터</router-link>
      </nav>
      <SearchBar />
      <div class="user-area">
        <NotificationBell />
        <WorkQueueIcon />
        <span v-if="auth.me" class="user-name">{{ auth.me.fullName }}</span>
        <button type="button" class="logout-btn" @click="onLogout">로그아웃</button>
      </div>
    </header>

    <main class="app-main">
      <slot />
    </main>

    <div class="toast-host" aria-live="polite">
      <Toast
        v-for="t in toasts"
        :key="t.id"
        :message="t.message"
        :type="t.type"
        :duration="t.duration"
        @dismiss="dismissToast(t.id)"
      />
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  --color-primary: #1a56db;
  --spacing-4: 1rem;
  --radius-base: 0.375rem;
  --font-base: 'Inter', system-ui, sans-serif;

  min-width: 1024px;
  min-height: 100vh;
  font-family: var(--font-base);
  color: #111827;
  background: #f9fafb;
}

.app-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
  padding: 0 var(--spacing-4);
  height: 56px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-weight: 700;
  font-size: 1.125rem;
  color: var(--color-primary);
}

.nav {
  display: flex;
  gap: var(--spacing-4);
  flex: 1;
}
.nav a {
  text-decoration: none;
  color: #374151;
  padding: 0.375rem 0.5rem;
  border-radius: var(--radius-base);
}
.nav a:hover { background: #f3f4f6; }
.nav a.router-link-active { color: var(--color-primary); font-weight: 600; }

.user-area {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.user-name { color: #4b5563; font-size: 0.875rem; }

.logout-btn {
  background: var(--color-primary);
  color: #ffffff;
  border: none;
  padding: 0.5rem 0.875rem;
  border-radius: var(--radius-base);
  cursor: pointer;
  font-size: 0.875rem;
}
.logout-btn:hover { background: #1742a6; }

.app-main {
  padding: var(--spacing-4);
}

.toast-host {
  position: fixed;
  top: 72px;
  right: var(--spacing-4);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  z-index: 100;
}
</style>
