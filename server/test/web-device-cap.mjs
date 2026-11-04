/**
 * O-09 / SUB-09 در نسخهٔ وب با مرورگرِ واقعی — صاحبِ دکان از مرورگرِ تازه، وقتی جاهای دستگاهِ دکان پر است.
 *
 *   ۱) ورودِ با رمز (`TohidLicense.login`، همان که فرمِ ورودِ `index.html` صدا می‌زند) دستگاهِ همین مرورگر را
 *      می‌گوید ⇒ سرور ثبتش می‌کند ⇒ `license/sync` مجوز می‌دهد، صندوق باز است و نوارِ زردِ
 *      «۳ دستگاه با اشتراکِ ۲ دستگاهی» بی بارِ دوبارهٔ صفحه بالای صفحه می‌آید؛ دکمه‌اش «اشتراکِ من» را با
 *      فهرستِ دستگاه‌ها باز می‌کند. تا ۲.۱۴.۵ همین‌جا `device_limit` بود و صندوق قفل.
 *   ۲) SEC-CLI-01: همان نشست روی مرورگرِ دیگر (شناسهٔ خودش، ثبت‌نشده) ⇒ قفل، بی نوار.
 *   ۳) نوار در ۳۲۰ پیکسل بیرون نمی‌زند؛ بی خطای جاوااسکریپت.
 *
 * منطقِ بی مرورگر (همان license-client.js در vm): `test/sec-sub-device-cap-web.test.js`.
 * اجرا:  TEST_DATABASE_URL=pglite:memory node test/web-device-cap.mjs
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const require = createRequire(import.meta.url);
let chromium = null;
for (const p of [(() => { try { return `${execSync('npm root -g', { encoding: 'utf8' }).trim()}/playwright`; } catch { return ''; } })(), 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-devcap-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');

let failures = 0;
async function step(name, fn) {
  try { await fn(); console.log(`✅  ${name}`); }
  catch (err) { failures++; console.log(`❌  ${name}\n    ${err.message}`); }
}

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
const app = await createApp({ runMigrations: false });
const api = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const apiBase = `http://127.0.0.1:${api.address().port}`;

/* ---- index.html با نشانیِ همین سرور (همان کارِ web-vip-gate.mjs) ---- */
const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const site = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html';
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404).end('no'); return; }
  if (rel === 'index.html') {
    const src = fs.readFileSync(file, 'utf8');
    const real = (src.replace(/<!--[\s\S]*?-->/g, '').match(/<meta name="tohid-api-base" content="([^"]+)">/) || [])[1];
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(src
      .replace(/<meta name="tohid-api-base" content="[^"]*">/g, `<meta name="tohid-api-base" content="${apiBase}">`)
      .replace(/(<meta http-equiv="Content-Security-Policy" content="[^"]*?)(connect-src [^;"]*)/, (_, head, d) => head + d
        .split(real.replace(/^https:/, 'wss:')).join(apiBase.replace(/^http/, 'ws')).split(real).join(apiBase)));
    return;
  }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => site.listen(0, '127.0.0.1', r));
const siteBase = `http://127.0.0.1:${site.address().port}`;

const call = async (method, p, b, t) => {
  const r = await fetch(apiBase + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) }, body: b ? JSON.stringify(b) : undefined });
  return { status: r.status, body: await r.json().catch(() => null) };
};
const PASSWORD = 'Passw0rd!test';
async function person(email, name) {
  const s = await call('POST', '/api/auth/register/start', { name, email, password: PASSWORD });
  const v = await call('POST', '/api/auth/register/verify', { email, code: s.body.devCode });
  const d = await call('POST', '/api/auth/register/complete', {
    ticket: v.body.ticket, name, password: PASSWORD, app: 'shop', terms: { accepted: true },
    device: { deviceId: `reg-${email.split('@')[0]}`, name: 'گوشی', platform: 'android' },
  });
  assert.equal(d.status, 201, JSON.stringify(d.body));
  return { email, token: d.body.accessToken, user: d.body.user };
}

/* ---- دکانِ پر: اشتراکِ ۲ دستگاهی = گوشیِ ثبت‌نامِ صاحب + گوشیِ کارمند؛ کلیدِ سقف روشن ---- */
await query(
  `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
   VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
  [newId('adm'), await require('../src/lib/password').hashPassword('Admin!12345'), now()]);
const admin = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const owner = await person('devcap-owner@panel.local', 'صاحب');
const shop = (await call('POST', '/api/shop', { name: 'دکانِ پر' }, owner.token)).body.shop;
assert.equal((await call('POST', '/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days: 30, maxDevices: 2 }, admin)).status, 201);
const staff = await person('devcap-staff@panel.local', 'کارمند');
await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
             VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), shop.id, staff.user.id, now()]);
assert.equal((await call('PUT', '/api/admin/pump/live-config', { scope: shop.id, key: 'shop.device_cap', value: true }, admin)).status, 200);

const browser = await chromium.launch();
const pageErrors = [];
/** مرورگرِ تازه: بی نشست، از پشتِ صفحهٔ ورود گذشته (ورود از خودِ صفحه با رمز) */
async function open(bundle, viewport = { width: 1280, height: 800 }) {
  const ctx = await browser.newContext({ viewport, serviceWorkers: 'block' });
  await ctx.addInitScript(([b]) => {
    if (localStorage.getItem('seeded')) return;
    localStorage.setItem('seeded', '1');
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    if (b) localStorage.setItem('tohid-license-v1', JSON.stringify(b));
  }, [bundle || null]);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => pageErrors.push(String(e.message)));
  await page.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => window.TohidVip && window.TohidLicense && typeof window.goTo === 'function', null, { timeout: 20000 });
  return { ctx, page };
}
const salesLocked = (page) => page.evaluate(() => { window.goTo('quick-sale'); return !!document.querySelector('#page-quick-sale > .vip-overlay'); });

let session = null;
await step('صاحب از مرورگرِ تازه در دکانِ پر با رمز وارد می‌شود ⇒ صندوق باز و نوارِ زردِ سقفِ دستگاه، بی بارِ دوباره', async () => {
  const { ctx, page } = await open(null);
  await page.evaluate(([e, p]) => window.TohidLicense.login(e, p), [owner.email, PASSWORD]);
  await page.waitForSelector('#vip-device-bar', { state: 'visible', timeout: 10000 })
    .catch(async () => { throw new Error(`نوارِ سقفِ دستگاه نیامد؛ ردِ سرور: «${await page.evaluate(() => window.TohidLicense.deniedCode())}»`); });
  const text = await page.locator('#vip-device-bar').innerText();
  assert.match(text, /3 دستگاه با اشتراکِ 2 دستگاهی/);
  assert.match(text, /دستگاهِ شما همیشه کار می‌کند/);
  assert.equal(await page.evaluate(() => window.TohidLicense.deniedCode()), '');
  await page.waitForFunction(() => window.TohidVip.hasFeature('sales'), null, { timeout: 8000 });
  assert.equal(await salesLocked(page), false, 'صاحب روی مرورگرِ خودش قفل شد');

  //  راهِ برداشتن: «اشتراکِ من» با فهرستِ دستگاه‌ها، همین مرورگر نشان‌دار
  await page.click('#vip-device-bar button');
  await page.waitForSelector('#acct-plan.open #pl-devices .acct-row', { timeout: 10000 });
  assert.match(await page.locator('#pl-devices').innerText(), /همین دستگاه/);
  session = await page.evaluate(() => JSON.parse(localStorage.getItem('tohid-license-v1')));
  await ctx.close();
});

await step('SEC-CLI-01: همان نشست روی مرورگرِ دیگر (شناسهٔ ثبت‌نشده) ⇒ قفل، بی نوارِ صاحب', async () => {
  assert.ok(session && session.accessToken, 'گامِ پیش نشستی نداد');
  const { ctx, page } = await open({ accessToken: session.accessToken, refreshToken: session.refreshToken,
    accessExpiresAt: session.accessExpiresAt, userId: session.userId, userLabel: 'صاحب' });
  await page.waitForFunction(() => window.TohidLicense.deniedCode() === 'device_limit', null, { timeout: 10000 })
    .catch(async () => { throw new Error(`شناسهٔ ثبت‌نشده با نشستِ کپی‌شده رد نشد (${await page.evaluate(() => window.TohidLicense.deniedCode())})`); });
  assert.equal(await page.evaluate(() => window.TohidVip.hasFeature('sales')), false);
  assert.equal(await page.locator('#vip-device-bar').count(), 0);
  await ctx.close();
});

await step('نوار در ۳۲۰ پیکسل بیرون نمی‌زند', async () => {
  const { ctx, page } = await open(null, { width: 320, height: 700 });
  await page.evaluate(([e, p]) => window.TohidLicense.login(e, p), [owner.email, PASSWORD]);
  await page.waitForSelector('#vip-device-bar', { state: 'visible', timeout: 10000 });
  const out = await page.evaluate(() => {
    const b = document.getElementById('vip-device-bar').getBoundingClientRect();
    return { left: b.left, right: b.right, vw: document.documentElement.clientWidth };
  });
  assert.ok(out.left >= 0 && out.right <= out.vw, `بیرون‌زدگی: ${JSON.stringify(out)}`);
  await ctx.close();
});

await step('بی خطای جاوااسکریپت', async () => { assert.deepEqual(pageErrors, []); });

await browser.close();
site.close();
api.close();
await closeDb();
console.log(failures ? `\n❌ ${failures} سنجه قرمز` : '\n✅ سقفِ دستگاه در وب: صاحب هرگز بسته نمی‌شود، شناسهٔ ساختگی رد');
process.exit(failures ? 1 : 0);
