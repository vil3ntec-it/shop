/**
 * ویترین با کرومیومِ واقعی: سه ادعای خانهٔ «تأیید» که فقط سمتِ سرور آزموده شده بودند، این‌بار از خودِ صفحه:
 *
 *   • CHK-23: «پیش از رسیدن زنگ بزنید» و «تحویل به…» ِ فرمِ ثبت (`#d-call`، `#d-leave`) همان سفارشِ سرور می‌شوند.
 *   • CHK-19: پاسخِ ثبت گم شد (سرور ساخت، شبکه برید) ⇒ «ثبت» ِ دوباره همان کلیدِ یکتا را می‌فرستد ⇒ همان سفارش، نه دومی؛
 *     صفحه پیگیریِ همان سفارش را باز می‌کند.
 *   • WEB-25: همهٔ دستگاه‌های دکان ۲۰ دقیقه بی تماس ⇒ سرِ ویترین و برگهٔ ثبت «ممکن است دیرتر بررسی شود» و ثبت ممکن؛
 *     «توقف هنگامِ قطع» ِ میزِ سفارش روشن ⇒ «فعلاً سفارشِ تازه گرفته نمی‌شود» (پیش‌تر خالی) و ثبتِ فوری ⇒ پیامِ روشن، بی سفارش.
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت.
 *
 *     TEST_DATABASE_URL=pglite:memory node test/web-store-checkout-rows.mjs      (در `npm run test:web-store`)
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

//  ── دکانِ آزمایشی (دادهٔ ساختگی) ──
const email = 'owner@rows.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ ردیف‌ها' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront']) await flag(key, true);
const slug = 'rows-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ ردیف‌ها', contactPhone: '0700000001',
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

const phone = '0791115151';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
const count = async () => Number((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n);
const jsonOf = (v) => (typeof v === 'string' ? JSON.parse(v) : v) || {};

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript(([t]) => {
  if (localStorage.getItem('seeded')) return;
  localStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
}, [{ email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt }]);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

let loads = 0;
const CART = { rice: { qtyMilli: 1000, name: 'برنج', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false } };
/** سبدِ یک برنج و برگهٔ ثبت؛ fulfillment = delivery | pickup */
async function toCheckout(fulfillment) {
  await page.goto(`${base}/store/${slug}?r=${++loads}`);
  await page.evaluate(([s, c]) => localStorage.setItem(`store-cart:${s}`, JSON.stringify(c)), [slug, CART]);
  await page.goto(`${base}/store/${slug}?r=${++loads}#/checkout`);
  await page.waitForSelector('#view-checkout:not([hidden]) #submit-order', { timeout: 15000 });
  await page.fill('#c-name', 'مشتری');
  await page.fill('#c-phone', '0791230001');   //  «فقط ایمیل»: شمارهٔ تماسِ پیک جدا و بی کد
  await page.check(`input[name="fulfillment"][value="${fulfillment}"]`);
}
const trackedToken = () => page.evaluate(() => (/^#\/o\//.test(location.hash) ? decodeURIComponent(location.hash.slice(4)) : ''));

//  ── CHK-23: «زنگ بزنید» و «تحویل به…» از فرمِ ثبت ⇒ سفارشِ سرور ──
await toCheckout('delivery');
await page.fill('#a-text', 'کارته‌سه، کوچهٔ دوم');
await page.click('#addr-more > summary');   //  «جزئیاتِ بیشتر (اختیاری)»
ok(await page.isVisible('#d-call') && await page.isVisible('#d-leave'), 'CHK-23: دو گزینهٔ تحویل در «جزئیاتِ بیشتر» ِ فرمِ «ارسال» دیده می‌شوند');
await page.check('#d-call');
await page.fill('#d-leave', 'دربان');
await page.click('#submit-order');
await page.waitForFunction(() => /^#\/o\//.test(location.hash), null, { timeout: 15000 });
const o1 = await one('SELECT id, delivery, fulfillment FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [shopId]);
const dj = jsonOf(o1.delivery);
ok(o1.fulfillment === 'delivery' && dj.callBeforeArrival === true && dj.leaveWith === 'دربان', 'CHK-23: سفارشِ سرور همان «زنگ بزنید» و «تحویل به دربان» را دارد', JSON.stringify(dj));
const card = await call('GET', `/api/shop/orders/${o1.id}`, null, T);
ok(card.body?.order?.delivery?.callBeforeArrival === true && card.body.order.delivery.leaveWith === 'دربان', 'CHK-23: کارتِ سفارشِ دکان (API ِ برنامه) همان دو گزینه را می‌بیند',
  JSON.stringify(card.body?.order?.delivery || card.body).slice(0, 160));

//  ── CHK-19: پاسخِ گم‌شده ⇒ «ثبت» ِ دوباره همان کلید ⇒ همان سفارش ──
const before = await count();
await toCheckout('pickup');
const keys = [];
let cut = true;
await page.route(`**/api/store/${slug}/checkout`, async (route) => {
  keys.push(route.request().headers()['idempotency-key'] || '');
  if (!cut) return route.continue();
  cut = false;
  await route.fetch();   //  سرور سفارش را ساخت…
  await route.abort('connectionreset');   //  …ولی پاسخ به مرورگر نرسید
});
await page.click('#submit-order');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled && !document.getElementById('checkout-msg').hidden
  && !/فرستاده|در حالِ/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 15000 });
ok(await count() === before + 1, 'CHK-19: درخواستِ نخست در سرور سفارش ساخت (پاسخ گم شد)');
ok(!(await trackedToken()), 'صفحه هنوز روی برگهٔ ثبت است (پاسخی نرسید)', await page.textContent('#checkout-msg'));
await page.click('#submit-order');
await page.waitForFunction(() => /^#\/o\//.test(location.hash), null, { timeout: 15000 });
ok(keys.length === 2 && keys[0] && keys[0] === keys[1], 'CHK-19: «ثبت» ِ دوباره همان کلیدِ یکتا را فرستاد', JSON.stringify(keys));
ok(await count() === before + 1, 'CHK-19: هیچ سفارشِ دومی ساخته نشد');
const last = await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [shopId]);
ok(await trackedToken() === require('../src/lib/storefront').tokenOf(shopId, last.id), 'CHK-19: صفحه پیگیریِ همان سفارشِ نخست را باز کرد');
await page.unroute(`**/api/store/${slug}/checkout`);

//  ── WEB-25: دستگاه‌های دکان ۲۰ دقیقه بی تماس ⇒ پیامِ تأخیر، ثبت ممکن ──
await seen(20 * 60 * 1000);
await page.goto(`${base}/store/${slug}?r=${++loads}`);
await page.waitForFunction(() => (document.querySelector('#store-status .accept')?.textContent || '').length > 0, null, { timeout: 15000 });
ok(/دیرتر بررسی/.test(await page.textContent('#store-status .accept')), 'WEB-25: سرِ ویترین «ممکن است دیرتر بررسی شود»', await page.textContent('#store-status'));
const n2 = await count();
await toCheckout('pickup');
ok(/دیرتر بررسی/.test(await page.textContent('#accept-line')), 'WEB-25: برگهٔ ثبت هم همان پیامِ تأخیر', await page.textContent('#accept-line'));
await page.click('#submit-order');
await page.waitForFunction(() => /^#\/o\//.test(location.hash), null, { timeout: 15000 });
ok(await count() === n2 + 1, 'WEB-25: پیش‌فرض ⇒ سفارش با پیامِ تأخیر ثبت می‌شود');

//  ── WEB-25 (S30): «توقف هنگامِ قطع» ِ میزِ سفارش ⇒ سفارشِ فوری نه، با پیامِ روشن ──
await flag('shop.orders.desk', true);
const desk = await call('PUT', '/api/shop/orders/desk/settings', { eventId: `ev-${Date.now()}-rows-desk`, offlinePause: { enabled: true } }, T);
ok(desk.status === 200, 'میزِ سفارش: «توقف هنگامِ قطع» روشن', JSON.stringify(desk.body).slice(0, 160));
await page.goto(`${base}/store/${slug}?r=${++loads}`);
await page.waitForSelector('#store-status .accept', { state: 'attached', timeout: 15000 });
await page.waitForFunction(() => (document.querySelector('#store-status')?.textContent || '').length > 0, null, { timeout: 15000 });
ok(/سفارشِ تازه گرفته نمی‌شود/.test(await page.textContent('#store-status .accept')), 'WEB-25: سرِ ویترین «فعلاً سفارشِ تازه گرفته نمی‌شود» (پیش‌تر خالی)',
  JSON.stringify(await page.textContent('#store-status')));
const n3 = await count();
await toCheckout('pickup');
await page.click('#submit-order');
await page.waitForFunction(() => /وصل نیست|سفارشِ فوری/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 15000 });
ok(await count() === n3, 'WEB-25: ثبتِ فوری ⇒ ۴۰۹ store_offline با پیامِ روشن و هیچ سفارشی', await page.textContent('#checkout-msg'));
ok(!(await trackedToken()), 'صفحه روی برگهٔ ثبت ماند');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: CHK-23، CHK-19 و WEB-25 از خودِ صفحه');
process.exit(bad ? 1 : 0);
