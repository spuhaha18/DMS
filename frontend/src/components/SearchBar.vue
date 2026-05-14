<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const query = ref('');

function submit() {
  const q = query.value.trim();
  if (q.length < 2) return;
  router.push({ name: 'search', query: { q } });
}
</script>

<template>
  <form class="search-bar" role="search" @submit.prevent="submit">
    <input
      v-model="query"
      type="search"
      placeholder="문서번호, 제목으로 검색 (2자 이상)"
      minlength="2"
      aria-label="문서 검색"
    />
    <button type="submit" :disabled="query.trim().length < 2">검색</button>
  </form>
</template>
