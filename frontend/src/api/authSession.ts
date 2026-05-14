import { api } from './client';

export interface SessionState {
  userId: string;
  userName: string;
  firstSignRequired: boolean;
}

const suppressToast = { suppressGlobalToast: true } as const;

export async function fetchSessionState(): Promise<SessionState> {
  return (await api.get<SessionState>('/auth/session-state', suppressToast)).data;
}
