import { api } from './client';

export interface SearchResult {
  documentId: number;
  versionId: number;
  docNumber: string;
  title: string;
  state: string;
  categoryCode: string;
  department: string;
  effectiveDate: string | null;
  authorUserId: string;
  rank: number;
}

export interface SearchPage {
  content: SearchResult[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface SearchParams {
  q: string;
  category?: string;
  dept?: string;
  state?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export async function searchDocuments(params: SearchParams): Promise<SearchPage> {
  const { data } = await api.get<SearchPage>('/search', { params });
  return data;
}
