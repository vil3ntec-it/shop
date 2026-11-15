/**
 * ویترین با کرومیومِ واقعی: پیامِ ۴۰۹ ِ «ثبت» وقتی دکان سفارش نمی‌گیرد، در هر زبان (WEB-25، ORD-08/09، S30).
 *
 *   • `store_paused` (توقفِ دستیِ میزِ سفارش): en، ps و دری پیامِ معنادارِ همان زبان را می‌بینند، نه «خطای عمومی»؛
 *     فارسی همان متنِ سرور. هیچ سفارشی ساخته نمی‌شود و صفحه روی برگهٔ ثبت می‌ماند.
 *   • `store_offline` («توقف هنگامِ قطع» با دستگاه‌های کهنهٔ دکان): همان، در en و ps.
 *   • متنِ چشم‌داشته از خودِ واژه‌نامه‌های `kit/i18n` (هر پرونده‌ای که کلید را دارد)؛ نبودِ کلید = سرخ.
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت.
 *
 * پیش‌تر کلیدِ `err.store_paused`/`err.store_offline` در هیچ واژه‌نامه نبود (fa.json و checkout.fa.json پر بودند) ⇒
 * K.api برای غیرِفارسی «خطا؛ دوباره بزنید» می‌داد (دری هم، چون زبانش `prs` است نه `fa`).
 *
 *     TEST_DATABASE_URL=pglite:memory node test/web-store-paused-i18n.mjs      (در `npm run test:web-store`)
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
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

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.RATE_STORE_CHECKOUT_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };

//  واژه‌نامهٔ کاملِ هر زبان (اصلی + هر بخش)؛ دری = فارسی (+ لایهٔ dari.js در خودِ صفحه)
const I18N = path.join(import.meta.dirname, '..', 'public', 'kit', 'i18n');
const dictOf = (l) => Object.assign({}, ...fs.readdirSync(I18N).filter((f) => f === `${l}.json` || f.endsWith(`.${l}.json`))
  .map((f) => JSON.parse(fs.readFileSync(path.join(I18N, f), 'utf8'))));
const D = { fa: dictOf('fa'), en: dictOf('en'), ps: dictOf('ps') };

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

//  ── دکانِ آزمایشی (دادهٔ ساختگی) ──
const email = 'owner@paused.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ توقف' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.orders.desk']) await flag(key, true);
const slug = 'paused-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ توقف', contactPhone: '0700000002',
  acceptPickup: true, acceptDelivery: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 160));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
  op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
  fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
}] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);
const seen = (msAgo) => query(`UPDATE sync_devices SET last_seen_at=$2 WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId, Date.now() - msAgo]);
await seen(0);

const phone = '0791115252';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
const count = async () => Number((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n);

//  ── توقفِ دستیِ میز ⇒ ثبت ۴۰۹ store_paused ──
const paused = await call('POST', '/api/shop/orders/desk/pause', { eventId: `ev-${Date.now()}-pause`, minutes: 60, reason: 'busy' }, T);
ok(paused.status === 200 && paused.body?.state?.pause, 'میزِ سفارش: توقفِ ۶۰ دقیقه', JSON.stringify(paused.body).slice(0, 160));

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const CART = { rice: { qtyMilli: 1000, name: 'برنج', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false } };

/** زبانِ `lang` ⇒ برگهٔ ثبت با یک برنج ⇒ «ثبت» ⇒ { status ِ پاسخ، متنِ #checkout-msg، هنوز روی برگهٔ ثبت؟ } */
async function submitIn(lang) {
  const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: lang === 'en' ? 'en-GB' : 'fa-AF' });
  await ctx.addInitScript(([t, s, c]) => {
    localStorage.setItem('store-ticket', JSON.stringify(t));
    localStorage.setItem(`store-cart:${s}`, JSON.stringify(c));
  }, [{ email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt }, slug, CART]);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(`${lang}: ${e}`));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  await page.goto(`${base}/store/${slug}?lang=${lang}#/checkout`);
  await page.waitForSelector('#view-checkout:not([hidden]) #submit-order', { timeout: 15000 });
  await page.fill('#c-name', 'Customer');
  await page.check('input[name="fulfillment"][value="pickup"]');
  const [res] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith(`/api/store/${slug}/checkout`) && r.request().method() === 'POST', { timeout: 15000 }),
    page.click('#submit-order'),
  ]);
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled && !document.getElementById('checkout-msg').hidden
    && document.getElementById('checkout-msg').textContent.trim().length > 0, null, { timeout: 15000 });
  const err = (await res.json().catch(() => null))?.error || {};
  const out = { status: res.status(), code: err.code, server: err.message, msg: (await page.textContent('#checkout-msg')).trim(),
    stayed: await page.evaluate(() => location.hash === '#/checkout') };
  await ctx.close();
  return out;
}

/** پیامِ چشم‌داشته: کلیدِ `err.<code>` ِ واژه‌نامهٔ همان زبان، نه «خطای عمومی» */
function expect(lang, code, r, word) {
  const d = D[lang === 'prs' ? 'fa' : lang];
  const want = d[`err.${code}`];
  ok(r.status === 409 && r.code === code, `${lang}: ثبت ⇒ ۴۰۹ ${code}`, `${r.status} ${r.code}`);
  //  fa: همان متنِ سرور (کلیدِ واژه‌نامه = همان متن)؛ دری: همان قالبِ فارسی پس از لایهٔ دری؛ en/ps: دقیقاً متنِ واژه‌نامه
  ok(Boolean(want) && (lang === 'prs' || r.msg === want), `${lang}: واژه‌نامه کلیدِ err.${code} را دارد و صفحه همان را نشان داد`, r.msg);
  if (lang === 'fa') ok(r.msg === r.server, 'fa: همان پیامِ روشنِ سرور', r.server);
  ok(r.msg !== d['err.generic'] && word.test(r.msg), `${lang}: پیامِ معنادار، نه «خطای عمومی»`, r.msg);
  ok(r.stayed, `${lang}: صفحه روی برگهٔ ثبت ماند`);
}

const n0 = await count();
expect('en', 'store_paused', await submitIn('en'), /paused/i);
expect('ps', 'store_paused', await submitIn('ps'), /درول/);
//  دری: واژه‌نامهٔ فارسی + لایهٔ دری (پیش‌تر «خطای عمومی»، چون زبان `prs` است)
expect('prs', 'store_paused', await submitIn('prs'), /متوقف/);
expect('fa', 'store_paused', await submitIn('fa'), /متوقف/);
ok(await count() === n0, 'هیچ سفارشی ساخته نشد (توقف)');

//  ── «توقف هنگامِ قطع» ⇒ ثبتِ فوری ۴۰۹ store_offline ──
const resumed = await call('POST', '/api/shop/orders/desk/resume', { eventId: `ev-${Date.now()}-resume` }, T);
ok(resumed.status === 200, 'میزِ سفارش: پایانِ توقف', JSON.stringify(resumed.body).slice(0, 120));
const desk = await call('PUT', '/api/shop/orders/desk/settings', { eventId: `ev-${Date.now()}-desk`, offlinePause: { enabled: true } }, T);
ok(desk.status === 200, 'میزِ سفارش: «توقف هنگامِ قطع» روشن', JSON.stringify(desk.body).slice(0, 160));
await seen(20 * 60 * 1000);
expect('en', 'store_offline', await submitIn('en'), /internet/i);
expect('ps', 'store_offline', await submitIn('ps'), /انټرنېټ/);
ok(await count() === n0, 'هیچ سفارشی ساخته نشد (قطع)');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: پیامِ توقف و قطعِ دکان در هر زبان از خودِ صفحه');
process.exit(bad ? 1 : 0);
