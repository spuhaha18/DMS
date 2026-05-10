import { api } from './client';
import type {
  NumberingTemplate, UpsertNumberingTemplateRequest,
  NumberingPreviewRequest, NumberingPreviewResponse,
} from '../types';

export const numberingApi = {
  list: async () => (await api.get<NumberingTemplate[]>('/admin/numbering-templates')).data,
  create: async (req: UpsertNumberingTemplateRequest) =>
    (await api.post<NumberingTemplate>('/admin/numbering-templates', req)).data,
  update: async (id: number, req: UpsertNumberingTemplateRequest) =>
    (await api.put<NumberingTemplate>(`/admin/numbering-templates/${id}`, req)).data,
  preview: async (req: NumberingPreviewRequest) =>
    (await api.post<NumberingPreviewResponse>('/admin/numbering-counters/preview', req)).data,
};
