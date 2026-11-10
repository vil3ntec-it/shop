/**
 * ویترین با کرومیومِ واقعی: «خبرم کن روی همین دستگاه» ِ صفحهٔ پیگیری — پوشِ وب برای همین سفارش
 * (`store/order-push.js` ِ دیربار، `store/sw.js`، `lib/store-push.js`؛ CHK-20، NTF-01).
 *
 *     npm run test:web-store      (پس از web-store-weigh.mjs)
 *
 * store-push.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰). سرویسِ پوشِ مرورگر (FCM) از این
 * ماشین در دسترس نیست، پس `pushManager.subscribe` با کلیدِ P-256 ِ واقعیِ آزمون جایگزین می‌شود و سرور به `useTransport`
 * می‌فرستد — پیام با همان کلید باز می‌شود؛ کارگرِ سرویس پیام را از DevTools (`ServiceWorker.deliverPushMessage`) می‌گیرد.
 *   • پرچم‌ها روشن ⇒ کارتِ «خبرِ این سفارش روی همین دستگاه» با «خبرم کن» (axe)؛ لمس ⇒ اجازه، کارگرِ سرویس با دامنهٔ همین
 *     ویترین، اشتراک با کلیدِ عمومیِ سرور، «روشن است» فقط پس از پاسخِ سرور؛ «ثبت شد» ِ ماندهٔ صف رمزشده به همین مرورگر.
 *   • کارگرِ سرویس اعلان را با عنوان، متن و نشانیِ پیگیری نشان می‌دهد.
 *   • تازه‌سازی ⇒ همان «روشن» بی درخواستِ دوباره؛ سفارشِ تازهٔ همین دکان ⇒ بی لمس وصل؛ «خاموش کن» فقط همان سفارش.
 *   • اجازهٔ بسته ⇒ پیامِ «در مرورگر بسته است» بی دکمه؛ انگلیسی؛ پرچمِ خاموش ⇒ نه اسکریپت، نه کارت، نه کارگرِ سرویس.
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

//  ── سرویسِ پوشِ ساختگی: هر درخواستِ سرور این‌جا می‌ماند (هرگز شبکه) ──
const sent = [];
P.useTransport(async (req) => { sent.push(req); return { status: 201 }; });

//  ── دو دکانِ آزمایشی (دادهٔ ساختگی): «روشن» با هر دو پرچم، «خاموش» بی پرچمِ پوش ──
const email = 'owner@push.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

async function shop(name, slug, flags) {
  const shopId = (await call('POST', '/api/shop', { name }, T)).body.shop.id;
  for (const key of flags) await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
  const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: name, contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
  ok(put.status === 200, `ویترینِ «${name}»`, JSON.stringify(put.body).slice(0, 120));
  await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
    op_id: `op-rice-${slug}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
  }] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
  await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);
  return { shopId, slug, name };
}
const BASE_FLAGS = ['shop.orders', 'shop.storefront', 'shop.customer_notify'];
const S = await shop('ویترینِ پوش', 'push-store', [...BASE_FLAGS, 'shop.storefront.web_push']);

const phone = '0791112266';
const ticket = async (slug) => {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
  return vc.body;
};
const tk = await ticket(S.slug);
ok(Boolean(tk.ticket), 'شماره تأیید شد');
async function order(st, t) {
  const ck = await call('POST', `/api/store/${st.slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }] },
    null, { 'Idempotency-Key': `web-push-${crypto.randomUUID()}`, 'X-Store-Ticket': t.ticket });
  ok(ck.status === 201, `سفارش در «${st.name}» ثبت شد`, JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [st.shopId, ck.body.order.orderNo])).id;
  return { id, token: ck.body.order.token, no: ck.body.order.orderNo };
}
const o1 = await order(S, tk);
await N.tick();
ok((await one('SELECT status FROM customer_notify_outbox WHERE order_id=$1', [o1.id])).status === 'no_channel', 'پیش از «خبرم کن»: «ثبت شد» بی مرورگر ماند (no_channel)');

//  ── «مرورگرِ» آزمون: کلیدِ P-256 ِ واقعی؛ subscribe/getSubscription ِ مرورگر با همین کلید ──
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
    localStorage.setItem('test-subscribed', String(Number(localStorage.getItem('test-subscribed') || 0) + 1));
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

//  کرومیومِ کامل (headless ِ تازه): «پوستهٔ بی‌سر» ِ پیش‌فرض اعلان ندارد و اجازه‌اش همیشه «بسته» است. `playwright install chromium`
//  هر دو را می‌گذارد؛ بی آن، آزمون همان‌جا ❌ می‌گوید (نه سبزِ دروغ).
const browser = await chromium.launch({ channel: 'chromium' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.grantPermissions(['notifications']);
await ctx.addInitScript((t) => {
  if (location.protocol === 'about:') return;   //  برگهٔ خالیِ میانِ دو باز کردن حافظه ندارد
  if (sessionStorage.getItem('seeded')) return;
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
}, { phone: tk.phone, ticket: tk.ticket, expiresAt: tk.expiresAt });
await ctx.addInitScript(stubPush, fakeSub);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const posts = [];
page.on('request', (r) => { if (/\/push$/.test(r.url())) posts.push(`${r.method()} ${r.url().split('/orders/')[1]}`); });

const open = async (st, o, q = '') => {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${st.slug}${q}#/o/${encodeURIComponent(o.token)}`);
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const visible = (sel) => page.isVisible(sel).catch(() => false);
const subsOf = (id) => many('SELECT endpoint, page FROM store_push_subs WHERE order_id=$1', [id]);

//  ── ۱) کارت و «خبرم کن» ──
await open(S, o1);
const perm = await page.evaluate(() => Notification.permission);
ok(perm === 'granted', 'کرومیومِ کامل با اجازهٔ اعلان (نه پوستهٔ بی‌سر)', perm);
await page.waitForSelector('#o-np-on', { state: 'visible', timeout: 10000 });
ok(/خبرِ این سفارش روی همین دستگاه/.test(await text('#o-np-h')), 'کارت: «خبرِ این سفارش روی همین دستگاه»', await text('#o-np-h'));
ok(/قبول کرد/.test(await text('#o-np-state')) && (await text('#o-np-on')) === 'خبرم کن', 'شرح و دکمهٔ «خبرم کن»');
ok(!(await visible('#o-np-off')), 'پیش از لمس: بی «خاموش کن»');
//  CHK-20: رسیدنِ واژه‌نامهٔ کارت عنوانِ رسید («سفارشِ شمارهٔ …») را به «سفارش» برنمی‌گرداند (پیش‌تر `K.part('push', #view-order)`)
ok(/شمارهٔ/.test(await text('#o-title')), 'CHK-20: پس از کارتِ پوش عنوانِ رسید همان «سفارشِ شمارهٔ …» است', await text('#o-title'));
ok((await subsOf(o1.id)).length === 0 && posts.length === 0, 'بی لمس: هیچ اشتراک و هیچ درخواست');
if (axeSrc) {
  await page.evaluate(axeSrc);
  const v = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core، WCAG 2.1 A/AA): صفحهٔ پیگیری با کارتِ پوش', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (AXE_PATH)');

await page.click('#o-np-on');
await page.waitForSelector('#o-np-off', { state: 'visible', timeout: 15000 });
ok(/روشن است/.test(await text('#o-np-state')), '«روشن است» پس از پاسخِ سرور', await text('#o-np-state'));
ok(!(await visible('#o-np-on')), '«خبرم کن» پنهان شد');
const subs1 = await subsOf(o1.id);
ok(subs1.length === 1 && subs1[0].endpoint === fakeSub.endpoint && subs1[0].page === `/store/${S.slug}`, 'سرور: یک اشتراک با همین مرورگر و همین ویترین', JSON.stringify(subs1));
const scope = await page.evaluate(async (sc) => { const r = await navigator.serviceWorker.getRegistration(sc); return r ? r.scope : ''; }, `/store/${S.slug}`);
ok(scope === `${base}/store/${S.slug}`, 'کارگرِ سرویس با دامنهٔ همین ویترین', scope);
const appKey = await page.evaluate(() => localStorage.getItem('test-push-sub'));
const viewKey = (await call('GET', `/api/store/${S.slug}/orders/${encodeURIComponent(o1.token)}`)).body.order.push.key;
ok(Buffer.from(appKey, 'base64').toString('base64url') === viewKey, 'اشتراک با کلیدِ عمومیِ همین سرور');

//  ── ۲) «ثبت شد» ِ ماندهٔ صف رمزشده به همین مرورگر ──
await N.tick();
ok(sent.length === 1 && sent[0].endpoint === fakeSub.endpoint, 'یک پیام به سرویسِ پوشِ همین مرورگر', String(sent.length));
const msg = sent[0] ? openMsg(sent[0].body) : {};
ok(msg.title === S.name && /ثبت شد/.test(msg.body) && msg.url === `/store/${S.slug}#/o/${encodeURIComponent(o1.token)}`, 'پیام: نامِ ویترین، «ثبت شد»، نشانیِ پیگیری', JSON.stringify(msg).slice(0, 160));
ok((await one('SELECT status, channel FROM customer_notify_outbox WHERE order_id=$1', [o1.id])).channel === 'push', 'صفِ اعلان: «رفت» از پوش');

//  ── ۳) کارگرِ سرویس اعلان را نشان می‌دهد (DevTools: همان پیام) ──
const cdp = await ctx.newCDPSession(page);
const regs = [];
cdp.on('ServiceWorker.workerRegistrationUpdated', (e) => regs.push(...e.registrations));
await cdp.send('ServiceWorker.enable');
await page.waitForTimeout(300);
const reg = regs.find((r) => r.scopeURL === `${base}/store/${S.slug}` && !r.isDeleted);
let shown = [];
if (reg) {
  await cdp.send('ServiceWorker.deliverPushMessage', { origin: base, registrationId: reg.registrationId, data: JSON.stringify(msg) });
  for (let i = 0; i < 50 && !shown.length; i++) {
    await page.waitForTimeout(200);
    shown = await page.evaluate(async (sc) => (await (await navigator.serviceWorker.getRegistration(sc)).getNotifications())
      .map((n) => ({ title: n.title, body: n.body, url: n.data && n.data.url, tag: n.tag, dir: n.dir })), `/store/${S.slug}`);
  }
}
ok(Boolean(reg), 'DevTools: ثبتِ کارگرِ سرویسِ همین ویترین پیدا شد');
ok(shown.length === 1 && shown[0].title === msg.title && shown[0].body === msg.body && shown[0].url === msg.url && shown[0].tag === msg.tag && shown[0].dir === 'rtl',
  'کارگرِ سرویس: اعلان با عنوان، متن، نشانیِ پیگیری و برچسب (راست‌به‌چپ)', JSON.stringify(shown).slice(0, 200));
//  پیامِ دست‌کاری‌شده: نشانیِ بیرونی ⇒ هیچ نشانی روی اعلان (لمس هیچ پنجره‌ای باز نمی‌کند)
if (reg) {
  await cdp.send('ServiceWorker.deliverPushMessage', { origin: base, registrationId: reg.registrationId, data: JSON.stringify({ ...msg, url: '//evil.example/x', tag: 'o-evil' }) });
  let evil = [];
  for (let i = 0; i < 50 && !evil.length; i++) {
    await page.waitForTimeout(200);
    evil = await page.evaluate(async (sc) => (await (await navigator.serviceWorker.getRegistration(sc)).getNotifications({ tag: 'o-evil' }))
      .map((n) => n.data && n.data.url), `/store/${S.slug}`);
  }
  ok(evil.length === 1 && evil[0] === '', 'نشانیِ بیرونیِ پیام دور ریخته شد', JSON.stringify(evil));
}

//  ── ۴) تازه‌سازی ⇒ همان «روشن»، بی درخواستِ دوباره ──
const before = posts.length;
await open(S, o1);
await page.waitForSelector('#o-np-off', { state: 'visible', timeout: 10000 });
ok(posts.length === before, 'تازه‌سازی: «روشن» بی درخواستِ دوباره', posts.join(' | '));

//  ── ۵) سفارشِ تازهٔ همین دکان ⇒ بی لمس وصل؛ «خاموش کن» فقط همان ──
const o2 = await order(S, tk);
await open(S, o2);
await page.waitForSelector('#o-np-off', { state: 'visible', timeout: 15000 });
ok((await subsOf(o2.id)).length === 1, 'سفارشِ تازه: بی لمس وصل شد (اجازه از پیش)');
ok((await page.evaluate(() => localStorage.getItem('test-subscribed'))) === '1', 'همان اشتراکِ مرورگر (بی subscribe ِ دوباره)');
await page.click('#o-np-off');
await page.waitForSelector('#o-np-on', { state: 'visible', timeout: 10000 });
ok(/خاموش شد/.test(await text('#o-np-state')), '«خاموش شد»', await text('#o-np-state'));
ok((await subsOf(o2.id)).length === 0 && (await subsOf(o1.id)).length === 1, '«خاموش» فقط همین سفارش');
const o3 = await order(S, tk);
await open(S, o3);
await page.waitForSelector('#o-np-on', { state: 'visible', timeout: 10000 });
ok((await subsOf(o3.id)).length === 0, 'پس از «خاموش»: سفارشِ تازه خودکار وصل نمی‌شود');

//  ── ۶) انگلیسی ──
await open(S, o3, '?lang=en');
await page.waitForFunction(() => /Updates for this order/.test((document.getElementById('o-np-h') || {}).textContent || ''), null, { timeout: 10000 });
ok((await text('#o-np-on')) === 'Notify me', 'انگلیسی: واژه‌نامهٔ push.en');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await ctx.close();

//  ── ۷) اجازهٔ بسته ⇒ پیامِ روشن، بی دکمه ──
const ctx2 = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx2.addInitScript((t) => {
  localStorage.setItem('store-ticket', JSON.stringify(t));
  Object.defineProperty(Notification, 'permission', { get: () => 'denied' });
}, { phone: tk.phone, ticket: tk.ticket, expiresAt: tk.expiresAt });
const p2 = await ctx2.newPage();
const errors2 = [];
p2.on('pageerror', (e) => errors2.push(String(e)));
await p2.goto(`${base}/store/${S.slug}#/o/${encodeURIComponent(o3.token)}`);
await p2.waitForSelector('#o-np', { state: 'visible', timeout: 15000 });
ok(/در مرورگرتان بسته است/.test(await p2.$eval('#o-np-state', (e) => e.textContent)) && !(await p2.isVisible('#o-np-on')), 'اجازهٔ بسته: «در مرورگرتان بسته است» بی دکمه');
ok(errors2.length === 0, 'اجازهٔ بسته: بی خطای جاوااسکریپت', errors2.join(' | '));
await ctx2.close();

//  ── ۸) پرچمِ خاموش ⇒ همان صفحهٔ امروز ──
ok((await call('PUT', '/api/admin/pump/live-config', { scope: S.shopId, key: 'shop.storefront.web_push', value: false }, A)).status === 200, 'پرچمِ پوش خاموش شد');
const ctx3 = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx3.addInitScript((t) => { localStorage.setItem('store-ticket', JSON.stringify(t)); }, { phone: tk.phone, ticket: tk.ticket, expiresAt: tk.expiresAt });
const p3 = await ctx3.newPage();
const o4 = await order(S, tk);
await p3.goto(`${base}/store/${S.slug}#/o/${encodeURIComponent(o4.token)}`);
await p3.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
await p3.waitForTimeout(500);
const offState = await p3.evaluate(async () => ({
  card: Boolean(document.getElementById('o-np')),
  script: Boolean(document.querySelector('script[src="/store/order-push.js"]')),
  sw: (await navigator.serviceWorker.getRegistrations()).length,
}));
ok(!offState.card && !offState.script && offState.sw === 0, 'پرچمِ خاموش ⇒ نه order-push.js، نه کارت، نه کارگرِ سرویس', JSON.stringify(offState));
await ctx3.close();

await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «خبرم کن روی همین دستگاه» (پوشِ وب) در صفحهٔ پیگیری');
process.exit(bad ? 1 : 0);
