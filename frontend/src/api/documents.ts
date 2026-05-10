import { api } from './client';
import type {
  CreateDocumentRequest, CreateDocumentResponse,
  DocumentSummary, DocumentVersionSummary, DocumentFileSummary, PageResponse
} from '../types';

export const documentsApi = {
  list: async (params: { categoryCode?: string; department?: string; state?: string; page?: number; size?: number } = {}) =>
    (await api.get<PageResponse<DocumentSummary>>('/documents', { params })).data,

  get: async (docId: number) =>
    (await api.get<DocumentSummary>(`/documents/${docId}`)).data,

  create: async (req: CreateDocumentRequest) =>
    (await api.post<CreateDocumentResponse>('/documents', req)).data,

  listVersions: async (docId: number) =>
    (await api.get<DocumentVersionSummary[]>(`/documents/${docId}/versions`)).data,

  getVersion: async (docId: number, verId: number) =>
    (await api.get<DocumentVersionSummary>(`/documents/${docId}/versions/${verId}`)).data,

  uploadFile: async (docId: number, verId: number, file: File, onProgress?: (pct: number) => void) => {
    const form = new FormData();
    form.append('file', file);
    return (await api.post<DocumentFileSummary>(
      `/documents/${docId}/versions/${verId}/files`,
      form,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
      }
    )).data;
  },

  listCreatableDepts: async (categoryId: number) =>
    (await api.get<string[]>('/permissions/creatable', { params: { categoryId } })).data,
};
