/**
 * ویترین با کرومیومِ واقعی: «تغییرهای سفارش» در صفحهٔ پیگیری — پیشنهادِ ویرایش با پذیرفتن/رد (ORD-14،
 * سرور: `order-amend.js`) و پیگیریِ بازپرداخت (NTF-08، سرور: `order-refund-view.js`)، در `store/order-changes.js` ِ دیربار.
 *
 *     npm run test:web-store      (پس از web-store-add.mjs)
 *
 * order-amend.test.js و order-refund-view.test.js همین را روی API قفل می‌کنند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • پرچم روشن ولی هنوز پیشنهاد/برگشتی نیست ⇒ نه اسکریپت، نه بخش (همان صفحهٔ امروز)؛
 *   • پیشنهادِ دکان ⇒ قلم، مقدارِ پیشین و تازه، جمع و مهلت؛ «می‌پذیرم» ⇒ اقلامِ **پاسخِ سرور** عوض و «پذیرفتید»؛
 *   • پیشنهادِ دوم ⇒ «نمی‌پذیرم» با تأیید ⇒ سفارش دست‌نخورده و «نپذیرفتید»؛
 *   • پیشنهادِ سوم که دکان در همین میان پس گرفت ⇒ «می‌پذیرم» ⇒ ۴۰۹ با پیامِ روشن و وضعیتِ تازه از سرور؛
 *   • درخواستِ برگشت و بازپرداختِ بخشی ⇒ وضعیت، «برگشته به شما» و ردیفِ «نقد»؛ همان با ‎?lang=en‎ به انگلیسی؛
 *   • پرچم‌ها خاموش ⇒ هیچ‌کدام؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
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
const email = 'owner@changes.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ تغییرها' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
const FLAGS = ['shop.orders', 'shop.storefront', 'shop.orders.amend', 'shop.orders.refunds', 'shop.orders.refund_tracking'];
//  پیش‌فرض‌روشن‌ها (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) `false` ِ صریح ⇒ همان ویترینِ پیشین؛ سپس کلیدهای همین آزمون
for (const key of require('../src/lib/flag-defaults').DEFAULT_ON) await flag(key, false);
for (const key of FLAGS) await flag(key, true);
const slug = 'changes-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ تغییرها', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [['rice', 'برنج', 300], ['salt', 'نمک', 20]].map(([id, name, salePrice]) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name, unit: 'دانه', salePrice, category: 'خوراکی', active: true },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const id of ['rice', 'salt']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);

//  مشتری: تأییدِ شماره و دو سفارش با API ِ واقعی (یکی برای ویرایش، یکی برای برگشت)
const phone = '0791112244';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
let seq = 0;
const checkout = async (lines) => {
  const ck = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines },
    null, { 'Idempotency-Key': `web-changes-${Date.now()}-${++seq}`, 'X-Store-Ticket': vc.body.ticket });
  ok(ck.status === 201, `سفارشِ ${seq} ثبت شد`, JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, ck.body.order.orderNo])).id;
  return { token: ck.body.order.token, id };
};
const oa = await checkout([{ productId: 'rice', qtyMilli: 2000 }, { productId: 'salt', qtyMilli: 1000 }]);
const ob = await checkout([{ productId: 'rice', qtyMilli: 1000 }]);
let pk = 0;
const propose = (id, body) => call('POST', `/api/shop/orders/${id}/amendments`, body, T, { 'Idempotency-Key': `amend-${Date.now()}-${++pk}` });

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript((t) => {
  if (sessionStorage.getItem('seeded')) return;
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
}, { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

let last = '';
const open = async (o, q = '') => {
  const url = `${base}/store/${slug}${q}#/o/${encodeURIComponent(o.token)}`;
  //  همان نشانی فقط #مسیر را عوض می‌کند ⇒ بارِ دوباره تا سفارش از نو خوانده شود
  if (url === last) await page.reload(); else await page.goto(url);
  if (url !== last && last && last.split('#')[0] === url.split('#')[0]) await page.reload();
  last = url;
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
  await page.waitForTimeout(300);
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const noPart = async () => (await page.$('#o-ch')) === null && (await page.$('script[src="/store/order-changes.js"]')) === null;

//  پرچم روشن، هنوز نه پیشنهاد و نه برگشت ⇒ همان صفحهٔ امروز
await open(oa);
ok(await noPart(), 'پرچم روشن ولی کلیدِ تهی ⇒ نه order-changes.js، نه #o-ch');

//  ── ORD-14: پیشنهاد ⇒ «می‌پذیرم» ──
const p1 = await propose(oa.id, { changes: [{ lineNo: 1, qtyMilli: 1000 }], reason: 'out_of_stock' });
ok(p1.status === 201, 'دکان ویرایش پیشنهاد داد', JSON.stringify(p1.body).slice(0, 120));
await open(oa);
await page.waitForSelector('#o-ch-yes', { timeout: 10000 });
const am = await text('#o-ch-am');
ok(/برنج/.test(am) && am.includes('۲') && am.includes('۱') && /کالا تمام شده/.test(am), 'پیشنهاد: قلم، مقدارِ پیشین و تازه و دلیل', am.slice(0, 160));
ok(/جمع/.test(await text('#o-ch-total')) && /فرصتِ پاسخ/.test(am), 'جمعِ پیشین و تازه و مهلتِ پاسخ پیداست');
await page.click('#o-ch-yes');
await page.waitForFunction(() => /پذیرفتید/.test((document.getElementById('o-ch-msg') || {}).textContent || ''), null, { timeout: 10000 });
const rowA = await one('SELECT total_minor FROM online_orders WHERE id=$1', [oa.id]);
ok(Number(rowA.total_minor) === 30000 + 2000, 'سرور: مقدارِ تازه اعمال شد', JSON.stringify(rowA));
const linesA = await page.$$eval('#o-lines li', (ls) => ls.map((l) => l.textContent).join(' | '));
ok(linesA.includes('۱') && !(await page.isVisible('#o-ch-yes')), 'اقلامِ صفحه از پاسخِ سرور به‌روز و دکمه‌ها رفتند', linesA);

//  ── پیشنهادِ دوم ⇒ «نمی‌پذیرم» با تأیید ──
const p2 = await propose(oa.id, { changes: [{ lineNo: 2, qtyMilli: 0 }], reason: 'customer_request' });
ok(p2.status === 201, 'پیشنهادِ دوم', JSON.stringify(p2.body).slice(0, 120));
await open(oa);
await page.waitForSelector('#o-ch-no', { timeout: 10000 });
ok(/برداشته می‌شود/.test(await text('#o-ch-lines')), 'قلمِ برداشتنی «برداشته می‌شود»');
await page.click('#o-ch-no');
await page.waitForSelector('dialog.confirm .btn-danger', { timeout: 5000 });
await page.click('dialog.confirm .btn-danger');
await page.waitForFunction(() => /نپذیرفتید/.test((document.getElementById('o-ch-msg') || {}).textContent || ''), null, { timeout: 10000 });
ok(Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [oa.id])).total_minor) === 32000, 'رد ⇒ سفارش دست‌نخورده');

//  ── پیشنهادِ سوم، دکان در همین میان پس می‌گیرد ⇒ ۴۰۹ و وضعیتِ تازه ──
const p3 = await propose(oa.id, { changes: [{ lineNo: 1, qtyMilli: 3000 }], reason: 'price_error' });
await open(oa);
await page.waitForSelector('#o-ch-yes', { timeout: 10000 });
const wd = await call('POST', `/api/shop/orders/${oa.id}/amendments/${p3.body.amendment.id}/withdraw`, {}, T);
ok(wd.status === 200, 'دکان پیشنهاد را پس گرفت', JSON.stringify(wd.body).slice(0, 120));
await page.click('#o-ch-yes');
await page.waitForFunction(() => /دیگر باز نیست/.test((document.getElementById('o-ch-msg') || {}).textContent || '')
  && !document.getElementById('o-ch-yes'), null, { timeout: 10000 });
ok(Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [oa.id])).total_minor) === 32000, '۴۰۹ ⇒ هیچ تغییری؛ پیام و وضعیتِ تازه از سرور');

//  ── NTF-08: درخواستِ برگشت و بازپرداختِ بخشی ──
for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up', 'return_requested']) {
  const r = await call('POST', `/api/shop/orders/${ob.id}/transition`, { to }, T);
  ok(r.status === 200, `سفارشِ دوم ⇒ ${to}`, r.status === 200 ? '' : JSON.stringify(r.body).slice(0, 120));
}
await open(ob);
await page.waitForSelector('#o-ch-rf-st', { timeout: 10000 });
ok(/درخواستِ برگشت ثبت شد/.test(await text('#o-ch-rf-st')) && (await page.$('#o-ch-rf-list')) === null, 'درخواست ⇒ «هنوز پولی برنگشته» و بی ردیف');
const rf = await call('POST', `/api/shop/orders/${ob.id}/refunds`, { amountMinor: 10000, reason: 'یادداشتِ داخلی: کیسه پاره' }, T, { 'Idempotency-Key': `rf-web-${Date.now()}-key` });
ok(rf.status === 201, 'بازپرداختِ بخشی ثبت شد', JSON.stringify(rf.body).slice(0, 120));
await open(ob);
await page.waitForSelector('#o-ch-rf-list', { timeout: 10000 });
const rfText = await text('#o-ch-rf');
//  ممیزیِ ویترین (ORD-15): فقط «برگشته به شما» — «مانده» ِ کنارِ آن با «پرداخت‌شده» ِ صفحه نمی‌خواند
ok(/بخشی از پول برگشته/.test(rfText) && /نقد/.test(rfText) && /برگشته به شما: ۱۰۰/.test(rfText) && !/مانده/.test(rfText), 'وضعیت، «برگشته به شما» و ردیفِ «نقد»', rfText.slice(0, 200));
ok(!/یادداشتِ داخلی/.test(await page.textContent('body')), 'دلیلِ نوشتهٔ کارمند در صفحهٔ مشتری نیست');
ok((await page.$('#o-ch-rc-1')) === null, 'E2E-RET-11: پرچمِ رسیدِ اصلاحی خاموش ⇒ بی رسید');
//  E2E-RET-11 (`shop.orders.refund_receipt`): رسیدِ اصلاحیِ همان ردیف با شمارهٔ `<سفارش>-R1` و مبلغِ درستِ سفارش از سرور
await flag('shop.orders.refund_receipt', true);
await open(ob);
await page.waitForSelector('#o-ch-rc-1', { timeout: 10000 });
const rcText = await text('#o-ch-rc-1');
ok(/رسیدِ اصلاحیِ \d+-R1/.test(rcText) && /برگشتِ همین رسید ۱۰۰/.test(rcText) && /مبلغِ درستِ سفارش/.test(rcText), 'E2E-RET-11: رسیدِ اصلاحی با شماره و مبلغِ درست', rcText.slice(0, 160));
ok(!/یادداشتِ داخلی/.test(await page.textContent('body')), 'E2E-RET-11: رسید دلیلِ کارمند را ندارد');
await flag('shop.orders.refund_receipt', false);
await open(ob, '?lang=en');
await page.waitForFunction(() => /Part of the money/.test((document.getElementById('o-ch-rf-st') || {}).textContent || ''), null, { timeout: 10000 });
ok(/Cash/.test(await text('#o-ch-rf-list')), 'انگلیسی: همان بخش با واژه‌نامهٔ changes.en');
await open(ob, '?lang=fa');

//  پرچم‌ها خاموش ⇒ همان صفحهٔ امروز
await flag('shop.orders.amend', false);
await flag('shop.orders.refund_tracking', false);
await open(oa);
ok(await noPart(), 'پرچمِ ویرایش خاموش ⇒ نه اسکریپت، نه بخش');
await open(ob);
ok(await noPart(), 'پرچمِ پیگیریِ بازپرداخت خاموش ⇒ نه اسکریپت، نه بخش');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: پیشنهادِ ویرایش و پیگیریِ بازپرداخت در صفحهٔ پیگیری');
process.exit(bad ? 1 : 0);
