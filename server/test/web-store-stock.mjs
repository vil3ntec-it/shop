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
 *   • WEB-09: «کالای ناموجود را پنهان کن» ِ دکان ⇒ کارتش نیست، کالای موجود هست، پیوندِ مستقیمش «ناموجود» با دکمهٔ
 *     بسته؛ خاموش ⇒ دوباره دیده می‌شود (پالایه فقط در سرور، بی JS ِ تازه)؛
 *   • O-06/R6: نگه‌داشته «ناموجود» می‌کند؛ پرچمِ `shop.stock_reserve` خاموش ⇒ همان کارت/برگهٔ قابلِ سفارش، روشنِ دوباره ⇒ «ناموجود»؛
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

//  WEB-09: «کالای ناموجود را پنهان کن» ِ دکان (مهاجرتِ ۲۴۰) — سرور پالایش می‌کند، صفحه همان را می‌کشد (بی JS ِ تازه)
const hid = await call('PUT', '/api/shop/storefront', { hideOutOfStock: true }, T);
ok(hid.status === 200 && hid.body.settings.hideOutOfStock === true, 'WEB-09: دکان «کالای ناموجود را پنهان کن» را روشن کرد');
({ card, text } = await look());
ok(await page.locator('#products li[data-id="oil"]').count() === 0, 'WEB-09 روشن: کارتِ کالای ناموجود در ویترین نیست');
ok(text.includes('در انبار، آماده‌سازی ~۴۵ دقیقه'), 'WEB-09 روشن: کالای موجود همان‌جاست');
await page.goto(`${base}/store/${slug}#/p/oil`);   //  پیوندِ کالا (`/p/<id>` با سئو هم به همین `#/p/` می‌رسد)
await page.waitForSelector('#product-sheet:not([hidden]) #ps-stock .pill', { timeout: 10000 });
ok((await page.textContent('#ps-stock')).includes('ناموجود') && await page.isDisabled('#ps-add'),
  'WEB-09 روشن: پیوندِ مستقیمِ کالای پنهان همان «ناموجود» با دکمهٔ بسته (پیوند نمی‌شکند)');
const shown = await call('PUT', '/api/shop/storefront', { hideOutOfStock: false }, T);
ok(shown.status === 200 && shown.body.settings.hideOutOfStock === false, 'WEB-09: دوباره «نشان بده»');
await look();
ok(await page.locator('#products li[data-id="oil"]').count() === 1, 'WEB-09 خاموش: کالای ناموجود دوباره با «ناموجود» دیده می‌شود');

//  O-06 = ب (ORD-20، CHK-18): نگه‌داشتنِ کوتاهِ موجودی پشتِ پرچمِ خاموشِ `shop.stock_reserve` — مشتریِ ویترین آخرین روغن را در
//  سبد دارد، سفارشِ تلفنیِ دکان همان را نگه می‌دارد ⇒ «ثبت» ِ مشتری پیامِ روشنِ «نگه داشته شده» می‌گیرد و کارت «ناموجود» با
//  دکمهٔ بسته است؛ دکان سفارشِ تلفنی را رد می‌کند ⇒ روغن دوباره قابلِ سفارش
await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key: 'shop.stock_reserve', value: true }, A);
const oilIn = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('warehouseEntries', 'oil-e1', { productId: 'oil', units: 1, cartons: 0, perCarton: 0, unit: 'دانه', price: 200, createdAt: Date.now() }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
//  ⛔ ویترین دفترِ دستگاه را برای هر دکان حداکثر هر `CATALOG_REFRESH_THROTTLE_MS` (۵۰۰ms) یک بار می‌پرسد (`catalog.refresh`)؛
//  push ِ درونِ پنجرهٔ پرسشِ `look()` ِ پیشین را بارِ بعدیِ ویترین هنوز نمی‌بیند و صفحه دوباره نمی‌پرسد ⇒ «ناموجود» تا ۱۰ ثانیه.
//  پس اول خودِ رسیدن در دفترِ سرور (خواندنِ کارمند بی مهلت می‌خواند و تا کامیت صبر می‌کند)، سپس ویترین. بازتولید: پاسخِ
//  `PUT /api/shop/storefront` ۶۰۰ms دیر، یا `CATALOG_REFRESH_THROTTLE_MS=5000` ⇒ کدِ پیشین هر بار همین‌جا سرخ
const oilNow = await call('GET', '/api/shop/catalog/products/oil', null, T);
const oilLv = oilNow.body && oilNow.body.product ? oilNow.body.product.stock : null;
ok(oilIn.status === 200 && oilNow.status === 200 && oilLv && Number(oilLv.levels.shop_floor) === 1000 && Number(oilLv.sellableMilli) === 1000
  && oilLv.badge.status === 'in_stock', 'O-06: یک روغن به قفسه رسید (در دفترِ سرور)', JSON.stringify(oilLv));
await look();
const oilCard = () => page.locator('#products li[data-id="oil"]');
await page.waitForFunction(() => {
  const b = document.querySelector('#products li[data-id="oil"] .p-add');
  return b && !b.disabled;
}, null, { timeout: 10000 });
await oilCard().locator('.p-add').click();
ok(await page.isVisible('#cart-bar'), 'O-06: آخرین روغن در سبدِ مشتری');
const phone = await call('POST', '/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ تلفنی' },
  lines: [{ productId: 'oil', name: 'روغن', unit: 'دانه', qtyMilli: 1000, unitPriceMinor: 25000 }],
}, T, { 'Idempotency-Key': `web-stock-hold-${Date.now()}` });
ok(phone.status === 201 && phone.body.order.stockHold && phone.body.order.stockHold.status === 'held',
  'O-06: سفارشِ تلفنیِ دکان آخرین روغن را نگه داشت', JSON.stringify(phone.body).slice(0, 160));
await page.click('#open-cart');
await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'));
await page.click('#cart-next');
await page.waitForSelector('#view-checkout:not([hidden])');
await page.check('input[name="fulfillment"][value="pickup"]');
await page.fill('#c-name', 'مشتریِ ویترین');
await page.fill('#c-phone', '0791112244');
await page.click('#verify-send');
await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10000 });
await page.fill('#v-code', ((await page.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1] || '');
await page.click('#verify-check');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.click('#submit-order');
await page.waitForFunction(() => /نگه داشته شده/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 10000 })
  .catch(() => {});
ok(/برای سفارشِ دیگری نگه داشته شده/.test(await page.textContent('#checkout-msg')),
  'O-06: «ثبت» ِ مشتری ⇒ پیامِ روشنِ «برای سفارشِ دیگری نگه داشته شده»', await page.textContent('#checkout-msg'));
ok(await page.isVisible('#view-checkout'), 'O-06: سفارشِ دوم ثبت نشد (همان صفحهٔ ثبت)');
const orders = await call('GET', '/api/shop/orders?tab=new', null, T);
ok(orders.status === 200 && orders.body.items.length === 1, 'O-06: فقط سفارشِ تلفنی در صف', String(orders.body && orders.body.items && orders.body.items.length));
await look();
ok(((await oilCard().textContent()) || '').includes('ناموجود') && await oilCard().locator('.p-add').isDisabled(),
  'O-06: کارتِ روغنِ نگه‌داشته «ناموجود» و دکمه‌اش بسته');
const rej = await call('POST', `/api/shop/orders/${phone.body.order.id}/transition`, { to: 'rejected', reason: 'busy' }, T);
ok(rej.status === 200, 'O-06: دکان سفارشِ تلفنی را رد کرد');
await look();
ok(!((await oilCard().textContent()) || '').includes('ناموجود') && await oilCard().locator('.p-add').isEnabled(),
  'O-06: رد ⇒ روغن آزاد و دوباره قابلِ سفارش');

//  ⛔ R6: خاموش کردنِ `shop.stock_reserve` با نگه‌داشتهٔ باز ⇒ کارت همان main (قابلِ سفارش)؛ روشنِ دوباره ⇒ نگه‌داشتهٔ نگذشته دوباره «ناموجود»
const phone2 = await call('POST', '/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ تلفنی' },
  lines: [{ productId: 'oil', name: 'روغن', unit: 'دانه', qtyMilli: 1000, unitPriceMinor: 25000 }],
}, T, { 'Idempotency-Key': `web-stock-hold2-${Date.now()}` });
ok(phone2.status === 201 && phone2.body.order.stockHold && phone2.body.order.stockHold.status === 'held', 'R6: سفارشِ تلفنیِ دوم آخرین روغن را نگه داشت');
const flag = (value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key: 'shop.stock_reserve', value }, A);
ok((await flag(false)).status === 200, 'R6: نگه‌داشتن خاموش شد (نگه‌داشته باز ماند)');
await look();
ok(!((await oilCard().textContent()) || '').includes('ناموجود') && await oilCard().locator('.p-add').isEnabled(),
  'R6 خاموش: کارتِ روغن قابلِ سفارش (نگه‌داشتهٔ باز نادیده)', (await oilCard().textContent()) || '');
await oilCard().locator('.p-open').click();
await page.waitForSelector('#product-sheet:not([hidden]) #ps-stock .pill', { timeout: 10000 });
ok(!(await page.textContent('#ps-stock')).includes('ناموجود') && await page.isEnabled('#ps-add'), 'R6 خاموش: برگهٔ کالا هم قابلِ سفارش');
ok((await flag(true)).status === 200, 'R6: نگه‌داشتن دوباره روشن شد');
await look();
ok(((await oilCard().textContent()) || '').includes('ناموجود') && await oilCard().locator('.p-add').isDisabled(),
  'R6 روشن: نگه‌داشتهٔ نگذشته دوباره «ناموجود»');
ok((await call('POST', `/api/shop/orders/${phone2.body.order.id}/transition`, { to: 'rejected', reason: 'busy' }, T)).status === 200, 'R6: سفارشِ دوم رد شد');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: موجودیِ کاتالوگ روی کارت و برگهٔ کالا');
process.exit(bad ? 1 : 0);
