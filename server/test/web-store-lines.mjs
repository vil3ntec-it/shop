/**
 * ویترین با کرومیومِ واقعی: یادداشتِ هر قلم (CHK-16) و «اگر نبود…» (CHK-15) در برگهٔ ثبت و پیگیری —
 * `store/line-prefs.js` ِ دیربار؛ سرور: `order-substitution.js` و `parseCheckout` ِ `lib/storefront.js`.
 *
 *     npm run test:web-store      (پس از web-store-paystep.mjs)
 *
 * order-substitution.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • «+ یادداشت» زیرِ هر قلم (همیشه) ⇒ پس از بارِ دوباره همان یادداشت روی همین دستگاه؛
 *   • با `shop.orders.substitution`: گزینشِ کلی زیرِ «یادداشت برای دکان»، و گزینشِ قلم **فقط** برای قلمِ کم‌موجود
 *     (موجودیِ اعلام‌شده کمتر از دو برابرِ سبد)؛ «کالای مشخص» بی کالا ⇒ پیامِ روشن و هیچ سفارش؛ جست‌وجو ⇒ کالای همین ویترین؛
 *   • ثبت ⇒ ردیفِ سرور: یادداشت، «کالای مشخص» با نامِ دفتر، ترجیحِ کلی برای قلمِ دیگر؛ پیگیری همان را زیرِ قلم نشان می‌دهد؛
 *   • ممیزیِ ویترین: سبدِ گوشی «− ۱ +» ِ همیشه‌پیدا، جمع روی نوار و نوارِ پایینِ پنهان زیرِ سبدِ باز، و تغییرِ تعداد یادداشت را
 *     نگه می‌دارد؛ متنِ پرداخت فقط «نقد…»؛ خلاصهٔ پول: باز ⇒ «مانده»، ردشده ⇒ فقط جمع، گرفته‌شده با نقد ⇒ «پرداخت‌شده (نقد…)»؛
 *   • پرچمِ جایگزینی خاموش ⇒ نه گزینشِ کلی، نه گزینشِ قلم (یادداشت می‌ماند)؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
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
const { query, one, many, newId, now, closeDb } = require('../src/db');
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
const email = 'owner@lines.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ قلم‌ها' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.catalog', 'shop.orders.substitution']) await flag(key, true);
const slug = 'lines-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ قلم‌ها', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));

let n = 0;
const op = (table, row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++n}`, ts: Date.now(), table, row_id: row, type: 'insert', fields });
const entry = (id, units) => op('warehouseEntries', `${id}-e1`, { productId: id, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 100, createdAt: Date.now() });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('products', 'flour', { name: 'آرد', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true }),
  op('products', 'oil', { name: 'روغن', unit: 'دانه', salePrice: 250, category: 'خوراکی', active: true }),
  op('products', 'sugar', { name: 'شکرِ سفید', unit: 'دانه', salePrice: 200, category: 'خوراکی', active: true }),
  entry('flour', 5), entry('oil', 50), entry('sugar', 50),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا و ورودِ انبار از راهِ همگام‌سازیِ واقعی رسیدند');
for (const id of ['flour', 'oil', 'sugar']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);
const set = await call('PUT', '/api/shop/catalog/settings', { showQty: 'exact' }, T);
ok(set.status === 200, 'سیاستِ موجودی: نمایشِ عدد');
const ids = await call('GET', `/api/store/${slug}/products?ids=flour,oil`);
const sell = Object.fromEntries((ids.body.items || []).map((p) => [p.id, p.stock && p.stock.sellableMilli]));
ok(sell.flour === 5000 && sell.oil === 50000, 'موجودیِ اعلام‌شده: آرد ۵، روغن ۵۰', JSON.stringify(sell));

//  مشتری: شمارهٔ تأییدشده (همان بلیت در مرورگر)
const phone = '0791112233';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript(([s, t]) => {
  if (sessionStorage.getItem('seeded')) return;
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
  localStorage.setItem(`store-cart:${s}`, JSON.stringify({
    flour: { qtyMilli: 3000, name: 'آرد', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false },
    oil: { qtyMilli: 1000, name: 'روغن', unit: 'دانه', priceMinor: 25000, maxQtyMilli: null, photo: false, unavailable: false },
  }));
}, [slug, { email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt }]);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

const li = (name) => page.locator('#cart-lines li', { hasText: name });
let loads = 0;
const openCheckout = async () => {
  //  تنها عوض شدنِ hash صفحه را بار نمی‌کند ⇒ نشانیِ تازه (`?r=`) تا سبد و پاسخِ سرور (پرچم) از نو خوانده شوند
  await page.goto(`${base}/store/${slug}?r=${++loads}#/checkout`);
  await page.waitForFunction(() => document.querySelectorAll('#cart-lines li .lp').length === 2, null, { timeout: 15000 });
};

//  ── یادداشت (CHK-16): همیشه، و روی همین دستگاه می‌ماند ──
await openCheckout();
await li('روغن').getByRole('button', { name: '+ یادداشت' }).click();
await li('روغن').locator('input[type="text"]').fill('سرِ تازه');
await page.fill('#c-name', 'مشتری');   //  blur ⇒ «change» ⇒ ذخیرهٔ سبد
await page.fill('#c-phone', '0791230004');   //  «فقط ایمیل»: شمارهٔ تماسِ پیک جدا و بی کد
await page.reload();
await page.waitForFunction(() => document.querySelectorAll('#cart-lines li .lp').length === 2, null, { timeout: 15000 });
const kept = await li('روغن').locator('input[type="text"]').inputValue();
ok(kept === 'سرِ تازه', 'یادداشتِ قلم پس از بارِ دوباره همان‌جا', kept);

//  ── سبدِ گوشی (CHK-03/04، ممیزیِ ویترین): «− ۱ +» همیشه پیدا، جمع روی نوار، نوارِ پایین زیرِ سبدِ باز پنهان ──
await page.goto(`${base}/store/${slug}?r=${++loads}#/`);
await page.waitForSelector('#cart-bar:not([hidden])', { timeout: 15000 });
const barSum = await page.locator('#cart-bar .sum').isVisible() && await page.textContent('#bar-total');
ok(/۱٬۱۵۰|۱,۱۵۰|1,150/.test(barSum || ''), 'نوارِ سبدِ گوشی جمعِ پول را نشان می‌دهد', barSum);
await page.click('#cart-bar');
await page.waitForFunction(() => document.body.classList.contains('cart-open'), null, { timeout: 5000 });
const more = page.locator('#sheet-lines li', { hasText: 'روغن' }).getByRole('button', { name: /یکی بیشتر/ });
ok(await more.isVisible(), '«+» ِ قلم بی لمسِ سطر پیداست');
ok(!(await page.locator('.bnav').first().isVisible()), 'نوارِ پایینِ برنامه زیرِ سبدِ باز پنهان');
await more.click();
const q2 = await page.evaluate((s) => JSON.parse(localStorage.getItem(`store-cart:${s}`)).oil, slug);
ok(q2.qtyMilli === 2000 && q2.note === 'سرِ تازه', 'تغییرِ تعداد یادداشتِ قلم را نگه می‌دارد', JSON.stringify(q2));
await page.locator('#sheet-lines li', { hasText: 'روغن' }).getByRole('button', { name: /یکی کمتر/ }).click();
await openCheckout();

//  ── «اگر نبود…» (CHK-15): کلی همیشه با پرچم، قلم فقط برای کم‌موجود ──
await page.waitForSelector('#cart-lines li select', { timeout: 10000 });
ok(await page.isVisible('#ck-sub select'), 'گزینشِ کلی زیرِ «یادداشت برای دکان»');
const payText = await page.textContent('label.pay-opt');
ok(!/هنوز|درگاه/.test(payText) && /نقد/.test(payText), 'پرداخت: فقط «نقد…» بی «پرداختِ آنلاین هنوز فعال نیست» (CHK-13)', payText);
const flourSel = li('آرد').locator('select');
const oilSel = li('روغن').locator('select');
ok(await flourSel.count() === 1 && await flourSel.isVisible() && await oilSel.count() === 0,
  'گزینشِ قلم فقط برای آرد (۳ از ۵)، نه روغن (۱ از ۵۰)', `${await flourSel.count()} / ${await oilSel.count()}`);

await flourSel.selectOption('specific');
await page.fill('#c-name', 'مشتری');
await page.fill('#c-phone', '0791230004');
await page.check('input[name="fulfillment"][value="pickup"]');
await page.click('#submit-order');
await page.waitForFunction(() => /آرد/.test((document.getElementById('checkout-msg') || {}).textContent || ''), null, { timeout: 5000 });
const missMsg = await page.textContent('#checkout-msg');
const none = await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId]);
ok(/جایگزین/.test(missMsg) && none.n === 0, '«کالای مشخص» بی کالا ⇒ پیامِ روشن و هیچ سفارش', missMsg);

await li('آرد').locator('input[type="search"]').fill('شکر');
await li('آرد').getByRole('button', { name: /شکرِ سفید/ }).click();
await page.waitForFunction(() => /«شکرِ سفید»/.test(document.getElementById('cart-lines').textContent), null, { timeout: 5000 });
ok(true, 'جست‌وجو ⇒ «جایگزین: «شکرِ سفید»»');
await page.selectOption('#ck-sub select', 'refund');
await page.click('#submit-order');
await page.waitForFunction(() => /^#\/o\//.test(location.hash), null, { timeout: 15000 });

const order = await one('SELECT id, substitution FROM online_orders WHERE shop_id=$1', [shopId]);
const lines = await many('SELECT product_id, note, substitution, substitute_product_id, substitute_name FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [order.id]);
const by = Object.fromEntries(lines.map((l) => [l.product_id, l]));
ok(order.substitution === 'refund', 'سرور: ترجیحِ کلی «بازپرداخت»', order.substitution);
ok(by.flour && by.flour.substitution === 'specific' && by.flour.substitute_product_id === 'sugar' && by.flour.substitute_name === 'شکرِ سفید',
  'سرور: آرد ⇒ «کالای مشخص» با نامِ دفتر', JSON.stringify(by.flour));
ok(by.oil && by.oil.note === 'سرِ تازه' && by.oil.substitution === 'refund', 'سرور: یادداشتِ روغن، و ترجیحِ کلی برای قلمِ بی گزینش', JSON.stringify(by.oil));

//  ── پیگیری: همان زیرِ قلم ──
await page.waitForFunction(() => document.querySelectorAll('#o-lines .lp-r').length === 2 && !document.getElementById('o-sub').hidden, null, { timeout: 15000 });
const oLines = await page.textContent('#o-lines');
const oSub = await page.textContent('#o-sub');
ok(/یادداشت: سرِ تازه/.test(oLines) && /اگر نبود: جایگزین: «شکرِ سفید»/.test(oLines) && /بازپرداخت/.test(oSub),
  'پیگیری: یادداشت و «اگر نبود» زیرِ قلم، ترجیحِ کلی زیرِ فهرست', `${oLines} | ${oSub}`);

//  ── خلاصهٔ پول (CHK-20/ORD-15/PCK-07، ممیزیِ ویترین): باز ⇒ «مانده»، ردشده ⇒ فقط جمع، گرفته‌شده با نقد ⇒ «پرداخت‌شده (نقد…)» ──
const totals = () => page.textContent('#o-totals');
const t0 = await totals();
ok(/مانده/.test(t0) && !/پرداخت‌شده/.test(t0), 'سفارشِ باز: «مانده» و بی «پرداخت‌شده ۰»', t0);
const rej = await call('POST', `/api/shop/orders/${order.id}/transition`, { to: 'rejected', reason: 'out_of_stock' }, T);
ok(rej.status === 200, 'فروشنده رد کرد', JSON.stringify(rej.body).slice(0, 120));
await page.reload();
await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
const t1 = await totals();
ok(!/مانده|پرداخت‌شده/.test(t1) && /مبلغِ نهایی/.test(t1), 'ردشده: فقط جمع، نه «مانده» نه «پرداخت‌شده»', t1);
const ck2 = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone },
  lines: [{ productId: 'oil', qtyMilli: 1000 }] }, null, { 'Idempotency-Key': `lines-2-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
ok(ck2.status === 201, 'سفارشِ دوم (دریافت از دکان) ثبت شد', JSON.stringify(ck2.body).slice(0, 120));
const id2 = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, ck2.body.order.orderNo])).id;
for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
  const r = await call('POST', `/api/shop/orders/${id2}/transition`, { to }, T);
  if (r.status !== 200) ok(false, `گذار به ${to}`, JSON.stringify(r.body).slice(0, 160));
}
await page.goto(`${base}/store/${slug}?r=${++loads}#/o/${encodeURIComponent(ck2.body.order.token)}`);
await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
const t2 = await totals();
ok(/پرداخت‌شده \(نقد/.test(t2) && /۲۵۰/.test(t2) && !/مانده/.test(t2), 'گرفته‌شده با نقد: «پرداخت‌شده (نقد…)» = جمع، بی «مانده»', t2);

//  ── پرچمِ جایگزینی خاموش ⇒ فقط یادداشت ──
await flag('shop.orders.substitution', false);
await page.evaluate((s) => localStorage.setItem(`store-cart:${s}`, JSON.stringify({
  flour: { qtyMilli: 3000, name: 'آرد', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false },
  oil: { qtyMilli: 1000, name: 'روغن', unit: 'دانه', priceMinor: 25000, maxQtyMilli: null, photo: false, unavailable: false },
})), slug);
await openCheckout();
await page.waitForTimeout(500);
ok(await page.$('#ck-sub') === null && await page.locator('#cart-lines select').count() === 0
  && await li('آرد').getByRole('button', { name: '+ یادداشت' }).isVisible(), 'پرچمِ خاموش ⇒ نه گزینشِ کلی، نه گزینشِ قلم؛ «+ یادداشت» می‌ماند');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: یادداشت و «اگر نبود…» ِ هر قلم در ثبت و پیگیری');
process.exit(bad ? 1 : 0);
