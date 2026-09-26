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
  assert.equal(state.shown[0].options.icon, '/assets/icon-192.png');
  assert.equal(state.shown[0].options.badge, '/assets/notification-badge-96.png');
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
const editionSource = readFileSync(new URL('../src/features/editions/EditionOffers.tsx', import.meta.url), 'utf8');
let editionCode = ts.transpileModule(editionSource, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
editionCode = editionCode.replaceAll('"react/jsx-runtime"', JSON.stringify(pathToFileURL(require.resolve('react/jsx-runtime')).href));
const { EditionOffers } = await import('data:text/javascript;base64,' + Buffer.from(editionCode).toString('base64'));
test('real retailer variants share one expandable retailer/platform group', () => {
  const offers = [1,2,3].map(id => ({
    id: String(id), retailerCode: 'WOG', retailerName: 'WOG.ch', platform: 'PS5',
    price: 72.9, currency: 'CHF', availabilityStatus: 'PREORDER_AVAILABLE', preorderAvailable: true,
    url: 'https://www.wog.ch/fr/index.cfm/details/product/' + id + '-Grand-Theft-Auto-6',
  }));
  const html = renderToStaticMarkup(React.createElement(EditionOffers, { offers }));
  assert.equal((html.match(/<summary/g) ?? []).length, 1);
  assert.ok(html.includes('3 product options'));
  assert.equal((html.match(/<a /g) ?? []).length, 3);
});
test('album formats retain limited-edition and purchase labels on one card', () => {
  const html = renderToStaticMarkup(React.createElement(ProductCard, { item: {
    id: 'album', name: 'Grand Theft Auto VI: The Album', category: 'ALBUM',
    sourceUrl: 'https://www.rockstargames.com/VI/music',
    offers: [
      { id: 'vinyl', variant: 'Vinyl', purchaseUrl: 'https://gtavithealbum.lnk.to/vinyl' },
      { id: 'limited', variant: 'Limited vinyl', limited: true, purchaseUrl: 'https://gtavithealbum.lnk.to/limitededitionvinyl' },
    ],
  } }));
  assert.equal((html.match(/<article/g) ?? []).length, 1);
  assert.ok(html.includes('Limited edition'));
  assert.ok(html.includes('limitededitionvinyl'));
  assert.ok(html.includes('Price not announced'));
});

async function componentModule(path, replacements = {}) {
  let compiled = ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), { compilerOptions: {
    jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022,
  } }).outputText;
  for (const dependency of ['react/jsx-runtime', 'react']) {
    const url = JSON.stringify(pathToFileURL(require.resolve(dependency)).href);
    compiled = compiled.replaceAll('"' + dependency + '"', url).replaceAll("'" + dependency + "'", url);
  }
  for (const [from, to] of Object.entries(replacements)) compiled = compiled.replaceAll(from, to);
  return import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));
}
const cardsUrl = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64');
const { NewsCarousel, swipeStep } = await componentModule('../src/features/news/NewsCarousel.tsx', { "'./NewsCards'": JSON.stringify(cardsUrl) });
const { sectionAtPosition } = await componentModule('../src/hooks/useActiveSection.ts');
const announcements = [
  { id: 'newest', title: 'Newest announcement', sourceUrl: product.sourceUrl, category: 'NEWS' },
  { id: 'older', title: 'Older announcement', sourceUrl: product.sourceUrl, category: 'NEWS' },
];
test('news renders one card, opens linked older articles, and exposes navigation', () => {
  const props = { items: announcements, hasMore: true, busy: false, onLoadMore() {} };
  const html = renderToStaticMarkup(React.createElement(NewsCarousel, props));
  assert.equal((html.match(/<article/g) ?? []).length, 1);
  assert.ok(html.includes('Newest announcement'));
  assert.ok(!html.includes('id="news-older"'));
  assert.ok(html.includes('Load older announcements'));
  const linked = renderToStaticMarkup(React.createElement(NewsCarousel, { ...props, selectedId: 'older' }));
  assert.ok(linked.includes('id="news-older"'));
  assert.ok(!linked.includes('id="news-newest"'));
});
test('swipes change cards only for deliberate horizontal movement', () => {
  assert.equal(swipeStep(-90, 10), 1);
  assert.equal(swipeStep(90, -10), -1);
  assert.equal(swipeStep(-20, 0), 0);
  assert.equal(swipeStep(90, 120), 0);
});
test('scroll navigation handles tall sections, reverse scrolling, and the final short section', () => {
  assert.equal(sectionAtPosition([{ id: 'editions', top: -1800 }, { id: 'music', top: 220 }], 120, false), 'editions');
  assert.equal(sectionAtPosition([{ id: 'editions', top: -2100 }, { id: 'music', top: 100 }], 120, false), 'music');
  assert.equal(sectionAtPosition([{ id: 'editions', top: -1800 }, { id: 'music', top: 220 }], 120, false), 'editions');
  assert.equal(sectionAtPosition([{ id: 'updates', top: -50 }, { id: 'alerts', top: 500 }], 120, true), 'alerts');
  assert.equal(sectionAtPosition([], 120, false), undefined);
});
