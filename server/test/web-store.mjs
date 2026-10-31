/**
 * ویترینِ اینترنتی با کرومیومِ واقعی — فاز ۴ (WEB، CHK؛ بخش‌های ۲۳، ۳۱.۱، ۳۲.۳).
 *
 *     npm run test:web-store
 *
 * سرورِ واقعی، دکانِ واقعی (ثبت‌نام، همگام‌سازیِ کالا، انتشار) و صفحهٔ ویترین
 * در عرضِ گوشی (۳۶۰). آن‌چه سنجیده می‌شود:
 *   • راست‌چین، قلمِ Vazirmatn از همین سرور، بی بیرون‌زدگی در ۳۲۰ تا ۱۲۸۰؛
 *   • CSP ِ سخت: هیچ نقضی (بی اسکریپت یا style ِ درون‌خطی)؛
 *   • وضعیتِ دکان (۳۱.۱) دیده می‌شود و هیچ دکمهٔ «پرداختِ آنلاین» ِ ساختگی نیست؛
 *   • جست‌وجو، سبد، تأییدِ شماره با کد، ثبت ⇒ رسید؛ مبلغِ روی صفحه همان سرور؛
 *   • دسترس‌پذیری (axe-core): کنتراست، نامِ دکمه، برچسبِ فیلد، پیوند.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

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
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, many, newId, now, closeDb } = require('../src/db');
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
const email = 'owner@store.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ آزمایشی' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
for (const key of ['shop.orders', 'shop.storefront']) {
  await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
}
const slug = 'demo-store';
const put = await call('PUT', '/api/shop/storefront', {
  slug, enabled: true, title: 'ویترینِ آزمایشی', tagline: 'کالای روزانه', contactPhone: '0700000001',
  deliveryFeeMinor: 5000, minOrderMinor: 0,
}, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
  op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالاها از راهِ همگام‌سازیِ واقعی رسیدند');
for (const id of ['rice', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
async function open(width, ctxOpts = {}) {
  const ctx = await browser.newContext({ viewport: { width, height: 760 }, locale: 'fa-AF', ...ctxOpts });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#products li[data-id]', { timeout: 15000 });
  return { ctx, page };
}

const { ctx, page } = await open(360);
ok(await page.evaluate(() => document.documentElement.dir === 'rtl' && document.documentElement.lang === 'fa'), 'راست‌چین و فارسی');
await page.evaluate(() => document.fonts.ready);
ok(await page.evaluate(() => document.fonts.check('16px Vazirmatn') && getComputedStyle(document.body).fontFamily.includes('Vazirmatn')),
  'قلمِ Vazirmatn از همین سرور بار شد');
ok((await page.textContent('#store-title')) === 'ویترینِ آزمایشی', 'نامِ ویترین');
const status = await page.textContent('#store-status');
ok(/باز است|بسته است|آفلاین|اعلام شده/.test(status) && /اتصال/.test(status), 'وضعیتِ دکان و اتصال دیده می‌شود (۳۱.۱)', status.slice(0, 80));
ok((await page.locator('#products li[data-id]').count()) === 2, 'فقط دو کالای منتشرشده — «صابون» نه');
//  ۳۲.۳: موجودیِ نامعلوم هرگز «موجود» نیست؛ «در حال بررسی» یک بار بالای کاتالوگ، نه روی هر کارت
ok(await page.isVisible('#stock-note') && (await page.textContent('#stock-note')).includes('موجودی در حال بررسی است')
  && !/(^|[\s،.])موجود([\s،.]|$)/.test(await page.textContent('#products')), 'موجودیِ نامعلوم «موجود» گفته نمی‌شود (۳۲.۳)');

for (const w of [320, 360, 768, 1280]) {
  await page.setViewportSize({ width: w, height: 760 });
  const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  ok(over <= 0, `بی بیرون‌زدگیِ افقی در ${w}`, `${over}px`);
}
await page.setViewportSize({ width: 360, height: 760 });

await page.fill('#q', 'روغن');
await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length === 1, null, { timeout: 5000 });
ok(true, 'جست‌وجو');
await page.fill('#q', 'چیزی‌که‌نیست');
await page.waitForSelector('#catalog-empty:not([hidden])', { timeout: 5000 });
ok((await page.textContent('#catalog-empty')).includes('پیدا نشد'), 'صفحهٔ «چیزی پیدا نشد» راه دارد (WEB-06)');
await page.click('#clear-search');
await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length === 2, null, { timeout: 5000 });

const rice = page.locator('#products li[data-id="rice"]');
await rice.getByRole('button', { name: /افزودن/ }).click();
//  DESIGN v2: کارت −/+ ندارد؛ «یک زدن = یک واحد» و نشانِ تعداد (−/+ فقط در سبد)
await rice.getByRole('button', { name: /افزودنِ یکی دیگر/ }).click();
ok((await rice.locator('.p-add').textContent()).includes('۲'), 'نشانِ تعداد روی کارت');
const oil = page.locator('#products li[data-id="oil"]');
await oil.getByRole('button', { name: /افزودن/ }).click();
ok(await page.isVisible('#cart-bar'), 'نوارِ سبد پیدا شد');
await page.reload();
await page.waitForSelector('#products li[data-id]');
ok((await page.textContent('#open-cart')).includes('۲'), 'سبدِ مهمان با تازه‌سازی می‌ماند (S132)');

await page.click('#open-cart');
//  نوارِ تیره ⇐ برگهٔ تیرهٔ سبد (ref-5/6) ⇐ «ادامه»
//  گوشی (۳۶۰): مدلِ مرجعِ r3/r2 — سطحِ روشن بالا می‌رود و سبد روی پایهٔ سیاه است
await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'));
ok(await page.evaluate(() => document.body.classList.contains('phone-cart') && document.body.classList.contains('cart-open')), 'گوشی: پایهٔ سیاه و سبدِ باز (r2)');
ok((await page.locator('#sheet-lines li').count()) === 2 && (await page.locator('#sheet-lines .stepper').count()) === 2, 'برگهٔ سبد: اقلام با −/+');
await page.click('#cart-next');
await page.waitForSelector('#view-checkout:not([hidden])');
await page.check('input[name="fulfillment"][value="pickup"]');
//  ۱۲۰٫۵ × ۲ = ۲۴۱ + ۳۰۰ = ۵۴۱
ok((await page.textContent('#t-total')).includes('۵۴۱'), 'جمعِ سبد پیش از ثبت (CHK-04)', await page.textContent('#t-total'));
ok(await page.isDisabled('#submit-order'), 'بی شمارهٔ تأییدشده، «ثبت» بسته است (S133)');
ok((await page.locator('button', { hasText: /پرداختِ آنلاین|پرداخت آنلاین/ }).count()) === 0, 'هیچ دکمهٔ پرداختِ آنلاینِ ساختگی نیست (D-08)');
await page.fill('#c-name', 'مشتریِ آزمایشی');
await page.fill('#c-phone', '0791112233');
await page.click('#verify-send');
await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10000 });
const code = ((await page.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1];
ok(Boolean(code), 'کدِ آزمایشی (فقط بیرون از تولید) نشان داده شد');
await page.fill('#v-code', code || '');
await page.click('#verify-check');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
ok(true, 'شماره تأیید شد و «ثبت» باز شد');

if (axeSrc) {
  const axeCtx = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  const ap = await axeCtx.newPage();
  const v = [];
  for (const hash of ['#/', '#/cart']) {
    await ap.goto(`${base}/store/${slug}${hash}`);
    await ap.waitForSelector('#products li[data-id]', { state: 'attached', timeout: 15000 });
    await ap.waitForTimeout(400);
    await ap.addScriptTag({ content: axeSrc });
    const out = await ap.evaluate(async () => (await window.axe.run(document,
      { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'] })).violations
      .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
    out.forEach((x) => v.push(`${hash} ${x}`));
  }
  ok(v.length === 0, 'دسترس‌پذیری (axe-core)', v.join(' | '));
  await axeCtx.close();
} else if (SKIP) {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

//  WEB-15: رسید و پیگیری (store/order.js) جزوِ بارِ اول نیست — فقط با صفحهٔ سفارش می‌آید
ok(!(await page.$('script[src="/store/order.js"]')), 'order.js پیش از صفحهٔ سفارش بار نشد');
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 10000 });
await page.waitForFunction(() => /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 10000 });
const row = await one(`SELECT order_no, source, total_minor, customer_phone FROM online_orders WHERE shop_id=$1`, [shopId]);
ok(row && row.source === 'storefront' && Number(row.total_minor) === 54100 && row.customer_phone === '+93791112233',
  'سفارش با قیمتِ سرور و شمارهٔ تأییدشده ثبت شد', JSON.stringify(row));
const faNum = (n) => Number(n).toLocaleString('fa-AF');
ok((await page.textContent('#o-title')).includes(faNum(row.order_no)), 'رسید: شمارهٔ سفارش همان سرور (CHK-20)');
const totals = await page.textContent('#o-totals');
ok(totals.includes('۵۴۱') && totals.includes('مانده'), 'رسید: مبلغِ نهایی، پرداخت‌شده و مانده (S136)');
ok(await page.isVisible('#o-cancel'), 'لغوِ رایگان پیش از قبول (CHK-22) پیدا است');
ok(/#\/o\/[0-9A-Z]{26}\.[A-Za-z0-9_-]{32}$/.test(page.url()), 'نشانیِ پیگیری توکنِ حدس‌نزدنی دارد (S137)');

const man = await (await fetch(`${base}/store/${slug}/manifest.webmanifest`)).json();
ok(man.name === 'ویترینِ آزمایشی' && man.start_url === `/store/${slug}` && man.icons.length === 3, 'وب‌اپِ قابلِ نصبِ همین دکان (WEB-14)');
ok((await fetch(`${base}/store/no-such-store/manifest.webmanifest`)).status === 404, 'manifest ِ ویترینِ ناموجود ⇒ ۴۰۴');

//  سه زبان (دودی): en چپ‌به‌راست و ps راست‌به‌چپ — ویترین، سبد، ثبت؛ axe در هر دو جهت
for (const [lang, dir, stock, ck] of [['en', 'ltr', 'Stock is being checked', 'Checkout'], ['ps', 'rtl', 'موجودي لا کتل کېږي', 'فرمایش ثبتول']]) {
  const lc = await browser.newContext({ viewport: { width: 390, height: 844 }, bypassCSP: Boolean(axeSrc) });
  const lp = await lc.newPage();
  lp.on('pageerror', (e) => errors.push(String(e)));
  await lp.goto(`${base}/store/${slug}?lang=${lang}`);
  await lp.waitForSelector('#products li[data-id]', { timeout: 15000 });
  ok((await lp.getAttribute('html', 'dir')) === dir && (await lp.getAttribute('html', 'lang')) === lang, `${lang}: جهت و زبانِ ویترین`);
  ok((await lp.locator('#products li[data-id]').count()) === 2 && (await lp.textContent('#stock-note')).includes(stock), `${lang}: کاتالوگ و یادداشتِ موجودی`);
  await lp.locator('#products li[data-id] .p-add').first().click();
  await lp.goto(`${base}/store/${slug}?lang=${lang}#/checkout`);
  await lp.waitForSelector('#view-checkout:not([hidden])');
  ok((await lp.textContent('#view-checkout h1')) === ck, `${lang}: صفحهٔ ثبت`);
  const over = await lp.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  ok(over <= 0, `${lang}: بی بیرون‌زدگی`, `${over}px`);
  if (axeSrc) {
    await lp.addScriptTag({ content: axeSrc });
    const v = await lp.evaluate(async () => (await window.axe.run(document, { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'] })).violations.map((x) => x.id));
    ok(v.length === 0, `دسترس‌پذیری (axe-core): ثبت (${lang})`, v.join(' | '));
  }
  await lc.close();
}

//  ⛔ واژه‌نامه نیامد (شبکه یا ۵۰۰): ویترینِ زنده هرگز کلیدِ خام («od.history»، «ship.free») نشان نمی‌دهد —
//  HTML ِ فارسی می‌ماند و متنِ ساختهٔ JS از واژه‌های پایهٔ کیت؛ و یک بار دوباره خواسته می‌شود
const KEYS = Object.keys(JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '..', 'public', 'kit', 'i18n', 'fa.json'), 'utf8')));
const rawKeys = (pg) => pg.evaluate((keys) => {
  const set = new Set(keys);
  const out = [];
  const words = (s) => String(s || '').split(/[\s،,()«»:—]+/).filter((w) => set.has(w));
  const tw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let n = tw.nextNode(); n; n = tw.nextNode()) out.push(...words(n.nodeValue));
  for (const e of document.querySelectorAll('[aria-label],[placeholder],[title],[alt]')) {
    for (const a of ['aria-label', 'placeholder', 'title', 'alt']) out.push(...words(e.getAttribute(a)).map((w) => `${a}=${w}`));
  }
  out.push(...words(document.title).map((w) => `<title>=${w}`));
  return out;
}, KEYS);
const placedId = (await one(`SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at LIMIT 1`, [shopId])).id;
const placedToken = require('../src/lib/storefront').tokenOf(shopId, placedId);
for (const lang of ['fa', 'en']) {
  const nc = await browser.newContext({ viewport: { width: 390, height: 844 } });
  let asked = 0;
  await nc.route('**/kit/i18n/*.json', (r) => { asked++; return r.abort('failed'); });
  const np = await nc.newPage();
  np.on('pageerror', (e) => errors.push(String(e)));
  await np.goto(`${base}/store/${slug}?lang=${lang}`);
  await np.waitForSelector('#products li[data-id]', { timeout: 15000 });
  await np.waitForFunction(() => !document.documentElement.hasAttribute('data-i18n'), null, { timeout: 5000 });
  ok(asked === 2, `بی واژه‌نامه (${lang}): یک بار دوباره خواسته شد`, `${asked} درخواست`);
  ok(await np.isVisible('#products li[data-id]'), `بی واژه‌نامه (${lang}): صفحه پیداست و کاتالوگ آمد`);
  const seen = [];
  seen.push(...await rawKeys(np));
  await np.locator('#products li[data-id] .p-add').first().click();
  await np.goto(`${base}/store/${slug}?lang=${lang}#/cart`);
  await np.waitForTimeout(400);
  seen.push(...await rawKeys(np));
  await np.goto(`${base}/store/${slug}?lang=${lang}#/checkout`);
  await np.waitForSelector('#view-checkout:not([hidden])');
  seen.push(...await rawKeys(np));
  ok((await np.textContent('#view-checkout h1')).trim() === 'ثبتِ سفارش', `بی واژه‌نامه (${lang}): متنِ فارسیِ HTML ماند`);
  await np.goto(`${base}/store/${slug}?lang=${lang}#/o/${encodeURIComponent(placedToken)}`);
  await np.waitForFunction(() => /\d|[۰-۹]/.test(document.getElementById('o-title').textContent), null, { timeout: 10000 });
  seen.push(...await rawKeys(np));
  ok(seen.length === 0, `بی واژه‌نامه (${lang}): هیچ کلیدِ خامی در ویترین، سبد، ثبت و سفارش`, [...new Set(seen)].join(' '));
  await nc.close();
}
//  بارِ نخست نیامد، بارِ دوم آمد ⇒ صفحه همان زبان را دارد
{
  const rc = await browser.newContext({ viewport: { width: 390, height: 844 } });
  let n = 0;
  await rc.route('**/kit/i18n/*.json', (r) => (++n === 1 ? r.fulfill({ status: 503, body: '' }) : r.continue()));
  const rp = await rc.newPage();
  await rp.goto(`${base}/store/${slug}?lang=en`);
  await rp.waitForSelector('#products li[data-id]', { timeout: 15000 });
  ok(n === 2 && (await rp.textContent('#stock-note')).includes('Stock is being checked'), 'واژه‌نامه پس از یک شکست، با بارِ دوم آمد', `${n} درخواست`);
  await rc.close();
}

//  ── E2E-MKT-02: موجودیِ کم هنگامِ ثبت (۴۰۹ ِ out_of_stock) — دکانِ دوم با موجودیِ دفتر ──
//  پرچمِ shop.storefront.stock_list خاموش ⇒ همان پیامِ پیشینِ سرور؛ روشن ⇒ همهٔ قلم‌های کم، هر کدام با مقدارِ
//  موجود و «کم کن به …»؛ تا مشتری کم نکند «ثبت» بسته است و هیچ سفارشی بی‌صدا با مقدارِ دیگر ثبت نمی‌شود.
{
  const em2 = 'stock@store.local';
  const r1 = await call('POST', '/api/auth/register/start', { name: 'صاحبِ دوم', email: em2, password });
  const r2 = await call('POST', '/api/auth/register/verify', { email: em2, code: r1.body.devCode });
  const T = (await call('POST', '/api/auth/register/complete', { ticket: r2.body.ticket, name: 'صاحبِ دوم', password,
    device: { deviceId: 'dev-owner-2', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } })).body.accessToken;
  const sid = (await call('POST', '/api/shop', { name: 'دکانِ موجودی' }, T)).body.shop.id;
  const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: sid, key, value }, A);
  for (const key of ['shop.orders', 'shop.storefront', 'shop.catalog']) await flag(key, true);
  const sslug = 'stock-store';
  await call('PUT', '/api/shop/storefront', { slug: sslug, enabled: true, title: 'ویترینِ موجودی', contactPhone: '0700000002', minOrderMinor: 0 }, T);
  const sop = (table, row, fields) => ({ op_id: `op-${row}-${Date.now()}`, ts: Date.now(), table, row_id: row, type: 'insert', fields });
  const pr = (row, name) => sop('products', row, { name, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now() });
  const en = (row, pid, units) => sop('warehouseEntries', row, { productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now() });
  const sp = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner-2', schema_version: 2, ops: [
    pr('tea', 'چای'), pr('salt', 'نمک'), en('tea-e', 'tea', 2), en('salt-e', 'salt', 1),
  ] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner-2' });
  ok(sp.status === 200, 'دکانِ موجودی: کالا و ورودِ انبار رسید', JSON.stringify(sp.body).slice(0, 160));
  for (const id of ['tea', 'salt']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);

  const sc = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  const sg = await sc.newPage();
  sg.on('pageerror', (e) => errors.push(String(e)));
  sg.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  const toCheckout = async () => {
    await sg.goto(`${base}/store/${sslug}#/checkout`);
    await sg.reload();   //  همان نشانی با # بار نمی‌شود؛ پرچمِ تازه فقط با بارِ دوباره
    await sg.waitForSelector('#view-checkout:not([hidden])', { timeout: 15000 });
    await sg.check('input[name="fulfillment"][value="pickup"]');
    await sg.fill('#c-name', 'مشتریِ موجودی');
  };
  await sg.goto(`${base}/store/${sslug}`);
  await sg.waitForSelector('#products li[data-id="tea"]', { timeout: 15000 });
  for (const [id, n] of [['tea', 3], ['salt', 2]]) {
    for (let i = 0; i < n; i++) await sg.locator(`#products li[data-id="${id}"] .p-add`).click();
  }
  await toCheckout();
  await sg.fill('#c-phone', '0791112244');
  await sg.click('#verify-send');
  await sg.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10000 });
  await sg.fill('#v-code', ((await sg.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1] || '');
  await sg.click('#verify-check');
  await sg.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });

  //  خاموش: همان پیامِ یک‌قلمیِ سرور، بی «کم کن»
  await sg.click('#submit-order');
  await sg.waitForFunction(() => /موجود/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 10000 });
  const offMsg = await sg.textContent('#checkout-msg');
  ok(!(offMsg.includes('چای') && offMsg.includes('نمک')) && (await sg.locator('#cart-lines .fit').count()) === 0,
    'E2E-MKT-02 خاموش: همان پیامِ پیشینِ سرور', offMsg);

  //  روشن: فهرستِ هر دو قلم با مقدارِ موجود؛ «ثبت» تا کم کردن بسته
  ok(!('features' in (await call('GET', `/api/store/${sslug}`)).body), 'E2E-MKT-02 خاموش: پاسخِ ویترین همان پیشین، بی کلیدِ features');
  await flag('shop.storefront.stock_list', true);
  ok((await call('GET', `/api/store/${sslug}`)).body.features?.stockList === true, 'E2E-MKT-02 روشن: features.stockList');
  await toCheckout();
  await sg.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
  await sg.click('#submit-order');
  await sg.waitForFunction(() => document.querySelectorAll('#cart-lines .fit').length === 2, null, { timeout: 10000 });
  const onMsg = await sg.textContent('#checkout-msg');
  ok(onMsg.includes('چای') && onMsg.includes('نمک') && onMsg.includes('فقط ۲') && onMsg.includes('فقط ۱'),
    'E2E-MKT-02 روشن: همهٔ قلم‌های کم با مقدارِ موجود', onMsg);
  ok(await sg.isDisabled('#submit-order'), 'E2E-MKT-02: تا مشتری مقدار را کم نکند «ثبت» بسته است');
  ok((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [sid])).n === 0, 'E2E-MKT-02: هیچ سفارشی بی‌صدا ثبت نشد');
  await sg.locator('#cart-lines .fit').first().click();
  ok(await sg.isDisabled('#submit-order') && (await sg.locator('#cart-lines .fit').count()) === 1, 'E2E-MKT-02: یک قلم ماند، «ثبت» هنوز بسته');
  await sg.locator('#cart-lines .fit').first().click();
  await sg.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 5000 });
  await sg.click('#submit-order');
  await sg.waitForSelector('#view-order:not([hidden])', { timeout: 10000 });
  const placed = await many(`SELECT l.product_id, l.qty_milli FROM online_order_lines l JOIN online_orders o ON o.id = l.order_id
    WHERE o.shop_id=$1 ORDER BY l.product_id`, [sid]);
  ok(JSON.stringify(placed.map((r) => [r.product_id, Number(r.qty_milli)])) === JSON.stringify([['salt', 1000], ['tea', 2000]]),
    'E2E-MKT-02: سفارش با همان مقدارِ پذیرفته‌شدهٔ مشتری ثبت شد', JSON.stringify(placed));
  await sc.close();
}

await page.goto(`${base}/store/no-such-store`);
await page.waitForSelector('#view-missing:not([hidden])', { timeout: 10000 });
ok(true, 'ویترینِ ناموجود: «پیدا نشد»');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
const jsSize = fs.statSync(path.join(import.meta.dirname, '..', 'public', 'store', 'store.js')).size
  + fs.statSync(path.join(import.meta.dirname, '..', 'public', 'kit', 'kit.js')).size
  + fs.statSync(path.join(import.meta.dirname, '..', 'public', 'kit', 'boot.js')).size;
ok(jsSize < 75 * 1024, 'جاوااسکریپتِ صفحه (آغاز + کیت + ویترین) زیرِ ۷۵KB (۳.۱۰)', `${jsSize} بایت، فشرده‌نشده`);
//  صفحهٔ سفارش همان بارِ اول + order.js است؛ سقفِ §۳.۱۰ برای آن هم (با فشرده‌سازیِ سند)
const orderGz = zlib.gzipSync(Buffer.concat(['store/store.js', 'store/order.js', 'kit/kit.js', 'kit/boot.js']
  .map((f) => fs.readFileSync(path.join(import.meta.dirname, '..', 'public', f))))).length;
ok(orderGz < 75 * 1024, 'جاوااسکریپتِ صفحهٔ سفارش (با order.js) زیرِ ۷۵KB ِ فشرده (۳.۱۰)', `${orderGz} بایتِ gzip`);

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: از کاتالوگ تا رسید');
process.exit(bad ? 1 : 0);
