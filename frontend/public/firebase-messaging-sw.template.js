// Firebase Cloud Messaging service worker.
// Must live at /firebase-messaging-sw.js (root of the domain).
// Firebase SDK self-imports via importScripts — no bundler needed here.
// Placeholders (VITE_*_PLACEHOLDER) are replaced at build time by scripts/inject-firebase-sw.js.

importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: 'VITE_FIREBASE_API_KEY_PLACEHOLDER',
  authDomain: 'VITE_FIREBASE_AUTH_DOMAIN_PLACEHOLDER',
  projectId: 'VITE_FIREBASE_PROJECT_ID_PLACEHOLDER',
  storageBucket: 'VITE_FIREBASE_STORAGE_BUCKET_PLACEHOLDER',
  messagingSenderId: 'VITE_FIREBASE_MESSAGING_SENDER_ID_PLACEHOLDER',
  appId: 'VITE_FIREBASE_APP_ID_PLACEHOLDER',
});

const messaging = firebase.messaging();

// Background message handler — fires when the app is NOT in the foreground.
messaging.onBackgroundMessage((payload) => {
  console.log('[FCM SW] Background message received:', payload);
  const { title, body } = payload.notification ?? {};
  const icon = '/assets/icon-192.png';

  self.registration.showNotification(title ?? 'GTA VI Update', {
    body: body ?? '',
    icon,
    badge: icon,
    data: payload.data ?? {},
    vibrate: [200, 100, 200],
    tag: 'gtavi-update',
    requireInteraction: false,
  });
});

// Handle notification click — open the app
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow('/');
      }
    })
  );
});
