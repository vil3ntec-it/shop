/**
 * ویترین با کرومیومِ واقعی: سه رابطِ وبِ WEB که تا امروز فقط در سرور یا با منطقِ خالص سنجیده می‌شدند.
 *
 *     npm run test:web-store      (پس از web-store-zones.mjs)
 *
 *   • WEB-14: بی اینترنت ⇒ نوارِ «اینترنت وصل نیست…»؛ سبد روی همین دستگاه می‌ماند و برگشتِ شبکه نوار را برمی‌دارد؛
 *   • WEB-13: «دیده‌شده‌های اخیر» (پرچمِ `shop.storefront.recent`) پس از دیدنِ کالا و بارِ دوباره، فقط روی همین دستگاه؛
 *   • WEB-20: دکمهٔ «فرستادنِ نشانیِ کالا» (پرچمِ `shop.storefront.seo`) نشانیِ `/p/…` را کپی می‌کند؛
 *   • WEB-11: ♥ روی کارت و برگهٔ کالا، «دلخواه‌ها» و «فهرستِ خرید» (پرچمِ `shop.storefront.favorites`؛ خاموش ⇒ هیچ)؛
 *     با نشستِ حساب ♥ و «فهرستِ خرید» با حساب هم‌گام‌اند (دو دستگاه، بی اینترنت در صف، ۴۰۹ ِ نسخه، بی نشست فقط محلی)؛
 *   • WEB-24: بنرهای همین لحظه (پرچمِ `shop.storefront.banners`)، پیوندِ کالا و دسته؛
 *   • WEB-04: «تحویلِ تقریبی: … تا … دقیقه» در برگهٔ ثبت (ارسال و دریافت) و پیگیری (پرچمِ `shop.storefront.eta`؛ خاموش ⇒ هیچ)؛
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { execSync } from 'node:child_process';

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

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };

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

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@web.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ وب' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.recent', 'shop.storefront.seo']) {
  await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
}
const slug = 'web-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ وب', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: ['rice', 'oil'].map((id, i) => ({
  op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name: i ? 'روغن' : 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
})) }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const id of ['rice', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF', permissions: ['clipboard-read', 'clipboard-write'] });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const open = async () => {
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#products li[data-id="rice"]', { timeout: 15000 });
};

//  WEB-14: بی اینترنت
await open();
ok(await page.locator('.offline').isHidden(), 'با اینترنت نوارِ آفلاین پنهان است');
await ctx.setOffline(true);
await page.waitForSelector('.offline:not([hidden])', { timeout: 5000 });
ok(((await page.textContent('.offline')) || '').includes('اینترنت وصل نیست'), 'WEB-14: بی اینترنت ⇒ «اینترنت وصل نیست؛ سبدِ شما روی همین دستگاه می‌ماند»');
await page.locator('#products li[data-id="oil"] .p-add').first().click();
await ctx.setOffline(false);
await page.waitForSelector('.offline', { state: 'hidden', timeout: 5000 });
ok(true, 'برگشتِ شبکه نوار را برمی‌دارد');
await open();
ok(/"?oil"?/.test(await page.evaluate((k) => localStorage.getItem(k) || '', `store-cart:${slug}`)), 'سبدی که بی اینترنت پر شد پس از بارِ دوباره همان است (`store-cart`)');

//  WEB-13: دیده‌شده‌های اخیر
await page.locator('#products li[data-id="rice"] .p-open').first().click();
await page.waitForSelector('#product-sheet:not([hidden])', { timeout: 10000 });
//  WEB-20: فرستادنِ نشانیِ کالا (بی navigator.share ⇒ کپی)
await page.waitForSelector('#ps-share:not([hidden])', { timeout: 10000 });
await page.click('#ps-share');
await page.waitForFunction(() => /کپی شد/.test((document.getElementById('toast') || {}).textContent || ''), null, { timeout: 5000 });
const copied = await page.evaluate(() => navigator.clipboard.readText());
ok(/\/p\/rice$/.test(copied), 'WEB-20: «فرستادن» نشانیِ `/p/<کالا>` را کپی کرد', copied);
await page.click('#ps-close');
await open();
await page.waitForSelector('#recent:not([hidden]) li[data-id="rice"]', { timeout: 10000 });
ok(true, 'WEB-13: «دیده‌شده‌های اخیر» پس از بارِ دوباره برنج را دارد');
const keys = await page.evaluate(() => Object.keys(localStorage));
ok(keys.some((k) => /recent/i.test(k)), 'فهرستِ اخیر فقط روی همین دستگاه (localStorage)', keys.join(','));

//  WEB-11: پرچمِ خاموش ⇒ بی ♥ و بی فهرست؛ روشن ⇒ ♥ روی کارت و برگه، «دلخواه‌ها» و «فهرستِ خرید» (catalog.js، فقط روی همین دستگاه)
ok(await page.locator('.fav').count() === 0 && await page.isHidden('#favs') && await page.isHidden('#list'), 'WEB-11 خاموش: بی ♥ و بی فهرستِ خرید');
await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key: 'shop.storefront.favorites', value: true }, A);
await open();
const heart = page.locator('#products li[data-id="rice"] .fav');
await heart.waitFor({ timeout: 10000 });
ok(await heart.getAttribute('aria-pressed') === 'false' && /برنج/.test(await heart.getAttribute('aria-label')), 'WEB-11: ♥ روی کارت با نامِ کالا');
await heart.click();
await page.waitForSelector('#favs:not([hidden]) #favs-list li[data-id="rice"]', { timeout: 5000 });
ok(await heart.getAttribute('aria-pressed') === 'true' && await page.locator('#favs-list li[data-id="rice"] .fav[aria-pressed="true"]').count() === 1,
  '♥ ⇒ «دلخواه‌ها» برنج را دارد و ♥ ِ هر دو کارت روشن است');
await open();
await page.waitForSelector('#favs:not([hidden]) #favs-list li[data-id="rice"]', { timeout: 10000 });
ok(true, 'دلخواه‌ها پس از بارِ دوباره همان‌اند');
await page.locator('#products li[data-id="oil"] .p-open').first().click();
await page.waitForSelector('#ps-fav:not([hidden])', { timeout: 10000 });
await page.click('#ps-fav');
await page.waitForSelector('#favs-list li[data-id="oil"]', { state: 'attached', timeout: 5000 });
ok(await page.getAttribute('#ps-fav', 'aria-pressed') === 'true' && await page.locator('#products li[data-id="oil"] .fav[aria-pressed="true"]').count() === 1,
  'برگهٔ کالا: ♥ همان فهرست و همان کارت');
await page.click('#ps-close');
await page.waitForSelector('#list:not([hidden])', { timeout: 5000 });
ok(await page.isHidden('#list-add') && !(await page.isDisabled('#list-save')), 'فهرستِ خالی با سبدِ پر: فقط «نگه‌داشتنِ سبد»');
await page.click('#list-save');
await page.waitForSelector('#list-lines li', { timeout: 5000 });
ok((await page.textContent('#list-lines')).includes('روغن') && await page.isVisible('#list-add'), '«فهرستِ خرید» = اقلامِ سبد');
await page.evaluate((k) => localStorage.removeItem(k), `store-cart:${slug}`);
await open();
await page.waitForSelector('#list:not([hidden]) #list-lines li', { timeout: 10000 });
ok((await page.textContent('#list-lines')).includes('روغن'), 'فهرست با سبدِ خالی و پس از بارِ دوباره می‌ماند');
const listed = await page.evaluate((k) => localStorage.getItem(k) || '', `store-list:${slug}`);
ok(!/price|name/i.test(listed), 'فهرست فقط شناسه و مقدار را نگه می‌دارد، نه قیمت', listed);
await page.click('#list-add');
await page.waitForFunction(() => location.hash === '#/cart', null, { timeout: 5000 });
ok(/"oil"/.test(await page.evaluate((k) => localStorage.getItem(k) || '', `store-cart:${slug}`)), '«همه به سبد» ⇒ همان اقلام با قیمتِ امروز در سبد');

//  WEB-11: ♥ با حسابِ مشتری (نشستِ `store-session`): ادغامِ نخستین بار، دستگاهِ دوم همان را می‌بیند، حذف در یکی ⇒ دیگری،
//  بی اینترنت در صف تا برگشتِ شبکه، و بی نشست فقط محلی
{
  const phone = '0791114888';
  const sessionOf = async () => {
    const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
    const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
    const a = await call('POST', `/api/store/${slug}/account`, {}, null, { 'X-Store-Ticket': vc.body.ticket });
    return { token: a.body.session, expiresAt: a.body.expiresAt, phone: a.body.customer.phone, name: '' };
  };
  const S1 = await sessionOf();
  const favsOf = async (S) => ((await call('GET', `/api/store/${slug}/account/favorites`, null, null, { 'X-Store-Session': S.token })).body.items || []).map((p) => p.id);
  const until = async (S, want, what) => {
    let got = [];
    for (let i = 0; i < 50; i++) { got = await favsOf(S); if (JSON.stringify(got) === JSON.stringify(want)) break; await new Promise((r) => setTimeout(r, 100)); }
    ok(JSON.stringify(got) === JSON.stringify(want), what, JSON.stringify(got));
  };
  ok(JSON.stringify(await favsOf(S1)) === '[]', 'حسابِ تازه: هیچ ♥ ِ سروری');
  await page.evaluate((v) => localStorage.setItem('store-session', JSON.stringify(v)), S1);
  await open();
  await until(S1, ['oil', 'rice'], 'WEB-11 هم‌گامی: نخستین بارِ نشست ⇒ ♥ ِ این دستگاه به حساب رفت، با همان ترتیب (تازه‌ترین اول)');

  const ctx2 = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  const p2 = await ctx2.newPage();
  p2.on('pageerror', (e) => errors.push(String(e)));
  await p2.goto(`${base}/store/${slug}`);
  await p2.evaluate((v) => localStorage.setItem('store-session', JSON.stringify(v)), await sessionOf());
  await p2.goto(`${base}/store/${slug}`);
  await p2.waitForSelector('#favs:not([hidden]) #favs-list li[data-id="rice"]', { timeout: 10000 });
  ok(await p2.locator('#favs-list li').count() === 2 && await p2.getAttribute('#products li[data-id="rice"] .fav', 'aria-pressed') === 'true',
    'دستگاهِ دوم (همان شماره، دستگاهِ خالی): همان دو ♥ از حساب');
  await p2.click('#products li[data-id="rice"] .fav');
  await until(S1, ['oil'], 'برداشتنِ ♥ در دستگاهِ دوم ⇒ از حساب هم رفت');
  await ctx2.close();

  await open();
  await page.waitForFunction(() => document.querySelector('#products li[data-id="rice"] .fav')?.getAttribute('aria-pressed') === 'false', null, { timeout: 10000 });
  ok(await page.locator('#favs-list li').count() === 1, 'دستگاهِ نخست پس از بارِ دوباره: حساب حرفِ آخر را می‌زند (فقط روغن)');

  await ctx.setOffline(true);
  await page.click('#products li[data-id="rice"] .fav');
  const queued = await page.evaluate((k) => JSON.parse(localStorage.getItem(k) || '{}').ops, `store-favsync:${slug}`);
  ok(JSON.stringify(queued) === '[["rice",1]]', 'بی اینترنت ⇒ ♥ در صفِ همین دستگاه', JSON.stringify(queued));
  ok(JSON.stringify(await favsOf(S1)) === '["oil"]', '…و هنوز به حساب نرفته');
  await ctx.setOffline(false);
  await until(S1, ['rice', 'oil'], 'برگشتِ شبکه ⇒ صف رفت');

  await page.evaluate(() => localStorage.removeItem('store-session'));
  await open();
  await page.click('#products li[data-id="oil"] .fav');
  await page.waitForFunction(() => document.querySelector('#products li[data-id="oil"] .fav')?.getAttribute('aria-pressed') === 'false', null, { timeout: 5000 });
  await new Promise((r) => setTimeout(r, 500));
  ok(JSON.stringify(await favsOf(S1)) === '["rice","oil"]', 'بی نشست ⇒ ♥ فقط روی همین دستگاه؛ حساب دست نمی‌خورد');
}

//  WEB-11: «فهرستِ خرید» با حساب (`/account/shopping-list`، همان پرچم): ادغامِ نخستین بار (بیشینه، نه جای‌گزینی)، دستگاهِ دوم،
//  برداشتن در یکی ⇒ دیگری، بی اینترنت در صف، ۴۰۹ ِ نسخه ⇒ همان کار روی فهرستِ تازه (کارِ دستگاهِ دیگر می‌ماند)، بی نشست فقط محلی
{
  const phone = '0791114999';
  const listUrl = `/api/store/${slug}/account/shopping-list`;
  const sessionOf = async () => {
    const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
    const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
    const a = await call('POST', `/api/store/${slug}/account`, {}, null, { 'X-Store-Ticket': vc.body.ticket });
    return { token: a.body.session, expiresAt: a.body.expiresAt, phone: a.body.customer.phone, name: '' };
  };
  const L1 = await sessionOf();
  const listOf = async (S) => (await call('GET', listUrl, null, null, { 'X-Store-Session': S.token })).body.list;
  const linesOf = async (S) => (await listOf(S)).lines.map((l) => [l.productId, l.qtyMilli]);
  const until = async (S, want, what) => {
    let got = [];
    for (let i = 0; i < 50; i++) { got = await linesOf(S); if (JSON.stringify(got) === JSON.stringify(want)) break; await new Promise((r) => setTimeout(r, 100)); }
    ok(JSON.stringify(got) === JSON.stringify(want), what, JSON.stringify(got));
  };
  const seed = (pg, S, items) => pg.evaluate(([sl, v, it]) => {
    localStorage.setItem('store-session', JSON.stringify(v));
    localStorage.setItem(`store-list:${sl}`, JSON.stringify(it));
    localStorage.removeItem(`store-listsync:${sl}`);
  }, [slug, S, items]);
  ok(JSON.stringify(await linesOf(L1)) === '[]', 'حسابِ تازه: فهرستِ سروری خالی');
  await seed(page, L1, [{ id: 'oil', q: 2000 }]);
  await open();
  await until(L1, [['oil', 2000]], 'WEB-11 فهرست: نخستین بارِ نشست ⇒ فهرستِ این دستگاه به حساب رفت (ادغام)');

  const ctx3 = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  const p3 = await ctx3.newPage();
  p3.on('pageerror', (e) => errors.push(String(e)));
  await p3.goto(`${base}/store/${slug}`);
  await seed(p3, await sessionOf(), [{ id: 'rice', q: 1000 }, { id: 'oil', q: 500 }]);
  await p3.goto(`${base}/store/${slug}`);
  await until(L1, [['oil', 2000], ['rice', 1000]], 'دستگاهِ دوم: ادغام، نه جای‌گزینی (برای کالای مشترک بیشینهٔ مقدار)');
  await p3.waitForFunction(() => document.querySelectorAll('#list-lines li').length === 2, null, { timeout: 10000 });
  ok(true, 'دستگاهِ دوم پس از ادغام همان دو قلمِ حساب را نشان می‌دهد');
  await p3.click('#list-lines li:has-text("روغن") button');
  await until(L1, [['rice', 1000]], 'برداشتنِ قلم در دستگاهِ دوم ⇒ از حساب هم رفت');
  await ctx3.close();

  await open();
  await page.waitForFunction(() => document.querySelectorAll('#list-lines li').length === 1 && /برنج/.test(document.querySelector('#list-lines').textContent), null, { timeout: 10000 });
  ok(true, 'دستگاهِ نخست پس از بارِ دوباره: حساب حرفِ آخر را می‌زند (فقط برنج)');

  await ctx.setOffline(true);
  await page.click('#list-clear');
  const lq = await page.evaluate((k) => JSON.parse(localStorage.getItem(k) || '{}').ops, `store-listsync:${slug}`);
  ok(JSON.stringify(lq) === '[["all",[]]]', 'بی اینترنت ⇒ «پاک کردنِ فهرست» در صفِ همین دستگاه', JSON.stringify(lq));
  ok(JSON.stringify(await linesOf(L1)) === '[["rice",1000]]', '…و هنوز به حساب نرفته');
  await ctx.setOffline(false);
  await until(L1, [], 'برگشتِ شبکه ⇒ صف رفت');

  //  دستگاهِ دیگر (این‌جا خودِ آزمون) دو بار می‌نویسد؛ دستگاهِ نخست فقط بارِ اول را دیده ⇒ برداشتنِ برنج با نسخهٔ کهنه ⇒ ۴۰۹ ⇒
  //  همان «برداشتن» روی فهرستِ تازه: مقدارِ تازهٔ روغن ِ آن دستگاه می‌ماند
  let v = (await listOf(L1)).version;
  v = (await call('PUT', listUrl, { version: v, items: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'oil', qtyMilli: 3000 }] }, null, { 'X-Store-Session': L1.token })).body.list.version;
  await open();
  await page.waitForFunction(() => document.querySelectorAll('#list-lines li').length === 2, null, { timeout: 10000 });
  const w2 = await call('PUT', listUrl, { version: v, items: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'oil', qtyMilli: 4000 }] }, null, { 'X-Store-Session': L1.token });
  ok(w2.status === 200, 'دستگاهِ دیگر مقدارِ روغن را عوض کرد', JSON.stringify(w2.body).slice(0, 80));
  await page.click('#list-lines li:has-text("برنج") button');
  await until(L1, [['oil', 4000]], '۴۰۹ ِ نسخه ⇒ برداشتن روی فهرستِ تازه؛ کارِ دستگاهِ دیگر گم نشد');
  const stored = await page.evaluate((k) => localStorage.getItem(k) || '', `store-list:${slug}`);
  ok(!/price|name/i.test(stored) && /4000/.test(stored), 'فهرستِ دستگاه همان فهرستِ حساب، فقط شناسه و مقدار', stored);

  await page.evaluate(() => localStorage.removeItem('store-session'));
  await open();
  await page.waitForSelector('#list:not([hidden]) #list-lines li', { timeout: 10000 });
  await page.click('#list-clear');
  await new Promise((r) => setTimeout(r, 500));
  ok(JSON.stringify(await linesOf(L1)) === '[["oil",4000]]', 'بی نشست ⇒ فهرست فقط روی همین دستگاه؛ حساب دست نمی‌خورد');
}

//  WEB-24: بنرها — خاموش ⇒ هیچ؛ روشن ⇒ فقط بنرِ اکنون و همه‌جا (بی ناحیهٔ دانسته)، پیوندِ کالا و دسته
ok(await page.isHidden('#banners'), 'WEB-24 خاموش: بی بنر');
await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key: 'shop.storefront.banners', value: true }, A);
{
  const now = Date.now(); const H = 3600 * 1000;
  const bn = await call('PUT', '/api/shop/storefront/banners', { banners: [
    { title: 'روغنِ تازه رسید', body: 'تا پایانِ هفته', startAt: now - H, endAt: now + H, sort: 9, link: { type: 'product', value: 'oil' } },
    { title: 'همهٔ خوراکی‌ها', startAt: now - H, endAt: now + H, sort: 5, link: { type: 'category', value: 'خوراکی' } },
    { title: 'دستهٔ نبوده', startAt: now - H, endAt: now + H, sort: 1, link: { type: 'category', value: 'نیست' } },
    { title: 'فقط یک ناحیه', startAt: now - H, endAt: now + H, zoneIds: ['01HZZZZZZZZZZZZZZZZZZZZZZ1'] },
    { title: 'گذشته', startAt: now - 3 * H, endAt: now - H },
  ] }, T);
  ok(bn.status === 200, 'بنرها نوشته شدند', JSON.stringify(bn.body).slice(0, 120));
}
await open();
await page.waitForSelector('#banners:not([hidden]) .banner', { timeout: 10000 });
const titles = await page.locator('#banners-list .banner b').allTextContents();
ok(JSON.stringify(titles) === JSON.stringify(['روغنِ تازه رسید', 'همهٔ خوراکی‌ها', 'دستهٔ نبوده']), 'فقط بنرِ اکنون و همه‌جا، به ترتیبِ اولویت', JSON.stringify(titles));
ok(await page.locator('#banners-list .banner').nth(2).locator('.btn').count() === 0 && (await page.getAttribute('#banners', 'aria-label')) === 'اعلان‌های دکان',
  'دستهٔ نبوده ⇒ بی دکمه؛ بخش نامِ خودش را دارد');
await page.locator('#banners-list .banner').first().getByRole('link').click();
await page.waitForSelector('#product-sheet:not([hidden])', { timeout: 10000 });
ok((await page.textContent('#ps-name')) === 'روغن', 'پیوندِ کالا ⇒ برگهٔ همان کالا');
await page.click('#ps-close');
await page.locator('#banners-list .banner').nth(1).getByRole('button').click();
await page.waitForSelector('#cats [aria-pressed="true"]:has-text("خوراکی")', { timeout: 5000 });
ok(true, 'پیوندِ دسته ⇒ همان دسته در کاتالوگ');

//  WEB-04: زمانِ تقریبیِ تحویل — خاموش ⇒ برگهٔ ثبت بی آن؛ روشن ⇒ بازهٔ همان سرور (بی سابقه: ۱۵ + ۲۰ دقیقه) و پیگیری
const etaText = () => { const e = document.getElementById('ck-eta'); return e && !e.hidden ? e.textContent : ''; };
await page.goto(`${base}/store/${slug}#/checkout`);
await page.waitForSelector('#view-checkout:not([hidden]) #cart-lines li', { timeout: 10000 });
await page.waitForFunction(() => Boolean((document.getElementById('t-total') || {}).textContent), null, { timeout: 5000 });
ok(await page.isHidden('#ck-eta'), 'WEB-04 خاموش: برگهٔ ثبت بی «تحویلِ تقریبی»');
await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key: 'shop.storefront.eta', value: true }, A);
await page.goto(`${base}/store/${slug}`);
await page.waitForSelector('#products li[data-id="rice"]', { timeout: 15000 });
await page.evaluate(() => { location.hash = '#/checkout'; });
await page.waitForSelector('#view-checkout:not([hidden]) #cart-lines li', { timeout: 10000 });
await page.check('input[name="fulfillment"][value="delivery"]');
await page.waitForFunction(() => { const e = document.getElementById('ck-eta'); return Boolean(e && !e.hidden && /تحویلِ تقریبی: (۳۰|30) تا (۴۵|45) دقیقه/.test(e.textContent)); },
  null, { timeout: 10000 });
ok(true, 'WEB-04: ارسال ⇒ «تحویلِ تقریبی: ۳۰ تا ۴۵ دقیقه»', await page.evaluate(etaText));
await page.check('input[name="fulfillment"][value="pickup"]');
await page.waitForFunction(() => { const e = document.getElementById('ck-eta'); return Boolean(e && !e.hidden && /آماده برای دریافت، تقریبی: (۱۰|10) تا (۲۰|20) دقیقه/.test(e.textContent)); },
  null, { timeout: 5000 });
ok(true, 'WEB-04: دریافت از دکان ⇒ «آماده برای دریافت، تقریبی: ۱۰ تا ۲۰ دقیقه»');
{
  const phone = '0791113777';
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
  const placed = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone },
    lines: [{ productId: 'rice', qtyMilli: 1000 }] }, null, { 'Idempotency-Key': `eta-${Date.now()}-key`, 'X-Store-Ticket': vc.body.ticket });
  ok(placed.status === 201, 'سفارشِ آزمایشیِ پیگیری ثبت شد', JSON.stringify(placed.body).slice(0, 120));
  await page.evaluate((tk) => { location.hash = '#/o/' + encodeURIComponent(tk); }, placed.body.order.token);
  await page.waitForFunction(() => { const e = document.getElementById('o-eta-live'); return Boolean(e && !e.hidden && /تقریبی: .+ تا .+ دقیقهٔ دیگر/.test(e.textContent)); },
    null, { timeout: 10000 });
  ok(true, 'WEB-04: پیگیری ⇒ «آماده برای دریافت، تقریبی: … تا … دقیقهٔ دیگر»', await page.textContent('#o-eta-live'));
}

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: آفلاین، دیده‌شده‌های اخیر، فرستادنِ نشانی، دلخواه‌ها، فهرستِ خرید، بنرها و زمانِ تقریبیِ تحویل');
process.exit(bad ? 1 : 0);
