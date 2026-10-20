/**
 * قفلِ اشتراکِ نسخهٔ وب (`license/vip.js`) با سرورِ واقعی — سه باگ که سناریوی
 * سرتاسری (test/e2e/sync.e2e.mjs) روی main پیدا کرد:
 *
 *   ۱) سرور در `/api/v1/billing/status` دسترسی را **تخت** می‌دهد (`features`، `trial`،
 *      `source` …) ولی `vip.js` فقط `data.entitlement` را می‌خواند ⇐ هیچ‌وقت عوض نمی‌شد و
 *      «مهمان» می‌ماند: صندوق، قرض‌داران و بارکد برای دکانِ واردشده با دورهٔ آزمایشیِ
 *      فعال هم قفل بود («این بخش نیازمند اشتراک است»).
 *   ۲) `refresh()` ⇐ `TohidLicense.sync()` ⇐ `tohid:license-change` ⇐ `refresh()` …
 *      حلقهٔ بی‌پایان: هر برگهٔ واردشده چند درخواست در ثانیه به `billing/status` و
 *      `license/sync` می‌زد تا سقفِ درخواستِ سرور پر شود (و آن‌وقت همگام‌سازی هم ۴۲۹).
 *   ۳) پس از «ورود با ایمیل» هیچ‌کس `refresh` نمی‌کرد ⇐ تا بارِ دوبارهٔ صفحه همان قفل و
 *      همان «حساب رایگان بسازید» در سربرگ.
 *
 * و قفل هنوز سرِ جایش است: دکانی که دورهٔ آزمایشی‌اش تمام شده و اشتراک ندارد، قفل می‌ماند.
 *
 * اجرا:  TEST_DATABASE_URL=pglite:memory node test/web-vip-gate.mjs
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
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-vip-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, one, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const codes = require('../src/lib/login-codes');

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

/* ---- index.html با نشانیِ همین سرور (همان کارِ web-session.mjs) ---- */
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

const post = async (p, b, t) => {
  const r = await fetch(apiBase + p, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) }, body: JSON.stringify(b) });
  return { status: r.status, body: await r.json().catch(() => null) };
};
async function owner(email, shopName) {
  const password = 'Passw0rd!test';
  const s = await post('/api/auth/register/start', { name: 'صاحب', email, password });
  const v = await post('/api/auth/register/verify', { email, code: s.body.devCode });
  const d = await post('/api/auth/register/complete', {
    ticket: v.body.ticket, name: 'صاحب', password, app: 'shop', terms: { accepted: true },
    device: { deviceId: `reg-${email.split('@')[0]}`, name: 'تست', platform: 'test' },
  });
  assert.equal(d.status, 201, JSON.stringify(d.body));
  const shop = await post('/api/shop', { name: shopName }, d.body.accessToken);
  return { email, token: d.body.accessToken, refreshToken: d.body.refreshToken, user: d.body.user, shopId: shop.body.shop.id };
}
const trial = await owner('vip-trial@panel.local', 'دکانِ آزمایشی');
const expired = await owner('vip-expired@panel.local', 'دکانِ تمام‌شده');
//  دورهٔ آزمایشیِ دکان از `created_at` ِ خودش است — یک ماه عقب ⇒ تمام‌شده، بی اشتراک
await query('UPDATE shops SET created_at=$1 WHERE id=$2', [Date.now() - 30 * 86400_000, expired.shopId]);

const browser = await chromium.launch();
const pageErrors = [];
async function open(acct) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, serviceWorkers: 'block' });
  await ctx.addInitScript(([bundle]) => {
    if (localStorage.getItem('seeded')) return;
    localStorage.setItem('seeded', '1');
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    if (bundle) localStorage.setItem('tohid-license-v1', JSON.stringify(bundle));
  }, [acct ? { accessToken: acct.token, refreshToken: acct.refreshToken, accessExpiresAt: Date.now() + 3600_000, userId: acct.user.id, userLabel: 'صاحب' } : null]);
  const page = await ctx.newPage();
  const seen = [];
  page.on('pageerror', (e) => pageErrors.push(String(e.message)));
  page.on('request', (r) => { const p = new URL(r.url()).pathname; if (p === '/api/v1/billing/status' || p === '/api/v1/license/sync') seen.push(p); });
  await page.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => window.TohidVip && typeof window.goTo === 'function', null, { timeout: 20000 });
  return { ctx, page, seen };
}
const salesLocked = (page) => page.evaluate(() => {
  window.goTo('quick-sale');
  return !!document.querySelector('#page-quick-sale > .vip-overlay');
});

await step('دکانِ واردشده با دورهٔ آزمایشیِ فعال: صندوق و قرض‌داران باز است', async () => {
  const { ctx, page } = await open(trial);
  await page.waitForFunction(() => window.TohidVip.hasFeature('sales'), null, { timeout: 8000 })
    .catch(() => { throw new Error('پس از ۸ ثانیه صندوق هنوز قفل است (دسترسیِ سرور خوانده نشد)'); });
  assert.equal(await page.evaluate(() => window.TohidVip.entitlement.source), 'trial');
  assert.equal(await page.evaluate(() => window.TohidVip.hasFeature('debtors')), true);
  assert.equal(await salesLocked(page), false, 'روی صفحهٔ فروش هنوز قفلِ اشتراک نشسته است');
  assert.doesNotMatch(await page.locator('#vip-badge').innerText(), /حساب رایگان بسازید/);
  await ctx.close();
});

await step('بی حلقه: در شش ثانیه فقط چند درخواستِ billing/status و license/sync، نه ده‌ها', async () => {
  const { ctx, page, seen } = await open(trial);
  await page.waitForTimeout(6000);
  const n = seen.length;
  assert.ok(n <= 4, `${n} درخواست در شش ثانیه (${seen.filter((p) => p.includes('billing')).length} billing/status) — حلقهٔ refresh ⇄ license-change`);
  //  و رویدادِ «مجوز عوض شد» ِ بعدی (مثلاً فعال‌سازی) هنوز یک بار تازه می‌کند
  const before = seen.length;
  await page.evaluate(() => document.dispatchEvent(new CustomEvent('tohid:license-change')));
  await page.waitForTimeout(1500);
  const extra = seen.slice(before).filter((p) => p.includes('billing')).length;
  assert.equal(extra, 1, `پس از یک رویداد باید یک بار پرسیده شود، نه ${extra}`);
  await ctx.close();
});

await step('ورود با ایمیل از خودِ صفحه ⇒ بی بارِ دوباره، قفل برداشته و سربرگ درست می‌شود', async () => {
  const { ctx, page } = await open(null);
  assert.equal(await salesLocked(page), true, 'مهمان: صندوق باید قفل باشد');
  await page.evaluate(() => window.TohidAccountUI.openLogin());
  await page.fill('#lg-email', trial.email);
  await page.click('#lg-send');
  await page.waitForSelector('#lg-step-code:not([hidden])', { timeout: 10000 });
  const row = await one(`SELECT code_sealed FROM login_requests WHERE email=$1 ORDER BY created_at DESC LIMIT 1`, [trial.email]);
  const code = codes.unseal(row.code_sealed);
  await page.locator('#lg-boxes .code-box').first().focus();
  await page.keyboard.type(code, { delay: 30 });
  await page.waitForFunction(() => window.TohidAccount.isSignedIn(), null, { timeout: 10000 });
  await page.waitForFunction(() => window.TohidVip.hasFeature('sales'), null, { timeout: 8000 })
    .catch(() => { throw new Error('پس از ورود، صندوق تا بارِ دوبارهٔ صفحه قفل ماند'); });
  assert.equal(await salesLocked(page), false);
  assert.doesNotMatch(await page.locator('#vip-badge').innerText(), /حساب رایگان بسازید/);
  await ctx.close();
});

await step('قفل سرِ جایش است: دکانی که آزمایشی‌اش تمام شده و اشتراک ندارد', async () => {
  const { ctx, page } = await open(expired);
  await page.waitForFunction(() => window.TohidVip.entitlement.source !== 'guest', null, { timeout: 8000 })
    .catch(() => { throw new Error('پاسخِ سرور هرگز به قفل نرسید (همان «مهمان» ماند)'); });
  assert.equal(await page.evaluate(() => window.TohidVip.hasFeature('sales')), false);
  assert.equal(await salesLocked(page), true);
  assert.match(await page.locator('#page-quick-sale > .vip-overlay').innerText(), /دوره آزمایشی شما به پایان رسیده/);
  await ctx.close();
});

await step('بی خطای جاوااسکریپت', async () => { assert.deepEqual(pageErrors, []); });

await browser.close();
site.close();
api.close();
await closeDb();
console.log(failures ? `\n❌ ${failures} سنجه قرمز` : '\n✅ قفلِ اشتراکِ وب: درست باز، درست بسته، بی حلقه');
process.exit(failures ? 1 : 0);
