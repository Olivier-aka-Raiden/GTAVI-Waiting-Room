import { initializeApp } from 'firebase/app';
import { getMessaging, getToken, onMessage, type Messaging } from 'firebase/messaging';
import { firebaseConfig, vapidKey } from './config';
import { registerDevice } from '../api/devices';

let messaging: Messaging | null = null;

/** Initialize Firebase (idempotent — safe to call multiple times). */
export function initFirebase() {
  if (messaging) return messaging;

  const app = initializeApp(firebaseConfig);
  messaging = getMessaging(app);
  return messaging;
}

/**
 * Request notification permission, get an FCM token, and register it with
 * the backend. Returns the token string if successful, or null.
 * On failure, the reason is attached to the error's message field.
 */
export async function enablePushNotifications(installationId: string): Promise<string> {
  const m = initFirebase();

  // 1. Browser permission (must be called from a user gesture)
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    console.warn('[FCM] Notification permission denied:', permission);
    throw new Error('PERMISSION_DENIED');
  }

  // 2. Ensure we get the latest service worker (not a stale cached one)
  const swReg = await getSwRegistration();

  // 3. Get the FCM token
  let token: string;
  try {
    token = await getToken(m, { vapidKey, serviceWorkerRegistration: swReg });
  } catch (err: any) {
    const code = err?.code ?? 'UNKNOWN';
    const msg = err?.message ?? String(err);
    console.error('[FCM] getToken failed:', code, msg);
    throw new Error(`TOKEN_${code}`);
  }

  if (!token) {
    console.warn('[FCM] No token returned — missing VAPID key or Firebase project not configured for web push');
    throw new Error('NO_TOKEN');
  }

  // 4. Register the token + device with the backend
  await registerDevice({
    installationId,
    pushToken: token,
    platform: getPlatform(),
    locale: navigator.language,
    appVersion: '1.0.0',
  });

  console.log('[FCM] Token registered:', token.substring(0, 12) + '...');
  return token;
}

/** Listen for foreground messages (only works when the app is open). */
export function onForegroundMessage(callback: (payload: any) => void) {
  const m = initFirebase();
  return onMessage(m, callback);
}

async function getSwRegistration(): Promise<ServiceWorkerRegistration> {
  // Unregister any stale service worker first, then register fresh
  const registrations = await navigator.serviceWorker.getRegistrations();
  for (const reg of registrations) {
    if (reg.active?.scriptURL.includes('firebase-messaging-sw')) {
      console.log('[FCM] Unregistering stale service worker:', reg.active.scriptURL);
      await reg.unregister();
    }
  }
  // Small delay to let the unregister settle
  await new Promise(r => setTimeout(r, 300));
  return navigator.serviceWorker.register('/firebase-messaging-sw.js');
}

function getPlatform(): string {
  const ua = navigator.userAgent;
  if (/Android/i.test(ua)) return 'ANDROID';
  if (/iPhone|iPad|iPod/i.test(ua)) return 'IOS';
  return 'WEB';
}
