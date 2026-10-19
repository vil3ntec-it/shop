/**
 * ویترین، گامِ ۲ با کرومیومِ واقعی — ناحیه، بازه، حساب، صفحهٔ کالا
 * (CHK-05/07/10/12، WEB-02/08/12، ۲۳ S138/S140).
 *
 *     npm run test:web-store-v2
 *     SHOTS_DIR=/tmp/shots npm run test:web-store-v2   # + عکسِ صفحه در ۳۹۰ و ۱۲۸۰
 *
 * سرورِ واقعی، دکانِ واقعی (ثبت‌نام، همگام‌سازیِ کالا، انتشار، ناحیه و بازه از
 * راهِ API ِ مالک) و موقعیتِ ساختگیِ مرورگر (Playwright geolocation):
 *   • پینِ داخلِ ناحیه ⇒ هزینهٔ همان ناحیه در جمع و در سفارشِ سرور؛ «تا رایگان»؛
 *   • پینِ بیرون ⇒ پیامِ روشن، «ثبت» بسته، دکمهٔ «خودم از دکان می‌گیرم»؛
 *   • بازهٔ پر «پر است» و انتخاب‌نشدنی؛ سفارش در بازهٔ دیگر با همان زمانِ سرور؛
 *   • حساب از رسید با همان شماره؛ تاریخچه، خریدِ دوباره و نشانیِ نگه‌داشته؛
 *   • سبد با ساختنِ حساب دست نمی‌خورد (S140)؛ CSP ِ سخت؛ axe-core.
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
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };
const SHOTS = process.env.SHOTS_DIR || '';
if (SHOTS) fs.mkdirSync(SHOTS, { recursive: true });
const shot = async (page, name) => { if (SHOTS) await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: false }); };

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
const email = 'owner@store2.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ آزمایشی' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
for (const key of ['shop.orders', 'shop.storefront']) await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
const slug = 'demo-two';
const put = await call('PUT', '/api/shop/storefront', {
  slug, enabled: true, title: 'سوپرمارکتِ نمونه', tagline: 'خوراکی و بهداشتی', contactPhone: '0700000001',
  deliveryFeeMinor: 5000, minOrderMinor: 0,
}, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد');
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('rice', { name: 'برنجِ سیلا', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
  op('oil', { name: 'روغنِ آفتاب‌گردان', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  op('tea', { name: 'چای سبز', unit: 'بسته', salePrice: 95, category: 'خوراکی' }),
  op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
for (const id of ['rice', 'oil', 'tea', 'soap']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);
await call('PUT', '/api/shop/storefront/products/rice', {
  published: true, description: 'برنجِ دانه‌بلندِ اعلا، پاک‌شده و بسته‌بندیِ تازه.',
  specs: [{ name: 'وزن', value: '۱ کیلو' }, { name: 'کشور', value: 'افغانستان' }],
}, T);
const POLY = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
const del = await call('PUT', '/api/shop/storefront/delivery', {
  zones: [
    { name: 'کارته‌سه', kind: 'polygon', polygon: POLY, feeMinor: 3000, minOrderMinor: 0, freeOverMinor: 150000 },
    { name: 'دارالامان', kind: 'radius', centerLat: 34.45, centerLng: 69.10, radiusM: 1500, feeMinor: 8000, minOrderMinor: 60000 },
  ],
  slotDays: 3,
  slotRules: [{ fulfillment: 'pickup', weekdays: [0, 1, 2, 3, 4, 5, 6], startMin: 600, endMin: 720, capacity: 1, cutoffMin: 0 }],
}, T);
ok(del.status === 200, 'ناحیه و بازه از راهِ API ِ مالک', JSON.stringify(del.body).slice(0, 100));

//  نخستین بازهٔ «دریافت» را مشتریِ دیگری پر می‌کند (همان راهِ عمومی)
const slotsBefore = (await call('GET', `/api/store/${slug}/slots?fulfillment=pickup`)).body.slots;
const otherStart = await call('POST', `/api/store/${slug}/verify/start`, { phone: '0799990000' });
const otherTicket = (await call('POST', `/api/store/${slug}/verify/check`, { phone: '0799990000', code: otherStart.body.devCode })).body.ticket;
const filled = await call('POST', `/api/store/${slug}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتریِ دیگر', phone: '0799990000' },
  lines: [{ productId: 'tea', qtyMilli: 1000 }], slotStartsAt: slotsBefore[0].startsAt,
}, null, { 'X-Store-Ticket': otherTicket, 'Idempotency-Key': 'fill-first-slot-0001' });
ok(filled.status === 201, 'بازهٔ نخست پر شد (ظرفیتِ ۱)', String(filled.status));

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const INSIDE = { latitude: 34.53, longitude: 69.17 };
const OUTSIDE = { latitude: 34.62, longitude: 69.31 };
async function open(width, height, geolocation, hash = '') {
  const ctx = await browser.newContext({ viewport: { width, height }, locale: 'fa-AF', geolocation, permissions: ['geolocation'] });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  await page.goto(`${base}/store/${slug}${hash}`);
  await page.waitForSelector('#products li', { state: 'attached', timeout: 15000 });
  return { ctx, page };
}
const faNum = (n) => Number(n).toLocaleString('fa-AF');

//  ═════ ۱) خانه، ردیف‌ها و صفحهٔ کالا ═════
const { ctx, page } = await open(390, 844, INSIDE);
await page.waitForSelector('#shelf-new:not([hidden]) .mini', { timeout: 10000 });
ok((await page.locator('#shelf-new .mini').count()) === 4, 'WEB-02: «تازه‌ها» از انتشارِ واقعی');
ok(await page.isHidden('#shelf-best') === false, '«پرفروش‌ها» از سفارشِ واقعی (یک سفارشِ چای)');
ok((await page.textContent('#shelf-best')).includes('چای سبز'), 'پرفروش = همان کالای سفارش‌داده');
await shot(page, 'home-390');

await page.click('#products li[data-id="rice"] .card-link');
await page.waitForSelector('#view-product:not([hidden])');
await page.waitForFunction(() => document.getElementById('p-name').textContent.includes('برنج'), null, { timeout: 10000 });
ok((await page.textContent('#p-desc')).includes('دانه‌بلند'), 'WEB-08: توضیحِ عمومیِ کالا');
ok((await page.textContent('#p-specs')).includes('افغانستان'), 'WEB-08: مشخصات');
ok((await page.locator('#p-similar .mini').count()) >= 2, 'WEB-08: کالاهای مشابهِ همان دسته');
await page.locator('#p-act').getByRole('button', { name: /افزودن/ }).click();
await page.locator('#p-act').getByRole('button', { name: /یکی بیشتر/ }).click();
await shot(page, 'product-390');

//  ═════ ۲) ناحیه: پینِ داخل ⇒ هزینهٔ همان ناحیه ═════
await page.goto(`${base}/store/${slug}#/cart`);
await page.waitForSelector('#view-checkout:not([hidden])');
await page.check('input[name="fulfillment"][value="delivery"]');
ok((await page.textContent('#t-fee')).includes('پس از پین'), 'بی پین و بی ناحیه، هزینه «پس از پین یا ناحیه» است، نه عددِ ساختگی');
ok(await page.isDisabled('#submit-order'), 'و «ثبت» بسته است');
await page.fill('#a-text', 'کارته‌سه، سرکِ دوم');
await page.click('#pin-btn');
await page.waitForFunction(() => /کارته‌سه/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
ok(true, 'CHK-10: پین داخلِ ناحیهٔ «کارته‌سه» سنجیده شد (بی ژئوکدر)');
//  ۲ کیلو برنج = ۲۴۱ + ارسالِ ۳۰ = ۲۷۱
ok((await page.textContent('#t-fee')).includes('۳۰'), 'CHK-07: هزینهٔ ارسالِ همان ناحیه', await page.textContent('#t-fee'));
ok((await page.textContent('#t-total')).includes('۲۷۱'), 'جمع با هزینهٔ ناحیه', await page.textContent('#t-total'));
ok((await page.textContent('#free-note')).includes('تا ارسالِ رایگان'), 'CHK-05: «X تا ارسالِ رایگان»');
await page.evaluate(() => window.scrollTo(0, 0));
await shot(page, 'checkout-totals-390');
await page.fill('#c-name', 'زهره');
await page.fill('#c-phone', '0791112233');
await page.click('#verify-send');
await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10000 });
const code = ((await page.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1];
await page.fill('#v-code', code || '');
await page.click('#verify-check');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.evaluate(() => document.getElementById('zone-box').scrollIntoView({ block: 'center' }));
await shot(page, 'checkout-zone-390');
await page.click('#submit-order');
await page.waitForFunction(() => /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 10000 });
const o1 = await one(`SELECT order_no, delivery_fee_minor, total_minor, address FROM online_orders WHERE shop_id=$1 AND customer_phone='+93791112233'`, [shopId]);
const a1 = typeof o1.address === 'string' ? JSON.parse(o1.address) : o1.address;
ok(Number(o1.delivery_fee_minor) === 3000 && Number(o1.total_minor) === 27100 && a1.zone && a1.zone.name === 'کارته‌سه' && a1.zone.source === 'pin',
  'سرور همان هزینهٔ ناحیه را گرفت و ناحیه را با منبعِ «پین» نوشت', JSON.stringify({ fee: o1.delivery_fee_minor, zone: a1.zone }));
ok((await page.textContent('#o-when')).includes('کارته‌سه'), 'رسید: ناحیه');

//  ═════ ۳) حساب از رسید (CHK-01)، سبد دست نمی‌خورد (S140) ═════
await page.evaluate(() => localStorage.setItem('store-cart:demo-two', JSON.stringify({ oil: { qtyMilli: 1000, name: 'روغنِ آفتاب‌گردان', unit: 'بوتل', priceMinor: 30000, maxQtyMilli: null } })));
const cartBefore = await page.evaluate(() => localStorage.getItem('store-cart:demo-two'));
ok(await page.isVisible('#o-account'), 'پس از سفارشِ مهمان، پیشنهادِ «ساختنِ حساب»');
await page.click('#o-account-btn');
await page.waitForFunction(() => /حساب ساخته شد/.test(document.getElementById('o-msg').textContent), null, { timeout: 10000 });
ok(await page.evaluate(() => Boolean(JSON.parse(localStorage.getItem('store-session') || 'null'))), 'نشستِ حساب در همین دستگاه');
ok((await page.evaluate(() => localStorage.getItem('store-cart:demo-two'))) === cartBefore, 'S140: ساختنِ حساب سبد را جابه‌جا، تکرار یا پاک نکرد');
const cust = await one(`SELECT name FROM store_customers WHERE phone='+93791112233'`);
ok(cust && cust.name === 'زهره', 'حساب با همان شمارهٔ تأییدشده و نام');

//  ═════ ۴) تاریخچه و خریدِ دوباره ═════
await page.goto(`${base}/store/${slug}#/account`);
await page.waitForSelector('#acc-home:not([hidden])');
await page.waitForSelector('#acc-orders li', { timeout: 10000 });
const hist = await page.textContent('#acc-orders');
ok(hist.includes(faNum(o1.order_no)) && hist.includes('برنج'), 'S138: تاریخچهٔ حساب همان سفارش را دارد', hist.slice(0, 80));
ok((await page.locator('#acc-orders li').count()) === 1, 'سفارشِ شمارهٔ دیگر در تاریخچه نیست');
await shot(page, 'account-390');
await page.locator('#acc-orders li').first().getByRole('button', { name: /خریدِ دوباره/ }).click();
await page.waitForSelector('#view-checkout:not([hidden])');
ok((await page.textContent('#reorder-note')).includes('با قیمتِ امروز'), 'WEB-12: سبد با قیمتِ امروز پر شد و گفته شد');
ok((await page.textContent('#cart-lines')).includes('برنج'), 'اقلامِ سفارشِ قبلی در سبد');

//  ═════ ۵) نشانیِ نگه‌داشته (CHK-09) با سفارشِ دوم و نشستِ حساب ═════
await page.check('input[name="fulfillment"][value="delivery"]');
await page.fill('#a-text', 'کارته‌سه، سرکِ دوم');
await page.click('#pin-btn');
await page.waitForFunction(() => /کارته‌سه/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
ok(await page.isVisible('#save-address-box'), 'واردشده ⇒ «این نشانی را نگه دار»');
await page.check('#save-address');
await page.fill('#save-label', 'خانه');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
ok(true, 'نشستِ حساب شماره را تأییدشده نگه داشت (بی کدِ تازه)');
await page.click('#submit-order');
await page.waitForFunction(() => /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 10000 });
await page.waitForFunction(async () => true);
await new Promise((r) => setTimeout(r, 400));
const saved = await one(`SELECT a.label, a.lat FROM store_customer_addresses a JOIN store_customers c ON c.id=a.customer_id WHERE c.phone='+93791112233'`);
ok(saved && saved.label === 'خانه' && Number(saved.lat) === 34.53, 'CHK-09: نشانی با نام و پین نگه داشته شد', JSON.stringify(saved));

//  ═════ ۶) بازه با ظرفیت (CHK-12) ═════
await page.goto(`${base}/store/${slug}#/`);
await page.waitForSelector('#products li');
await page.locator('#products li[data-id="tea"]').getByRole('button', { name: /افزودن/ }).click();
await page.goto(`${base}/store/${slug}#/cart`);
await page.waitForSelector('#view-checkout:not([hidden])');
ok(await page.isVisible('#saved-box'), 'نشانیِ «خانه» برای سفارشِ بعد پیدا است');
await page.check('input[name="fulfillment"][value="pickup"]');
await page.waitForSelector('#slot-box:not([hidden])', { timeout: 10000 });
const full = page.locator(`#slot option[value="${slotsBefore[0].startsAt}"]`);
ok((await full.isDisabled()) && (await full.textContent()).includes('پر است'), 'بازهٔ پرشده «پر است» و انتخاب‌نشدنی', await full.textContent());
ok((await page.textContent('#slot option[value=""]')).includes('هرچه زودتر'), '«هرچه زودتر» هم هست (allowAsap)');
await page.selectOption('#slot', String(slotsBefore[1].startsAt));
await page.evaluate(() => document.getElementById('slot-box').scrollIntoView({ block: 'center' }));
await shot(page, 'checkout-slot-390');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.click('#submit-order');
await page.waitForFunction(() => /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 10000 });
const o3 = await one(`SELECT scheduled_for FROM online_orders WHERE shop_id=$1 AND customer_phone='+93791112233' AND fulfillment='pickup'`, [shopId]);
ok(o3 && Number(o3.scheduled_for) === slotsBefore[1].startsAt, 'سرور همان بازهٔ برگزیده را نوشت', String(o3 && o3.scheduled_for));
ok((await page.textContent('#o-when')).includes('زمانِ دریافت'), 'رسید: زمانِ دریافت');

//  ═════ ۷) پینِ بیرون از محدوده (CHK-10) ═════
const out = await open(390, 844, OUTSIDE);
await out.page.locator('#products li[data-id="oil"]').getByRole('button', { name: /افزودن/ }).click();
await out.page.goto(`${base}/store/${slug}#/cart`);
await out.page.waitForSelector('#view-checkout:not([hidden])');
await out.page.check('input[name="fulfillment"][value="delivery"]');
await out.page.fill('#a-text', 'بیرون از شهر');
await out.page.click('#pin-btn');
await out.page.waitForFunction(() => /بیرون از محدوده/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
ok(true, 'CHK-10: پینِ بیرون ⇒ پیامِ «بیرون از محدوده»');
ok(await out.page.isDisabled('#submit-order'), 'و «ثبت» بسته است');
ok(await out.page.isVisible('#zone-pickup'), 'پیشنهادِ «خودم از دکان می‌گیرم»');
await out.page.evaluate(() => document.getElementById('zone-box').scrollIntoView({ block: 'center' }));
await shot(out.page, 'checkout-out-of-zone-390');
await out.page.click('#zone-pickup');
ok(await out.page.isChecked('input[name="fulfillment"][value="pickup"]'), 'یک لمس ⇒ دریافت از دکان');
ok(await out.page.isHidden('#t-fee'), 'دریافت از دکان بی هزینهٔ ارسال');

//  ═════ ۸) عرضِ میزکار و بیرون‌زدگی ═════
const wide = await open(1280, 800, INSIDE);
await wide.page.waitForSelector('#shelf-new:not([hidden]) .mini', { timeout: 10000 });
await shot(wide.page, 'home-1280');
await wide.page.goto(`${base}/store/${slug}#/p/rice`);
await wide.page.waitForFunction(() => document.getElementById('p-name').textContent.includes('برنج'), null, { timeout: 10000 });
await shot(wide.page, 'product-1280');
await wide.page.locator('#p-act').getByRole('button', { name: /افزودن/ }).click();
await wide.page.goto(`${base}/store/${slug}#/cart`);
await wide.page.waitForSelector('#view-checkout:not([hidden])');
await wide.page.check('input[name="fulfillment"][value="delivery"]');
await wide.page.click('#pin-btn');
await wide.page.waitForFunction(() => /کارته‌سه/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
await shot(wide.page, 'checkout-1280');
for (const [p, w] of [[page, 390], [wide.page, 1280]]) {
  for (const hash of ['#/', '#/p/rice', '#/cart', '#/account']) {
    await p.goto(`${base}/store/${slug}${hash}`);
    await p.waitForTimeout(300);
    const over = await p.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    ok(over <= 0, `بی بیرون‌زدگیِ افقی در ${w} (${hash})`, `${over}px`);
  }
}

if (axeSrc) {
  const axeCtx = await browser.newContext({ viewport: { width: 390, height: 844 }, bypassCSP: true });
  const ap = await axeCtx.newPage();
  const v = [];
  for (const hash of ['#/', '#/p/rice', '#/account', '#/cart']) {
    await ap.goto(`${base}/store/${slug}${hash}`);
    await ap.waitForSelector('#products li', { state: 'attached', timeout: 15000 });
    await ap.waitForTimeout(500);
    await ap.addScriptTag({ content: axeSrc });
    const outV = await ap.evaluate(async () => (await window.axe.run(document,
      { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name', 'select-name'] })).violations
      .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
    outV.forEach((x) => v.push(`${hash} ${x}`));
  }
  ok(v.length === 0, 'دسترس‌پذیری (axe-core): خانه، کالا، حساب، سبد', v.join(' | '));
  await axeCtx.close();
} else if (SKIP) {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
const jsSize = fs.statSync(path.join(import.meta.dirname, '..', 'public', 'store', 'store.js')).size;
ok(jsSize < 75 * 1024, 'جاوااسکریپتِ صفحه زیرِ ۷۵KB (۳.۱۰)', `${jsSize} بایت، فشرده‌نشده`);

await ctx.close();
await out.ctx.close();
await wide.ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین، گامِ ۲: ناحیه، بازه، حساب و صفحهٔ کالا');
process.exit(bad ? 1 : 0);
