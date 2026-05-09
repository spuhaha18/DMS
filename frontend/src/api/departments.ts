import { api } from './client';
import type { Department, UpsertDepartmentRequest, UpsertAliasRequest } from '../types';

export const departmentsApi = {
  list: async () => (await api.get<Department[]>('/admin/departments')).data,
  listPublic: async () => (await api.get<Department[]>('/departments')).data,
  create: async (req: UpsertDepartmentRequest) =>
    (await api.post<Department>('/admin/departments', req)).data,
  update: async (id: number, req: UpsertDepartmentRequest) =>
    (await api.put<Department>(`/admin/departments/${id}`, req)).data,
  deactivate: async (id: number) => {
    await api.delete(`/admin/departments/${id}`);
  },
  addAlias: async (id: number, req: UpsertAliasRequest) =>
    (await api.post<{ id: number; aliasName: string; locale: string | null }>(
      `/admin/departments/${id}/aliases`, req)).data,
  deleteAlias: async (id: number, aliasId: number) => {
    await api.delete(`/admin/departments/${id}/aliases/${aliasId}`);
  },
};
