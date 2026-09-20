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

/* ══════════════════════════════════════════════════════════
   Sync v1 و ورود با کدِ شش‌رقمی — بندِ ۲۱، در کرومیومِ واقعی
   ----------------------------------------------------------
   ⚠️ هر بند از **دیتابیس** سنجیده می‌شود، نه از متنِ روی صفحه.
   ══════════════════════════════════════════════════════════ */

await step('لایه‌های Sync v1 در صفحه بالا آمده‌اند', async () => {
  const out = await page.evaluate(() => ({
    core: !!window.TohidSyncCore,
    account: !!window.TohidAccount,
    sync: !!window.TohidSync,
    ui: !!window.TohidAccountUI,
    schema: window.TohidSyncCore && window.TohidSyncCore.SCHEMA_VERSION,
    ulid: window.TohidSyncCore ? window.TohidSyncCore.ulid().length : 0,
  }));
  assert.equal(out.core, true, 'sync-core.js');
  assert.equal(out.account, true, 'account-code.js');
  assert.equal(out.sync, true, 'sync-engine.js');
  assert.equal(out.ui, true, 'account-ui.js');
  assert.equal(out.schema, 2, 'نسخهٔ schema باید با سرور یکی باشد');
  assert.equal(out.ulid, 26, 'شناسهٔ ردیف باید ULID باشد');
});

await step('چراغِ همگام‌سازی در نوارِ بالا هست و رنگ دارد', async () => {
  await page.waitForSelector('#sync-dot-btn', { timeout: 10_000 });
  const dot = await page.getAttribute('#sync-dot-btn', 'data-dot');
  assert.ok(['green', 'yellow', 'grey', 'red'].includes(dot), `رنگِ چراغ: ${dot}`);
});

await step('ثبتِ یک قرض‌دار از خودِ فرم ⇒ op ⇒ ردیف روی سرور', async () => {
  await page.evaluate(() => {
    document.getElementById('app-root')?.classList.remove('app-hidden');
    document.querySelectorAll('.page').forEach((el) => el.classList.remove('active'));
    document.getElementById('page-debtors')?.classList.add('active');
    document.getElementById('modal-debtor')?.classList.add('open');
  });
  await page.fill('#in-debtor-name', 'احمدِ سنجه');
  await page.fill('#in-debtor-phone', '0700111222');
  await page.$eval('#form-debtor', (f) => f.requestSubmit ? f.requestSubmit() : f.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })));

  //  خودِ موتور با ۵۰۰ms تأخیر می‌فرستد؛ صبر می‌کنیم تا ردیف واقعاً بنشیند
  const deadline = Date.now() + 20_000;
  let row = null;
  while (Date.now() < deadline) {
    row = await one(
      `SELECT data, row_id FROM sync_rows WHERE app='shop' AND account_id=$1
         AND table_name='debtors' AND data->>'name'='احمدِ سنجه'`, [shopId],
    );
    if (row) break;
    await page.waitForTimeout(400);
  }
  assert.ok(row, 'ردیفِ قرض‌دار باید روی سرور نشسته باشد');
  assert.equal(row.data.phone, '0700111222');
  assert.match(row.row_id, /^[0-9A-HJKMNP-TV-Z]{26}$/, 'شناسهٔ ردیفِ تازه باید ULID باشد');

  const op = await one(
    `SELECT op_type, fields FROM oplog WHERE app='shop' AND account_id=$1 AND row_id=$2
      ORDER BY server_seq ASC LIMIT 1`, [shopId, row.row_id],
  );
  assert.equal(op.op_type, 'insert');
  assert.equal(op.fields.name, 'احمدِ سنجه');
  assert.equal(op.fields.id, undefined, 'شناسه نباید داخلِ fields تکرار شود');
});

await step('ویرایشِ یک فیلد فقط همان یک فیلد را می‌فرستد', async () => {
  const before = await one(
    `SELECT row_id FROM sync_rows WHERE app='shop' AND account_id=$1
       AND table_name='debtors' AND data->>'name'='احمدِ سنجه'`, [shopId],
  );
  await page.evaluate(() => {
    document.getElementById('modal-debtor')?.classList.add('open');
  });
  //  همان دکمهٔ «ویرایش» را برنامه با شناسهٔ ردیف صدا می‌زند؛ این‌جا
  //  فرمِ ویرایش را از خودِ فهرست باز می‌کنیم
  await page.evaluate((id) => {
    const btn = document.querySelector(`[data-edit-debtor="${id}"]`);
    if (btn) btn.click();
  }, before.row_id);
  await page.fill('#in-debtor-phone', '0799888777');
  await page.$eval('#form-debtor', (f) => f.requestSubmit ? f.requestSubmit() : f.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })));

  const deadline = Date.now() + 20_000;
  let op = null;
  while (Date.now() < deadline) {
    op = await one(
      `SELECT op_type, fields FROM oplog WHERE app='shop' AND account_id=$1 AND row_id=$2
         AND op_type='update' ORDER BY server_seq DESC LIMIT 1`, [shopId, before.row_id],
    );
    if (op) break;
    await page.waitForTimeout(400);
  }
  assert.ok(op, 'opِ ویرایش باید رسیده باشد');
  assert.deepEqual(Object.keys(op.fields), ['phone'], `فقط فیلدِ عوض‌شده: ${JSON.stringify(op.fields)}`);
  assert.equal(op.fields.phone, '0799888777');
});

await step('«اشتراکِ من» از تپشِ سرور پر می‌شود و رنگش از سرور می‌آید', async () => {
  await page.evaluate(() => window.TohidAccount.heartbeat());
  await page.evaluate(() => window.TohidAccountUI.openPlan());
  await page.waitForSelector('#acct-plan.open .plan-bar', { timeout: 10_000 });
  const out = await page.evaluate(() => {
    const bar = document.querySelector('#acct-plan .plan-bar');
    const days = document.querySelector('#acct-plan .plan-days');
    const chip = document.querySelector('#acct-plan .plan-chip');
    return {
      color: bar && bar.getAttribute('data-color'),
      label: days && days.textContent.trim(),
      plan: chip && chip.textContent.trim(),
      body: document.querySelector('#acct-plan .acct-body').textContent,
    };
  });
  assert.ok(['green', 'yellow', 'red'].includes(out.color), `رنگ: ${out.color}`);
  assert.ok(out.label && out.label.length > 0, 'برچسبِ روزهای مانده باید از سرور آمده باشد');
  assert.ok(out.body.includes('دستگاه‌های فعال'), 'میزِ دستگاه‌ها باید باشد');
  assert.ok(out.body.includes('پرداخت‌ها'), 'میزِ پرداخت‌ها باید باشد');
  await page.evaluate(() => document.getElementById('acct-plan').classList.remove('open'));
});

await step('پنجرهٔ «همگام‌سازی» آخرین موفق، صف و نسخهٔ Schema را می‌گوید', async () => {
  await page.evaluate(() => window.TohidAccountUI.openSync());
  await page.waitForSelector('#acct-sync.open', { timeout: 10_000 });
  const text = await page.textContent('#acct-sync .acct-body');
  for (const needle of ['آخرین همگام‌سازیِ موفق', 'در صف', 'نسخهٔ Schema', 'گزارشِ خطا']) {
    assert.ok(text.includes(needle), `«${needle}» باید در پنجرهٔ همگام‌سازی باشد`);
  }
  //  ⛔ کادرِ نشانیِ سرور نباید ساخته شده باشد — قاعدهٔ قفلِ نشانی
  assert.ok(!text.includes('آدرس سرور'), 'کادرِ نشانیِ سرور نباید باشد');
  await page.evaluate(() => document.getElementById('acct-sync').classList.remove('open'));
});

await step('قفلِ نرم: اشتراکِ تمام‌شده فقط‌خواندنی می‌کند و خروجی را نمی‌بندد', async () => {
  const out = await page.evaluate(() => {
    localStorage.setItem('tohid-heartbeat-v1', JSON.stringify({
      subscription: { status: 'expired', active: false, plan: 'm1', color: 'red', label: 'منقضی', daysLeft: 0, endsAt: 1, permanent: false },
      at: Date.now(),
    }));
    localStorage.removeItem('tohid-banner-seen-v1');
    window.TohidAccountUI._applySubscription();
    const banner = document.getElementById('acct-banner');
    //  دکمهٔ نوشتن باید بسته شود و دکمهٔ خروجی نه
    let writeBlocked = false;
    let exportAllowed = true;
    const write = document.getElementById('btn-add-debtor-top');
    const exp = document.getElementById('btn-export-backup');
    if (write) {
      const ev = new MouseEvent('click', { bubbles: true, cancelable: true });
      write.dispatchEvent(ev);
      writeBlocked = ev.defaultPrevented;
    }
    if (exp) {
      const ev2 = new MouseEvent('click', { bubbles: true, cancelable: true });
      exp.dispatchEvent(ev2);
      exportAllowed = !ev2.defaultPrevented;
    }
    return {
      locked: window.TohidAccountUI.isSoftLocked(),
      bannerShown: !!banner && banner.classList.contains('show'),
      bannerText: banner ? banner.textContent : '',
      writeBlocked, exportAllowed,
      ledgerRows: (JSON.parse(localStorage.getItem('tohid-shop-data-v1') || '{}').debtors || []).length,
    };
  });
  assert.equal(out.locked, true, 'باید فقط‌خواندنی شده باشد');
  assert.equal(out.bannerShown, true, 'بنرِ سرخ باید دیده شود');
  assert.ok(out.bannerText.includes('فقط‌خواندنی'), out.bannerText.slice(0, 80));
  assert.equal(out.writeBlocked, true, 'دکمهٔ «قرض‌دار جدید» باید بسته باشد');
  assert.equal(out.exportAllowed, true, '⛔ خروجی و چاپ هیچ‌وقت بسته نمی‌شوند');
  assert.ok(out.ledgerRows > 0, '⛔ هیچ داده‌ای پاک نمی‌شود');

  //  بنرِ زرد، هفت روز پیش از پایان
  const warn = await page.evaluate(() => {
    localStorage.setItem('tohid-heartbeat-v1', JSON.stringify({
      subscription: { status: 'active', active: true, plan: 'm1', color: 'yellow', label: '۵ روز مانده', daysLeft: 5, endsAt: Date.now() + 5 * 86400000, permanent: false },
      at: Date.now(),
    }));
    localStorage.removeItem('tohid-banner-seen-v1');
    window.TohidAccountUI._applySubscription();
    const b = document.getElementById('acct-banner');
    return { locked: window.TohidAccountUI.isSoftLocked(), kind: b.className, text: b.textContent };
  });
  assert.equal(warn.locked, false, 'هفت روز پیش از پایان هنوز قفل نیست');
  assert.ok(warn.kind.includes('warn'), 'بنر باید زرد باشد');
  assert.ok(warn.text.includes('تا پایانِ اشتراک'), warn.text.slice(0, 80));
});

/* ---- ورود با کدِ شش‌رقمی، روی یک مرورگرِ تازه و بی نشست ---- */
await step('شش خانهٔ کد: پرشِ خودکار، Paste، ارقامِ فارسی و ارسالِ خودکار', async () => {
  const fresh = await browser.newPage();
  fresh.on('pageerror', (e) => pageErrors.push('login: ' + String(e.message)));
  await fresh.addInitScript(() => { localStorage.setItem('tohid-unlocked-v1', '1'); });
  await fresh.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
  await fresh.waitForFunction(() => !!window.TohidAccountUI, null, { timeout: 15_000 });

  await fresh.evaluate(() => window.TohidAccountUI.openLogin());
  await fresh.waitForSelector('#acct-login.open #lg-email', { state: 'visible', timeout: 10_000 });
  const boxCount = await fresh.evaluate(() => document.querySelectorAll('#lg-boxes .code-box').length);
  assert.equal(boxCount, 6, 'کادرِ کد باید شش خانه باشد');

  //  پله‌ی ایمیل ⇒ پله‌ی کد، با یک درخواستِ واقعی
  const loginEmail = `wpe1.web.${Date.now()}@example.com`;
  await fresh.fill('#lg-email', loginEmail);
  await fresh.click('#lg-send');
  await fresh.waitForSelector('#lg-step-code:not([hidden])', { timeout: 20_000 });

  //  ارقامِ فارسی در خانهٔ اول ⇒ باید انگلیسی شوند و خودشان پخش شوند
  await fresh.evaluate(() => {
    const b = document.querySelectorAll('#lg-boxes .code-box');
    b[0].value = '۱۲۳';
    b[0].dispatchEvent(new Event('input', { bubbles: true }));
  });
  let filled = await fresh.evaluate(() =>
    Array.from(document.querySelectorAll('#lg-boxes .code-box')).map(x => x.value).join(''));
  assert.equal(filled, '123', `ارقامِ فارسی باید انگلیسی شوند: «${filled}»`);

  //  Backspace در خانهٔ خالی ⇒ برگشت به خانهٔ قبل
  await fresh.evaluate(() => {
    const b = document.querySelectorAll('#lg-boxes .code-box');
    b[3].focus();
    b[3].dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true }));
  });
  filled = await fresh.evaluate(() =>
    Array.from(document.querySelectorAll('#lg-boxes .code-box')).map(x => x.value).join(''));
  assert.equal(filled, '12', 'Backspace باید رقمِ قبلی را پاک کند');

  //  شمارشِ معکوسِ شصت ثانیه
  const timer = await fresh.textContent('#lg-timer');
  assert.ok(/ثانیه/.test(timer), `شمارشِ معکوس: ${timer}`);
  const resendDisabled = await fresh.evaluate(() => document.getElementById('lg-resend').disabled);
  assert.equal(resendDisabled, true, 'تا پایانِ شصت ثانیه، «فرستادنِ دوباره» بسته است');

  //  و حالا کدِ واقعی — از دفترِ خودِ سرور، نه از حدس
  const req = await one(
    'SELECT request_id, code_sealed FROM login_requests WHERE email=$1 ORDER BY created_at DESC LIMIT 1',
    [loginEmail],
  );
  assert.ok(req, 'درخواستِ کد باید در دفتر نشسته باشد');
  const codes = require('../src/lib/login-codes');
  const realCode = codes.unseal(req.code_sealed);

  //  رقم‌به‌رقم، همان کاری که آدم می‌کند — ارسالِ خودکار پس از رقمِ ششم
  await fresh.evaluate((code) => {
    const b = document.querySelectorAll('#lg-boxes .code-box');
    b.forEach((x) => { x.value = ''; });
    b[0].focus();
    for (let i = 0; i < 6; i++) {
      const box = document.activeElement.classList.contains('code-box') ? document.activeElement : b[i];
      box.value = code[i];
      box.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }, realCode);

  await fresh.waitForFunction(() => {
    try { return !!(JSON.parse(localStorage.getItem('tohid-license-v1') || '{}').accessToken); }
    catch { return false; }
  }, null, { timeout: 20_000 });

  const session = await fresh.evaluate(() => {
    const a = JSON.parse(localStorage.getItem('tohid-license-v1') || '{}');
    return { hasAccess: !!a.accessToken, hasRefresh: !!a.refreshToken, email: a.userEmail };
  });
  assert.equal(session.hasAccess, true, 'پس از رقمِ ششم باید خودکار وارد شده باشد');
  assert.equal(session.hasRefresh, true, 'توکنِ تازه‌سازی هم باید نشسته باشد');
  assert.equal(session.email, loginEmail);

  const user = await one('SELECT id FROM users WHERE email=$1', [loginEmail]);
  assert.ok(user, 'حساب باید روی سرور ساخته شده باشد — ثبت‌نام و ورود یکی‌اند');
  await fresh.close();
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
