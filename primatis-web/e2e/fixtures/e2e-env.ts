
export const E2E_BACKEND_PORT = process.env['E2E_BACKEND_PORT'] ?? '8180';
export const E2E_WEB_PORT = process.env['E2E_WEB_PORT'] ?? '4300';

export const API_ORIGIN = process.env['E2E_API_ORIGIN'] ?? `http://localhost:${E2E_BACKEND_PORT}`;
export const API_BASE = `${API_ORIGIN}/api/v1`;
export const WEB_BASE_URL = process.env['E2E_WEB_BASE_URL'] ?? `http://localhost:${E2E_WEB_PORT}`;
