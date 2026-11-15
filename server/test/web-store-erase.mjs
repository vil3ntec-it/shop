/**
 * ویترین با کرومیومِ واقعی: «پاک کردنِ داده‌های من در این دکان» — SEC-10 (صفحه: `store/erase.js` ِ دیربار از `store/acct.js`؛
 * سرور: `lib/customer-erasure.js`، `POST /api/store/:slug/account/erase`؛ قرارداد: `customer-erasure.test.js`).
 *
 *     npm run test:web-store      (پس از web-store-acct.mjs)
 *
 *   • ⛔ پرچمِ `shop.customer_erasure` خاموش ⇒ erase.js خواسته نمی‌شود و «حسابِ من» همان امروز؛
 *   • روشن ⇒ «پاک کردنِ داده‌های من …» زیرِ «حسابِ من»؛ پیش‌نمایش با سفارشِ باز فقط پیام (بی دکمهٔ پاک کردن)، بی هیچ نوشتن؛
 *   • سفارش تمام شد ⇒ شمارِ هر دسته؛ دکمه تا «می‌دانم برگشت ندارد» بسته؛ بلیتِ شمارهٔ دیگر روی این دستگاه ⇒ کد برای **همان**
 *     شمارهٔ حساب، سپس پاک ⇒ نام و شماره از سفارش رفت، مبلغ ماند، حساب ماند، فهرستِ محلیِ سفارش‌ها پاک؛
 *   • راست‌چین، بی بیرون‌زدگی در ۳۶۰، بی نقضِ CSP، بی خطای جاوااسکریپت و (axe-core) بی خطای دسترس‌پذیریِ همان بخش.
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
const email = 'owner@erase.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ حذف' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.web_account']) await flag(key, true);
const slug = 'erase-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ حذف', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  { op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true } },
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

const phone = '0791116677';
const NAME = 'رحیمِ آزمونی';
const ticket = async (p) => {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(p) });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(p), code: vs.body.devCode });
  return { email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt };
};
const mine = await ticket(phone);
const acc = await call('POST', `/api/store/${slug}/account`, { name: NAME }, null, { 'X-Store-Ticket': mine.ticket });
ok(acc.status === 201, 'حسابِ وبِ مشتری ساخته شد', JSON.stringify(acc.body).slice(0, 120));
const placed = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: NAME, phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }] },
  null, { 'X-Store-Ticket': mine.ticket, 'Idempotency-Key': 'erase-web-order-1' });
ok(placed.status === 201, 'یک سفارش با همین شماره', JSON.stringify(placed.body).slice(0, 160));
const orderId = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, placed.body.order.orderNo])).id;
//  بلیتِ تازهٔ شمارهٔ **دیگر** روی این دستگاه (بازارچه/ویترینِ دیگر) — نباید جای تأییدِ شمارهٔ حساب را بگیرد
const other = await ticket('0792228899');

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const loads = [];
const session = { token: acc.body.session, expiresAt: acc.body.expiresAt, email: acc.body.customer.email, phone: acc.body.customer.phone, name: NAME };
const seed = (c) => c.addInitScript(([s, sess, tk, order]) => {
  if (localStorage.getItem('seeded')) return;
  localStorage.setItem('seeded', '1');
  localStorage.setItem('store-session', JSON.stringify(sess));
  localStorage.setItem('store-ticket', JSON.stringify(tk));
  localStorage.setItem(`store-orders:${s}`, JSON.stringify([order]));
}, [slug, session, other, { token: placed.body.order.token, orderNo: placed.body.order.orderNo, at: Date.now() }]);
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await seed(ctx);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
page.on('request', (r) => { if (/\/store\/erase\.js/.test(r.url())) loads.push(r.url()); });
const text = async (sel) => ((await page.textContent(sel)) || '').trim();
const openOrders = async () => {
  await page.goto(`${base}/store/${slug}?r=${Date.now()}#/orders`);
  await page.waitForSelector('#view-orders:not([hidden]) #ac-mine .btn', { timeout: 15000 });
};
const piiOf = () => one('SELECT customer_name, customer_phone, total_minor FROM online_orders WHERE id=$1', [orderId]);

//  ── ⛔ پرچمِ خاموش ⇒ همان «حسابِ من» ──
await openOrders();
await page.waitForLoadState('networkidle');
ok(!(await page.$('#er-box')), 'خاموش ⇒ بخشِ پاک کردن نیست');
ok(loads.length === 0, 'خاموش ⇒ erase.js خواسته نشد', loads.join(' '));

//  ── روشن: پیش‌نمایش با سفارشِ باز ──
await flag('shop.customer_erasure', true);
await openOrders();
await page.waitForSelector('#er-box', { timeout: 15000 });
ok(loads.length > 0, 'روشن ⇒ erase.js دیربار آمد');
ok(await page.isHidden('#er-check'), 'بسته تا کاربر باز کند (details)');
await page.click('#er-box > summary');
ok((await text('#er-box > summary')) === 'پاک کردنِ داده‌های من در این دکان', 'عنوانِ بخش از واژه‌نامه', await text('#er-box > summary'));
await page.click('#er-check');
await page.waitForFunction(() => !document.querySelector('#er-box .note').hidden, null, { timeout: 15000 });
ok((await text('#er-box .note')).includes('هنوز تمام نشده'), 'سفارشِ باز ⇒ فقط پیام', await text('#er-box .note'));
ok(!(await page.$('#er-go')), 'سفارشِ باز ⇒ دکمهٔ پاک کردن نیست');
let row = await piiOf();
ok(row.customer_name === NAME, 'پیش‌نمایش هیچ چیز ننوشت');

//  ── سفارش تمام شد ⇒ شمار و دکمهٔ بسته تا تأیید ──
const rej = await call('POST', `/api/shop/orders/${orderId}/transition`, { to: 'rejected', reason: 'busy' }, T);
ok(rej.status === 200, 'دکان سفارش را رد کرد (پایانی)', JSON.stringify(rej.body).slice(0, 120));
await page.click('#er-check');
await page.waitForSelector('#er-list li', { timeout: 15000 });
ok((await text('#er-list')).includes('سفارش‌ها') && (await text('#er-list')).includes('۱'), 'شمارِ سفارش‌ها: ۱', await text('#er-list'));
ok(await page.isDisabled('#er-go'), 'دکمه تا «می‌دانم برگشت ندارد» بسته است');
ok(await page.evaluate(() => getComputedStyle(document.getElementById('er-box')).direction) === 'rtl', 'راست‌چین');
ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'بی بیرون‌زدگی در ۳۶۰');

if (axeSrc) {
  //  CSP ِ سختِ ویترین اسکریپتِ درون‌خطی را نمی‌پذیرد ⇒ همان صفحه و همان حالت در زمینهٔ bypassCSP
  const ax = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  await seed(ax);
  const ap = await ax.newPage();
  await ap.goto(`${base}/store/${slug}#/orders`);
  await ap.waitForSelector('#er-box', { timeout: 15000 });
  await ap.click('#er-box > summary');
  await ap.click('#er-check');
  await ap.waitForSelector('#er-list li', { timeout: 15000 });
  await ap.addScriptTag({ content: axeSrc });
  const v = await ap.evaluate(async () => (await window.axe.run('#er-box',
    { runOnly: ['color-contrast', 'button-name', 'label', 'list', 'listitem', 'aria-allowed-attr', 'nested-interactive'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  await ax.close();
  ok(v.length === 0, 'دسترس‌پذیری (axe-core) ِ بخشِ پاک کردن', v.join(' | '));
} else if (SKIP) {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

//  ── پاک کردن: بلیتِ شمارهٔ دیگر ⇒ کد برای همان شمارهٔ حساب ──
await page.check('#er-sure');
ok(!(await page.isDisabled('#er-go')), 'تأیید ⇒ دکمه باز');
await page.click('#er-go');
await page.waitForSelector('#er-verify', { timeout: 15000 });
ok((await text('#er-verify')).includes(mailOf(phone)), 'کد برای ایمیلِ همین حساب («فقط ایمیل»)', await text('#er-verify'));
row = await piiOf();
ok(row.customer_name === NAME, 'بلیتِ شمارهٔ دیگر چیزی پاک نکرد');
await page.click('#er-send');
await page.waitForSelector('#er-code:visible', { timeout: 15000 });
const dev = ((await text('#er-box .note')).match(/\d{6}/) || [])[0];
ok(Boolean(dev), 'کدِ آزمایشی رسید', await text('#er-box .note'));
await page.fill('#er-code', dev || '');
await page.click('#er-ok');
await page.waitForFunction(() => /پاک شد/.test((document.querySelector('#er-box .note') || {}).textContent || ''), null, { timeout: 15000 });
ok(true, 'پیامِ «داده‌های شما در این دکان پاک شد»');
row = await piiOf();
ok(row.customer_name === '' && String(row.customer_phone).startsWith('erased:'), 'نام و شمارهٔ سفارش رفت', JSON.stringify(row));
ok(Number(row.total_minor) === 30000, 'مبلغِ سفارش همان ماند');
ok((await one('SELECT COUNT(*)::int AS n FROM store_customers WHERE id=$1', [acc.body.customer.id])).n === 1, 'حساب ماند');
ok(await page.evaluate((s) => localStorage.getItem(`store-orders:${s}`) === null, slug), 'فهرستِ محلیِ سفارش‌های همین دکان پاک شد');
ok(await page.evaluate(() => JSON.parse(localStorage.getItem('store-ticket') || '{}').phone) === mine.phone, 'بلیتِ تازه برای شمارهٔ حساب');
ok(!(await page.$('#er-go')) && !(await page.$('#er-check')), 'پس از پاک کردن دکمه‌ای نمی‌ماند');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «پاک کردنِ داده‌های من در این دکان»');
process.exit(bad ? 1 : 0);
