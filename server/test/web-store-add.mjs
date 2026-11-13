/**
 * ویترین با کرومیومِ واقعی: «افزودنِ سبد به همین سفارش» در صفحهٔ پیگیری — CHK-21 (سرور: `order-add-items.js`).
 *
 *     npm run test:web-store      (پس از web-store-web.mjs)
 *
 * order-add-items.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • سفارشِ تازه با بلیتِ تأییدشدهٔ همین دستگاه و یک کالا در سبد ⇒ دکمه پیدا؛ زدن و «بله» ⇒ قلمِ تازه در
 *     فهرستِ اقلامِ **پاسخِ سرور**، جمعِ تازه و «افزوده شد»، و سبد خالی؛
 *   • پس از شروعِ جمع‌آوری ⇒ دکمه پنهان و «در حال آماده‌سازی» (S38)؛
 *   • پرچمِ `shop.orders.add_items` خاموش ⇒ هیچ‌کدام (همان صفحهٔ امروز)؛
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
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
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

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
const email = 'owner@add.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ افزودن' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.orders.add_items']) await flag(key, true);
const slug = 'add-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ افزودن', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [['rice', 'برنج', 300], ['salt', 'نمک', 20]].map(([id, name, salePrice]) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name, unit: 'دانه', salePrice, category: 'خوراکی', active: true },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const id of ['rice', 'salt']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);

//  مشتری: تأییدِ شماره و ثبتِ یک سفارش با API ِ واقعی؛ همان بلیت در مرورگر
const phone = '0791112233';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
const ck = await call('POST', `/api/store/${slug}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
}, null, { 'Idempotency-Key': `web-add-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
ok(ck.status === 201, 'سفارش ثبت شد', JSON.stringify(ck.body).slice(0, 120));
const token = ck.body.order.token;
const orderId = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, ck.body.order.orderNo])).id;

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript(([s, t]) => {
  if (sessionStorage.getItem('seeded')) return;
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
  localStorage.setItem(`store-cart:${s}`, JSON.stringify({
    salt: { qtyMilli: 2000, name: 'نمک', unit: 'دانه', priceMinor: 2000, maxQtyMilli: null, photo: false, unavailable: false },
  }));
}, [slug, { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt }]);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

let opened = false;
const open = async () => {
  if (opened) await page.reload(); else await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(token)}`);
  opened = true;
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
};

await open();
ok(await page.isVisible('#o-add-btn'), 'سفارشِ تازه ⇒ دکمهٔ «افزودنِ سبد به همین سفارش» پیدا');
await page.click('#o-add-btn');
await page.waitForSelector('dialog.confirm .btn-danger', { timeout: 5000 });
await page.click('dialog.confirm .btn-danger');
//  حالتِ واقعیِ نتیجه: قلمِ تازه در فهرستِ اقلامِ پاسخِ سرور و پیامِ «افزوده شد»
await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length === 2
  && /افزوده شد/.test(document.getElementById('o-add-note').textContent), null, { timeout: 10000 });
const lines = await page.$$eval('#o-lines li', (ls) => ls.map((l) => l.textContent));
ok(lines.some((l) => l.includes('نمک')), 'قلمِ «نمک» در فهرستِ اقلامِ سفارش', lines.join(' | '));
const row = await one('SELECT total_minor, items_count FROM online_orders WHERE id=$1', [orderId]);
ok(Number(row.items_count) === 2 && Number(row.total_minor) === 30000 + 4000, 'سرور: دو قلم و جمعِ تازه', JSON.stringify(row));
ok((await page.evaluate((s) => localStorage.getItem(`store-cart:${s}`), slug)) === '{}', 'سبدِ همین دستگاه خالی شد');

//  پس از شروعِ جمع‌آوری ⇒ دکمه پنهان و «در حال آماده‌سازی»
for (const to of ['accepted', 'picking']) await call('POST', `/api/shop/orders/${orderId}/transition`, { to }, T);
await open();
await page.waitForFunction(() => /آماده‌سازی/.test(document.getElementById('o-add-note').textContent), null, { timeout: 10000 });
ok(await page.isHidden('#o-add-btn'), 'جمع‌آوری شروع شد ⇒ دکمه پنهان، پیامِ «در حال آماده‌سازی»');

//  پرچمِ خاموش ⇒ همان صفحهٔ امروز
await flag('shop.orders.add_items', false);
await open();
ok(await page.$('#o-add') === null && await page.$('#o-apology') === null, 'پرچمِ خاموش ⇒ هیچ بخشِ «افزودن» در صفحه (همان صفحهٔ امروز)');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: افزودنِ سبد به همان سفارش پیش از جمع‌آوری');
process.exit(bad ? 1 : 0);
