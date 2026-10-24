/**
 * پنلِ مدیریت — با مرورگرِ واقعی، نه با حدس.
 *
 * ── چرا این فایل هست ───────────────────────────────────────────────
 * بخش‌های تازهٔ پنل (کدِ دکان، پشتیبانی، پیامِ همگانی، ایمیل و پوش،
 * پشتیبانِ هر حساب) همه جاوااسکریپتِ مرورگرند. آزمون‌های `node:test`
 * فقط سمتِ سرور را می‌سنجند، پس یک `$('svip-plan')`ِ غلط یا یک
 * `onclick`ِ جانشانده هیچ‌جا قرمز نمی‌شد — تا روزی که صاحبِ سامانه
 * دکمه را می‌زد و هیچ اتفاقی نمی‌افتاد.
 *
 * این‌جا صفحه واقعاً در کرومیوم باز می‌شود، واقعاً کلیک می‌شود، و
 * نتیجه از **دیتابیس** سنجیده می‌شود نه از متنِ روی صفحه.
 *
 * اجرا:  node test/admin-panel.mjs
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);

/*
 *  ⚠️ پلی‌رایت روی این ریپو نصب نیست و نباید بشود: سرور عمداً فقط دو
 *  وابستگی دارد (`express` و `pg`) تا روی هر هاستی و بی اینترنتِ npm
 *  بالا بیاید. پس از هر جایی که باشد برداشته می‌شود — نصبِ سراسری،
 *  `node_modules`ِ خودِ ریپو، یا مسیرِ `NODE_PATH`.
 *
 *  و اگر هیچ‌جا نبود، سنجه **قرمز** می‌شود (F0-17): «رد» با کدِ ۰ در CI
 *  سبز دیده می‌شد بی آن‌که مرورگری باز شده باشد. ردِ آگاهانه فقط با
 *  SKIP_BROWSER_TESTS=1 (`browser-guard.mjs`).
 */
function candidates() {
  const out = [];
  try { out.push(`${execSync('npm root -g', { encoding: 'utf8' }).trim()}/playwright`); } catch { /* نبود */ }
  out.push('playwright');
  return out;
}
let chromium = null;
for (const path of candidates()) {
  try { ({ chromium } = require(path)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL
  || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.BACKUP_PATH = `${require('node:os').tmpdir()}/shop-panel-backups-${process.pid}`;
process.env.RATE_GENERAL_MAX = '100000';
process.env.RATE_BROADCAST_MAX = '10000';
process.env.OTP_RESEND_SECONDS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

let failures = 0;
const results = [];
async function step(name, fn) {
  try { await fn(); results.push(['✅', name]); }
  catch (err) { failures++; results.push(['❌', `${name} — ${err.message}`]); }
}

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
await query(
  `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
   VALUES ($1,'admin','مدیرِ آزمون',$2,'superadmin','active',$3)`,
  [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
);

const app = await createApp({ runMigrations: false });
const server = await new Promise((resolve) => {
  const s = app.listen(0, '127.0.0.1', () => resolve(s));
});
const base = `http://127.0.0.1:${server.address().port}`;

/** یک دکان‌دارِ واقعی، از همان سه پلهٔ ثبت‌نام. */
async function makeShop(name) {
  const email = `${name}@panel.local`;
  const password = 'Passw0rd!test';
  const post = async (p, b, t) => {
    const r = await fetch(base + p, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) },
      body: JSON.stringify(b),
    });
    return { status: r.status, body: await r.json().catch(() => null) };
  };
  const started = await post('/api/auth/register/start', { name, email, password });
  const verified = await post('/api/auth/register/verify', { email, code: started.body.devCode });
  const done = await post('/api/auth/register/complete', {
    ticket: verified.body.ticket, name, password,
    device: { deviceId: `dev-${name}`, name: 'تست', platform: 'test' },
    app: 'shop', terms: { accepted: true },
  });
  const shop = await post('/api/shop', { name: `دکانِ ${name}` }, done.body.accessToken);
  return { token: done.body.accessToken, shopId: shop.body.shop.id, userId: done.body.user.id };
}

const owner = await makeShop('panelowner');

//  یک پیامِ پشتیبانی تا فهرست خالی نباشد
await fetch(`${base}/api/support/messages`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${owner.token}` },
  body: JSON.stringify({ body: 'سلام، یک مشکلی دارم' }),
});

//  و یک پشتیبان تا جدولِ پشتیبان‌ها چیزی داشته باشد
await fetch(`${base}/api/me/backups?ext=json&kind=manual&label=از%20آزمون`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/octet-stream', Authorization: `Bearer ${owner.token}` },
  body: Buffer.from(JSON.stringify({ products: [] })),
});

/**
 *  یک پمپِ واقعی با یک خبر — تا جدولِ «خبرهای این پمپ» چیزی داشته باشد.
 *
 *  ⛔ همان چیزی که تا امروز هیچ‌جا دیده نمی‌شد: خبرهای برنامهٔ پمپ
 *  («اضافه برد»، «کم مانده») اصلاً به ابر نمی‌رفتند، پس وقتی صاحبِ پمپ
 *  می‌گفت «خبر نگرفتم»، مدیر هیچ راهی برای دیدنش نداشت.
 */
async function makeStationWithEvent(name) {
  const email = `${name}@panel.local`;
  const password = 'Passw0rd!test';
  const post = async (p, b, t) => {
    const r = await fetch(base + p, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) },
      body: JSON.stringify(b),
    });
    return { status: r.status, body: await r.json().catch(() => null) };
  };
  const started = await post('/api/auth/register/start', { name, email, password });
  const verified = await post('/api/auth/register/verify', { email, code: started.body.devCode });
  const done = await post('/api/auth/register/complete', {
    ticket: verified.body.ticket, name, password,
    device: { deviceId: `dev-${name}`, name: 'تست', platform: 'test' },
    //  ⚠️ نشستِ بخشِ **پمپ** — توکنِ دکان روی مسیرهای پمپ پیدا نمی‌شود
    app: 'pump', terms: { accepted: true },
  });
  const token = done.body.accessToken;
  const st = await post('/api/pump', { name: `پمپِ ${name}` }, token);
  const bound = await post('/api/pump/device/bind', { device: { uid: `pc-${name}` } }, token);
  await post('/api/pump/device/events',
    { events: [{ kind: 'debt', title: 'کریم اضافه برد', clientId: 'panel-1' }] },
    bound.body.deviceToken);
  return { stationId: st.body.station.id, name: `پمپِ ${name}` };
}

const station = await makeStationWithEvent('panelpump');

const browser = await chromium.launch();
const page = await browser.newPage();
const consoleErrors = [];
page.on('pageerror', (err) => consoleErrors.push(String(err.message)));
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });

await page.goto(`${base}/admin/`, { waitUntil: 'domcontentloaded' });

// ── ۱) ورود ──────────────────────────────────────────────────────
await step('مدیر وارد می‌شود', async () => {
  await page.fill('#in-username', 'admin');
  await page.fill('#in-password', 'Admin!12345');
  await page.click('#btn-login');
  await page.waitForSelector('#app-view:not(.hidden)', { timeout: 10_000 });
});

// ── F0-08: بی BACKUP_PASSPHRASE، هشدارِ قرمز بالای پنل ────────────
await step('پشتیبانِ بی‌رمز: هشدارِ قرمز بالای همهٔ زبانه‌ها', async () => {
  await page.waitForSelector('#backup-crypto-warn:not(.hidden)', { timeout: 10_000 });
  const box = page.locator('#backup-crypto-warn');
  assert.match(await box.textContent(), /BACKUP_PASSPHRASE/);
  assert.ok((await box.getAttribute('class')).includes('msg-bad'), 'هشدار باید قرمز باشد');
  if (process.env.PANEL_SHOTS) await page.screenshot({ path: `${process.env.PANEL_SHOTS}/f0-08-backup-warning.png` });
});

const openTab = async (name) => {
  await page.click(`.tab[data-tab="${name}"]`);
  await page.waitForSelector(`#tab-${name}:not(.hidden)`, { timeout: 10_000 });
  await page.waitForTimeout(400);
};

// ── ۲) هر تب باز می‌شود و خطای جاوااسکریپت نمی‌دهد ────────────────
await step('هر تب بی خطا باز می‌شود', async () => {
  for (const t of ['shops', 'pump', 'users', 'subs', 'sales', 'payments', 'discounts', 'notices',
    'requests', 'plans', 'support', 'delivery', 'backups', 'health', 'ops', 'audit']) {
    await openTab(t);
  }
  assert.deepEqual(consoleErrors, [], `خطای صفحه: ${consoleErrors.join(' | ')}`);
});

// ── ۳) کدِ اشتراکِ دکان — چیزی که تا امروز در پنل نبود ────────────
await step('کارتِ کدِ دکان در پنل هست', async () => {
  await openTab('shops');
  assert.ok(await page.isVisible('#btn-svip-make'), 'دکمهٔ ساختِ کدِ دکان باید دیده شود');
  const options = await page.$$eval('#svip-plan option', (o) => o.length);
  assert.ok(options > 0, 'فهرستِ پلن‌ها باید پر شده باشد');
});

await step('ساختِ کدِ دکان واقعاً کد می‌سازد', async () => {
  const before = (await one('SELECT COUNT(*)::int n FROM vip_codes')).n;
  await page.selectOption('#svip-plan', 'custom');
  await page.fill('#svip-days', '45');
  await page.fill('#svip-note', 'از پنل');
  await page.click('#btn-svip-make');
  await page.waitForFunction(() => document.querySelector('#svip-msg')?.textContent?.includes('کد:'), null, { timeout: 10_000 });

  const after = (await one('SELECT COUNT(*)::int n FROM vip_codes')).n;
  assert.equal(after, before + 1, 'یک کدِ تازه باید در دفترِ دکان نشسته باشد');
  const row = await one('SELECT * FROM vip_codes ORDER BY created_at DESC LIMIT 1');
  assert.equal(Number(row.days), 45);
  assert.equal(row.note, 'از پنل');

  //  و در دفترِ پمپ چیزی ننشسته
  const pumpCount = (await one('SELECT COUNT(*)::int n FROM station_vip_codes')).n;
  assert.equal(pumpCount, 0, 'کدِ دکان نباید در دفترِ پمپ بنشیند');
});

await step('کدِ تازه در جدولِ همان کارت دیده می‌شود', async () => {
  const rows = await page.$$eval('#svip-body tr', (r) => r.length);
  assert.ok(rows >= 1, 'جدولِ کدها باید پر شود');
  const text = await page.textContent('#svip-body');
  assert.ok(text.includes('••••'), 'فقط دو رقمِ آخر باید دیده شود، نه خودِ کد');
});

// ── ۴) پشتیبانِ هر حساب در صفحهٔ دکان ─────────────────────────────
await step('پشتیبان‌های همان دکان در صفحه‌اش می‌آیند', async () => {
  await openTab('shops');
  await page.click('#shops-body tr:first-child button');
  await page.waitForSelector('#shop-detail:not(.hidden)', { timeout: 10_000 });
  await page.waitForFunction(
    () => (document.querySelector('#shop-bak-body')?.children.length || 0) > 0,
    null, { timeout: 10_000 }
  );
  const text = await page.textContent('#shop-bak-body');
  assert.ok(text.includes('از آزمون'), `یادداشتِ پشتیبان باید دیده شود — دیده شد: ${text.slice(0, 120)}`);
  const stats = await page.textContent('#shop-bak-stats');
  assert.ok(stats.includes('نسخه'), 'خلاصهٔ سهم باید نوشته شود');
});

//  ⛔ دانلود = خروجیِ دادهٔ خصوصی ⇒ پنل دلیل می‌پرسد و سرور ردیفِ سابقه می‌نویسد
//  (ADMIN-CONTROL-PLANE.md). لغوِ پرسش ⇒ هیچ درخواستی نمی‌رود.
await step('دانلودِ پشتیبانِ دکان دلیل می‌پرسد و در سابقه می‌ماند', async () => {
  const count = async () => (await one(
    `SELECT COUNT(*)::int n FROM audit_logs WHERE action='admin.account_backup_downloaded'`)).n;
  const btn = '#shop-bak-body button:has-text("دانلود")';
  page.once('dialog', (d) => d.dismiss());
  await page.click(btn);
  await page.waitForTimeout(300);
  assert.equal(await count(), 0, 'لغوِ دلیل ⇒ دانلودی نیست');

  page.once('dialog', (d) => d.accept('بازیابی به درخواستِ صاحبِ دکان'));
  const [dl] = await Promise.all([page.waitForEvent('download', { timeout: 10_000 }), page.click(btn)]);
  assert.ok(dl.suggestedFilename(), 'فایل باید برسد');
  const row = await one(
    `SELECT * FROM audit_logs WHERE action='admin.account_backup_downloaded' ORDER BY created_at DESC LIMIT 1`);
  assert.ok(row, 'ردیفِ سابقه باید باشد');
  const detail = typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail;
  assert.equal(detail.reason, 'بازیابی به درخواستِ صاحبِ دکان');
});

// ── ۵) پشتیبانی ───────────────────────────────────────────────────
await step('گفت‌وگوی پشتیبانی در پنل دیده و جواب داده می‌شود', async () => {
  await openTab('support');
  await page.waitForFunction(
    () => (document.querySelector('#sup-body')?.children.length || 0) > 0,
    null, { timeout: 10_000 }
  );
  await page.click('#sup-body tr:first-child button');
  await page.waitForSelector('#sup-detail:not(.hidden)', { timeout: 10_000 });
  const msgs = await page.textContent('#sup-messages');
  assert.ok(msgs.includes('یک مشکلی دارم'), 'پیامِ کاربر باید دیده شود');

  await page.fill('#sup-reply', 'سلام، چه مشکلی؟');
  await page.click('#btn-sup-send');
  await page.waitForFunction(
    () => document.querySelector('#sup-msg')?.textContent?.includes('فرستاده شد'),
    null, { timeout: 10_000 }
  );

  const reply = await one(
    "SELECT * FROM support_messages WHERE sender='admin' ORDER BY created_at DESC LIMIT 1"
  );
  assert.ok(reply, 'پاسخ باید در دیتابیس نشسته باشد');
  assert.equal(reply.body, 'سلام، چه مشکلی؟');
});

await step('صافیِ بخش، فهرست را واقعاً کم می‌کند', async () => {
  await page.selectOption('#sup-app', 'pump');
  await page.waitForTimeout(600);
  const text = await page.textContent('#sup-body');
  assert.ok(text.includes('گفت‌وگویی نیست'), 'هیچ گفت‌وگوی پمپی نیست، پس فهرست باید خالی باشد');
  await page.selectOption('#sup-app', '');
  await page.waitForTimeout(600);
});

// ── ۶) پیامِ همگانی ───────────────────────────────────────────────
await step('پیامِ همگانی از پنل واقعاً می‌رود', async () => {
  page.once('dialog', (d) => d.accept());
  await page.fill('#bc-body', 'خبرِ همگانی از پنل');
  await page.selectOption('#bc-target', 'all');
  await page.click('#btn-bc-send');
  await page.waitForFunction(
    () => document.querySelector('#bc-msg')?.textContent?.includes('گیرنده'),
    null, { timeout: 10_000 }
  );
  const row = await one(
    "SELECT * FROM support_messages WHERE body=$1", ['خبرِ همگانی از پنل']
  );
  assert.ok(row, 'پیامِ همگانی باید در گفت‌وگوی دکان‌دار نشسته باشد');
  assert.equal(row.sender, 'system');
});

// ── ۷) ایمیل و پوش ────────────────────────────────────────────────
await step('صفحهٔ ایمیل حالِ واقعی را می‌گوید', async () => {
  await openTab('delivery');
  const state = await page.textContent('#mail-state');
  assert.ok(state.includes('فقط لاگ'),
    `پیش‌فرض «log» است و صفحه باید همین را بگوید — گفت: ${state}`);
});

await step('ذخیرهٔ تنظیماتِ ایمیل واقعاً می‌نشیند', async () => {
  await page.selectOption('#mail-provider', 'smtp');
  await page.fill('#mail-host', 'smtp.example.com');
  await page.fill('#mail-port', '587');
  await page.fill('#mail-user', 'robot@example.com');
  await page.fill('#mail-from', 'robot@example.com');
  await page.fill('#mail-pass', 'secret-pass-1');
  await page.click('#btn-mail-save');
  await page.waitForFunction(
    () => document.querySelector('#mail-msg')?.textContent?.includes('ذخیره شد'),
    null, { timeout: 10_000 }
  );

  const host = await one("SELECT value FROM app_config WHERE key='email_host'");
  assert.equal(host.value, 'smtp.example.com');
  //  ⚠️ «ذخیره شد» پیش از تمام شدنِ `loadDelivery()` می‌آید، پس کارتِ حال
  //  یک لحظه هنوز کهنه است — منتظرش بمانید، وگرنه سنجه گاهی سرخ می‌شود.
  await page.waitForFunction(
    () => !document.getElementById('mail-state').textContent.includes('فقط لاگ'),
    null, { timeout: 10_000 }
  );
});

await step('رمزِ ایمیل با ذخیرهٔ بعدی پاک نمی‌شود', async () => {
  //  کادرِ رمز خالی است (هیچ‌وقت پر برنمی‌گردد) — ذخیرهٔ دوباره نباید
  //  همان را پاک کند، وگرنه هر ویرایشِ کوچکی ایمیل را می‌خواباند
  await page.fill('#mail-fromname', 'توحید');
  await page.click('#btn-mail-save');
  await page.waitForTimeout(800);
  const pass = await one("SELECT value FROM app_config WHERE key='email_pass'");
  assert.equal(pass.value, 'secret-pass-1', 'رمز باید سرِ جایش مانده باشد');
});

await step('فایلِ حساب سرویسِ خراب، پیامِ آدمیزاد می‌دهد', async () => {
  await page.fill('#push-sa', '{ این JSON نیست }');
  await page.click('#btn-push-save');
  await page.waitForFunction(
    () => (document.querySelector('#push-msg')?.textContent || '').length > 0,
    null, { timeout: 10_000 }
  );
  const text = await page.textContent('#push-msg');
  assert.ok(!text.includes('Unexpected token') && !text.includes('JSON.parse'),
    `پیامِ خامِ کتابخانه نباید به مدیر برسد — رسید: ${text}`);

  /*
   *  ⚠️ همین بند عمداً یک ۴۰۰ می‌گیرد، و کرومیوم هر پاسخِ ۴۰۰ را در
   *  کنسول «Failed to load resource» می‌نویسد. آن **خطای صفحه نیست**،
   *  خطایی است که خودمان خواسته‌ایم. پس همین‌جا پاک می‌شود تا بندِ
   *  آخر فقط خطاهای واقعی را ببیند — نه این‌که کلاً از سنجش بیفتد.
   */
  consoleErrors.length = 0;
});

await step('باتِ تلگرامِ پمپ: بی رمز «داده نشده» می‌گوید و رمزِ بدشکل ذخیره نمی‌شود', async () => {
  await page.waitForFunction(
    () => (document.getElementById('tg-state')?.textContent || '').length > 0,
    null, { timeout: 10_000 }
  );
  const state = await page.textContent('#tg-state');
  assert.ok(state.includes('داده نشده'), `بی رمز، کارت باید همین را بگوید — گفت: ${state}`);

  await page.fill('#tg-token', 'این-رمز-نیست');
  await page.click('#btn-tg-save');
  await page.waitForFunction(
    () => (document.querySelector('#tg-msg')?.textContent || '').length > 0,
    null, { timeout: 10_000 }
  );
  const text = await page.textContent('#tg-msg');
  //  باتِ پمپ در ریپوی پمپ است (lib/bots.js)؛ این‌جا معمولاً نصب نیست ⇒ همان پیامِ «نصب نیست»
  const missing = !require('../src/lib/bots').pump().available;
  assert.ok(missing ? text.includes('نصب نیست') : text.includes('BotFather'),
    `پیامِ رمزِ بدشکل باید بگوید ${missing ? 'بات نصب نیست' : 'رمز از کجا می‌آید'} — رسید: ${text}`);
  const row = await one("SELECT value FROM app_config WHERE key='telegram_token'");
  assert.ok(!row || !row.value, 'رمزِ بدشکل نباید ذخیره شود');
  //  همان ۴۰۰ِ خواسته — بالا را ببینید
  consoleErrors.length = 0;
});

// ── ۷ب) پلن‌های پمپ — جدولِ خودش، و کادرِ پلنِ اشتراکِ پمپ ─────────
/*
 *  ⛔ دو باگی که تا امروز هیچ‌جا قرمز نمی‌شدند، چون هر دو در مرورگر
 *  بودند:
 *    ۱) جدولِ پلن‌های پمپ اصلاً وجود نداشت — استاندارد/وی‌آی‌پی/دائمی
 *       نه دیده می‌شدند و نه ویرایش.
 *    ۲) کادرِ «پلن»ِ اشتراکِ پمپ از فهرستِ **دکان** پر می‌شد، پس مدیر
 *       `m6`ی دکان را به یک پمپ می‌داد؛ آن کد روی پمپ فهرستِ خالی
 *       دارد و فهرستِ خالی یعنی «پلنِ کامل» — یعنی هر اشتراکی که از
 *       پنل به پمپ داده می‌شد عملاً وی‌آی‌پی بود.
 */
await step('جدولِ پلن‌های پمپ سه پلنِ واقعی را نشان می‌دهد', async () => {
  await openTab('plans');
  await page.waitForFunction(
    () => document.querySelectorAll('#pump-plans-body tr').length > 0,
    null, { timeout: 10_000 }
  );
  const codes = await page.$$eval('#pump-plans-body tr td:first-child',
    (tds) => tds.map((t) => t.textContent.trim()));
  for (const want of ['std', 'vip', 'perm']) {
    assert.ok(codes.includes(want), `پلنِ ${want} باید در جدولِ پمپ باشد`);
  }
  //  و جدولِ دکان پلن‌های خودش را دارد، نه پلن‌های پمپ
  const shopCodes = await page.$$eval('#plans-body tr td:first-child',
    (tds) => tds.map((t) => t.textContent.trim()));
  assert.ok(!shopCodes.includes('std'), 'پلنِ پمپ نباید در جدولِ دکان بیاید');
});

await step('ویرایشِ قیمتِ پلنِ پمپ روی خودِ پمپ می‌نشیند، نه روی دکان', async () => {
  const before = await one(`SELECT price_afn FROM plans WHERE app='shop' AND code='m1'`);

  const row = await page.$('#pump-plans-body tr');
  const codeCell = await row.$eval('td:first-child', (t) => t.textContent.trim());
  await row.$eval('td:nth-child(4) input', (i) => { i.value = '131'; });
  await row.$eval('td:last-child button', (b) => b.click());
  await page.waitForFunction(
    () => (document.querySelector('#pump-plans-msg')?.textContent || '').includes('ذخیره'),
    null, { timeout: 10_000 }
  );

  const pumpRow = await one('SELECT price_afn FROM plans WHERE app=$1 AND code=$2', ['pump', codeCell]);
  assert.equal(Number(pumpRow.price_afn), 131, 'قیمتِ پلنِ پمپ باید عوض شده باشد');
  const after = await one(`SELECT price_afn FROM plans WHERE app='shop' AND code='m1'`);
  assert.equal(Number(after.price_afn), Number(before.price_afn),
    'ویرایشِ پلنِ پمپ نباید قیمتِ دکان را تکان بدهد');
});

await step('کادرِ پلنِ اشتراکِ پمپ از فهرستِ پمپ پر می‌شود', async () => {
  await openTab('pump');
  await page.waitForFunction(
    () => document.querySelectorAll('#pump-plan option').length > 1,
    null, { timeout: 10_000 }
  );
  const opts = await page.$$eval('#pump-plan option', (o) => o.map((x) => x.value));
  assert.ok(opts.includes('vip'), 'پلنِ وی‌آی‌پیِ پمپ باید در فهرست باشد');
  assert.ok(!opts.includes('m6'), 'پلنِ دکان نباید در فهرستِ پمپ باشد');

  //  کدِ اشتراکِ پمپ هم همان فهرست را می‌بیند
  const vipOpts = await page.$$eval('#pvip-plan option', (o) => o.map((x) => x.value));
  assert.ok(vipOpts.includes('std'), 'کدِ پمپ باید پلنِ استاندارد را داشته باشد');
  assert.ok(!vipOpts.includes('m1'), 'کدِ پمپ نباید پلنِ دکان را نشان بدهد');
});

// ── ۸) هیچ خطای جاوااسکریپتی در کلِ نشست ──────────────────────────
// ── ۷ج) خبرهای پمپ — «خبر نگرفتم» را همین‌جا می‌شود سنجید ────────
await step('خبرهای یک پمپ در پروندهٔ همان پمپ دیده می‌شوند', async () => {
  await openTab('pump');
  await page.waitForFunction(
    () => document.querySelectorAll('#pump-body tr').length > 0,
    null, { timeout: 10_000 }
  );
  //  دکمهٔ «مدیریت»ِ همان پمپ
  const clicked = await page.evaluate((wanted) => {
    for (const tr of document.querySelectorAll('#pump-body tr')) {
      if (tr.textContent.includes(wanted)) {
        tr.querySelector('button')?.click();
        return true;
      }
    }
    return false;
  }, station.name);
  assert.ok(clicked, 'پمپِ آزمون باید در فهرست باشد');

  await page.waitForFunction(
    () => document.querySelectorAll('#pump-events tr').length > 0,
    null, { timeout: 10_000 }
  );
  const rows = await page.$$eval('#pump-events tr', (trs) => trs.map((t) => t.textContent));
  assert.ok(rows.some((r) => r.includes('کریم اضافه برد')),
    `خبر باید در جدول باشد — ${JSON.stringify(rows)}`);
  //  و کامپیوترِ پمپ حساب ندارد، پس نامِ خودِ دستگاه نشان داده می‌شود
  assert.ok(rows.some((r) => r.includes('کامپیوترِ پمپ')),
    `فرستنده باید دیده شود — ${JSON.stringify(rows)}`);
});


/* ══════════════════════════════════════════════════════════════════
   بخش‌های تازه: فروش · پرداخت · تخفیف · مرکزِ اعلان
   ------------------------------------------------------------------
   همان دلیلِ بالای این فایل: این چهار بخش جاوااسکریپتِ مرورگرند و یک
   شناسهٔ غلط فقط وقتی پیدا می‌شود که صاحبِ سامانه دکمه را بزند و هیچ
   اتفاقی نیفتد. پس این‌جا واقعاً کلیک می‌شود و نتیجه از **دیتابیس**
   سنجیده می‌شود.
   ══════════════════════════════════════════════════════════════════ */

await step('ثبتِ پرداخت از پنل واقعاً ردیف می‌سازد', async () => {
  await openTab('payments');
  const before = (await one('SELECT COUNT(*)::int n FROM sub_payments')).n;
  await page.selectOption('#pay-app', 'shop');
  await page.fill('#pay-tenant', owner.shopId);
  await page.fill('#pay-amount', '2500');
  await page.selectOption('#pay-method', 'hawala');
  await page.fill('#pay-note', 'از پنل');
  await page.click('#btn-pay-add');
  //  ⚠️ منتظرِ **پیامِ موفقیت** بمانید، نه شمارِ ردیف‌ها: جدولِ خالی خودش
  //  یک ردیفِ «پرداختی ثبت نشده است» دارد، پس `length > 0` همان لحظه هم
  //  درست است و سنجه پیش از رسیدنِ درخواست می‌خواند (سبزِ دروغ).
  await page.waitForFunction(
    () => /ثبت شد/.test(document.getElementById('pay-msg').textContent), null, { timeout: 10_000 }
  );
  const after = (await one('SELECT COUNT(*)::int n FROM sub_payments')).n;
  assert.equal(after, before + 1, 'پرداخت باید در دیتابیس نشسته باشد');
  const row = await one(`SELECT * FROM sub_payments ORDER BY created_at DESC LIMIT 1`);
  assert.equal(row.tenant_id, owner.shopId);
  assert.equal(Number(row.amount), 2500);
  assert.equal(row.method, 'hawala');
  assert.ok(row.receipt_no, 'شمارهٔ رسیدِ خودکار');
});

await step('داشبوردِ فروش همان پرداخت را نشان می‌دهد', async () => {
  await openTab('sales');
  await page.waitForFunction(
    () => document.querySelectorAll('#sales-stats .stat').length > 0, null, { timeout: 10_000 }
  );
  const cards = await page.$$eval('#sales-stats .stat', (ns) => ns.map((n) => n.textContent));
  assert.ok(cards.some((c) => c.includes('امروز') && c.includes('دکان')), `کارتِ درآمد: ${JSON.stringify(cards)}`);
  assert.ok(cards.join(' ').includes('افغانی'), 'مبلغ با واحدِ خودش دیده می‌شود');
  const series = await page.$$eval('#sales-series tr', (ns) => ns.length);
  assert.equal(series, 12, 'نمودارِ رشد دوازده ماه است');
});

//  ⛔ G2ِ ADMIN-CONTROL-PLANE.md: دلیلِ اختیاری فقط در سابقه می‌نشیند. «لغو»ِ
//  پرسش هیچ کاری نمی‌کند؛ پرداختِ همین گام در پایان حذف می‌شود تا گام‌های بعد
//  همان عددهای قبلی را ببینند.
await step('دلیلِ اختیاری: ثبت و حذفِ پرداخت آن را در سابقه می‌گذارند', async () => {
  const detailOf = (r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) || {};
  await openTab('payments');
  await page.selectOption('#pay-app', 'shop');
  await page.fill('#pay-tenant', owner.shopId);
  await page.fill('#pay-amount', '700');
  await page.fill('#pay-note', 'با دلیلِ پنل');
  await page.fill('#pay-reason', 'رسیدِ حواله رسید');
  await page.evaluate(() => { document.getElementById('pay-msg').textContent = ''; });
  await page.click('#btn-pay-add');
  await page.waitForFunction(
    () => /ثبت شد/.test(document.getElementById('pay-msg').textContent), null, { timeout: 10_000 }
  );
  const pay = await one(`SELECT * FROM sub_payments WHERE note='با دلیلِ پنل'`);
  assert.ok(pay, 'پرداخت باید ثبت شده باشد');
  const made = await one(`SELECT * FROM audit_logs WHERE action='admin.payment_created' AND target_id=$1`, [pay.id]);
  assert.equal(detailOf(made).reason, 'رسیدِ حواله رسید');
  assert.equal(await page.inputValue('#pay-reason'), '', 'خانهٔ دلیل پس از ثبت خالی می‌شود');

  const row = page.locator('#payments-body tr', { hasText: pay.receipt_no });
  await row.waitFor({ timeout: 10_000 });
  page.once('dialog', (d) => d.dismiss());
  await row.locator('button', { hasText: 'حذف' }).click();
  await page.waitForTimeout(300);
  assert.equal((await one('SELECT deleted FROM sub_payments WHERE id=$1', [pay.id])).deleted, false, 'لغو ⇒ حذف نمی‌شود');

  page.once('dialog', (d) => d.accept('رسیدِ تکراری'));
  await row.locator('button', { hasText: 'حذف' }).click();
  for (let i = 0; i < 50; i += 1) {
    if ((await one('SELECT deleted FROM sub_payments WHERE id=$1', [pay.id])).deleted) break;
    await page.waitForTimeout(100);
  }
  const gone = await one(`SELECT * FROM audit_logs WHERE action='admin.payment_deleted' AND target_id=$1`, [pay.id]);
  assert.ok(gone, 'حذف باید سابقه داشته باشد');
  assert.equal(detailOf(gone).reason, 'رسیدِ تکراری');
});

await step('کدِ تخفیف از پنل ساخته می‌شود و در دیتابیس می‌نشیند', async () => {
  await openTab('discounts');
  await page.selectOption('#dc-app', 'shop');
  await page.fill('#dc-code', 'PANEL25');
  await page.selectOption('#dc-kind', 'percent');
  await page.fill('#dc-value', '25');
  await page.click('#btn-dc-make');
  await page.waitForFunction(
    () => /PANEL25/.test(document.getElementById('dc-body').textContent), null, { timeout: 10_000 }
  );
  const row = await one(`SELECT * FROM discount_codes WHERE code='PANEL25'`);
  assert.ok(row, 'کد باید ساخته شده باشد');
  assert.equal(row.app, 'shop', '⛔ کد به بخشِ خودش بسته است');
  assert.equal(Number(row.value), 25);
});

await step('کمپین از پنل: کد + اعلان، در یک کلیک', async () => {
  await openTab('discounts');
  await page.fill('#cmp-name', 'کمپینِ پنل');
  await page.selectOption('#cmp-app', 'shop');
  await page.selectOption('#cmp-audience', 'all');
  await page.fill('#cmp-code', 'PANELCMP');
  await page.fill('#cmp-value', '15');
  await page.fill('#cmp-title', 'خبرِ کمپین');
  await page.fill('#cmp-body', 'کدِ شما: {کد-تخفیف}');
  await page.uncheck('#cmp-email');
  await page.click('#btn-cmp-make');
  await page.waitForFunction(
    () => /کمپینِ پنل/.test(document.getElementById('cmp-body-list').textContent), null, { timeout: 15_000 }
  );
  const camp = await one(`SELECT * FROM campaigns WHERE name='کمپینِ پنل'`);
  assert.ok(camp, 'کمپین باید ساخته شده باشد');
  const code = await one(`SELECT * FROM discount_codes WHERE code='PANELCMP'`);
  assert.ok(code, 'کدِ کمپین ساخته شد');
  //  و اعلانش واقعاً رفت — همان متن، با کدِ پرشده
  const deliv = await one(
    `SELECT * FROM notice_deliveries WHERE notice_id=$1 AND channel='inapp' LIMIT 1`, [camp.notice_id]);
  assert.ok(deliv, 'اعلانِ کمپین باید فرستاده شده باشد');
  assert.ok(deliv.body.includes('PANELCMP'), `متغیر باید پر شده باشد — ${deliv.body}`);
});

await step('مرکزِ اعلان: شمارِ گیرنده، ساختن و فرستادن، و گزارش', async () => {
  await openTab('notices');
  await page.selectOption('#nt-app', 'shop');
  await page.selectOption('#nt-kind', 'all');
  await page.click('#btn-nt-count');
  await page.waitForFunction(
    () => /گیرنده/.test(document.getElementById('nt-count').textContent), null, { timeout: 10_000 }
  );

  await page.fill('#nt-title', 'اعلانِ آزمونِ پنل');
  await page.fill('#nt-body', 'سلام {نام}، این یک آزمون است.');
  await page.uncheck('#nt-email');
  page.once('dialog', (d) => d.accept());
  await page.click('#btn-nt-send');
  //  ⚠️ منتظرِ پیامِ **پایانِ ارسال** بمانید، نه دیده شدنِ ردیف: ردیف با
  //  ذخیرهٔ پیش‌نویس (پیش از ارسال) هم می‌آید و آن‌وقت وضعیت هنوز
  //  `sending` است — همین یک بار سنجه را سرخِ دروغ کرد.
  await page.waitForFunction(
    () => /گیرنده/.test(document.getElementById('nt-msg').textContent),
    null, { timeout: 15_000 }
  );

  const n = await one(`SELECT * FROM notices WHERE title='اعلانِ آزمونِ پنل'`);
  assert.ok(n, 'اعلان باید ساخته شده باشد');
  assert.equal(n.status, 'sent');
  const deliv = await one(
    `SELECT * FROM notice_deliveries WHERE notice_id=$1 LIMIT 1`, [n.id]);
  assert.ok(deliv, '⛔ یک ردیفِ تحویل برای هر گیرنده در هر کانال');
  assert.ok(deliv.title.includes('اعلانِ آزمونِ پنل'));
  assert.ok(deliv.body.includes('سلام'), 'متغیرها پر شده‌اند');
});

await step('قالبِ آمادهٔ اعلان از پنل ویرایش می‌شود', async () => {
  await openTab('notices');
  await page.waitForFunction(
    () => document.querySelectorAll('#nt-templates tr').length > 0, null, { timeout: 10_000 }
  );
  const rowIndex = await page.$$eval('#nt-templates tr', (trs) =>
    trs.findIndex((t) => t.children[0].textContent.trim() === 'welcome'));
  assert.ok(rowIndex >= 0, 'قالبِ welcome باید در جدول باشد');
  const sel = `#nt-templates tr:nth-child(${rowIndex + 1})`;
  await page.fill(`${sel} input`, 'خوش آمدی از پنل');
  await page.click(`${sel} button`);
  await page.waitForFunction(
    () => /ذخیره شد/.test(document.getElementById('tpl-msg').textContent), null, { timeout: 10_000 }
  );
  const row = await one(`SELECT * FROM notice_templates WHERE key='welcome' ORDER BY app LIMIT 1`);
  assert.ok(row.title.includes('خوش آمدی از پنل') || row.title === 'خوش آمدی از پنل',
    `عنوانِ قالب باید عوض شده باشد — ${row.title}`);
});

// ── §۳۸.۹: حداقل نسخهٔ پشتیبانی‌شده از همان فرمِ «نشانیِ دانلود» ──
await step('حداقل نسخه: از پنل ذخیره می‌شود، نادرست رد می‌شود و دوباره خوانده می‌شود', async () => {
  /*
   *  ⚠️ هم بازکردنِ برگه و هم `selectOption` تابعِ `loadDownloads()` را صدا می‌زنند. پاسخِ
   *  دیررسِ آن، فرمِ تازه‌پرشده را با مقدارِ ذخیره‌شده (این‌جا نسخهٔ خالی) عوض می‌کرد؛ PUT
   *  با version='' می‌رفت، سرور «آخرین نسخه»ای برای سنجش نداشت و 9.0.0 هم ۲۰۰ می‌گرفت
   *  (CIِ #296: دو PUT ِ ۲۰۰ و انتظارِ بی‌پایان برای «بالاتر»). پس پیش از پر کردن، هر
   *  خواندنِ /downloads ِ همین گام باید تمام شده و روی فرم نشسته باشد.
   */
  const loads = new Set();
  const onReq = (r) => { if (r.method() === 'GET' && /\/downloads\?app=/.test(r.url())) loads.add(r); };
  const onEnd = (r) => { loads.delete(r); };
  page.on('request', onReq);
  page.on('requestfinished', onEnd);
  page.on('requestfailed', onEnd);
  try {
    await openTab('sales');
    await page.selectOption('#dl-app', 'shop');
    for (let i = 0; loads.size && i < 100; i++) await new Promise((r) => setTimeout(r, 100));
    assert.equal(loads.size, 0, 'خواندنِ /downloads تمام نشد');
  } finally {
    page.off('request', onReq);
    page.off('requestfinished', onEnd);
    page.off('requestfailed', onEnd);
  }
  //  پاسخ رسیده؛ یک دورِ رویدادِ صفحه تا `loadDownloads` آن را روی فرم بنشاند
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  await page.fill('#dl-version', '2.14.2');
  await page.fill('#dl-min', '2.14.0');
  await page.click('#btn-dl-save');
  await page.waitForFunction(() => /ذخیره شد/.test(document.getElementById('dl-msg').textContent), null, { timeout: 10_000 });
  assert.equal((await one(`SELECT value FROM app_config WHERE key='min_app_version_shop'`)).value, '2.14.0');

  //  ۴۰۰ِ عمدی در کنسولِ مرورگر «Failed to load resource» می‌نویسد — همان یکی کنار می‌رود
  const errorsBefore = consoleErrors.length;
  await page.fill('#dl-min', '9.0.0');
  await page.click('#btn-dl-save');
  await page.waitForFunction(() => /بالاتر/.test(document.getElementById('dl-msg').textContent), null, { timeout: 10_000 });
  assert.equal((await one(`SELECT value FROM app_config WHERE key='min_app_version_shop'`)).value, '2.14.0');
  const extra = consoleErrors.splice(errorsBefore);
  assert.ok(extra.every((e) => /400/.test(e)), extra.join(' | '));

  await page.fill('#dl-min', '');
  await openTab('sales');
  assert.equal(await page.inputValue('#dl-min'), '2.14.0', 'پس از بازخوانی همان مقدارِ ذخیره‌شده');
});

await step('همگام‌سازی و امنیت: دستگاه‌ها، خطاها و درخواست‌های ورود بی خطا پر می‌شوند', async () => {
  await openTab('health');
  await page.waitForTimeout(400);
  for (const id of ['health-devices', 'health-conflicts', 'health-errors', 'health-logins']) {
    const rows = await page.locator(`#${id} tr`).count();
    assert.ok(rows >= 1, `${id} خالی ماند`);
  }
  //  §۳۸.۸، OPS-01@38: کارت‌های سلامتِ سرور با وضعیتِ متنی (نه فقط رنگ)
  await page.waitForFunction(() => document.querySelectorAll('#sys-health .stat').length === 7, null, { timeout: 10_000 });
  assert.match((await page.locator('#sys-overall').textContent()).trim(), /^● (سالم|هشدار|خراب|نامعلوم)$/);
  const cardsText = await page.locator('#sys-health').textContent();
  assert.match(cardsText, /دیتابیس/);
  assert.ok(!/postgres(ql)?:\/\//i.test(cardsText), 'نشانیِ دیتابیس در کارت‌ها');
  const csp = await page.evaluate(async () => (await fetch('/admin/')).headers.get('content-security-policy'));
  assert.match(csp || '', /script-src 'self'/);
});

// ── OPS-04@29: سرویسِ آزمایشی «زنده» معرفی نمی‌شود — روشن و تاریک ──
await step('عملیات: سرویس‌های بیرونی با برچسبِ «آزمایشی»، نه «زنده» (روشن و تاریک)', async () => {
  const badgeOf = (id) => page.locator(`#ops-providers tr[data-provider="${id}"] .badge`).textContent();
  const look = () => page.evaluate(() => {
    const b = document.querySelector('#ops-providers tr[data-provider="sms"] .badge');
    return { badge: getComputedStyle(b).backgroundColor, body: getComputedStyle(document.body).backgroundColor };
  });
  await openTab('ops');
  await page.waitForFunction(() => document.querySelectorAll('#ops-providers tr').length === 5, null, { timeout: 10_000 });
  //  هر ردیف همان حالتِ سرور را می‌گوید؛ راهِ «لاگ» همیشه «آزمایشی»
  const FA = { live: 'زنده', mock: 'آزمایشی', incomplete: 'ناقص', off: 'خاموش', none: 'ندارد', unknown: 'نامعلوم' };
  const api = await page.evaluate(async () => (await fetch('/api/admin/system/providers', {
    headers: { Authorization: `Bearer ${sessionStorage.getItem('shop-admin-token')}` },
  })).json());
  for (const p of api.providers) {
    const shown = (await badgeOf(p.id)).trim();
    assert.equal(shown, FA[p.state], `${p.id}`);
    if (p.mode === 'log') assert.equal(shown, 'آزمایشی', `${p.id}: راهِ لاگ`);
  }
  //  پیامکِ آزمون فقط لاگ است؛ پوش خاموش؛ پرداختِ آنلاین ندارد
  assert.equal((await badgeOf('sms')).trim(), 'آزمایشی');
  assert.equal((await badgeOf('push')).trim(), 'خاموش');
  assert.equal((await badgeOf('payment')).trim(), 'ندارد');
  assert.match(await page.locator('#ops-prov-msg').textContent(), /آزمایشی/);
  const light = await look();
  if (process.env.PANEL_SHOTS) await page.screenshot({ path: `${process.env.PANEL_SHOTS}/ops-04-29-light.png` });
  await page.emulateMedia({ colorScheme: 'dark' });
  try {
    const dark = await look();
    assert.notEqual(dark.badge, light.badge, 'برچسب در حالتِ تاریک همان رنگِ روشن ماند');
    assert.notEqual(dark.body, light.body, 'زمینه در حالتِ تاریک عوض نشد');
    if (process.env.PANEL_SHOTS) await page.screenshot({ path: `${process.env.PANEL_SHOTS}/ops-04-29-dark.png` });
  } finally {
    await page.emulateMedia({ colorScheme: 'light' });
  }
  //  پیامکِ واقعی که تنظیم شود ⇒ «زنده»؛ و برمی‌گردد
  const smsSettings = require('../src/lib/sms-settings');
  await smsSettings.save({ provider: 'sms', url: 'https://sms.panel.test/{to}' });
  try {
    await openTab('ops');
    await page.waitForFunction(() => /زنده/.test(document.querySelector('#ops-providers tr[data-provider="sms"] .badge')?.textContent || ''), null, { timeout: 10_000 });
    assert.ok(!(await page.locator('#ops-providers').textContent()).includes('sms.panel.test'), 'نشانیِ سرویس در پنل');
  } finally {
    await smsSettings.save({ provider: 'log', url: '' });
  }
});

// ── OPS-04: داشبوردِ نسخهٔ دستگاه‌ها و هشدارِ زیرِ حداقل ───────────
await step('عملیات: نسخهٔ دستگاه‌ها با هشدارِ قرمزِ «زیرِ حداقل»', async () => {
  const plans = require('../src/lib/plans');
  const before = await plans.allConfig();
  const t = now();
  await query(
    `INSERT INTO sync_devices (app, account_kind, account_id, device_id, app_version, last_seen_at)
     VALUES ('shop','shop','ops-acc','ops-dev-1','2.13.0',$1), ('shop','shop','ops-acc','ops-dev-2','2.14.2',$1)`,
    [t - 60_000]
  );
  await plans.setConfig('latest_version_shop', '2.14.2');
  await plans.setConfig('min_app_version_shop', '2.14.0');
  try {
    await openTab('ops');
    await page.waitForSelector('#ops-versions tr[data-version="2.13.0"]', { timeout: 10_000 });
    const badge = (v) => page.locator(`#ops-versions tr[data-app="shop"][data-version="${v}"] .badge`).textContent();
    assert.equal((await badge('2.13.0')).trim(), 'زیرِ حداقل');
    assert.equal((await badge('2.14.2')).trim(), 'به‌روز');
    const alert = page.locator('#ops-ver-msg .msg');
    assert.match(await alert.textContent(), /۱ دستگاهِ دکان زیرِ حداقل نسخهٔ 2\.14\.0/);
    assert.ok((await alert.getAttribute('class')).includes('msg-bad'), 'هشدارِ زیرِ حداقل باید قرمز باشد');
    assert.ok(!(await page.locator('#ops-versions').textContent()).includes('ops-acc'), 'شناسهٔ حساب در پنل');
    if (process.env.PANEL_SHOTS) await page.screenshot({ path: `${process.env.PANEL_SHOTS}/ops-04-versions.png`, fullPage: true });
  } finally {
    await query(`DELETE FROM sync_devices WHERE account_id='ops-acc'`);
    await plans.setConfig('latest_version_shop', before.latest_version_shop || '');
    await plans.setConfig('min_app_version_shop', before.min_app_version_shop || '');
  }
});

// ── SEC-02: بررسیِ زنجیرهٔ هش از پنل — سالم، بعد دست‌کاری‌شده ──────────
await step('عملیات: بررسیِ زنجیرهٔ هش؛ دست‌کاری با نخستین حلقهٔ شکسته و دلیل دیده می‌شود', async () => {
  const store = require('../src/lib/hash-chain-store');
  const live = require('../src/lib/live-config');
  await live.set(store.FLAG_SCOPE, store.FLAG_AUDIT, true, 'panel-test');
  const tamper = async (sql, params) => {
    await query('ALTER TABLE hash_chain DISABLE TRIGGER hash_chain_immutable');
    try { await query(sql, params); } finally { await query('ALTER TABLE hash_chain ENABLE TRIGGER hash_chain_immutable'); }
  };
  try {
    const audit = require('../src/lib/audit');
    for (const action of ['sale.create', 'sale.void']) {
      await audit.log({ shopId: 'shp_panel_chain', actorType: 'user', userId: 'usr_panel', action, detail: { n: 1 } });
    }
    const chain = store.auditChainId({ shopId: 'shp_panel_chain', actorType: 'user', userId: 'usr_panel' });
    await openTab('ops');
    await page.waitForSelector('#ops-chain-tools:not(.hidden)', { timeout: 10_000 });
    await page.fill('#ops-chain-name', chain);
    await page.click('#btn-ops-chain');
    await page.waitForSelector(`#ops-chains tr[data-chain="${chain}"]`, { timeout: 10_000 });
    assert.match(await page.locator('#ops-chain-msg').textContent(), /✔/);
    assert.match(await page.locator(`#ops-chains tr[data-chain="${chain}"]`).textContent(), /سالم.*سر:/);

    const two = (await one('SELECT body FROM hash_chain WHERE chain=$1 AND seq=2', [chain])).body;
    await tamper('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=2', [chain, two.replace('"sale.void"', '"sale.keep"')]);
    try {
      await page.click('#btn-ops-chain');
      await page.waitForFunction(() => /✖/.test(document.getElementById('ops-chain-msg').textContent), null, { timeout: 10_000 });
      const row = await page.locator(`#ops-chains tr[data-chain="${chain}"]`).textContent();
      assert.match(row, /شکسته/);
      assert.match(row, /#۲: متنِ حلقه با هشش نمی‌خواند \(hash_mismatch\)/);
      assert.ok((await page.locator('#ops-chain-msg .msg').getAttribute('class')).includes('msg-bad'));
      if (process.env.PANEL_SHOTS) await page.screenshot({ path: `${process.env.PANEL_SHOTS}/sec-02-chain.png`, fullPage: true });
    } finally {
      await tamper('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=2', [chain, two]);
    }
    assert.ok(await one(`SELECT 1 FROM audit_logs WHERE action='admin.chain_verified'`), 'بررسی سابقه نداشت');
  } finally {
    await live.set(store.FLAG_SCOPE, store.FLAG_AUDIT, false, 'panel-test');
  }
});

// ── F0-27: کانالِ آزمایشی — مدیرِ ارشد از پنل دکان و دستگاه را می‌افزاید ──
await step('کانالِ آزمایشی: ذخیره در live_config و سابقه؛ شناسهٔ ناجور پیامِ قرمز و بی‌تغییر', async () => {
  await openTab('plans');
  await page.waitForSelector('#beta-panel:not(.hidden)', { timeout: 10_000 });
  await page.fill('#beta-shops', 'shop_panel_1\nshop_panel_2');
  await page.fill('#beta-devices', 'dev_panel_1, dev_panel_1');
  await page.fill('#beta-reason', 'آزمونِ پنل');
  await page.click('#btn-beta-save');
  await page.waitForSelector('#beta-msg .msg-ok', { timeout: 10_000 });
  const val = async (key) => {
    const r = await one(`SELECT value FROM live_config WHERE scope='release' AND key=$1`, [key]);
    return typeof r.value === 'string' ? JSON.parse(r.value) : r.value;
  };
  assert.deepEqual(await val('beta.shops'), ['shop_panel_1', 'shop_panel_2']);
  assert.deepEqual(await val('beta.devices'), ['dev_panel_1']);
  const a = await one(`SELECT detail FROM audit_logs WHERE action='admin.release_beta_set' ORDER BY created_at DESC LIMIT 1`);
  const d = typeof a.detail === 'string' ? JSON.parse(a.detail) : a.detail;
  assert.equal(d.reason, 'آزمونِ پنل');
  assert.equal(d.shops.added, 'shop_panel_1, shop_panel_2');
  assert.match(await page.textContent('#beta-shops-meta'), /relroot|admin/);

  await page.fill('#beta-shops', 'bad id!');
  await page.click('#btn-beta-save');
  await page.waitForSelector('#beta-msg .msg-bad', { timeout: 10_000 });
  assert.deepEqual(await val('beta.shops'), ['shop_panel_1', 'shop_panel_2'], 'شناسهٔ ناجور چیزی را عوض نکرد');
});

await step('کانالِ آزمایشی: دکمهٔ صفحهٔ دکان همان دکان را می‌افزاید', async () => {
  await openTab('shops');
  await page.click('#shops-body tr:first-child button');
  await page.waitForSelector('#shop-detail:not(.hidden) #btn-shop-beta', { timeout: 10_000 });
  const id = (await page.textContent('#shop-id')).trim();
  await page.click('#btn-shop-beta');
  await page.waitForFunction(() => document.querySelector('#btn-shop-beta')?.textContent?.includes('در کانالِ آزمایشی است'), null, { timeout: 10_000 });
  const r = await one(`SELECT value FROM live_config WHERE scope='release' AND key='beta.shops'`);
  const ids = typeof r.value === 'string' ? JSON.parse(r.value) : r.value;
  assert.deepEqual(ids, ['shop_panel_1', 'shop_panel_2', id], 'بقیهٔ فهرست دست نخورد');
});

// ── F0-15: ورودِ دومرحله‌ایِ اختیاری — روشن کردن و ورود با کد، از خودِ پنل ──
await step('ورودِ دومرحله‌ای: از پنل روشن می‌شود، ورود کد می‌خواهد و با کد وارد می‌شود', async () => {
  const totp = require('../src/lib/totp');
  const errorsBefore = consoleErrors.length;
  await page.click('#btn-2fa');
  await page.waitForSelector('#twofa-panel:not(.hidden)', { timeout: 10_000 });
  await page.waitForFunction(() => /خاموش است/.test(document.getElementById('twofa-state').textContent), null, { timeout: 10_000 });
  await page.fill('#twofa-pass', 'Admin!12345');
  await page.click('#btn-2fa-start');
  await page.waitForSelector('#twofa-setup:not(.hidden)', { timeout: 10_000 });
  const secret = (await page.textContent('#twofa-secret')).replace(/\s/g, '');
  assert.match(secret, /^[A-Z2-7]{32}$/);
  assert.match(await page.getAttribute('#twofa-link', 'href'), /^otpauth:\/\/totp\//);
  await page.fill('#twofa-code', totp.generate(secret));
  await page.click('#btn-2fa-enable');
  await page.waitForSelector('#twofa-codes-box:not(.hidden)', { timeout: 10_000 });
  const codes = (await page.textContent('#twofa-codes')).trim().split('\n');
  assert.equal(codes.length, 10);
  if (process.env.PANEL_SHOTS) await page.screenshot({ path: `${process.env.PANEL_SHOTS}/f0-15-enabled.png` });

  //  بیرون و دوباره تو: اول «کد لازم است»، بعد با کدِ گامِ بعدی (همان کد دو بار پذیرفته نمی‌شود)
  await page.click('#btn-logout');
  await page.waitForSelector('#login-view:not(.hidden)', { timeout: 10_000 });
  await page.fill('#in-username', 'admin');
  await page.fill('#in-password', 'Admin!12345');
  await page.click('#btn-login');
  await page.waitForSelector('#totp-row:not(.hidden)', { timeout: 10_000 });
  if (process.env.PANEL_SHOTS) await page.screenshot({ path: `${process.env.PANEL_SHOTS}/f0-15-login-code.png` });
  const next = totp.hotp(totp.base32Decode(secret), totp.stepAt() + 1);
  await page.fill('#in-totp', next);
  await page.click('#btn-login');
  await page.waitForSelector('#app-view:not(.hidden)', { timeout: 10_000 });

  //  پاک‌کاری: خاموش با کدِ بازیابی، تا اجرای بعدی همان باشد
  await page.click('#btn-2fa');
  await page.waitForSelector('#twofa-panel:not(.hidden)', { timeout: 10_000 });
  await page.fill('#twofa-pass', 'Admin!12345');
  await page.fill('#twofa-code', codes[0]);
  page.once('dialog', (d) => d.accept());
  await page.click('#btn-2fa-disable');
  await page.waitForFunction(() => /خاموش است/.test(document.getElementById('twofa-state').textContent), null, { timeout: 10_000 });
  //  ۴۰۱ِ گامِ اولِ ورود («کد لازم است») خطای صفحه نیست — همان پیامِ مرورگر برای پاسخِ ۴۰۱ است
  const extra = consoleErrors.splice(errorsBefore);
  assert.deepEqual(extra.filter((m) => !/status of 401/.test(m)), []);
});

await step('در کلِ این نشست هیچ خطای صفحه‌ای نبود', async () => {
  assert.deepEqual(consoleErrors, [], consoleErrors.join(' | '));
});

await browser.close();
await new Promise((r) => server.close(r));
await closeDb();

console.log('\n── پنلِ مدیریت ─────────────────────────────');
for (const [mark, name] of results) console.log(`${mark}  ${name}`);
console.log(`\n${results.length - failures}/${results.length} سبز`);
process.exit(failures ? 1 : 0);
