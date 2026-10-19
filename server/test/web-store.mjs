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
 *   • نوار و برگهٔ تیرهٔ سبد (phone-ui): نوار فقط با سبدِ پُر (عکس/حرفِ اول، شمار،
 *     مبلغ)، کارت فقط «افزودن» و نشانِ مقدار، برگه با − مقدار + و مقدارِ نوشتنی،
 *     ردیفِ ارسال از تنظیمِ سرور، Esc و زدنِ بالا، کاهشِ حرکت، axe و بی بیرون‌زدگی؛
 *   • دسترس‌پذیری (axe-core): کنتراست، نامِ دکمه، برچسبِ فیلد، پیوند.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
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
  await page.waitForSelector('#products li', { timeout: 15000 });
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
ok((await page.locator('#products li').count()) === 2, 'فقط دو کالای منتشرشده — «صابون» نه');
ok((await page.textContent('#products')).includes('موجودی در حال بررسی است'), 'موجودیِ نامعلوم «موجود» گفته نمی‌شود (۳۲.۳)');

for (const w of [320, 360, 768, 1280]) {
  await page.setViewportSize({ width: w, height: 760 });
  const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  ok(over <= 0, `بی بیرون‌زدگیِ افقی در ${w}`, `${over}px`);
}
await page.setViewportSize({ width: 360, height: 760 });

await page.fill('#q', 'روغن');
await page.waitForFunction(() => document.querySelectorAll('#products li').length === 1, null, { timeout: 5000 });
ok(true, 'جست‌وجو');
await page.fill('#q', 'چیزی‌که‌نیست');
await page.waitForSelector('#catalog-empty:not([hidden])', { timeout: 5000 });
ok((await page.textContent('#catalog-empty')).includes('پیدا نشد'), 'صفحهٔ «چیزی پیدا نشد» راه دارد (WEB-06)');
await page.click('#clear-search');
await page.waitForFunction(() => document.querySelectorAll('#products li').length === 2, null, { timeout: 5000 });

ok(!(await page.isVisible('#cart-bar')), 'سبدِ خالی ⇒ نوارِ سبد نیست');
const rice = page.locator('#products li[data-id="rice"]');
await rice.getByRole('button', { name: /افزودن/ }).click();
//  design v2: کارت فقط «افزودن» دارد (هر زدن یکی بیشتر) و نشانِ مقدار — − و + فقط در برگه
await rice.getByRole('button', { name: /افزودن/ }).click();
ok((await rice.getByRole('button', { name: /یکی کمتر|یکی بیشتر/ }).count()) === 0, 'کارتِ کالا − / + ندارد');
ok((await rice.locator('.qty-badge').textContent()).includes('۲ در سبد'), 'نشانِ «۲ در سبد» روی کارت');
const oil = page.locator('#products li[data-id="oil"]');
await oil.getByRole('button', { name: /افزودن/ }).click();
ok(await page.isVisible('#cart-bar'), 'نوارِ سبد پیدا شد');
await page.reload();
await page.waitForSelector('#products li');
ok((await page.textContent('#open-cart')).includes('۲'), 'سبدِ مهمان با تازه‌سازی می‌ماند (S132)');

//  ── نوار و برگهٔ تیرهٔ سبد (phone-ui، تصویرهای مرجعِ ۵ و ۶) ──
const bar = await page.evaluate(() => ({
  thumbs: [...document.querySelectorAll('#cart-thumbs .cbar-thumb')].map((t) => t.textContent.trim()),
  count: document.getElementById('cart-bar-count').textContent.trim(),
  total: document.getElementById('cart-bar-total').textContent,
  name: document.getElementById('open-cart').getAttribute('aria-label'),
  bg: getComputedStyle(document.getElementById('open-cart')).backgroundColor,
  h: document.getElementById('open-cart').getBoundingClientRect().height,
}));
ok(bar.thumbs.join(',') === 'ر,ب' && bar.count === '۲' && bar.total.includes('۵۴۱') && /۲ قلم/.test(bar.name) && bar.h >= 48,
  'نوار: حرفِ اولِ تازه‌ترین‌ها، شمار، مبلغ، نامِ دسترس‌پذیر، ≥ ۴۸', JSON.stringify(bar));
await page.click('#open-cart');
await page.waitForSelector('#cart-sheet:not([hidden])');
const sheetState = await page.evaluate(() => ({
  role: document.getElementById('cart-sheet').getAttribute('role'),
  focus: document.activeElement && document.activeElement.id,
  lines: document.querySelectorAll('#sheet-lines li').length,
  barHidden: document.getElementById('cart-bar').hidden,
  expanded: document.getElementById('open-cart').getAttribute('aria-expanded'),
  fee: document.getElementById('sheet-delivery-fee').textContent,
  delivery: !document.getElementById('sheet-delivery').hidden,
  progress: !document.getElementById('sheet-progress').hidden,
  total: document.getElementById('sheet-total').textContent,
}));
ok(sheetState.role === 'dialog' && sheetState.focus === 'close-cart' && sheetState.lines === 2 && sheetState.barHidden && sheetState.expanded === 'true',
  'برگه باز شد: پنجره، تمرکز روی «بستن»، دو ردیف، نوار کنار رفت', JSON.stringify(sheetState));
ok(sheetState.delivery && sheetState.fee.includes('۵۰') && !sheetState.progress && sheetState.total.includes('۵۴۱'),
  'ردیفِ ارسال: هزینهٔ واقعیِ دکان (۵۰)، بی نوارِ پیشرفتِ ساختگی (حداقلِ سفارش صفر)، جمعِ کالاها', JSON.stringify(sheetState));
const oilLine = page.locator('#sheet-lines li', { hasText: 'روغن' });
await oilLine.getByRole('button', { name: /یکی بیشتر/ }).click();
ok((await page.textContent('#sheet-total')).includes('۸۴۱'), '+ در برگه: ۲ روغن ⇒ ۸۴۱');
await page.locator('#sheet-lines li', { hasText: 'روغن' }).getByRole('button', { name: /یکی کمتر/ }).click();
ok((await page.textContent('#sheet-total')).includes('۵۴۱'), '− در برگه ⇒ ۵۴۱');
await page.locator('#sheet-lines li', { hasText: 'برنج' }).getByRole('textbox', { name: /مقدار/ }).fill('۳');
await page.keyboard.press('Tab');
ok((await page.textContent('#sheet-total')).includes('۶۶۲'), 'مقدارِ نوشتنی (رقمِ فارسی): ۳ برنج ⇒ ۳۶۲ + ۳۰۰ = ۶۶۲', await page.textContent('#sheet-total'));
await page.locator('#sheet-lines li', { hasText: 'برنج' }).getByRole('textbox', { name: /مقدار/ }).fill('2');
await page.keyboard.press('Tab');
ok((await page.textContent('#sheet-total')).includes('۵۴۱'), 'برگشت به ۲ برنج');
for (const w of [360, 390, 768]) {
  await page.setViewportSize({ width: w, height: 760 });
  const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  ok(over <= 0, `برگهٔ باز بی بیرون‌زدگی در ${w}`, `${over}px`);
}
await page.setViewportSize({ width: 360, height: 760 });
await page.keyboard.press('Escape');
await page.waitForSelector('#cart-sheet', { state: 'hidden' });
ok(await page.evaluate(() => document.activeElement && document.activeElement.id === 'open-cart'), 'Esc برگه را می‌بندد و تمرکز به نوار برمی‌گردد');
await page.click('#open-cart');
await page.waitForSelector('#cart-sheet:not([hidden])');
await page.mouse.click(180, 30);
await page.waitForSelector('#cart-sheet', { state: 'hidden' });
ok(true, 'زدنِ بالای برگه، برگه را می‌بندد');

await page.click('#open-cart');
await page.waitForSelector('#cart-sheet:not([hidden])');
await page.click('#sheet-continue');
await page.waitForSelector('#view-checkout:not([hidden])');
ok(await page.evaluate(() => document.getElementById('cart-sheet').hidden && !document.body.classList.contains('sheet-open')), '«ادامه» ⇒ همان صفحهٔ ثبتِ پیشین، برگه بسته');
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
  //  برگهٔ تیرهٔ سبد، روز و شب، و «کاهشِ حرکت»
  for (const scheme of ['light', 'dark']) {
    const sc = await browser.newContext({ viewport: { width: 390, height: 844 }, bypassCSP: true, colorScheme: scheme, reducedMotion: 'reduce' });
    const sp = await sc.newPage();
    await sp.goto(`${base}/store/${slug}`);
    await sp.waitForSelector('#products li');
    await sp.locator('#products li[data-id="rice"]').getByRole('button', { name: /افزودن/ }).click();
    await sp.click('#open-cart');
    await sp.waitForSelector('#cart-sheet:not([hidden])');
    const anim = await sp.evaluate(() => getComputedStyle(document.getElementById('cart-sheet')).animationName);
    ok(anim === 'none', `کاهشِ حرکت (${scheme}): برگه بی انیمیشن`, anim);
    await sp.addScriptTag({ content: axeSrc });
    const out = await sp.evaluate(async () => (await window.axe.run('#cart-sheet',
      { runOnly: ['color-contrast', 'button-name', 'label', 'aria-dialog-name', 'aria-valid-attr-value'] })).violations
      .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
    ok(out.length === 0, `دسترس‌پذیریِ برگهٔ سبد (${scheme})`, out.join(' | '));
    await sp.click('#close-cart');
    await sp.addScriptTag({ content: axeSrc });
    const outBar = await sp.evaluate(async () => (await window.axe.run('#cart-bar', { runOnly: ['color-contrast', 'button-name'] })).violations.map((x) => x.id));
    ok(outBar.length === 0, `دسترس‌پذیریِ نوارِ سبد (${scheme})`, outBar.join(' | '));
    await sc.close();
  }
  const axeCtx = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  const ap = await axeCtx.newPage();
  const v = [];
  for (const hash of ['#/', '#/cart']) {
    await ap.goto(`${base}/store/${slug}${hash}`);
    await ap.waitForSelector('#products li', { state: 'attached', timeout: 15000 });
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

await page.goto(`${base}/store/no-such-store`);
await page.waitForSelector('#view-missing:not([hidden])', { timeout: 10000 });
ok(true, 'ویترینِ ناموجود: «پیدا نشد»');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
const jsSize = fs.statSync(path.join(import.meta.dirname, '..', 'public', 'store', 'store.js')).size;
ok(jsSize < 75 * 1024, 'جاوااسکریپتِ صفحه زیرِ ۷۵KB (۳.۱۰)', `${jsSize} بایت، فشرده‌نشده`);

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: از کاتالوگ تا رسید');
process.exit(bad ? 1 : 0);
