/**
 * ویترین با کرومیومِ واقعی: روش و مسئولِ تحویل (S71) در برگهٔ ثبت و پیگیری، و «تغییرِ روشِ تحویل» (S72) —
 * سرور: `order-delivery-method.js`.
 *
 *     npm run test:web-store      (پس از web-store-add.mjs)
 *
 * order-delivery-method.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • برگهٔ ثبت: زیرِ «ارسال/دریافت» روش و مسئولِ همان گزینه (و با عوض‌کردنِ گزینه، عوض می‌شود)؛
 *   • پیگیریِ سفارشِ «ارسال»: «ارسال به دستِ خودِ دکان — مسئول: دکان»؛ «تغییر» ⇒ «دریافت از دکان» ⇒ برگهٔ تازه
 *     (هزینه و جمعِ سرور) ⇒ «بله» ⇒ خطِ روش، جمعِ اقلام و ردیفِ سرور همه «دریافت»؛
 *   • پرچمِ `shop.storefront.delivery_method` خاموش ⇒ نه اسکریپت، نه گره (همان صفحهٔ امروز)؛
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
const email = 'owner@method.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ روش' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.delivery_method']) await flag(key, true);
const slug = 'method-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ روش', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 5000, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
  op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
  fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
}] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);

//  مشتری: تأییدِ شماره و ثبتِ سفارشِ «ارسال» با API ِ واقعی؛ همان بلیت در مرورگر
const phone = '0791112233';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
const ck = await call('POST', `/api/store/${slug}/checkout`, {
  fulfillment: 'delivery', customer: { name: 'مشتری', phone }, address: { text: 'کارته‌سه، کوچهٔ دوم' },
  lines: [{ productId: 'rice', qtyMilli: 1000 }],
}, null, { 'Idempotency-Key': `web-method-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
ok(ck.status === 201 && ck.body.order.totalMinor === 35000, 'سفارشِ «ارسال» با هزینهٔ ۵۰ ثبت شد', JSON.stringify(ck.body).slice(0, 120));
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
    rice: { qtyMilli: 1000, name: 'برنج', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false },
  }));
}, [slug, { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt }]);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

//  ── صفحهٔ دکان، پیش از سبد (S71): روش و مسئولِ هر دو راهِ این ویترین ──
await page.goto(`${base}/store/${slug}#/`);
await page.waitForFunction(() => { const b = document.getElementById('store-dm'); return b && !b.hidden && b.textContent.length > 0; }, null, { timeout: 15000 });
const shopLine = await page.textContent('#store-dm');
ok(/خودِ دکان/.test(shopLine) && /خودتان/.test(shopLine) && !/VILL3N/.test(shopLine), 'صفحهٔ دکان: «ارسال — مسئول: دکان · دریافت — مسئول: خودتان» پیش از سبد', shopLine);

//  ── برگهٔ ثبت (S71) ──
await page.goto(`${base}/store/${slug}#/checkout`);
await page.waitForFunction(() => { const b = document.getElementById('ck-dm'); return b && !b.hidden && b.textContent.length > 0; }, null, { timeout: 15000 });
const first = await page.textContent('#ck-dm');
await page.check('input[name="fulfillment"][value="pickup"]');
await page.waitForFunction((was) => document.getElementById('ck-dm').textContent !== was, first, { timeout: 5000 });
const pick = await page.textContent('#ck-dm');
ok(/دکان/.test(first) && /خودتان/.test(pick), 'برگهٔ ثبت: روش و مسئولِ گزینهٔ برگزیده', `${first} | ${pick}`);

//  ── پیگیری (S71/S72) ──
let opened = false;
const open = async () => {
  if (opened) await page.reload(); else await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(token)}`);
  opened = true;
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
};
await open();
await page.waitForSelector('#o-dm-line', { timeout: 10000 });
const line0 = await page.textContent('#o-dm-line');
ok(/خودِ دکان/.test(line0) && !/VILL3N/.test(line0), 'پیگیری: «ارسال به دستِ خودِ دکان — مسئول: دکان»', line0);
await page.click('#o-dm-btn');
await page.selectOption('#o-dm-to', 'pickup');
await page.click('#o-dm-send');
await page.waitForSelector('dialog.confirm .btn-danger', { timeout: 5000 });
const q = await page.textContent('dialog.confirm p');
ok(/رایگان/.test(q) && /۳۰۰/.test(q), 'برگهٔ تازه از سرور: بی هزینهٔ ارسال، جمعِ ۳۰۰', q);
await page.click('dialog.confirm .btn-danger');
//  حالتِ واقعیِ نتیجه: خطِ روش «دریافت» و جمعِ اقلامِ پاسخِ تازهٔ سرور
await page.waitForFunction(() => /خودتان/.test((document.getElementById('o-dm-line') || {}).textContent || '')
  && !/۳۵۰/.test(document.getElementById('o-totals').textContent), null, { timeout: 10000 });
const row = await one('SELECT fulfillment, delivery_fee_minor, total_minor FROM online_orders WHERE id=$1', [orderId]);
ok(row.fulfillment === 'pickup' && Number(row.delivery_fee_minor) === 0 && Number(row.total_minor) === 30000, 'سرور: «دریافت»، بی هزینه، جمعِ ۳۰۰', JSON.stringify(row));

//  پرچمِ خاموش ⇒ همان صفحهٔ امروز
await flag('shop.storefront.delivery_method', false);
await open();   //  بارِ تازهٔ صفحه ⇒ هیچ اسکریپتِ پیشین نمی‌ماند
ok(await page.$('#o-dm') === null && await page.$('#store-dm') === null && await page.$('script[src="/store/delivery-method.js"]') === null, 'پرچمِ خاموش ⇒ نه اسکریپت، نه بخشِ «روشِ تحویل» (پیگیری و صفحهٔ دکان)');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: روش و مسئولِ تحویل و تغییرِ روش پیش از جمع‌آوری');
process.exit(bad ? 1 : 0);
