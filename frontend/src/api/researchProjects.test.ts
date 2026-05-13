import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  listProjects,
  createProject,
  approveProject,
  terminateProject,
  listProjectTypes,
} from './researchProjects';
import { api } from './client';

vi.mock('./client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const mockProject = {
  projectCode: 'PROJ-001',
  projectName: '테스트 프로젝트',
  typeCode: 'TYPE-A',
  typeNameKr: '임상시험',
  perpetual: false,
  status: 'ACTIVE' as const,
};

const mockType = {
  typeCode: 'TYPE-A',
  typeNameKr: '임상시험',
  perpetual: false,
};

describe('researchProjects api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('listProjects', () => {
    it('returns array on success', async () => {
      (api.get as any).mockResolvedValue({ data: [mockProject] });
      const result = await listProjects();
      expect(result).toEqual([mockProject]);
      expect(api.get).toHaveBeenCalledWith('/admin/research-projects', { params: {} });
    });

    it('passes status and typeCode params', async () => {
      (api.get as any).mockResolvedValue({ data: [] });
      await listProjects({ status: 'APPROVED', typeCode: 'TYPE-A' });
      expect(api.get).toHaveBeenCalledWith('/admin/research-projects', {
        params: { status: 'APPROVED', typeCode: 'TYPE-A' },
      });
    });
  });

  describe('createProject', () => {
    it('posts and returns project', async () => {
      (api.post as any).mockResolvedValue({ data: mockProject });
      const body = { projectCode: 'PROJ-001', projectName: '테스트 프로젝트', typeCode: 'TYPE-A' };
      const result = await createProject(body);
      expect(result).toEqual(mockProject);
      expect(api.post).toHaveBeenCalledWith('/admin/research-projects', body);
    });
  });

  describe('approveProject', () => {
    it('posts approval date to /approve endpoint', async () => {
      const approved = { ...mockProject, status: 'APPROVED' as const, approvalDate: '2026-05-01' };
      (api.post as any).mockResolvedValue({ data: approved });
      const result = await approveProject('PROJ-001', '2026-05-01');
      expect(result.status).toBe('APPROVED');
      expect(api.post).toHaveBeenCalledWith('/admin/research-projects/PROJ-001/approve', {
        approvalDate: '2026-05-01',
      });
    });
  });

  describe('terminateProject', () => {
    it('posts termination date to /terminate endpoint', async () => {
      const terminated = { ...mockProject, status: 'TERMINATED' as const, terminationDate: '2026-06-01' };
      (api.post as any).mockResolvedValue({ data: terminated });
      const result = await terminateProject('PROJ-001', '2026-06-01');
      expect(result.status).toBe('TERMINATED');
      expect(api.post).toHaveBeenCalledWith('/admin/research-projects/PROJ-001/terminate', {
        terminationDate: '2026-06-01',
      });
    });
  });

  describe('listProjectTypes', () => {
    it('returns types array', async () => {
      (api.get as any).mockResolvedValue({ data: [mockType] });
      const result = await listProjectTypes();
      expect(result).toEqual([mockType]);
      expect(api.get).toHaveBeenCalledWith('/admin/research-project-types');
    });
  });
});
