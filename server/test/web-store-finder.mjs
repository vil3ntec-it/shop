/**
 * ویترین با کرومیومِ واقعی — فیلترِ کاتالوگ (WEB-07) و «خبرم کن» (WEB-10)، پشتِ پرچم‌های خاموشِ
 * `shop.storefront.filters` و `shop.storefront.alerts`:
 *
 *     TEST_DATABASE_URL=pglite:memory node test/web-store-finder.mjs
 *
 *   • فرمِ فیلتر فقط با گزینه‌های سرور؛ برند و قیمت فهرست را از سرور پالایش می‌کنند؛ قیمتِ نادرست ⇒ پیام، بی درخواست؛
 *   • برگهٔ کالا: بی شمارهٔ تأییدشده فقط راهنما؛ با بلیت «وقتی ارزان شد خبرم کن» ⇒ ردیف در سرور و دکمهٔ «لغو»؛
 *   • پرچم‌ها خاموش ⇒ نه فرم، نه filters.js؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
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
const email = 'owner@finder.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ فیلتر' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.filters', 'shop.storefront.alerts']) await flag(key, true);
const slug = 'web-finder';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ فیلتر', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const items = [['rice', 'برنج', 300, 'Alpha'], ['oil', 'روغن', 500, 'Beta'], ['tea', 'چای', 100, 'Alpha']];
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: items.map(([id, name, salePrice, brand]) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name, unit: 'دانه', salePrice, category: 'خوراکی', brand, active: true },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const [id] of items) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
let finderJs = 0;
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
page.on('request', (r) => { if (r.url().endsWith('/store/filters.js')) finderJs++; });
const shown = async () => (await page.locator('#products li[data-id]').evaluateAll((l) => l.map((x) => x.getAttribute('data-id')))).sort().join(',');
const open = async () => {
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#products li[data-id="rice"]', { timeout: 15000 });
};

//  WEB-07: فرمِ فیلتر
await open();
await page.waitForSelector('#filters:not([hidden])', { timeout: 10000 });
ok((await page.textContent('#filters summary')).includes('فیلتر'), 'فرمِ فیلتر با برچسبِ فارسی');
await page.click('#filters summary');
ok(JSON.stringify(await page.locator('#fl-brand option').allTextContents()) === JSON.stringify(['همه', 'Alpha', 'Beta']), 'برندها فقط از سرور');
ok(await page.locator('#fl-stock-l').isHidden(), 'بی موجودیِ معلوم «فقط موجود» نیست');
await page.selectOption('#fl-brand', 'Alpha');
await page.click('#fl-form button[type="submit"]');
await page.waitForFunction(() => !document.querySelector('#products li[data-id="oil"]') && document.querySelector('#products li[data-id="rice"]'), null, { timeout: 10000 });
ok(await shown() === 'rice,tea', 'برند ⇒ فقط همان برند', await shown());
await page.fill('#fl-min', '۲۰۰');
await page.click('#fl-form button[type="submit"]');
//  فهرست هنگامِ بارِ تازه لحظه‌ای خالی است ⇒ تا خودِ نتیجه («برنج» هست، «چای» نه) صبر، نه فقط نبودنِ «چای»
await page.waitForFunction(() => !document.querySelector('#products li[data-id="tea"]') && document.querySelector('#products li[data-id="rice"]'), null, { timeout: 10000 });
ok(await shown() === 'rice', 'برند + کمینهٔ قیمت (رقمِ فارسی) ⇒ فقط همان', await shown());
await page.fill('#fl-max', '50');
await page.click('#fl-form button[type="submit"]');
ok(await page.locator('#fl-msg').isVisible() && await shown() === 'rice', 'کمینه > بیشینه ⇒ پیام و همان فهرست');
await page.click('#fl-clear');
await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length === 3, null, { timeout: 10000 });
ok(true, 'پاک کردن ⇒ همهٔ کالاها');

//  WEB-10: برگهٔ کالا بی بلیت و با بلیت
await page.locator('#products li[data-id="rice"] .p-open').first().click();
await page.waitForSelector('#ps-alerts:not([hidden])', { timeout: 10000 });
ok(await page.locator('#ps-alerts button').count() === 0 && (await page.textContent('#ps-alerts')).includes('تأیید'), 'بی شمارهٔ تأییدشده فقط راهنما');
const st = await call('POST', `/api/store/${slug}/verify/start`, { phone: '0791260001' });
const ck = await call('POST', `/api/store/${slug}/verify/check`, { phone: '0791260001', code: st.body.devCode });
await page.evaluate((t) => localStorage.setItem('store-ticket', JSON.stringify({ phone: t.phone, ticket: t.ticket, expiresAt: t.expiresAt })), ck.body);
await open();
await page.locator('#products li[data-id="rice"] .p-open').first().click();
await page.waitForSelector('#ps-alerts:not([hidden]) button', { timeout: 15000 });
ok(await page.locator('#ps-alerts button').count() === 1, 'موجودیِ نامعلوم ⇒ فقط «وقتی ارزان شد»');
await page.click('#ps-alerts button');
await page.waitForSelector('#ps-alerts button[aria-pressed="true"]', { timeout: 10000 });
const row = await one("SELECT kind, state, base_price_minor FROM storefront_alerts WHERE shop_id = $1 AND product_id = 'rice'", [shopId]);
ok(row && row.kind === 'price' && row.state === 'armed' && Number(row.base_price_minor) === 30000, 'ثبت در سرور با مبنای قیمتِ کارت', JSON.stringify(row));
await page.click('#ps-alerts button');
await page.waitForSelector('#ps-alerts button[aria-pressed="false"]', { timeout: 10000 });
ok(!(await one("SELECT 1 AS x FROM storefront_alerts WHERE shop_id = $1", [shopId])), 'لغو ⇒ ردیف پاک');

//  پرچم‌ها خاموش ⇒ همان صفحهٔ پیشین
for (const key of ['shop.storefront.filters', 'shop.storefront.alerts']) await flag(key, false);
finderJs = 0;
await open();
await page.waitForTimeout(500);
ok(await page.locator('#filters').isHidden() && finderJs === 0, 'خاموش ⇒ نه فرم، نه filters.js');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: فیلترِ کاتالوگ و «خبرم کن»');
process.exit(bad ? 1 : 0);
