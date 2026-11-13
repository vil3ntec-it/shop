/**
 * ویترین با کرومیومِ واقعی: «پرداخت پس از پذیرش» در صفحهٔ پیگیری (`lib/order-flow.js`، `store/pay-step.js` ِ دیربار).
 *
 *     npm run test:web-store      (پس از web-store-chat.mjs)
 *
 * order-flow.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • تنظیمِ روشن، سفارشِ «تازه» ⇒ «منتظرِ پذیرشِ فروشنده» بی دکمهٔ نقد و بی دکمهٔ حساب‌پی؛
 *   • فروشنده قبول کرد ⇒ «لطفاً روشِ پرداخت را انتخاب کنید»، دکمهٔ «نقد هنگامِ برداشت» و دکمهٔ حساب‌پی؛
 *   • «نقد» ⇒ حالتِ تازه از پاسخِ سرور و رویدادِ سرور؛ همان با ‎?lang=en‎ به انگلیسی؛
 *   • تنظیمِ خاموش ⇒ نه اسکریپت، نه بخش (همان صفحهٔ امروز)؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
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
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-web-paystep';

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
const email = 'owner@paystep.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ پرداخت' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'pay.hesabpay']) await flag(key, true);
const sub = await call('POST', '/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, A);
ok(sub.status < 300, 'اشتراک (حساب‌پیِ ویترین فقط برای دکانِ مشترک)', JSON.stringify(sub.body).slice(0, 120));
const slug = 'paystep-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ پرداخت', contactPhone: '0700000001',
  acceptPickup: true, minOrderMinor: 0, payAfterAccept: true }, T);
ok(put.status === 200 && put.body.settings.payAfterAccept === true, 'ویترین با «پرداخت پس از پذیرش»', JSON.stringify(put.body).slice(0, 120));
await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
  op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
  fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
}] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);

const phone = '0791112255';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
const ck = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 2000 }] },
  null, { 'Idempotency-Key': `web-paystep-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
ok(ck.status === 201, 'سفارش ثبت شد', JSON.stringify(ck.body).slice(0, 120));
const o = { token: ck.body.order.token, id: (await one('SELECT id FROM online_orders WHERE shop_id=$1', [shopId])).id };

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
const open = async (q = '') => {
  const url = `${base}/store/${slug}${q}#/o/${encodeURIComponent(o.token)}`;
  if (url === last) await page.reload(); else await page.goto(url);
  if (url !== last && last && last.split('#')[0] === url.split('#')[0]) await page.reload();
  last = url;
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
  await page.waitForTimeout(300);
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const visible = (sel) => page.isVisible(sel).catch(() => false);

//  ── پیش از قبول ──
await open();
await page.waitForSelector('#o-ps-state', { timeout: 10000 });
ok(/منتظرِ پذیرشِ فروشنده/.test(await text('#o-ps-state')), 'پیش از قبول: «منتظرِ پذیرشِ فروشنده»', await text('#o-ps-state'));
ok(!(await visible('#o-ps-cash')) && !(await visible('#o-pay')), 'پیش از قبول: نه دکمهٔ نقد، نه حساب‌پی');

//  ── فروشنده قبول کرد ──
const acc = await call('POST', `/api/shop/orders/${o.id}/transition`, { to: 'accepted' }, T);
ok(acc.status === 200, 'فروشنده قبول کرد', JSON.stringify(acc.body).slice(0, 120));
await open();
await page.waitForSelector('#o-ps-cash', { timeout: 10000 });
ok(/روشِ پرداخت/.test(await text('#o-ps-state')) && (await text('#o-ps-state')).includes('۶۰۰'), 'پس از قبول: «روشِ پرداختِ ۶۰۰ افغانی را انتخاب کنید»', await text('#o-ps-state'));
ok(/برداشت از دکان/.test(await text('#o-ps-cash')), 'دکمه: «نقد هنگامِ برداشت از دکان»');
ok(await visible('#o-pay'), 'حساب‌پی پس از قبول (دکانِ مشترک، درگاهِ ساختگی)');

await page.click('#o-ps-cash');
await page.waitForFunction(() => /نقد هنگامِ برداشت از دکان —/.test((document.getElementById('o-ps-state') || {}).textContent || ''), null, { timeout: 10000 });
ok(!(await visible('#o-ps-cash')), '«نقد» ثبت شد ⇒ دکمه رفت و حالت از پاسخِ سرور');
const ev = await many(`SELECT 1 FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='pay_choice'`, [o.id]);
ok(ev.length === 1, 'سرور: یک رویدادِ «انتخابِ نقد»');
const row = await one('SELECT status, payment_status, cod_status FROM online_orders WHERE id=$1', [o.id]);
ok(row.status === 'accepted' && row.payment_status === 'unpaid' && row.cod_status === 'to_collect', 'وضعیت و نقد دست‌نخورده (پیشخوان می‌گیرد)', JSON.stringify(row));

await open('?lang=en');
await page.waitForFunction(() => /cash at pickup/.test((document.getElementById('o-ps-state') || {}).textContent || ''), null, { timeout: 10000 });
ok(/Payment/.test(await text('#o-ps h2')), 'انگلیسی: واژه‌نامهٔ paystep.en');
await open('?lang=fa');

//  ── تنظیمِ خاموش ⇒ همان صفحهٔ امروز ──
await call('PUT', '/api/shop/storefront', { payAfterAccept: false }, T);
await open();
ok((await page.$('#o-ps')) === null && (await page.$('script[src="/store/pay-step.js"]')) === null, 'تنظیمِ خاموش ⇒ نه pay-step.js، نه #o-ps');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «پرداخت پس از پذیرش» در صفحهٔ پیگیری');
process.exit(bad ? 1 : 0);
