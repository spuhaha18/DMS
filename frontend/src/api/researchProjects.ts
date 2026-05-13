import { api } from './client';
import type { ResearchProject, ResearchProjectType, ResearchProjectStatus } from '../types';

export async function listProjects(params: {
  status?: ResearchProjectStatus;
  typeCode?: string;
} = {}): Promise<ResearchProject[]> {
  return (await api.get<ResearchProject[]>('/admin/research-projects', { params })).data;
}

export async function createProject(body: {
  projectCode: string;
  projectName: string;
  typeCode: string;
}): Promise<ResearchProject> {
  return (await api.post<ResearchProject>('/admin/research-projects', body)).data;
}

export async function approveProject(code: string, approvalDate: string): Promise<ResearchProject> {
  return (await api.post<ResearchProject>(`/admin/research-projects/${code}/approve`, { approvalDate })).data;
}

export async function terminateProject(code: string, terminationDate: string): Promise<ResearchProject> {
  return (await api.post<ResearchProject>(`/admin/research-projects/${code}/terminate`, { terminationDate })).data;
}

export async function listProjectTypes(): Promise<ResearchProjectType[]> {
  return (await api.get<ResearchProjectType[]>('/admin/research-project-types')).data;
}
