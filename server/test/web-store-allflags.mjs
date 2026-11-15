/**
 * ویترین با کرومیومِ واقعی، «همه با هم روشن» — هر پرچمِ مشتری‌رو (`shop.storefront*`، `shop.chat*`، `shop.orders*`،
 * `shop.catalog`، `shop.image_bank`، `shop.customer_notify`، `shop.delivery_customer_view`) روی یک دکان، همان‌طور که صاحب‌کار
 * پس از روشن کردنِ پرچم‌ها می‌گرداند. هر آزمونِ دیگرِ ویترین فقط پرچمِ خودش را روشن می‌کند؛ این‌جا همه با هم.
 *
 *     TEST_DATABASE_URL=pglite:memory node test/web-store-allflags.mjs      (در `npm run test:web-store`)
 *
 * فهرستِ پرچم‌ها از کاتالوگِ `KNOWN` ِ `routes/admin-flags.js` (همان زبانهٔ «پرچم‌ها» و `E2E_FLAGS_ON=all`) و چاپ می‌شود؛
 * پرچمِ پمپ هرگز؛ هر پرچمِ دیگرِ کاتالوگ با دلیلِ کنار ماندن چاپ می‌شود. ⛔ هر کلیدِ همین خانواده که کد می‌خواند باید در
 * کاتالوگ باشد (وگرنه صاحب‌کار نه می‌بیندش نه روشنش می‌کند و «"true"» ِ رشته‌ای بی‌صدا بی‌اثر می‌ماند).
 *
 * سفرِ واقعیِ مشتری در ۳۶۰ (fa)، ۷۶۸ (en) و ۱۲۸۰ (ps)؛ و همان صفحه‌ها در شش ترکیبِ دیگرِ عرض و زبان:
 *   ۱ ویترین و دسته‌ها؛ ۲ جست‌وجو (رقمِ فارسی، «ي» ِ عربی، غلطِ تایپی)؛ ۳ برگهٔ کالا، صفحهٔ سئو/فرستادن، «دیده‌شده‌های
 *   اخیر» و «خبرم کن» ِ قیمت/موجودی؛ ۴ سبد، ورود به حسابِ وب (سبدِ سرور) و تغییرِ تعداد؛ ۵ ثبت: ناحیهٔ درون و بیرون،
 *   «دریافت از دکان»، اسکناسِ ۱۰۰۰ («با ۱۰۰۰ می‌پردازم») و گامِ پرداخت؛ ۶ رسید و پیگیری، نمای پیک با گذارهای دکان و
 *   پیک از API؛ ۷ گفت‌وگو: متن، عکس، صدا (اگر MediaRecorder هست) و صفِ بی‌اینترنت؛ ۸ خروج.
 * در هر صفحه (پس از پایانِ گذارها، همان الگوی `web-store.mjs`): axe (WCAG 2.1 A/AA) بی نقض، بی بیرون‌زدگیِ افقی، بی خطای
 * جاوااسکریپت و بی نقضِ CSP، بی ۴xx/۵xx جز آن‌چه صریح سنجیده شده، و بارِ نخست (`storefront-budget.js`) همان سه پرونده.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-web-allflags';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const F = require('./image-fixtures');
const { FIRST_LOAD } = require('./storefront-budget');
const { RESERVED } = require('../src/lib/live-config');

let bad = 0;
let checks = 0;
const ok = (c, m, d = '') => { checks++; if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const until = async (fn, ms = 10000) => { const end = Date.now() + ms; for (;;) { const v = await fn(); if (v || Date.now() > end) return v; await sleep(150); } };
const faDigits = (s) => String(s).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
const latin = (s) => String(s).replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0)).replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));

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
const register = async (email, name, dev) => {
  const password = 'Passw0rd!test';
  const s = await call('POST', '/api/auth/register/start', { name, email, password });
  const v = await call('POST', '/api/auth/register/verify', { email, code: s.body.devCode });
  const d = await call('POST', '/api/auth/register/complete', { ticket: v.body.ticket, name, password,
    device: { deviceId: dev, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
  if (d.status !== 201 && d.status !== 200) ok(false, `ثبت‌نامِ ${email}`, JSON.stringify(d.body).slice(0, 160));
  return { token: d.body.accessToken, userId: d.body.user && d.body.user.id };
};

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const owner = await register('owner@allflags.local', 'صاحب', 'dev-owner');
const T = owner.token;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ همه‌روشن' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

//  ── ۰ پرچم‌ها: از کاتالوگِ KNOWN، با بررسیِ کامل بودنش در برابرِ کد ──
const FAMILY_RE = /^shop\.(storefront|chat|orders)(\.[a-z_]+)*$/;
//  SEC-10: `shop.customer_erasure` از ۱۴۰۵/۰۸/۲۰ «پاک کردنِ داده‌های من» ِ وب هم هست (`store/erase.js`) ⇒ مشتری‌رو
const FAMILY_EXTRA = ['shop.catalog', 'shop.image_bank', 'shop.customer_notify', 'shop.delivery_customer_view', 'shop.customer_erasure'];
const inFamily = (k) => FAMILY_RE.test(k) || FAMILY_EXTRA.includes(k);
//  بازنشسته: `lib/order-ledger.js` «دیگر خوانده نمی‌شود» (فقط کفِ تحویل هنگامِ راه‌اندازی، همان `shop.catalog`) ⇒ نه در کاتالوگ، نه روشن
const RETIRED = { 'shop.orders.ledger': 'بازنشسته — order-ledger.js دیگر نمی‌خواندش (کفِ تحویل همان shop.catalog)' };
const isPump = (k, scope) => /(^|\.)pump(\.|_|$)/.test(k) || scope === 'pump';
//  پیش‌نیازِ گام‌های همین سفر (بیرون از خانواده، با دلیل): بی آن‌ها همان گام اصلاً پیدا نمی‌شود
const DEPS = {
  'shop.delivery': 'پایهٔ پیک و `shop.delivery_customer_view` (گامِ ۶)',
  'shop.visual_search': 'پایهٔ `shop.storefront.visual_search` (دکمهٔ «جست‌وجو با عکس»)',
  'pay.hesabpay': 'دکمهٔ حساب‌پی ِ گامِ پرداخت (درگاهِ ساختگیِ آزمون؛ پول هرگز به کلیدِ پلتفرم نمی‌رود)',
};
const flagsRes = await call('GET', `/api/admin/flags?scope=${encodeURIComponent(shopId)}`, null, A);
ok(flagsRes.status === 200 && Array.isArray(flagsRes.body.known), 'کاتالوگِ پرچم‌ها از همان درِ پنل (`GET /api/admin/flags`)', String(flagsRes.status));
const KNOWN = flagsRes.body.known || [];
const ON = KNOWN.filter((k) => inFamily(k.key) && !isPump(k.key, k.scope) && typeof k.default === 'boolean' && !RESERVED.includes(k.key)).map((k) => k.key);
{
  //  ⛔ کاتالوگ باید هر کلیدِ همین خانواده را که کد می‌خواند داشته باشد — وگرنه «همه روشن» فقط نیمی از آن‌هاست
  const SRC = path.join(import.meta.dirname, '..', 'src');
  const PUB = path.join(import.meta.dirname, '..', 'public', 'store');
  const files = [];
  const walk = (d) => { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const f = path.join(d, e.name); if (e.isDirectory()) walk(f); else if (/\.js$/.test(e.name)) files.push(f); } };
  walk(SRC); walk(PUB);
  const used = new Set();
  for (const f of files) {
    for (const m of fs.readFileSync(f, 'utf8').matchAll(/['"`](shop\.[a-z_]+(?:\.[a-z_]+)*)['"`]/g)) if (inFamily(m[1]) && !RESERVED.includes(m[1]) && !RETIRED[m[1]]) used.add(m[1]);
  }
  const missing = [...used].filter((k) => !KNOWN.some((x) => x.key === k)).sort();
  ok(missing.length === 0, `هر پرچمِ خانواده که کد می‌خواند در کاتالوگِ KNOWN است (${used.size} کلید در کد)`, missing.join(' '));
  //  خاموش مگر فهرستِ پیش‌فرض‌روشنِ `lib/flag-defaults.js` (۱۴۰۵/۰۸/۲۲) — همان یک فهرست
  const { DEFAULT_ON } = require('../src/lib/flag-defaults');
  const off = KNOWN.filter((k) => inFamily(k.key) && typeof k.default === 'boolean' && k.default !== DEFAULT_ON.has(k.key)).map((k) => k.key);
  ok(off.length === 0, 'هر پرچمِ خانواده پیش‌فرض‌خاموش است، جز فهرستِ پیش‌فرض‌روشن', off.join(' '));
}
const WHY = (k) => {
  if (isPump(k.key, k.scope)) return 'پمپ — هرگز در این مخزنِ دکان دست نمی‌خورد';
  if (RESERVED.includes(k.key)) return 'کلیدِ رزروِ خودِ سرور، نه پرچم';
  if (k.scope !== 'shop') return `دامنهٔ «${k.scope}» (نه پرچمِ دکان)`;
  if (k.default === true) return 'پیش‌فرض روشن (رفعِ درستی/امنیت؛ خاموش فقط اضطراری) — همان پیش‌فرضِ تولید می‌ماند';
  if (/^shop\.(sync|returns|save_base|ledger_merge|sync_shadow_file|settings_sync|held_sync|count_absorb|staff_scope|custom_roles|approval_requests|credit_|promo_loyalty_review|receipt_v2|promo_stamp|loyalty_rule)/.test(k.key)) return 'صندوق/همگام‌سازیِ برنامهٔ دکان، نه صفحهٔ مشتری';
  if (/^shop\.(owner_report|backup_contact_guard)/.test(k.key)) return 'کارِ صاحب/پشتیبان در برنامه یا پنل، نه سفرِ مشتری';
  if (/^shop\.(order_notify|device_push)/.test(k.key)) return 'اعلان به گوشی‌های دکان (Firebase، O-01)، نه صفحهٔ مشتری';
  if (/^market\./.test(k.key)) return 'بازارچه، نه ویترینِ یک دکان';
  return 'بیرون از خانوادهٔ مشتری‌رو';
};
console.log(`\nروشن (${ON.length}، از KNOWN): ${ON.join(' ')}`);
console.log(`پیش‌نیاز (${Object.keys(DEPS).length}): ${Object.entries(DEPS).map(([k, w]) => `${k} — ${w}`).join(' · ')}`);
console.log(`کنار مانده (کد، نه کاتالوگ): ${[...RESERVED.filter(inFamily).map((k) => `${k} — کلیدِ رزروِ خودِ سرور، نه پرچم`), ...Object.entries(RETIRED).map(([k, w]) => `${k} — ${w}`)].join(' · ')}`);
console.log('کنار مانده (کاتالوگ):');
for (const k of KNOWN.filter((x) => !ON.includes(x.key) && !DEPS[x.key])) console.log(`   ${k.key} — ${WHY(k)}`);
console.log('');
for (const key of [...ON, ...Object.keys(DEPS)]) {
  const r = await call('PUT', '/api/admin/flags', { scope: shopId, key, value: true, reason: 'آزمونِ همه‌روشن ِ ویترین' }, A);
  if (r.status !== 200) ok(false, `روشن کردنِ ${key}`, JSON.stringify(r.body).slice(0, 160));
}
{
  const st = await call('GET', `/api/admin/flags?scope=${encodeURIComponent(shopId)}`, null, A);
  const items = new Map((st.body.items || []).map((i) => [i.key, i.value]));
  const notOn = [...ON, ...Object.keys(DEPS)].filter((k) => items.get(k) !== true);
  ok(notOn.length === 0, `همهٔ ${ON.length + Object.keys(DEPS).length} پرچم برای همین دکان true است`, notOn.join(' '));
}
const sub = await call('POST', '/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, A);
ok(sub.status < 300, 'اشتراکِ فعال (`shop.orders.subscribers_only` و حساب‌پی فقط برای دکانِ مشترک)', JSON.stringify(sub.body).slice(0, 100));

const slug = 'allflags-store';
const put = await call('PUT', '/api/shop/storefront', {
  slug, enabled: true, title: 'ویترینِ همه‌روشن', tagline: 'کالای روزانه', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 5000, minOrderMinor: 0, payAfterAccept: true,
}, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد (با «پرداخت پس از پذیرش»)', JSON.stringify(put.body).slice(0, 120));
let opn = 0;
const op = (table, row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++opn}`, ts: Date.now(), table, row_id: row, type: 'insert', fields });
const PRODUCTS = {
  rice: { name: 'برنج', salePrice: 300, category: 'خوراکی', brand: 'Alpha', units: 40 },
  oil: { name: 'روغن', salePrice: 500, category: 'خوراکی', brand: 'Beta', units: 40 },
  tea: { name: 'چای 500 گرمی', salePrice: 150, category: 'نوشیدنی', brand: 'Alpha', units: 40 },
  soap: { name: 'صابون', salePrice: 40, category: 'بهداشتی', brand: 'Beta', units: 0 },
};
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  ...Object.entries(PRODUCTS).map(([id, p]) => op('products', id, { name: p.name, unit: 'دانه', salePrice: p.salePrice, category: p.category, brand: p.brand, active: true })),
  ...Object.entries(PRODUCTS).filter(([, p]) => p.units > 0).map(([id, p]) => op('warehouseEntries', `${id}-e1`,
    { productId: id, units: p.units, cartons: 0, perCarton: 0, unit: 'دانه', price: 10, createdAt: Date.now() })),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا و موجودی از راهِ همگام‌سازیِ واقعی رسیدند', JSON.stringify(pushed.body).slice(0, 100));
for (const id of Object.keys(PRODUCTS)) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);
const photo = await call('PUT', '/api/sync/v1/photos/rice', { data: F.jpeg(320, 320, { seed: 9 }).toString('base64') }, T,
  { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(photo.status === 200, 'عکسِ برنج همگام شد');
const ZONE = 'کارته‌سه';
const zones = await call('PUT', '/api/shop/storefront/delivery', { zones: [{
  name: ZONE, kind: 'polygon', polygon: [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]],
  feeMinor: 3000, minOrderMinor: 60000, freeOverMinor: 100000, smallBasketFeeMinor: 1500,
}] }, T);
ok(zones.status === 200, 'ناحیهٔ ارسال (هزینهٔ ۳۰، حداقلِ ۶۰۰، رایگان از ۱٬۰۰۰)', JSON.stringify(zones.body).slice(0, 100));
const INSIDE = { latitude: 34.53, longitude: 69.17 };
const OUTSIDE = { latitude: 35.2, longitude: 70.4 };

//  پیکِ دکان (همان درِ برنامهٔ دکان)
const courierUser = await register('courier@allflags.local', 'کریم احمدی', 'dev-courier');
await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
  [newId('mem'), shopId, courierUser.userId, now()]);
const cr = await call('POST', '/api/shop/delivery/couriers', { userId: courierUser.userId, name: 'کریم احمدی', phone: '0799999999', cashCapMinor: 5_000_000 }, T);
ok(cr.status === 201, 'پیکِ دکان با نام و شماره', JSON.stringify(cr.body).slice(0, 100));
const courierId = cr.body && cr.body.courier && cr.body.courier.id;
let evn = 0;
const courierEv = async (assignmentId, type, extra = {}) => {
  const r = await call('POST', '/api/courier/events', { deviceId: 'courier-phone', events: [{ eventId: `ev-${Date.now()}-${++evn}`, assignmentId, type, deviceTime: Date.now(), deviceSeq: evn, ...extra }] }, courierUser.token);
  const res = r.body && r.body.results && r.body.results[0];
  if (r.status !== 200 || !res || res.status !== 'applied') ok(false, `رویدادِ پیک ${type}`, JSON.stringify(r.body).slice(0, 200));
};
const move = async (orderId, steps) => {
  for (const to of steps) {
    const r = await call('POST', `/api/shop/orders/${orderId}/transition`, { to, ...(to === 'accepted' ? { readyInMinutes: 15 } : {}) }, T);
    if (r.status !== 200) ok(false, `گذارِ ${to}`, JSON.stringify(r.body).slice(0, 200));
  }
};

// ══════════════════════ مرورگر و سنجه‌های هر صفحه ══════════════════════
const MEDIA = ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'];
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome', args: MEDIA }).catch(() => chromium.launch({ args: MEDIA }));
const BUDGETED = ['/kit/boot.js', '/kit/kit.js', '/store/store.js'];
const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const pages = [];   //  خلاصهٔ هر صفحه (برای چاپِ پایانی)

/** یک «دستگاهِ» مشتری: بافتِ تازه با همهٔ شنودها؛ `tag` = «۳۶۰·fa» */
async function device(width, lang, { ticket = null } = {}) {
  const tag = `${width}·${lang}`;
  const ctx = await browser.newContext({ viewport: { width, height: 800 }, locale: lang === 'en' ? 'en-US' : 'fa-AF', geolocation: INSIDE,
    permissions: ['geolocation', 'clipboard-read', 'clipboard-write'] });
  await ctx.grantPermissions(['microphone', 'geolocation', 'clipboard-read', 'clipboard-write'], { origin: base });
  await ctx.addInitScript(([tk]) => {
    window.__csp = [];
    //  هر گذار/پویانماییِ تازه (مثلاً «ثبت» که پس از پاسخِ ناحیه باز می‌شود و رنگش ۱۵۰ms جابه‌جا می‌شود)
    window.__motion = { n: 0, at: 0 };
    const bump = () => { window.__motion.n++; window.__motion.at = performance.now(); };
    for (const ev of ['transitionrun', 'animationstart']) document.addEventListener(ev, bump, true);
    document.addEventListener('securitypolicyviolation', (e) => window.__csp.push(`${e.violatedDirective} ${e.blockedURI}`));
    try {
      if (tk && !sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1'); localStorage.setItem('store-ticket', JSON.stringify(tk)); }
    } catch { /* about:blank */ }
  }, [ticket]);
  const page = await ctx.newPage();
  const P = { ctx, page, tag, width, lang, errors: [], csp: [], http: [], expect: [], docs: [], scripts: new Map() };
  page.on('pageerror', (e) => P.errors.push(String(e)));
  page.on('console', (m) => {
    const t = m.text();
    if (/Content Security Policy/i.test(t)) P.csp.push(t);
    else if (m.type() === 'error' && !/^Failed to load resource/.test(t)) P.errors.push(`console: ${t}`);
  });
  page.on('response', async (r) => {
    const u = new URL(r.url());
    if (u.origin !== base) return;
    const req = r.request();
    if (r.status() >= 400) P.http.push({ m: req.method(), p: u.pathname, s: r.status() });
    if (req.resourceType() === 'script' && r.status() === 200) {
      const b = await r.body().catch(() => null);
      if (b) P.scripts.set(u.pathname, b.length);
    }
    if (req.resourceType() === 'document' && r.frame() === page.mainFrame() && r.status() === 200) {
      const html = await r.text().catch(() => '');
      P.docs.push({ url: u.pathname, scripts: [...html.matchAll(/<script\b[^>]*\bsrc="([^"]+)"/g)].map((x) => x[1].split('?')[0]) });
    }
  });
  return P;
}
/** ۴xx/۵xx ِ موردِ انتظار (پیش از کار): `seen` باید پس از کار > ۰ باشد */
function expectHttp(P, re, status, why) { const e = { re, status, why, seen: 0 }; P.expect.push(e); return e; }
const url = (P, hash = '#/', extra = '') => `${base}/store/${slug}?lang=${P.lang}${extra}${hash}`;
async function goStore(P, hash = '#/', extra = '') {
  await P.page.goto(url(P, hash, extra));
  await P.page.waitForSelector('#products li[data-id="rice"]', { state: 'attached', timeout: 15000 });
}

/** سنجه‌های هر صفحه: گذارها تمام ⇒ axe، بیرون‌زدگی، خطای JS/CSP، ۴xx/۵xx ِ ناخواسته و بارِ نخست */
async function check(P, label) {
  const { page } = P;
  const name = `${P.tag} · ${label}`;
  const fails = [];
  await page.evaluate(() => document.fonts && document.fonts.ready).catch(() => {});
  //  پایانِ همهٔ گذارهای پایان‌دار و ۳۰۰ms بی گذارِ تازه (رنگِ نیمه‌راه کنتراستِ ساختگی می‌دهد؛ web-store.mjs ِ IMG-12)؛
  //  چرخنده‌های بی‌پایان شمرده نمی‌شوند
  const settle = () => page.waitForFunction(() => document.getAnimations().every((a) => {
    const tm = a.effect && a.effect.getComputedTiming ? a.effect.getComputedTiming() : null;
    return a.playState !== 'running' || Boolean(tm && tm.iterations === Infinity);
  }) && performance.now() - ((window.__motion || {}).at || 0) > 300, null, { timeout: 10000 }).then(() => true, () => false);
  const settled = await settle();
  if (!settled) fails.push('گذارها تمام نشدند');
  const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  if (over > 0) fails.push(`بیرون‌زدگیِ افقی ${over}px`);
  let axeN = '—';
  if (axeSrc) {
    //  axe از CDP (page.evaluate)، نه <script> ِ درون‌خطی ⇒ همان CSP ِ سخت می‌ماند و هر نقضی شمرده می‌شود
    if (!(await page.evaluate(() => typeof window.axe === 'object'))) await page.evaluate(axeSrc);
    const run = () => page.evaluate(async (tags) => {
      const busy = () => document.getAnimations().some((a) => {
        const tm = a.effect && a.effect.getComputedTiming ? a.effect.getComputedTiming() : null;
        return a.playState === 'running' && !(tm && tm.iterations === Infinity);
      });
      const n0 = window.__motion.n;
      const before = busy();
      const v = (await window.axe.run(document, { runOnly: { type: 'tag', values: tags }, resultTypes: ['violations'] })).violations
        .map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((n) => n.target.join(' ') + (n.any[0] ? ` (${n.any[0].message})` : '')).join(' ; ')}`);
      return { v, raced: before || busy() || window.__motion.n !== n0 };
    }, AXE_TAGS);
    //  گذاری که در میانهٔ خودِ axe آغاز شد ⇒ عکسِ نیمه‌راه؛ پس از آرام شدن دوباره (حداکثر سه بار، نتیجهٔ آخر گزارش می‌شود)
    let r = await run();
    for (let i = 0; r.raced && i < 2; i++) { await settle(); r = await run(); }
    const v = r.v;
    axeN = v.length;
    if (v.length) fails.push(`axe: ${v.join(' | ')}`);
  }
  const csp = P.csp.splice(0).concat(await page.evaluate(() => (window.__csp || []).splice(0)).catch(() => []));
  if (csp.length) fails.push(`CSP: ${csp.join(' | ')}`);
  const errs = P.errors.splice(0);
  if (errs.length) fails.push(`JS: ${errs.join(' | ')}`);
  const http = P.http.splice(0).filter((h) => {
    const e = P.expect.find((x) => x.status === h.s && x.re.test(`${h.m} ${h.p}`));
    if (e) { e.seen++; return false; }
    return true;
  });
  if (http.length) fails.push(`HTTP: ${http.map((h) => `${h.s} ${h.m} ${h.p}`).join(' | ')}`);
  let budget = '';
  for (const d of P.docs.splice(0)) {
    const extra = d.scripts.filter((s) => !BUDGETED.includes(s));
    await until(async () => d.scripts.every((s) => P.scripts.has(s)), 5000);
    const bytes = d.scripts.reduce((n, s) => n + (P.scripts.get(s) || 0), 0);
    budget = `${bytes.toLocaleString('en')}B`;
    if (extra.length) fails.push(`بارِ نخستِ ${d.url}: پرونده‌های بیرون از بودجه ${extra.join(' ')}`);
    if (!(bytes > 0 && bytes < FIRST_LOAD)) fails.push(`بارِ نخستِ ${d.url}: ${bytes} ≥ ${FIRST_LOAD}`);
  }
  pages.push({ name, ok: fails.length === 0 });
  ok(fails.length === 0, `صفحه ${name}: axe ${axeN} · بیرون‌زدگی ${Math.max(over, 0)}px · JS/CSP/HTTP ${fails.length ? '…' : '۰'}${budget ? ` · بارِ نخست ${budget}` : ''}`, fails.join(' ‖ '));
}
async function closeDevice(P) {
  for (const e of P.expect) ok(e.seen > 0, `${P.tag}: ${e.status} ِ موردِ انتظار رسید — ${e.why}`, `${e.seen}`);
  await P.ctx.close();
}
/** صبر تا شرط، با نتیجهٔ گزارش‌شده (هرگز بلعیده نمی‌شود) */
const waitOk = (P, pred, arg, what) => P.page.waitForFunction(pred, arg, { timeout: 15000 }).then(() => ok(true, `${P.tag}: ${what}`), () => ok(false, `${P.tag}: ${what}`));
const shown = (P, id) => P.page.evaluate((x) => { const e = document.getElementById(x); return Boolean(e) && !e.hidden && e.offsetParent !== null; }, id);
const ids = (P) => P.page.locator('#products li[data-id]').evaluateAll((l) => l.map((x) => x.getAttribute('data-id')).sort().join(','));
async function clickCard(P, id, sel) {
  const b = P.page.locator(`#products li[data-id="${id}"] ${sel}`).first();
  await b.waitFor({ state: 'visible', timeout: 10000 });
  //  کلیکِ DOM (همان web-store-zones): نوارِ پایین و بنرهای دیربار جای کاشی را جابه‌جا می‌کنند
  await b.evaluate((e) => { e.scrollIntoView({ block: 'center' }); e.click(); });
}
const cartLocal = (P) => P.page.evaluate((k) => { try { return Object.fromEntries(Object.entries(JSON.parse(localStorage.getItem(k)) || {}).map(([i, l]) => [i, l.qtyMilli])); } catch { return {}; } }, `store-cart:${slug}`);
const dbCart = async (phone) => {   //  «فقط ایمیل»: حساب با ایمیلِ تأییدشده (همان mailOf ِ شماره)
  const r = await one(`SELECT c.items FROM store_customer_carts c JOIN store_customers s ON s.id = c.customer_id WHERE c.shop_id = $1 AND s.email = $2`, [shopId, mailOf(phone)]);
  if (!r) return null;
  const items = typeof r.items === 'string' ? JSON.parse(r.items) : r.items;
  return Object.fromEntries(items.map((i) => [i.productId, i.qtyMilli]));
};
const same = (a, b) => JSON.stringify(Object.entries(a || {}).sort()) === JSON.stringify(Object.entries(b || {}).sort());
const devCodeOf = (txt) => latin((/([0-9۰-۹]{4,8})/.exec(txt || '') || [])[1] || '');

// ══════════════════════ سفرِ کامل ══════════════════════
const orders = [];
async function journey(width, lang, phone) {
  const P = await device(width, lang);
  const { page } = P;
  const fa = lang === 'fa';
  const intl = `+93${phone.slice(1)}`;

  //  ── ۱ ویترین و دسته‌ها ──
  await goStore(P);
  if (!orders.length) {
    //  سنجهٔ سنجه‌ها: اسکریپتِ درون‌خطی (CSP)، خطای JS و یک ۴۰۴ ِ واقعی باید دیده شوند — وگرنه «سبز» بی‌معناست
    await page.evaluate(() => {
      const sc = document.createElement('script'); sc.textContent = 'window.__probe = 1'; document.head.appendChild(sc);
      setTimeout(() => { throw new Error('probe-error'); }, 0);
      return fetch('/kit/no-such-file.js').catch(() => null);
    });
    await until(async () => P.errors.length && P.http.length && (P.csp.length || await page.evaluate(() => window.__csp.length)), 5000);
    const seen = { csp: P.csp.length + await page.evaluate(() => window.__csp.length), js: P.errors.some((e) => /probe-error/.test(e)),
      http: P.http.map((h) => h.s).join(), ran: await page.evaluate(() => window.__probe === 1) };
    ok(seen.csp > 0 && seen.js && /^4\d\d$/.test(seen.http) && !seen.ran, `${P.tag}: سنجه‌ها کار می‌کنند — CSP اسکریپتِ درون‌خطی را بست و دیده شد، خطای JS و ۴۰۴ دیده شدند`, JSON.stringify(seen));
    P.csp.length = 0; P.errors.length = 0; P.http.length = 0;
    await page.evaluate(() => { window.__csp = []; });
  }
  ok(await page.evaluate((l) => document.documentElement.lang === l && document.documentElement.dir === (l === 'en' ? 'ltr' : 'rtl'), lang), `${P.tag}: زبان و جهتِ صفحه`);
  //  صابونِ ناموجود هم دیده می‌شود (بی «پنهان کردنِ ناموجود») تا «وقتی موجود شد خبرم کن» داشته باشد
  ok(await ids(P) === 'oil,rice,soap,tea', `${P.tag}: هر چهار کالای منتشرشده، ناموجود هم`, await ids(P));
  await check(P, 'ویترین');
  const chip = page.locator('#cats button', { hasText: 'نوشیدنی' });
  await chip.click();
  await page.waitForFunction(() => [...document.querySelectorAll('#products li[data-id]')].map((x) => x.dataset.id).join() === 'tea', null, { timeout: 10000 })
    .then(() => ok(true, `${P.tag}: دستهٔ «نوشیدنی» ⇒ فقط چای`), async () => ok(false, `${P.tag}: دستهٔ «نوشیدنی» ⇒ فقط چای`, await ids(P)));
  ok(await chip.getAttribute('aria-pressed') === 'true', `${P.tag}: دکمهٔ دستهٔ برگزیده aria-pressed`);
  await check(P, 'دستهٔ نوشیدنی');
  await page.locator('#cats button').first().click();
  await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length >= 3, null, { timeout: 10000 });

  //  ── ۲ جست‌وجو ──
  const search = async (q, want, what) => {
    await page.fill('#q', q);
    const hit = await page.waitForFunction((w) => [...document.querySelectorAll('#products li[data-id]')].map((x) => x.dataset.id).sort().join() === w, want, { timeout: 10000 })
      .then(() => true, () => false);
    ok(hit, `${P.tag}: جست‌وجوی «${q}» ⇒ ${want} (${what})`, await ids(P));
  };
  await search('چای ۵۰۰', 'tea', 'رقمِ فارسی = لاتین، SRCH-01');
  await check(P, 'جست‌وجو با رقمِ فارسی');
  await search('چاي', 'tea', '«ي» ِ عربی = «ی»');
  await search('روغم', 'oil', 'غلطِ تایپی، WEB-05');
  await page.fill('#q', 'چیزی‌که‌نیست');
  await page.waitForSelector('#catalog-empty:not([hidden])', { timeout: 10000 });
  ok(await shown(P, 'picks') || await shown(P, 'store-call'), `${P.tag}: «پیدا نشد» با پیشنهاد یا تماس (WEB-06)`);
  await check(P, 'جست‌وجوی بی‌نتیجه');
  await page.click('#clear-search');
  await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length >= 3, null, { timeout: 10000 });

  //  ── ۳ برگهٔ کالا، فرستادن/سئو، اخیر، «خبرم کن» ──
  await clickCard(P, 'rice', '.p-open');
  await page.waitForSelector('#product-sheet:not([hidden]) #ps-name', { timeout: 10000 });
  ok((await page.textContent('#ps-name')).includes('برنج'), `${P.tag}: برگهٔ کالا`);
  await page.waitForSelector('#ps-share:not([hidden])', { timeout: 10000 });
  await page.click('#ps-share');
  const copied = await until(() => page.evaluate(() => navigator.clipboard.readText().catch(() => '')), 5000);
  ok(/\/p\/rice$/.test(copied || ''), `${P.tag}: «فرستادن» نشانیِ /p/rice را کپی کرد (WEB-20)`, copied);
  await page.waitForSelector('#ps-alerts:not([hidden])', { timeout: 10000 });
  ok(await page.locator('#ps-alerts button').count() === 0, `${P.tag}: «خبرم کن» بی شمارهٔ تأییدشده فقط راهنماست (WEB-10)`);
  await check(P, 'برگهٔ کالا');
  await page.click('#ps-close');
  //  صفحهٔ سئوی کالا (WEB-17/20): همان پوسته با برچسب‌های همان کالا؛ JS به #/p/rice برمی‌گرداند
  await page.goto(`${base}/store/${slug}/p/rice?lang=${lang}`);
  await page.waitForSelector('#product-sheet:not([hidden]) #ps-name', { timeout: 15000 });
  const og = await page.evaluate(() => ({ t: (document.querySelector('meta[property="og:title"]') || {}).content || '', ld: Boolean(document.querySelector('script[type="application/ld+json"]')) }));
  ok(og.t.includes('برنج') && og.ld, `${P.tag}: صفحهٔ سئوی کالا با og:title و JSON-LD`, JSON.stringify(og));
  await check(P, 'صفحهٔ سئوی کالا');
  await goStore(P);
  await page.waitForSelector('#recent:not([hidden]) li[data-id="rice"]', { timeout: 10000 }).then(() => ok(true, `${P.tag}: «دیده‌شده‌های اخیر» برنج را دارد (WEB-13)`),
    () => ok(false, `${P.tag}: «دیده‌شده‌های اخیر» برنج را دارد (WEB-13)`));
  await check(P, 'ویترین با «اخیر»');

  //  ── ۴ سبد، ورود به حسابِ وب و تغییرِ تعداد ──
  await clickCard(P, 'rice', '.p-add');
  await clickCard(P, 'oil', '.p-add');
  await page.waitForSelector('#cart-bar:not([hidden])', { timeout: 10000 });
  ok(same(await cartLocal(P), { rice: 1000, oil: 1000 }), `${P.tag}: سبدِ مهمان برنج و روغن`, JSON.stringify(await cartLocal(P)));
  await check(P, 'ویترین با سبد');
  await page.goto(url(P, '#/orders'));
  await page.waitForSelector('#ac-mine > button.btn-sm:not(.btn-ghost)', { timeout: 15000 });
  await check(P, 'سفارش‌های من (بی حساب)');
  await page.click('#ac-mine > button.btn-sm:not(.btn-ghost)');
  await page.fill('#ac-mine input[type="email"]', mailOf(phone));
  await page.click('#ac-mine button.btn-soft');
  await page.waitForSelector('#ac-mine .code-row:not([hidden])', { timeout: 10000 });
  const code = devCodeOf(await page.textContent('#ac-mine .note'));
  ok(/^\d{4,8}$/.test(code), `${P.tag}: کدِ آزمایشی (فقط بیرون از تولید)`, code);
  await check(P, 'ورود: کدِ تأیید');
  await page.fill('#ac-mine input[autocomplete="one-time-code"]', code);
  await page.click('#ac-mine .code-row button');
  await page.waitForSelector('#ac-mine button.btn-ghost', { timeout: 10000 });
  const merged = await until(() => page.evaluate((k) => { try { return JSON.parse(localStorage.getItem(k)).merged === true; } catch { return false; } }, `store-cartsync:${slug}`), 10000);
  ok(merged, `${P.tag}: ورود ⇒ سبدِ دستگاه با سبدِ حساب ادغام شد (shop.storefront.cart)`);
  ok(await until(async () => same(await dbCart(intl), { rice: 1000, oil: 1000 })), `${P.tag}: سبدِ حساب روی سرور = سبدِ دستگاه`, JSON.stringify(await dbCart(intl)));
  await check(P, 'سفارش‌های من (با حساب)');
  //  «خبرم کن» با شمارهٔ تأییدشده: قیمت (برنج) و موجودی (صابونِ ناموجود)
  await goStore(P, '#/p/rice');
  await page.waitForSelector('#ps-alerts:not([hidden]) button', { timeout: 15000 });
  await page.locator('#ps-alerts button').first().click();
  await page.waitForSelector('#ps-alerts button[aria-pressed="true"]', { timeout: 10000 });
  const al = await one("SELECT kind, state FROM storefront_alerts WHERE shop_id=$1 AND product_id='rice' ORDER BY created_at DESC LIMIT 1", [shopId]);
  ok(al && al.state === 'armed', `${P.tag}: «وقتی ارزان شد خبرم کن» روی سرور`, JSON.stringify(al));
  await check(P, 'برگهٔ کالا با «خبرم کن»');
  await page.locator('#ps-alerts button[aria-pressed="true"]').first().click();
  await page.waitForSelector('#ps-alerts button[aria-pressed="false"]', { timeout: 10000 });
  await page.click('#ps-close');
  {
    await clickCard(P, 'soap', '.p-open');
    await page.waitForSelector('#product-sheet:not([hidden]) #ps-name', { timeout: 10000 });
    await page.waitForSelector('#ps-alerts:not([hidden]) button', { timeout: 10000 });
    await page.locator('#ps-alerts button').first().click();
    await page.waitForSelector('#ps-alerts button[aria-pressed="true"]', { timeout: 10000 });
    const sa = await one("SELECT kind, state FROM storefront_alerts WHERE shop_id=$1 AND product_id='soap' ORDER BY created_at DESC LIMIT 1", [shopId]);
    ok(sa && sa.kind === 'stock' && sa.state === 'armed', `${P.tag}: کالای ناموجود ⇒ «وقتی موجود شد خبرم کن»`, JSON.stringify(sa));
    await check(P, 'برگهٔ کالای ناموجود');
    await page.click('#ps-close');
  }
  //  تغییرِ تعداد در سبد: برنج +۱، روغن −۱ (بیرون)، چای +۱ ⇒ برنج ۲ و چای ۱ (۷۵۰)
  await clickCard(P, 'tea', '.p-add');
  await page.click('#open-cart');
  await page.waitForSelector('#sheet-lines li .stepper', { timeout: 10000 });
  const stepper = (name, i) => page.locator('#sheet-lines li', { hasText: name }).locator('.stepper button').nth(i);
  await stepper('برنج', 1).click();
  await stepper('روغن', 0).click();
  await page.waitForFunction(() => document.querySelectorAll('#sheet-lines li').length === 2, null, { timeout: 10000 });
  ok(same(await cartLocal(P), { rice: 2000, tea: 1000 }), `${P.tag}: سبدِ دستگاه پس از −/+`, JSON.stringify(await cartLocal(P)));
  ok(await until(async () => same(await dbCart(intl), { rice: 2000, tea: 1000 })), `${P.tag}: همان تعدادها روی سبدِ سرور (درنگ‌دار)`, JSON.stringify(await dbCart(intl)));
  await page.waitForFunction(() => {
    const sh = document.getElementById('cart-sheet');
    return Boolean(sh) && !sh.hidden && getComputedStyle(sh).opacity === '1'
      && (!document.body.classList.contains('phone-cart') || document.body.classList.contains('cart-open'));
  }, null, { timeout: 10000 });
  await check(P, 'برگهٔ سبد');

  //  ── ۵ ثبت: ناحیه درون و بیرون، «دریافت از دکان»، اسکناس، گامِ پرداخت ──
  await page.click('#cart-next');
  await page.waitForSelector('#view-checkout:not([hidden])', { timeout: 10000 });
  await page.fill('#c-name', 'مشتریِ همه‌روشن');
  await page.fill('#c-phone', phone);   //  «فقط ایمیل»: شمارهٔ تماسِ پیک جدا و بی کد
  await page.check('input[name="fulfillment"][value="delivery"]');
  await page.waitForSelector('#zone-box:not([hidden])', { timeout: 10000 });
  await page.click('#pin-btn');
  await page.waitForFunction((z) => document.getElementById('zone-msg').textContent.includes(z), ZONE, { timeout: 10000 })
    .then(() => ok(true, `${P.tag}: پینِ درون ⇒ ناحیهٔ ${ZONE}`), async () => ok(false, `${P.tag}: پینِ درون ⇒ ناحیهٔ ${ZONE}`, await page.textContent('#zone-msg')));
  await check(P, 'ثبت: ناحیهٔ درون');
  await P.ctx.setGeolocation(OUTSIDE);
  await page.click('#pin-btn');
  await page.waitForSelector('#zone-pickup:not([hidden])', { timeout: 10000 });
  ok(await shown(P, 'zone-pickup') && await shown(P, 'zone-notify') && await page.isDisabled('#submit-order'),
    `${P.tag}: پینِ بیرون ⇒ «دریافت از دکان» و «خبرم کن»، «ثبت» بسته (CHK-10)`);
  const n0 = (await one('SELECT COUNT(*)::int AS n FROM storefront_area_requests WHERE shop_id=$1', [shopId])).n;
  await page.click('#zone-notify');
  ok(await until(async () => (await one('SELECT COUNT(*)::int AS n FROM storefront_area_requests WHERE shop_id=$1', [shopId])).n === n0 + 1),
    `${P.tag}: «خبرم کن وقتی به این‌جا هم فرستادید» ثبت شد (shop.storefront.notify_area)`);
  await check(P, 'ثبت: بیرون از محدوده');
  await page.click('#zone-pickup');
  ok(await page.isChecked('input[name="fulfillment"][value="pickup"]'), `${P.tag}: «دریافت از دکان» روش را عوض کرد`);
  await check(P, 'ثبت: دریافت از دکان');
  await P.ctx.setGeolocation(INSIDE);
  await page.check('input[name="fulfillment"][value="delivery"]');
  await page.click('#pin-btn');
  await page.waitForFunction((z) => document.getElementById('zone-msg').textContent.includes(z), ZONE, { timeout: 10000 });
  //  S33 (`shop.orders.plus_code`): Plus Code ِ همان پین زیرِ آن، با `store/plus-code.js` ِ دیربار (همان حسابِ سرور)
  const pinCode = require('../src/lib/plus-code').encode(INSIDE.latitude, INSIDE.longitude);
  await page.waitForFunction((c) => document.getElementById('pin-text').textContent.includes(c), pinCode, { timeout: 10000 })
    .then(() => ok(true, `${P.tag}: Plus Code ِ پین زیرِ آن (S33)`), async () => ok(false, `${P.tag}: Plus Code ِ پین زیرِ آن (S33)`, await page.textContent('#pin-text')));
  await page.fill('#a-text', `${ZONE}، کوچهٔ دوم`);
  await page.waitForSelector('#tender-row:not([hidden]) #d-tender option[value="100000"]', { state: 'attached', timeout: 10000 });
  const opt = await page.locator('#d-tender option[value="100000"]').textContent();
  if (fa) ok(opt.includes('۱٬۰۰۰') && opt.includes('پول خرد'), `${P.tag}: گزینهٔ «با ۱۰۰۰ می‌پردازم» با پول خرد`, opt);
  await page.selectOption('#d-tender', '100000');
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 })
    .then(() => ok(true, `${P.tag}: با نشستِ حساب و ناحیهٔ درون «ثبت» باز است (بی کدِ دوباره)`), () => ok(false, `${P.tag}: «ثبت» باز است`));
  await check(P, 'ثبت: ارسال با اسکناس');
  const before = (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;
  await page.click('#submit-order');
  await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
  const row = await one('SELECT id, order_no, total_minor, delivery_fee_minor, delivery, address, customer_phone, status FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [shopId]);
  const aj = row && (typeof row.address === 'string' ? JSON.parse(row.address) : row.address);
  ok(aj && aj.pinCode === pinCode, `${P.tag}: سفارش همان Plus Code ِ پین را دارد که برگهٔ ثبت نشان داد (S33)`, JSON.stringify(aj && aj.pinCode));
  const dj = row && (typeof row.delivery === 'string' ? JSON.parse(row.delivery) : row.delivery);
  ok((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n === before + 1
    && Number(row.total_minor) === 78000 && Number(row.delivery_fee_minor) === 3000 && row.customer_phone === intl && dj && dj.cashTenderMinor === 100000,
  `${P.tag}: سفارش با قیمتِ سرور (۷۵۰ + ۳۰ ِ ناحیه) و اسکناسِ ۱۰۰۰`, JSON.stringify({ t: row && row.total_minor, f: row && row.delivery_fee_minor, tender: dj && dj.cashTenderMinor }));
  const token = decodeURIComponent((/#\/o\/(.+)$/.exec(page.url()) || [])[1] || '');
  //  `shop.storefront.link_expiry` ⇒ توکنِ زمان‌دارِ v2 (TEN-03: شناسه، زمانِ صدور، نسل، HMAC)
  ok(/^[0-9A-Z]{26}\.[0-9a-z]{1,12}\.[0-9a-z]{1,7}\.[A-Za-z0-9_-]{32}$/.test(token), `${P.tag}: نشانیِ پیگیری با توکنِ زمان‌دارِ حدس‌نزدنی (TEN-03)`, token);
  await waitOk(P, () => { const e = document.getElementById('o-placed'); return Boolean(e) && !e.hidden && e.textContent.trim() !== ''; }, null, '«سفارش ثبت شد» (رسید)');
  await page.waitForSelector('#o-ps-state', { timeout: 10000 });
  ok(!(await shown(P, 'o-ps-cash')), `${P.tag}: گامِ پرداخت پیش از پذیرش: بی دکمهٔ نقد`);
  ok(await until(async () => same(await dbCart(intl), {})), `${P.tag}: پس از ثبت سبدِ حساب روی سرور خالی شد`, JSON.stringify(await dbCart(intl)));
  await check(P, 'رسید');
  orders.push({ tag: P.tag, token, id: row.id, phone });

  //  ── ۶ گامِ پرداخت و پیگیری ──
  await move(row.id, ['accepted']);
  await page.reload();
  await page.waitForSelector('#o-ps-cash', { timeout: 15000 });
  ok(await shown(P, 'o-pay'), `${P.tag}: پس از پذیرش: نقد و حساب‌پی`);
  await check(P, 'پیگیری: گامِ پرداخت');
  await page.click('#o-ps-cash');
  await page.waitForFunction(() => !document.getElementById('o-ps-cash'), null, { timeout: 10000 }).then(() => ok(true, `${P.tag}: «نقد هنگامِ تحویل» از پاسخِ سرور`),
    () => ok(false, `${P.tag}: «نقد هنگامِ تحویل» از پاسخِ سرور`));
  await check(P, 'پیگیری: نقد برگزیده');

  //  ── ۷ گفت‌وگو: متن، عکس، صدا، صفِ بی‌اینترنت (سفارش هنوز زنده) ──
  await page.waitForSelector('#o-chat-open', { timeout: 15000 });
  await page.click('#o-chat-open');
  await page.waitForFunction(() => { const f = document.getElementById('ct-form'); return f && !f.hidden; }, null, { timeout: 15000 });
  const conv = await until(async () => one("SELECT c.id FROM shop_chat_conversations c WHERE c.shop_id=$1 AND c.order_id=$2", [shopId, row.id]).catch(() => null), 10000);
  ok(Boolean(conv), `${P.tag}: رشتهٔ گفت‌وگوی همین سفارش`);
  const count = async (kind) => Number((await one("SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1 AND sender='customer' AND kind=$2", [conv && conv.id, kind])).n);
  await check(P, 'گفت‌وگو: برگه');
  await page.fill('#ct-text', `سلام از ${P.tag}`);
  await page.click('#ct-send');
  ok(await until(async () => (await count('text')) === 1), `${P.tag}: پیامِ متنیِ مشتری روی سرور`);
  await page.setInputFiles('#ct-file', { name: 'bag.jpg', mimeType: 'image/jpeg', buffer: F.jpeg(640, 480, { seed: 31 }) });
  await page.waitForFunction(() => { const b = document.getElementById('ct-ph'); return b && !b.hidden && /^(ready|err)$/.test(b.getAttribute('data-st')); }, null, { timeout: 20000 });
  ok(await page.getAttribute('#ct-ph', 'data-st') === 'ready', `${P.tag}: پیش‌نمایشِ عکس`);
  await check(P, 'گفت‌وگو: پیش‌نمایشِ عکس');
  await page.click('#ct-ph-send');
  ok(await until(async () => (await count('image')) === 1, 20000), `${P.tag}: عکسِ مشتری روی سرور`);
  const recorder = await page.evaluate(() => typeof window.MediaRecorder === 'function' && Boolean(navigator.mediaDevices && navigator.mediaDevices.getUserMedia));
  if (recorder && await shown(P, 'ct-vc-mic')) {
    await page.click('#ct-vc-mic');
    await page.waitForFunction(() => { const b = document.getElementById('ct-vc'); return b && !b.hidden && b.getAttribute('data-st') === 'rec'; }, null, { timeout: 15000 });
    await check(P, 'گفت‌وگو: در حالِ ضبط');
    await sleep(1200);
    await page.click('#ct-vc-finish');
    ok(await until(async () => (await count('audio')) === 1, 20000), `${P.tag}: پیامِ صوتیِ مشتری روی سرور (shop.chat.voice)`);
  } else {
    ok(!recorder || (await one("SELECT 1 AS x FROM live_config WHERE key='shop.chat.voice'")) === null,
      `${P.tag}: صدا: ${recorder ? 'دکمهٔ میکروفون پیدا نیست با اینکه MediaRecorder هست' : 'این مرورگر MediaRecorder ندارد ⇒ بی دکمه'}`);
  }
  await P.ctx.setOffline(true);
  await page.fill('#ct-text', `بی‌اینترنت از ${P.tag}`);
  await page.click('#ct-send');
  await page.waitForSelector('#ct-out .ct-q[data-st="waiting"]', { timeout: 10000 });
  ok((await count('text')) === 1, `${P.tag}: بی‌اینترنت ⇒ «در صف»، نه روی سرور`);
  await check(P, 'گفت‌وگو: صفِ بی‌اینترنت');
  await P.ctx.setOffline(false);
  ok(await until(async () => (await count('text')) === 2, 25000), `${P.tag}: وصل شد ⇒ پیامِ صف خودش یک بار رفت`);
  await waitOk(P, () => !document.querySelector('#ct-out .ct-q'), null, 'صفِ گفت‌وگو پس از رفتن خالی شد');
  await check(P, 'گفت‌وگو: پس از صف');
  await page.keyboard.press('Escape');

  //  ── ۸ نمای پیک با گذارهای دکان و پیک ──
  await move(row.id, ['picking', 'packed']);
  const as = await call('POST', `/api/shop/delivery/orders/${row.id}/assign`, { mode: 'shop_courier', courierId, requireCode: true }, T, { 'Idempotency-Key': `asg-${row.id}` });
  ok(as.status === 201, `${P.tag}: دکان سفارش را به پیک سپرد`, JSON.stringify(as.body).slice(0, 160));
  const aid = as.body && as.body.assignment && as.body.assignment.id;
  const track = async (pred, what) => {
    await page.goto('about:blank');
    await page.goto(url(P, `#/o/${encodeURIComponent(token)}`));
    await page.waitForFunction(() => { const s = document.getElementById('o-status'); return !document.getElementById('view-order').hidden && s && s.textContent.trim() !== ''; }, null, { timeout: 15000 });
    ok(await until(() => page.evaluate(pred), 8000), `${P.tag}: ${what}`, await page.evaluate(() => [...['o-status', 'o-courier', 'o-eta', 'o-code-n']].map((i) => (document.getElementById(i) || {}).textContent || '').join(' | ')));
    await check(P, `پیگیری: ${what}`);
  };
  await track(() => !document.getElementById('o-code').hidden && /\S/.test(document.getElementById('o-code-n').textContent), 'سپرده: کدِ تحویل');
  await courierEv(aid, 'assigned');
  await track(() => /کریم/.test(document.getElementById('o-courier').textContent) && !/احمدی/.test(document.body.textContent), 'پیک پذیرفت: فقط نامِ کوچک');
  await courierEv(aid, 'picked_up');
  await courierEv(aid, 'moving');
  const eta = await call('POST', `/api/courier/missions/${aid}/eta`, { minutes: 20 }, courierUser.token);
  ok(eta.status === 200, `${P.tag}: پیک زمانِ تقریبی داد`);
  await track(() => !document.getElementById('o-eta').hidden, 'در راه با زمانِ تقریبی');
  await courierEv(aid, 'arrived');
  await track(() => !document.getElementById('o-courier').hidden && document.getElementById('o-eta').hidden, 'پیک رسید');
  const shownCode = latin(await page.textContent('#o-code-n'));
  await courierEv(aid, 'delivered', { cashCollectedMinor: 78000, code: shownCode });
  await track(() => document.getElementById('o-dlv').hidden, 'تحویل شد: بی کد و بی پیک');
  const fin = await one('SELECT status FROM online_orders WHERE id=$1', [row.id]);
  ok(fin && fin.status === 'delivered', `${P.tag}: سفارش روی سرور «تحویل شد»`, fin && fin.status);

  //  ── ۹ سفارش‌های من و خروج ──
  await page.goto(url(P, '#/orders'));
  await page.waitForSelector('#ac-mine button.btn-ghost', { timeout: 15000 });
  await waitOk(P, () => document.querySelectorAll('#my-orders li').length >= 1, null, '«سفارش‌های من» بار شد');
  ok(await page.locator('#my-orders li').count() >= 1, `${P.tag}: «سفارش‌های من» همین سفارش را دارد`);
  await check(P, 'سفارش‌های من پس از تحویل');
  await page.click('#ac-mine button.btn-ghost');
  await page.waitForSelector('#ac-mine > button.btn-sm:not(.btn-ghost)', { timeout: 10000 });
  ok(await page.evaluate(() => { try { return JSON.parse(localStorage.getItem('store-session')) === null; } catch { return true; } }), `${P.tag}: خروج ⇒ نشست از دستگاه پاک`);
  await check(P, 'پس از خروج');
  await closeDevice(P);
}

/** همان صفحه‌ها در یک ترکیبِ دیگرِ عرض و زبان (سفارش و رشتهٔ یک سفرِ کامل؛ بی ثبتِ تازه) */
async function sweep(width, lang, o) {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(o.phone) });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(o.phone), code: vs.body.devCode });
  const P = await device(width, lang, { ticket: { email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt } });
  const { page } = P;
  await goStore(P);
  await check(P, 'ویترین');
  await page.fill('#q', 'چای ۵۰۰');
  await waitOk(P, () => [...document.querySelectorAll('#products li[data-id]')].map((x) => x.dataset.id).join() === 'tea', null, 'جست‌وجوی «چای ۵۰۰» ⇒ چای');
  await check(P, 'جست‌وجو');
  await page.fill('#q', '');
  await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length >= 3, null, { timeout: 10000 });
  await clickCard(P, 'rice', '.p-open');
  await page.waitForSelector('#product-sheet:not([hidden]) #ps-name', { timeout: 10000 });
  await check(P, 'برگهٔ کالا');
  await page.click('#ps-close');
  await clickCard(P, 'rice', '.p-add');
  await page.click('#open-cart');
  await page.waitForSelector('#sheet-lines li .stepper', { timeout: 10000 });
  await page.waitForFunction(() => { const sh = document.getElementById('cart-sheet'); return Boolean(sh) && !sh.hidden && getComputedStyle(sh).opacity === '1'; }, null, { timeout: 10000 });
  await check(P, 'برگهٔ سبد');
  await page.click('#cart-next');
  await page.waitForSelector('#view-checkout:not([hidden])', { timeout: 10000 });
  await page.check('input[name="fulfillment"][value="delivery"]');
  await page.click('#pin-btn');
  await waitOk(P, (z) => document.getElementById('zone-msg').textContent.includes(z), ZONE, `پینِ درون ⇒ ناحیهٔ ${ZONE}`);
  await check(P, 'ثبت: ناحیه');
  await page.goto(url(P, `#/o/${encodeURIComponent(o.token)}`));
  await page.waitForFunction(() => { const s = document.getElementById('o-status'); return !document.getElementById('view-order').hidden && s && s.textContent.trim() !== ''; }, null, { timeout: 15000 });
  await check(P, 'پیگیری');
  {
    //  کارتِ گفت‌وگو دیربار است (chat.js + واژه‌نامه) ⇒ صبر، نه «اگر بود»
    await page.waitForSelector('#o-chat-open', { timeout: 15000 });
    await page.click('#o-chat-open');
    await waitOk(P, () => document.querySelectorAll('#ct-list .ct-msg').length > 0, null, 'گفت‌وگوی همان سفارش با پیام‌ها');
    await check(P, 'گفت‌وگو');
    await page.keyboard.press('Escape');
  }
  await page.goto(url(P, '#/orders'));
  await page.waitForSelector('#ac-mine button', { timeout: 15000 });
  await check(P, 'سفارش‌های من');
  await closeDevice(P);
}

const FULL = [[360, 'fa', '0793100001'], [768, 'en', '0793100002'], [1280, 'ps', '0793100003']];
for (const [w, l, ph] of FULL) await journey(w, l, ph);
const SWEEP = [[360, 'en'], [360, 'ps'], [768, 'fa'], [768, 'ps'], [1280, 'fa'], [1280, 'en']];
for (const [w, l] of SWEEP) await sweep(w, l, orders[0] || { token: '', phone: FULL[0][2] });

if (!axeSrc) {
  if (SKIP) console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
  else ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}
const first = BUDGETED.reduce((n, f) => n + fs.statSync(path.join(import.meta.dirname, '..', 'public', f)).size, 0);
ok(first < FIRST_LOAD, `بارِ نخست روی دیسک (${BUDGETED.join(' + ')}) زیرِ ${FIRST_LOAD} بایت`, `${first}`);

await browser.close();
server.close();
await closeDb();
console.log(`\nصفحه‌ها: ${pages.filter((p) => p.ok).length}/${pages.length} سالم · سنجه‌ها: ${checks - bad}/${checks}`);
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین با همهٔ پرچم‌های مشتری‌رو روشن: سفرِ کامل در سه عرض و سه زبان');
process.exit(bad ? 1 : 0);
