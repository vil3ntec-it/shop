/**
 * ویترین با کرومیومِ واقعی: موجودیِ کاتالوگ روی کارت و برگهٔ کالا — v3 §۳۲.۴ INV-02@32.
 *
 *     npm run test:web-store      (پس از web-store.mjs)
 *
 * catalog-storefront.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه:
 * دکانِ واقعی (ثبت‌نام، همگام‌سازیِ کالا و ورودِ انبار، انتشار، جابه‌جاییِ قفسه ⇒ انبار)
 * با پرچمِ `shop.catalog` روشن، و صفحهٔ ویترین در عرضِ گوشی (۳۶۰):
 *   • قفسه صفر و انبار ۵ ⇒ کارت «در انبار، آماده‌سازی ~۴۵ دقیقه» و دکمهٔ «+» باز؛
 *     هیچ «ناموجود»؛ یادداشتِ «در حال بررسی» پنهان (وضعیت دانسته است)؛
 *   • برگهٔ کالا همان متن و دکمهٔ افزودنِ باز؛ با `showQty = hidden` وضعیت همان؛
 *   • کالای بی موجودی در همان صفحه «ناموجود» و دکمه‌اش بسته — مرزِ مقابل؛
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت.
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
const email = 'owner@stock.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ انبار' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
for (const key of ['shop.orders', 'shop.storefront', 'shop.catalog']) {
  await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
}
const slug = 'stock-store';
const put = await call('PUT', '/api/shop/storefront', {
  slug, enabled: true, title: 'ویترینِ انبار', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0,
}, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));

let n = 0;
const op = (table, row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++n}`, ts: Date.now(), table, row_id: row, type: 'insert', fields });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('products', 'flour', { name: 'آرد', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true }),
  op('products', 'oil', { name: 'روغن', unit: 'دانه', salePrice: 250, category: 'خوراکی', active: true }),
  op('warehouseEntries', 'flour-e1', { productId: 'flour', units: 5, cartons: 0, perCarton: 0, unit: 'دانه', price: 200, createdAt: Date.now() }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا و ورودِ انبار از راهِ همگام‌سازیِ واقعی رسیدند');
for (const id of ['flour', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);
const moved = await call('POST', '/api/shop/catalog/moves', { kind: 'transfer', productId: 'flour', from: 'shop_floor', to: 'warehouse', qtyMilli: 5000 },
  T, { 'Idempotency-Key': `web-stock-${Date.now()}` });
ok(moved.status === 201, 'هر ۵ آرد از قفسه به انبار رفت', JSON.stringify(moved.body).slice(0, 120));
const set = await call('PUT', '/api/shop/catalog/settings', { prepMinutes: 45, showQty: 'exact' }, T);
ok(set.status === 200, 'سیاست: آماده‌سازی ۴۵ دقیقه');

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

async function look() {
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#products li[data-id="flour"]', { timeout: 15000 });
  const card = page.locator('#products li[data-id="flour"]');
  return { card, text: (await card.textContent()) || '' };
}

let { card, text } = await look();
ok(text.includes('در انبار، آماده‌سازی ~۴۵ دقیقه'), 'قفسه صفر و انبار ۵ ⇒ کارت «در انبار، آماده‌سازی ~۴۵ دقیقه»', text.slice(0, 120));
ok(!text.includes('ناموجود'), 'کارتِ آرد «ناموجود» نمی‌گوید');
ok(await card.locator('.p-add').isEnabled(), 'دکمهٔ «+» ِ آرد باز است (قابلِ سفارش)');
ok(await page.isHidden('#stock-note'), 'وضعیت دانسته است ⇒ یادداشتِ «در حال بررسی» پنهان');
const oil = page.locator('#products li[data-id="oil"]');
ok(((await oil.textContent()) || '').includes('ناموجود') && await oil.locator('.p-add').isDisabled(),
  'مرزِ مقابل: کالای بی موجودی «ناموجود» و دکمه‌اش بسته');

await card.locator('.p-open').click();
await page.waitForSelector('#product-sheet:not([hidden]) #ps-stock .pill', { timeout: 10000 });
ok((await page.textContent('#ps-stock')).includes('در انبار، آماده‌سازی ~۴۵ دقیقه'), 'برگهٔ کالا همان وضعیت');
ok(await page.isEnabled('#ps-add'), 'برگهٔ کالا: دکمهٔ افزودن باز');

//  عددِ انبار پنهان ⇒ وضعیت همان؛ هیچ «تمام شده» ای ساخته نمی‌شود
await call('PUT', '/api/shop/catalog/settings', { showQty: 'hidden' }, T);
({ card, text } = await look());
ok(text.includes('در انبار، آماده‌سازی ~۴۵ دقیقه') && !text.includes('ناموجود'), 'showQty = hidden ⇒ همان «در انبار»');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: موجودیِ کاتالوگ روی کارت و برگهٔ کالا');
process.exit(bad ? 1 : 0);
