/**
 * ویترین با کرومیومِ واقعی: ناحیه‌های ارسال در فرمِ سفارش — CHK-05/06/07/10 (سرور: `storefront-delivery.js`).
 *
 *     npm run test:web-store      (پس از web-store.mjs و web-store-stock.mjs)
 *
 * دکانِ واقعی با ناحیهٔ «کارته‌سه» (هزینهٔ ۳۰، حداقلِ ۶۰۰، رایگان از ۱٬۰۰۰، هزینهٔ سبدِ کوچکِ ۱۵):
 *   • پرچمِ `shop.storefront.zones` خاموش ⇒ همان هزینهٔ ثابت و فرمِ امروز (بی کادرِ ناحیه)؛
 *   • روشن: پیش از پین «هزینه پس از ناحیه» و «ثبت» بسته؛ پینِ درون ⇒ ناحیه و هزینهٔ ۳۰ + ۱۵ (CHK-07/06)؛
 *   • پینِ بیرون ⇒ «بیرون از محدوده» با «دریافت از دکان» و «خبرم کن» (CHK-10)، و «خبرم کن» ثبت می‌شود؛
 *   • نوارِ «… تا ارسالِ رایگان» در سبد (CHK-05)؛ ثبت با ناحیهٔ برگزیده ⇒ همان هزینهٔ سرور، `source: customer`؛
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
const email = 'owner@zones.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ ناحیه' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront']) await flag(key, true);
const slug = 'zone-store';
const put = await call('PUT', '/api/shop/storefront', {
  slug, enabled: true, title: 'ویترینِ ناحیه', contactPhone: '0700000001', acceptPickup: true, deliveryFeeMinor: 5000, minOrderMinor: 0,
}, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  { op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true } },
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

const INSIDE = { latitude: 34.53, longitude: 69.17 };
const OUTSIDE = { latitude: 35.2, longitude: 70.4 };
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF', geolocation: INSIDE, permissions: ['geolocation'] });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const text = async (sel) => ((await page.textContent(sel)) || '').trim();

async function open(hash = '') {
  await page.goto(`${base}/store/${slug}${hash}`);
  await page.waitForSelector('#products li[data-id="rice"]', { timeout: 15000 });
}
async function toCheckout() {
  await page.evaluate(() => { location.hash = '#/checkout'; });
  await page.waitForSelector('#view-checkout:not([hidden])', { timeout: 10000 });
  await page.check('input[name="fulfillment"][value="delivery"]');
}

//  ── پرچم خاموش: همان امروز ──
await open();
ok((await text('#store-meta')).includes('ارسال ۵۰ افغانی'), 'خاموش ⇒ همان هزینهٔ ثابتِ ۵۰', await text('#store-meta'));
await page.locator('#products li[data-id="rice"] .p-add').first().click();
await toCheckout();
ok(await page.isHidden('#zone-box'), 'خاموش ⇒ کادرِ ناحیه نیست');
ok((await text('#t-fee')).includes('۵۰'), 'خاموش ⇒ هزینهٔ ارسالِ ثابت در برگه', await text('#t-fee'));

//  ── پرچم روشن و ناحیه ──
await flag('shop.storefront.zones', true);
await flag('shop.storefront.notify_area', true);
const set = await call('PUT', '/api/shop/storefront/delivery', { zones: [{
  name: 'کارته‌سه', kind: 'polygon', polygon: [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]],
  feeMinor: 3000, minOrderMinor: 60000, freeOverMinor: 100000, smallBasketFeeMinor: 1500,
}] }, T);
ok(set.status === 200, 'ناحیه ذخیره شد', JSON.stringify(set.body).slice(0, 160));
await open();
ok((await text('#store-meta')).includes('ارسال از ۳۰ افغانی'), 'روشن ⇒ «ارسال از ۳۰»', await text('#store-meta'));
await page.evaluate(() => { location.hash = '#/cart'; });
await page.waitForSelector('#sheet-deliv .txt', { timeout: 10000 });
ok((await text('#sheet-deliv')).includes('پس از پین'), 'سبد پیش از ناحیه: «هزینهٔ ارسال پس از پین…»', await text('#sheet-deliv'));
ok(await page.isEnabled('#cart-next'), 'ناحیهٔ ندانسته سبد را نمی‌بندد');
await toCheckout();
ok(await page.isVisible('#zone-box'), 'روشن ⇒ کادرِ ناحیه');
ok((await text('#t-fee')) === '—', 'پیش از ناحیه هزینه «—»', await text('#t-fee'));
ok((await text('#zone-msg')).includes('پین بگذارید'), '«پین بگذارید یا ناحیه را برگزینید»');

//  CHK-07/06: پینِ درون ⇒ ۳۰ + ۱۵ ِ سبدِ کوچک (۳۰۰ زیرِ حداقلِ ۶۰۰)
await page.click('#pin-btn');
await page.waitForFunction(() => /کارته‌سه/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
ok(true, 'پینِ درون ⇒ «نشانیِ شما در ناحیهٔ کارته‌سه»');
ok((await text('#t-fee')).includes('۴۵'), 'هزینهٔ ناحیه ۳۰ + هزینهٔ سبدِ کوچکِ ۱۵', await text('#t-fee'));
ok((await text('#t-total')).includes('۳۴۵'), 'جمع ۳۰۰ + ۴۵', await text('#t-total'));

//  تأییدِ شماره (برای «خبرم کن» و ثبت)
await page.fill('#c-name', 'مشتریِ آزمایشی');
await page.fill('#c-phone', '0791112255');
await page.click('#verify-send');
await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10000 });
await page.fill('#v-code', ((await text('#verify-msg')).match(/(\d{4,8})/) || [])[1] || '');
await page.click('#verify-check');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
ok(true, 'شماره تأیید شد و با ناحیهٔ پین «ثبت» باز است');

//  CHK-10: پینِ بیرون ⇒ «بیرون از محدوده»، «دریافت از دکان» و «خبرم کن»
await ctx.setGeolocation(OUTSIDE);
await page.click('#pin-btn');
await page.waitForFunction(() => /بیرون/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
ok(await page.isVisible('#zone-pickup') && await page.isVisible('#zone-notify'), 'بیرون ⇒ «دریافت از دکان» و «خبرم کن»');
ok(await page.isDisabled('#submit-order'), 'بیرون از محدوده ⇒ «ثبت» برای ارسال بسته');
await page.click('#zone-notify');
await page.waitForFunction(() => /ثبت شد/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
const req = await one('SELECT COUNT(*)::int AS n FROM storefront_area_requests WHERE shop_id = $1', [shopId]);
ok(req.n === 1, '«خبرم کن» ثبت شد', String(req.n));
await page.click('#zone-pickup');
ok(await page.isChecked('input[name="fulfillment"][value="pickup"]'), '«دریافت از دکان» روش را عوض کرد');
ok((await text('#t-total')).includes('۳۰۰'), 'دریافت از دکان ⇒ بی هزینهٔ ارسال', await text('#t-total'));

//  CHK-05: گزینشِ دستیِ ناحیه و نوارِ «تا ارسالِ رایگان» در سبد (۳ × ۳۰۰ = ۹۰۰؛ ۱۰۰ تا ۱٬۰۰۰)
await page.check('input[name="fulfillment"][value="delivery"]');
await page.selectOption('#zone-pick', { label: 'کارته‌سه — ارسال ۳۰ افغانی' });
ok((await text('#zone-msg')).includes('کارته‌سه'), 'ناحیهٔ برگزیده', await text('#zone-msg'));
await page.evaluate(() => { location.hash = '#/'; });
for (let i = 0; i < 2; i++) await page.locator('#products li[data-id="rice"] .p-add').first().click();
await page.evaluate(() => { location.hash = '#/cart'; });
await page.waitForFunction(() => /تا ارسالِ رایگان/.test(document.getElementById('sheet-deliv').textContent), null, { timeout: 10000 });
ok((await text('#sheet-deliv')).includes('۱۰۰ افغانی تا ارسالِ رایگان'), 'CHK-05: «۱۰۰ افغانی تا ارسالِ رایگان»', await text('#sheet-deliv'));
ok(await page.isVisible('#sheet-deliv [role="progressbar"]'), 'نوارِ پیشرفت تا ارسالِ رایگان');

await toCheckout();
ok((await text('#t-fee')).includes('۳۰') && !(await text('#t-fee')).includes('۴۵'), 'بالای حداقل ⇒ فقط هزینهٔ ناحیه', await text('#t-fee'));
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.fill('#a-text', 'کارته‌سه، کوچهٔ دوم');
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
const row = await one(`SELECT delivery_fee_minor, total_minor, address FROM online_orders WHERE shop_id = $1`, [shopId]);
const addr = typeof row.address === 'string' ? JSON.parse(row.address) : row.address;
ok(Number(row.delivery_fee_minor) === 3000 && Number(row.total_minor) === 93000, 'ثبت ⇒ همان هزینهٔ ناحیهٔ سرور (۳۰) و جمعِ ۹۳۰',
  `${row.delivery_fee_minor} / ${row.total_minor}`);
ok(addr && addr.zone && addr.zone.name === 'کارته‌سه' && addr.zone.source === 'customer', 'ناحیهٔ برگزیده ⇒ `source: customer`', JSON.stringify(addr && addr.zone));

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: ناحیه‌های ارسال در فرمِ سفارش');
process.exit(bad ? 1 : 0);
