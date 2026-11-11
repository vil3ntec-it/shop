/**
 * وبِ دکان برداشته شد (تصمیمِ صاحب، ۱۴۰۵/۰۸/۲۰) — صفحهٔ جانشینِ «برنامه را نصب کنید» (`index.html`) و
 * سرویس‌ورکرِ خداحافظی (`sw.js`) در کرومیومِ واقعی، روی همان چیدمانِ GitHub Pages (`/shop/` و `/shop/beta/`).
 *
 *   ۱) CSP: اسکریپت و سبکِ درون‌خطی فقط با هشِ خودشان (بی unsafe-inline/eval)، connect-src فقط 'self' (همان قاعدهٔ
 *      `WebViewHardeningTest` ِ پوستهٔ کهنهٔ اندروید)؛ sw.js بی fetch و با `BETA_PATH` (دروازهٔ ورک‌فلوی Pages).
 *   ۲) مرورگرِ تازه (بی داده): بی خطا و بی نقضِ CSP، بخشِ پشتیبان پنهان، هیچ پایگاهِ IndexedDB ساخته نمی‌شود و
 *      localStorage تهی می‌ماند؛ axe روشن و تاریک، بی بیرون‌زدگی در ۳۲۰؛ چهار زبان (English چپ‌به‌راست)؛ آیفون ⇒ راستِ ماجرا.
 *   ۳) دفترِ مانده در localStorage (+ حسابِ دیگر و کلیدهای رمز): پشتیبان همان قالبِ «دانلود نسخه پشتیبان» ِ وب
 *      (exportedAt + فهرست‌ها + settings) که `BackupRestore.decode` ِ برنامه می‌خواند؛ دفترِ حسابِ دیگر جدا و بی
 *      settings؛ فایلِ خام بی رمز و توکن؛ هیچ کلیدی عوض یا پاک نمی‌شود.
 *   ۴) دفتر در IndexedDB با عکس: ZIP ِ پشتیبانِ کاملِ برنامه (`shop-data.json` + `photos/<id>.jpg`، CRC درست، بایت‌به‌بایت
 *      همان عکس)، «فقط دفتر» همان JSON؛ IndexedDB دست نمی‌خورد. نبودِ `indexedDB.databases()` هم پایگاهی نمی‌سازد.
 *   ۵) دفترِ خراب ⇒ فقط فایلِ خام و پیامِ روشن؛ پلِ دانلودِ پوستهٔ WebViewِ بازنشستهٔ `android/` همان فایل را می‌گیرد.
 *   ۶) سرویس‌ورکرِ کهنه (همان رفتارِ پیشین: کش‌های `tohid-shop-v…`، منتظرِ دکمه) ⇒ با بازکردنِ صفحه sw.js ِ تازه
 *      فعال می‌شود، فقط کشِ همان کانال را پاک می‌کند (نه `/beta/`، نه برنامهٔ دیگرِ همین دامنه)، خودش را برمی‌دارد و
 *      برگهٔ بازِ کهنه را دوباره بار می‌کند؛ سپس همان برای `/beta/`. داده دست نمی‌خورد.
 *
 * اجرا:  node test/web-shop-gone.mjs   (در `npm run test:web`)
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import crypto from 'node:crypto';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}
if (!axeSrc) missingTool('axe-core', 'npm i -g axe-core@4.10.3');

const ROOT = path.join(import.meta.dirname, '..', '..');
const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
const sw = fs.readFileSync(path.join(ROOT, 'sw.js'), 'utf8');

let failures = 0;
const results = [];
async function step(name, fn) {
  try { await fn(); results.push(['✅', name]); } catch (err) { failures++; results.push(['❌', `${name} — ${err.message}`]); }
}

/* ---------------------------------- ۱) ایستا ---------------------------------- */
await step('CSP: هشِ اسکریپت و سبکِ درون‌خطی، بی unsafe-inline/eval، connect-src فقط self و blob:', () => {
  const csp = (/http-equiv="Content-Security-Policy"\s+content="([^"]+)"/.exec(html) || [])[1];
  assert.ok(csp, 'CSP نیست');
  const dir = Object.fromEntries(csp.split(';').map((d) => d.trim().split(/\s+/)).filter((d) => d[0]).map((d) => [d[0], d.slice(1)]));
  const hash = (t) => `'sha256-${crypto.createHash('sha256').update(t, 'utf8').digest('base64')}'`;
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  const styles = [...html.matchAll(/<style>([\s\S]*?)<\/style>/g)].map((m) => m[1]);
  assert.equal(scripts.length, 1);
  assert.equal(styles.length, 1);
  assert.deepEqual(dir['script-src'], [hash(scripts[0])], `هشِ درستِ اسکریپت: ${hash(scripts[0])}`);
  assert.deepEqual(dir['style-src'], [hash(styles[0])], `هشِ درستِ سبک: ${hash(styles[0])}`);
  //  `blob:`: پلِ دانلودِ پوستهٔ WebViewِ بازنشستهٔ `android/` فایل را با fetch ِ همان blob می‌خواند (WebViewHardeningTest می‌پذیرد)
  assert.deepEqual(dir['connect-src'], ["'self'", 'blob:']);
  for (const k of ['object-src', 'base-uri', 'form-action']) assert.deepEqual(dir[k], ["'none'"], k);
  assert.doesNotMatch(csp, /unsafe-inline|unsafe-eval|\*|https?:|wss?:/);
  assert.doesNotMatch(html, /<script\s+src=|\son[a-z]+\s*=\s*["']|\sstyle\s*=\s*["']|javascript:/i, 'بی اسکریپتِ بیرونی، رویداد یا style ِ درون‌خطی');
  assert.doesNotMatch(scripts[0], /\beval\s*\(|new\s+Function|innerHTML|serviceWorker\.register|\.setItem\(|\.removeItem\(|\.clear\(\)|deleteDatabase|\.put\(|\.delete\(/,
    'صفحه نه چیزی می‌نویسد، نه پاک می‌کند، نه سرویس‌ورکر ثبت می‌کند');
});

await step('sw.js: بی fetch، skipWaiting، فقط کشِ همین کانال، unregister، و BETA_PATH برای ورک‌فلوی Pages', () => {
  assert.ok(sw.includes('BETA_PATH'), 'ورک‌فلوی «وب — GitHub Pages» BETA_PATH را در هر دو sw.js می‌جوید');
  assert.doesNotMatch(sw, /addEventListener\(\s*['"]fetch/);
  assert.match(sw, /skipWaiting\(\)/);
  assert.match(sw, /registration\.unregister\(\)/);
  assert.match(sw, /k\.startsWith\(OWN_CACHE\)/);
  const code = sw.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  assert.doesNotMatch(code, /localStorage|indexedDB/i, 'دادهٔ دکان هرگز');
});

/* ------------------------------ چیدمانِ GitHub Pages ------------------------------ */
//  همان رفتارِ sw.js ِ پیشین (نسخهٔ v76): پوسته در `tohid-shop-v76-shell` (و `beta-…` در /beta/)، ناوبری اول شبکه،
//  و منتظرِ دکمهٔ «به‌روزرسانی» (بی skipWaiting) — برای سنجیدنِ جابه‌جایی بی وابستگی به تاریخچهٔ git
const OLD_SW = `const BETA = /\\/beta\\/sw\\.js$/.test(self.location.pathname);
const VERSION = (BETA ? 'beta-' : '') + 'tohid-shop-v76';
self.addEventListener('install', (e) => e.waitUntil(caches.open(VERSION + '-shell').then((c) => c.put('./index.html', new Response('old-app')))
  .then(() => caches.open(VERSION + '-fonts')).then((c) => c.put('./font', new Response('f')))));
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));
self.addEventListener('message', (e) => { if (e.data === 'skip-waiting' || (e.data && e.data.type === 'SKIP_WAITING')) self.skipWaiting(); });
self.addEventListener('fetch', (e) => { if (e.request.mode === 'navigate') e.respondWith(fetch(e.request).catch(() => caches.match('./index.html'))); });`;
let swNow = OLD_SW;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png', '.ttf': 'font/ttf', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (p === '/other/blank.html') { res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); res.end('<!doctype html><title>x</title>'); return; }
  const m = /^\/shop\/(?:beta\/)?(.*)$/.exec(p);
  if (!m) { res.writeHead(404); res.end(); return; }
  let rel = m[1] || 'index.html';
  if (rel === 'sw.js') {
    res.writeHead(200, { 'content-type': 'text/javascript; charset=utf-8', 'cache-control': 'no-store' });
    res.end(swNow);
    return;
  }
  if (!/^(index\.html|download\.html|privacy\.html|manifest\.webmanifest|icons\/[\w.-]+|fonts\/[\w.-]+)$/.test(rel)) { res.writeHead(404); res.end(); return; }
  const file = path.join(ROOT, rel);
  res.writeHead(200, { 'content-type': (TYPES[path.extname(rel)] || 'application/octet-stream') + '; charset=utf-8', 'cache-control': 'no-store' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}`;

const browser = await chromium.launch();
const LEDGER = {
  debtors: [{ id: 'd1', name: 'احمد', phone: '0700000001' }], transactions: [{ id: 't1', debtorId: 'd1', amount: 500 }],
  expenses: [], expenseCategories: ['کرایه'], products: [{ id: 'p1', name: 'چای سبز', price: 120, photo: true }, { id: 'p2', name: 'بوره', price: 80 }],
  productCategories: [], productUnits: [], warehouseEntries: [{ id: 'w1', productId: 'p1', qty: 10 }],
  sales: [{ id: 's1', invoiceNo: 1, total: 240 }, { id: 's2', invoiceNo: 2, total: 80 }], saleItems: [{ id: 'si1', saleId: 's1', productId: 'p1', quantity: 2 }],
  returns: [], nextInvoiceNo: 3, suppliers: [], purchases: [], supplierPayments: [], stockMovements: [], priceHistory: [], auditLog: [],
};
const VAULT = { ...LEDGER, products: [{ id: 'v1', name: 'دکانِ دوم' }], sales: [], debtors: [], transactions: [], warehouseEntries: [], saleItems: [] };
const SETTINGS = { shopName: 'دکانِ آزمون', currency: 'AFN' };
const SECRETS = { 'tohid-license-v1': '{"accessToken":"AT-secret","refreshToken":"RT-secret"}', 'tohid-account-key-v1': 'key-secret', 'tohid-shop-auth-v1': '{"hash":"x"}', 'tohid-saved-logins-v1': '[1]' };

const readFile = async (dl) => fs.readFileSync(await dl.path());
const lsSnapshot = (pg) => pg.evaluate(() => { const o = {}; for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); o[k] = localStorage.getItem(k); } return o; });
async function axe(pg, label) {
  if (!(await pg.evaluate(() => Boolean(window.axe)))) await pg.evaluate(axeSrc);
  const out = await pg.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa'] } })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  assert.deepEqual(out, [], `axe (${label})`);
}
function watch(pg) {
  const bad = [];
  pg.on('pageerror', (e) => bad.push(String(e)));
  pg.on('console', (m) => { if (m.type() === 'error' || /Content Security Policy/i.test(m.text())) bad.push(m.text()); });
  return bad;
}
async function openPage(ctx, url = `${base}/shop/`) {
  const pg = await ctx.newPage();
  const bad = watch(pg);
  await pg.goto(url);
  await pg.waitForFunction(() => document.documentElement.lang && document.querySelector('#backup'));
  await pg.waitForTimeout(300);   //  scan ِ ناهمگام (IndexedDB) تمام شود
  return { pg, bad };
}
/** خوانندهٔ کمینهٔ ZIP ِ STORE با CRC ِ خودش (همان سنجشِ `ZipInputStream`) */
function unzip(buf) {
  const table = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc32 = (b) => { let c = 0xFFFFFFFF; for (const x of b) c = table[(c ^ x) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const out = new Map();
  let at = 0;
  while (buf.readUInt32LE(at) === 0x04034b50) {
    assert.equal(buf.readUInt16LE(at + 8), 0, 'STORE');
    assert.equal(buf.readUInt16LE(at + 6) & 0x0800, 0x0800, 'نامِ UTF-8');
    const crc = buf.readUInt32LE(at + 14), size = buf.readUInt32LE(at + 18), n = buf.readUInt16LE(at + 26), x = buf.readUInt16LE(at + 28);
    const name = buf.subarray(at + 30, at + 30 + n).toString('utf8');
    const data = buf.subarray(at + 30 + n + x, at + 30 + n + x + size);
    assert.equal(crc32(data), crc, `CRC ِ ${name}`);
    out.set(name, Buffer.from(data));
    at += 30 + n + x + size;
  }
  const end = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  assert.ok(end > 0, 'رکوردِ پایانیِ ZIP');
  assert.equal(buf.readUInt16LE(end + 10), out.size, 'شمارِ قلم‌ها در رکوردِ پایانی');
  assert.equal(buf.readUInt32LE(end + 16), at, 'جای فهرستِ مرکزی');
  return out;
}

try {
  /* ---------------------------- ۲) مرورگرِ تازه، بی داده ---------------------------- */
  await step('بی داده: صفحه بی خطا، پشتیبان پنهان، هیچ پایگاه و هیچ کلیدی ساخته نمی‌شود', async () => {
    const ctx = await browser.newContext();
    const { pg, bad } = await openPage(ctx);
    assert.equal(await pg.isVisible('#backup'), false);
    assert.equal(await pg.isVisible('#iphone-note'), false);
    assert.equal(await pg.getAttribute('#get-android', 'href'), 'https://github.com/vil3ntec-it/shop/releases/download/tohid-native/MyShop-Android.apk');
    assert.equal(await pg.getAttribute('#get-computer', 'href'), 'download.html');
    assert.deepEqual(await pg.evaluate(() => indexedDB.databases()), []);
    assert.deepEqual(await lsSnapshot(pg), {});
    assert.deepEqual(bad, []);
    await axe(pg, 'روشن');
    await ctx.close();
  });

  await step('تاریک، ۳۲۰ پیکسل و آیفون: axe، بی بیرون‌زدگی، و «برنامهٔ آیفون هنوز منتشر نشده»', async () => {
    const ctx = await browser.newContext({ colorScheme: 'dark', viewport: { width: 320, height: 640 },
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1' });
    const { pg, bad } = await openPage(ctx);
    assert.equal(await pg.isVisible('#iphone-note'), true);
    assert.ok(await pg.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'بیرون‌زدگیِ افقی');
    assert.deepEqual(bad, []);
    await axe(pg, 'تاریک ۳۲۰');
    await ctx.close();
  });

  await step('چهار زبان: English چپ‌به‌راست، دری و پشتو راست‌به‌چپ؛ ?lang= و axe', async () => {
    const ctx = await browser.newContext();
    const { pg, bad } = await openPage(ctx);
    await pg.click('button[data-lang="en"]');
    assert.deepEqual(await pg.evaluate(() => [document.documentElement.lang, document.documentElement.dir, document.title]), ['en', 'ltr', 'My Shop — install the app']);
    assert.match(await pg.textContent('h1'), /^Shop management/);
    assert.equal(await pg.getAttribute('button[data-lang="en"]', 'aria-pressed'), 'true');
    await axe(pg, 'English');
    await pg.click('button[data-lang="ps"]');
    assert.deepEqual(await pg.evaluate(() => [document.documentElement.lang, document.documentElement.dir]), ['ps', 'rtl']);
    await pg.click('button[data-lang="prs"]');
    assert.deepEqual(await pg.evaluate(() => [document.documentElement.lang, document.documentElement.dir]), ['fa-AF', 'rtl']);
    assert.match(await pg.textContent('h1'), /دکان/);
    const q = await ctx.newPage();
    await q.goto(`${base}/shop/?lang=ps`);
    assert.equal(await q.evaluate(() => document.documentElement.lang), 'ps');
    assert.deepEqual(bad, []);
    await ctx.close();
  });

  /* --------------------------- ۳) دفترِ مانده در localStorage --------------------------- */
  await step('دفترِ localStorage: پشتیبانِ همان قالبِ وب، دفترِ حسابِ دیگر جدا، فایلِ خام بی رمز؛ هیچ کلیدی عوض نمی‌شود', async () => {
    const ctx = await browser.newContext();
    const seed = await ctx.newPage();
    await seed.goto(`${base}/other/blank.html`);
    await seed.evaluate(({ L, V, S, X }) => {
      localStorage.setItem('tohid-shop-data-v1', JSON.stringify(L));
      localStorage.setItem('tohid-shop-settings-v1', JSON.stringify(S));
      localStorage.setItem('tohid-ledger-vault-acct_2', JSON.stringify(V));
      localStorage.setItem('tohid-sync-v1-q', '[{"op":1}]');
      localStorage.setItem('other-app-key', 'x');
      for (const [k, v] of Object.entries(X)) localStorage.setItem(k, v);
    }, { L: LEDGER, V: VAULT, S: SETTINGS, X: SECRETS });
    const before = await lsSnapshot(seed);
    const { pg, bad } = await openPage(ctx);
    assert.equal(await pg.isVisible('#backup'), true);
    assert.equal((await pg.textContent('#backup-sum')).trim(), '۲ کالا، ۲ فروش، ۱ قرض‌دار');
    assert.equal(await pg.isVisible('#btn-backup-json'), false, 'بی عکس ⇒ یک دکمه، JSON');
    await axe(pg, 'با دفتر');

    const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click('#btn-backup')]);
    assert.match(dl.suggestedFilename(), /^tohid-shop-backup-\d{4}-\d{2}-\d{2}\.json$/);
    const j = JSON.parse(await readFile(dl));
    assert.equal(Object.keys(j)[0], 'exportedAt');
    assert.ok(!Number.isNaN(Date.parse(j.exportedAt)), 'exportedAt ⇒ `BackupRestore.decode` آن را پشتیبانِ این برنامه می‌داند');
    for (const [k, v] of Object.entries(LEDGER)) assert.deepEqual(j[k], v, k);
    assert.deepEqual(j.settings, SETTINGS);
    assert.match(await pg.textContent('#backup-status'), /tohid-shop-backup-/);

    const more = pg.locator('#more-ledgers li');
    assert.equal(await more.count(), 1);
    assert.match(await more.first().textContent(), /حسابِ دیگری[\s\S]*۱ کالا/);
    const [dv] = await Promise.all([pg.waitForEvent('download'), more.first().locator('button').click()]);
    assert.match(dv.suggestedFilename(), /^tohid-shop-backup-\d{4}-\d{2}-\d{2}-2\.json$/);
    const jv = JSON.parse(await readFile(dv));
    assert.deepEqual(jv.products, VAULT.products);
    assert.equal(jv.settings, undefined, 'تنظیماتِ این مرورگر مالِ دفترِ خودش است');

    await pg.click('#backup summary');
    const [dr] = await Promise.all([pg.waitForEvent('download'), pg.click('#btn-raw')]);
    assert.match(dr.suggestedFilename(), /^tohid-shop-raw-\d{4}-\d{2}-\d{2}\.json$/);
    const rawText = (await readFile(dr)).toString('utf8');
    const raw = JSON.parse(rawText);
    assert.equal(raw.format, 'tohid-web-raw-v1');
    assert.equal(raw.localStorage['tohid-shop-data-v1'], JSON.stringify(LEDGER));
    assert.equal(raw.localStorage['tohid-sync-v1-q'], '[{"op":1}]', 'صفِ همگام‌سازیِ نرفته هم');
    assert.equal(raw.localStorage['other-app-key'], undefined, 'فقط کلیدهای tohid-');
    assert.deepEqual(raw.left_out.sort(), Object.keys(SECRETS).sort());
    assert.doesNotMatch(rawText, /secret|AT-secret|RT-secret/);

    assert.deepEqual(await lsSnapshot(pg), before, 'localStorage دست نخورد');
    assert.deepEqual(await pg.evaluate(() => indexedDB.databases()), [], 'پایگاهی ساخته نشد');
    assert.deepEqual(bad, []);
    await ctx.close();
  });

  /* ----------------------------- ۴) دفتر در IndexedDB با عکس ----------------------------- */
  const seedIdb = (pg, withPhotos) => pg.evaluate(async ({ L, withPhotos: wp }) => {
    const open = (name, store) => new Promise((res, rej) => {
      const r = indexedDB.open(name, 1);
      r.onupgradeneeded = () => r.result.createObjectStore(store);
      r.onsuccess = () => res(r.result);
      r.onerror = () => rej(r.error);
    });
    const put = (db, store, entries) => new Promise((res, rej) => {
      const t = db.transaction(store, 'readwrite');
      for (const [k, v] of entries) t.objectStore(store).put(v, k);
      t.oncomplete = res; t.onerror = () => rej(t.error);
    });
    localStorage.setItem('tohid-ledger-store-v1', 'idb');
    const kv = await open('tohid-ledger', 'kv');
    await put(kv, 'kv', [['tohid-shop-data-v1', JSON.stringify(L)], ['tohid-ledger-owner-v1', 'u1']]);
    kv.close();
    if (!wp) return [];
    const jpeg = (color) => new Promise((res) => {
      const c = document.createElement('canvas'); c.width = 8; c.height = 8;
      const g = c.getContext('2d'); g.fillStyle = color; g.fillRect(0, 0, 8, 8);
      c.toBlob(res, 'image/jpeg', 0.72);
    });
    const a = await jpeg('#c00'), b = await jpeg('#0c0');
    const ph = await open('tohid-photos', 'products');
    await put(ph, 'products', [['p1', a], ['p2', b], ['../evil', a]]);
    ph.close();
    const bytes = async (bl) => Array.from(new Uint8Array(await bl.arrayBuffer()));
    return [await bytes(a), await bytes(b)];
  }, { L: LEDGER, withPhotos });
  const idbDump = (pg) => pg.evaluate(async () => {
    const out = {};
    for (const [name, store] of [['tohid-ledger', 'kv'], ['tohid-photos', 'products']]) {
      if (!(await indexedDB.databases()).some((d) => d.name === name)) continue;
      const db = await new Promise((res) => { const r = indexedDB.open(name); r.onsuccess = () => res(r.result); });
      out[name] = await new Promise((res) => {
        const t = db.transaction(store, 'readonly');
        const r = t.objectStore(store).getAllKeys();
        r.onsuccess = () => res(r.result.map(String).sort());
      });
      db.close();
    }
    return out;
  });

  await step('دفترِ IndexedDB با عکس: ZIP ِ پشتیبانِ کاملِ برنامه و «فقط دفتر»؛ IndexedDB دست نمی‌خورد', async () => {
    const ctx = await browser.newContext();
    const seed = await ctx.newPage();
    await seed.goto(`${base}/other/blank.html`);
    const [jpgA, jpgB] = await seedIdb(seed, true);
    const before = await idbDump(seed);
    const lsBefore = await lsSnapshot(seed);
    const { pg, bad } = await openPage(ctx);
    assert.equal(await pg.isVisible('#backup'), true);
    assert.match(await pg.textContent('#btn-backup'), /با ۲ عکس \(ZIP\)/, 'عکسِ با نامِ ناامن شمرده نمی‌شود');
    assert.equal(await pg.isVisible('#btn-backup-json'), true);
    const [dz] = await Promise.all([pg.waitForEvent('download'), pg.click('#btn-backup')]);
    assert.match(dz.suggestedFilename(), /^tohid-shop-backup-\d{4}-\d{2}-\d{2}\.zip$/);
    const z = unzip(await readFile(dz));
    assert.deepEqual([...z.keys()], ['shop-data.json', 'photos/p1.jpg', 'photos/p2.jpg']);
    const j = JSON.parse(z.get('shop-data.json').toString('utf8'));
    assert.deepEqual(j.products, LEDGER.products);
    assert.equal(typeof j.exportedAt, 'string');
    assert.deepEqual([...z.get('photos/p1.jpg')], jpgA);
    assert.deepEqual([...z.get('photos/p2.jpg')], jpgB);
    assert.equal(z.get('photos/p1.jpg')[0], 0xFF, 'JPEG');
    const [dj] = await Promise.all([pg.waitForEvent('download'), pg.click('#btn-backup-json')]);
    assert.deepEqual(JSON.parse(await readFile(dj)).sales, LEDGER.sales);
    assert.deepEqual(await idbDump(pg), before, 'IndexedDB دست نخورد');
    assert.deepEqual(await lsSnapshot(pg), lsBefore);
    assert.deepEqual(bad, []);
    await ctx.close();
  });

  await step('بی `indexedDB.databases()` (Safari ِ کهنه): پایگاهِ نبوده با لغوِ ارتقا ساخته نمی‌شود؛ بودهٔ آن خوانده می‌شود', async () => {
    const ctx = await browser.newContext();
    await ctx.addInitScript(() => { if (location.pathname.startsWith('/shop/')) delete IDBFactory.prototype.databases; });
    const seed = await ctx.newPage();
    await seed.goto(`${base}/other/blank.html`);
    await seed.evaluate(() => localStorage.setItem('tohid-ledger-store-v1', 'idb'));
    const { pg, bad } = await openPage(ctx);
    assert.equal(await pg.isVisible('#backup'), false);
    assert.deepEqual(await seed.evaluate(() => indexedDB.databases()), [], 'هیچ پایگاهی ساخته نشد');
    await seedIdb(seed, false);
    const again = await openPage(ctx);
    assert.equal(await again.pg.isVisible('#backup'), true);
    assert.equal(await again.pg.isVisible('#btn-backup-json'), false);
    assert.deepEqual((await seed.evaluate(() => indexedDB.databases())).map((d) => d.name), ['tohid-ledger']);
    assert.deepEqual([...bad, ...again.bad], []);
    await ctx.close();
  });

  /* --------------------------------- ۵) دفترِ خراب --------------------------------- */
  await step('دفترِ خراب: پیامِ روشن و فقط فایلِ خام', async () => {
    const ctx = await browser.newContext();
    const seed = await ctx.newPage();
    await seed.goto(`${base}/other/blank.html`);
    await seed.evaluate(() => localStorage.setItem('tohid-shop-data-v1', '{"products":[{"id":"p1"'));
    const { pg, bad } = await openPage(ctx);
    assert.equal(await pg.isVisible('#backup-damaged'), true);
    assert.equal(await pg.isVisible('#btn-backup'), false);
    await pg.click('#backup summary');
    const [dr] = await Promise.all([pg.waitForEvent('download'), pg.click('#btn-raw')]);
    assert.equal(JSON.parse(await readFile(dr)).localStorage['tohid-shop-data-v1'], '{"products":[{"id":"p1"');
    assert.deepEqual(bad, []);
    await ctx.close();
  });

  await step('پوستهٔ WebViewِ بازنشستهٔ android/: پلِ دانلودِ خودش (DOWNLOAD_BRIDGE) فایلِ پشتیبان را زیرِ همین CSP می‌گیرد', async () => {
    const kt = fs.readFileSync(path.join(ROOT, 'android/app/src/main/java/af/tohid/shop/MainActivity.kt'), 'utf8');
    const bridge = (/const val DOWNLOAD_BRIDGE = """([\s\S]*?)"""/.exec(kt) || [])[1];
    assert.ok(bridge, 'DOWNLOAD_BRIDGE در MainActivity.kt پیدا نشد');
    const ctx = await browser.newContext();
    await ctx.addInitScript(() => { window.__saved = []; window.ShopAndroid = { saveFile: (b64, name, type) => window.__saved.push({ b64, name, type }) }; });
    await ctx.addInitScript(bridge);
    const seed = await ctx.newPage();
    await seed.goto(`${base}/other/blank.html`);
    await seed.evaluate((L) => localStorage.setItem('tohid-shop-data-v1', JSON.stringify(L)), LEDGER);
    const { pg, bad } = await openPage(ctx);
    let downloads = 0;
    pg.on('download', () => { downloads++; });
    await pg.click('#btn-backup');
    await pg.waitForFunction(() => window.__saved.length === 1, null, { timeout: 5000 });
    const [got] = await pg.evaluate(() => window.__saved);
    assert.match(got.name, /^tohid-shop-backup-\d{4}-\d{2}-\d{2}\.json$/);
    assert.deepEqual(JSON.parse(Buffer.from(got.b64, 'base64').toString('utf8')).products, LEDGER.products);
    assert.equal(downloads, 0, 'دانلودِ مرورگر نه؛ همان پل');
    assert.deepEqual(bad, [], 'بی نقضِ CSP (connect-src blob:)');
    await ctx.close();
  });

  /* ---------------------------- ۶) سرویس‌ورکرِ کهنه ⇐ خداحافظی ---------------------------- */
  await step('سرویس‌ورکرِ کهنه: فقط کشِ همان کانال پاک، خودش برداشته، برگهٔ کهنه دوباره بار؛ /beta/ و برنامهٔ دیگر دست‌نخورده', async () => {
    swNow = OLD_SW;
    const ctx = await browser.newContext();
    const obs = await ctx.newPage();
    await obs.goto(`${base}/other/blank.html`);
    await obs.evaluate(async () => {
      localStorage.setItem('tohid-shop-data-v1', '{"products":[{"id":"p1"}]}');
      await (await caches.open('other-app-v1')).put('/x', new Response('keep'));
      const ready = (reg) => new Promise((r) => {
        const w = reg.installing || reg.waiting || reg.active;
        if (w.state === 'activated') return r();
        w.addEventListener('statechange', () => { if (w.state === 'activated') r(); });
      });
      await ready(await navigator.serviceWorker.register('/shop/sw.js', { scope: '/shop/' }));
      await ready(await navigator.serviceWorker.register('/shop/beta/sw.js', { scope: '/shop/beta/' }));
    });
    const state = () => obs.evaluate(async () => ({
      regs: (await navigator.serviceWorker.getRegistrations()).map((r) => new URL(r.scope).pathname).sort(),
      caches: (await caches.keys()).sort(),
      data: localStorage.getItem('tohid-shop-data-v1'),
    }));
    //  waitForFunction با پیش‌شرطِ async وعده را «درست» می‌گیرد و منتظر نمی‌ماند ⇒ پرسیدنِ پیاپی از همین‌جا
    const until = async (fn, label) => {
      for (let t = Date.now(); Date.now() - t < 15000; await obs.waitForTimeout(100)) if (fn(await state())) return;
      throw new Error(`منتظرِ «${label}» ماند: ${JSON.stringify(await state())}`);
    };
    const s0 = await state();
    assert.deepEqual(s0.regs, ['/shop/', '/shop/beta/']);
    assert.deepEqual(s0.caches, ['beta-tohid-shop-v76-fonts', 'beta-tohid-shop-v76-shell', 'other-app-v1', 'tohid-shop-v76-fonts', 'tohid-shop-v76-shell']);

    //  برگهٔ کهنه‌ای که زیرِ کارگرِ کهنه باز است (پیش از رسیدنِ sw.js ِ تازه)
    const old = await ctx.newPage();
    await old.goto(`${base}/shop/`);
    await old.waitForFunction(() => !!navigator.serviceWorker.controller);
    await old.evaluate(() => { window.__old = 1; });

    swNow = sw;   //  انتشارِ تازه
    const fresh = await ctx.newPage();
    await fresh.goto(`${base}/shop/`);
    await until((x) => !x.regs.includes('/shop/'), 'برداشتنِ کارگرِ /shop/');
    await old.waitForFunction(() => window.__old === undefined && document.readyState === 'complete', null, { timeout: 15000 });
    const s1 = await state();
    assert.deepEqual(s1.regs, ['/shop/beta/'], 'کارگرِ /beta/ کارِ خودش است');
    assert.deepEqual(s1.caches, ['beta-tohid-shop-v76-fonts', 'beta-tohid-shop-v76-shell', 'other-app-v1']);
    assert.equal(await old.evaluate(() => navigator.serviceWorker.controller), null, 'برگهٔ دوباره‌بارشده دیگر زیرِ کارگر نیست');
    assert.match(await old.textContent('h1'), /فقط در برنامه/);

    const beta = await ctx.newPage();
    await beta.goto(`${base}/shop/beta/`);
    await until((x) => x.regs.length === 0, 'برداشتنِ کارگرِ /shop/beta/');
    const s2 = await state();
    assert.deepEqual(s2.caches, ['other-app-v1'], 'کشِ برنامهٔ دیگرِ همین دامنه می‌ماند');
    assert.equal(s2.data, s0.data, 'دادهٔ دکان دست نخورد');
    await ctx.close();
  });
} finally {
  await browser.close();
  server.close();
}

for (const [mark, name] of results) console.log(`${mark} ${name}`);
if (failures) {
  console.error(`\n${failures} سنجه رد شد`);
  process.exit(1);
}
console.log(`\nهمه ${results.length} سنجه قبول`);
