/**
 * نسخهٔ وب — ماندنِ نشست پس از تازه‌سازی و قطع و وصلِ اینترنت، با مرورگرِ واقعی.
 *
 * ── چرا این فایل هست (بندِ ۲۲.۲ مرحلهٔ A۱، ۲۳.۴ و ۲۹.۲ سندِ v3) ─────
 * «ثبت‌نام/ورود و باقی‌ماندنِ نشست پس از بستن و بازکردنِ برنامه یا قطع و
 * وصلِ اینترنت.» سرور توکنِ تازه‌سازی را در هر `/auth/refresh` می‌چرخاند و
 * توکنِ قبلی را — اگر بیرون از پنجرهٔ ارفاق برگردد — «دزدی» می‌گیرد و کلِ
 * نشستِ آن دستگاه را می‌بندد (`refresh_reused`).
 *
 * سه لایهٔ وب (`license-client.js`، `shop-sync.js`، `account-code.js`) هر
 * کدام جدا تازه می‌کردند؛ دو تای اول توکنِ چرخیده را دور می‌ریختند، پس
 * دومین تازه‌سازی کاربر را بیرون می‌انداخت. و `account-code.js` با **هر**
 * شکستِ تازه‌سازی — حتی قطعیِ اینترنت — نشست را پاک می‌کرد.
 *
 * این‌جا `index.html`ِ واقعی در کرومیوم باز می‌شود، توکن‌ها در دیتابیس
 * پیر می‌شوند و نتیجه هم از `localStorage` و هم از جدولِ `tokens` سنجیده
 * می‌شود.
 *
 * اجرا:  TEST_DATABASE_URL=pglite:memory node test/web-session.mjs
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

function candidates() {
  const out = [];
  try { out.push(`${execSync('npm root -g', { encoding: 'utf8' }).trim()}/playwright`); } catch { /* نبود */ }
  out.push('playwright');
  return out;
}
let chromium = null;
for (const p of candidates()) {
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL
  || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-session-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, one, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const { hashToken } = require('../src/lib/tokens');

const ROOT = path.join(import.meta.dirname, '..', '..');

let failures = 0;
const results = [];
async function step(name, fn) {
  try { await fn(); results.push(['✅', name]); }
  catch (err) { failures++; results.push(['❌', `${name} — ${err.message}`]); }
}

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();

const app = await createApp({ runMigrations: false });
const api = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const apiBase = `http://127.0.0.1:${api.address().port}`;

/* ---- وب‌سرورِ ایستا — همان شیوهٔ `web-backup.mjs` (نشانیِ سرور در `<meta>` و CSP) ---- */
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const site = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html';
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404).end('no');
    return;
  }
  if (rel === 'index.html') {
    const src = fs.readFileSync(file, 'utf8');
    const real = (src.replace(/<!--[\s\S]*?-->/g, '').match(/<meta name="tohid-api-base" content="([^"]+)">/) || [])[1];
    const html = src
      .replace(/<meta name="tohid-api-base" content="[^"]*">/g,
        `<meta name="tohid-api-base" content="${apiBase}">`)
      .replace(/(<meta http-equiv="Content-Security-Policy" content="[^"]*?)(connect-src [^;"]*)/, (_, head, d) => head + d
        .split(real.replace(/^https:/, 'wss:')).join(apiBase.replace(/^http/, 'ws'))
        .split(real).join(apiBase));
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
    return;
  }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => site.listen(0, '127.0.0.1', r));
const siteBase = `http://127.0.0.1:${site.address().port}`;

/* ---- یک دکان‌دارِ واقعی، از همان سه پلهٔ ثبت‌نام ---- */
const post = async (p, b, t) => {
  const r = await fetch(apiBase + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) },
    body: JSON.stringify(b),
  });
  return { status: r.status, body: await r.json().catch(() => null) };
};
const email = 'websession@panel.local';
const password = 'Passw0rd!test';
const started = await post('/api/auth/register/start', { name: 'نشست‌دار', email, password });
const verified = await post('/api/auth/register/verify', { email, code: started.body.devCode });
const done = await post('/api/auth/register/complete', {
  ticket: verified.body.ticket, name: 'نشست‌دار', password,
  device: { deviceId: 'dev-web-session', name: 'تست', platform: 'test' },
  app: 'shop', terms: { accepted: true },
});
assert.equal(done.status, 201, JSON.stringify(done.body));
await post('/api/shop', { name: 'دکانِ نشست' }, done.body.accessToken);
const userId = done.body.user.id;

/**
 *  «یک ساعت گذشت»: توکن‌های دسترسیِ این شخص در سرور می‌میرند و پنجرهٔ ارفاقِ
 *  هر توکنِ تازه‌سازیِ چرخیده هم تمام می‌شود — یعنی توکنِ کهنه از این پس
 *  «دزدی» است، همان‌طور که در دنیای واقعی پس از سی ثانیه.
 */
async function anHourLater() {
  await query('UPDATE tokens SET expires_at=$1 WHERE kind=$2 AND subject_id=$3', [Date.now() - 1000, 'access', userId]);
  await query('UPDATE tokens SET grace_until=$1 WHERE grace_until IS NOT NULL AND subject_id=$2', [Date.now() - 1000, userId]);
}
async function refreshRow(raw) {
  return one('SELECT revoked_at, rotated_to FROM tokens WHERE token_hash=$1 AND kind=$2', [hashToken(raw || ''), 'refresh']);
}

const browser = await chromium.launch();
const page = await browser.newPage();
const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(String(e.message)));

await page.addInitScript(([tokenBundle]) => {
  //  فقط بارِ اول — وگرنه هر بار باز کردنِ صفحه نشستِ کهنه را برمی‌گرداند
  if (!localStorage.getItem('tohid-license-v1')) localStorage.setItem('tohid-license-v1', JSON.stringify(tokenBundle));
  localStorage.setItem('tohid-unlocked-v1', '1');
}, [{
  accessToken: done.body.accessToken,
  refreshToken: done.body.refreshToken,
  accessExpiresAt: Date.now() + 3600_000,
  userId,
  userLabel: 'نشست‌دار',
}]);

await page.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
await page.waitForFunction(() => window.TohidLicense && window.TohidShop && window.TohidAccount, null, { timeout: 15000 });
await page.waitForTimeout(1500);   //  کارهای خودکارِ آغازِ صفحه (تپش، همگام‌سازی) بنشینند

const stored = () => page.evaluate(() => JSON.parse(localStorage.getItem('tohid-license-v1') || '{}'));
/** تابع در خودِ صفحه می‌دود (رشته، تا CSPِ صفحه `eval` نخواهد) */
async function inPage(fn) {
  return page.evaluate(`(async () => {
    try { await (${fn})(); return { ok: true }; }
    catch (e) { return { ok: false, code: e && e.code, status: e && e.status, message: e && e.message }; }
  })()`);
}

/* ------------------------------------------------------------------ */

for (const [label, call] of [
  ['license-client (`TohidLicense.backups.list`)', () => window.TohidLicense.backups.list()],
  ['shop-sync (`TohidShop.apiAuth`)', () => window.TohidShop.apiAuth('/api/v1/shop/me')],
  ['account-code (`TohidAccount.authFetch`)', () => window.TohidAccount.authFetch('/api/me/heartbeat')],
]) {
  await step(`${label}: دو تازه‌سازیِ پشتِ سرِ هم ⇒ کاربر وارد می‌ماند و توکنِ زنده ذخیره است`, async () => {
    for (let round = 1; round <= 2; round++) {
      await anHourLater();
      const out = await inPage(call);
      assert.equal(out.ok, true, `دورِ ${round}: ${out.message} (${out.code})`);
      const s = await stored();
      assert.ok(s.accessToken, `دورِ ${round}: توکنِ دسترسی ماند`);
      const row = await refreshRow(s.refreshToken);
      assert.ok(row, `دورِ ${round}: توکنِ تازه‌سازیِ ذخیره‌شده در سرور هست`);
      assert.equal(row.revoked_at, null, `دورِ ${round}: توکنِ ذخیره‌شده باید همان حلقهٔ زنده باشد، نه توکنِ چرخیدهٔ کهنه`);
    }
  });
}

await step('بستن و باز کردنِ دوبارهٔ صفحه ⇒ همان نشست، بی ورودِ دوباره', async () => {
  await anHourLater();
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => window.TohidAccount, null, { timeout: 15000 });
  assert.equal(await page.evaluate(() => window.TohidLicense.isLoggedIn()), true);
  const out = await inPage(() => window.TohidAccount.authFetch('/api/me/heartbeat'));
  assert.equal(out.ok, true, `${out.message} (${out.code})`);
});

for (const [label, fulfil] of [
  ['قطعِ اینترنت', (route) => route.abort('internetdisconnected')],
  ['صفحهٔ HTMLِ وای‌فای/اپراتور (۲۰۰ ولی نه JSON)', (route) => route.fulfill({ status: 200, contentType: 'text/html', body: '<html>لطفاً وارد شوید</html>' })],
  ['سقفِ نرخ (۴۲۹)', (route) => route.fulfill({ status: 429, contentType: 'application/json', body: '{"error":{"code":"rate_limited","message":"زیاد"}}' })],
  ['خطای سرور (۵۰۲)', (route) => route.fulfill({ status: 502, contentType: 'text/html', body: 'Bad Gateway' })],
]) {
  await step(`${label} وسطِ تازه‌سازی ⇒ نشست پاک نمی‌شود و پس از وصل شدن کار می‌کند`, async () => {
    await page.route('**/auth/refresh', fulfil);
    try {
      await anHourLater();
      const before = await stored();
      for (const call of [
        () => window.TohidAccount.authFetch('/api/me/heartbeat'),
        () => window.TohidLicense.backups.list(),
        () => window.TohidShop.apiAuth('/api/v1/shop/me'),
      ]) {
        const out = await inPage(call);
        assert.equal(out.ok, false, 'بی پاسخِ سالم، درخواست نباید «موفق» وانمود شود');
        const s = await stored();
        assert.equal(s.refreshToken, before.refreshToken, `توکنِ تازه‌سازی ماند (${out.code})`);
        assert.ok(s.accessToken, `توکنِ دسترسی پاک نشد (${out.code})`);
      }
      assert.equal(await page.evaluate(() => window.TohidLicense.isLoggedIn()), true, 'کاربر هنوز وارد است');
    } finally {
      await page.unroute('**/auth/refresh');
    }
    const back = await inPage(() => window.TohidAccount.authFetch('/api/me/heartbeat'));
    assert.equal(back.ok, true, `پس از وصل شدن: ${back.message} (${back.code})`);
  });
}

await step('نشستی که سرور واقعاً بسته (خروج از همهٔ دستگاه‌ها) ⇒ پاک می‌شود — رفتارِ درست سرِ جایش است', async () => {
  const s = await stored();
  const out1 = await post('/api/auth/logout-all', {}, s.accessToken);
  assert.equal(out1.status, 200, JSON.stringify(out1.body));
  const out = await inPage(() => window.TohidAccount.authFetch('/api/me/heartbeat'));
  assert.equal(out.ok, false);
  assert.equal(out.status, 401);
  const after = await stored();
  assert.ok(!after.accessToken && !after.refreshToken, 'نشستِ مرده باید از حافظه برود');
});

await step('صفحه بی خطای جاوااسکریپت ماند', async () => {
  assert.deepEqual(pageErrors, []);
});

await browser.close();
api.close();
site.close();
await closeDb?.();

for (const [mark, line] of results) console.log(`${mark} ${line}`);
console.log(failures ? `\n${failures} مورد شکست خورد` : '\nهمه سبز');
process.exit(failures ? 1 : 0);
