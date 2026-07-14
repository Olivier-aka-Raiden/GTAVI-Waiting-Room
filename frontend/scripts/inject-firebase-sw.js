// Build-time script: reads the service worker template with placeholders,
// injects actual VITE_* env var values, and writes the result to
// public/firebase-messaging-sw.js (which Vite then copies to dist/).
// The template file is NEVER modified — placeholders survive across builds.

import { readFileSync, writeFileSync } from 'fs';
import { resolve } from 'path';

const templatePath = resolve('public/firebase-messaging-sw.template.js');
const outputPath = resolve('public/firebase-messaging-sw.js');

let sw = readFileSync(templatePath, 'utf-8');

const replacements = {
  'VITE_FIREBASE_API_KEY_PLACEHOLDER': process.env.VITE_FIREBASE_API_KEY ?? '',
  'VITE_FIREBASE_AUTH_DOMAIN_PLACEHOLDER': process.env.VITE_FIREBASE_AUTH_DOMAIN ?? '',
  'VITE_FIREBASE_PROJECT_ID_PLACEHOLDER': process.env.VITE_FIREBASE_PROJECT_ID ?? '',
  'VITE_FIREBASE_STORAGE_BUCKET_PLACEHOLDER': process.env.VITE_FIREBASE_STORAGE_BUCKET ?? '',
  'VITE_FIREBASE_MESSAGING_SENDER_ID_PLACEHOLDER': process.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
  'VITE_FIREBASE_APP_ID_PLACEHOLDER': process.env.VITE_FIREBASE_APP_ID ?? '',
};

let replaced = 0;
for (const [placeholder, value] of Object.entries(replacements)) {
  if (sw.includes(placeholder)) {
    sw = sw.replaceAll(placeholder, value);
    replaced++;
  }
}

writeFileSync(outputPath, sw);
console.log(`[build:sw] Firebase config injected (${replaced}/6 fields replaced)`);
