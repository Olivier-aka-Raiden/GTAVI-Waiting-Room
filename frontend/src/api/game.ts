// ============================================================
// GTA VI Waiting Room — API client
// ============================================================

import type { GameOverview, Trailer, Edition, EventsPage } from '../types/game';

const BASE = '/api/v1';

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function getGameOverview(): Promise<GameOverview> {
  return fetchJson<GameOverview>(`${BASE}/games/gta-vi`);
}

export async function getTrailers(type?: string): Promise<Trailer[]> {
  const params = type ? `?type=${type}` : '';
  return fetchJson<Trailer[]>(`${BASE}/games/gta-vi/trailers${params}`);
}

export async function getEditions(): Promise<Edition[]> {
  return fetchJson<Edition[]>(`${BASE}/games/gta-vi/editions`);
}

export async function getEvents(page = 0, size = 20): Promise<EventsPage> {
  return fetchJson<EventsPage>(`${BASE}/games/gta-vi/events?page=${page}&size=${size}`);
}
