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
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);

/*
 *  ⚠️ پلی‌رایت روی این ریپو نصب نیست و نباید بشود: سرور عمداً فقط دو
 *  وابستگی دارد (`express` و `pg`) تا روی هر هاستی و بی اینترنتِ npm
 *  بالا بیاید. پس از هر جایی که باشد برداشته می‌شود — نصبِ سراسری،
 *  `node_modules`ِ خودِ ریپو، یا مسیرِ `NODE_PATH`.
 *
 *  و اگر هیچ‌جا نبود، سنجه **رد** می‌شود نه شکست: کسی که فقط سرور را
 *  عوض کرده نباید به‌خاطر نبودِ مرورگر قرمز ببیند.
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

const openTab = async (name) => {
  await page.click(`.tab[data-tab="${name}"]`);
  await page.waitForSelector(`#tab-${name}:not(.hidden)`, { timeout: 10_000 });
  await page.waitForTimeout(400);
};

// ── ۲) هر تب باز می‌شود و خطای جاوااسکریپت نمی‌دهد ────────────────
await step('هر هشت تب بی خطا باز می‌شود', async () => {
  for (const t of ['shops', 'pump', 'users', 'subs', 'requests', 'plans', 'support', 'delivery', 'backups', 'audit']) {
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
  const state = await page.textContent('#mail-state');
  assert.ok(!state.includes('فقط لاگ'), 'دیگر روی حالتِ لاگ نیست');
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
