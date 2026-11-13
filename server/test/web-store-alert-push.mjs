/**
 * ویترین با کرومیومِ واقعی: «روی همین دستگاه هم خبرم کن» زیرِ «خبرم کن وقتی موجود/ارزان شد» ِ برگهٔ کالا — پوشِ وب برای
 * «خبرم کن» ِ همین شماره (`store/alert-push.js` و `store/push-client.js` ِ دیربار، `store/sw.js`، `lib/store-alert-push.js`؛ WEB-10).
 *
 *     npm run test:web-store      (پس از web-store-push.mjs)
 *
 * store-alert-push.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰). مثلِ web-store-push.mjs: سرویسِ
 * پوشِ مرورگر (FCM) از این ماشین در دسترس نیست، پس `pushManager.subscribe` با کلیدِ P-256 ِ واقعیِ آزمون جایگزین می‌شود و سرور
 * به `useTransport` می‌فرستد — پیام با همان کلید باز می‌شود؛ کارگرِ سرویس پیام را از DevTools (`deliverPushMessage`) می‌گیرد.
 *   • پیش از «خبرم کن»: نه بخشِ «روی همین دستگاه»، نه کارگرِ سرویس، نه درخواست؛ پس از «خبرم کن»: بخش با دکمه (axe)، هنوز بی اجازه.
 *   • لمس ⇒ اجازه، کارگرِ سرویس با دامنهٔ همین ویترین، اشتراک با کلیدِ عمومیِ سرور، «روشن است» فقط پس از پاسخِ سرور.
 *   • گذار (موجود شد) ⇒ یک پیامِ رمزشده فقط با نام و قیمتِ کالا؛ کارگرِ سرویس اعلان را با نشانیِ برگهٔ همان کالا نشان می‌دهد.
 *   • تازه‌سازی ⇒ «روشن» و تازه‌سازیِ بی‌صدای اشتراک (بی subscribe ِ دوباره)؛ «خاموش کن» ⇒ اشتراک پاک.
 *   • شمارهٔ دیگر در همین مرورگر ⇒ «روشن» نیست (هر شماره خودش لمس می‌کند)؛ انگلیسی؛ اجازهٔ بسته؛ پرچمِ خاموش ⇒ هیچ.
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
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
const P = require('../src/lib/store-push');
const AL = require('../src/lib/storefront-alerts');
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

//  ── دکانِ آزمایشی (دادهٔ ساختگی) با کاتالوگ: «چای» تمام شده، ۱۲۰ افغانی ──
const email = 'owner@alert-push.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const FLAGS = ['shop.orders', 'shop.storefront', 'shop.catalog', 'shop.customer_notify', 'shop.storefront.alerts', 'shop.storefront.web_push'];
const shopId = (await call('POST', '/api/shop', { name: 'ویترینِ خبرم کن' }, T)).body.shop.id;
for (const key of FLAGS) await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
const slug = 'alert-push-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ خبرم کن', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین روشن شد', JSON.stringify(put.body).slice(0, 120));
await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: ['tea', 'rice'].map((id, i) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name: id === 'tea' ? 'چای' : 'برنج', unit: 'دانه', salePrice: id === 'tea' ? 120 : 300, category: 'خوراکی', active: true, purchasePrice: 1, barcodes: [], createdAt: Date.now() + i },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
for (const id of ['tea', 'rice']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);
const setStock = (productId, qtyMilli) => call('POST', '/api/shop/catalog/moves', { kind: 'adjust', productId, location: 'shop_floor', qtyMilli, reason: 'شمارشِ آزمون' },
  T, { 'Idempotency-Key': `mv-${crypto.randomUUID()}` });
ok((await setStock('rice', 5000)).status === 201, 'برنج موجود؛ چای تمام شده');

const ticket = async (phone) => {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
  return vc.body;
};
const tk = await ticket('0791113377');
ok(Boolean(tk.ticket), 'شماره تأیید شد');

//  ── «مرورگرِ» آزمون: کلیدِ P-256 ِ واقعی؛ subscribe/getSubscription ِ مرورگر با همین کلید (همان web-store-push.mjs) ──
const ecdh = crypto.createECDH('prime256v1');
ecdh.generateKeys();
const fakeSub = { endpoint: `https://fcm.googleapis.com/fcm/send/alert-${crypto.randomUUID()}`, keys: { p256dh: ecdh.getPublicKey('base64url'), auth: crypto.randomBytes(16).toString('base64url') } };
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
const seedTicket = (t) => {
  if (location.protocol === 'about:') return;
  if (sessionStorage.getItem('seeded')) return;
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
};

//  کرومیومِ کامل (headless ِ تازه): «پوستهٔ بی‌سر» ِ پیش‌فرض اعلان ندارد و اجازه‌اش همیشه «بسته» است (نه سبزِ دروغ)
const browser = await chromium.launch({ channel: 'chromium' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.grantPermissions(['notifications']);
await ctx.addInitScript(seedTicket, { phone: tk.phone, ticket: tk.ticket, expiresAt: tk.expiresAt });
await ctx.addInitScript(stubPush, fakeSub);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const posts = [];
page.on('request', (r) => { if (/\/alerts\/push$/.test(r.url())) posts.push(r.method()); });
const scripts = [];
page.on('request', (r) => { if (/\/store\/(alert-push|push-client)\.js$/.test(r.url())) scripts.push(r.url().split('/store/')[1]); });

const open = async (id, q = '') => {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${slug}${q}#/p/${id}`);
  await page.waitForSelector('#ps-alerts:not([hidden]) button', { timeout: 15000 });
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const visible = (sel) => page.isVisible(sel).catch(() => false);
const rows = () => many('SELECT phone, endpoint, page, proof FROM store_alert_push WHERE shop_id=$1', [shopId]);
const swCount = () => page.evaluate(async () => (await navigator.serviceWorker.getRegistrations()).length);

//  ── ۱) پیش از «خبرم کن»: هیچ ──
await open('tea');
const perm = await page.evaluate(() => Notification.permission);
ok(perm === 'granted', 'کرومیومِ کامل با اجازهٔ اعلان (نه پوستهٔ بی‌سر)', perm);
ok(/وقتی موجود شد خبرم کن/.test(await text('#ps-alerts')), 'برگهٔ «چای»: «وقتی موجود شد خبرم کن»');
await page.waitForTimeout(500);
ok(!(await visible('#ps-ap')) && posts.length === 0 && (await swCount()) === 0, 'پیش از «خبرم کن»: نه بخشِ «روی همین دستگاه»، نه درخواست، نه کارگرِ سرویس');

//  ── ۲) «خبرم کن» ⇒ بخشِ «روی همین دستگاه هم» با دکمه؛ هنوز بی اشتراک ──
await page.locator('#ps-alerts button', { hasText: 'وقتی موجود شد خبرم کن' }).click();
await page.waitForSelector('#ps-ap-on', { state: 'visible', timeout: 15000 });
ok((await text('#ps-ap-on')) === 'روی همین دستگاه هم خبرم کن' && /روی همین دستگاه هم بگیرید/.test(await text('#ps-ap-state')), 'بخش: شرح و «روی همین دستگاه هم خبرم کن»');
ok((await page.getAttribute('#ps-ap', 'aria-label')) === 'خبرِ «خبرم کن» روی همین دستگاه', 'گروه با نامِ دسترس‌پذیر');
ok(!(await visible('#ps-ap-off')), 'پیش از لمس: بی «خاموش کن»');
ok((await rows()).length === 0 && posts.length === 0 && (await swCount()) === 0, 'بی لمس: هیچ اشتراک، درخواست یا کارگرِ سرویس');
ok(scripts.includes('alert-push.js') && scripts.includes('push-client.js'), 'alert-push.js و push-client.js فقط با کلیدِ push ِ «هشدارهای من»', scripts.join(','));
if (axeSrc) {
  await page.evaluate(axeSrc);
  const v = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core، WCAG 2.1 A/AA): برگهٔ کالا با بخشِ «روی همین دستگاه هم»', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (AXE_PATH)');

//  ── ۳) لمس ⇒ اجازه، کارگرِ سرویس، اشتراک؛ «روشن است» پس از پاسخِ سرور ──
await page.click('#ps-ap-on');
await page.waitForSelector('#ps-ap-off', { state: 'visible', timeout: 15000 });
ok(/روشن است/.test(await text('#ps-ap-state')) && !(await visible('#ps-ap-on')), '«روشن است» و «خاموش کن»', await text('#ps-ap-state'));
const r1 = await rows();
ok(r1.length === 1 && r1[0].endpoint === fakeSub.endpoint && r1[0].page === `/store/${slug}` && r1[0].phone === '+93791113377' && r1[0].proof === 'ticket',
  'سرور: یک اشتراک برای همین شماره، همین مرورگر و همین ویترین (اثبات: بلیت)', JSON.stringify(r1));
const scope = await page.evaluate(async (sc) => { const r = await navigator.serviceWorker.getRegistration(sc); return r ? r.scope : ''; }, `/store/${slug}`);
ok(scope === `${base}/store/${slug}`, 'کارگرِ سرویس با دامنهٔ همین ویترین', scope);
const appKey = await page.evaluate(() => localStorage.getItem('test-push-sub'));
ok(Buffer.from(appKey || '', 'base64').toString('base64url') === await P.publicKey(), 'اشتراک با کلیدِ عمومیِ همین سرور');
ok(sent.length === 0, 'روشن کردن خودش هیچ پیامی نمی‌فرستد');

//  ── ۴) گذار ⇒ یک پیامِ رمزشده فقط با نام و قیمت؛ کارگرِ سرویس اعلان را با برگهٔ همان کالا نشان می‌دهد ──
ok((await setStock('tea', 3000)).status === 201, 'چای موجود شد');
await AL.tick();
ok(sent.length === 1 && sent[0].endpoint === fakeSub.endpoint, 'یک پیام به سرویسِ پوشِ همین مرورگر', String(sent.length));
const msg = sent[0] ? openMsg(sent[0].body) : {};
ok(msg.title === 'چای' && msg.body === 'دوباره موجود است — ۱۲۰ افغانی' && msg.url === `/store/${slug}#/p/tea` && /^a-/.test(msg.tag || ''),
  'پیام: نام و قیمتِ کالا، نشانیِ برگهٔ کالا', JSON.stringify(msg));
ok(!/0791113377|\+93|ویترینِ خبرم کن/.test(JSON.stringify(msg)), 'پیام بی شماره و بی نامِ ویترین');
const cdp = await ctx.newCDPSession(page);
const regs = [];
cdp.on('ServiceWorker.workerRegistrationUpdated', (e) => regs.push(...e.registrations));
await cdp.send('ServiceWorker.enable');
await page.waitForTimeout(300);
const reg = regs.find((r) => r.scopeURL === `${base}/store/${slug}` && !r.isDeleted);
let shown = [];
if (reg) {
  await cdp.send('ServiceWorker.deliverPushMessage', { origin: base, registrationId: reg.registrationId, data: JSON.stringify(msg) });
  for (let i = 0; i < 50 && !shown.length; i++) {
    await page.waitForTimeout(200);
    shown = await page.evaluate(async (sc) => (await (await navigator.serviceWorker.getRegistration(sc)).getNotifications())
      .map((n) => ({ title: n.title, body: n.body, url: n.data && n.data.url, tag: n.tag, dir: n.dir })), `/store/${slug}`);
  }
}
ok(Boolean(reg), 'DevTools: ثبتِ کارگرِ سرویسِ همین ویترین پیدا شد');
ok(shown.length === 1 && shown[0].title === 'چای' && shown[0].body === msg.body && shown[0].url === `/store/${slug}#/p/tea` && shown[0].tag === msg.tag && shown[0].dir === 'rtl',
  'کارگرِ سرویس: اعلان با نام، قیمت، نشانیِ برگهٔ کالا و برچسبِ a- (راست‌به‌چپ)', JSON.stringify(shown));
if (reg) {
  await cdp.send('ServiceWorker.deliverPushMessage', { origin: base, registrationId: reg.registrationId, data: JSON.stringify({ ...msg, url: 'https://evil.example/p', tag: 'a-evil' }) });
  let evil = [];
  for (let i = 0; i < 50 && !evil.length; i++) {
    await page.waitForTimeout(200);
    evil = await page.evaluate(async (sc) => (await (await navigator.serviceWorker.getRegistration(sc)).getNotifications({ tag: 'a-evil' }))
      .map((n) => n.data && n.data.url), `/store/${slug}`);
  }
  ok(evil.length === 1 && evil[0] === '', 'نشانیِ بیرونیِ پیام دور ریخته شد (لمس فقط همین مبدأ)', JSON.stringify(evil));
}

//  ── ۵) تازه‌سازی ⇒ «روشن» و تازه‌سازیِ بی‌صدا (بی subscribe ِ دوباره)؛ «خاموش کن» ⇒ پاک ──
const before = posts.length;
await open('rice');
await page.locator('#ps-alerts button', { hasText: 'وقتی ارزان شد خبرم کن' }).click();
await page.waitForSelector('#ps-ap-off', { state: 'visible', timeout: 15000 });
ok(posts.length === before + 1 && posts[posts.length - 1] === 'POST', 'تازه‌سازی: یک تازه‌سازیِ بی‌صدای اشتراک', posts.join(','));
ok((await page.evaluate(() => localStorage.getItem('test-subscribed'))) === '1', 'همان اشتراکِ مرورگر (بی subscribe ِ دوباره، بی پرسش)');
await page.click('#ps-ap-off');
await page.waitForSelector('#ps-ap-on', { state: 'visible', timeout: 10000 });
ok((await rows()).length === 0, '«خاموش کن» ⇒ اشتراکِ سرور پاک');
ok(/روی همین دستگاه هم بگیرید/.test(await text('#ps-ap-state')), 'پس از «خاموش»: همان شرح و دکمه');

//  ── ۶) شمارهٔ دیگر در همین مرورگر ⇒ «روشن» نیست؛ انگلیسی ──
await page.click('#ps-ap-on');
await page.waitForSelector('#ps-ap-off', { state: 'visible', timeout: 15000 });
const tk2 = await ticket('0791113388');
await page.evaluate((t) => localStorage.setItem('store-ticket', JSON.stringify(t)), { phone: tk2.phone, ticket: tk2.ticket, expiresAt: tk2.expiresAt });
const beforeOther = posts.length;
await open('rice');
await page.locator('#ps-alerts button', { hasText: 'وقتی ارزان شد خبرم کن' }).click();
await page.waitForSelector('#ps-ap-on', { state: 'visible', timeout: 15000 });
ok(posts.length === beforeOther && (await rows()).every((r) => r.phone === '+93791113377'), 'شمارهٔ دیگر: «روشن» نیست و بی لمس هیچ اشتراکی برایش ساخته نمی‌شود');
await open('rice', '?lang=en');
await page.waitForFunction(() => (document.getElementById('ps-ap-on') || {}).textContent === 'Notify me on this device too', null, { timeout: 10000 });
ok(true, 'انگلیسی: «Notify me on this device too»');

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
await p2.goto(`${base}/store/${slug}#/p/rice`);
await p2.waitForSelector('#ps-ap', { state: 'visible', timeout: 15000 });
ok(/در مرورگرتان بسته است/.test(await p2.$eval('#ps-ap-state', (e) => e.textContent)) && !(await p2.isVisible('#ps-ap-on')) && !(await p2.isVisible('#ps-ap-off')),
  'اجازهٔ بسته: «در مرورگرتان بسته است» بی دکمه');
ok(errors2.length === 0, 'اجازهٔ بسته: بی خطای جاوااسکریپت', errors2.join(' | '));
await ctx2.close();

//  ── ۸) پرچمِ پوش خاموش ⇒ همان «خبرم کن» ِ پیشین ──
ok((await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key: 'shop.storefront.web_push', value: false }, A)).status === 200, 'پرچمِ پوش خاموش شد');
const ctx3 = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx3.addInitScript((t) => { localStorage.setItem('store-ticket', JSON.stringify(t)); }, { phone: tk.phone, ticket: tk.ticket, expiresAt: tk.expiresAt });
const p3 = await ctx3.newPage();
const scripts3 = [];
p3.on('request', (r) => { if (/\/store\/(alert-push|push-client)\.js$/.test(r.url())) scripts3.push(r.url()); });
await p3.goto(`${base}/store/${slug}#/p/rice`);
await p3.waitForSelector('#ps-alerts:not([hidden]) button[aria-pressed="true"]', { timeout: 15000 });
await p3.waitForTimeout(500);
const offState = await p3.evaluate(async () => ({ row: Boolean(document.getElementById('ps-ap')), sw: (await navigator.serviceWorker.getRegistrations()).length }));
ok(!offState.row && offState.sw === 0 && scripts3.length === 0, 'پرچمِ خاموش ⇒ نه alert-push.js، نه push-client.js، نه بخش، نه کارگرِ سرویس', JSON.stringify({ ...offState, scripts: scripts3.length }));
await ctx3.close();

await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «روی همین دستگاه هم خبرم کن» (پوشِ وب ِ «خبرم کن») در برگهٔ کالا');
process.exit(bad ? 1 : 0);
