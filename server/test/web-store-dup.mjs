/**
 * ویترین با کرومیومِ واقعی: S41 «ادغام یا سفارشِ دوم؟» — همان شماره، همان اقلام و همان جا در پنجرهٔ تکرار
 * (پرچمِ `shop.order_risk`؛ سرور: `lib/order-risk.js`).
 *
 *     npm run test:web-store      (پس از web-store-same.mjs)
 *
 * order-risk.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • ثبتِ دوبارهٔ همان سبد ⇒ پرسش با شمارهٔ سفارشِ پیشین و دو دکمه؛ فوکوس روی گزینهٔ بی‌خطر («همان را دنبال کن»)؛
 *   • «همان را دنبال کن» ⇒ پیگیریِ همان سفارش و هیچ سفارشِ تازه‌ای در سرور؛
 *   • «سفارشِ دوم ثبت شود» ⇒ سفارشِ دومِ جدا (شماره و توکنِ تازه) در سرور؛
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
const email = 'owner@dup.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ تکرار' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.order_risk']) await flag(key, true);
const slug = 'dup-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ تکرار', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
  op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
  fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
}] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);

//  مشتری: تأییدِ شماره و نخستین سفارشِ «دریافت» با API ِ واقعی؛ همان بلیت در مرورگر
const phone = '0791114141';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
const ck = await call('POST', `/api/store/${slug}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
}, null, { 'Idempotency-Key': `web-dup-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
ok(ck.status === 201, 'نخستین سفارش ثبت شد', JSON.stringify(ck.body).slice(0, 120));
const first = ck.body.order;
const count = async () => Number((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript(([s, t]) => {
  if (localStorage.getItem('seeded')) return;
  localStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
  localStorage.setItem(`store-cart:${s}`, JSON.stringify({
    rice: { qtyMilli: 1000, name: 'برنج', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false },
  }));
}, [slug, { email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt }]);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

let loads = 0;
const submitSame = async () => {
  await page.goto(`${base}/store/${slug}?r=${++loads}#/checkout`);
  await page.waitForSelector('#view-checkout:not([hidden]) #submit-order', { timeout: 15000 });
  await page.fill('#c-name', 'مشتری');
  await page.fill('#c-phone', '0791230002');   //  «فقط ایمیل»: شمارهٔ تماسِ پیک جدا و بی کد
  await page.check('input[name="fulfillment"][value="pickup"]');
  await page.click('#submit-order');
  await page.waitForSelector('dialog.confirm .btn-danger', { timeout: 10000 });
};

//  ── ۱) پرسش و «همان را دنبال کن» ──
await submitSame();
const q = await page.textContent('dialog.confirm p');
const second = await page.textContent('dialog.confirm .btn-danger');
const track = await page.textContent('dialog.confirm .btn-ghost');
const fa = (n) => String(n).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[Number(d)]);
ok(q.includes(fa(first.orderNo)) && /دوم/.test(q), 'پرسش با شمارهٔ سفارشِ پیشین: «همان یا سفارشِ دوم؟»', q);
ok(/دوم/.test(second) && /دنبال/.test(track), 'دو دکمهٔ روشن: «سفارشِ دوم ثبت شود» و «همان را دنبال کن»', `${second} | ${track}`);
ok(await page.evaluate(() => document.activeElement && document.activeElement.classList.contains('btn-ghost')),
  'فوکوس روی گزینهٔ بی‌خطر («همان را دنبال کن»)');
await page.click('dialog.confirm .btn-ghost');
await page.waitForFunction(() => /^#\/o\//.test(location.hash), null, { timeout: 10000 });
const hash1 = await page.evaluate(() => decodeURIComponent(location.hash.slice(4)));
ok(hash1 === first.token, '«همان را دنبال کن» ⇒ پیگیریِ همان سفارش', hash1.slice(0, 16));
ok(await count() === 1, 'هیچ سفارشِ تازه‌ای در سرور ساخته نشد');

//  ── ۲) «سفارشِ دوم ثبت شود» ──
await submitSame();
await page.click('dialog.confirm .btn-danger');
await page.waitForFunction((t) => /^#\/o\//.test(location.hash) && decodeURIComponent(location.hash.slice(4)) !== t, first.token, { timeout: 15000 });
await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
const rows = await query('SELECT order_no FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [shopId]);
ok(rows.rows.length === 2 && Number(rows.rows[1].order_no) !== Number(first.orderNo), 'سفارشِ دومِ جدا در سرور', JSON.stringify(rows.rows));

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «ادغام یا سفارشِ دوم؟» (S41)');
process.exit(bad ? 1 : 0);
