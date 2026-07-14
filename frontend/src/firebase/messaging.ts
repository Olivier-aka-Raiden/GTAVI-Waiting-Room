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
 * the backend. Returns the token string if successful. Throws on failure
 * with a descriptive error message.
 */
export async function enablePushNotifications(installationId: string): Promise<string> {
  const m = initFirebase();

  // 1. Browser permission (must be called from a user gesture)
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    console.warn('[FCM] Notification permission denied:', permission);
    throw new Error('PERMISSION_DENIED');
  }

  // 2. Get a fully active service worker registration
  const swReg = await getActiveSwRegistration();

  // 2b. Clear any stale push subscriptions that might cause 304/cache issues
  try {
    const sub = await swReg.pushManager.getSubscription();
    if (sub) {
      console.log('[FCM] Unsubscribing stale push subscription');
      await sub.unsubscribe();
    }
  } catch { /* non-critical */ }

  // 3. Get the FCM token (this creates a fresh push subscription)
  let token: string;
  try {
    token = await getToken(m, { vapidKey, serviceWorkerRegistration: swReg });
  } catch (err: any) {
    const detail = [
      err?.code,
      err?.message,
      err?.name !== 'FirebaseError' ? err?.name : null,
    ].filter(Boolean).join(' | ');
    console.error('[FCM] getToken failed:', err);
    throw new Error(detail || 'Unknown FCM error');
  }

  if (!token) {
    console.warn('[FCM] No token returned');
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

/**
 * Register the Firebase SW and wait for it to become fully active.
 * Unregisters any stale SWs first, then blocks until the new one
 * reaches the 'activated' state so that PushManager.subscribe() works.
 */
async function getActiveSwRegistration(): Promise<ServiceWorkerRegistration> {
  // Unregister all stale FCM workers
  const registrations = await navigator.serviceWorker.getRegistrations();
  for (const reg of registrations) {
    if (reg.active?.scriptURL.includes('firebase-messaging-sw')) {
      console.log('[FCM] Unregistering stale service worker:', reg.active.scriptURL);
      await reg.unregister();
    }
  }

  // Register fresh
  const reg = await navigator.serviceWorker.register('/firebase-messaging-sw.js');

  // Already active — done
  if (reg.active) {
    console.log('[FCM] Service worker active');
    return reg;
  }

  // Wait for installing/waiting worker to become active
  const worker = reg.installing || reg.waiting;
  if (!worker) {
    throw new Error('SW_NO_WORKER');
  }

  console.log('[FCM] Waiting for service worker to activate...');
  await new Promise<void>((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('SW_ACTIVATE_TIMEOUT')), 15_000);
    worker.addEventListener('statechange', function onStateChange() {
      if (worker.state === 'activated') {
        worker.removeEventListener('statechange', onStateChange);
        clearTimeout(timeout);
        console.log('[FCM] Service worker activated');
        resolve();
      }
    });
  });

  return reg;
}

function getPlatform(): string {
  const ua = navigator.userAgent;
  if (/Android/i.test(ua)) return 'ANDROID';
  if (/iPhone|iPad|iPod/i.test(ua)) return 'IOS';
  return 'WEB';
}

/** True if the current browser is Brave on desktop (where push is unreliable). */
export function isBraveDesktop(): boolean {
  if (typeof navigator === 'undefined') return false;
  // Brave sets navigator.brave
  const nav = navigator as any;
  if (nav.brave?.isBrave) return !/Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
  // Fallback: Brave's push service is always used on desktop unless opted out
  return false;
}
