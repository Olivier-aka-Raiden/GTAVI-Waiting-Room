import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const require = createRequire(import.meta.url);
let source = readFileSync(new URL('../src/features/news/NewsCards.tsx', import.meta.url), 'utf8')
  .replace("import { API_BASE } from '../../api/config';", "const API_BASE = '';");
let code = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
for (const dependency of ['react/jsx-runtime', 'react']) code = code.replaceAll('"' + dependency + '"', JSON.stringify(pathToFileURL(require.resolve(dependency)).href));
code = code.replaceAll("from 'react'", "from " + JSON.stringify(pathToFileURL(require.resolve("react")).href));
const { ProductCard, NewsCard } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
const product = { id: 'x', name: 'GTA VI Collector Box', category: 'COLLECTIBLE',
  sourceUrl: 'https://www.rockstargames.com/VI/', imageUrl: 'https://example.com/box.png',
  purchaseUrl: 'https://store.rockstargames.com/merchandise/box', price: 374.99, currency: 'CHF',
  availability: 'PREORDER', gameIncluded: false, limited: true };
test('collector card shows actual image, region currency, preorder, and game exclusion', () => {
  const html = renderToStaticMarkup(React.createElement(ProductCard, { item: product }));
  for (const text of ['box.png', '374.99', 'CHF', 'Pre-order', 'Game sold separately', 'Limited edition']) assert.ok(html.includes(text), text);
});
test('missing details have honest fallbacks and unsafe links are not rendered', () => {
  const html = renderToStaticMarkup(React.createElement(ProductCard, { item: { ...product, price: null, imageUrl: null, purchaseUrl: 'javascript:alert(1)' } }));
  assert.ok(html.includes('Price not announced'));
  assert.ok(html.includes('Image not available yet'));
  assert.ok(!html.includes('javascript:'));
  assert.ok(!html.includes('>Pre-order<'));
});
test('metadata-only Album announcement remains visible', () => {
  const html = renderToStaticMarkup(React.createElement(NewsCard, { item: {
    id: 'abc', title: 'GTA VI: The Album', category: 'MUSIC', sourceUrl: product.sourceUrl, enrichmentPending: true,
  }}));
  assert.ok(html.includes('The Album'));
  assert.ok(html.includes('More details are being checked'));
  assert.ok(html.includes('Read the announcement'));
});
function worker() {
  const state = { shown: [], handlers: {}, background: null, opened: [] };
  const context = {
    URL, importScripts() {},
    firebase: { initializeApp() {}, messaging: () => ({ onBackgroundMessage: callback => { state.background = callback; } }) },
    self: { location: { origin: 'https://waiting.test' }, addEventListener: (type, callback) => { state.handlers[type] = callback; },
      registration: { showNotification: (title, options) => state.shown.push({ title, options }) } },
    clients: { matchAll: async () => [], openWindow: async url => state.opened.push(url) },
  };
  vm.runInNewContext(readFileSync(new URL('../public/firebase-messaging-sw.template.js', import.meta.url), 'utf8'), context);
  return state;
}
test('background messages display once and different events retain different tags', () => {
  const state = worker();
  state.background({ notification: { title: 'Already automatically displayed' } });
  assert.equal(state.shown.length, 0);
  state.background({ data: { title: 'Album', eventId: 'a' } });
  state.background({ data: { title: 'Collector', eventId: 'b' } });
  assert.equal(state.shown.length, 2);
  assert.notEqual(state.shown[0].options.tag, state.shown[1].options.tag);
});
test('notification click opens the selected article and blocks external redirects', async () => {
  const state = worker();
  let pending;
  const click = url => state.handlers.notificationclick({ notification: { close() {}, data: { url } }, waitUntil: value => { pending = value; } });
  click('/?news=abc'); await pending;
  assert.deepEqual(state.opened, ['https://waiting.test/?news=abc']);
  click('https://evil.test/'); await pending;
  assert.equal(state.opened.length, 1);
});

test('regional offers retain both prices and variant labels', () => {
  const html = renderToStaticMarkup(React.createElement(ProductCard, { item: {
    ...product,
    offers: [
      { id: 'ch', purchaseUrl: product.purchaseUrl, price: 374.99, currency: 'CHF', market: 'Switzerland', variant: 'red', availability: 'PREORDER' },
      { id: 'us', purchaseUrl: product.purchaseUrl, price: 399.99, currency: 'USD', market: 'United States', variant: 'blue', availability: 'AVAILABLE' },
    ],
  }}));
  for (const text of ['374.99', '399.99', 'CHF', 'USD', 'Switzerland', 'United States', 'red', 'blue']) assert.ok(html.includes(text), text);
});