/**
 * ویترین با کرومیومِ واقعی: سه رابطِ وبِ WEB که تا امروز فقط در سرور یا با منطقِ خالص سنجیده می‌شدند.
 *
 *     npm run test:web-store      (پس از web-store-zones.mjs)
 *
 *   • WEB-14: بی اینترنت ⇒ نوارِ «اینترنت وصل نیست…»؛ سبد روی همین دستگاه می‌ماند و برگشتِ شبکه نوار را برمی‌دارد؛
 *   • WEB-13: «دیده‌شده‌های اخیر» (پرچمِ `shop.storefront.recent`) پس از دیدنِ کالا و بارِ دوباره، فقط روی همین دستگاه؛
 *   • WEB-20: دکمهٔ «فرستادنِ نشانیِ کالا» (پرچمِ `shop.storefront.seo`) نشانیِ `/p/…` را کپی می‌کند؛
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
const email = 'owner@web.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ وب' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.recent', 'shop.storefront.seo']) {
  await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
}
const slug = 'web-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ وب', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: ['rice', 'oil'].map((id, i) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name: i ? 'روغن' : 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const id of ['rice', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF', permissions: ['clipboard-read', 'clipboard-write'] });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const open = async () => {
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#products li[data-id="rice"]', { timeout: 15000 });
};

//  WEB-14: بی اینترنت
await open();
ok(await page.locator('.offline').isHidden(), 'با اینترنت نوارِ آفلاین پنهان است');
await ctx.setOffline(true);
await page.waitForSelector('.offline:not([hidden])', { timeout: 5000 });
ok(((await page.textContent('.offline')) || '').includes('اینترنت وصل نیست'), 'WEB-14: بی اینترنت ⇒ «اینترنت وصل نیست؛ سبدِ شما روی همین دستگاه می‌ماند»');
await page.locator('#products li[data-id="oil"] .p-add').first().click();
await ctx.setOffline(false);
await page.waitForSelector('.offline', { state: 'hidden', timeout: 5000 });
ok(true, 'برگشتِ شبکه نوار را برمی‌دارد');
await open();
ok(/"?oil"?/.test(await page.evaluate((k) => localStorage.getItem(k) || '', `store-cart:${slug}`)), 'سبدی که بی اینترنت پر شد پس از بارِ دوباره همان است (`store-cart`)');

//  WEB-13: دیده‌شده‌های اخیر
await page.locator('#products li[data-id="rice"] .p-open').first().click();
await page.waitForSelector('#product-sheet:not([hidden])', { timeout: 10000 });
//  WEB-20: فرستادنِ نشانیِ کالا (بی navigator.share ⇒ کپی)
await page.waitForSelector('#ps-share:not([hidden])', { timeout: 10000 });
await page.click('#ps-share');
await page.waitForFunction(() => /کپی شد/.test((document.getElementById('toast') || {}).textContent || ''), null, { timeout: 5000 });
const copied = await page.evaluate(() => navigator.clipboard.readText());
ok(/\/p\/rice$/.test(copied), 'WEB-20: «فرستادن» نشانیِ `/p/<کالا>` را کپی کرد', copied);
await page.click('#ps-close');
await open();
await page.waitForSelector('#recent:not([hidden]) li[data-id="rice"]', { timeout: 10000 });
ok(true, 'WEB-13: «دیده‌شده‌های اخیر» پس از بارِ دوباره برنج را دارد');
const keys = await page.evaluate(() => Object.keys(localStorage));
ok(keys.some((k) => /recent/i.test(k)), 'فهرستِ اخیر فقط روی همین دستگاه (localStorage)', keys.join(','));

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: آفلاین، دیده‌شده‌های اخیر و فرستادنِ نشانی');
process.exit(bad ? 1 : 0);
