import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '../api/client';

export interface Me {
  userId: string;
  fullName: string;
  email: string;
  department: string;
  roles: string[];
}

export const useAuthStore = defineStore('auth', () => {
  const me = ref<Me | null>(null);
  const forceChangePw = ref(false);

  async function login(userId: string, password: string) {
    const { data } = await api.post('/auth/login', { userId, password });
    forceChangePw.value = data.forceChangePw === true;
    await fetchMe();
  }

  async function fetchMe() {
    try {
      const { data } = await api.get('/auth/me');
      me.value = data;
    } catch {
      me.value = null;
    }
  }

  async function logout() {
    await api.post('/auth/logout');
    me.value = null;
    forceChangePw.value = false;
  }

  function isAuthenticated(): boolean { return me.value !== null; }

  return { me, forceChangePw, login, logout, fetchMe, isAuthenticated };
});
