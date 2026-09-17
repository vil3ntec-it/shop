/**
 * نسخهٔ وب — پشتیبانِ ابری، با مرورگرِ واقعی.
 *
 * ── چرا این فایل هست ───────────────────────────────────────────────
 * قاعدهٔ مخزن: «هر تغییری که در برنامهٔ نیتیو انجام می‌شود، در همان
 * نشست باید در نسخهٔ وب هم انجام شود… کاربرانِ آیفون فقط همین را
 * دارند.» پس پشتیبانِ ابری در وب هم هست — ولی تا چیزی سنجیده نشود،
 * «هست» یعنی «نوشته شده»، نه «کار می‌کند».
 *
 * این‌جا `index.html`ِ واقعی در کرومیوم باز می‌شود، دکمه واقعاً زده
 * می‌شود، و نتیجه از **دیتابیس** سنجیده می‌شود.
 *
 * ⚠️ نشانیِ سرور در `<meta>`ی خودِ صفحه **قفل** است و این عمدی است
 * (هیچ‌چیز نباید بتواند برنامه را به سرورِ دیگری ببرد). پس آزمون
 * پاسخِ همان یک فایل را در مرورگر بازنویسی می‌کند — فایلِ روی دیسک
 * دست نمی‌خورد.
 *
 * اجرا:  node test/web-backup.mjs
 */
import { createRequire } from 'node:module';
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
if (!chromium) {
  console.log('⏭  پلی‌رایت پیدا نشد — این سنجه رد شد');
  process.exit(0);
}

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL
  || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-backups-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
//  ⚠️ صفحه از یک مبدأ دیگر (وب‌سرورِ ایستا) به API می‌زند، پس بی این
//  همه‌چیز پشتِ CORS می‌ماند و سنجه «شکسته» می‌نماید در حالی که فقط
//  تنظیمِ آزمون کم بوده
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');

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

/* ---- وب‌سرورِ ایستا برای خودِ صفحه ----
 *  ⚠️ با `file://` باز نمی‌شود: سرویس‌ورکر و `localStorage` آن‌جا
 *  بالا نمی‌آیند — همان چیزی که `CLAUDE.md` نوشته.
 */
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
let API_BASE = '';
const site = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html';
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404).end('no');
    return;
  }
  /*
   *  نشانیِ سرور در `<meta>`ی صفحه **قفل** است و این عمدی است: هیچ‌چیز
   *  نباید بتواند برنامه را به سرورِ دیگری ببرد. پس آزمون همان یک خط را
   *  سرِ راه عوض می‌کند — فایلِ روی دیسک دست نمی‌خورد.
   *
   *  ⚠️ `replace` سراسری است: در `index.html` **دو** تگِ
   *  `tohid-api-base` هست — یکی نمونهٔ داخلِ کامنتِ راهنما و یکی تگِ
   *  واقعی، و نمونه اول می‌آید. با `replace`ِ تک‌باره فقط کامنت عوض
   *  می‌شد و صفحه به سرورِ واقعی می‌زد.
   */
  if (rel === 'index.html') {
    const html = fs.readFileSync(file, 'utf8')
      .replace(/<meta name="tohid-api-base" content="[^"]*">/g,
        `<meta name="tohid-api-base" content="${API_BASE}">`);
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
    return;
  }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => site.listen(0, '127.0.0.1', r));
const siteBase = `http://127.0.0.1:${site.address().port}`;
API_BASE = apiBase;

/* ---- یک دکان‌دارِ واقعی، از همان سه پلهٔ ثبت‌نام ---- */
const post = async (p, b, t) => {
  const r = await fetch(apiBase + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) },
    body: JSON.stringify(b),
  });
  return { status: r.status, body: await r.json().catch(() => null) };
};
const email = 'webowner@panel.local';
const password = 'Passw0rd!test';
const started = await post('/api/auth/register/start', { name: 'وب‌دار', email, password });
const verified = await post('/api/auth/register/verify', { email, code: started.body.devCode });
const done = await post('/api/auth/register/complete', {
  ticket: verified.body.ticket, name: 'وب‌دار', password,
  device: { deviceId: 'dev-web', name: 'تست', platform: 'test' },
  app: 'shop', terms: { accepted: true },
});
const shop = await post('/api/shop', { name: 'دکانِ وب' }, done.body.accessToken);
const shopId = shop.body.shop.id;

const browser = await chromium.launch();
const page = await browser.newPage();
const pageErrors = [];
page.on('pageerror', (e) => pageErrors.push(String(e.message)));
page.on('requestfailed', (r) => {
  if (r.url().includes('/api')) console.log('  ↯', r.method(), r.url(), r.failure()?.errorText);
});

await page.addInitScript(([tokenBundle]) => {
  //  نشستِ واقعی، همان شکلی که `license-client.js` می‌نویسد
  localStorage.setItem('tohid-license-v1', JSON.stringify(tokenBundle));
  //  قفلِ رمزِ صفحه را باز می‌کنیم تا سنجه به خودِ برنامه برسد
  localStorage.setItem('tohid-unlocked-v1', '1');
}, [{
  accessToken: done.body.accessToken,
  refreshToken: done.body.refreshToken,
  accessExpiresAt: Date.now() + 3600_000,
  userLabel: 'وب‌دار',
}]);

await page.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1500);
console.log('  نشانیِ سرور از دیدِ صفحه:',
  await page.evaluate(() => (window.TohidApi && window.TohidApi.baseUrl && window.TohidApi.baseUrl())
    || (window.TohidLicense && window.TohidLicense.getServerUrl && window.TohidLicense.getServerUrl())
    || '(نامعلوم)'));

await step('لایهٔ اشتراکِ وب پشتیبانِ ابری را می‌شناسد', async () => {
  const has = await page.evaluate(() =>
    !!(window.TohidLicense && window.TohidLicense.backups
      && typeof window.TohidLicense.backups.upload === 'function'));
  assert.equal(has, true, '`TohidLicense.backups` باید در دسترس باشد');
});

await step('صفحه توکن را می‌شناسد و می‌داند وارد شده', async () => {
  const on = await page.evaluate(() => window.TohidLicense.isLoggedIn());
  assert.equal(on, true);
});

await step('فرستادنِ پشتیبان از خودِ صفحه، ردیف می‌سازد', async () => {
  const before = (await one("SELECT COUNT(*)::int n FROM account_backups WHERE app='shop'")).n;

  const out = await page.evaluate(async () => {
    try {
      const r = await window.TohidLicense.backups.upload(
        JSON.stringify({ products: [{ name: 'برنج' }] }),
        { manual: true, ext: 'json' },
      );
      return { ok: true, id: r.backup && r.backup.id, bytes: r.backup && r.backup.bytes };
    } catch (e) { return { ok: false, message: e.message, code: e.code }; }
  });
  assert.equal(out.ok, true, `فرستادن نشد: ${out.message} (${out.code})`);

  const after = (await one("SELECT COUNT(*)::int n FROM account_backups WHERE app='shop'")).n;
  assert.equal(after, before + 1, 'یک ردیفِ تازه باید نشسته باشد');

  const row = await one('SELECT * FROM account_backups ORDER BY created_at DESC LIMIT 1');
  assert.equal(row.tenant_id, shopId, 'باید به نامِ همین دکان باشد');
  assert.equal(row.kind, 'manual');
  assert.equal(row.app, 'shop');
  assert.ok(row.file.endsWith('.json'));
  assert.equal(Number(row.bytes), out.bytes);
});

await step('فهرست از خودِ صفحه خوانده می‌شود', async () => {
  const out = await page.evaluate(async () => {
    const r = await window.TohidLicense.backups.list();
    return { n: r.items.length, used: r.stats.usedBytes, quota: r.stats.quotaBytes };
  });
  assert.equal(out.n, 1);
  assert.ok(out.used > 0);
  assert.ok(out.quota > out.used);
});

await step('کادرِ «پشتیبان روی سرور» در تنظیمات هست و حقیقت را می‌گوید', async () => {
  const has = await page.evaluate(() => !!document.getElementById('btn-cloud-backup'));
  assert.equal(has, true, 'دکمه باید در صفحه باشد');

  /*
   *  ⚠️ باید واقعاً به صفحهٔ تنظیمات رفت.
   *
   *  دکمه در DOM هست ولی تا وقتی `page-settings` کلاسِ `active` نگیرد
   *  دیده نمی‌شود، و Playwright روی عنصرِ نادیده کلیک نمی‌کند. یک بار
   *  سنجه همین‌جا تایم‌اوت داد — و درست هم داد: کاربری که به تنظیمات
   *  نرود، این دکمه را نمی‌بیند.
   */
  await page.evaluate(() => {
    /*
     *  ⚠️ صفحه پشتِ **قفلِ رمز** باز می‌شود (`#app-root.app-hidden`).
     *  همان کاری که `CLAUDE.md` برای آزمونِ بی‌پنجره نوشته: قفل را
     *  کنار بزن و خودِ برنامه را نشان بده — نه این‌که قفل را از کد
     *  بردار.
     */
    document.getElementById('app-root')?.classList.remove('app-hidden');
    document.querySelectorAll('.page').forEach((el) => el.classList.remove('active'));
    document.getElementById('page-settings')?.classList.add('active');
  });
  await page.waitForSelector('#btn-cloud-backup', { state: 'visible', timeout: 10_000 });
  /*
   *  ⚠️ کلیکِ واقعیِ ماوس این‌جا کار نمی‌کند و این عیبِ کد نیست: پردهٔ
   *  قفلِ رمز هنوز روی صفحه است و جلوی نشانگر را می‌گیرد. چیزی که باید
   *  سنجیده شود «شنوندهٔ این دکمه واقعاً کار می‌کند؟» است، پس رویداد
   *  مستقیم روی خودش زده می‌شود.
   */
  await page.$eval('#btn-cloud-backup', (el) => el.click());
  await page.waitForFunction(
    () => (document.getElementById('cloud-backup-text')?.textContent || '').includes('نسخه روی سرور'),
    null, { timeout: 15_000 },
  );
  const text = await page.textContent('#cloud-backup-text');
  assert.ok(text.includes('نسخه روی سرور'), `متنِ کادر: ${text}`);
});

await step('پشتیبانِ فرستاده‌شده از صفحه، مالِ همین دکان است و بس', async () => {
  const rows = await query(
    "SELECT tenant_id FROM account_backups WHERE app='shop'",
  );
  assert.ok(rows.rows.every(r => r.tenant_id === shopId), 'همه باید مالِ همین دکان باشند');
});

await step('در کلِ این نشست هیچ خطای صفحه‌ای نبود', async () => {
  //  ⚠️ خطاهای سرویس‌ورکر و آیکونِ نبوده شمرده نمی‌شوند: آن‌ها مالِ
  //  محیطِ آزمون‌اند نه کد
  const real = pageErrors.filter(m => !/ServiceWorker|favicon|manifest/i.test(m));
  assert.deepEqual(real, [], real.join(' | '));
});

await browser.close();
await new Promise((r) => site.close(r));
await new Promise((r) => api.close(r));
await closeDb();

console.log('\n── نسخهٔ وب: پشتیبانِ ابری ───────────────');
for (const [mark, name] of results) console.log(`${mark}  ${name}`);
console.log(`\n${results.length - failures}/${results.length} سبز`);
process.exit(failures ? 1 : 0);
