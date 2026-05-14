// frontend/src/api/training.ts
import { api } from './client';

export interface TrainingAssignment {
  id: number;
  docId: number;
  docNumber: string;
  docTitle: string;
  versionId: number;
  assignedAt: string;
  dueAt: string | null;
  completed: boolean;
  completedAt: string | null;
}

export interface TrainingStatus {
  assignmentId: number;
  userDisplayName: string;
  assignedAt: string;
  completedAt: string | null;
  completed: boolean;
}

export async function listMyTraining(): Promise<TrainingAssignment[]> {
  const { data } = await api.get<TrainingAssignment[]>('/training');
  return data;
}

export async function acknowledgeTraining(assignmentId: number): Promise<void> {
  await api.post(`/training/${assignmentId}/acknowledge`);
}

export async function getTrainingStatus(versionId: number): Promise<TrainingStatus[]> {
  const { data } = await api.get<TrainingStatus[]>(`/training/status/${versionId}`);
  return data;
}
