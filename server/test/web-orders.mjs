/**
 * بخشِ «سفارش‌ها»ی نسخهٔ وب (فاز ۴: ORD-01، ۰۶، ۰۷، ۱۱، ۱۲، ۱۶، ۱۷، ۲۱) — سرورِ
 * واقعی، دیتابیسِ واقعی (PGlite یا Postgres)، کرومیومِ واقعی؛ هیچ پاسخی ساختگی نیست.
 *
 *   • دکانِ پرچم‌روشن: بخش در منو پیدا می‌شود، برگه‌ها با شمارش، سفارشِ دستی با
 *     کلیدِ یکتایی (قطعِ نت وسطِ ثبت ⇒ تکرار، و باز فقط یک سفارش)، جزئیات با
 *     خطِ زمانی، قبول با زمانِ آماده، رد/لغو با دلیل («دیگر» بی توضیح نمی‌رود)،
 *     ۴۰۹ِ «وضعیت عوض شد»، صفحه‌بندی، صفِ زنده و «فقط وقتی برگه پیداست».
 *   • کارمند: شمارهٔ مشتری نقاب‌دار و بی دکمهٔ تماس (ORD-17).
 *   • دکانِ پرچم‌خاموش: هیچ اثری — نه صفحه، نه منو، نه درخواستِ شمارش.
 *
 * اجرا:  TEST_DATABASE_URL=pglite:memory node test/web-orders.mjs
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
process.env.BACKUP_PATH = path.join(os.tmpdir(), `shop-web-orders-${process.pid}`);
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, one, many, newId, now, closeDb } = require('../src/db');
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

/* ---- وب‌سرورِ ایستا؛ نشانیِ سرور در <meta> و CSP همان کارِ web-backup.mjs ---- */
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

/* ---- حساب‌ها از همان سه پلهٔ ثبت‌نام ---- */
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
    device: { deviceId: `dev-${email.split("@")[0]}`, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true },
  });
  assert.ok(done.body && done.body.accessToken, `ثبت‌نامِ ${email}: ${JSON.stringify(done.body)}`);
  return done.body;
}
const owner = await account('orders-on@panel.local', 'صاحبِ روشن');
const shopOn = (await post('/api/shop', { name: 'دکانِ سفارش' }, owner.accessToken)).body.shop.id;
const ownerOff = await account('orders-off@panel.local', 'صاحبِ خاموش');
const shopOff = (await post('/api/shop', { name: 'دکانِ بی‌سفارش' }, ownerOff.accessToken)).body.shop.id;
const staff = await account('orders-staff@panel.local', 'شاگرد');
await query(
  `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
   VALUES ($1,$2,$3,'staff','active',$4,$4)`,
  [newId('mem'), shopOn, staff.user.id, now()],
);
//  همان راهی که «تنظیماتِ زنده»ی پنل می‌رود — فقط برای همین یک دکان
await liveConfig.set(String(shopOn), 'shop.orders', true);

let keySeq = 0;
const apiOrder = (over = {}) => post('/api/v1/shop/orders', {
  source: 'telegram', fulfillment: 'pickup', customer: { name: 'مشتریِ تلگرام', phone: '0700111222' },
  lines: [{ name: 'چای', qtyMilli: 1000, unitPriceMinor: 15000 }], ...over,
}, owner.accessToken, { 'Idempotency-Key': `api-order-${++keySeq}-${Date.now()}` });

/* ---- مرورگر ---- */
const browser = await chromium.launch();
const pageErrors = [];
async function openAs(acct, { width = 1366, hash = '' } = {}) {
  //  ⚠️ سرویس‌ورکر بسته: درخواستی که از دستِ آن برود، نه به `page.route` می‌رسد و نه
  //  به شمارشِ درخواست‌های صفحه — سنجهٔ «قطعِ نت» و «فقط وقتی پیداست» بی این تصادفی می‌شد
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, serviceWorkers: 'block' });
  await ctx.addInitScript(([bundle]) => {
    localStorage.setItem('tohid-license-v1', JSON.stringify(bundle));
    localStorage.setItem('tohid-unlocked-v1', '1');
    //  صفحهٔ ورودِ خودِ برنامه هم باید بداند وارد شده‌ایم، وگرنه روی همه‌چیز می‌نشیند
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'email', value: bundle.userLabel || '', name: bundle.userLabel || '', loginAt: 1 }));
    window.TOHID_ORDERS_POLL_MS = 700;
  }, [{
    accessToken: acct.accessToken, refreshToken: acct.refreshToken, accessExpiresAt: Date.now() + 3600_000,
    userLabel: acct.user && acct.user.name, userId: acct.user && acct.user.id,
  }]);
  const page = await ctx.newPage();
  const seen = [];
  page.on('pageerror', (e) => pageErrors.push(String(e.message)));
  page.on('request', (r) => { if (r.url().includes('/shop/orders')) seen.push(`${r.method()} ${new URL(r.url()).pathname}${new URL(r.url()).search}`); });
  await page.goto(`${siteBase}/${hash}`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => !!window.TohidOrders);
  await page.evaluate(() => document.getElementById('app-root')?.classList.remove('app-hidden'));
  return { ctx, page, seen };
}
/** «برنامه رفت پس‌زمینه / برگشت» — همان رویدادی که مرورگر می‌فرستد */
const setVisible = (pg, on) => pg.evaluate((v) => {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => (v ? 'visible' : 'hidden') });
  document.dispatchEvent(new Event('visibilitychange'));
}, on);
const orderRows = () => many('SELECT * FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [shopOn]);

/* ══════════════════════ ۱) پرچم خاموش ══════════════════════ */
await step('پرچمِ خاموش: نه صفحه، نه منو، نه درخواستِ شمارش؛ #orders ⇐ داشبورد', async () => {
  const { ctx, page, seen } = await openAs(ownerOff, { hash: '#orders' });
  await page.waitForFunction(() => location.hash === '#dashboard', null, { timeout: 10000 });
  await page.waitForTimeout(2000);   // چند دورِ زمان‌سنج
  assert.equal(await page.locator('#page-orders').count(), 0);
  assert.equal(await page.locator('[data-page="orders"]').count(), 0);
  assert.equal(await page.locator('.ord-modal').count(), 0);
  assert.equal(await page.locator('#page-dashboard.active').count(), 1);
  assert.equal(await page.evaluate(() => window.TohidOrders.enabled), false);
  assert.deepEqual(seen.filter((s) => !s.endsWith('/orders/config')), [], `جز config نباید چیزی پرسیده شود: ${seen.join(' | ')}`);
  assert.ok(seen.length >= 1 && seen.length <= 2, `config بیش از اندازه پرسیده شد: ${seen.length}`);
  await ctx.close();
});

/* ══════════════════════ ۲) پرچم روشن — صاحبِ دکان ══════════════════════ */
const { page, seen } = await openAs(owner);

await step('بخش در منو پیدا می‌شود و شش برگهٔ ORD-12 دارد', async () => {
  await page.locator('#sidebar-nav [data-page="orders"]').waitFor({ timeout: 10000 });
  assert.equal(await page.locator('#more-nav-list [data-page="orders"]').count(), 1);
  await page.click('#sidebar-nav [data-page="orders"]');
  await page.waitForSelector('#page-orders.active');
  assert.equal(await page.locator('#page-title').innerText(), 'سفارش‌ها');
  const tabs = await page.locator('#ord-tabs [role="tab"]').allInnerTexts();
  assert.deepEqual(tabs.map((t) => t.trim()), ['جدید', 'در آماده‌سازی', 'آماده', 'در مسیر', 'زمان‌بندی‌شده', 'تمام‌شده']);
  await page.waitForSelector('#ord-list .ord-empty');
  //  کامپیوتر: کلیدِ نما پیداست و پیش‌فرضِ صفحهٔ مدیریت «جدولی» است (VISUAL-DIRECTION §۱٫۵)
  assert.equal(await page.locator('#ord-view').isVisible(), true);
  assert.equal(await page.getAttribute('#ord-list', 'data-view'), 'table');
});

await step('سفارشِ دستی (ORD-21): قطعِ نت وسطِ ثبت ⇒ «ثبت» دوباره، و فقط یک سفارش', async () => {
  let dropped = 0;
  await page.route('**/api/v1/shop/orders', closeSafeRoute(async (route) => {
    if (route.request().method() === 'POST' && !dropped) {
      dropped++;
      await route.fetch();            // سرور واقعاً ثبت می‌کند…
      return route.abort('failed');   // …ولی پاسخ به مرورگر نمی‌رسد
    }
    return route.fallback();
  }));
  await page.click('#ord-new-btn');
  await page.waitForSelector('#ord-new-modal.open');
  await page.selectOption('#ord-f-source', 'phone');
  await page.selectOption('#ord-f-ful', 'delivery');
  await page.fill('#ord-f-name', 'احمد کریمی');
  await page.fill('#ord-f-phone', '0791234567');
  await page.fill('#ord-f-addr', 'کارتهٔ سه، سرکِ دوم');
  await page.fill('#ord-f-fee', '50');
  await page.fill('[name="ln-name"]', 'برنج');
  await page.fill('[name="ln-qty"]', '۱٫۵');
  await page.fill('[name="ln-price"]', '120');
  await page.click('#ord-f-addline');
  await page.locator('[name="ln-name"]').nth(1).fill('روغن');
  await page.locator('[name="ln-qty"]').nth(1).fill('2');
  await page.locator('[name="ln-price"]').nth(1).fill('250.5');
  await page.click('#ord-new-ok');
  await page.waitForFunction(() => /اتصال قطع شد/.test(document.getElementById('ord-new-err').textContent));
  assert.equal((await orderRows()).length, 1, 'سرور همان بارِ اول ثبت کرده بود');
  await page.click('#ord-new-ok');
  await page.waitForFunction(() => !document.querySelector('#ord-new-modal.open'));
  await page.unroute('**/api/v1/shop/orders');
  const rows = await orderRows();
  assert.equal(rows.length, 1, 'تکرارِ همان فرم سفارشِ دوم نساخت');
  const o = rows[0];
  assert.match(o.client_key, /^web-/);
  assert.equal(o.source, 'phone');
  assert.equal(o.fulfillment, 'delivery');
  assert.equal(o.customer_phone, '+93791234567');
  assert.equal(Number(o.delivery_fee_minor), 5000);
  const lines = await many('SELECT qty_milli, unit_price_minor FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [o.id]);
  assert.deepEqual(lines.map((l) => [Number(l.qty_milli), Number(l.unit_price_minor)]), [[1500, 12000], [2000, 25050]]);
});

await step('کارت (ORD-11) و شمارش: ارسال، دو قلم، نقد هنگامِ تحویل، تلفنی؛ نشانِ منو', async () => {
  //  نمای جدولی همان داده را دارد…
  const row = page.locator('.ord-table tbody tr').first();
  await row.waitFor();
  const rowText = await row.innerText();
  for (const w of ['#1', 'احمد کریمی', 'ارسال', '2', 'نقد هنگامِ تحویل', 'تلفنی', 'جدید']) assert.ok(rowText.includes(w), `«${w}» در ردیف نیست: ${rowText}`);
  //  …و کلیدِ «کارتی» نما را عوض می‌کند، برگه و فیلتر سرِ جایش، و ترجیح روی همین دستگاه می‌ماند
  await page.click('#ord-view [data-view="card"]');
  assert.equal(await page.getAttribute('#ord-list', 'data-view'), 'card');
  assert.equal(await page.getAttribute('#ord-tabs [data-tab="new"]', 'aria-selected'), 'true');
  assert.equal(await page.evaluate((u) => localStorage.getItem(`view_mode:${u}:orders`), owner.user.id), 'card');
  const card = page.locator('.ord-card').first();
  await card.waitFor();
  const text = await card.innerText();
  for (const w of ['#1', 'احمد کریمی', 'ارسال', '2 قلم', 'نقد هنگامِ تحویل', 'تلفنی', 'جدید']) assert.ok(text.includes(w), `«${w}» در کارت نیست: ${text}`);
  await page.waitForFunction(() => /1/.test(document.querySelector('#ord-tabs [data-tab="new"] .ord-count')?.textContent || ''));
  await page.waitForFunction(() => document.querySelector('#sidebar-nav [data-ord-badge]')?.textContent === '1');
});

await step('جزئیات (ORD-16/17): شمارهٔ کامل با دکمهٔ تماس برای صاحب؛ خطِ زمانی؛ دکمه‌های مجاز', async () => {
  await page.click('.ord-card');
  await page.waitForSelector('#ord-detail-modal.open .ord-timeline li');
  assert.equal(await page.locator('#ord-detail-title').innerText(), 'سفارش #1');
  assert.equal(await page.locator('#ord-detail-body a[href="tel:+93791234567"]').count(), 2);
  assert.equal(await page.locator('.ord-timeline li').count(), 1);
  const acts = await page.locator('#ord-actions [data-act]').evaluateAll((b) => b.map((x) => x.dataset.act));
  assert.deepEqual(acts, ['accepted', 'rejected', 'cancelled_by_customer'], 'ردِ خودکار کارِ سیستم است، دکمه ندارد');
});

await step('رد (ORD-06): پنجرهٔ دلیل، و «دلیلِ دیگر» بی توضیح تأیید نمی‌شود', async () => {
  await page.click('#ord-actions [data-act="rejected"]');
  await page.waitForSelector('#ord-act-modal.open');
  const reasons = await page.locator('#ord-act-body input[name="ord-reason"]').evaluateAll((x) => x.map((i) => i.value));
  assert.deepEqual(reasons, ['out_of_stock', 'closed', 'busy', 'out_of_area', 'other']);
  assert.equal(await page.locator('#ord-act-ok').isDisabled(), true, 'بی دلیل نمی‌رود');
  await page.check('#ord-act-body input[value="other"]');
  assert.equal(await page.locator('#ord-act-ok').isDisabled(), true, '«دیگر» بی توضیح نمی‌رود');
  await page.fill('#ord-act-note', 'آزمون');
  assert.equal(await page.locator('#ord-act-ok').isDisabled(), false);
  await page.click('#ord-act-modal [data-ord-close].btn');   // انصراف — سفارش دست نخورد
  assert.equal((await orderRows())[0].status, 'new');
});

await step('قبول با زمانِ آماده (ORD-07) ⇒ «در آماده‌سازی»، رویداد و ready_at', async () => {
  await page.click('#ord-actions [data-act="accepted"]');
  await page.waitForSelector('#ord-act-modal.open #ord-ready');
  assert.equal(await page.inputValue('#ord-ready'), '15');
  await page.selectOption('#ord-ready', '30');
  const t0 = Date.now();
  await page.click('#ord-act-ok');
  await page.waitForFunction(() => document.querySelectorAll('.ord-timeline li').length === 2);
  assert.match(await page.locator('#ord-detail-body .ord-status-line').innerText(), /قبول‌شده/);
  assert.match(await page.locator('#ord-detail-body .ord-status-line').innerText(), /آماده تا/);
  const o = (await orderRows())[0];
  assert.equal(o.status, 'accepted');
  const ready = Number(o.ready_at) - t0;
  assert.ok(ready > 29 * 60_000 && ready < 31 * 60_000, `ready_at: ${ready}`);
  const ev = await one("SELECT * FROM online_order_events WHERE order_id=$1 AND seq=2", [o.id]);
  assert.equal(ev.to_status, 'accepted');
  assert.equal(ev.actor_id, owner.user.id);
  const acts = await page.locator('#ord-actions [data-act]').evaluateAll((b) => b.map((x) => x.dataset.act));
  assert.deepEqual(acts, ['picking', 'cancelled']);
  await page.waitForFunction(() => /1/.test(document.querySelector('#ord-tabs [data-tab="preparing"] .ord-count')?.textContent || ''));
});

await step('لغو با دلیلِ «دیگر» و توضیح ⇒ «تمام‌شده»؛ دلیل و توضیح در خطِ زمانی', async () => {
  await page.click('#ord-actions [data-act="cancelled"]');
  await page.waitForSelector('#ord-act-modal.open');
  const reasons = await page.locator('#ord-act-body input[name="ord-reason"]').evaluateAll((x) => x.map((i) => i.value));
  assert.deepEqual(reasons, ['customer_request', 'out_of_stock', 'no_show', 'other']);
  await page.check('#ord-act-body input[value="other"]');
  await page.fill('#ord-act-note', 'مشتری زنگ زد و نخواست');
  await page.click('#ord-act-ok');
  await page.waitForFunction(() => document.querySelectorAll('.ord-timeline li').length === 3);
  const last = await page.locator('.ord-timeline li').last().innerText();
  assert.match(last, /دلیلِ دیگر/);
  assert.match(last, /مشتری زنگ زد و نخواست/);
  assert.match(last, /شما/);
  assert.equal(await page.locator('#ord-actions').count(), 0, 'سفارشِ بسته دکمه ندارد');
  const o = (await orderRows())[0];
  assert.equal(o.status, 'cancelled');
  assert.equal(o.status_reason, 'other');
  await page.keyboard.press('Escape');
  await page.click('#ord-tabs [data-tab="done"]');
  await page.waitForFunction(() => /#1/.test(document.getElementById('ord-list').innerText));
});

await step('۴۰۹: کسِ دیگری زودتر وضعیت را عوض کرد ⇒ پیام و وضعیتِ تازه، بی گذارِ دوم', async () => {
  const made = await apiOrder();
  assert.equal(made.status, 201, JSON.stringify(made.body));
  await page.click('#ord-tabs [data-tab="new"]');
  await page.locator(`.ord-card[data-id="${made.body.order.id}"]`).click();
  await page.waitForSelector('#ord-actions [data-act="cancelled_by_customer"]');
  //  برگه پنهان ⇒ صفِ زنده خودش جزئیات را تازه نمی‌کند و دکمهٔ کهنه سرِ جایش می‌ماند
  await setVisible(page, false);
  const moved = await post(`/api/v1/shop/orders/${made.body.order.id}/transition`, { to: 'accepted', from: 'new' }, owner.accessToken);
  assert.equal(moved.status, 200);
  await page.evaluate(() => document.querySelector('#ord-actions [data-act="cancelled_by_customer"]').click());
  await page.waitForFunction(() => /قبول‌شده/.test(document.querySelector('#ord-detail-body .ord-status-line')?.textContent || ''));
  await setVisible(page, true);
  const row = await one('SELECT status FROM online_orders WHERE id=$1', [made.body.order.id]);
  assert.equal(row.status, 'accepted');
  const n = await one('SELECT COUNT(*)::int n FROM online_order_events WHERE order_id=$1', [made.body.order.id]);
  assert.equal(n.n, 2);
  await page.keyboard.press('Escape');
});

await step('پاسخِ دیرِ سفارشِ بسته‌شده جای سفارشِ تازه‌باز نمی‌نشیند؛ گذار روی همانی است که باز است', async () => {
  const a = (await apiOrder({ customer: { name: 'مشتریِ الف', phone: '0700111222' } })).body.order;
  const b = (await apiOrder({ customer: { name: 'مشتریِ ب', phone: '0700111222' } })).body.order;
  await page.click('#ord-tabs [data-tab="new"]');
  await page.locator(`.ord-card[data-id="${a.id}"]`).click();
  await page.waitForSelector('#ord-actions [data-act="accepted"]');
  //  از این‌جا هر پاسخِ جزئیاتِ «الف» نگه داشته می‌شود — همان کندیِ شبکه‌ای که در CI پیش آمد
  const aPath = `/api/v1/shop/orders/${a.id}`;
  const held = [];
  const isA = (u) => u.pathname === aPath;
  await page.route(isA, (route) => { held.push(route); });
  //  تغییری روی سرور ⇒ صفِ زنده جزئیاتِ بازِ «الف» را بی‌صدا دوباره می‌پرسد (و پاسخ در راه می‌ماند)
  assert.equal((await post(`/api/v1/shop/orders/${a.id}/transition`, { to: 'accepted', from: 'new' }, owner.accessToken)).status, 200);
  for (let i = 0; i < 100 && !held.length; i++) await page.waitForTimeout(50);
  assert.ok(held.length > 0, 'تازه‌سازیِ بی‌صدای «الف» پرسیده نشد');
  await page.keyboard.press('Escape');
  await page.locator(`.ord-card[data-id="${b.id}"]`).click();
  const showsB = async (when) => {
    try {
      await page.waitForFunction((no) => document.getElementById('ord-detail-title')?.textContent === `سفارش #${no}`
        && !!document.querySelector('#ord-actions [data-act="accepted"]'), Number(b.orderNo), { timeout: 10000 });
    } catch {
      throw new Error(`${when}: پنجره «${await page.locator('#ord-detail-title').innerText()}» را نشان می‌دهد، نه «سفارش #${b.orderNo}»`);
    }
  };
  await showsB('پیش از رسیدنِ پاسخِ دیر');
  //  حالا پاسخِ دیرِ «الف» می‌رسد
  const late = page.waitForResponse((r) => isA(new URL(r.url())));
  for (const r of held.splice(0)) await r.continue();
  await late;
  await page.unroute(isA);
  await page.waitForTimeout(300);
  await showsB('پس از رسیدنِ پاسخِ دیر');
  await page.click('#ord-actions [data-act="accepted"]');
  await page.waitForSelector('#ord-act-modal.open #ord-ready');
  await page.click('#ord-act-ok');
  await page.waitForFunction(() => /قبول‌شده/.test(document.querySelector('#ord-detail-body .ord-status-line')?.textContent || ''));
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [b.id])).status, 'accepted');
  assert.equal((await one('SELECT COUNT(*)::int n FROM online_order_events WHERE order_id=$1', [a.id])).n, 2, '«الف» گذارِ دوم نخورد');
  assert.equal((await one('SELECT COUNT(*)::int n FROM online_order_events WHERE order_id=$1', [b.id])).n, 2);
  await page.keyboard.press('Escape');
});

await step('صفحه‌بندی: ۳۲ سفارشِ تازه ⇒ ۳۰ و «بیشتر» ⇒ ۳۲', async () => {
  for (let i = 0; i < 32; i++) assert.equal((await apiOrder({ customer: { name: `مشتری ${i}`, phone: '0700111222' } })).status, 201);
  //  صفِ زنده خودش تازه می‌کند (بی بارگذاریِ دوباره)
  await page.waitForFunction(() => document.querySelectorAll('#ord-list .ord-card').length === 30, null, { timeout: 10000 });
  await page.waitForTimeout(1600);   // شمارش‌ها آرام بگیرند
  await page.click('#ord-more');
  await page.waitForFunction(() => document.querySelectorAll('#ord-list .ord-card').length === 32);
  assert.equal(await page.locator('#ord-more').isHidden(), true);
  await page.waitForFunction(() => document.querySelector('#sidebar-nav [data-ord-badge]')?.textContent === '32');
});

await step('صفِ زنده فقط وقتی برگه پیداست: پنهان ⇒ هیچ درخواستِ شمارش', async () => {
  await setVisible(page, false);
  const before = seen.filter((s) => s.includes('/summary')).length;
  await page.waitForTimeout(2500);
  assert.equal(seen.filter((s) => s.includes('/summary')).length, before, 'در پس‌زمینه شمارش پرسیده شد');
  await setVisible(page, true);
  await page.waitForTimeout(1600);
  assert.ok(seen.filter((s) => s.includes('/summary')).length > before, 'برگشت به جلو ⇒ شمارش دوباره');
});

/* ══════════════════════ ۲-ب) افزونهٔ پشتِ پرچم: ORD-03 و ORD-07 ══════════════════════ */
const deskLoaded = () => page.evaluate(() => performance.getEntriesByType('resource').some((e) => /orders-desk\.js/.test(e.name)));
await step('بی پرچم‌های presence/delay: افزونه (orders-desk.js) بار نمی‌شود، نه نشانگر، نه «عقب انداختن»', async () => {
  assert.equal(await deskLoaded(), false);
  assert.equal(await page.evaluate(() => !!window.TohidOrdersDesk), false);
  assert.equal(await page.locator('#ord-presence, #ord-net').count(), 0);
  const made = (await apiOrder()).body.order;
  assert.equal((await post(`/api/v1/shop/orders/${made.id}/transition`, { to: 'accepted', readyInMinutes: 15 }, owner.accessToken)).status, 200);
  await page.click('#ord-tabs [data-tab="preparing"]');
  await page.locator(`.ord-card[data-id="${made.id}"]`).click();
  await page.waitForSelector('#ord-actions [data-act="picking"]');
  assert.equal(await page.locator('[data-delay]').count(), 0);
  await page.keyboard.press('Escape');
});

/** پرچمِ تازه ⇒ برنامه config را دوباره می‌پرسد (همان کاری که هر ۱۰ دقیقه می‌کند) */
const reconfig = async () => {
  await page.evaluate(() => window.TohidOrders.refreshConfig());
  await page.waitForFunction(() => !!window.TohidOrdersDesk, null, { timeout: 10000 });
};

await step('ORD-03: نشانگرِ بزرگِ «باز / بسته» همان وضعیتِ ویترین — باز، اعلامِ «بسته» ⇒ بسته', async () => {
  await liveConfig.set(String(shopOn), 'shop.storefront', true);
  await liveConfig.set(String(shopOn), 'shop.orders.presence', true);
  const sfPut = await call('PUT', '/api/v1/shop/storefront', { slug: `web-ord-${Date.now().toString(36)}`, enabled: true, title: 'ویترینِ آزمون', contactPhone: '0700000001' }, owner.accessToken);
  assert.equal(sfPut.status, 200, JSON.stringify(sfPut.body));
  //  یک دستگاهِ دکان همین حالا همگام شد ⇒ «اتصالِ تأییدشده» (همان سنجهٔ ویترین، D-18)
  await query(`INSERT INTO sync_devices (app, account_kind, account_id, device_id, last_seen_at)
    VALUES ('shop','shop',$1,'web-orders-dev',$2)
    ON CONFLICT (app, account_kind, account_id, device_id) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at`, [shopOn, Date.now()]);
  await reconfig();
  await page.waitForSelector('#ord-presence:not([hidden])', { timeout: 10000 });
  const box = page.locator('#ord-presence');
  const pub = (await call('GET', '/api/v1/shop/orders/summary', undefined, owner.accessToken)).body.presence;
  assert.deepEqual([pub.badge, pub.connection, pub.published], ['open_online', 'confirmed', true], JSON.stringify(pub));
  await page.waitForFunction(() => document.querySelector('#ord-presence b')?.textContent.trim() === 'باز', null, { timeout: 10000 });
  assert.match(await box.innerText(), /سفارش می‌گیرد/);
  assert.match(await box.getAttribute('class'), /p-open/);
  //  اعلامِ «بسته» از دستگاهِ دیگر ⇒ پرسشِ بعدی همین‌جا «بسته» (همان چیزی که مشتری می‌بیند)
  const d = await post('/api/v1/shop/storefront/presence', { eventId: `ev-web-${Date.now()}`, mode: 'closed' }, owner.accessToken);
  assert.equal(d.status, 200, JSON.stringify(d.body));
  await page.waitForFunction(() => document.querySelector('#ord-presence b')?.textContent.trim() === 'بسته', null, { timeout: 10000 });
  assert.match(await box.innerText(), /اعلامِ «بسته»/);
  assert.match(await box.getAttribute('class'), /p-closed/);
  assert.equal(await box.getAttribute('role'), 'status');
});

await step('ORD-03: سرور نرسید (۵۰۳ یا قطعِ اینترنت) ⇒ هشدارِ «اتصال قطع است»؛ برگشت ⇒ می‌رود', async () => {
  const warn = page.locator('#ord-net');
  assert.equal(await warn.isHidden(), true);
  await page.route('**/api/v1/shop/orders/summary', (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{"error":{"code":"unavailable","message":"x"}}' }));
  await page.waitForSelector('#ord-net:not([hidden])', { timeout: 10000 });
  assert.match(await warn.innerText(), /اتصال قطع است/);
  assert.match(await warn.innerText(), /آخرین به‌روزرسانی/);
  assert.equal(await warn.getAttribute('role'), 'alert');
  assert.equal(await page.locator('#ord-presence').isHidden(), false, '«باز / بسته» ِ پیشین سرِ جایش');
  await page.unroute('**/api/v1/shop/orders/summary');
  await page.waitForSelector('#ord-net[hidden]', { state: 'attached', timeout: 10000 });
  //  قطعِ اینترنتِ خودِ دستگاه ⇒ همان لحظه
  await page.context().setOffline(true);
  await page.waitForSelector('#ord-net:not([hidden])', { timeout: 5000 });
  await page.context().setOffline(false);
  await page.waitForSelector('#ord-net[hidden]', { state: 'attached', timeout: 10000 });
});

await step('ORD-07: «عقب انداختن» — قطعِ نت وسطِ ثبت ⇒ «تأیید» دوباره، و فقط یک بار عقب؛ خطِ زمانی', async () => {
  await liveConfig.set(String(shopOn), 'shop.orders.delay', true);
  await reconfig();
  await page.waitForFunction(() => !!(window.TohidOrders && document.getElementById('page-orders')));
  const made = (await apiOrder()).body.order;
  const acc = await post(`/api/v1/shop/orders/${made.id}/transition`, { to: 'accepted', readyInMinutes: 15 }, owner.accessToken);
  const before = Number(acc.body.order.readyAt);
  //  پنجره‌ای که پیش از روشن شدن باز بود ⇒ دوباره
  await page.click('#ord-tabs [data-tab="preparing"]');
  await page.locator(`.ord-card[data-id="${made.id}"]`).click();
  await page.waitForSelector('#ord-actions [data-delay]', { timeout: 10000 });
  await page.click('#ord-actions [data-delay]');
  await page.waitForSelector('#ord-delay-modal.open #ord-delay-min');
  assert.equal(await page.inputValue('#ord-delay-min'), '15');
  await page.selectOption('#ord-delay-min', '20');
  await page.check('#ord-delay-body input[value="other"]');
  assert.equal(await page.locator('#ord-delay-ok').isDisabled(), true, '«دیگر» بی توضیح نمی‌رود');
  await page.fill('#ord-delay-note', 'منتظرِ نان');
  assert.equal(await page.locator('#ord-delay-ok').isDisabled(), false);
  let dropped = 0;
  const delayPath = `**/api/v1/shop/orders/${made.id}/delay`;
  await page.route(delayPath, closeSafeRoute(async (route) => {
    if (!dropped) { dropped++; await route.fetch(); return route.abort('failed'); }
    return route.fallback();
  }));
  await page.click('#ord-delay-ok');
  await page.waitForFunction(() => /اتصال قطع شد/.test(document.getElementById('ord-delay-err').textContent));
  await page.click('#ord-delay-ok');
  await page.waitForFunction(() => !document.querySelector('#ord-delay-modal.open'));
  await page.unroute(delayPath);
  await page.waitForFunction(() => /عقب افتاد/.test(document.querySelector('.ord-timeline')?.innerText || ''));
  const row = await one('SELECT ready_at, status FROM online_orders WHERE id=$1', [made.id]);
  assert.equal(Number(row.ready_at), before + 20 * 60_000, 'روی زمانِ سرور، یک بار');
  assert.equal(row.status, 'accepted');
  const ev = await many(`SELECT reason, note, actor_id FROM online_order_events WHERE order_id=$1 AND kind='note'`, [made.id]);
  assert.deepEqual(ev.map((e) => [e.reason, e.note, e.actor_id]), [['other', 'منتظرِ نان', owner.user.id]]);
  const line = await page.locator('.ord-timeline li.ord-ev-delay').innerText();
  assert.match(line, /دلیلِ دیگر/);
  assert.match(line, /منتظرِ نان/);
  assert.match(line, /پیش‌تر/);
});

await step('ORD-07: کسِ دیگری زودتر عقب انداخت ⇒ پیام و زمانِ تازه، بی جمعِ ناخواسته', async () => {
  assert.equal(await page.locator('#ord-detail-modal.open').count(), 1, 'جزئیاتِ همان سفارش باز است');
  await page.click('#ord-actions [data-delay]');
  await page.waitForSelector('#ord-delay-modal.open');
  const orderId = (await one(`SELECT order_id FROM online_order_events WHERE shop_id=$1 AND kind='note' ORDER BY created_at DESC LIMIT 1`, [shopOn])).order_id;
  const other = await post(`/api/v1/shop/orders/${orderId}/delay`, { minutes: 5 }, owner.accessToken, { 'Idempotency-Key': `other-device-${Date.now()}` });
  assert.equal(other.status, 200, JSON.stringify(other.body));
  await page.click('#ord-delay-ok');
  await page.waitForFunction(() => !document.querySelector('#ord-delay-modal.open'));
  await page.waitForFunction(() => document.querySelectorAll('.ord-timeline li.ord-ev-delay').length === 2, null, { timeout: 10000 });
  assert.equal((await one(`SELECT COUNT(*)::int n FROM online_order_events WHERE order_id=$1 AND kind='note'`, [orderId])).n, 2,
    'زمانِ کهنهٔ پنجره روی زمانِ تازه جمع نشد');
  await page.keyboard.press('Escape');
});

if (axeSrc) {
  await step('دسترس‌پذیری: نامِ دکمه، برچسبِ فیلد، کنتراست — صفحه و فرمِ سفارشِ دستی', async () => {
    await page.addScriptTag({ content: axeSrc });
    await page.waitForTimeout(400);
    const check = (sel) => page.evaluate(async (s) => {
      const out = await window.axe.run(document.querySelector(s), { runOnly: ['color-contrast', 'button-name', 'label', 'link-name'] });
      return out.violations.map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`);
    }, sel);
    const bad = [...await check('#page-orders')];
    await page.click('#ord-new-btn');
    await page.waitForSelector('#ord-new-modal.open');
    await page.waitForTimeout(400);
    bad.push(...await check('#ord-new-modal .modal-box'));
    await page.keyboard.press('Escape');
    assert.deepEqual(bad, [], bad.join('\n    '));
  });
}

/* ══════════════════════ ۳) کارمند — ORD-17 ══════════════════════ */
/* ══════════════════════ ۲-پ) CHK-10: فهرستِ «خبرم کن» ِ ویترین (license/area-requests.js) ══════════════════════ */
const areaLoaded = () => page.evaluate(() => performance.getEntriesByType('resource').some((e) => /area-requests\.js/.test(e.name)));
await step('CHK-10 بی پرچمِ notify_area: area-requests.js بار نمی‌شود و دکمهٔ «خبرم کن» نیست', async () => {
  for (let i = 0; i < 2; i++) await page.keyboard.press('Escape');
  assert.equal(await areaLoaded(), false);
  assert.equal(await page.locator('#ord-area-btn').count(), 0);
});

await step('CHK-10: «خبرم کن» — شماره فقط برای محلِ پوشیده، تقاضای بیرون بی شماره، «خبر دادم» ردیف را پاک می‌کند', async () => {
  await liveConfig.set(String(shopOn), 'shop.storefront.zones', true);
  await liveConfig.set(String(shopOn), 'shop.storefront.notify_area', true);
  const z = await call('PUT', '/api/v1/shop/storefront/delivery', { zones: [{ name: 'کارته‌سه', kind: 'polygon',
    polygon: [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]], feeMinor: 3000, minOrderMinor: 0 }] }, owner.accessToken);
  assert.equal(z.status, 200, JSON.stringify(z.body));
  const t0 = Date.now();
  for (const [id, phone, lat, lng] of [['01HAREAWEB0000000000000001', '+93791200077', 34.53, 69.17], ['01HAREAWEB0000000000000002', '+93791200078', 34.70, 69.40]]) {
    await query(`INSERT INTO storefront_area_requests (id, shop_id, phone, cell_lat, cell_lng, created_at, updated_at)
      VALUES ($1,$2,$3,$4,$5,$6,$6)`, [id, String(shopOn), phone, lat, lng, t0]);
  }
  await page.evaluate(() => window.TohidOrders.refreshConfig());
  await page.waitForSelector('#ord-area-btn', { timeout: 10000 });
  await page.waitForFunction(() => document.getElementById('ord-area-n')?.textContent.trim() === '۱', null, { timeout: 10000 });
  await page.click('#ord-area-btn');
  await page.waitForSelector('#ord-area-modal.open .ord-area-row');
  const body = await page.locator('#ord-area-body').innerText();
  assert.ok(body.includes('+93791200077') && !body.includes('+93791200078'), `شمارهٔ بیرون نباید برسد: ${body}`);
  assert.match(body, /هنوز بیرون از ناحیه‌ها/);
  assert.match(body, /سرور چیزی نمی‌فرستد/);
  assert.equal(await page.locator('#ord-area-body a[href="tel:+93791200077"]').count(), 1);
  await page.click('[data-area-done="01HAREAWEB0000000000000001"]');
  await page.waitForFunction(() => !document.querySelector('#ord-area-body .ord-area-row'), null, { timeout: 10000 });
  assert.match(await page.locator('#ord-area-body').innerText(), /کسی برای خبر دادن نیست/);
  assert.deepEqual((await many('SELECT id FROM storefront_area_requests WHERE shop_id = $1', [String(shopOn)])).map((r) => r.id), ['01HAREAWEB0000000000000002']);
  assert.equal(await page.locator('#ord-area-n').isHidden(), true, 'نشانِ شمار پس از خبر دادن می‌رود');
  await page.click('#ord-area-modal [data-area-close]');
  await page.waitForFunction(() => !document.querySelector('#ord-area-modal.open'));
});

await step('کارمند (۳۹۰ پیکسل): شمارهٔ نقاب‌دار، بی دکمهٔ تماس؛ دکمه‌های گذار هست؛ بی بیرون‌زدگی', async () => {
  const s = await openAs(staff, { width: 390, hash: '#orders' });
  await s.page.waitForSelector('#page-orders.active', { timeout: 10000 });
  //  CHK-10: فهرستِ «خبرم کن» (شمارهٔ مشتری) فقط صاحب و مدیر — پرچم روشن است ولی کارمند نه دکمه دارد نه افزونه
  assert.equal(await s.page.locator('#ord-area-btn').count(), 0, 'کارمند ⇒ بی «خبرم کن»');
  await s.page.click('#ord-tabs [data-tab="new"]');
  await s.page.locator('.ord-card').first().waitFor();
  assert.equal(await s.page.locator('#ord-view').isVisible(), false, 'گوشی همیشه کارتی است، بی کلیدِ نما');
  assert.ok((await s.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)) <= 1, 'صفحه افقی بیرون زد');
  await s.page.locator('.ord-card').first().click();
  await s.page.waitForSelector('#ord-detail-modal.open .ord-timeline li');
  const body = await s.page.locator('#ord-detail-body').innerText();
  assert.match(body, /•/, `شماره باید نقاب‌دار باشد: ${body}`);
  assert.ok(!body.includes('0700111222') && !body.includes('+93700111222'), 'شمارهٔ کامل نباید برسد');
  assert.equal(await s.page.locator('#ord-detail-body a[href^="tel:"]').count(), 0);
  assert.ok(await s.page.locator('#ord-actions [data-act="accepted"]').count() === 1, 'کارمند orders.manage دارد');
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

console.log('\n── نسخهٔ وب: بخشِ «سفارش‌ها» ───────────────');
for (const [mark, name] of results) console.log(`${mark}  ${name}`);
console.log(`\n${results.length - failures}/${results.length} سبز`);
process.exit(failures ? 1 : 0);
