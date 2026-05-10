import { api } from './client';
import type { DocumentCategory, UpsertCategoryRequest } from '../types';

export const categoriesApi = {
  list: async () => (await api.get<DocumentCategory[]>('/admin/categories')).data,
  listPublic: async () => (await api.get<DocumentCategory[]>('/categories')).data,
  create: async (req: UpsertCategoryRequest) =>
    (await api.post<DocumentCategory>('/admin/categories', req)).data,
  update: async (id: number, req: UpsertCategoryRequest) =>
    (await api.put<DocumentCategory>(`/admin/categories/${id}`, req)).data,
};
