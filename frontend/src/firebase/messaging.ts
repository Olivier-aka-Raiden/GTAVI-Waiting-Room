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
 */
export async function enablePushNotifications(installationId: string): Promise<string | null> {
  const m = initFirebase();

  // 1. Browser permission (must be called from a user gesture)
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    console.warn('[FCM] Notification permission denied');
    return null;
  }

  // 2. Get the FCM token (requires the service worker registered in public/)
  try {
    const token = await getToken(m, { vapidKey, serviceWorkerRegistration: await getSwRegistration() });

    if (!token) {
      console.warn('[FCM] No token returned — browser may block notifications');
      return null;
    }

    // 3. Register the token + device with the backend
    await registerDevice({
      installationId,
      pushToken: token,
      platform: getPlatform(),
      locale: navigator.language,
      appVersion: '1.0.0',
    });

    console.log('[FCM] Token registered:', token.substring(0, 12) + '...');
    return token;

  } catch (err) {
    console.error('[FCM] Failed to get token:', err);
    return null;
  }
}

/** Listen for foreground messages (only works when the app is open). */
export function onForegroundMessage(callback: (payload: any) => void) {
  const m = initFirebase();
  return onMessage(m, callback);
}

async function getSwRegistration(): Promise<ServiceWorkerRegistration> {
  const reg = await navigator.serviceWorker.getRegistration();
  if (reg) return reg;
  return navigator.serviceWorker.register('/firebase-messaging-sw.js');
}

function getPlatform(): string {
  const ua = navigator.userAgent;
  if (/Android/i.test(ua)) return 'ANDROID';
  if (/iPhone|iPad|iPod/i.test(ua)) return 'IOS';
  return 'WEB';
}
