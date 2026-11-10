/**
 * CRM-15 در نسخهٔ وبِ دکان (`license/customer-access.js`) — سرورِ واقعی، دیتابیسِ واقعی (PGlite یا Postgres)، کرومیومِ
 * واقعی، همان موتورِ همگام‌سازیِ صفحه؛ هیچ پاسخی ساختگی نیست.
 *
 *   • صندوق‌دار (پیش‌فرض: شمارهٔ مشتریان را می‌بیند) دفتر را با شماره می‌گیرد؛ صاحب «شمارهٔ مشتریان» را برایش می‌بندد ⇒ پس
 *     از تازه شدنِ مجوز، فهرستِ قرض‌داران (کارت و جدول) شمارهٔ پوشیده (جز دو رقمِ آخر) نشان می‌دهد در حالی که دفترِ ازپیش‌
 *     همگام‌شدهٔ همین مرورگر هنوز شماره را دارد؛ ویرایشِ پرونده کادرِ شماره را ندارد و ذخیره شماره را دست نمی‌زند (سرور هم)؛
 *     خروجیِ برگهٔ قرض‌داران پوشیده.
 *   • هر دیدن ردیفِ درِ `app` در `customer_access_log` با بیننده و نقشِ توکن: باز شدنِ حساب (`detail`)، صورت‌حساب (`export`)،
 *     برگهٔ قرض‌داران (`*`)؛ صاحب با کادرِ شمارهٔ پیدا ⇒ `phone`. هیچ نام یا شماره‌ای در ردیف‌ها نیست.
 *   • «چه کسی این پرونده را دید» در صفحهٔ حساب: صاحب می‌بیند (دیدن‌های صندوق‌دار با نقش و نوع)، صندوق‌دار (بی «سابقه») نه؛
 *     انگلیسی برچسبِ خودش؛ گوشی ۳۷۵px بی بیرون‌زدگی؛ axe (اگر هست) بر خودِ بخش.
 *
 * اجرا:  TEST_DATABASE_URL=pglite:memory node test/web-customer-access.mjs
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
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-access-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, many, closeDb } = require('../src/db');
const migrate = require('../src/migrate');

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

/* ---- وب‌سرورِ ایستا؛ نشانیِ سرور در <meta> و CSP همان کارِ web-product-cycle.mjs ---- */
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

/* ---- حساب، دکان و دفتر از همان راه‌های واقعی ---- */
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
const owner = await account('access-web-owner@panel.local', 'صاحبِ سابقه');
const shopId = (await post('/api/shop', { name: 'دکانِ سابقه' }, owner.accessToken)).body.shop.id;
const cashier = await account('access-web-cashier@panel.local', 'کریمِ صندوق');
await query(
  `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
   VALUES ($1,$2,$3,'cashier','active',$4,$4)`,
  [`mem-${Date.now().toString(36)}`, shopId, cashier.user.id, Date.now()],
);

const PHONE = '0799123456';
const AT = Date.now() - 86_400_000;
let opN = 0;
const opId = () => `01JXA${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
const pushed = await post('/api/sync/v1/push', {
  device_id: 'dev-access-phone', schema_version: 2, caps: ['a00'], ops: [
    op('debtors', 'd-web', { name: 'رحیمِ وب', phone: PHONE, notes: '', createdAt: AT }),
    op('debtors', 'd-two', { name: 'کریمِ دوم', phone: '0700222333', notes: '', createdAt: AT }),
    op('transactions', 't-1', { debtorId: 'd-web', type: 'give', amount: 250, date: '2026-11-01', notes: '', createdAt: AT }),
  ],
}, owner.accessToken, { 'X-App': 'shop', 'X-Device': 'dev-access-phone' });
assert.equal(pushed.status, 200, JSON.stringify(pushed.body));

const appRows = () => many("SELECT * FROM customer_access_log WHERE shop_id=$1 AND door='app' ORDER BY first_at, id", [shopId]);
const SECRETS = [PHONE, '799123456', 'رحیمِ وب', 'کریمِ دوم'];

/* ---- مرورگر ---- */
const browser = await chromium.launch();
const pageErrors = [];
async function openAs(acct, { width = 1366, tag = 'a' } = {}) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, serviceWorkers: 'block' });
  await ctx.addInitScript(([bundle, mark]) => {
    if (sessionStorage.getItem(mark)) return;   //  بارگذاریِ دوباره همان دفترِ همین مرورگر را می‌خواند
    sessionStorage.setItem(mark, '1');
    localStorage.setItem('tohid-license-v1', JSON.stringify(bundle));
    localStorage.setItem('tohid-unlocked-v1', '1');
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'email', value: bundle.userLabel || '', name: bundle.userLabel || '', loginAt: 1 }));
  }, [{
    accessToken: acct.accessToken, refreshToken: acct.refreshToken, accessExpiresAt: Date.now() + 3600_000,
    userLabel: acct.user && acct.user.name, userId: acct.user && acct.user.id,
  }, `access-init-${tag}`]);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => pageErrors.push(String(e.message)));
  await ready(page);
  return { ctx, page };
}
async function ready(page) {
  if (page.url() === 'about:blank') await page.goto(`${siteBase}/`, { waitUntil: 'domcontentloaded' });
  else await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => !!window.TohidCustomerAccess && !!window.TohidSync && !!window.TohidShop, null, { timeout: 15000 });
  await page.evaluate(() => document.getElementById('app-root')?.classList.remove('app-hidden'));
  await page.waitForFunction(() => {
    /* global debtors */
    try { return ['d-web', 'd-two'].every((id) => debtors.some((d) => d.id === id)); } catch { return false; }
  }, null, { timeout: 20000 }).catch(async (e) => {
    throw new Error(`قرض‌داران از سرور نرسیدند: ${JSON.stringify(await page.evaluate(() => window.TohidSync.status()))} — ${e.message}`);
  });
  //  نقش و مجوزِ همین حساب از سرور (`refreshShopInfo`) — پیش از هر دیدن
  await page.waitForFunction(() => !!(window.TohidShop.state.shop && window.TohidShop.state.shop.policy), null, { timeout: 20000 });
}
/** مجوزِ همین صفحه از سرور تازه شد (`refreshShopInfo`) */
const contactMode = (page) => page.evaluate(() => {
  const s = window.TohidShop.state.shop;
  return s && s.policy && s.policy.effective && s.policy.effective['customers.contact'] ? s.policy.effective['customers.contact'].mode : '';
});
async function freshPolicy(page, want) {
  await page.evaluate(() => window.TohidShop.refreshShopInfo());
  await page.waitForFunction((w) => {
    const s = window.TohidShop.state.shop;
    return !!(s && s.policy && s.policy.effective && s.policy.effective['customers.contact'] && s.policy.effective['customers.contact'].mode === w);
  }, want, { timeout: 15000 }).catch(async () => { throw new Error(`مجوز: «${await contactMode(page)}»`); });
}
const listPhones = (page) => page.evaluate(() => [...document.querySelectorAll('#debtor-list .debtor-meta span')].map((s) => s.textContent));
const shown = (page, sel) => page.evaluate((s) => { const e = document.querySelector(s); return !!e && e.getClientRects().length > 0; }, sel);
async function waitRows(pred, label) {
  const until = Date.now() + 10000;
  let rows = [];
  while (Date.now() < until) {
    rows = await appRows();
    if (pred(rows)) return rows;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(`${label}: ${JSON.stringify(rows.map((r) => [r.role, r.kind, r.customer_ref, Number(r.hits)]))}`);
}
async function openAccount(page, id) {
  await page.evaluate((x) => window.openAccount(x), id);
  await page.waitForFunction(() => document.getElementById('page-debtor-account').classList.contains('active')
    || getComputedStyle(document.getElementById('page-debtor-account')).display !== 'none', null, { timeout: 10000 });
}

const C = await openAs(cashier, { tag: 'cashier' });

await step('صندوق‌دار با مجوزِ پیش‌فرض: دفتر شماره دارد و فهرست شمارهٔ کامل را نشان می‌دهد', async () => {
  assert.equal(await contactMode(C.page), 'allow');
  const d = await C.page.evaluate(() => debtors.find((x) => x.id === 'd-web'));
  assert.equal(d.phone.replace(/\D/g, '').slice(-9), '799123456', JSON.stringify(d));
  await C.page.evaluate(() => { window.goTo('debtors'); window.renderDebtors(); });
  const phones = await listPhones(C.page);
  assert.ok(phones.some((t) => t.replace(/\D/g, '').endsWith('799123456')), JSON.stringify(phones));
  assert.ok(phones.every((t) => !t.includes('•')), JSON.stringify(phones));
});

await step('صاحب «شمارهٔ مشتریان» را می‌بندد ⇒ کارت و جدول پوشیده (دو رقمِ آخر)، دفترِ مرورگر هنوز شماره دارد', async () => {
  const close = await call('PUT', `/api/shop/permissions/members/${cashier.user.id}`, { perms: { 'customers.contact': { mode: 'deny' } } }, owner.accessToken);
  assert.equal(close.status, 200, JSON.stringify(close.body));
  await freshPolicy(C.page, 'deny');
  //  بی renderِ دوباره هم: رویدادِ «مجوزِ تازه» فهرستِ همین لحظه را پوشاند
  let phones = await listPhones(C.page);
  assert.ok(phones.length >= 2 && phones.every((t) => t.includes('•')), JSON.stringify(phones));
  assert.ok(phones.some((t) => t.replace(/[^0-9]/g, '') === '56'), JSON.stringify(phones));
  await C.page.evaluate(() => window.renderDebtors());
  phones = await listPhones(C.page);
  assert.ok(phones.every((t) => t.includes('•') && !t.replace(/[^0-9]/g, '').includes('799')), JSON.stringify(phones));
  //  هر دو نما: جدول (پیش‌فرضِ کامپیوتر) و کارت
  for (const mode of ['table', 'card']) {
    const got = await C.page.evaluate((m) => {
      localStorage.setItem(debtorViewKey(), m);
      window.renderDebtors();
      const sel = m === 'table' ? '#debtor-list .debtor-row .debtor-meta span' : '#debtor-list .debtor-card .debtor-meta span';
      return [...document.querySelectorAll(sel)].map((x) => x.textContent);
    }, mode);
    assert.ok(got.length >= 2 && got.every((t) => t.includes('•')), `${mode}: ${JSON.stringify(got)}`);
  }
  const d = await C.page.evaluate(() => debtors.find((x) => x.id === 'd-web'));
  assert.ok(d.phone.replace(/\D/g, '').endsWith('799123456'), 'فقط نمایش است: دفترِ ازپیش‌همگام‌شده دست نخورد');
});

await step('باز شدنِ حساب ⇒ ردیفِ «app» ِ صندوق‌دار (detail)؛ بخشِ «چه کسی دید» برای او نیست', async () => {
  await query("DELETE FROM customer_access_log WHERE shop_id=$1", [shopId]);
  await openAccount(C.page, 'd-web');
  const rows = await waitRows((r) => r.length === 1, 'دیدنِ صندوق‌دار');
  assert.equal(rows[0].viewer_id, cashier.user.id);
  assert.equal(rows[0].role, 'cashier');
  assert.equal(rows[0].kind, 'detail');
  assert.equal(rows[0].customer_ref, 'd-web');
  await C.page.waitForTimeout(300);
  assert.equal(await shown(C.page, '#acc-access-panel'), false, 'صندوق‌دار «سابقه» ندارد');
  //  باز شدنِ دوباره در ۱۰ دقیقه از همین صفحه درخواستی نمی‌فرستد
  await openAccount(C.page, 'd-web');
  await C.page.waitForTimeout(300);
  assert.equal(Number((await appRows())[0].hits), 1);
});

await step('ویرایشِ پرونده بی کادرِ شماره؛ ذخیرهٔ نامِ تازه شماره را نه در دفتر و نه در سرور عوض می‌کند', async () => {
  await C.page.evaluate(() => window.openEditDebtor('d-web'));
  await C.page.waitForFunction(() => document.getElementById('modal-debtor').classList.contains('open'));
  assert.equal(await shown(C.page, '#in-debtor-phone'), false, 'کادرِ شماره پنهان');
  assert.equal(await shown(C.page, '#in-debtor-name'), true);
  const t = Date.now();
  await C.page.fill('#in-debtor-name', 'رحیمِ وبِ تازه');
  await C.page.click('#modal-debtor button[type="submit"]');
  await C.page.waitForFunction(() => !document.getElementById('modal-debtor').classList.contains('open'), null, { timeout: 10000 });
  const d = await C.page.evaluate(() => debtors.find((x) => x.id === 'd-web'));
  assert.equal(d.name, 'رحیمِ وبِ تازه');
  assert.ok(d.phone.replace(/\D/g, '').endsWith('799123456'), JSON.stringify(d));
  await C.page.evaluate(() => window.TohidSync.sync && window.TohidSync.sync()).catch(() => {});
  await C.page.waitForFunction((x) => { const s = window.TohidSync.status(); return s.queued === 0 && !s.busy && s.lastPushAt >= x; }, t, { timeout: 20000 });
  //  دفترِ سرور (v1): نامِ تازه نشست، شماره همان
  const srv = await many("SELECT data FROM sync_rows WHERE account_id=$1 AND table_name='debtors' AND row_id='d-web'", [shopId]);
  assert.equal(srv.length, 1, JSON.stringify(srv));
  assert.equal(srv[0].data.name, 'رحیمِ وبِ تازه');
  assert.equal(srv[0].data.phone, PHONE, JSON.stringify(srv[0].data));
  //  «قرض‌دارِ تازه» کادرِ شماره را دارد (شماره‌ای که خودش می‌نویسد)
  await C.page.evaluate(() => window.openAddDebtor());
  assert.equal(await shown(C.page, '#in-debtor-phone'), true);
  await C.page.evaluate(() => window.closeModal('modal-debtor'));
});

await step('صورت‌حساب و برگهٔ قرض‌داران ⇒ export ِ همین پرونده و «*»؛ شمارهٔ برگه پوشیده', async () => {
  await query("DELETE FROM customer_access_log WHERE shop_id=$1", [shopId]);
  await openAccount(C.page, 'd-web');
  await C.page.click('#btn-acc-statement-csv');
  const sheet = await C.page.evaluate(() => buildReportCsvRows('debtors').rows.slice(1).map((r) => r[1]));
  assert.ok(sheet.length >= 2 && sheet.every((p) => !p || p.includes('•')), JSON.stringify(sheet));
  const rows = await waitRows((r) => r.some((x) => x.kind === 'export' && x.customer_ref === 'd-web') && r.some((x) => x.customer_ref === '*'), 'خروجی‌ها');
  assert.deepEqual(rows.filter((r) => r.kind === 'export').map((r) => r.customer_ref).sort(), ['*', 'd-web']);
  assert.ok(rows.every((r) => r.viewer_id === cashier.user.id && r.role === 'cashier'));
});

const O = await openAs(owner, { tag: 'owner' });

await step('صاحب: کادرِ شمارهٔ پیدا ⇒ «phone»؛ بخشِ «چه کسی این پرونده را دید» با دیدن‌های صندوق‌دار', async () => {
  await O.page.evaluate(() => window.openEditDebtor('d-web'));
  await O.page.waitForFunction(() => document.getElementById('modal-debtor').classList.contains('open'));
  assert.equal(await shown(O.page, '#in-debtor-phone'), true);
  await waitRows((r) => r.some((x) => x.viewer_id === owner.user.id && x.kind === 'phone'), 'شمارهٔ صاحب');
  await O.page.evaluate(() => window.closeModal('modal-debtor'));
  await openAccount(O.page, 'd-web');
  await O.page.waitForFunction(() => { const p = document.getElementById('acc-access-panel'); return p && !p.hidden && document.querySelectorAll('#acc-access-list li').length >= 3; }, null, { timeout: 10000 })
    .catch(async () => { throw new Error(await O.page.evaluate(() => document.getElementById('acc-access-panel')?.outerHTML || 'نیست')); });
  assert.equal((await O.page.locator('#acc-access-panel h2').innerText()).trim(), 'چه کسی این پرونده را دید');
  const lines = await O.page.evaluate(() => [...document.querySelectorAll('#acc-access-list li')].map((l) => l.textContent));
  assert.ok(lines.some((l) => l.includes('کریمِ صندوق') && l.includes('صندوق‌دار') && l.includes('خروجیِ همهٔ مشتریان')), lines.join(' | '));
  assert.ok(lines.some((l) => l.includes('کریمِ صندوق') && l.includes('خروجی') && !l.includes('همهٔ')), lines.join(' | '));
  assert.ok(lines.some((l) => l.includes('صاحبِ سابقه') && l.includes('شماره و نشانی')), lines.join(' | '));
  //  نامِ بیننده دادهٔ خام است (لایهٔ زبان آن را ترجمه نمی‌کند)
  assert.ok(await O.page.evaluate(() => [...document.querySelectorAll('#acc-access-list li span[data-raw]')].length >= 3));
  //  پروندهٔ دیگر بخش را از نو می‌سازد (ردیفِ «*» ِ برگهٔ همه در هر پرونده)
  await openAccount(O.page, 'd-two');
  await O.page.waitForFunction(() => {
    const ls = [...document.querySelectorAll('#acc-access-list li')].map((l) => l.textContent);
    return ls.length >= 1 && ls.every((l) => !l.includes('شماره و نشانی'));
  }, null, { timeout: 10000 });
});

await step('ردیف‌های سرور هیچ نام، شماره یا نشانیِ مشتری ندارند', async () => {
  const dump = JSON.stringify(await many('SELECT * FROM customer_access_log WHERE shop_id=$1', [shopId]));
  for (const s of SECRETS) assert.equal(dump.includes(s), false, s);
});

await step('انگلیسی: عنوانِ بخش و نقش و نوع ترجمه، نامِ بیننده دست‌نخورده', async () => {
  await openAccount(O.page, 'd-web');
  await O.page.waitForFunction(() => document.querySelectorAll('#acc-access-list li').length >= 3, null, { timeout: 10000 });
  await O.page.evaluate(() => window.TohidWebI18n.setLang('en'));
  await O.page.waitForFunction(() => document.querySelector('#acc-access-panel h2').textContent.trim() === 'Who viewed this file', null, { timeout: 15000 })
    .catch(async () => { throw new Error(`«${await O.page.locator('#acc-access-panel h2').innerText()}»`); });
  const lines = await O.page.evaluate(() => [...document.querySelectorAll('#acc-access-list li')].map((l) => l.textContent));
  assert.ok(lines.some((l) => l.includes('کریمِ صندوق') && l.includes('Cashier')), lines.join(' | '));
  await O.page.evaluate(() => window.TohidWebI18n.setLang('fa'));
  await O.page.waitForFunction(() => document.querySelector('#acc-access-panel h2').textContent.trim() === 'چه کسی این پرونده را دید', null, { timeout: 15000 });
});

await step('گوشی ۳۷۵px: بخش بیرون نمی‌زند؛ axe (اگر هست) بر خودِ بخش', async () => {
  const s = await openAs(owner, { width: 375, tag: 'phone' });
  await openAccount(s.page, 'd-web');
  await s.page.waitForFunction(() => { const p = document.getElementById('acc-access-panel'); return p && !p.hidden && p.getClientRects().length > 0; }, null, { timeout: 10000 });
  assert.ok((await s.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)) <= 1, 'صفحه افقی بیرون زد');
  const box = await s.page.evaluate(() => { const r = document.getElementById('acc-access-panel').getBoundingClientRect(); return { l: r.left, r: r.right }; });
  assert.ok(box.l >= 0 && box.r <= 375 + 1, JSON.stringify(box));
  if (axeSrc) {
    await s.page.waitForFunction(() => document.getAnimations().every((a) => a.playState === 'finished'), null, { timeout: 10_000 });
    await s.page.addScriptTag({ content: axeSrc });
    const v = await s.page.evaluate(async () => (await window.axe.run('#acc-access-panel', { runOnly: ['wcag2a', 'wcag2aa'] })).violations.map((x) => x.id));
    assert.deepEqual(v, []);
  } else console.log('   (axe-core نیست: فقط چیدمان)');
  await s.ctx.close();
});

await step('در کلِ این نشست هیچ خطای صفحه‌ای نبود', async () => {
  const real = pageErrors.filter((m) => !/ServiceWorker|favicon|manifest/i.test(m));
  assert.deepEqual(real, [], real.join(' | '));
});

await C.ctx.close();
await O.ctx.close();
await browser.close();
await new Promise((r) => site.close(r));
await new Promise((r) => api.close(r));
await closeDb();

console.log('\n── نسخهٔ وب: دسترسیِ محدود و سابقهٔ دسترسیِ مشتری (CRM-15) ───────────────');
for (const [mark, name] of results) console.log(`${mark}  ${name}`);
console.log(`\n${results.length - failures}/${results.length} سبز`);
process.exit(failures ? 1 : 0);
