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
  { path: '/admin/departments', name: 'admin-departments',
    component: () => import('../views/admin/DepartmentAdminView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
  { path: '/admin/categories', name: 'admin-categories',
    component: () => import('../views/admin/CategoryAdminView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
  { path: '/admin/numbering-templates', name: 'admin-numbering',
    component: () => import('../views/admin/NumberingTemplateAdminView.vue'),
    meta: { requiresAuth: true, requiresRole: ['ADMIN'] } },
  { path: '/admin/research-projects', name: 'AdminResearchProjects',
    component: () => import('../views/admin/ResearchProjectsView.vue'),
    meta: { requiresAuth: true, requiresRole: ['QA', 'ADMIN'] } },
  { path: '/admin/research-project-types', name: 'AdminResearchProjectTypes',
    component: () => import('../views/admin/ResearchProjectTypesView.vue'),
    meta: { requiresAuth: true, requiresRole: ['QA', 'ADMIN'] } },
  { path: '/documents', name: 'document-list',
    component: () => import('../views/documents/DocumentListView.vue'),
    meta: { requiresAuth: true } },
  { path: '/documents/new', name: 'document-create',
    component: () => import('../views/documents/DocumentCreateView.vue'),
    meta: { requiresAuth: true } },
  { path: '/documents/:docId', name: 'document-detail',
    component: () => import('../views/documents/DocumentDetailView.vue'),
    meta: { requiresAuth: true } },
  {
    path: '/documents/:docId/versions/:verId/pdf',
    name: 'document-pdf-view',
    component: () => import('../views/documents/DocumentPdfView.vue'),
    meta: { requiresAuth: true },
  },
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
