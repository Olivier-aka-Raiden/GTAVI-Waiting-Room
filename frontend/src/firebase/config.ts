// Firebase config — these are PUBLIC values (embedded in the browser bundle).
// Set in Vercel dashboard: Settings → Environment Variables.
// Prefix VITE_ makes them available at build time via import.meta.env.
export const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

// VAPID key — also public, from Firebase Console → Cloud Messaging → Web Push certs
export const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY;
