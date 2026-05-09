import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { requiresAuth: true },
  },
  { path: '/admin/users', name: 'admin-users',
    component: () => import('../views/admin/UserListView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
  { path: '/admin/users/new', name: 'admin-user-create',
    component: () => import('../views/admin/UserCreateView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
  { path: '/admin/users/:userPk', name: 'admin-user-edit',
    component: () => import('../views/admin/UserEditView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
  { path: '/admin/roles', name: 'admin-roles',
    component: () => import('../views/admin/RoleListView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
  { path: '/admin/permissions', name: 'admin-permissions',
    component: () => import('../views/admin/PermissionMatrixView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated()) {
    await auth.fetchMe();
    if (!auth.isAuthenticated()) return { name: 'login' };
  }
  const required = (to.meta.requiresRole as string[] | undefined) ?? [];
  if (required.length && !required.some((c) => auth.hasRole(c))) {
    return { name: 'home' };
  }
});
