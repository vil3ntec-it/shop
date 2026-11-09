/**
 * MKT-06 — «چرخهٔ مصرف (روز)» در ویرایشِ کالای نسخهٔ وبِ دکان (`license/product-cycle.js`) — سرورِ واقعی، دیتابیسِ واقعی
 * (PGlite یا Postgres)، کرومیومِ واقعی، همان موتورِ همگام‌سازیِ صفحه؛ هیچ پاسخی ساختگی نیست.
 *
 *   • ویرایشِ کالا: کادر در «جزئیاتِ بیشتر» با مقدارِ همان کالا (خالی، یا ۹۰ ای که برنامه نوشته بود)؛ «۳۰» با رقمِ فارسی ⇒ دفتر،
 *     سابقهٔ «ویرایشِ کالا» و پس از همگام‌سازی همان ۳۰ در نمای عمومیِ ویترین؛ خالی کردن ⇒ کلید برداشته و ویترین بی کلید.
 *   • کادرِ خراب (۰، ۴۰۰، ۷٫۵، متن) ذخیره را می‌بندد: پنجره باز، خطا کنارِ کادر، نامِ عوض‌شده هم نمی‌نشیند، سابقه‌ای نیست.
 *   • ویرایشِ کالای بی چرخه بی دست زدن به کادر ⇒ ردیف هیچ کلیدِ تازه‌ای نمی‌گیرد (بایت‌به‌بایت همان چرخه‌نداشتن).
 *   • کالای تازه کادر ندارد؛ پس از بارگذاریِ دوباره همان مقدار؛ انگلیسی و پشتو برچسبِ خودشان؛ گوشی ۳۷۵px بی بیرون‌زدگی؛
 *     axe (اگر هست) بر خودِ کادر.
 *
 * اجرا:  TEST_DATABASE_URL=pglite:memory node test/web-product-cycle.mjs
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
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-cycle-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const liveConfig = require('../src/lib/live-config');

const ROOT = path.join(import.meta.dirname, '..', '..');
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

/* ---- وب‌سرورِ ایستا؛ نشانیِ سرور در <meta> و CSP همان کارِ web-transfers.mjs ---- */
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

/* ---- حساب، دکان، ویترین و کالاها از همان راه‌های واقعی ---- */
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
const owner = await account('cycle-web@panel.local', 'صاحبِ چرخه');
const shopId = (await post('/api/shop', { name: 'دکانِ چرخه' }, owner.accessToken)).body.shop.id;
const slug = `cyc-${Date.now().toString(36)}`;
await liveConfig.set(String(shopId), 'shop.orders', true);
await liveConfig.set(String(shopId), 'shop.storefront', true);
const sf = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ چرخه', minOrderMinor: 0 }, owner.accessToken);
assert.equal(sf.status, 200, JSON.stringify(sf.body));

let opN = 0;
const opId = () => `01JXC${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
const base = (name, extra = {}) => ({ name, category: 'خوراکی', unit: 'بسته', salePrice: 50, purchasePrice: 40, wholesalePrice: 0, minStock: 0, notes: '', barcodes: [], active: true, createdAt: Date.now(), ...extra });
//  «برنج» را برنامهٔ گوشی با چرخهٔ ۹۰ نوشته است (همان ردیفِ کالا)
const pushed = await post('/api/sync/v1/push', {
  device_id: 'dev-cycle-phone', schema_version: 2, caps: ['a00'], ops: [
    op('products', 'tea', base('چای')),
    op('products', 'rice', base('برنج', { consumptionDays: 90 })),
    op('products', 'salt', base('نمک')),
  ],
}, owner.accessToken, { 'X-App': 'shop', 'X-Device': 'dev-cycle-phone' });
assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
for (const id of ['tea', 'rice', 'salt']) {
  const r = await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, owner.accessToken);
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
/** نمای عمومیِ ویترین (بی توکن): `consumptionDays` ِ هر کالا یا 'none' */
const storeCycles = async () => {
  const r = await call('GET', `/api/store/${slug}/products?ids=tea,rice,salt`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return Object.fromEntries(r.body.items.map((p) => [p.id, Object.hasOwn(p, 'consumptionDays') ? p.consumptionDays : 'none']));
};
assert.deepEqual(await storeCycles(), { tea: 'none', rice: 90, salt: 'none' });

/* ---- مرورگر ---- */
const browser = await chromium.launch();
const pageErrors = [];
async function openAs(acct, { width = 1366 } = {}) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, serviceWorkers: 'block' });
  await ctx.addInitScript(([bundle]) => {
    if (sessionStorage.getItem('cycle-init')) return;   //  بارگذاریِ دوباره همان دفترِ همین مرورگر را می‌خواند
    sessionStorage.setItem('cycle-init', '1');
    localStorage.setItem('tohid-license-v1', JSON.stringify(bundle));
    localStorage.setItem('tohid-unlocked-v1', '1');
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'email', value: bundle.userLabel || '', name: bundle.userLabel || '', loginAt: 1 }));
  }, [{
    accessToken: acct.accessToken, refreshToken: acct.refreshToken, accessExpiresAt: Date.now() + 3600_000,
    userLabel: acct.user && acct.user.name, userId: acct.user && acct.user.id,
  }]);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => pageErrors.push(String(e.message)));
  await ready(page);
  return { ctx, page };
}
async function ready(page) {
  if (page.url() === 'about:blank') await page.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
  else await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => !!window.TohidProductCycle && !!window.TohidSync, null, { timeout: 15000 });
  await page.evaluate(() => document.getElementById('app-root')?.classList.remove('app-hidden'));
  //  کالاها از همان همگام‌سازیِ صفحه (pull) می‌رسند
  await page.waitForFunction(() => {
    /* global products */
    try { return ['tea', 'rice', 'salt'].every((id) => products.some((p) => p.id === id)); } catch { return false; }
  }, null, { timeout: 20000 }).catch(async (e) => {
    throw new Error(`کالاها از سرور نرسیدند: ${JSON.stringify(await page.evaluate(() => window.TohidSync.status()))} — ${e.message}`);
  });
}
const prod = (page, id) => page.evaluate((x) => {
  const p = products.find((r) => r.id === x);
  return p ? JSON.parse(JSON.stringify(p)) : null;
}, id);
const audits = (page) => page.evaluate(() => auditLog.filter((a) => a.type === 'product_edit').map((a) => a.notes || a.details || a.text || JSON.stringify(a)));
/** صفِ همگام‌سازی خالی و آخرین فرستادن پس از `since` ⇒ سرور همان دفتر را دارد */
const synced = async (page, since) => {
  await page.evaluate(() => window.TohidSync.sync && window.TohidSync.sync()).catch(() => {});
  await page.waitForFunction((t) => { const s = window.TohidSync.status(); return s.queued === 0 && !s.busy && s.lastPushAt >= t; }, since, { timeout: 20000 })
    .catch(async (e) => { throw new Error(`همگام نشد: ${JSON.stringify(await page.evaluate(() => window.TohidSync.status()))} — ${e.message}`); });
};
const modalOpen = (page) => page.evaluate(() => document.getElementById('modal-bulk-product').classList.contains('open'));
const shown = (page, sel) => page.evaluate((s) => { const e = document.querySelector(s); return !!e && e.getClientRects().length > 0; }, sel);
async function edit(page, id) {
  await page.evaluate((x) => window.openEditProduct(x), id);
  await page.waitForFunction(() => document.getElementById('modal-bulk-product').classList.contains('open'));
}
const save = (page) => page.click('#btn-bp-save');
const cancel = (page) => page.click('#modal-bulk-product [data-close-modal="modal-bulk-product"]');

const { ctx, page } = await openAs(owner);

await step('ویرایشِ کالای بی چرخه: کادر در «جزئیاتِ بیشتر»، خالی، با برچسب و راهنما', async () => {
  await edit(page, 'tea');
  assert.ok(await shown(page, '#bp-extras #in-bp-cycle'), 'کادر دیده نمی‌شود');
  assert.equal(await page.inputValue('#in-bp-cycle'), '');
  assert.equal((await page.locator('label[for="in-bp-cycle"]').innerText()).trim(), 'چرخهٔ مصرف (روز)');
  assert.match(await page.locator('#bp-cycle-hint').innerText(), /وقتِ دوباره خریدن؟/);
  assert.equal(await shown(page, '#bp-cycle-error'), false, 'خطا پیش از ذخیره');
  //  پیش از «فعال در فروش»
  assert.equal(await page.evaluate(() => document.getElementById('fg-bp-cycle').nextElementSibling.classList.contains('check-row')), true);
});

let t0 = 0;
await step('«۳۰» با رقمِ فارسی ⇒ دفتر و سابقهٔ «ویرایشِ کالا»؛ پس از همگام‌سازی همان ۳۰ در ویترین', async () => {
  const before = await prod(page, 'tea');
  t0 = Date.now();
  await page.fill('#in-bp-cycle', '۳۰');
  await save(page);
  await page.waitForFunction(() => !document.getElementById('modal-bulk-product').classList.contains('open'));
  const after = await prod(page, 'tea');
  assert.equal(after.consumptionDays, 30);
  const { consumptionDays, ...rest } = after;
  //  ذخیرهٔ ویرایشِ خودِ صفحه همیشه این چهار را از «جزئیاتِ بیشتر» می‌نویسد (پیش از این کادر هم همین بود)
  const page4 = { subcategory: '', supplierId: '', saleUnit: '', unitFactor: 1 };
  assert.deepEqual(rest, { ...before, ...page4 }, 'جز چرخه هیچ فیلدی عوض نشد');
  assert.ok((await audits(page)).some((n) => n.includes('چرخهٔ مصرف — ⇐ 30 روز')), JSON.stringify(await audits(page)));
  await synced(page, t0);
  assert.deepEqual(await storeCycles(), { tea: 30, rice: 90, salt: 'none' });
});

await step('کادرِ خراب (۰، ۴۰۰، ۷٫۵، متن) ذخیره را می‌بندد: خطا کنارِ کادر، نامِ عوض‌شده هم نمی‌نشیند', async () => {
  const auditsBefore = (await audits(page)).length;
  await edit(page, 'tea');
  assert.equal(await page.inputValue('#in-bp-cycle'), '30');
  await page.fill('#modal-bulk-product [data-bp-name]', 'چای سبز');
  for (const bad of ['0', '400', '۷٫۵', 'سی']) {
    await page.fill('#in-bp-cycle', bad);
    await save(page);
    await page.waitForTimeout(150);
    assert.equal(await modalOpen(page), true, `${bad}: پنجره بسته شد`);
    assert.ok(await shown(page, '#bp-cycle-error'), `${bad}: خطا دیده نمی‌شود`);
    assert.equal(await page.getAttribute('#in-bp-cycle', 'aria-invalid'), 'true');
    const p = await prod(page, 'tea');
    assert.equal(p.name, 'چای', `${bad}: نام نشست`);
    assert.equal(p.consumptionDays, 30, `${bad}: چرخه عوض شد`);
  }
  assert.equal((await audits(page)).length, auditsBefore, 'سابقه‌ای ثبت نشد');
  await page.fill('#in-bp-cycle', '30');
  assert.equal(await shown(page, '#bp-cycle-error'), false, 'نوشتن خطا را برمی‌دارد');
  await cancel(page);
  assert.equal((await prod(page, 'tea')).name, 'چای');
});

await step('کالایی که برنامه چرخه‌اش را نوشته (۹۰): کادر پر؛ خالی کردن ⇒ کلید برداشته و ویترین بی کلید', async () => {
  await edit(page, 'rice');
  assert.equal(await page.inputValue('#in-bp-cycle'), '90');
  const t = Date.now();
  await page.fill('#in-bp-cycle', '');
  await save(page);
  await page.waitForFunction(() => !document.getElementById('modal-bulk-product').classList.contains('open'));
  const p = await prod(page, 'rice');
  assert.equal(Object.hasOwn(p, 'consumptionDays'), false, JSON.stringify(p));
  assert.ok((await audits(page)).some((n) => n.includes('چرخهٔ مصرف 90 ⇐ — روز')));
  await synced(page, t);
  assert.deepEqual(await storeCycles(), { tea: 30, rice: 'none', salt: 'none' });
});

await step('ویرایشِ کالای بی چرخه بی دست زدن به کادر ⇒ هیچ کلیدِ تازه‌ای (حتی null)', async () => {
  await edit(page, 'salt');
  assert.equal(await page.inputValue('#in-bp-cycle'), '');
  await page.fill('#modal-bulk-product [data-bp-name]', 'نمکِ ید‌دار');
  await save(page);
  await page.waitForFunction(() => !document.getElementById('modal-bulk-product').classList.contains('open'));
  const p = await prod(page, 'salt');
  assert.equal(p.name, 'نمکِ ید‌دار');
  assert.equal(Object.hasOwn(p, 'consumptionDays'), false, JSON.stringify(p));
  assert.ok(!(await audits(page)).some((n) => n.includes('نمک') && n.includes('چرخهٔ مصرف')));
});

await step('کالای تازه کادرِ چرخه ندارد (فقط ویرایش، همان برنامه)', async () => {
  await page.evaluate(() => window.openBulkProduct());
  await page.waitForFunction(() => document.getElementById('modal-bulk-product').classList.contains('open'));
  assert.equal(await shown(page, '#in-bp-cycle'), false);
  await cancel(page);
});

await step('پس از بارگذاریِ دوباره: همان ۳۰ در کادر (دفترِ ذخیره‌شده)', async () => {
  await ready(page);
  await edit(page, 'tea');
  assert.equal(await page.inputValue('#in-bp-cycle'), '30');
  await cancel(page);
});

await step('زبان: انگلیسی و پشتو برچسب و راهنمای خودشان را دارند؛ برگشت به فارسی همان متن', async () => {
  await edit(page, 'tea');
  const lbl = () => page.locator('label[for="in-bp-cycle"]').innerText().then((s) => s.trim());
  for (const [l, want] of [['en', 'Consumption cycle (days)'], ['ps', 'د مصرف دوره (ورځې)'], ['fa', 'چرخهٔ مصرف (روز)']]) {
    await page.evaluate((x) => window.TohidWebI18n.setLang(x), l);
    await page.waitForFunction(([w]) => document.querySelector('label[for="in-bp-cycle"]').textContent.trim() === w, [want], { timeout: 15000 })
      .catch(async () => { throw new Error(`${l}: «${await lbl()}»`); });
  }
  assert.equal(await page.inputValue('#in-bp-cycle'), '30', 'عوض کردنِ زبان مقدار را دست نمی‌زند');
  await cancel(page);
});

await step('گوشی ۳۷۵px: پنجرهٔ ویرایش با کادر افقی بیرون نمی‌زند؛ axe (اگر هست) بر خودِ کادر', async () => {
  const s = await openAs(owner, { width: 375 });
  await edit(s.page, 'tea');
  assert.ok(await shown(s.page, '#in-bp-cycle'));
  assert.ok((await s.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)) <= 1, 'صفحه افقی بیرون زد');
  const box = await s.page.evaluate(() => { const r = document.getElementById('fg-bp-cycle').getBoundingClientRect(); return { l: r.left, r: r.right }; });
  assert.ok(box.l >= 0 && box.r <= 375 + 1, JSON.stringify(box));
  if (axeSrc) {
    await s.page.addScriptTag({ content: axeSrc });
    const v = await s.page.evaluate(async () => (await window.axe.run('#fg-bp-cycle', { runOnly: ['wcag2a', 'wcag2aa'] })).violations.map((x) => x.id));
    assert.deepEqual(v, []);
  } else console.log('   (axe-core نیست: فقط چیدمان)');
  await s.ctx.close();
});

await step('در کلِ این نشست هیچ خطای صفحه‌ای نبود', async () => {
  const real = pageErrors.filter((m) => !/ServiceWorker|favicon|manifest/i.test(m));
  assert.deepEqual(real, [], real.join(' | '));
});

await ctx.close();
await browser.close();
await new Promise((r) => site.close(r));
await new Promise((r) => api.close(r));
await closeDb();

console.log('\n── نسخهٔ وب: «چرخهٔ مصرف» ِ کالا (MKT-06) ───────────────');
for (const [mark, name] of results) console.log(`${mark}  ${name}`);
console.log(`\n${results.length - failures}/${results.length} سبز`);
process.exit(failures ? 1 : 0);
