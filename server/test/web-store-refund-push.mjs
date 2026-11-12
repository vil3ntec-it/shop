/**
 * ویترین با کرومیومِ واقعی: خبرِ هر بازپرداخت روی پوشِ وب ِ صفحهٔ پیگیری (NTF-08؛ `store/order-push.js`، `store/sw.js`،
 * `lib/store-push.js`، `lib/customer-notify.js`، مهاجرتِ ۴۹۶).
 *
 *     npm run test:web-store      (پس از web-store-alert-push.mjs)
 *
 * customer-notify-refund.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه در عرضِ گوشی (۳۶۰). سرویسِ پوش ساختگی
 * (`useTransport`) و `pushManager` با کلیدِ P-256 ِ واقعیِ آزمون، همان روشِ web-store-push.mjs.
 *   • پرچم‌ها روشن: «خبرم کن» پیش از تحویل ⇒ پس از «برداشته شد» کارت هنوز «روشن است» و اشتراک می‌ماند.
 *   • هر بازپرداخت ⇒ یک پیامِ رمزشده با مبلغ و شمارهٔ سفارش؛ کارگرِ سرویس دو اعلانِ جدا نشان می‌دهد (برچسبِ جدا).
 *   • سفارشِ تحویل‌شدهٔ بی اشتراک ⇒ کارت با شرحِ «وقتی دکان پولی … برگرداند» (fa و en)، axe بی نقض.
 *   • بازپرداختِ کامل ⇒ کارت پنهان؛ بی پیگیریِ بازپرداخت ⇒ پس از تحویل کارت نیست (همان رفتارِ پیشین).
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
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
const { query, one, many, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const N = require('../src/lib/customer-notify');
const P = require('../src/lib/store-push');
const ece = require('http_ece');

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

const sent = [];
P.useTransport(async (req) => { sent.push(req); return { status: 201 }; });

//  ── دو دکانِ آزمایشی (دادهٔ ساختگی): «روشن» با پیگیریِ بازپرداخت، «بی پیگیری» بی آن ──
async function owner(email) {
  const password = 'Passw0rd!test';
  const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
  const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
  const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
    device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
  return d1.body.accessToken;
}
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

async function shop(name, slug, flags, T) {
  const shopId = (await call('POST', '/api/shop', { name }, T)).body.shop.id;
  //  پیش‌فرض‌روشن‌ها (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) `false` ِ صریح ⇒ همان ویترینِ پیشین؛ سپس کلیدهای همین آزمون
  for (const key of require('../src/lib/flag-defaults').DEFAULT_ON) await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: false }, A);
  for (const key of flags) await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
  const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: name, contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
  ok(put.status === 200, `ویترینِ «${name}»`, JSON.stringify(put.body).slice(0, 120));
  await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
    op_id: `op-rice-${slug}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
  }] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
  await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);
  return { shopId, slug, name, T };
}
const FLAGS = ['shop.orders', 'shop.storefront', 'shop.customer_notify', 'shop.storefront.web_push', 'shop.orders.refunds'];
const S = await shop('ویترینِ بازپرداخت', 'refund-push', [...FLAGS, 'shop.orders.refund_tracking'], await owner('owner@refund-push.local'));
const Q = await shop('ویترینِ بی پیگیری', 'refund-quiet', FLAGS, await owner('owner@refund-quiet.local'));

const phone = '0791112277';
const ticket = async (slug) => {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
  return vc.body;
};
const tk = await ticket(S.slug);
const tq = await ticket(Q.slug);
ok(Boolean(tk.ticket && tq.ticket), 'شماره تأیید شد');
async function order(st, t) {
  const ck = await call('POST', `/api/store/${st.slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }] },
    null, { 'Idempotency-Key': `web-rf-${crypto.randomUUID()}`, 'X-Store-Ticket': t.ticket });
  ok(ck.status === 201, `سفارش در «${st.name}» ثبت شد`, JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [st.shopId, ck.body.order.orderNo])).id;
  return { id, token: ck.body.order.token, no: ck.body.order.orderNo, total: ck.body.order.totalMinor, T: st.T };
}
const move = async (o, list) => {
  for (const to of list) {
    const r = await call('POST', `/api/shop/orders/${o.id}/transition`, { to }, o.T);
    if (r.status !== 200) ok(false, `گذار به ${to}`, JSON.stringify(r.body).slice(0, 160));
  }
};
const DELIVERED = ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'];
const refund = async (o, amountMinor, final = false) => {
  const r = await call('POST', `/api/shop/orders/${o.id}/refunds`, { amountMinor, reason: 'یادداشتِ کارمند', final }, o.T,
    { 'Idempotency-Key': `rf-${crypto.randomUUID()}` });
  ok(r.status === 201, `بازپرداختِ ${amountMinor}`, JSON.stringify(r.body).slice(0, 160));
  return r.body.entry;
};
const subsOf = (id) => many('SELECT endpoint FROM store_push_subs WHERE order_id=$1', [id]);

//  ── «مرورگرِ» آزمون (همان web-store-push.mjs) ──
const ecdh = crypto.createECDH('prime256v1');
ecdh.generateKeys();
const fakeSub = { endpoint: `https://fcm.googleapis.com/fcm/send/web-${crypto.randomUUID()}`, keys: { p256dh: ecdh.getPublicKey('base64url'), auth: crypto.randomBytes(16).toString('base64url') } };
const openMsg = (body) => JSON.parse(ece.decrypt(Buffer.from(body), { version: 'aes128gcm', privateKey: ecdh, authSecret: fakeSub.keys.auth }).toString('utf8'));
const stubPush = (fake) => {
  if (location.protocol === 'about:') return;
  const KEY = 'test-push-sub';
  const make = (key) => ({
    endpoint: fake.endpoint, options: { applicationServerKey: key, userVisibleOnly: true },
    toJSON() { return { endpoint: fake.endpoint, expirationTime: null, keys: fake.keys }; },
    unsubscribe() { localStorage.removeItem(KEY); return Promise.resolve(true); },
  });
  PushManager.prototype.subscribe = function (opts) {
    const u = new Uint8Array(opts.applicationServerKey);
    localStorage.setItem(KEY, btoa(String.fromCharCode.apply(null, u)));
    return Promise.resolve(make(u.buffer));
  };
  PushManager.prototype.getSubscription = function () {
    const k = localStorage.getItem(KEY);
    if (!k) return Promise.resolve(null);
    const raw = atob(k); const u = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) u[i] = raw.charCodeAt(i);
    return Promise.resolve(make(u.buffer));
  };
};

const browser = await chromium.launch({ channel: 'chromium' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
async function context(t) {
  const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  await ctx.grantPermissions(['notifications']);
  await ctx.addInitScript((x) => {
    if (location.protocol === 'about:') return;
    if (sessionStorage.getItem('seeded')) return;
    sessionStorage.setItem('seeded', '1');
    localStorage.setItem('store-ticket', JSON.stringify(x));
  }, { phone: t.phone, ticket: t.ticket, expiresAt: t.expiresAt });
  await ctx.addInitScript(stubPush, fakeSub);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  return { ctx, page };
}
const openOn = async (page, st, o, q = '') => {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${st.slug}${q}#/o/${encodeURIComponent(o.token)}`);
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
};
const textOf = (page, sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');

//  ── ۱) «خبرم کن» پیش از تحویل؛ پس از «برداشته شد» هنوز روشن ──
const { ctx, page } = await context(tk);
const o1 = await order(S, tk);
await openOn(page, S, o1);
await page.waitForSelector('#o-np-on', { state: 'visible', timeout: 10000 });
await page.click('#o-np-on');
await page.waitForSelector('#o-np-off', { state: 'visible', timeout: 15000 });
await N.tick();
await move(o1, DELIVERED);
await N.tick();
ok((await subsOf(o1.id)).length === 1, 'پس از «برداشته شد» اشتراک برای خبرِ بازپرداخت ماند');
await openOn(page, S, o1);
await page.waitForSelector('#o-np-off', { state: 'visible', timeout: 10000 });
ok(/روشن است/.test(await textOf(page, '#o-np-state')), 'کارت پس از تحویل: «روشن است»', await textOf(page, '#o-np-state'));

//  ── ۲) هر بازپرداخت ⇒ یک پیامِ رمزشده با مبلغ؛ کارگرِ سرویس دو اعلانِ جدا ──
await move(o1, ['return_requested']);
const before = sent.length;
await refund(o1, 10000);
await N.tick();
await refund(o1, 5000);
await N.tick();
await N.tick();
const msgs = sent.slice(before).map((r) => openMsg(r.body));
ok(msgs.length === 2, 'دو بازپرداخت ⇒ دو پیام، نه بیشتر', String(msgs.length));
ok(/۱۰۰ افغانی/.test(msgs[0] && msgs[0].body) && /۵۰ افغانی/.test(msgs[1] && msgs[1].body), 'هر پیام مبلغِ همان بازپرداخت', JSON.stringify(msgs.map((m) => m.body)));
const noFa = String(o1.no).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[Number(d)]);
ok(msgs.every((m) => m.body.includes(noFa) && m.title === S.name && !m.body.includes('یادداشتِ کارمند') && !m.body.includes(phone.slice(1))),
  'شمارهٔ سفارش و نامِ ویترین؛ بی دلیلِ کارمند و شماره', msgs[0] && msgs[0].body);
ok(msgs.length === 2 && msgs[0].tag !== msgs[1].tag, 'برچسبِ جدای هر بازپرداخت');

const cdp = await ctx.newCDPSession(page);
const regs = [];
cdp.on('ServiceWorker.workerRegistrationUpdated', (e) => regs.push(...e.registrations));
await cdp.send('ServiceWorker.enable');
await page.waitForTimeout(300);
const reg = regs.find((r) => r.scopeURL === `${base}/store/${S.slug}` && !r.isDeleted);
let shown = [];
if (reg && msgs.length === 2) {
  for (const m of msgs) await cdp.send('ServiceWorker.deliverPushMessage', { origin: base, registrationId: reg.registrationId, data: JSON.stringify(m) });
  for (let i = 0; i < 50 && shown.length < 2; i++) {
    await page.waitForTimeout(200);
    shown = await page.evaluate(async (sc) => (await (await navigator.serviceWorker.getRegistration(sc)).getNotifications())
      .filter((n) => /افغانی/.test(n.body)).map((n) => ({ body: n.body, tag: n.tag })), `/store/${S.slug}`);
  }
}
ok(Boolean(reg), 'DevTools: کارگرِ سرویسِ همین ویترین');
ok(shown.length === 2 && shown.some((n) => /۱۰۰ افغانی/.test(n.body)) && shown.some((n) => /۵۰ افغانی/.test(n.body)),
  'کارگرِ سرویس: هر دو اعلانِ بازپرداخت روی صفحه می‌مانند', JSON.stringify(shown));

//  ── ۳) بازپرداختِ کامل ⇒ کارت پنهان ──
await refund(o1, o1.total - 15000);
await N.tick();
ok((await subsOf(o1.id)).length === 0, 'بازپرداختِ کامل ⇒ اشتراک پاک');
await openOn(page, S, o1);
await page.waitForTimeout(500);
ok(!(await page.isVisible('#o-np').catch(() => false)), 'پس از بازپرداختِ کامل: کارت پنهان');
await ctx.close();

//  ── ۴) سفارشِ تحویل‌شدهٔ بی اشتراک (مرورگرِ تازه) ⇒ شرحِ خبرِ بازپرداخت ──
const o2 = await order(S, tk);
await move(o2, DELIVERED);
const fresh = await context(tk);
await openOn(fresh.page, S, o2);
await fresh.page.waitForSelector('#o-np-on', { state: 'visible', timeout: 10000 });
ok(/پولی از این سفارش را به شما برگرداند/.test(await textOf(fresh.page, '#o-np-state')), 'شرحِ کارت پس از تحویل: خبرِ بازپرداخت', await textOf(fresh.page, '#o-np-state'));
if (axeSrc) {
  await fresh.page.evaluate(axeSrc);
  const v = await fresh.page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core، WCAG 2.1 A/AA): پیگیریِ تحویل‌شده با کارتِ خبرِ بازپرداخت', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (AXE_PATH)');
await openOn(fresh.page, S, o2, '?lang=en');
await fresh.page.waitForFunction(() => /refunds money/.test((document.getElementById('o-np-state') || {}).textContent || ''), null, { timeout: 10000 })
  .then(() => ok(true, 'انگلیسی: شرحِ خبرِ بازپرداخت (push.en)'), async () => ok(false, 'انگلیسی: شرحِ خبرِ بازپرداخت', await textOf(fresh.page, '#o-np-state')));
await fresh.ctx.close();

//  ── ۵) بی پیگیریِ بازپرداخت ⇒ پس از تحویل کارت نیست (همان رفتارِ پیشین) ──
const o3 = await order(Q, tq);
await move(o3, DELIVERED);
const quiet = await context(tq);
await openOn(quiet.page, Q, o3);
await quiet.page.waitForTimeout(500);
ok(!(await quiet.page.isVisible('#o-np').catch(() => false)), 'بی پیگیریِ بازپرداخت: سفارشِ تحویل‌شده بی کارتِ پوش');
await quiet.ctx.close();

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: خبرِ هر بازپرداخت روی پوشِ وب ِ صفحهٔ پیگیری');
process.exit(bad ? 1 : 0);
