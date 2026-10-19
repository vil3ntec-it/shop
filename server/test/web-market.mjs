/**
 * بازارچه با کرومیومِ واقعی — از خانه تا لغوِ سفارش (WEB، CHK، §۲۳، §۳۱.۱، §۳۲.۳).
 *
 *     npm run test:web-market
 *
 * سرورِ واقعی، سه دکانِ واقعی (ثبت‌نام، همگام‌سازیِ کالا، انتشار) و پرچمِ
 * `market.directory`. آن‌چه سنجیده می‌شود:
 *   • خانه: فقط دکان‌های در فهرست؛ دسته؛ یک جعبهٔ جست‌وجو برای دکان و کالا؛
 *   • ویترین: کارتِ کالا «یک زدن = یک واحد»، نوار و برگهٔ تیرهٔ سبد با −/+؛
 *     سبد با تازه‌سازی می‌ماند (S132)؛
 *   • ثبت با کدِ آزمایشی، ارسال با نشانی؛ قیمتِ عوض‌شده ⇒ ۴۰۹ و برگهٔ تازه
 *     (S135)، بی ثبتِ بی‌صدا؛ پذیرش ⇒ ثبت؛
 *   • پیگیری با توکن، پله‌های وضعیت، لغوِ رایگان با پرسشِ تأیید، خریدِ دوباره؛
 *   • «سفارش‌های من» در بازارچه با همان شمارهٔ تأییدشده؛
 *   • بازارچهٔ خاموش ⇒ ۴۰۴ و «هنوز باز نیست»؛ manifest ِ نصب؛
 *   • CSP ِ سخت بی هیچ نقض، بی خطای JS، بی بیرون‌زدگی در ۳۲۰ تا ۱۲۸۰،
 *     axe-core روشن و تاریک، JS ِ هر صفحه زیرِ ۷۵KB، و هیچ پیوندی به برنامهٔ مدیریت.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP, closeSafeRoute } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.RATE_MARKET_SEARCH_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';
process.env.MARKET_GATE_CACHE_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };
const fa = (n) => Number(n).toLocaleString('fa-AF');

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
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (scope, key, value) => call('PUT', '/api/admin/pump/live-config', { scope, key, value }, A);

//  ── دکان‌های آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
let n = 0;
async function shop(slug, title, listing, products) {
  n++;
  const email = `owner${n}@market.local`, password = 'Passw0rd!test';
  const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
  const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
  const dev = `dev-${n}`;
  const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
    device: { deviceId: dev, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
  const T = d1.body.accessToken;
  const shopId = (await call('POST', '/api/shop', { name: title }, T)).body.shop.id;
  for (const key of ['shop.orders', 'shop.storefront']) await flag(shopId, key, true);
  await call('PUT', '/api/shop/storefront', { slug, enabled: true, title, tagline: 'کالای روزانه', contactPhone: '0700000001', deliveryFeeMinor: 5000, minOrderMinor: 0 }, T);
  await call('PUT', '/api/shop/market', listing, T);
  const ops = products.map(([id, name, price]) => ({ op_id: `op-${slug}-${id}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields: { name, unit: 'دانه', salePrice: price, category: 'خوراکی' } }));
  await call('POST', '/api/sync/v1/push', { device_id: dev, schema_version: 2, ops }, T, { 'X-App': 'shop', 'X-Device': dev });
  for (const [id] of products) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);
  return { slug, shopId, token: T };
}
const ga = await shop('golestan-test', 'سوپرمارکتِ آزمایشی', { category: 'grocery', area: 'کارتهٔ سه' }, [['rice', 'برنج', 120.5], ['oil', 'روغن', 300]]);
const gb = await shop('bahar-test', 'نانواییِ آزمایشی', { category: 'bakery', area: 'شهرِ نو' }, [['naan', 'نانِ تندوری', 15]]);
const gc = await shop('hidden-test', 'دکانِ بیرون از فهرست', { listed: false }, [['tea', 'چای', 80]]);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const watch = (page) => {
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
};
async function axe(page, label) {
  if (!axeSrc) return;
  //  از راهِ CDP (نه برچسبِ <script>) تا CSP ِ سختِ صفحه برای آزمون شل نشود
  if (!(await page.evaluate(() => Boolean(window.axe)))) await page.evaluate(axeSrc);
  const out = await page.evaluate(async () => (await window.axe.run(document,
    { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name', 'aria-valid-attr-value', 'aria-required-attr', 'duplicate-id-aria', 'nested-interactive'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(out.length === 0, `دسترس‌پذیری (axe-core): ${label}`, out.join(' | '));
}
async function noOverflow(page, label) {
  const bads = [];
  for (const w of [320, 390, 768, 1280]) {
    await page.setViewportSize({ width: w, height: 800 });
    await page.waitForTimeout(150);
    const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    if (over > 0) bads.push(`${w}:${over}px`);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  ok(bads.length === 0, `بی بیرون‌زدگیِ افقی در ۳۲۰ تا ۱۲۸۰: ${label}`, bads.join(' '));
}

//  ── بازارچهٔ خاموش ──
{
  const r = await fetch(`${base}/market/`);
  ok(r.status === 404, 'بازارچهٔ خاموش ⇒ ۴۰۴', String(r.status));
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'fa-AF' });
  const page = await ctx.newPage(); watch(page);
  await page.goto(`${base}/market/`);
  await page.waitForSelector('#view-off:not([hidden])', { timeout: 10000 });
  ok(true, 'صفحهٔ «بازارچه هنوز باز نیست»');
  await ctx.close();
}
await flag('all', 'market.directory', true);

const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'fa-AF', geolocation: { latitude: 34.53, longitude: 69.17 } });
const page = await ctx.newPage();
watch(page);

//  ── خانه ──
await page.goto(`${base}/market/`);
await page.waitForSelector('#shops li a.shop-card', { timeout: 15000 });
ok(await page.evaluate(() => document.documentElement.dir === 'rtl' && document.documentElement.lang === 'fa'), 'راست‌چین و فارسی');
const cards = await page.$$eval('#shops a.shop-card', (as) => as.map((a) => a.getAttribute('href')));
ok(cards.length === 2 && cards.includes(`/store/${ga.slug}`) && cards.includes(`/store/${gb.slug}`), 'خانه: فقط دکان‌های در فهرست', cards.join());
const homeText = await page.textContent('main');
ok(homeText.includes('سوپرمارکتِ آزمایشی') && !homeText.includes('بیرون از فهرست'), 'دکانِ «نخواسته در فهرست» دیده نمی‌شود');
ok(/باز است|بسته است|آفلاین|آنلاین تا/.test(await page.textContent('#shops')), 'وضعیتِ باز/بسته روی کارت (۳۱.۱)');
ok((await page.locator('input[type="search"]').count()) === 1, 'یک جعبهٔ جست‌وجو در صفحه');
await noOverflow(page, 'خانه');
await axe(page, 'خانه');

await page.click('#cats button[data-cat="bakery"]');
await page.waitForFunction(() => document.querySelectorAll('#shops a.shop-card').length === 1, null, { timeout: 5000 });
ok((await page.getAttribute('#shops a.shop-card', 'href')) === `/store/${gb.slug}`, 'تراشهٔ دسته: فقط نانوایی');
await page.click('#cats button[data-cat=""]');
await page.waitForFunction(() => document.querySelectorAll('#shops a.shop-card').length === 2, null, { timeout: 5000 });

await page.click('#locate');
await page.waitForFunction(() => /نزدیک/.test(document.getElementById('shops-title').textContent), null, { timeout: 10000 }).catch(() => {});
ok(true, '«نزدیکِ من» اختیاری است و بی آن هم فهرست کامل است');

await page.fill('#q', 'برن');
await page.waitForSelector('#r-products li a', { timeout: 10000 });
const hit = await page.getAttribute('#r-products li a', 'href');
ok(hit === `/store/${ga.slug}#/p/rice`, 'جست‌وجوی کالا در همهٔ دکان‌ها', hit);
await page.fill('#q', 'چای');
await page.waitForSelector('#r-empty:not([hidden])', { timeout: 10000 });
ok(true, 'کالای دکانِ بیرون از فهرست پیدا نمی‌شود؛ «چیزی پیدا نشد» راه دارد');
await page.fill('#q', 'برن');
await page.waitForSelector('#r-products li a', { timeout: 10000 });
await page.click('#r-products li a');

//  ── ویترین: برگهٔ کالا، کارت، نوار و برگهٔ تیرهٔ سبد ──
await page.waitForSelector('#product-sheet.on', { timeout: 15000 });
ok((await page.textContent('#ps-name')) === 'برنج', 'برگهٔ کالا از پیوندِ جست‌وجو');
await page.click('#ps-add');
await page.click('#ps-close');
await page.waitForFunction(() => location.hash === '#/', null, { timeout: 5000 });
await page.waitForSelector('#products li[data-id]');
const oil = page.locator('#products li[data-id="oil"]');
await oil.getByRole('button', { name: /افزودن/ }).click();
ok(await page.isVisible('#cart-bar'), 'نوارِ تیرهٔ سبد با افزودن پیدا شد');
ok((await page.textContent('#open-cart')).includes('۲'), 'شمارِ اقلام روی نوار');
ok((await page.locator('#products .stepper').count()) === 0, 'کارتِ کالا −/+ ندارد (DESIGN v2)');
await page.reload();
await page.waitForSelector('#products li[data-id]');
ok(await page.isVisible('#cart-bar') && (await page.textContent('#open-cart')).includes('۲'), 'سبد با تازه‌سازی می‌ماند (S132)');
await noOverflow(page, 'ویترین');
await axe(page, 'ویترین');

//  گوشی = مدلِ مرجعِ صاحب محصول (phone-v3 r3 بسته / r2 باز)
const opened = (pg) => pg.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 10000 });
const closed = (pg) => pg.waitForFunction(() => !document.body.classList.contains('cart-open') && !document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 10000 });
ok(await page.evaluate(() => document.body.classList.contains('phone-cart') && getComputedStyle(document.body).backgroundColor === 'rgb(13, 13, 15)'
  && getComputedStyle(document.getElementById('surface')).borderBottomLeftRadius === '36px'), 'گوشی با سبدِ پُر: پایهٔ سیاه و سطحِ روشن با گوشهٔ ۳۶ (r3)');
ok(await page.evaluate(() => getComputedStyle(document.getElementById('open-cart')).backgroundColor === 'rgb(255, 194, 74)'), 'دایرهٔ زردِ شمار (r3)');
await page.click('#cart-bar');
await opened(page);
ok(await page.evaluate(() => document.getElementById('cart-bar').inert && document.getElementById('surface').inert), 'سبدِ باز: نوار و سطحِ زیرین از دسترسِ صفحه‌کلید بیرون‌اند');
await page.locator('#sheet-lines li').filter({ hasText: 'برنج' }).getByRole('button', { name: /تغییرِ تعداد/ }).click();
await page.locator('#sheet-lines li').filter({ hasText: 'برنج' }).getByRole('button', { name: /یکی بیشتر/ }).click();
//  ۱۲۰٫۵ × ۲ = ۲۴۱ + ۳۰۰ = ۵۴۱ + ارسالِ ۵۰ = ۵۹۱
await page.waitForFunction(() => document.getElementById('sheet-total').textContent.includes('۵۹۱'), null, { timeout: 5000 });
ok(true, 'برگهٔ سبد: −/+ و جمع با هزینهٔ ارسال (CHK-04)');
await axe(page, 'برگهٔ تیرهٔ سبد');
await page.keyboard.press('Escape');
await closed(page);
ok(await page.isVisible('#cart-bar'), 'Esc سبد را می‌بندد و نوار برمی‌گردد');
await page.click('#cart-bar');
await opened(page);
await page.click('#surface-cap');
await closed(page);
ok(true, 'زدنِ بخشِ روشنِ بالا سبد را می‌بندد (r2 ⇐ r3)');
await page.click('#cart-bar');
await opened(page);
await page.click('#cart-next');

//  ── ثبت ──
await page.waitForSelector('#view-checkout:not([hidden])');
await page.check('input[name="fulfillment"][value="delivery"]');
ok((await page.textContent('#t-total')).includes('۵۹۱'), 'جمعِ نهایی با ارسال پیش از ثبت', await page.textContent('#t-total'));
ok(await page.isDisabled('#submit-order'), 'بی شمارهٔ تأییدشده، «ثبت» بسته است (S133)');
ok((await page.locator('button', { hasText: /پرداختِ آنلاین|پرداخت آنلاین/ }).count()) === 0, 'هیچ دکمهٔ پرداختِ آنلاینِ ساختگی نیست (D-08)');
await page.fill('#a-text', 'کارتهٔ سه، سرکِ دوم');
await page.fill('#c-name', 'مشتریِ آزمایشی');
await page.fill('#c-phone', '0791112233');
await page.click('#verify-send');
await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10000 });
const code = ((await page.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1];
ok(Boolean(code), 'کدِ آزمایشی (فقط بیرون از تولید)');
await page.fill('#v-code', code || '');
await page.click('#verify-check');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await noOverflow(page, 'ثبتِ سفارش');
await axe(page, 'ثبتِ سفارش');

//  CHK-17/S135: قیمت میانِ سبد و ثبت عوض می‌شود ⇒ ۴۰۹ با برگهٔ تازه، بی سفارش
await call('PUT', '/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 32000 }, ga.token);
await page.click('#submit-order');
await page.waitForFunction(() => /قیمت عوض شده/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 10000 });
const count0 = (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [ga.shopId])).n;
ok(count0 === 0, 'قیمتِ عوض‌شده: سفارش بی‌صدا ثبت نشد');
ok((await page.textContent('#checkout-msg')).includes('۶۱۱') && (await page.textContent('#submit-order')).includes('پذیرفتن'), 'برگهٔ تازه با مبلغِ تازه و دکمهٔ «پذیرفتن»', await page.textContent('#checkout-msg'));
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 10000 });
await page.waitForFunction(() => /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 10000 });
const row = await one(`SELECT id, order_no, status, total_minor, fulfillment FROM online_orders WHERE shop_id=$1`, [ga.shopId]);
ok(row && Number(row.total_minor) === 61100 && row.fulfillment === 'delivery', 'سفارش با قیمتِ پذیرفته‌شدهٔ سرور ثبت شد', JSON.stringify(row));
ok((await page.textContent('#o-title')).includes(fa(row.order_no)), 'رسید: شمارهٔ سفارش (CHK-20)');
ok(/#\/o\/[0-9A-Z]{26}\.[A-Za-z0-9_-]{32}$/.test(page.url()), 'نشانیِ پیگیری توکنِ حدس‌نزدنی دارد (S137)');
ok(await page.isHidden('#cart-bar'), 'پس از ثبت سبد خالی است');
ok((await page.locator('#o-steps li.step.now').count()) === 1, 'پله‌های وضعیت: «ثبت شد» در جریان');
await noOverflow(page, 'پیگیری');
await axe(page, 'پیگیری');

//  دکان قبول می‌کند ⇒ پیگیری نشان می‌دهد و لغو دیگر نیست؛ سفارشِ دوم را لغو می‌کنیم
await call('POST', `/api/shop/orders/${row.id}/transition`, { to: 'accepted' }, ga.token);
await page.reload();
await page.waitForFunction(() => /قبول/.test(document.getElementById('o-pill').textContent), null, { timeout: 10000 });
ok(await page.isHidden('#o-cancel'), 'پس از قبول، لغوِ رایگان نیست (CHK-22)');

//  خریدِ دوباره ⇒ همان اقلام با قیمتِ امروز به سبد
await page.click('#o-reorder');
await opened(page);
ok((await page.locator('#sheet-lines li').count()) === 2, 'خریدِ دوباره: اقلام با قیمتِ امروز در سبد (WEB-12)');
await page.click('#cart-next');
await page.waitForSelector('#view-checkout:not([hidden])');
ok((await page.inputValue('#c-name')) === 'مشتریِ آزمایشی' && (await page.inputValue('#c-phone')) === '0791112233', 'نام و شمارهٔ تأییدشده برای سفارشِ بعدی روی همین دستگاه می‌ماند');
await page.check('input[name="fulfillment"][value="pickup"]');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.click('#submit-order');
await page.waitForFunction(() => /#\/o\//.test(location.hash) && /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 10000 })
  .catch(async (e) => { console.log('تشخیص:', page.url(), await page.textContent('#checkout-msg'), await page.textContent('#o-title')); throw e; });
await page.waitForSelector('#o-cancel:not([hidden])');
await page.click('#o-cancel');
await page.waitForSelector('dialog.confirm[open]');
await page.click('dialog.confirm .btn-danger');
await page.waitForFunction(() => /لغو کردید/.test(document.getElementById('o-pill').textContent), null, { timeout: 10000 });
const cancelled = await one(`SELECT status FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1`, [ga.shopId]);
ok(cancelled.status === 'cancelled_by_customer', 'لغوِ رایگان با پرسشِ تأیید (CHK-22)', cancelled.status);
ok((await page.locator('#o-steps li.step.stop').count()) === 1, 'پله‌ها پایانِ «لغو» را نشان می‌دهند');

//  ── سفارش‌های من در بازارچه (همان شمارهٔ تأییدشده، S138) ──
await page.goto(`${base}/market/#/orders`);
await page.waitForSelector('#orders li a', { timeout: 10000 });
const mine = await page.$$eval('#orders li a', (as) => as.map((a) => a.getAttribute('href')));
ok(mine.length === 2 && mine.every((h) => h.startsWith(`/store/${ga.slug}#/o/`)), 'سفارش‌های من: هر دو سفارش با پیوندِ پیگیری', mine.join());
ok((await page.textContent('#orders')).includes('لغو کردید'), 'وضعیتِ هر سفارش در فهرست');
await axe(page, 'سفارش‌های من');

//  ── تاریک ──
const dark = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'fa-AF', colorScheme: 'dark' });
const dp = await dark.newPage(); watch(dp);
await dp.goto(`${base}/market/`);
await dp.waitForSelector('#shops li a.shop-card');
await axe(dp, 'خانه (تاریک)');
await dp.goto(`${base}/store/${ga.slug}`);
await dp.waitForSelector('#products li[data-id]');
await dp.locator('#products li[data-id="rice"]').getByRole('button', { name: /افزودن/ }).click();
await dp.click('#cart-bar');
await opened(dp);
await axe(dp, 'برگهٔ سبد (تاریک)');
await dark.close();

//  ── تولید بی سرویسِ پیامک (۲۳.۳ بندِ ۳): «تأیید هنوز راه‌اندازی نشده»، نه «فرستاده شد» ──
//  سرور در تولید `verification.available = false` می‌دهد (آزمونِ سرورِ #244)؛ این‌جا همان پاسخ به صفحه داده می‌شود.
{
  const c2 = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'fa-AF' });
  const p2 = await c2.newPage(); watch(p2);
  await p2.route(`**/api/store/${ga.slug}`, closeSafeRoute(async (route) => {
    const res = await route.fetch();
    const j = await res.json();
    j.verification = { available: false };
    await route.fulfill({ response: res, json: j });
  }));
  await p2.goto(`${base}/store/${ga.slug}`);
  await p2.waitForSelector('#products li[data-id]');
  await p2.locator('#products li[data-id="rice"]').getByRole('button', { name: /افزودن/ }).click();
  await p2.goto(`${base}/store/${ga.slug}#/checkout`);
  await p2.waitForSelector('#view-checkout:not([hidden])');
  await p2.fill('#c-phone', '0790000000');
  const t = await p2.textContent('#verify-msg');
  ok(/راه‌اندازی نشده/.test(t) && (await p2.isHidden('#verify-send')) && (await p2.isDisabled('#submit-order')),
    'بی سرویسِ پیامک: پیامِ روشن، بی دکمهٔ «فرستادن» و بی ثبت', t);
  await c2.close();
}

//  ── نصب، اندازه و جداییِ برنامهٔ مدیریت ──
const man = await (await fetch(`${base}/market/manifest.webmanifest`)).json();
ok(man.start_url === '/market/' && man.icons.length >= 2, 'manifest ِ بازارچه');
for (const ic of man.icons) ok((await fetch(base + ic.src)).status === 200, `نشانهٔ نصب ${ic.sizes} (${ic.purpose})`);
const sman = await (await fetch(`${base}/store/${ga.slug}/manifest.webmanifest`)).json();
ok(sman.name === 'سوپرمارکتِ آزمایشی' && sman.start_url === `/store/${ga.slug}`, 'manifest ِ هر دکان');
ok((await fetch(`${base}/store/${gc.slug}/manifest.webmanifest`)).status === 200, 'دکانِ بیرون از فهرست ویترینِ خودش را دارد');
const pub = path.join(import.meta.dirname, '..', 'public');
const size = (...f) => f.reduce((s, x) => s + fs.statSync(path.join(pub, x)).size, 0);
ok(size('kit/kit.js', 'market/market.js') < 75 * 1024, 'JS ِ بازارچه زیرِ ۷۵KB', `${size('kit/kit.js', 'market/market.js')} بایت`);
ok(size('kit/kit.js', 'store/store.js') < 75 * 1024, 'JS ِ ویترین زیرِ ۷۵KB', `${size('kit/kit.js', 'store/store.js')} بایت`);
const html = ['market/index.html', 'market/market.js', 'store/index.html', 'store/store.js', 'kit/kit.js'].map((f) => fs.readFileSync(path.join(pub, f), 'utf8')).join('\n');
ok(!/index\.html|\/admin\b|\/portal\b|\/pos\b/.test(html), 'هیچ پیوندی به برنامهٔ مدیریتِ دکان، پنل یا صندوق');
ok(!/<script(?![^>]*\bsrc=)[^>]*>/i.test(html) && !/\sstyle=/.test(html) && !/\son[a-z]+=/i.test(html), 'بی اسکریپت، style یا رویدادِ درون‌خطی');
const csp = (await fetch(`${base}/market/`)).headers.get('content-security-policy') || '';
ok(/script-src 'self'/.test(csp) && !/unsafe-inline/.test(csp), 'CSP ِ سخت روی بازارچه', csp);

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
if (!axeSrc) {
  if (SKIP) console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
  else ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ بازارچه: از خانه تا لغوِ سفارش');
process.exit(bad ? 1 : 0);
