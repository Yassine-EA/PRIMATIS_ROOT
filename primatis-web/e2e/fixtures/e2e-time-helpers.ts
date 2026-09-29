import { APIRequestContext } from '@playwright/test';

import { e2eAccounts } from './e2e-accounts';
import { API_BASE } from './e2e-env';



export interface E2eTimeState {
  currentTime: string;
  offsetSeconds: number;
}

async function freshAdminToken(request: APIRequestContext): Promise<string> {
  const response = await request.post(`${API_BASE}/auth/login`, {
    data: { email: e2eAccounts.admin.email, password: e2eAccounts.admin.password },
  });
  const { token } = await response.json();
  return token;
}

export async function getE2eTime(request: APIRequestContext): Promise<E2eTimeState> {
  const token = await freshAdminToken(request);
  const response = await request.get(`${API_BASE}/e2e/time`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.json();
}


export async function advanceE2eTime(request: APIRequestContext, hours: number): Promise<E2eTimeState> {
  const token = await freshAdminToken(request);
  const response = await request.post(`${API_BASE}/e2e/time/advance`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { hours },
  });
  return response.json();
}

export async function resetE2eTime(request: APIRequestContext): Promise<E2eTimeState> {
  const token = await freshAdminToken(request);
  const response = await request.post(`${API_BASE}/e2e/time/reset`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.json();
}
