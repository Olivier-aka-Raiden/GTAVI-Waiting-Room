// Shared API configuration. In dev, Vite proxies /api to localhost:8080.
// In production (Vercel), either Vercel rewrites forward /api/* to Cloud Run,
// or set VITE_API_BASE_URL to the full Cloud Run URL.
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';
