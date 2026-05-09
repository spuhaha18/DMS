import { api } from './client';
import type {
  CreateUserRequest, DisableUserRequest, PageResponse, Permission,
  Role, UpdateRoleRequest, UpdateRolesRequest, UpdateUserRequest,
  UpsertPermissionRequest, User,
} from '../types';

export const usersApi = {
  list: async (params: { status?: string; department?: string; page?: number; size?: number } = {}) => {
    const { data } = await api.get<PageResponse<User>>('/admin/users', { params });
    return data;
  },
  get: async (userPk: number) => (await api.get<User>(`/admin/users/${userPk}`)).data,
  create: async (req: CreateUserRequest) => (await api.post<User>('/admin/users', req)).data,
  update: async (userPk: number, req: UpdateUserRequest) =>
    (await api.put<User>(`/admin/users/${userPk}`, req)).data,
  updateRoles: async (userPk: number, req: UpdateRolesRequest) =>
    (await api.put<User>(`/admin/users/${userPk}/roles`, req)).data,
  disable: async (userId: string, req: DisableUserRequest) => {
    await api.post(`/admin/users/${userId}/disable`, req);
  },
  passwordReset: async (userId: string) => {
    await api.post(`/admin/users/${userId}/password-reset`);
  },
  exportCsv: async () => (await api.get<string>('/admin/users/export?format=csv')).data,
};

export const rolesApi = {
  list: async () => (await api.get<Role[]>('/admin/roles')).data,
  update: async (roleId: number, req: UpdateRoleRequest) =>
    (await api.put<Role>(`/admin/roles/${roleId}`, req)).data,
};

export const permissionsApi = {
  list: async (params: { role_id?: number; category_id?: number } = {}) =>
    (await api.get<Permission[]>('/admin/permissions', { params })).data,
  upsert: async (req: UpsertPermissionRequest) =>
    (await api.put<Permission>('/admin/permissions', req)).data,
  delete: async (permissionId: number) => {
    await api.delete(`/admin/permissions/${permissionId}`);
  },
};
