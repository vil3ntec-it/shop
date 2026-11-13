/**
 * ویترین با کرومیومِ واقعی: ممیزیِ «با چشمِ مشتری» (۱۴۰۵/۰۸/۱۷)، گوشیِ ۳۶۰×۷۶۰ — شش مشکلِ این شاخه
 * (۳، ۴، ۷ و ۸ را `web-store-lines.mjs`/`web-store-zones.mjs` ِ PR ‏#955 می‌سنجند):
 *
 *     npm run test:web-store      (پس از web-store-dari.mjs)
 *     AUDIT_SHOTS=<پوشه> node test/web-store-audit.mjs   ⇐ عکس‌های «پس از» با همان نامِ عکس‌های ممیزی
 *
 *   ۱) WEB-11: ♥ ِ دلخواه دکمهٔ «+ افزودن» را نمی‌پوشاند (`elementFromPoint` ِ مرکزِ «+» خودِ همان دکمه است) و ♥ واقعاً
 *      ♥ است، نه ⓘ ِ «نشانهٔ ناشناس» — در کارت و برگهٔ کالا؛
 *   ۲) WEB-02/05/06: کادرِ جست‌وجو و نخستین کالا در صفحهٔ اول؛ با تایپ، بنرها و ردیف‌ها کنار و نتیجه درست زیرِ کادر؛
 *   ۵) WEB-01: لوگو و تصویرِ سرِ خودِ دکان در ویترین، و لوگو (نه عکسِ یکی از کالاها) روی کارتِ بازارچه؛
 *   ۶) WEB-02/03، CHK-11: نشانی، «مسیر روی نقشه» و ساعتِ کاریِ امروز در سربرگ، «دریافت از دکان» و پیگیری؛ «ساعتِ دکان»
 *      و «آخرین تأییدِ اتصال» برای دکانِ متصل دیده نمی‌شوند؛
 *   ۹) WEB-07: شمارِ کالا، «مرتب‌سازی» (ارزان‌ترین)، فیلترِ دکمه‌شکل با رقمِ فارسی، و قیمتِ پیشینِ خط‌خورده بی پرچمِ بازارچه؛
 *  ۱۰) ORD-14/13: «می‌پذیرم» دکمهٔ اصلیِ پررنگ و «نمی‌پذیرم» دکمهٔ دوم؛ سفارشِ ردشده به‌خاطرِ «ناموجود» کالاهای مشابه دارد.
 *  بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const F = require('./image-fixtures');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };
const SHOTS = process.env.AUDIT_SHOTS || '';

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
             VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
const app = await createApp({ runMigrations: false });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const base = `http://127.0.0.1:${server.address().port}`;

const call = async (method, p, b, t, headers = {}) => {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
    body: b === undefined || b === null ? undefined : JSON.stringify(b) });
  return { status: r.status, body: await r.json().catch(() => null) };
};
const raw = async (p, buf, type, t) => (await fetch(base + p, { method: 'PUT', headers: { 'Content-Type': type, Authorization: `Bearer ${t}` }, body: buf })).status;

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@audit.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'سوپرمارکت گلستان' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (scope, key, value = true) => call('PUT', '/api/admin/pump/live-config', { scope, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.favorites', 'shop.storefront.banners', 'shop.storefront.media',
  'shop.storefront.filters', 'shop.orders.amend']) await flag(shopId, key);
await flag('all', 'market.directory');
const slug = 'golestan';
const day = [[480, 1260]];
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'سوپرمارکت گلستان', tagline: 'کالای روزانهٔ خانه', contactPhone: '0700000001',
  acceptPickup: true, acceptDelivery: true, deliveryFeeMinor: 5000, minOrderMinor: 0, payAfterAccept: false,
  weeklyHours: Object.fromEntries([0, 1, 2, 3, 4, 5, 6].map((d) => [String(d), day])),
  pickupAddress: 'کابل، کارتهٔ سه، سرکِ مسجدِ جامع', pickupPin: { lat: 34.5281, lng: 69.1723 } }, T);
ok(put.status === 200 && put.body.settings.pickupAddress, 'ویترین با نشانی و پینِ دکان (مهاجرتِ ۲۱۵)', JSON.stringify(put.body).slice(0, 160));
const PRODUCTS = [
  ['rice', 'برنج سیلا ۵ کیلو', 1450, 'خوراکی'], ['oil', 'روغن نباتی ۵ لیتر', 980, 'خوراکی'], ['sugar', 'بوره ۱ کیلو', 95, 'خوراکی'],
  ['tea', 'چای سبز', 250, 'خوراکی'], ['flour', 'آرد ۱۰ کیلو', 900, 'خوراکی'], ['beans', 'لوبیا', 140, 'خوراکی'],
  ['milk', 'شیر تازه ۱ لیتر', 60, 'لبنیات'], ['yogurt', 'ماست', 75, 'لبنیات'], ['cheese', 'پنیر', 220, 'لبنیات'],
  ['soap', 'صابون', 35, 'شوینده'], ['water', 'آب معدنی', 25, 'نوشیدنی'], ['juice', 'آب میوه', 1650, 'نوشیدنی'],
];
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: PRODUCTS.map(([id, name, salePrice, category]) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields: { name, unit: 'بسته', salePrice, category, active: true },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالاها از راهِ همگام‌سازیِ واقعی رسیدند');
for (const [id] of PRODUCTS) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, ...(id === 'oil' ? { onlinePriceMinor: 92000 } : {}) }, T);
{
  const t0 = Date.now(); const H = 3600 * 1000;
  const bn = await call('PUT', '/api/shop/storefront/banners', { banners: [
    { title: 'روغن ارزان‌تر شد', body: 'تا پایانِ هفته', startAt: t0 - H, endAt: t0 + H, sort: 9, link: { type: 'product', value: 'oil' } },
    { title: 'لبنیاتِ تازه هر صبح', startAt: t0 - H, endAt: t0 + H, sort: 5, link: { type: 'category', value: 'لبنیات' } },
  ] }, T);
  ok(bn.status === 200, 'دو بنر');
}
//  لوگوی سبز با دایرهٔ سفید و تصویرِ سرِ رنگی (تا عکس‌های «پس از» چیزی نشان دهند)
const logo = F.pngImage(256, 256, (x, y) => ((x - 128) ** 2 + (y - 128) ** 2 < 70 ** 2 ? [255, 255, 255] : [14, 130, 120]), { mode: 'rgb' });
const header = F.pngImage(1200, 400, (x, y) => [20 + Math.round(x / 6), 120 + Math.round(y / 8), 140], { mode: 'rgb' });
ok(await raw('/api/shop/storefront/media/logo', logo, 'image/png', T) === 200
  && await raw('/api/shop/storefront/media/header', header, 'image/png', T) === 200, 'لوگو و تصویرِ سر بارگذاری شد');
ok((await call('PUT', '/api/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }, T)).status === 200, 'دکان در بازارچه');

//  مشتری: تأییدِ شماره و دو سفارشِ «دریافت از دکان» (یکی برای پیشنهادِ ویرایش، یکی ردشده به‌خاطرِ «ناموجود»)
const phone = '0791112266';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
let seq = 0;
const checkout = async (lines) => {
  const ck = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines },
    null, { 'Idempotency-Key': `web-audit-${Date.now()}-${++seq}`, 'X-Store-Ticket': vc.body.ticket });
  ok(ck.status === 201, `سفارشِ ${seq} ثبت شد`, JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, ck.body.order.orderNo])).id;
  return { token: ck.body.order.token, id };
};
const oa = await checkout([{ productId: 'rice', qtyMilli: 2000 }, { productId: 'tea', qtyMilli: 1000 }]);
const ob = await checkout([{ productId: 'rice', qtyMilli: 1000 }]);
const am = await call('POST', `/api/shop/orders/${oa.id}/amendments`, { changes: [{ lineNo: 1, qtyMilli: 1000 }], reason: 'out_of_stock' }, T,
  { 'Idempotency-Key': `amend-${Date.now()}` });
ok(am.status === 201, 'دکان ویرایش پیشنهاد داد', JSON.stringify(am.body).slice(0, 120));
const rj = await call('POST', `/api/shop/orders/${ob.id}/transition`, { to: 'rejected', reason: 'out_of_stock' }, T);
ok(rj.status === 200, 'دکان سفارشِ دوم را به‌خاطرِ «ناموجود» نپذیرفت', JSON.stringify(rj.body).slice(0, 120));

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, deviceScaleFactor: 2, locale: 'fa-AF', colorScheme: 'light' });
await ctx.addInitScript((t) => {
  if (sessionStorage.getItem('seeded')) return;
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
}, { email: vc.body.email, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const shot = async (name, full = false) => {
  if (!SHOTS) return;
  fs.mkdirSync(SHOTS, { recursive: true });
  await page.waitForTimeout(250);
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: full });
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const rect = (sel) => page.$eval(sel, (e) => { const r = e.getBoundingClientRect(); return { top: r.top, bottom: r.bottom, h: r.height }; }).catch(() => null);

await page.goto(`${base}/store/${slug}`);
await page.waitForSelector('#products li.p-card', { timeout: 15000 });
await page.waitForSelector('#products li.p-card .fav', { timeout: 10000 });
await page.waitForSelector('#banners:not([hidden]) .banner', { timeout: 10000 });
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(400);

//  ── ۲) صفحهٔ اول ──
{
  const q = await rect('#q');
  ok(q && q.bottom <= 760, 'کادرِ جست‌وجو در صفحهٔ اول (WEB-02)', JSON.stringify(q));
  const first = await page.evaluate(() => Math.min(...[...document.querySelectorAll('li.p-card')].filter((li) => li.offsetParent).map((li) => li.getBoundingClientRect().top)));
  ok(first < 760 - 120, 'دست‌کم یک کالا در صفحهٔ اولِ گوشی (نه فقط کارتِ دکان و بنر)', String(first));
  await shot('01-catalog-home-phone');
}
//  ── ۵) لوگو و تصویرِ سر ──
{
  const logo = await page.$eval('#store-avatar img', (i) => i.getAttribute('src')).catch(() => '');
  ok(/^\/api\/store\/golestan\/media\/logo\/1$/.test(logo), 'لوگوی خودِ دکان به‌جای حرفِ نخست (WEB-01)', logo);
  ok(await page.$eval('#bar-avatar img', (i) => /media\/logo/.test(i.src)).catch(() => false), 'لوگو در نوارِ بالا هم');
  ok(await page.$eval('#store-cover img', (i) => /media\/header/.test(i.src) && i.getBoundingClientRect().height > 40).catch(() => false), 'تصویرِ سرِ دکان بالای کارت');
}
//  ── ۶) نشانی، نقشه، ساعت؛ بی زبانِ فنی ──
{
  const hero = await text('.shop-hero');
  ok(hero.includes('کارتهٔ سه، سرکِ مسجدِ جامع'), 'نشانیِ دکان در سربرگ', hero.slice(0, 160));
  const map = await page.$eval('.shop-hero a[href*="google.com/maps"]', (a) => ({ href: a.href, t: a.textContent, target: a.target })).catch(() => null);
  ok(map && map.href.includes('34.5281%2C69.1723') && map.t === 'مسیر روی نقشه' && map.target === '_blank', '«مسیر روی نقشه» با پینِ دکان', JSON.stringify(map));
  const st = await text('#store-status');
  ok(/امروز/.test(st) && /۸|۰۸/.test(st), 'ساعتِ کاریِ امروز دیده می‌شود', st);
  ok(!/ساعتِ دکان/.test(hero) && !/آخرین تأییدِ اتصال/.test(hero), '«ساعتِ دکان» و «آخرین تأییدِ اتصال» برای دکانِ متصل نیست', st);
}
//  ── ۱) ♥ و «+» ──
{
  const hit = await page.$$eval('#products li.p-card', (cards) => cards.slice(0, 6).map((li) => {
    const b = li.querySelector('.p-add').getBoundingClientRect();
    li.scrollIntoView({ block: 'center' });
    const r = li.querySelector('.p-add').getBoundingClientRect();
    const e = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
    return Boolean(b.width) && Boolean(e && e.closest('.p-add') === li.querySelector('.p-add'));
  }));
  ok(hit.length === 6 && hit.every(Boolean), 'مرکزِ «+ افزودن» ِ هر کارت خودِ همان دکمه است — ♥ آن را نمی‌پوشاند (WEB-11)', JSON.stringify(hit));
  const icons = await page.evaluate(() => ({
    fav: document.querySelector('#products .fav svg path').getAttribute('d'), info: K.ICONS.info, heart: K.ICONS.heart || '',
  }));
  ok(icons.fav !== icons.info && icons.fav === icons.heart, '♥ واقعاً ♥ است، نه ⓘ', icons.fav.slice(0, 30));
  //  همان دکمه کار می‌کند
  await page.locator('#products li[data-id="milk"] .p-add').click({ timeout: 5000 }).catch(() => {});
  await page.waitForFunction(() => /۱/.test((document.querySelector('#products li[data-id="milk"] .p-add') || {}).textContent || ''), null, { timeout: 5000 }).catch(() => {});
  ok(/۱/.test(await text('#products li[data-id="milk"] .p-add')), 'زدنِ «+» یک واحد به سبد افزود');
  await page.locator('#products').scrollIntoViewIfNeeded();
  await shot('02-catalog-products-phone');
}
//  ── ۹) شمار، مرتب‌سازی، فیلتر، خط‌خورده ──
{
  ok((await text('#cat-count')) === '۱۲ کالا', 'شمارِ کالا: «۱۲ کالا»', await text('#cat-count'));
  const was = await page.$eval('#products li[data-id="oil"] .p-was s', (s) => s.textContent).catch(() => '');
  ok(/۹۸۰/.test(was), 'روغنِ ۹۲۰: قیمتِ پیشینِ ۹۸۰ خط‌خورده، بی پرچمِ بازارچه', was);
  ok(await page.isVisible('#sort'), '«مرتب‌سازی» کنارِ فیلتر');
  await page.selectOption('#sort', 'price', { timeout: 3000 }).catch(() => {});
  await page.waitForFunction(() => (document.querySelector('#products li.p-card') || {}).getAttribute && document.querySelector('#products li.p-card').getAttribute('data-id') === 'water', null, { timeout: 8000 }).catch(() => {});
  const order = await page.$$eval('#products li.p-card', (l) => l.map((x) => x.getAttribute('data-id')));
  ok(order[0] === 'water' && order[order.length - 1] === 'juice', 'ارزان‌ترین اول، گران‌ترین آخر', order.join(','));
  await page.selectOption('#sort', '', { timeout: 3000 }).catch(() => {});
  await page.waitForSelector('#filters:not([hidden])', { timeout: 8000 });
  const sum = await page.$eval('#filters summary', (s) => { const c = getComputedStyle(s); return { r: parseFloat(c.borderRadius), b: c.borderTopStyle }; });
  ok(sum.r >= 16 && sum.b === 'solid', '«فیلتر» شکلِ دکمه دارد', JSON.stringify(sum));
  await page.click('#filters summary');
  await page.waitForFunction(() => (document.getElementById('fl-max') || {}).placeholder, null, { timeout: 8000 }).catch(() => {});
  const ph = await page.$eval('#fl-max', (i) => i.placeholder).catch(() => '');
  ok(/^[۰-۹٬]+$/.test(ph), 'جاگیرِ قیمت با رقمِ فارسی', ph);
  const sel = await page.$eval('#sort', (s) => { const c = getComputedStyle(s); return { r: parseFloat(c.borderRadius), h: s.getBoundingClientRect().height }; }).catch(() => ({}));
  ok(sel.r >= 8 && sel.h >= 44, 'گزینش‌گرها سبک‌دار (گوشهٔ گرد، ۴۴px)', JSON.stringify(sel));
  await page.locator('#filters').scrollIntoViewIfNeeded();
  await shot('06-filters-open-phone');
  await page.click('#filters summary');
}
//  ── ۲) جست‌وجو: نتیجه درست زیرِ کادر، بنرها کنار ──
{
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.fill('#q', 'شیر');
  await page.waitForFunction(() => [...document.querySelectorAll('#products li.p-card')].map((l) => l.getAttribute('data-id')).join() === 'milk', null, { timeout: 8000 }).catch(() => {});
  const q = await rect('#q');
  const hit = await rect('#products li.p-card');
  ok(await page.isHidden('#banners') && await page.isHidden('#picks'), 'با تایپ بنرها و ردیف‌ها کنار می‌روند (WEB-05)');
  //  «درست زیرِ کادر» = میانِ کادر و نخستین نتیجه فقط ابزارهای خودِ فهرست است (دسته‌ها، فیلتر، شمار و مرتب‌سازی، یادداشتِ
  //  موجودی)، نه بنر یا ردیف. فاصلهٔ پیکسلی جایگزینِ این نیست: پیچیدنِ تراشه‌ها به خطِ دوم میانِ کرومیومِ محلی و CI فرق دارد.
  const between = await page.evaluate(() => {
    const qb = document.querySelector('#q').getBoundingClientRect().bottom;
    const ht = document.querySelector('#products li.p-card').getBoundingClientRect().top;
    const ids = new Set();
    for (const el of document.querySelectorAll('#view-catalog *')) {
      const r = el.getBoundingClientRect();
      if (r.height < 1 || r.top < qb - 1 || r.bottom > ht + 1) continue;
      //  ردیفِ «N کالا · مرتب‌سازی» (`.cat-head`) شناسه ندارد؛ با نامِ خودش، نه با `view-catalog` ِ بالادست
      const a = el.closest('[id], .cat-head');
      ids.add(a ? (a.id || 'cat-head') : '?');
    }
    return [...ids];
  });
  const tools = new Set(['cats', 'filters', 'fl-price', 'fl-count', 'stock-note', 'cat-head', 'cat-count', 'sort']);
  ok(q && hit && hit.top < 760 && between.every((id) => tools.has(id)), 'نتیجهٔ جست‌وجو درست زیرِ کادر، در صفحهٔ اول', JSON.stringify({ q, hit, between }));
  await shot('03-search-results-phone');
  await page.fill('#q', '');
  await page.waitForFunction(() => document.querySelectorAll('#products li.p-card').length === 12, null, { timeout: 8000 }).catch(() => {});
  ok(await page.isVisible('#banners'), 'کادرِ خالی ⇒ بنرها برمی‌گردند');
}
//  ── ۱) ♥ در برگهٔ کالا ──
{
  await page.goto(`${base}/store/${slug}#/p/rice`);
  await page.waitForSelector('#ps-fav:not([hidden])', { timeout: 10000 }).catch(() => {});
  const d = await page.evaluate(() => ({ d: document.querySelector('#ps-fav svg path').getAttribute('d'), info: K.ICONS.info }));
  ok(d.d !== d.info, 'برگهٔ کالا: «افزودن به دلخواه‌ها» با ♥، نه ⓘ');
  await shot('07-product-rice-phone');
  await page.click('#ps-close', { timeout: 5000 }).catch(() => {});
}
//  ── ۶) «دریافت از دکان»: نشانی و نقشه در ثبت ──
{
  await page.goto(`${base}/store/${slug}#/checkout`);
  await page.waitForSelector('#checkout-form', { timeout: 10000 }).catch(() => {});
  await page.check('input[name="fulfillment"][value="pickup"]').catch(() => {});
  await page.waitForSelector('#pickup-place:not([hidden]) .place', { timeout: 8000 }).catch(() => {});
  const pl = await text('#pickup-place');
  ok(pl.includes('کارتهٔ سه') && pl.includes('مسیر روی نقشه'), 'ثبت: «دریافت از دکان» نشانی و نقشهٔ دکان را دارد (CHK-11)', pl);
  await shot('10-checkout-pickup-phone');
}
//  ── ۱۰) پیشنهادِ ویرایش: «می‌پذیرم» پررنگ ──
const lum = (c) => { const m = c.match(/[\d.]+/g).map(Number); return (0.299 * m[0] + 0.587 * m[1] + 0.114 * m[2]) * (m[3] === undefined ? 1 : m[3]) + 255 * (1 - (m[3] === undefined ? 1 : m[3])); };
{
  await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(oa.token)}`);
  await page.waitForSelector('#o-ch-yes', { timeout: 15000 }).catch(() => {});
  const st = await page.evaluate(() => ['o-ch-yes', 'o-ch-no'].map((id) => getComputedStyle(document.getElementById(id)).backgroundColor));
  ok(lum(st[0]) < lum(st[1]) - 60, '«می‌پذیرم» دکمهٔ اصلیِ پررنگ، «نمی‌پذیرم» دکمهٔ دوم (ORD-14)', st.join(' | '));
  const pl = await text('#o-place');
  ok(pl.includes('کارتهٔ سه'), 'پیگیریِ «دریافت از دکان»: نشانیِ دکان', pl);
  await page.locator('#o-ch-yes').scrollIntoViewIfNeeded();
  await shot('16-order-amendment-phone');
}
//  ── ۱۰) ردشده به‌خاطرِ «ناموجود» ⇒ کالاهای مشابه ──
{
  await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(ob.token)}`);
  await page.waitForSelector('#o-alt .p-card', { timeout: 15000 }).catch(() => {});
  const alt = await page.$$eval('#o-alt .p-card', (l) => l.map((x) => x.getAttribute('data-id'))).catch(() => []);
  ok(alt.length >= 2 && !alt.includes('rice') && alt.every((id) => ['oil', 'sugar', 'tea', 'flour', 'beans'].includes(id)), 'ردشده برای «ناموجود»: کالاهای مشابهِ همان دسته، بی خودِ برنج (ORD-13)', alt.join(','));
  ok(await page.isHidden('#o-place'), 'سفارشِ بسته: نشانیِ دکان دیگر لازم نیست');
  await page.locator('#o-alt').scrollIntoViewIfNeeded().catch(() => {});
  await shot('18-order-rejected-phone');
  if (alt.length) {
    await page.locator(`#o-alt li[data-id="${alt[0]}"] .p-add`).click({ timeout: 5000 }).catch(() => {});
    await page.waitForFunction(() => location.hash === '#/cart', null, { timeout: 8000 }).catch(() => {});
    ok(page.url().endsWith('#/cart'), '«+» ِ کالای مشابه ⇒ سبد');
  }
}
//  ── ۵) بازارچه: لوگوی دکان روی کارت ──
{
  await page.goto(`${base}/market/#/shops`);
  await page.waitForSelector('.shop-card[data-slug="golestan"]', { timeout: 15000 }).catch(() => {});
  const src = await page.$eval('.shop-card[data-slug="golestan"] .cover img', (i) => i.getAttribute('src')).catch(() => '');
  ok(src === '/api/store/golestan/media/logo/1', 'کارتِ بازارچه: لوگوی خودِ دکان، نه عکسِ یکی از کالاها (WEB-01)', src);
  await shot('33-market-shops-phone');
  await page.goto(`${base}/market/`);
  await page.waitForSelector('.shop-card[data-slug="golestan"]', { timeout: 15000 }).catch(() => {});
  ok(await page.$eval('.shop-card[data-slug="golestan"] .cover img', (i) => /media\/logo/.test(i.src)).catch(() => false), 'خانهٔ بازارچه: همان لوگو');
  await shot('31-market-home-phone');
}

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: ممیزیِ «با چشمِ مشتری» (۱، ۲، ۵، ۶، ۹، ۱۰)');
process.exit(bad ? 1 : 0);
