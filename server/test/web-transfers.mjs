/**
 * «انتقالِ موجودی» ِ نسخهٔ وب (INV-11، S21؛ `license/stock-transfers.js`) — سرورِ واقعی، دیتابیسِ واقعی (PGlite یا
 * Postgres)، کرومیومِ واقعی؛ هیچ پاسخی ساختگی نیست.
 *
 *   • دکانِ پرچم‌خاموش: در صفحهٔ «انبار» نه دکمه، نه پنجره.
 *   • دکانِ پرچم‌روشن (صاحب): فهرست، پیش‌نویسِ ماندگار (بازگشایی پس از بستن)، فرستادن با قطعِ نت وسطِ ثبت ⇒ «فرستادن»
 *     دوباره و فقط یک انتقال؛ دریافتِ ناقص ⇒ «نیمه‌رسیده»؛ بستن با کسری بی دلیل نمی‌رود و با دلیل «رسید»؛ ردِ بی دلیل نه،
 *     با دلیل ماندهٔ در مسیر به مبدأ. مانده‌ها از خودِ سرور سنجیده می‌شوند.
 *   • صندوق‌دار (`stock.view` بی `stock.move`): فقط دیدن — نه «انتقالِ تازه»، نه دریافت/رد.
 *   • گوشی (۳۷۵px): صفحه افقی بیرون نمی‌زند؛ axe (اگر هست) بر پنجرهٔ باز.
 *   • INV-01 «موجودیِ هر مکان»: هر کالا در قفسه، انبار، خراب و در مسیر و جمع — عیناً پاسخِ خودِ سرور؛ جست‌وجو؛ نامِ کالا متن.
 *   • زبانِ صفحه (`web-i18n.js`) بی بارگذاریِ دوباره: پنجرهٔ باز، دکمه و فرمِ دریافت با هر زبان عوض می‌شوند، آنچه در فرم
 *     نوشته شده می‌ماند، نامِ کالا داده است، و برگشت به فارسی همان متنِ پیشین.
 *
 * اجرا:  TEST_DATABASE_URL=pglite:memory node test/web-transfers.mjs
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, closeSafeRoute } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [`${globalRoot}/playwright`, 'playwright']) {
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-transfers-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, one, many, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const liveConfig = require('../src/lib/live-config');

const ROOT = path.join(import.meta.dirname, '..', '..');
const XC = require(path.join(ROOT, 'license', 'stock-transfers.js'));
let failures = 0;
const results = [];
async function step(name, fn) {
  console.log(`… ${name}`);
  try { await fn(); results.push(['✅', name]); }
  catch (err) { failures++; results.push(['❌', `${name} — ${err.message}`]); }
}

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();

const app = await createApp({ runMigrations: false });
const api = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const apiBase = `http://127.0.0.1:${api.address().port}`;

/* ---- وب‌سرورِ ایستا؛ نشانیِ سرور در <meta> و CSP همان کارِ web-orders.mjs ---- */
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png' };
const site = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html';
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404).end('no'); return; }
  if (rel === 'index.html') {
    const src = fs.readFileSync(file, 'utf8');
    const real = (src.replace(/<!--[\s\S]*?-->/g, '').match(/<meta name="tohid-api-base" content="([^"]+)">/) || [])[1];
    const html = src
      .replace(/<meta name="tohid-api-base" content="[^"]*">/g, `<meta name="tohid-api-base" content="${apiBase}">`)
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

/* ---- حساب‌ها و داده از همان راه‌های واقعی ---- */
const call = async (method, p, b, t, headers = {}) => {
  const r = await fetch(apiBase + p, {
    method,
    headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
    body: b === undefined ? undefined : JSON.stringify(b),
  });
  return { status: r.status, body: await r.json().catch(() => null) };
};
const post = (p, b, t, h) => call('POST', p, b, t, h);
async function account(email, name) {
  const password = 'Passw0rd!test';
  const started = await post('/api/auth/register/start', { name, email, password });
  const verified = await post('/api/auth/register/verify', { email, code: started.body.devCode });
  const done = await post('/api/auth/register/complete', {
    ticket: verified.body.ticket, name, password,
    device: { deviceId: `dev-${email.split('@')[0]}`, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true },
  });
  assert.ok(done.body && done.body.accessToken, `ثبت‌نامِ ${email}: ${JSON.stringify(done.body)}`);
  return done.body;
}
const owner = await account('xfr-on@panel.local', 'صاحبِ انبار');
const shopOn = (await post('/api/shop', { name: 'دکانِ انتقال' }, owner.accessToken)).body.shop.id;
const ownerOff = await account('xfr-off@panel.local', 'صاحبِ خاموش');
await post('/api/shop', { name: 'دکانِ بی‌انتقال' }, ownerOff.accessToken);
const cashier = await account('xfr-cash@panel.local', 'صندوق‌دار');
await query(
  `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
   VALUES ($1,$2,$3,'cashier','active',$4,$4)`,
  [newId('mem'), shopOn, cashier.user.id, now()],
);
await liveConfig.set(String(shopOn), 'shop.catalog', true);

let opN = 0;
const opId = () => `01JXW${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
const pushed = await post('/api/sync/v1/push', {
  device_id: 'dev-xfr-on', schema_version: 2, ops: [
    op('products', 'rice', { name: 'برنج', unit: 'بوجی', salePrice: 50, purchasePrice: 40, active: true, barcodes: ['111'], createdAt: Date.now() }),
    op('warehouseEntries', 'rice-e', { productId: 'rice', units: 10, cartons: 0, perCarton: 0, unit: 'بوجی', price: 40, createdAt: Date.now() }),
    op('products', 'oil', { name: 'روغن', unit: 'قوطی', salePrice: 90, purchasePrice: 70, active: true, barcodes: ['222'], createdAt: Date.now() }),
    op('warehouseEntries', 'oil-e', { productId: 'oil', units: 4, cartons: 0, perCarton: 0, unit: 'قوطی', price: 70, createdAt: Date.now() }),
    //  نامِ کالا دادهٔ دفتر است (از هر دستگاه) ⇒ همیشه متن، هرگز HTML
    op('products', 'xss', { name: 'زهر <img src=x id="xss-probe">', unit: 'دانه', salePrice: 1, purchasePrice: 1, active: true, barcodes: [], createdAt: Date.now() }),
  ],
}, owner.accessToken, { 'X-App': 'shop', 'X-Device': 'dev-xfr-on' });
assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
const levels = async (pid) => {
  const r = await call('GET', `/api/shop/catalog/products/${pid}`, undefined, owner.accessToken);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const l = r.body.product.stock.levels;
  return { floor: l.shop_floor, wh: l.warehouse, transit: l.in_transit };
};
assert.deepEqual(await levels('rice'), { floor: 10000, wh: 0, transit: 0 });
const transfers = () => many('SELECT * FROM stock_transfers WHERE shop_id=$1 ORDER BY created_at', [shopOn]);

/* ---- مرورگر ---- */
const browser = await chromium.launch();
const pageErrors = [];
async function openAs(acct, { width = 1366 } = {}) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, serviceWorkers: 'block' });
  await ctx.addInitScript(([bundle]) => {
    localStorage.setItem('tohid-license-v1', JSON.stringify(bundle));
    localStorage.setItem('tohid-unlocked-v1', '1');
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'email', value: bundle.userLabel || '', name: bundle.userLabel || '', loginAt: 1 }));
  }, [{
    accessToken: acct.accessToken, refreshToken: acct.refreshToken, accessExpiresAt: Date.now() + 3600_000,
    userLabel: acct.user && acct.user.name, userId: acct.user && acct.user.id,
  }]);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => pageErrors.push(String(e.message)));
  await page.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => !!window.TohidStockTransfers);
  await page.evaluate(() => document.getElementById('app-root')?.classList.remove('app-hidden'));
  await page.evaluate(() => window.goTo('warehouse'));
  return { ctx, page };
}

await step('پرچمِ خاموش: صفحهٔ «انبار» بی دکمه و بی پنجره', async () => {
  const { ctx, page } = await openAs(ownerOff);
  await page.waitForTimeout(1500);
  assert.equal(await page.locator('#xfr-btn').count(), 0);
  assert.equal(await page.locator('#xfr-modal').count(), 0);
  assert.equal(await page.evaluate(() => window.TohidStockTransfers.enabled), false);
  await ctx.close();
});

const { page } = await openAs(owner);

await step('پرچمِ روشن: دکمه در «انبار»، پنجره با «در مسیر» ِ تهی', async () => {
  await page.locator('#page-warehouse #xfr-btn').waitFor({ timeout: 10000 });
  assert.equal((await page.locator('#xfr-btn').innerText()).trim(), 'انتقالِ موجودی');
  await page.click('#xfr-btn');
  await page.waitForSelector('#xfr-modal.open');
  //  «در حالِ خواندن…» هم `.xfr-empty` است ⇒ منتظرِ پایانِ خواندن (پاسخِ نخستِ `/transfers` گاهی کند است)، نه فقط همان کلاس
  await page.waitForFunction(() => !/در حالِ خواندن/.test(document.querySelector('#xfr-body')?.textContent || '') && !!document.querySelector('#xfr-body .xfr-empty'), null, { timeout: 10000 });
  assert.match(await page.locator('#xfr-body').innerText(), /هیچ انتقالی در مسیر نیست/);
  assert.equal(await page.locator('[data-xfr="new"]').count(), 1);
});

await step('پیش‌نویس: جست‌وجو، افزودن، مقدار با رقمِ فارسی؛ پس از بستن و بازگشایی همان می‌ماند', async () => {
  await page.click('[data-xfr="new"]');
  await page.waitForSelector('#xfr-draft');
  assert.equal(await page.locator('#xfr-draft select[name="from"]').inputValue(), 'shop_floor');
  assert.equal(await page.locator('#xfr-draft select[name="to"]').inputValue(), 'warehouse');
  await page.fill('#xfr-draft input[name="q"]', 'زهر');
  await page.click('[data-xfr="find"]');
  await page.waitForSelector('.xfr-found li');
  assert.equal(await page.locator('#xss-probe').count(), 0, 'نامِ کالا HTML نشد');
  assert.match(await page.locator('.xfr-found').innerText(), /<img src=x/);
  //  جست‌وجوی دوم با Enter: جست‌وجو است، نه «فرستادن»؛ فهرستِ جست‌وجوی پیشین همان لحظه می‌رود (پاسخِ کهنه هرگز
  //  جای پاسخِ تازه نمی‌نشیند و «افزودن» روی کالای پیشین زده نمی‌شود)
  await page.fill('#xfr-draft input[name="q"]', 'برنج');
  await page.press('#xfr-draft input[name="q"]', 'Enter');
  await page.waitForFunction(() => /برنج/.test(document.querySelector('.xfr-found')?.textContent || ''));
  const found = await page.locator('.xfr-found').innerText();
  assert.doesNotMatch(found, /زهر/, 'نتیجهٔ جست‌وجوی پیشین ماند');
  assert.match(found, /در مبدأ: 10/);
  assert.equal(await page.locator('#xfr-draft input[name="q"]').evaluate((el) => el === document.activeElement), true, 'کادرِ جست‌وجو فوکوس را نگه داشت');
  assert.equal((await transfers()).length, 0, 'Enter انتقالی نفرستاد');
  await page.click('.xfr-found [data-xfr="add"]');
  await page.fill('#xfr-draft input[name="xfr-qty"]', '۴');
  await page.fill('#xfr-draft input[name="note"]', 'برای انبارِ پشتی');
  //  مبدأ و مقصدِ یکی ⇒ خطای روشن، هیچ درخواست
  await page.selectOption('#xfr-draft select[name="to"]', 'shop_floor');
  await page.click('#xfr-send');
  assert.match(await page.locator('#xfr-err').innerText(), /مبدأ و مقصد یکی است/);
  await page.selectOption('#xfr-draft select[name="to"]', 'warehouse');
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('#xfr-modal.open'));
  await page.click('#xfr-btn');
  await page.click('[data-xfr="new"]');
  await page.waitForSelector('#xfr-draft input[name="xfr-qty"]');
  //  رقمِ فارسی را خودِ برنامهٔ وب هنگامِ تایپ لاتین می‌کند (web-digits)؛ پیش‌نویس همان را نگه داشت
  assert.equal(await page.locator('#xfr-draft input[name="xfr-qty"]').inputValue(), '4');
  assert.equal(await page.locator('#xfr-draft input[name="note"]').inputValue(), 'برای انبارِ پشتی');
  assert.equal((await transfers()).length, 0);
});

await step('فرستادن: قطعِ نت وسطِ ثبت ⇒ «فرستادن» دوباره، فقط یک انتقال؛ کالا «در مسیر»', async () => {
  let dropped = 0;
  await page.route('**/api/v1/shop/catalog/transfers', closeSafeRoute(async (route) => {
    if (route.request().method() === 'POST' && !dropped) {
      dropped++;
      await route.fetch();            // سرور واقعاً ثبت می‌کند…
      return route.abort('failed');   // …ولی پاسخ به مرورگر نمی‌رسد
    }
    return route.fallback();
  }));
  await page.click('#xfr-send');
  await page.waitForFunction(() => /اتصال قطع شد/.test(document.getElementById('xfr-err').textContent));
  assert.equal((await transfers()).length, 1, 'سرور همان بارِ اول ثبت کرده بود');
  await page.click('#xfr-send');
  await page.waitForSelector('#xfr-list .xfr-row');
  await page.unroute('**/api/v1/shop/catalog/transfers');
  const rows = await transfers();
  assert.equal(rows.length, 1, 'تکرارِ همان فرم انتقالِ دوم نساخت');
  assert.match(rows[0].send_key, /^webxfr-/);
  assert.equal(rows[0].note, 'برای انبارِ پشتی');
  assert.deepEqual(await levels('rice'), { floor: 6000, wh: 0, transit: 4000 });
  const row = await page.locator('#xfr-list .xfr-row').first().innerText();
  assert.match(row, /قفسهٔ فروشگاه ⇐ انبار/);
  assert.match(row, /در مسیر: 4/);
  //  پیش‌نویسِ فرستاده پاک شد
  assert.equal(await page.evaluate(() => localStorage.getItem('tohid-stock-transfer-draft-v1')), null);
});

await step('زبان بی بارگذاریِ دوباره: پنجره، دکمه و فرمِ باز با هر زبان؛ نوشتهٔ فرم می‌ماند؛ فارسی همان', async () => {
  const dict = (l) => JSON.parse(fs.readFileSync(path.join(ROOT, 'license', 'i18n', `transfers.${l}.json`), 'utf8'));
  const D = { fa: dict('fa'), en: dict('en'), ps: dict('ps') };
  const dari = require('../../license/dari.js');
  const LETTER = /[\u0621-\u064A\u066E-\u06D3\u06D5\u06FA-\u06FF]/;
  const ui = () => page.evaluate(() => ({
    title: document.getElementById('xfr-title').textContent,
    btn: document.querySelector('#xfr-btn span').textContent,
    close: document.querySelector('#xfr-modal .modal-close').getAttribute('aria-label'),
    head: (document.querySelector('.xfr-head') || {}).textContent || '',
    recv: (document.querySelector('#xfr-recv h4') || {}).textContent || '',
    when: [...document.querySelectorAll('.xfr-when, .xfr-info span')].map((e) => e.textContent).join(' | '),
    name: (document.querySelector('.xfr-table td[data-raw]') || {}).textContent || '',
    got: (document.querySelector('#xfr-recv input[name="xfr-got"]') || {}).value,
    final: (document.querySelector('#xfr-recv input[name="final"]') || {}).checked,
    reason: (document.querySelector('#xfr-recv input[name="reason"]') || {}).value,
  }));
  const to = async (l, title) => {
    await page.evaluate((l) => window.TohidWebI18n.setLang(l), l);
    await page.waitForFunction((t) => document.getElementById('xfr-title').textContent === t, title, { timeout: 10000 });
    await page.waitForTimeout(100);
    return ui();
  };
  //  فهرست در انگلیسی، با تاریخِ میلادی و بی هیچ حرفِ فارسی
  const list = await to('en', D.en.title);
  assert.equal(list.btn, D.en.btn);
  assert.equal(list.close, D.en.close);
  assert.ok(!LETTER.test(await page.locator('#xfr-body').innerText()), 'انگلیسی: هیچ حرفِ فارسی در فهرست');
  assert.match(list.when, /^[A-Z][a-z]{2} \d{1,2}, \d{2}:\d{2}$/, `تاریخِ میلادی: ${list.when}`);
  //  جزئیات و فرمِ دریافتِ نیمه‌نوشته
  await page.click('#xfr-list .xfr-row');
  await page.waitForSelector('.xfr-table');
  await page.click('[data-xfr="act"][data-act="receive"]');
  await page.waitForSelector('#xfr-recv');
  await page.fill('#xfr-recv input[name="xfr-got"]', '3');
  await page.uncheck('#xfr-recv input[name="final"]');
  await page.fill('#xfr-recv input[name="reason"]', 'نیمه‌نوشته');
  const ps = await to('ps', D.ps['detail.title']);
  assert.equal(ps.btn, D.ps.btn);
  assert.deepEqual([ps.got, ps.final, ps.reason], ['3', false, 'نیمه‌نوشته'], 'نوشتهٔ فرم ماند');
  assert.equal(ps.name, 'برنج', 'نامِ کالا داده است');
  assert.ok(ps.head.includes(D.ps['loc.shop_floor']), ps.head);
  //  دری = همان واژه‌نامهٔ فارسی از لایهٔ واژه
  const prs = await to('prs', dari.t(D.fa['detail.title']));
  assert.ok(prs.recv.includes('گدام') && !prs.recv.includes('انبار'), `دری: ${prs.recv}`);
  assert.deepEqual([prs.got, prs.final, prs.reason], ['3', false, 'نیمه‌نوشته']);
  const fa = await to('fa', D.fa['detail.title']);
  assert.equal(fa.btn, 'انتقالِ موجودی');
  assert.equal(fa.close, D.fa.close);
  assert.match(fa.head, /قفسهٔ فروشگاه ⇐ انبار/);
  assert.equal(fa.recv, D.fa['recv.title'].replace('{to}', D.fa['loc.warehouse']));
  assert.deepEqual([fa.got, fa.final, fa.reason], ['3', false, 'نیمه‌نوشته']);
  assert.ok(!/[A-Za-z]{3}/.test(fa.when), `فارسی: تاریخِ خورشیدیِ پیشین: ${fa.when}`);
  //  همان حالِ پیش از این گام برای گام‌های بعد: فهرستِ باز
  await page.click('[data-xfr="act-cancel"]');
  await page.waitForFunction(() => !document.getElementById('xfr-recv'));
  await page.click('[data-xfr="back"]');
  await page.waitForSelector('#xfr-list .xfr-row');
  assert.equal(await page.evaluate(() => localStorage.getItem('tohid-shop-lang-v1')), 'fa');
});

await step('دریافتِ ناقص ⇒ «نیمه‌رسیده»؛ بستن با کسری بی دلیل نمی‌رود، با دلیل «رسید»', async () => {
  await page.click('#xfr-list .xfr-row');
  await page.waitForSelector('.xfr-table');
  assert.match(await page.locator('.xfr-table').innerText(), /برنج/);
  await page.click('[data-xfr="act"][data-act="receive"]');
  await page.waitForSelector('#xfr-recv');
  await page.uncheck('#xfr-recv input[name="final"]');
  await page.fill('#xfr-recv input[name="xfr-got"]', '1');
  //  قطعِ نت پس از ثبتِ سرور ⇒ «ثبتِ دریافت» دوباره همان گام است (همان کلید)، نه دریافتِ دوم
  let dropped = 0;
  await page.route('**/api/v1/shop/catalog/transfers/*/receive', closeSafeRoute(async (route) => {
    if (!dropped) { dropped++; await route.fetch(); return route.abort('failed'); }
    return route.fallback();
  }));
  await page.click('#xfr-act-ok');
  await page.waitForFunction(() => /اتصال قطع شد/.test(document.getElementById('xfr-err').textContent));
  await page.click('#xfr-act-ok');
  await page.waitForFunction(() => /نیمه‌رسیده/.test(document.querySelector('.xfr-head').textContent));
  await page.unroute('**/api/v1/shop/catalog/transfers/*/receive');
  assert.deepEqual(await levels('rice'), { floor: 6000, wh: 1000, transit: 3000 }, 'یک واحد، یک بار');
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM stock_transfer_steps WHERE shop_id=$1 AND kind='receive'", [shopOn])).n, 1);
  await page.click('[data-xfr="act"][data-act="receive"]');
  await page.fill('#xfr-recv input[name="xfr-got"]', '2');
  await page.click('#xfr-act-ok');
  assert.match(await page.locator('#xfr-err').innerText(), /برای کسری دلیل بنویسید/);
  await page.fill('#xfr-recv input[name="reason"]', 'یک بوجی پاره شد');
  await page.click('#xfr-act-ok');
  await page.waitForFunction(() => /رسید/.test(document.querySelector('.xfr-head').textContent) && !document.getElementById('xfr-recv'));
  assert.deepEqual(await levels('rice'), { floor: 6000, wh: 3000, transit: 0 });
  const t = (await transfers())[0];
  assert.equal(t.status, 'received');
  assert.match(await page.locator('.xfr-table').innerText(), /\b1\b/);
  assert.equal(await page.locator('[data-xfr="act"]').count(), 0, 'انتقالِ بسته دکمهٔ دریافت/رد ندارد');
});

await step('رد: بی دلیل نه؛ با دلیل ماندهٔ در مسیر به مبدأ', async () => {
  const r = await post('/api/shop/catalog/transfers', { from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'oil', qtyMilli: 2000 }] },
    owner.accessToken, { 'Idempotency-Key': `api-xfr-${Date.now()}` });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  await page.click('[data-xfr="back"]');
  await page.waitForSelector(`#xfr-list [data-id="${r.body.transfer.id}"]`);
  await page.click(`#xfr-list [data-id="${r.body.transfer.id}"]`);
  await page.click('[data-xfr="act"][data-act="reject"]');
  await page.click('#xfr-act-ok');
  assert.match(await page.locator('#xfr-err').innerText(), /دلیلِ رد را بنویسید/);
  await page.fill('#xfr-rej input[name="reason"]', 'انبار پر است');
  await page.click('#xfr-act-ok');
  await page.waitForFunction(() => /رد شد/.test(document.querySelector('.xfr-head').textContent));
  assert.deepEqual(await levels('oil'), { floor: 4000, wh: 0, transit: 0 });
  //  «همه» هر دو را نشان می‌دهد، «در مسیر» هیچ
  await page.click('[data-xfr="back"]');
  await page.click('[data-xfr="tab"][data-tab="all"]');
  await page.waitForFunction(() => document.querySelectorAll('#xfr-list .xfr-row').length === 2);
  await page.click('[data-xfr="tab"][data-tab="open"]');
  await page.waitForSelector('#xfr-body .xfr-empty');
  if (axeSrc) {
    await page.addScriptTag({ content: axeSrc });
    const v = await page.evaluate(async () => (await window.axe.run('#xfr-modal', { runOnly: ['wcag2a', 'wcag2aa'] })).violations
      .filter((x) => x.impact === 'critical' || x.impact === 'serious').map((x) => x.id));
    assert.deepEqual(v, []);
  }
});

await step('هرگز دو بار: قطعِ نت وسطِ ارسال، بعد ویرایشِ پیش‌نویس ⇒ ۴۰۹ ِ «پیش‌تر ثبت شده بود»، نه انتقالِ دوم', async () => {
  const oilRows = async () => (await transfers()).filter((t) => JSON.stringify(t.lines).includes('"oil"'));
  const before = (await oilRows()).length;
  await page.click('[data-xfr="new"]');
  await page.waitForSelector('#xfr-draft');
  await page.fill('#xfr-draft input[name="q"]', 'روغن');
  await page.click('[data-xfr="find"]');
  await page.waitForSelector('.xfr-found [data-xfr="add"]');
  await page.click('.xfr-found [data-xfr="add"]');
  await page.fill('#xfr-draft input[name="xfr-qty"]', '1');
  let dropped = 0;
  await page.route('**/api/v1/shop/catalog/transfers', closeSafeRoute(async (route) => {
    if (route.request().method() === 'POST' && !dropped) { dropped++; await route.fetch(); return route.abort('failed'); }
    return route.fallback();
  }));
  await page.click('#xfr-send');
  await page.waitForFunction(() => /اتصال قطع شد/.test(document.getElementById('xfr-err').textContent));
  //  کاربر گمان می‌کند نرفت، مقدار را عوض می‌کند و صفحه را از نو باز می‌کند — کلید درونِ پیش‌نویس است
  await page.fill('#xfr-draft input[name="xfr-qty"]', '2');
  await page.keyboard.press('Escape');
  await page.click('#xfr-btn');
  await page.click('[data-xfr="new"]');
  await page.waitForSelector('#xfr-draft input[name="xfr-qty"]');
  assert.equal(await page.locator('#xfr-draft input[name="xfr-qty"]').inputValue(), '2');
  await page.click('#xfr-send');
  await page.waitForFunction(() => /دو بار ثبت نشد/.test(document.getElementById('xfr-err').textContent));
  await page.unroute('**/api/v1/shop/catalog/transfers');
  const rows = await oilRows();
  assert.equal(rows.length, before + 1, 'فقط همان ارسالِ نخست');
  assert.equal(rows[rows.length - 1].lines[0].sentMilli, 1000);
  assert.deepEqual(await levels('oil'), { floor: 3000, wh: 0, transit: 1000 });
  await page.click('[data-xfr="back"]');
  await page.waitForSelector('#xfr-list .xfr-row');
});

await step('INV-01: «موجودیِ هر مکان» — هر کالا در هر مکانِ سرور و جمع، عیناً پاسخِ سرور؛ جست‌وجو؛ نامِ کالا متن', async () => {
  const server = (await call('GET', '/api/shop/catalog/products?limit=50', undefined, owner.accessToken)).body.items;
  //  انتقال‌های بالا موجودی را میانِ مکان‌ها پخش کرده‌اند ⇒ دست‌کم یک کالا در دو مکان عدد دارد
  assert.ok(server.some((p) => XC.PLACES.filter((l) => p.stock.levels[l] > 0).length >= 2), JSON.stringify(server.map((p) => p.stock.levels)));
  await page.click('[data-xfr="tab"][data-tab="stock"]');
  await page.waitForSelector('#xfr-stock-table tbody tr');
  assert.deepEqual(await page.$$eval('#xfr-stock-table thead th', (ths) => ths.map((th) => th.textContent.trim())),
    ['کالا', 'قفسهٔ فروشگاه', 'انبار', 'خراب', 'در مسیر', 'جمع']);
  const rows = await page.$$eval('#xfr-stock-table tbody tr', (trs) => trs.map((tr) => ({ id: tr.dataset.id, cells: [...tr.children].map((c) => c.textContent.trim()) })));
  assert.deepEqual(rows.map((r) => r.id), server.map((p) => p.id));
  for (const p of server) {
    const lv = XC.PLACES.map((l) => p.stock.levels[l]);
    assert.deepEqual(rows.find((r) => r.id === p.id).cells, [p.name, ...lv.map(XC.qtyText), XC.qtyText(lv.reduce((a, b) => a + b, 0))], p.id);
  }
  //  نامِ کالا دادهٔ دفتر است ⇒ متن، هرگز HTML
  assert.equal(await page.locator('#xss-probe').count(), 0);
  assert.ok(rows.some((r) => r.cells[0] === 'زهر <img src=x id="xss-probe">'));
  //  جست‌وجو (Enter در کادر) فقط همان کالا؛ بی نتیجه ⇒ «کالایی پیدا نشد»
  await page.fill('#xfr-stock input[name="sq"]', 'روغن');
  await page.press('#xfr-stock input[name="sq"]', 'Enter');
  await page.waitForFunction(() => [...document.querySelectorAll('#xfr-stock-table tbody tr')].map((tr) => tr.dataset.id).join() === 'oil');
  await page.fill('#xfr-stock input[name="sq"]', 'ناموجود');
  await page.click('#xfr-stock button[type="submit"]');
  await page.waitForFunction(() => /کالایی پیدا نشد/.test(document.querySelector('#xfr-body .xfr-empty')?.textContent || ''));
  if (axeSrc) {
    await page.fill('#xfr-stock input[name="sq"]', '');
    await page.press('#xfr-stock input[name="sq"]', 'Enter');
    await page.waitForSelector('#xfr-stock-table tbody tr');
    await page.addScriptTag({ content: axeSrc });
    const v = await page.evaluate(async () => (await window.axe.run('#xfr-modal', { runOnly: ['wcag2a', 'wcag2aa'] })).violations
      .filter((x) => x.impact === 'critical' || x.impact === 'serious').map((x) => x.id));
    assert.deepEqual(v, []);
  }
  //  صفحهٔ بعد و «شاید تازه نباشد»: پاسخِ نخست یک کالا با نشانگر و `fresh: false`، «بیشتر» بقیه را می‌افزاید و دکمه می‌رود
  const asked = [];
  await page.route('**/api/v1/shop/catalog/products?*', closeSafeRoute(async (route) => {
    const u = new URL(route.request().url());
    asked.push(u.searchParams.get('cursor') || '');
    if (!u.searchParams.get('cursor')) return route.fulfill({ json: { items: server.slice(0, 1), nextCursor: 'next-1', freshness: { fresh: false, reason: 'stale', checkedAt: null } } });
    return route.fulfill({ json: { items: server.slice(1), nextCursor: null, freshness: { fresh: true, reason: '', checkedAt: 1 } } });
  }));
  await page.fill('#xfr-stock input[name="sq"]', '');
  await page.press('#xfr-stock input[name="sq"]', 'Enter');
  await page.waitForSelector('[data-xfr="more"]');
  assert.equal(await page.locator('#xfr-stock-table tbody tr').count(), 1);
  assert.match(await page.locator('#xfr-body').innerText(), /شاید تازه نباشد/);
  await page.click('[data-xfr="more"]');
  await page.waitForFunction((n) => document.querySelectorAll('#xfr-stock-table tbody tr').length === n, server.length);
  assert.deepEqual(await page.$$eval('#xfr-stock-table tbody tr', (trs) => trs.map((tr) => tr.dataset.id)), server.map((p) => p.id));
  assert.equal(await page.locator('[data-xfr="more"]').count(), 0);
  assert.deepEqual(asked, ['', 'next-1']);
  await page.unroute('**/api/v1/shop/catalog/products?*');
  //  بستن و بازگشایی روی همان زبانه، با خواندنِ تازه؛ بازگشت به «در مسیر» همان فهرستِ انتقال
  await page.keyboard.press('Escape');
  await page.click('#xfr-btn');
  await page.waitForSelector('#xfr-stock-table tbody tr');
  await page.click('[data-xfr="tab"][data-tab="open"]');
  await page.waitForFunction(() => !document.querySelector('#xfr-stock-table') && !/در حالِ خواندن/.test(document.querySelector('#xfr-body')?.textContent || ''));
});

await step('صندوق‌دار (بی stock.move): فقط دیدن — نه «انتقالِ تازه»، نه دریافت/رد؛ گوشی بی بیرون‌زدگی', async () => {
  const s = await openAs(cashier, { width: 375 });
  await s.page.locator('#xfr-btn').waitFor({ timeout: 10000 });
  await s.page.click('#xfr-btn');
  await s.page.click('[data-xfr="tab"][data-tab="all"]');
  await s.page.waitForFunction(() => document.querySelectorAll('#xfr-list .xfr-row').length === 3);
  assert.equal(await s.page.locator('[data-xfr="new"]').count(), 0);
  assert.match(await s.page.locator('#xfr-body').innerText(), /فقط دیدن/);
  await s.page.locator('#xfr-list .xfr-row').first().click();
  await s.page.waitForSelector('.xfr-table');
  assert.equal(await s.page.locator('[data-xfr="act"]').count(), 0);
  assert.ok((await s.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)) <= 1, 'صفحه افقی بیرون زد');
  //  INV-01: «موجودیِ هر مکان» برای همه با `stock.view`؛ جدولِ پهن درونِ قابِ خودش می‌لغزد، صفحه نه
  await s.page.click('[data-xfr="back"]');
  await s.page.click('[data-xfr="tab"][data-tab="stock"]');
  await s.page.waitForSelector('#xfr-stock-table tbody tr');
  assert.equal(await s.page.locator('#xfr-stock-table thead th').count(), 6);
  assert.ok((await s.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)) <= 1, 'جدولِ مکان‌ها صفحه را بیرون زد');
  await s.ctx.close();
});

await step('در کلِ این نشست هیچ خطای صفحه‌ای نبود', async () => {
  const real = pageErrors.filter((m) => !/ServiceWorker|favicon|manifest/i.test(m));
  assert.deepEqual(real, [], real.join(' | '));
});

await browser.close();
await new Promise((r) => site.close(r));
await new Promise((r) => api.close(r));
await closeDb();

console.log('\n── نسخهٔ وب: «انتقالِ موجودی» ───────────────');
for (const [mark, name] of results) console.log(`${mark}  ${name}`);
console.log(`\n${results.length - failures}/${results.length} سبز`);
process.exit(failures ? 1 : 0);
