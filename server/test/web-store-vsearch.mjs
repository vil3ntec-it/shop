/**
 * ویترین با کرومیومِ واقعی — «جست‌وجو با عکس» (AI-11)، پشتِ پرچم‌های خاموشِ `shop.visual_search` و
 * `shop.storefront.visual_search`:
 *
 *     TEST_DATABASE_URL=pglite:memory node test/web-store-vsearch.mjs
 *
 *   • پرچم‌ها خاموش ⇒ نه دکمه، نه visual-search.js، نه واژه‌نامه‌اش و نه هیچ درخواستِ جست‌وجوی تصویری؛
 *   • روشن ⇒ دکمهٔ «جست‌وجو با عکس»؛ بارگذاریِ عکسِ آزمایشی ⇒ فقط فهرستِ «مشابه» (پیوند به صفحهٔ کالا)، سبد دست نمی‌خورد؛
 *     عکسِ دور ⇒ «پیدا نشد»؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
 *   • S126 (شبکه و گوشیِ ضعیف): عکسِ ردیف ۵۰۴، بریده یا بایتِ خراب ⇒ حرفِ نخستِ نام در همان ۹۶×۹۶، بی آیکنِ شکسته؛ مرورگرِ
 *     بی `createImageBitmap` ⇒ همان JPEG ِ کوچک (FileReader ⇒ data:) و همان نتیجه، نه فرستادنِ PNG ِ خام.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const vs = require('../src/lib/visual-search');
const F = require('./image-fixtures');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
             VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
const app = await createApp({ runMigrations: false });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const base = `http://127.0.0.1:${server.address().port}`;

const call = async (method, p, b, t, headers = {}) => {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
    body: b === undefined || b === null ? undefined : JSON.stringify(b) });
  return { status: r.status, body: await r.json().catch(() => null) };
};

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@vsearch.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ عکس' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront']) await flag(key, true);
const slug = 'web-vsearch';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ عکس', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const items = [['rice', 'برنج', 300], ['oil', 'روغن', 500], ['tea', 'چای', 100]];
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: items.map(([id, name, salePrice]) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name, unit: 'دانه', salePrice, category: 'خوراکی', active: true },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const [id] of items) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

//  عکسِ کالاها: مجموعهٔ مصنوعیِ قطعیِ IMG-15 (همان `test/visual-search.test.js`)؛ «چای» بی عکس
const W = 800, H = 600;
const photo = { rice: F.pngImage(W, H, F.scene(W, H)), oil: F.pngImage(W, H, F.scene(W, H, { box: [0.05, 0.1, 0.45, 0.5] })) };
for (const [id, buf] of Object.entries(photo)) {
  await query(
    `INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at)
     VALUES ('shop',$1,$2,$3,'',$4,nextval('product_photo_rev'),$5)`, [shopId, id, buf, buf.length, now()]);
}
const Q_SAME = F.pngImage(W, H, F.scene(W, H));
const Q_NONE = F.pngImage(W, H, F.scene(W, H, { cells: 8 }));

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
let hits = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
page.on('request', (r) => { if (/visual-search|vsearch\./.test(r.url())) hits.push(r.url()); });
const open = async () => {
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#products li[data-id="rice"]', { timeout: 15000 });
};
const cartOf = () => page.evaluate((s) => Object.keys(localStorage).filter((k) => k.includes(s) && /cart/i.test(k)).map((k) => localStorage.getItem(k)).join(''), slug);

//  ── پرچم‌ها خاموش ⇒ همان صفحهٔ پیشین ──
await open();
await page.waitForFunction(() => document.querySelector('script[src="/store/catalog.js"]'), null, { timeout: 10000 });
await page.waitForTimeout(800);
ok(await page.locator('#vs, #vs-btn').count() === 0, 'خاموش ⇒ نه دکمه، نه بخش');
ok(hits.length === 0, 'خاموش ⇒ نه visual-search.js، نه واژه‌نامه، نه درخواستِ جست‌وجو', hits.join(' '));
const offStore = await call('GET', `/api/store/${slug}`);
ok(!(offStore.body.features || {}).visualSearch, 'خاموش ⇒ `features.visualSearch` نیست');

//  ── روشن: نمایه با همان کارِ دسته‌ایِ سرور، سپس جست‌وجو از مرورگر ──
for (const key of [vs.FLAG, vs.STORE_FLAG]) await flag(key, true);
for (let i = 0; i < 5 && (await vs.backfill(shopId, { limit: 50 })).more; i++) { /* تا همه نمایه شوند */ }
hits = [];
await open();
await page.waitForSelector('#vs-btn', { timeout: 15000 });
ok((await page.textContent('#vs-btn')).includes('جست‌وجو با عکس'), 'دکمهٔ «جست‌وجو با عکس» زیرِ جست‌وجو');
ok(hits.some((u) => u.endsWith('/store/visual-search.js')) && hits.some((u) => /vsearch\.fa\.json$/.test(u)), 'روشن ⇒ اسکریپت و واژه‌نامهٔ دیربار');
ok(!hits.some((u) => /\/visual-search\?/.test(u)), 'پیش از عکس هیچ درخواستِ جست‌وجو');
const cartBefore = await cartOf();
await page.setInputFiles('#vs-file', { name: 'q.png', mimeType: 'image/png', buffer: Q_SAME });
//  تا خودِ نتیجه (ردیفِ «مشابه»)، نه فقط «در حالِ جست‌وجو»
await page.waitForSelector('#vs-list li[data-vs]', { timeout: 20000 });
const rows = await page.locator('#vs-list li[data-vs]').evaluateAll((l) => l.map((x) => ({
  id: x.getAttribute('data-vs'), text: x.textContent, href: (x.querySelector('a') || {}).getAttribute?.('href') })));
ok(rows.length === 1 && rows[0].id === 'rice', 'فقط کالای مشابه (برنج)؛ صحنهٔ دیگر و بی‌عکس نه', JSON.stringify(rows.map((r) => r.id)));
ok(rows.every((r) => r.text.includes('مشابه') && !/همان کالا/.test(r.text)), 'هر ردیف «مشابه»، هرگز «همان کالا»');
ok(rows[0].text.includes('برنج') && rows[0].href === '#/p/rice', 'نام و پیوند به صفحهٔ کالا', JSON.stringify(rows[0]));
ok((await page.textContent('#vs-msg')).includes('مشابه'), 'یادآوری: فقط از روی عکس مشابه');
ok(hits.some((u) => /\/api\/store\/web-vsearch\/visual-search\?limit=8$/.test(u)), 'یک درخواستِ جست‌وجو به همان ویترین');
ok(await cartOf() === cartBefore, 'هیچ افزودنِ خودکار به سبد');
await page.click('#vs-list li[data-vs="rice"] a');
await page.waitForFunction(() => location.hash === '#/p/rice', null, { timeout: 10000 });
ok(true, 'زدنِ ردیف ⇒ فقط صفحهٔ همان کالا');
ok(await cartOf() === cartBefore, 'باز کردنِ کالا هم سبد را عوض نکرد');

await page.goto(`${base}/store/${slug}`);
await page.waitForSelector('#vs-btn', { timeout: 15000 });
await page.setInputFiles('#vs-file', { name: 'n.png', mimeType: 'image/png', buffer: Q_NONE });
await page.waitForFunction(() => /پیدا نشد/.test((document.getElementById('vs-msg') || {}).textContent || ''), null, { timeout: 20000 });
ok(await page.locator('#vs-list li').count() === 0, 'عکسِ دور ⇒ «پیدا نشد»، بی ردیف');

//  ── S126: عکسِ ردیف خراب یا نرسیده ⇒ حرفِ نخستِ نام در همان قاب، بی آیکنِ شکسته ──
const THUMB = '**/api/store/web-vsearch/products/rice/thumb*';
for (const [label, how] of [['۵۰۴', (r) => r.fulfill({ status: 504, body: '' })], ['بریده', (r) => r.abort('connectionreset')],
  ['بایتِ خراب', (r) => r.fulfill({ status: 200, contentType: 'image/jpeg', body: Buffer.from('not-a-jpeg-at-all') })]]) {
  await page.route(THUMB, how);
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#vs-btn', { timeout: 15000 });
  await page.setInputFiles('#vs-file', { name: 'q.png', mimeType: 'image/png', buffer: Q_SAME });
  await page.waitForSelector('#vs-list li[data-vs="rice"]', { timeout: 20000 });
  await page.waitForFunction(() => { const b = document.querySelector('#vs-list li[data-vs="rice"] .vs-img'); return b && !b.querySelector('img'); }, null, { timeout: 10000 }).catch(() => {});
  const box = await page.$eval('#vs-list li[data-vs="rice"]', (li) => {
    const b = li.querySelector('.vs-img'); const r = b ? b.getBoundingClientRect() : { width: 0, height: 0 };
    const broken = [...li.querySelectorAll('img')].filter((i) => i.complete && i.naturalWidth === 0).length;
    return { letter: b ? b.textContent : '', w: Math.round(r.width), h: Math.round(r.height), imgs: li.querySelectorAll('img').length, broken };
  });
  ok(box.letter === 'ب' && box.imgs === 0 && box.broken === 0 && box.w === 96 && box.h === 96,
    `S126: عکسِ ردیف ${label} ⇒ حرفِ نخستِ نام در همان ۹۶×۹۶، بی آیکنِ شکسته`, JSON.stringify(box));
  ok((await page.textContent('#vs-list li[data-vs="rice"]')).includes('دیدنِ کالا'), `S126 (${label}): ردیف و پیوندش همان`);
  await page.unroute(THUMB);
}

//  ── S126: گوشیِ کهنه بی `createImageBitmap` ⇒ همان JPEG ِ کوچک و همان نتیجه ──
const old = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await old.addInitScript(() => { delete window.createImageBitmap; });
const op = await old.newPage();
op.on('pageerror', (e) => errors.push(String(e)));
op.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const sent = [];
op.on('request', (r) => { if (/\/visual-search\?/.test(r.url()) && r.method() === 'POST') sent.push({ type: r.headers()['content-type'] }); });
await op.goto(`${base}/store/${slug}`);
await op.waitForSelector('#vs-btn', { timeout: 15000 });
ok(await op.evaluate(() => typeof window.createImageBitmap) === 'undefined', 'گوشیِ کهنه: createImageBitmap نیست');
const BIG = F.pngImage(1600, 1200, F.scene(1600, 1200));
await op.setInputFiles('#vs-file', { name: 'q.png', mimeType: 'image/png', buffer: BIG });
await op.waitForSelector('#vs-list li[data-vs]', { timeout: 20000 }).catch(() => {});
//  (بدنهٔ Blob در رخدادِ پلی‌رایت نیست؛ نوعِ فرستاده همان گواه است: کدِ پیشین PNG ِ خام را می‌فرستاد)
ok(sent.length === 1 && sent[0].type === 'image/jpeg', 'S126: بی createImageBitmap ⇒ JPEG ِ کوچک از راهِ data: (نه PNG ِ خام)', JSON.stringify(sent));
const oldRows = await op.locator('#vs-list li[data-vs]').evaluateAll((l) => l.map((x) => x.getAttribute('data-vs')));
ok(oldRows.length === 1 && oldRows[0] === 'rice', 'S126: همان نتیجه («برنج»)', JSON.stringify(oldRows));
await old.close();

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: جست‌وجو با عکس');
process.exit(bad ? 1 : 0);
