// Notification clicks must be registered before Firebase's own handlers.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const value = event.notification.data?.url ?? '/#section-updates';
  const target = new URL(value, self.location.origin);
  if (target.origin !== self.location.origin) return;
  event.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async windows => {
    const existing = windows.find(client => new URL(client.url).origin === self.location.origin);
    if (existing) { await existing.navigate(target.href); return existing.focus(); }
    return clients.openWindow(target.href);
  }));
});
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
messaging.onBackgroundMessage((payload) => {
  // Older server payloads are already displayed automatically by Firebase.
  if (payload.notification) return;
  const data = payload.data ?? {};
  return self.registration.showNotification(data.title ?? 'GTA VI Update', {
    body: data.body ?? '',
    icon: '/assets/icon-192.png',
    badge: '/assets/notification-badge-96.png',
    data,
    tag: data.eventId ?? 'gtavi-update',
    requireInteraction: false,
  });
});
