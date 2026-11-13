/**
 * ویترین با کرومیومِ واقعی: «شمارهٔ تماس و ایمیل» ِ حسابِ مشتری — §۲۳.۴ ⇐ «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳؛ صفحه:
 * `store/phone.js` ِ دیربار از `store/acct.js`؛ سرور: `POST /api/store/:slug/account/phone|email`، `lib/store-customers`؛
 * قرارداد: `storefront-email-identity.test.js`).
 *
 *     npm run test:web-store      (پس از web-store-erase.mjs)
 *
 *   • ⛔ پرچمِ `shop.storefront.web_account` خاموش ⇒ phone.js خواسته نمی‌شود و درهای سرور همان درِ ناشناخته؛
 *   • روشن ⇒ «شمارهٔ تماس و ایمیل» (بسته) زیرِ «حسابِ من»؛ شمارهٔ تماس **بی هیچ کدی** عوض می‌شود (هیچ `verify/start`)؛
 *   • ایمیلِ کنونی ⇒ پیام، بی کد؛ ایمیلِ حسابِ دیگر ⇒ کد به همان ایمیل، سپس «حسابِ جدا دارد» و بلیتِ پیشینِ دستگاه برمی‌گردد؛
 *   • کدِ نادرست ⇒ «کد درست نیست» و کد در کادر می‌ماند؛ قطعِ شبکه پس از کد ⇒ «اتصال برقرار نشد» و تلاشِ دوباره بی کدِ تازه؛
 *   • موفق ⇒ ایمیلِ تازه در «حسابِ من»، نشست/بلیتِ همین دستگاه و پایگاه؛ نشستِ دستگاهِ دیگر باطل؛
 *   • انگلیسی از واژه‌نامهٔ همان زبان؛ راست‌چین، بی بیرون‌زدگی در ۳۶۰، بی نقضِ CSP، بی خطای جاوااسکریپت و (axe-core) بی خطای
 *     دسترس‌پذیریِ همان بخش.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

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

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@phone.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ شماره' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront']) await flag(key, true);
await flag('shop.storefront.web_account', false);
const slug = 'phone-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ شماره', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));

const OLD = 'old.buyer@store.local', TAKEN = 'taken.buyer@store.local', NEW = 'new.buyer@store.local';
const NAME = 'کریمِ آزمونی';
const ticket = async (e) => {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: e });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: e, code: vs.body.devCode });
  return { email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt };
};
const mine = await ticket(OLD);
const acc = await call('POST', `/api/store/${slug}/account`, { name: NAME }, null, { 'X-Store-Ticket': mine.ticket });
ok(acc.status === 201, 'حسابِ وبِ مشتری ساخته شد', JSON.stringify(acc.body).slice(0, 120));
//  دستگاهِ دیگرِ همین حساب و حسابِ دیگری با ایمیلِ TAKEN
const elsewhere = (await call('POST', `/api/store/${slug}/account`, {}, null, { 'X-Store-Ticket': (await ticket(OLD)).ticket })).body;
const taken = (await call('POST', `/api/store/${slug}/account`, {}, null, { 'X-Store-Ticket': (await ticket(TAKEN)).ticket })).body;
ok(elsewhere.customer.id === acc.body.customer.id && taken.customer.id !== acc.body.customer.id, 'دستگاهِ دوم و حسابِ دیگر آماده‌اند');
const row = async () => one('SELECT email, phone FROM store_customers WHERE id=$1', [acc.body.customer.id]);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const loads = [];
const starts = [];
const session = { token: acc.body.session, expiresAt: acc.body.expiresAt, email: acc.body.customer.email, phone: '', name: NAME };
const seed = (c) => c.addInitScript(([sess, tk]) => {
  if (localStorage.getItem('seeded')) return;
  localStorage.setItem('seeded', '1');
  localStorage.setItem('store-session', JSON.stringify(sess));
  localStorage.setItem('store-ticket', JSON.stringify(tk));
}, [session, mine]);
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await seed(ctx);
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
page.on('request', (r) => {
  if (/\/store\/phone\.js/.test(r.url())) loads.push(r.url());
  if (/\/verify\/start$/.test(r.url())) starts.push(r.postData());
});
const text = async (sel) => ((await page.textContent(sel)) || '').trim();
const note = () => text('#ph-box .note');
const openOrders = async (q = '') => {
  await page.goto(`${base}/store/${slug}?r=${Date.now()}${q}#/orders`);
  await page.waitForSelector('#view-orders:not([hidden])', { timeout: 15000 });
};
const waitNote = (re) => page.waitForFunction((src) => new RegExp(src).test((document.querySelector('#ph-box .note') || {}).textContent || ''),
  re.source, { timeout: 15000 });
const devCode = async () => ((await note()).match(/\d{6}/) || [])[0] || '';

//  ── ⛔ پرچمِ خاموش ⇒ نه بخش، نه phone.js، و درهای سرور همان درِ ناشناخته ──
await openOrders();
await page.waitForLoadState('networkidle');
ok(!(await page.$('#ph-box')), 'خاموش ⇒ بخشِ تماس/ایمیل نیست');
ok(loads.length === 0, 'خاموش ⇒ phone.js خواسته نشد', loads.join(' '));
const fresh0 = await ticket(NEW);
const headers0 = { 'X-Store-Session': acc.body.session, 'X-Store-Ticket': fresh0.ticket };
for (const door of ['phone', 'email']) {
  const off = await call('POST', `/api/store/${slug}/account/${door}`, { email: NEW, phone: '0790001122' }, null, headers0);
  const unknown = await call('POST', `/api/store/${slug}/account/no-such-door`, { email: NEW }, null, headers0);
  ok(off.status === unknown.status && JSON.stringify(off.body) === JSON.stringify(unknown.body), `خاموش ⇒ درِ ${door} همان درِ ناشناخته`, `${off.status}`);
}
ok((await row()).email === OLD, 'خاموش ⇒ ایمیل همان');

//  ── روشن ──
await flag('shop.storefront.web_account', true);
await openOrders();
await page.waitForSelector('#ph-box', { timeout: 15000 });
ok(loads.length > 0, 'روشن ⇒ phone.js دیربار آمد');
ok(await page.isHidden('#em-send'), 'بسته تا کاربر باز کند (details)');
ok((await text('#ph-box > summary')) === 'شمارهٔ تماس و ایمیل', 'عنوانِ بخش از واژه‌نامه', await text('#ph-box > summary'));
await page.click('#ph-box > summary');

//  شمارهٔ تماس: بی هیچ کدی
await page.fill('#ph-phone', '0793337788');
await page.click('#ph-save');
await page.waitForFunction(() => /شمارهٔ تماس/.test((document.getElementById('toast') || {}).textContent || ''), null, { timeout: 15000 });
ok(starts.length === 0, 'شمارهٔ تماس ⇒ هیچ کدی فرستاده نشد');
ok((await row()).phone === '+93793337788', 'شمارهٔ تماس در پایگاه', (await row()).phone);
await page.waitForSelector('#ph-box', { timeout: 15000 });
if (await page.isHidden('#em-send')) await page.click('#ph-box > summary');

//  ایمیلِ کنونی ⇒ پیام، بی کد
await page.fill('#em-email', OLD);
await page.click('#em-send');
await waitNote(/همان ایمیلِ کنونی/);
ok(starts.length === 0, 'ایمیلِ کنونی ⇒ هیچ کدی فرستاده نشد');
ok(await page.isHidden('#em-code'), 'کادرِ کد بسته ماند');

//  ایمیلِ حسابِ دیگر ⇒ کد، سپس «حسابِ جدا دارد»، بی تغییر
await page.fill('#em-email', TAKEN);
await page.click('#em-send');
await page.waitForSelector('#em-code:visible', { timeout: 15000 });
ok(starts.length === 1 && /taken\.buyer/.test(starts[0]), 'کد فقط برای ایمیلِ تازه فرستاده شد', starts.join(' '));
await page.fill('#em-code', await devCode());
await page.click('#em-ok');
await waitNote(/حسابِ جدا/);
ok((await row()).email === OLD, 'ایمیلِ حسابِ دیگر ⇒ ایمیل همان');
ok(await page.evaluate(() => JSON.parse(localStorage.getItem('store-ticket') || '{}').email) === mine.email, 'بلیتِ پیشینِ دستگاه برگشت');

//  ایمیلِ تازه: کدِ نادرست، سپس قطعِ شبکه، سپس تلاشِ دوباره بی کدِ تازه
await page.fill('#em-email', NEW);
await page.click('#em-send');
await waitNote(/\d{6}/);
const right = await devCode();
const wrong = right === '111111' ? '222222' : '111111';
await page.fill('#em-code', wrong);
await page.click('#em-ok');
await waitNote(/کد درست نیست/);
ok((await page.inputValue('#em-code')) === wrong, 'کدِ نادرست ⇒ کد در کادر ماند');
ok((await row()).email === OLD, 'کدِ نادرست ⇒ ایمیل همان');
await page.fill('#em-code', right);
let cut = true;
await page.route('**/account/email', (r) => (cut ? (cut = false, r.abort()) : r.continue()));
await page.click('#em-ok');
await waitNote(/اتصال برقرار نشد/);
ok((await row()).email === OLD, 'قطعِ شبکه ⇒ ایمیل همان');
ok(await page.isEnabled('#em-ok'), 'قطعِ شبکه ⇒ دکمه باز برای تلاشِ دوباره');
const startsBefore = starts.length;

if (axeSrc) {
  //  CSP ِ سختِ ویترین اسکریپتِ درون‌خطی را نمی‌پذیرد ⇒ همان صفحه (بخشِ باز با کادرِ کد) در زمینهٔ bypassCSP
  const ax = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  await seed(ax);
  const ap = await ax.newPage();
  await ap.goto(`${base}/store/${slug}#/orders`);
  await ap.waitForSelector('#ph-box', { timeout: 15000 });
  await ap.click('#ph-box > summary');
  await ap.fill('#em-email', 'axe.buyer@store.local');
  await ap.click('#em-send');
  await ap.waitForSelector('#em-code:visible', { timeout: 15000 });
  await ap.addScriptTag({ content: axeSrc });
  const v = await ap.evaluate(async () => (await window.axe.run('#ph-box',
    { runOnly: ['color-contrast', 'button-name', 'label', 'aria-allowed-attr', 'nested-interactive', 'autocomplete-valid'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  await ax.close();
  ok(v.length === 0, 'دسترس‌پذیری (axe-core) ِ بخشِ تماس/ایمیل', v.join(' | '));
} else if (SKIP) {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

ok(await page.evaluate(() => getComputedStyle(document.getElementById('ph-box')).direction) === 'rtl', 'راست‌چین');
ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'بی بیرون‌زدگی در ۳۶۰');
await page.click('#em-ok');
await page.waitForFunction((n) => (document.getElementById('ac-mine') || {}).textContent.includes(n), NEW, { timeout: 15000 });
ok(/ایمیلِ حساب.*شد/.test(await text('#toast')), 'پیامِ «ایمیلِ حساب … شد»', await text('#toast'));
ok(starts.length === startsBefore, 'تلاشِ دوباره بی کدِ تازه');
ok((await row()).email === NEW, 'ایمیل در پایگاه عوض شد');
const ls = await page.evaluate(() => [JSON.parse(localStorage.getItem('store-session') || '{}'), JSON.parse(localStorage.getItem('store-ticket') || '{}')]);
ok(ls[0].email === NEW && ls[0].token === acc.body.session, 'نشستِ همین دستگاه ماند و ایمیلش تازه شد');
ok(ls[1].email === NEW, 'بلیتِ دستگاه برای ایمیلِ تازه');
ok((await call('GET', `/api/store/${slug}/account`, null, null, { 'X-Store-Session': elsewhere.session })).status === 401, 'نشستِ دستگاهِ دیگر باطل شد');
ok((await call('GET', `/api/store/${slug}/account`, null, null, { 'X-Store-Session': acc.body.session })).status === 200, 'نشستِ همین دستگاه کار می‌کند');
ok(starts.every((x) => !/\+93|07\d{8}/.test(x)), 'هیچ کدی به شماره نرفت', starts.join(' '));

//  ── انگلیسی ──
await openOrders('&lang=en');
await page.waitForSelector('#ph-box', { timeout: 15000 });
ok((await text('#ph-box > summary')) === 'Contact number and email', 'انگلیسی از واژه‌نامهٔ همان زبان', await text('#ph-box > summary'));

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «شمارهٔ تماس و ایمیل» ِ حسابِ مشتری');
process.exit(bad ? 1 : 0);
