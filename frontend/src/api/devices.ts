// ============================================================
// GTA VI Waiting Room — Notification API client
// ============================================================

import { API_BASE } from './config';

const BASE = `${API_BASE}/api/v1`;

async function postJson(url: string, body: unknown) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`API error: ${res.status}`);
  return res.json();
}

async function putJson(url: string, body: unknown) {
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`API error: ${res.status}`);
  return res.json();
}

async function getJson(url: string) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`API error: ${res.status}`);
  return res.json();
}

export interface DeviceRegistration {
  installationId: string;
  pushToken: string;
  platform: string;
  locale: string;
  appVersion: string;
}

export interface DeviceUpdate {
  pushToken?: string;
  locale?: string;
  appVersion?: string;
  notificationsEnabled?: boolean;
}

export interface NotificationPreferences {
  collectorEditionAnnouncement: boolean;
  collectorEditionPreorder: boolean;
  releaseDateChanges: boolean;
  newOfficialTrailers: boolean;
  majorRockstarNews: boolean;
  generalNews: boolean;
  priceChanges: boolean;
  outOfStock: boolean;
  backInStock: boolean;
}

export async function registerDevice(reg: DeviceRegistration) {
  return postJson(`${BASE}/devices`, reg);
}

export async function updateDevice(installationId: string, updates: DeviceUpdate) {
  return putJson(`${BASE}/devices/${installationId}`, updates);
}

export async function getPreferences(installationId: string): Promise<NotificationPreferences> {
  return getJson(`${BASE}/devices/${installationId}/preferences`);
}

export async function updatePreferences(installationId: string, prefs: Partial<NotificationPreferences>) {
  return putJson(`${BASE}/devices/${installationId}/preferences`, prefs);
}
