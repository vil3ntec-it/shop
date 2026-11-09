/**
 * ویترین با کرومیومِ واقعی: سبدِ حسابِ مشتری روی سرور — CHK-03، WEB-14، ۲۳ S140 (صفحه: `store/cart-sync.js` ِ دیربار
 * از `store/acct.js`؛ سرور: `lib/store-cart.js`؛ قرارداد: `storefront-cart.test.js`).
 *
 *     TEST_DATABASE_URL=pglite:memory node test/web-store-cart-sync.mjs      (پس از web-store-acct.mjs)
 *
 *   ۱ ⛔ پرچمِ `shop.storefront.cart` خاموش (حتی با نشست) یا بی نشست ⇒ cart-sync.js خواسته نمی‌شود، هیچ درِ `/account/cart`
 *     و سبد همان سبدِ مهمانِ امروز (خروج هم سبد را نگه می‌دارد)؛ بارِ نخست زیرِ `FIRST_LOAD`.
 *   ۲ ورود ⇒ یک بار `/merge` (بیشینه) و سبدِ دستگاه همان سبدِ سرور؛ بارِ دوباره فقط `GET`، بی ادغامِ دوم؛ قیمتِ کهنهٔ
 *     دستگاه ⇒ قیمتِ امروزِ پاسخِ سرور، با پیام.
 *   ۳ تغییرِ پیاپی ⇒ `PUT` ِ درنگ‌دار با نسخه؛ ۴۰۹ ⇒ تغییرِ همین دستگاه روی سبدِ تازه (تغییرِ دستگاهِ دیگر می‌ماند)، یک بار
 *     دوباره؛ ۴۰۹ ِ پیاپی ⇒ دقیقاً دو `PUT` و سپس درنگ، تغییر روی دستگاه می‌ماند و پس از آن نوشته می‌شود.
 *   ۴ ۵۰۳/۴۲۹/بی اینترنت ⇒ بی پیامِ خطا، سبد کار می‌کند، دوباره با درنگ (لمسِ کاربر درنگ را نمی‌شکند) و سپس نوشته می‌شود؛
 *     دو برگهٔ یک مرورگر و دو دستگاهِ یک حساب ⇒ هر دو تغییر روی سرور و بی «پینگ‌پنگ».
 *   ۵ «خروج» ⇒ تغییرِ در صف نوشته، سپس نسخهٔ سبدِ حساب از دستگاه پاک (سرور نگه می‌دارد، ورودِ دوباره برمی‌گرداند)؛
 *     پاک کردنِ حساب ⇒ ۴۰۱ ⇒ دستگاه پاک، بی چرخه و بی زنده شدنِ سبد؛ سبدِ سرورِ خالی‌شده ⇒ دستگاه خالی؛ نشستِ منقضی ⇒
 *     نسخهٔ حساب پاک ولی سبدِ مهمان می‌ماند؛ پرچمِ خاموشِ میانهٔ کار (۴۰۱ ِ درِ ناشناخته) ⇒ هرگز خروج.
 *   ۶ قیمتِ تازه، کالای بیرون‌رفته و ناموجود ⇒ پاسخِ سرور برنده و پیامِ ساده؛ axe روی همان سبد (پس از پایانِ گذارها).
 *   ۷ هیچ یادداشتِ سبد در لاگِ سرور؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
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
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.RATE_STORE_CHECKOUT_MAX = '100000';
process.env.RATE_VERIFY_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

//  ۷: هر خطِ لاگِ سرور (درخواست‌ها، خطاها) — یادداشتِ سبد هرگز نباید در آن بیاید
const SECRET = 'رازِ-یادداشتِ-سبد';
const logs = [];
for (const k of ['log', 'warn', 'error', 'info']) {
  const orig = console[k].bind(console);
  console[k] = (...a) => { logs.push(a.map((x) => (typeof x === 'string' ? x : (x && x.stack) || JSON.stringify(x))).join(' ')); if (k !== 'log') orig(...a); };
}
const say = (s) => process.stdout.write(s + '\n');

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const { FIRST_LOAD } = require('./storefront-budget');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; say((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };

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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const until = async (fn, ms = 8000) => { const end = Date.now() + ms; for (;;) { const v = await fn(); if (v || Date.now() > end) return v; await sleep(150); } };

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@cartsync.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ سبد' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.web_account', 'shop.catalog']) await flag(key, true);
const slug = 'cart-sync-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ سبد', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 100));
let n = 0;
const op = (table, row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++n}`, ts: Date.now(), table, row_id: row, type: 'insert', fields });
const PRODUCTS = { rice: ['برنج', 300], oil: ['روغن', 250], salt: ['نمک', 20], tea: ['چای', 150] };
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  ...Object.entries(PRODUCTS).map(([id, [name, salePrice]]) => op('products', id, { name, unit: 'دانه', salePrice, category: 'خوراکی', active: true })),
  ...Object.keys(PRODUCTS).map((id) => op('warehouseEntries', `${id}-e1`, { productId: id, units: 50, cartons: 0, perCarton: 0, unit: 'دانه', price: 10, createdAt: Date.now() })),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا و موجودی از راهِ همگام‌سازیِ واقعی رسیدند');
for (const id of Object.keys(PRODUCTS)) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

/** نشستِ تازهٔ حسابِ این شماره از راهِ API — «دستگاهی دیگر» */
const session = async (phone) => {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
  const a = await call('POST', `/api/store/${slug}/account`, {}, null, { 'X-Store-Ticket': vc.body.ticket });
  return { token: a.body.session, expiresAt: a.body.expiresAt, phone: a.body.customer.phone, name: '' };
};
const H = (s) => ({ 'X-Store-Session': s.token });
const apiCart = async (s) => (await call('GET', `/api/store/${slug}/account/cart`, null, null, H(s))).body.cart;
const qtys = (cart) => Object.fromEntries((cart ? cart.lines : []).map((l) => [l.productId, l.qtyMilli]));
const dbCart = async (phone) => {
  const r = await one(`SELECT c.items, c.version FROM store_customer_carts c JOIN store_customers s ON s.id = c.customer_id
                        WHERE c.shop_id = $1 AND s.phone = $2`, [shopId, phone]);
  if (!r) return null;
  const items = typeof r.items === 'string' ? JSON.parse(r.items) : r.items;
  return { version: Number(r.version), items: Object.fromEntries(items.map((i) => [i.productId, i.qtyMilli])), notes: items.map((i) => i.note || '') };
};
const line = (id, qtyMilli, extra = {}) => ({ [id]: { qtyMilli, name: PRODUCTS[id][0], unit: 'دانه', priceMinor: PRODUCTS[id][1] * 100, maxQtyMilli: null, photo: false, unavailable: false, ...extra } });

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const reqs = [];   //  هر درخواستِ `/account/cart…` و `cart-sync.js`: { tab, method, path, t }
let tabs = 0;
const watch = (page) => {
  const tab = ++tabs;
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  page.on('request', (r) => {
    const u = new URL(r.url());
    if (/\/account\/cart|\/store\/cart-sync\.js/.test(u.pathname)) reqs.push({ tab, method: r.method(), path: u.pathname, t: Date.now() });
  });
  page.tab = tab;
  return page;
};
const puts = (since, tab) => reqs.filter((r) => r.t >= since && r.method === 'PUT' && (!tab || r.tab === tab)).length;
/** بافتِ تازه («مرورگر»/«دستگاه»): نشست، سبد و پایهٔ هم‌گامی یک بار (برگهٔ تازهٔ همان بافت مالِ خودش را دارد) */
const device = async ({ sess = null, cart = null, sync = null } = {}) => {
  const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  await ctx.addInitScript(([s, se, c, sy]) => {
    window.__toasts = [];
    new MutationObserver(() => {
      const t = document.getElementById('toast');
      if (t && !t.hidden && t.textContent && window.__toasts[window.__toasts.length - 1] !== t.textContent) window.__toasts.push(t.textContent);
    }).observe(document, { subtree: true, childList: true, characterData: true, attributes: true });
    if (localStorage.getItem('seeded')) return;
    localStorage.setItem('seeded', '1');
    if (se) localStorage.setItem('store-session', JSON.stringify(se));
    if (c) localStorage.setItem(`store-cart:${s}`, JSON.stringify(c));
    if (sy) localStorage.setItem(`store-cartsync:${s}`, JSON.stringify(sy));
  }, [slug, sess, cart, sync]);
  return ctx;
};
let loads = 0;
const go = async (page, hash = '#/') => {
  await page.goto(`${base}/store/${slug}?r=${++loads}${hash}`);
  await page.waitForSelector('#products li[data-id="rice"] .p-add', { timeout: 15000, state: hash === '#/' ? 'visible' : 'attached' });
};
const ls = (page, key) => page.evaluate((k) => { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } }, key);
const local = async (page) => Object.fromEntries(Object.entries((await ls(page, `store-cart:${slug}`)) || {}).map(([k, v]) => [k, v.qtyMilli]));
const SYNC = `store-cartsync:${slug}`;
const add = (page, id) => page.click(`#products li[data-id="${id}"] .p-add`);
const toasts = (page) => page.evaluate(() => window.__toasts.slice());
const scary = async (page) => {
  const t = await toasts(page);
  return t.filter((x) => /خطا|دوباره|زیاد|اتصال/.test(x)).concat(await page.locator('.note.err:visible').allTextContents());
};
const same = (a, b) => JSON.stringify(Object.entries(a).sort()) === JSON.stringify(Object.entries(b).sort());

//  ── ۱ ⛔ پرچمِ سبد خاموش / بی نشست ⇒ همان سبدِ مهمانِ امروز ──
const bytes = ['store/store.js', 'kit/kit.js', 'kit/boot.js'].reduce((s, f) => s + fs.statSync(path.join(import.meta.dirname, '..', 'public', f)).size, 0);
ok(bytes <= FIRST_LOAD, `بارِ نخست ${bytes} بایت ≤ ${FIRST_LOAD} (cart-sync.js دیربار)`);
{
  const sOff = await session('0792000001');
  const ctx = await device({ sess: sOff, cart: line('rice', 1000) });
  const page = watch(await ctx.newPage());
  const t0 = Date.now();
  await go(page);
  await page.waitForTimeout(800);
  await add(page, 'oil');
  await page.waitForTimeout(1200);
  ok(reqs.filter((r) => r.t >= t0).length === 0 && same(await local(page), { rice: 1000, oil: 1000 }) && !(await ls(page, SYNC)),
    'پرچمِ سبد خاموش (با نشست) ⇒ cart-sync.js و `/account/cart` خواسته نشد و سبد همان سبدِ مهمان', JSON.stringify(reqs.filter((r) => r.t >= t0)));
  ok(await dbCart(sOff.phone) === null, 'پرچمِ خاموش ⇒ هیچ ردیفی روی سرور');
  await go(page, '#/orders');
  await page.waitForSelector('#ac-mine button:has-text("خروج از حساب")', { timeout: 10000 });
  await page.click('#ac-mine button:has-text("خروج از حساب")');
  await page.waitForSelector('#ac-mine button:has-text("ورود به حساب")', { timeout: 10000 });
  ok(same(await local(page), { rice: 1000, oil: 1000 }), 'پرچمِ خاموش ⇒ «خروج» سبدِ مهمان را مثلِ امروز نگه می‌دارد');
  await ctx.close();
}
await flag('shop.storefront.cart', true);
{
  const ctx = await device({ cart: line('rice', 1000) });
  const page = watch(await ctx.newPage());
  const t0 = Date.now();
  await go(page);
  await add(page, 'oil');
  await page.waitForTimeout(1200);
  ok(reqs.filter((r) => r.t >= t0).length === 0 && same(await local(page), { rice: 1000, oil: 1000 }),
    'پرچمِ روشن ولی بی نشست ⇒ cart-sync.js و `/account/cart` خواسته نشد و سبد همان سبدِ مهمان');
  await ctx.close();
}

//  ── ۲ ورود ⇒ یک ادغام؛ بارِ دوباره فقط GET؛ قیمتِ امروز از سرور ──
const phone = '0792000002';
const P = '+93' + phone.slice(1);
const other = await session(phone);   //  «دستگاهِ دیگرِ» همان حساب
ok((await call('PUT', `/api/store/${slug}/account/cart`, { version: 0, items: [{ productId: 'oil', qtyMilli: 2000 }] }, null, H(other))).status === 200,
  'سبدِ حساب از دستگاهِ دیگر: روغن ×۲');
const ctx = await device({ cart: { ...line('rice', 1000, { note: SECRET }), ...line('oil', 1000) } });
let page = watch(await ctx.newPage());
await go(page, '#/orders');
await page.waitForSelector('#ac-mine button:has-text("ورود به حساب")', { timeout: 15000 });
await page.click('#ac-mine button:has-text("ورود به حساب")');
await page.fill('#ac-mine input[type="tel"]', phone);
await page.click('#ac-mine button:has-text("فرستادنِ کد")');
await page.waitForFunction(() => /کدِ آزمایشی: \d+/.test(document.getElementById('ac-mine').textContent), null, { timeout: 5000 });
const code = /کدِ آزمایشی: (\d+)/.exec(await page.textContent('#ac-mine'))[1];
await page.fill('#ac-mine input[autocomplete="one-time-code"]', code);
const tIn = Date.now();
await page.click('#ac-mine button:has-text("تأیید")');
await page.waitForFunction((k) => { try { return JSON.parse(localStorage.getItem(k)).merged === true; } catch { return false; } }, SYNC, { timeout: 8000 });
await page.waitForTimeout(600);
const merges = reqs.filter((r) => r.t >= tIn && /\/merge$/.test(r.path));
const m1 = await dbCart(P);
ok(merges.length === 1 && same(m1.items, { rice: 1000, oil: 2000 }) && same(await local(page), { rice: 1000, oil: 2000 }),
  'ورود ⇒ یک بار ادغامِ بیشینه (روغن ۲ نه ۳) و سبدِ دستگاه همان سبدِ سرور', JSON.stringify({ merges: merges.length, m1, local: await local(page) }));
ok(m1.notes.includes(SECRET), 'یادداشتِ قلم با سبد رفت');
const tRe = Date.now();
await go(page);
await until(async () => reqs.some((r) => r.t >= tRe && r.method === 'GET' && /\/account\/cart$/.test(r.path)));
await page.waitForTimeout(800);
ok(!reqs.some((r) => r.t >= tRe && /\/merge$/.test(r.path)) && (await dbCart(P)).version === m1.version,
  'بارِ دوباره ⇒ فقط GET، بی ادغامِ دوم و بی نسخهٔ تازه');
//  نسخهٔ کهنهٔ دستگاه (قیمتِ دیروز) ⇒ پاسخِ سرور برنده، با پیام؛ store.js در حالتِ حساب قیمت را از سرور می‌گیرد
await page.evaluate((k) => { const c = JSON.parse(localStorage.getItem(k)); c.rice.priceMinor = 100; localStorage.setItem(k, JSON.stringify(c)); }, `store-cart:${slug}`);
await go(page);
await until(async () => ((await ls(page, `store-cart:${slug}`)) || {}).rice?.priceMinor === 30000);
const t2 = await toasts(page);
ok(((await ls(page, `store-cart:${slug}`)) || {}).rice.priceMinor === 30000 && t2.some((x) => x.includes('قیمتِ ۱ کالای سبد با قیمتِ امروز تازه شد')),
  'قیمتِ کهنهٔ دستگاه ⇒ قیمتِ امروزِ پاسخِ سرور و پیامِ «قیمت تازه شد»', JSON.stringify(t2));

//  ── ۳ PUT ِ درنگ‌دار؛ ۴۰۹ ⇒ سه‌طرفه، یک بار دوباره؛ ۴۰۹ ِ پیاپی ⇒ دو PUT و درنگ ──
let t = Date.now();
for (let i = 0; i < 5; i++) { await add(page, 'tea'); await page.waitForTimeout(60); }
await until(async () => (await dbCart(P)).items.tea === 5000);
await page.waitForTimeout(600);
ok((await dbCart(P)).items.tea === 5000 && puts(t) <= 2, `پنج لمسِ پیاپی ⇒ ${puts(t)} PUT ِ درنگ‌دار و چای ×۵ روی سرور`);

const fresh = await apiCart(other);
ok((await call('PUT', `/api/store/${slug}/account/cart`, { version: fresh.version,
  items: [...fresh.lines.map((l) => ({ productId: l.productId, qtyMilli: l.qtyMilli, ...(l.note ? { note: l.note } : {}) })), { productId: 'salt', qtyMilli: 3000 }] }, null, H(other))).status === 200,
  'دستگاهِ دیگر نمک ×۳ افزود (نسخهٔ این برگه کهنه شد)');
t = Date.now();
await add(page, 'rice');
await until(async () => { const c = await dbCart(P); return c.items.rice === 2000 && c.items.salt === 3000; });
await page.waitForTimeout(800);
let c = await dbCart(P);
ok(c.items.rice === 2000 && c.items.salt === 3000 && c.items.tea === 5000 && puts(t) === 2 && same(await local(page), c.items),
  '۴۰۹ ⇒ تغییرِ همین دستگاه (برنج +۱) روی سبدِ تازه، تغییرِ دستگاهِ دیگر (نمک) ماند؛ دقیقاً یک بار دوباره', JSON.stringify({ c, puts: puts(t), local: await local(page) }));

//  ۴۰۹ ِ پیاپی (دستگاهِ دیگر بی‌وقفه می‌نویسد): دقیقاً دو PUT، سپس درنگ؛ تغییر روی دستگاه می‌ماند
const sess = await ls(page, 'store-session');
let fake = 1000;
const conflict = async (route) => {
  if (route.request().method() !== 'PUT') return route.continue();
  const real = await apiCart(sess);
  return route.fulfill({ status: 409, contentType: 'application/json',
    body: JSON.stringify({ error: { code: 'cart_changed', message: 'سبد در دستگاهِ دیگری عوض شده است', cart: { ...real, version: ++fake } } }) });
};
await page.route('**/account/cart', conflict);
t = Date.now();
await add(page, 'oil');
await page.waitForTimeout(1700);
ok(puts(t) === 2 && (await local(page)).oil === 3000 && (await dbCart(P)).items.oil === 2000,
  `۴۰۹ ِ پیاپی ⇒ ${puts(t)} PUT (یک بار دوباره) و بی چرخه؛ روغن ×۳ روی دستگاه ماند`);
await page.unroute('**/account/cart', conflict);
await until(async () => (await dbCart(P)).items.oil === 3000, 10000);
ok((await dbCart(P)).items.oil === 3000 && (await scary(page)).length === 0, 'پس از درنگ همان تغییر نوشته شد (هیچ تغییری گم نشد) و هیچ پیامِ خطا', JSON.stringify(await scary(page)));

//  ── ۴ بی اینترنت / ۵۰۳ / ۴۲۹ ⇒ سبد روی دستگاه کار می‌کند و پس از آن نوشته می‌شود ──
await ctx.setOffline(true);
await page.waitForTimeout(200);
t = Date.now();
await add(page, 'salt');
await page.waitForTimeout(1500);
ok(puts(t) === 0 && (await local(page)).salt === 4000, 'بی اینترنت ⇒ هیچ تلاشی، سبد روی دستگاه (نمک ×۴)');
await ctx.setOffline(false);
await until(async () => (await dbCart(P)).items.salt === 4000);
ok((await dbCart(P)).items.salt === 4000, 'برگشتِ شبکه ⇒ آخرین سبدِ دستگاه همان دم نوشته شد');
let mode = 503;
const flaky = (route) => (route.request().method() === 'PUT'
  ? route.fulfill({ status: mode, contentType: 'application/json', headers: mode === 429 ? { 'Retry-After': '60' } : {},
    body: JSON.stringify({ error: { code: mode === 429 ? 'rate_limited' : 'db_busy', message: 'شلوغ' } }) })
  : route.continue());
await page.route('**/account/cart', flaky);
t = Date.now();
await add(page, 'salt');
await page.waitForTimeout(1200);
ok(puts(t) === 1 && (await local(page)).salt === 5000, '۵۰۳ ⇒ یک تلاش، سبد روی دستگاه (نمک ×۵)');
mode = 429;
await until(async () => puts(t) === 2, 6000);
const t429 = Date.now();
await add(page, 'salt');   //  لمس در میانهٔ درنگ ⇒ درخواستِ تازه نه
await page.waitForTimeout(3500);
ok(puts(t) === 2 && puts(t429) === 0 && (await local(page)).salt === 6000,
  '۴۲۹ ⇒ درنگِ بلند؛ لمسِ کاربر درنگ را نمی‌شکند (سطلِ سرور دور زده نمی‌شود) و سبد کار می‌کند', `puts=${puts(t)}`);
ok((await scary(page)).length === 0 && (await dbCart(P)).items.salt === 4000, '۵۰۳/۴۲۹ ⇒ هیچ پیامِ ترسناک؛ سرور هنوز همان', JSON.stringify(await scary(page)));
await page.unroute('**/account/cart', flaky);
await page.evaluate(() => window.dispatchEvent(new Event('online')));   //  برگشتِ شبکه درنگ را می‌شکند (نه لمسِ کاربر)
await until(async () => (await dbCart(P)).items.salt === 6000);
ok((await dbCart(P)).items.salt === 6000, 'پس از ۵۰۳/۴۲۹ همان آخرین سبدِ دستگاه نوشته شد');


//  دو برگهٔ یک مرورگر: یکی می‌نویسد، دیگری نه (بی پینگ‌پنگ)
const page2 = watch(await ctx.newPage());
await go(page2);
await page2.waitForTimeout(1200);
t = Date.now();
await add(page, 'tea');
await page.waitForTimeout(4500);
ok((await dbCart(P)).items.tea === 6000 && puts(t, page.tab) === 1 && puts(t, page2.tab) === 0,
  'دو برگهٔ یک مرورگر ⇒ یک PUT از همان برگه، برگهٔ دیگر نمی‌نویسد', `p1=${puts(t, page.tab)} p2=${puts(t, page2.tab)}`);
await page2.close();

//  دو دستگاهِ یک حساب، تغییرِ هم‌زمان ⇒ هر دو روی سرور، بی پینگ‌پنگ
const ctxB = await device({ sess: await session(phone) });
const pB = watch(await ctxB.newPage());
await go(pB);
await until(async () => (await local(pB)).tea === 6000);
t = Date.now();
await Promise.all([add(page, 'rice'), add(pB, 'oil')]);
await until(async () => { const x = await dbCart(P); return x.items.rice === 3000 && x.items.oil === 4000; });
await page.waitForTimeout(1500);
const race = puts(t);
const tQuiet = Date.now();
await page.waitForTimeout(4000);
c = await dbCart(P);
ok(c.items.rice === 3000 && c.items.oil === 4000 && race <= 4 && puts(tQuiet) === 0,
  `دو دستگاه هم‌زمان ⇒ هر دو تغییر روی سرور (${race} PUT) و پس از آن هیچ رفت‌وبرگشت`, JSON.stringify(c.items));
await ctxB.close();

//  ── ۶ سرور برنده: قیمتِ تازه، کالای بیرون‌رفته و ناموجود ──
await call('PUT', '/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: 35000, maxQtyMilli: null }, T);
await call('PUT', '/api/shop/storefront/products/oil', { published: false }, T);
const dmg = await call('POST', '/api/shop/catalog/moves', { kind: 'damage', productId: 'salt', from: 'shop_floor', qtyMilli: 50000, reason: 'آزمون' },
  T, { 'Idempotency-Key': `cart-sync-dmg-${Date.now()}` });
ok(dmg.status === 201, 'همهٔ نمک آسیب دید (ناموجود)', JSON.stringify(dmg.body).slice(0, 120));
await go(page, '#/cart');
await until(async () => ((await ls(page, `store-cart:${slug}`)) || {}).rice?.priceMinor === 35000);
await page.waitForTimeout(600);
const lc = await ls(page, `store-cart:${slug}`);
const t6 = (await toasts(page)).join(' | ');
ok(lc.rice.priceMinor === 35000 && lc.oil.unavailable === true && lc.salt.unavailable === true && lc.tea.unavailable === false,
  'پاسخِ سرور برنده: برنج با قیمتِ امروز، روغن و نمک خط‌خورده، چای سالم', JSON.stringify(lc));
ok(t6.includes('قیمتِ ۱ کالای سبد با قیمتِ امروز تازه شد.') && t6.includes('۱ کالای سبد دیگر در ویترین نیست.') && t6.includes('۱ کالای سبد فعلاً ناموجود است.'),
  'پیامِ ساده: قیمت تازه شد، دیگر در ویترین نیست، فعلاً ناموجود', t6);
c = await dbCart(P);
ok(c.items.oil === 4000 && c.items.salt === 6000, 'سرور قلمِ بیرون‌رفته/ناموجود را پاک نمی‌کند (تصمیم با مشتری)');
ok(await page.locator('#cart-lines li.cline.bad, .cline.bad').count() >= 2, 'سبدِ باز: دو قلم خط‌خورده');
if (axeSrc) {
  await page.waitForFunction(() => {
    const sh = document.getElementById('cart-sheet');
    return Boolean(sh) && !sh.hidden && getComputedStyle(sh).opacity === '1'
      && (!document.body.classList.contains('phone-cart') || document.body.classList.contains('cart-open'))
      && document.getAnimations().every((a) => a.playState === 'finished');
  }, null, { timeout: 10000 });
  await page.waitForFunction(() => document.getElementById('toast')?.hidden !== false, null, { timeout: 6000 }).catch(() => {});
  await page.evaluate(axeSrc);   //  بی bypassCSP: سنجهٔ CSP ِ همین برگه سرِ جای خود
  const v = await page.evaluate(async () => (await window.axe.run(document, { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name', 'aria-allowed-attr', 'aria-valid-attr-value'] }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nd) => nd.target.join(' ') + ' ' + (nd.any[0] && nd.any[0].message)).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core): سبدِ حساب با قلمِ خط‌خورده', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (AXE_PATH)');
//  پیش از بقیه: کالاها دوباره سالم
await call('PUT', '/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

//  ── ۵ «خروج»: تغییرِ در صف اول نوشته، سپس نسخهٔ حساب از دستگاه پاک ──
await go(page);
await page.route('**/account/cart', flaky);
mode = 503;
t = Date.now();
await add(page, 'tea');
await until(async () => puts(t) === 1, 4000);
await page.unroute('**/account/cart', flaky);
await page.evaluate(() => { location.hash = '#/orders'; });
await page.waitForSelector('#ac-mine button:has-text("خروج از حساب")', { timeout: 10000 });
await page.click('#ac-mine button:has-text("خروج از حساب")');
await page.waitForSelector('#ac-mine button:has-text("ورود به حساب")', { timeout: 10000 });
c = await dbCart(P);
ok(c.items.tea === 7000, 'خروج ⇒ تغییرِ در صف (چای ×۷) پیش از خروج نوشته شد', JSON.stringify(c.items));
ok(Object.keys((await ls(page, `store-cart:${slug}`)) || {}).length === 0 && !(await ls(page, SYNC)) && !(await ls(page, 'store-session')),
  'خروج ⇒ سبدِ حساب، پایهٔ هم‌گامی و نشست از دستگاه پاک', JSON.stringify(await ls(page, `store-cart:${slug}`)));
ok(Object.keys((await dbCart(P)).items).length === 4, 'سبدِ حساب روی سرور ماند');
await page.click('#ac-mine button:has-text("ورود به حساب")');
await page.fill('#ac-mine input[type="tel"]', phone);
await page.click('#ac-mine button:has-text("فرستادنِ کد")');
await page.waitForFunction(() => /کدِ آزمایشی: \d+/.test(document.getElementById('ac-mine').textContent), null, { timeout: 5000 });
await page.fill('#ac-mine input[autocomplete="one-time-code"]', /کدِ آزمایشی: (\d+)/.exec(await page.textContent('#ac-mine'))[1]);
await page.click('#ac-mine button:has-text("تأیید")');
await until(async () => (await local(page)).tea === 7000);
ok(same(await local(page), (await dbCart(P)).items), 'ورودِ دوباره ⇒ همان سبدِ حساب از سرور برگشت');

//  سبدِ سرور جای دیگری خالی شد (پاک کردنِ داده به دستِ دکان) ⇒ دستگاه خالی، سبد زنده نمی‌شود
await query('DELETE FROM store_customer_carts WHERE shop_id = $1', [shopId]);
await go(page);
await until(async () => Object.keys(await local(page)).length === 0);
await page.waitForTimeout(800);
ok(Object.keys(await local(page)).length === 0 && ((await dbCart(P)) === null || Object.keys((await dbCart(P)).items).length === 0),
  'سبدِ سرورِ پاک‌شده ⇒ دستگاه هم خالی، بی زنده کردنِ قلم‌ها');

//  پاک کردنِ حساب ⇒ ۴۰۱ ⇒ دستگاه پاک، بی چرخه و بی زنده شدنِ سبد
await add(page, 'rice');
await until(async () => (await dbCart(P))?.items.rice === 1000);
const live = await ls(page, 'store-session');
ok((await call('DELETE', `/api/store/${slug}/account`, null, null, H(live))).status === 200, 'مشتری حسابش را پاک کرد');
t = Date.now();
await add(page, 'tea');
await until(async () => !(await ls(page, 'store-session')));
await page.waitForTimeout(2500);
ok(!(await ls(page, 'store-session')) && Object.keys(await local(page)).length === 0 && !(await ls(page, SYNC)) && puts(t) === 1,
  'پس از پاک شدنِ حساب: یک PUT ⇒ ۴۰۱، نشست و نسخهٔ سبد از دستگاه پاک، بی چرخه', `puts=${puts(t)}`);
ok((await one('SELECT COUNT(*)::int AS n FROM store_customers WHERE phone = $1', [P])).n === 0 && (await dbCart(P)) === null,
  'سبد با حساب رفت و دوباره ساخته نشد');
await ctx.close();

//  ── نشستِ منقضی و پرچمِ خاموشِ میانهٔ کار ──
{
  const old = { token: 'x'.repeat(40), expiresAt: Date.now() - 1000, phone: '+93792000009', name: '' };
  const cx = await device({ sess: old, cart: line('rice', 1000), sync: { v: 3, k: '[["rice",1000,""]]', merged: true } });
  const px = watch(await cx.newPage());
  await go(px);
  await until(async () => Object.keys(await local(px)).length === 0, 6000);
  ok(Object.keys(await local(px)).length === 0 && !(await ls(px, SYNC)), 'نشستِ منقضی ⇒ نسخهٔ سبدِ حساب روی دستگاه نمی‌ماند');
  await cx.close();
  const cy = await device({ sess: old, cart: line('rice', 1000) });
  const py = watch(await cy.newPage());
  await go(py);
  await py.waitForTimeout(1200);
  ok(same(await local(py), { rice: 1000 }), 'نشستِ منقضی بی پایهٔ هم‌گامی ⇒ سبدِ مهمان همان می‌ماند');
  await cy.close();
}
{
  const sF = await session('0792000003');
  const cf = await device({ sess: sF, cart: line('rice', 1000) });
  const pf = watch(await cf.newPage());
  await go(pf);
  await until(async () => (await dbCart(sF.phone))?.items.rice === 1000);
  await flag('shop.storefront.cart', false);
  t = Date.now();
  await add(pf, 'rice');
  await pf.waitForTimeout(3000);
  ok(Boolean(await ls(pf, 'store-session')) && (await local(pf)).rice === 2000 && puts(t) === 1,
    'پرچمِ سبد میانهٔ کار خاموش (۴۰۱ ِ درِ ناشناخته) ⇒ مشتری بیرون نمی‌افتد، هم‌گامی می‌ایستد، سبد روی دستگاه', `puts=${puts(t)}`);
  await cf.close();
  await flag('shop.storefront.cart', true);
}

const leaked = logs.filter((l) => l.includes(SECRET));
ok(leaked.length === 0 && logs.some((l) => /\/account\/cart/.test(l)), 'یادداشتِ سبد در هیچ خطِ لاگِ سرور نیامد', leaked.slice(0, 2).join(' | '));
ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await browser.close();
server.close();
await closeDb();
say(bad ? `❌ ${bad} ایراد` : '✅ ویترین: سبدِ حساب روی سرور — ادغامِ یک‌باره، نسخه و ۴۰۹، بی اینترنت، خروج و سرورِ برنده');
process.exit(bad ? 1 : 0);
