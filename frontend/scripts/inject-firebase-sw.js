// Build-time script: replaces Firebase config placeholders in the
// service worker with actual VITE_* env var values.
import { readFileSync, writeFileSync } from 'fs';
import { resolve } from 'path';

const swPath = resolve('public/firebase-messaging-sw.js');
let sw = readFileSync(swPath, 'utf-8');

const replacements = {
  VITE_FIREBASE_API_KEY_PLACEHOLDER: process.env.VITE_FIREBASE_API_KEY ?? '',
  VITE_FIREBASE_AUTH_DOMAIN_PLACEHOLDER: process.env.VITE_FIREBASE_AUTH_DOMAIN ?? '',
  VITE_FIREBASE_PROJECT_ID_PLACEHOLDER: process.env.VITE_FIREBASE_PROJECT_ID ?? '',
  VITE_FIREBASE_STORAGE_BUCKET_PLACEHOLDER: process.env.VITE_FIREBASE_STORAGE_BUCKET ?? '',
  VITE_FIREBASE_MESSAGING_SENDER_ID_PLACEHOLDER: process.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
  VITE_FIREBASE_APP_ID_PLACEHOLDER: process.env.VITE_FIREBASE_APP_ID ?? '',
};

for (const [placeholder, value] of Object.entries(replacements)) {
  sw = sw.replaceAll(placeholder, value);
}

writeFileSync(swPath, sw);
console.log('[build:sw] Firebase config injected into firebase-messaging-sw.js');
