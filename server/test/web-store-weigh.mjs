/**
 * ویترین با کرومیومِ واقعی: «وزنِ بیشتر از سفارش» در صفحهٔ پیگیری (S56؛ `lib/pick-weight-hold.js`، `store/weight-hold.js` ِ دیربار).
 *
 *     npm run test:web-store      (پس از web-store-paystep.mjs)
 *
 * pick-weight-hold.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • پرچم روشن و نگه‌داشتِ باز ⇒ کارت با قلم، وزنِ سفارش و واقعی، مبلغِ تازه، «… دقیقه مانده» و دو دکمه (axe)؛
 *     «می‌پذیرم» ⇒ «پذیرفته شد» فقط پس از پاسخِ سرور و قلم با همان وزن؛ بارِ بعد (بی نگه‌داشتِ باز) نه اسکریپت، نه کارت.
 *   • انگلیسی: «No, remove it» ⇒ قلم حذف.
 *   • مهلت در صفحه سر می‌رسد ⇒ دکمه‌ها می‌روند و «وقتِ جواب گذشت…».
 *   • پرچمِ خاموش (نگه‌داشتِ باز در پایگاه) ⇒ نه اسکریپت، نه کارت؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { missingTool, PLAYWRIGHT_INSTALL, animationsSettled } from './browser-guard.mjs';
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
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
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
const email = 'owner@weigh.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ وزن' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.orders.picking', 'shop.orders.weight_hold']) await flag(key, true);
const slug = 'weigh-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ وزن', contactPhone: '0700000001',
  acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین روشن', JSON.stringify(put.body).slice(0, 120));
let seq = 0;
await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  ['meat', { name: 'گوشت', unit: 'کیلو', salePrice: 500, category: 'گوشت', active: true }],
  ['oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی', active: true }],
].map(([id, fields]) => ({ op_id: `op-${id}-${Date.now()}-${++seq}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields })) },
T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
for (const id of ['meat', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);

const phone = '0791112266';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');

/** سفارشِ ویترین (گوشتِ ۱ کیلو + روغن)، قبول، جمع‌آوری و گوشتِ ۱٫۴ کیلو ⇒ نگه‌داشتِ باز */
async function heldOrder(label) {
  const ck = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone },
    lines: [{ productId: 'meat', qtyMilli: 1000 }, { productId: 'oil', qtyMilli: 1000 }] },
  null, { 'Idempotency-Key': `web-weigh-${++seq}-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
  ok(ck.status === 201, `${label}: سفارش ثبت شد`, JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY order_no DESC LIMIT 1', [shopId])).id;
  await call('POST', `/api/shop/orders/${id}/transition`, { to: 'accepted' }, T);
  await call('POST', `/api/shop/orders/${id}/pick/start`, {}, T);
  const sheet = (await call('GET', `/api/shop/orders/${id}/pick`, null, T)).body.pick;
  const meat = sheet.lines.find((l) => l.productId === 'meat').lineNo;
  const r = await call('POST', `/api/shop/orders/${id}/pick/events`, { events: [{ eventId: `ev-weigh-${++seq}-key`, lineNo: meat, type: 'weigh', qtyMilli: 1400 }] }, T);
  const hold = r.body && r.body.results && r.body.results[0].hold;
  ok(Boolean(hold) && hold.status === 'pending', `${label}: نگه‌داشتِ باز`, JSON.stringify(r.body).slice(0, 160));
  return { id, token: ck.body.order.token, hold, meat };
}

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript((t) => {
  if (!/^http/.test(location.protocol) || sessionStorage.getItem('seeded')) return;   //  about:blank ِ میانِ دو بار
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
}, { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

//  هر بار صفحهٔ تازه (نه فقط تغییرِ #) تا «اسکریپت بار نشد» راست بسنجد
const open = async (o, q = '') => {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${slug}${q}#/o/${encodeURIComponent(o.token)}`);
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
  await page.waitForTimeout(300);
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const visible = (sel) => page.isVisible(sel).catch(() => false);
const holdRow = (id) => one('SELECT status, decided_by FROM order_pick_weight_holds WHERE id=$1', [id]);
const lineRow = (o) => one('SELECT status, picked_qty_milli FROM order_pick_lines WHERE order_id=$1 AND line_no=$2', [o.id, o.meat]);
const YES = '#o-wh-list button.btn-accent';
const NO = '#o-wh-list button.btn-ghost';

//  ── پذیرش (فارسی) ──
const a = await heldOrder('پذیرش');
await open(a);
await page.waitForSelector(YES, { state: 'visible', timeout: 15000 });
ok((await text('#o-wh-h')) === 'وزنِ بیشتر از سفارش', 'کارت: «وزنِ بیشتر از سفارش»', await text('#o-wh-h'));
const list = await text('#o-wh-list');
ok(list.includes('گوشت: سفارش ۱ کیلو، وزنِ واقعی ۱٫۴ کیلو'), 'قلم با وزنِ سفارش و واقعی', list);
ok(/مبلغ: ۷۰۰ .*به‌جای ۵۰۰ .*\(۲۰۰ .*بیشتر\)/.test(list), 'مبلغِ تازه به‌جای پیشین و تفاوت', list);
ok(/دقیقه مانده/.test(list) && /(۹|۱۰) دقیقه/.test(list), 'شمارشِ مهلت: «… دقیقه مانده» (۱۰ دقیقه)', list);
ok((await text(YES)).startsWith('می‌پذیرم') && (await text(NO)) === 'نه، حذف شود', 'دو دکمه: «می‌پذیرم — مبلغ» و «نه، حذف شود»');
ok((await page.$('script[src="/store/weight-hold.js"]')) !== null, 'اسکریپتِ دیربار فقط با نگه‌داشتِ باز بار شد');
if (axeSrc) {
  await animationsSettled(page, '#o-wh');
  await page.evaluate(axeSrc);
  const v = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core، WCAG 2.1 A/AA): صفحهٔ پیگیری با کارتِ وزن', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (AXE_PATH)');
const wide = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
ok(wide <= 0, 'بی بیرون‌زدگی در ۳۶۰', String(wide));

await page.click(YES);
await page.waitForFunction(() => /پذیرفته شد؛/.test((document.getElementById('o-wh-list') || {}).textContent || ''), null, { timeout: 10000 });
ok(!(await visible(YES)), '«پذیرفته شد» از پاسخِ سرور و دکمه‌ها رفتند');
const ha = await holdRow(a.hold.id);
ok(ha.status === 'accepted' && ha.decided_by === 'customer', 'سرور: نگه‌داشت پذیرفته (مشتری)', JSON.stringify(ha));
const la = await lineRow(a);
ok(la.status === 'picked' && Number(la.picked_qty_milli) === 1400, 'سرور: قلم با همان وزنِ ۱٫۴', JSON.stringify(la));
await open(a);
ok((await page.$('#o-wh')) === null && (await page.$('script[src="/store/weight-hold.js"]')) === null, 'بی نگه‌داشتِ باز ⇒ نه اسکریپت، نه کارت');

//  ── رد (انگلیسی) ──
const b = await heldOrder('رد');
await open(b, '?lang=en');
await page.waitForSelector(NO, { state: 'visible', timeout: 15000 });
ok((await text('#o-wh-h')) === 'Heavier than ordered' && /min left/.test(await text('#o-wh-list')), 'انگلیسی: واژه‌نامهٔ weigh.en', await text('#o-wh-list'));
ok((await text(NO)) === 'No, remove it', 'دکمهٔ «No, remove it»');
await page.click(NO);
await page.waitForFunction(() => /Removed;/.test((document.getElementById('o-wh-list') || {}).textContent || ''), null, { timeout: 10000 });
const hb = await holdRow(b.hold.id);
const lb = await lineRow(b);
ok(hb.status === 'rejected' && hb.decided_by === 'customer' && lb.status === 'removed', 'سرور: رد ⇒ قلم حذف', JSON.stringify([hb, lb]));

//  ── مهلت در صفحه سر می‌رسد ──
const c = await heldOrder('مهلت');
await query('UPDATE order_pick_weight_holds SET deadline_at=$2 WHERE id=$1', [c.hold.id, Date.now() + 7000]);
await open(c, '?lang=fa');
await page.waitForSelector(YES, { state: 'visible', timeout: 15000 });
ok(/۱ دقیقه مانده/.test(await text('#o-wh-list')), 'کمتر از یک دقیقه ⇒ «۱ دقیقه مانده»', await text('#o-wh-list'));
await page.waitForFunction(() => /وقتِ جواب گذشت/.test((document.getElementById('o-wh-list') || {}).textContent || ''), null, { timeout: 20000 });
ok(!(await visible(YES)) && !(await visible(NO)), 'مهلت گذشت ⇒ دکمه‌ها رفتند و «وقتِ جواب گذشت؛ حذف شد…»');

//  ── پرچمِ خاموش ⇒ همان صفحهٔ پیشین ──
const d = await heldOrder('خاموش');
await flag('shop.orders.weight_hold', false);
await open(d);
ok((await page.$('#o-wh')) === null && (await page.$('script[src="/store/weight-hold.js"]')) === null, 'پرچمِ خاموش ⇒ نه weight-hold.js، نه #o-wh');
ok((await holdRow(d.hold.id)).status === 'pending', 'پرچمِ خاموش: هیچ نوشتن');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «وزنِ بیشتر از سفارش» در صفحهٔ پیگیری');
process.exit(bad ? 1 : 0);
