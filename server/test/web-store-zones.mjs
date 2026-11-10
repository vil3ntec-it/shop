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
  { op_id: `op-oil-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'oil', type: 'insert',
    fields: { name: 'روغن', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true } },
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const id of ['rice', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

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
/**
 *  «افزودن» ِ برنج در کاتالوگ: نوارِ ثابتِ پایین (`nav.bnav`) دکمهٔ لبهٔ پایین را می‌پوشاند و scrollIntoView ِ
 *  Playwright فقط تا لبه می‌آورد؛ وقتی بنرها دیر بار شوند جای کاشی جابه‌جا می‌شود ⇒ گاه «intercepts pointer events»
 *  (CI ِ #831، #837).
 */
async function addRice() {
  const b = page.locator('#products li[data-id="rice"] .p-add').first();
  await b.waitFor({ state: 'visible' });
  //  کلیکِ DOM: این آزمون سبد و ناحیه را می‌سنجد، نه هدف‌گیریِ اشاره‌گر؛ با جابه‌جاییِ کاتالوگ (بنرها، نوارِ سبد)
  //  کلیکِ Playwright حتی پس از وسط‌آوردن گاه «outside of the viewport» ماند (CI ِ #837)
  await b.evaluate((el) => { el.scrollIntoView({ block: 'center' }); el.click(); });
}
await addRice();
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
//  WEB-24: بنری که فقط برای همین ناحیه است، تا ناحیه دانسته نیست دیده نمی‌شود
await flag('shop.storefront.banners', true);
{
  const zid = (set.body.zones || (set.body.delivery || {}).zones || [])[0].id;
  const H = 3600 * 1000;
  const bn = await call('PUT', '/api/shop/storefront/banners', { banners: [{ title: 'ویژهٔ کارته‌سه', startAt: Date.now() - H, endAt: Date.now() + H, zoneIds: [zid] }] }, T);
  ok(bn.status === 200, 'بنرِ ناحیه نوشته شد', JSON.stringify(bn.body).slice(0, 120));
}
await open();
ok(await page.isHidden('#banners'), 'WEB-24: ناحیهٔ ندانسته ⇒ بنرِ ناحیه نیست');
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
await page.waitForSelector('#banners:not([hidden]) .banner', { timeout: 10000 });
ok((await text('#banners-list')).includes('ویژهٔ کارته‌سه'), 'WEB-24: با برگشت به کاتالوگ پس از گزینشِ ناحیه ⇒ بنرِ همان ناحیه');
for (let i = 0; i < 2; i++) await addRice();
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

//  CHK-24: صفحهٔ پیگیری ⇐ «دستورِ تحویل» تا پیش از ارسال (پرچمِ `shop.orders.delivery_prefs`)؛ پس از حرکت بسته
await flag('shop.orders.delivery_prefs', true);
const first = await one('SELECT id FROM online_orders WHERE shop_id = $1 ORDER BY created_at LIMIT 1', [shopId]);
const firstToken = require('../src/lib/storefront').tokenOf(shopId, first.id);
//  همان نشانیِ صفحهٔ کنونی است (پس از ثبت) ⇒ بارِ دوباره تا پرچمِ تازه دیده شود
await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(firstToken)}`);
await page.reload();
await page.waitForSelector('#o-prefs:not([hidden])', { timeout: 15000 });
ok(!(await page.isChecked('#op-call')) && await page.isEnabled('#op-save'), 'CHK-24: دستورِ تحویل پیش از ارسال ویرایش‌پذیر است');
await page.check('#op-call');
await page.fill('#op-leave', 'دربان');
await page.click('#op-save');
await page.waitForFunction(() => /ثبت شد/.test(document.getElementById('op-msg').textContent), null, { timeout: 10000 });
const dl = await one('SELECT delivery FROM online_orders WHERE id = $1', [first.id]);
const dj = typeof dl.delivery === 'string' ? JSON.parse(dl.delivery) : dl.delivery;
ok(dj.callBeforeArrival === true && dj.leaveWith === 'دربان', 'دستورِ تازه همان سفارشِ سرور است', JSON.stringify(dj));
for (const to of ['accepted', 'picking', 'packed', 'out_for_delivery']) {
  const tr = await call('POST', `/api/shop/orders/${first.id}/transition`, { to }, T);
  if (tr.status !== 200) ok(false, `گذارِ ${to}`, JSON.stringify(tr.body));
}
await page.reload();
await page.waitForSelector('#o-prefs:not([hidden])', { timeout: 15000 });
await page.waitForFunction(() => /بیرون رفته/.test(document.getElementById('op-msg').textContent), null, { timeout: 10000 });
ok(await page.isDisabled('#op-save') && await page.isChecked('#op-call'), 'پس از حرکت: همان دستور دیده می‌شود ولی بسته است');

//  CHK-17/S32: فیِ دو قلم عوض شد ولی جمع همان ماند (۳۰۰ + ۳۰۰ ⇐ ۳۵۰ + ۲۵۰) ⇒ پیش از ثبت گفته می‌شود
await open();
for (const id of ['rice', 'oil']) await page.locator(`#products li[data-id="${id}"] .p-add`).first().click();
await toCheckout();
await page.check('input[name="fulfillment"][value="pickup"]');
ok((await text('#t-total')).includes('۶۰۰'), 'سبدِ تازه ۶۰۰', await text('#t-total'));
const re = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  { op_id: `op-rice-up-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'update', fields: { salePrice: 350 } },
  { op_id: `op-oil-dn-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'oil', type: 'update', fields: { salePrice: 250 } },
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(re.status === 200, 'قیمتِ دو کالا در دفترِ دکان عوض شد (جمع همان)');
const before = (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n;
await page.click('#submit-order');
await page.waitForFunction(() => /برنج/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 10000 });
ok((await text('#checkout-msg')).includes('۳۵۰') && (await text('#checkout-msg')).includes('۲۵۰'), 'CHK-17: تغییرِ فیِ هر دو قلم پیش از ثبت دیده شد', await text('#checkout-msg'));
ok((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n === before, 'بی پذیرش هیچ سفارشی ثبت نشد');
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
ok((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n === before + 1, 'پس از پذیرشِ برگهٔ تازه، ثبت شد');

//  MKT-01/09، CHK-04: تخفیفِ خودکارِ زمان‌دار با شمارشِ معکوس، کدِ تخفیف، و `promo_code_rejected` (پرچمِ `shop.storefront.promos`)
const rule = (id, data) => call('PUT', `/api/shop/promos/rules/${id}`, { rule: { name: id, stack: 'best', active: true, ...data } }, T);
const kabulDate = (ms) => new Date(ms + 4.5 * 3600e3).toISOString().slice(0, 10);
await open();
await addRice();
await toCheckout();
await page.check('input[name="fulfillment"][value="pickup"]');
ok(await page.isHidden('#promo-box') && await page.isHidden('#t-disc'), 'پرچمِ تخفیف خاموش ⇒ همان برگهٔ امروز');
for (const k of ['shop.promo_uses', 'shop.storefront.promos']) await flag(k, true);
const r1 = await rule('online10', { kind: 'percent', bp: 1000, channels: ['online'], window: { to: kabulDate(Date.now() + 2 * 864e5) } });
const r2 = await rule('vip5', { kind: 'percent', bp: 500, channels: ['online'], code: 'VIP5', stack: 'combine' });
ok(r1.status === 200 && r2.status === 200, 'دو قاعدهٔ اینترنتی (خودکارِ ۱۰٪ تا دو روزِ دیگر و کدِ VIP5)', JSON.stringify(r1.body).slice(0, 120));
await open();
await toCheckout();
await page.check('input[name="fulfillment"][value="pickup"]');
await page.waitForSelector('#promo-box:not([hidden]) #promo-auto li', { timeout: 10000 });
ok(/online10 — ۲ روز تا پایان/.test(await text('#promo-auto')), 'MKT-09: تخفیفِ زمان‌دار با «۲ روز تا پایان»', await text('#promo-auto'));
await page.waitForSelector('#t-disc:not([hidden])', { timeout: 10000 });
ok((await text('#t-disc')).includes('۳۵') && (await text('#t-total')).includes('۳۱۵'), 'CHK-04: تخفیفِ ۳۵ در جمع و مبلغِ نهایی ۳۱۵', `${await text('#t-disc')} / ${await text('#t-total')}`);
await page.fill('#promo-code', 'NOPE99');
await page.click('#promo-apply');
await page.waitForFunction(() => /در این ویترین نیست/.test(document.getElementById('promo-msg').textContent), null, { timeout: 10000 });
ok(true, 'کدِ ناشناخته ⇒ «در این ویترین نیست»، سبد بی کد');
await page.fill('#promo-code', 'vip5');
await page.click('#promo-apply');
await page.waitForFunction(() => /VIP5|vip5/.test(document.getElementById('promo-msg').textContent), null, { timeout: 10000 });
const withCode = await text('#t-disc');
ok(withCode !== '−۳۵ افغانی' && await page.isVisible('#promo-clear'), 'MKT-01: کد اعمال شد و تخفیف بیشتر شد', withCode);
//  کد پس از نمایش از کار افتاد ⇒ ثبت ۴۰۹ `promo_code_rejected` و پیامِ روشن، نه سفارشِ بی‌صدا
await rule('vip5', { kind: 'percent', bp: 500, channels: ['online'], code: 'VIP5', stack: 'combine', active: false });
const n0 = (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n;
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.click('#submit-order');
await page.waitForFunction(() => /کدِ تخفیف/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 10000 });
ok((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n === n0, '`promo_code_rejected` ⇒ پیام و بی سفارش', await text('#checkout-msg'));
await page.waitForFunction(() => document.getElementById('t-disc').textContent.includes('۳۵') && !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
const po = await one('SELECT subtotal_minor, discount_minor, total_minor FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
ok(Number(po.discount_minor) === 3500 && Number(po.total_minor) === 31500, 'ثبت ⇒ همان تخفیفِ دیده‌شده (۳۵) و جمعِ ۳۱۵ در سرور', JSON.stringify(po));

//  MKT-02 (پرچمِ `shop.storefront.promo_guard`): اولین خریدِ ۲۰٪ روی همان ۱۰٪ ِ خودکار — ردیفِ «تخفیف (اولین خرید: …)» در جمع،
//  شناسهٔ تصادفیِ همین مرورگر در ثبت (در پایگاه فقط چکیده‌اش)، و خریدِ بعدیِ همان شماره بی آن
await flag('shop.storefront.promo_guard', true);
const pg = await call('PUT', '/api/shop/storefront/promo-guard', { firstOrder: { enabled: true, kind: 'percent', bp: 2000 } }, T);
ok(pg.status === 200, 'تنظیمِ اولین خرید (۲۰٪)', JSON.stringify(pg.body).slice(0, 120));
//  «مشتریِ تازه» با همان شمارهٔ تأییدشده: سفارش‌های پیشینِ این آزمون به شمارهٔ دیگری
await query("UPDATE online_orders SET customer_phone = '+93700000099' WHERE shop_id = $1", [shopId]);
await open();
await addRice();
await toCheckout();
await page.check('input[name="fulfillment"][value="pickup"]');
await page.waitForFunction(() => /اولین خرید/.test(document.getElementById('t-disc-label').textContent) && !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
ok((await text('#t-disc-label')).includes('۶۳') && (await text('#t-disc')).includes('۹۸') && (await text('#t-total')).includes('۲۵۲'),
  'MKT-02: «تخفیف (اولین خرید: ۶۳)» و جمعِ تخفیف ۹۸ و مبلغِ ۲۵۲', `${await text('#t-disc-label')} / ${await text('#t-disc')} / ${await text('#t-total')}`);
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
const fo = await one('SELECT discount_minor, total_minor FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
const dev = await page.evaluate(() => JSON.parse(localStorage.getItem('store-device') || 'null'));
const claim = await one("SELECT device_key, discount_minor FROM storefront_promo_claims WHERE shop_id = $1 AND kind = 'first_order'", [shopId]);
ok(Number(fo.discount_minor) === 9800 && Number(fo.total_minor) === 25200 && /^[0-9a-f]{32}$/.test(dev || '')
  && claim && Number(claim.discount_minor) === 6300 && /^[0-9a-f]{32}$/.test(claim.device_key) && claim.device_key !== dev,
'ثبت ⇒ همان ۹۸ و ۲۵۲ در سرور؛ اولین خرید با چکیدهٔ دستگاه، نه خودِ شناسه', JSON.stringify({ fo, claim }));
await open();
await addRice();
await toCheckout();
await page.check('input[name="fulfillment"][value="pickup"]');
await page.waitForFunction(() => document.getElementById('t-disc').textContent.includes('۳۵') && !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
ok(!(await text('#t-disc-label')).includes('اولین'), 'خریدِ دومِ همان شماره ⇒ فقط همان ۱۰٪ ِ خودکار', await text('#t-disc-label'));

//  CHK-08: شمارهٔ دوم و Plus Code (پرچمِ `shop.storefront.address_extra`)؛ نادرست ⇒ پیامِ سرور و بی سفارش
await open();
await addRice();
await toCheckout();
ok(await page.$eval('#addr-extra', (e) => e.hidden), 'پرچمِ نشانیِ بیشتر خاموش ⇒ همان فرم');
await flag('shop.storefront.address_extra', true);
await open();
await toCheckout();
//  ممیزیِ ویترین (CHK-08): اختیاری‌ها زیرِ «جزئیاتِ بیشتر» ِ بسته‌اند ⇒ پیدا فقط پین، نشانی و نشانه
ok(!(await page.isVisible('#a-phone2')) && await page.isVisible('#a-text') && await page.isVisible('#a-landmark') && await page.isVisible('#pin-btn'),
  'فرمِ کوتاه: «شمارهٔ دوم» زیرِ «جزئیاتِ بیشتر»، نشانی و نشانه و پین پیدا');
await page.click('#addr-more > summary');
ok(await page.isVisible('#addr-extra'), 'روشن ⇒ «شمارهٔ دوم» و «کدِ مکانِ گوگل» در «جزئیاتِ بیشتر»');
await page.selectOption('#zone-pick', { label: 'کارته‌سه — ارسال ۳۰ افغانی' });
await page.fill('#a-text', 'کارته‌سه، کوچهٔ سوم');
await page.fill('#a-phone2', '۰۷۹۹ ۸۸۸ ۷۷۷');
await page.fill('#a-plus', 'ABC+12');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
const m0 = (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n;
await page.click('#submit-order');
await page.waitForFunction(() => /Plus Code/.test(document.getElementById('checkout-msg').textContent), null, { timeout: 10000 });   //  `err.bad_plus_code`
ok((await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n === m0, 'Plus Code ِ نادرست ⇒ پیام و بی سفارش', await text('#checkout-msg'));
await page.fill('#a-plus', '9g8f+6w کابل');
//  CHK-08 (§۴.۴): ولایت، گذر یا کوچه، منزل و طبقه (کدِ پستی نه، LOC-10)
await page.fill('#a-prov', 'کابل');
await page.fill('#a-street', 'گذرِ سوم');
await page.fill('#a-house', 'منزلِ ۱۲، طبقهٔ دوم');
ok(!(await page.$('#a-postal')), 'کادرِ کدِ پستی نیست (LOC-10)');
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
const ax = await one('SELECT address FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
const aj = typeof ax.address === 'string' ? JSON.parse(ax.address) : ax.address;
ok(aj.phone2 === '+93799888777' && aj.plusCode === '9G8F+6W کابل', 'CHK-08: شمارهٔ دوم و Plus Code در نشانیِ سفارش', JSON.stringify(aj));
ok(aj.province === 'کابل' && aj.street === 'گذرِ سوم' && aj.house === 'منزلِ ۱۲، طبقهٔ دوم' && !('postalCode' in aj),
  'CHK-08: ولایت، گذر و منزل و طبقه در نشانیِ سفارش', JSON.stringify(aj));

//  CHK-08 (همان پرچم): پین از پیوند یا مختصاتِ برنامهٔ نقشه — کسی که برای جای دیگری سفارش می‌دهد «موقعیتِ فعلی» ندارد.
//  `store/pin-text.js` فقط با کلیک؛ پیوندِ کوتاه ⇒ پیام و بی پین؛ Enter برگه را نمی‌فرستد؛ ناحیه را سرور از همان پین می‌گوید
//  (بیرون ⇒ «ثبت» بسته)؛ همان پین (شش رقم) در نشانیِ سفارش، بی `zoneId` ِ دستی
await open();
await addRice();
await toCheckout();
ok(await page.isVisible('#pin-paste') && await page.isVisible('#pin-use') && await page.isVisible('#pin-btn'),
  'CHK-08: «پین از برنامهٔ نقشه» کنارِ «موقعیتِ فعلیِ من»، بیرون از «جزئیاتِ بیشتر»');
ok(!(await page.$('script[src="/store/pin-text.js"]')), 'pin-text.js پیش از کلیک بار نشد');
await page.fill('#pin-paste', 'https://maps.app.goo.gl/AbCdEf');
await page.click('#pin-use');
await page.waitForFunction(() => /پین پیدا نشد/.test(document.getElementById('pin-text').textContent), null, { timeout: 10000 });
ok((await text('#pin-btn')).includes('موقعیتِ فعلیِ من'), 'پیوندِ کوتاه ⇒ «از این نوشته پین پیدا نشد…» و هیچ پینی گذاشته نشد', await text('#pin-btn'));
await page.fill('#pin-paste', 'geo:35.2,70.4');
await page.press('#pin-paste', 'Enter');
await page.waitForFunction(() => /بیرون/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
ok(await page.isHidden('#view-order') && await page.isDisabled('#submit-order'), 'Enter برگه را نفرستاد؛ پینِ چسباندهٔ بیرون ⇒ «بیرون از محدوده» و «ثبت» بسته');
await page.fill('#pin-paste', 'https://www.google.com/maps/place/X/@34.5312345,69.1711111,17z');
await page.click('#pin-use');
await page.waitForFunction(() => /کارته‌سه/.test(document.getElementById('zone-msg').textContent), null, { timeout: 10000 });
ok((await text('#pin-text')).includes('۳۴٫۵۳۱۲') && (await text('#pin-btn')).includes('دوباره'), 'پینِ گوگل ⇒ ناحیه از سرور و پین با رقمِ فارسی', await text('#pin-text'));
if (axeSrc) {
  await page.evaluate(axeSrc);
  const v = await page.evaluate(async () => (await window.axe.run(document.getElementById('address-box'),
    { runOnly: ['color-contrast', 'button-name', 'label', 'aria-allowed-attr', 'nested-interactive'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core): نشانی با «پین از برنامهٔ نقشه»', v.join(' | '));
} else if (process.env.SKIP_BROWSER_TESTS !== '1') ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
await page.fill('#a-text', 'خانهٔ پدر، کارته‌سه');
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
{
  const r = await one('SELECT address FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
  const a = typeof r.address === 'string' ? JSON.parse(r.address) : r.address;
  ok(a.lat === 34.531235 && a.lng === 69.171111 && a.zone && a.zone.name === 'کارته‌سه' && a.zone.source === 'pin',
    'CHK-08: پینِ چسبانده (شش رقم، گردِ سرور) در نشانیِ سفارش و ناحیه از پین', JSON.stringify(a));
}


//  CHK-07/12: زمانِ تحویل و هزینهٔ بازه — بی بازه ⇒ همان فرم؛ بازهٔ «ارسال» با هزینهٔ ۲۰ و بی «هرچه زودتر» ⇒ تا بازه
//  برگزیده نشده «ثبت» بسته، هزینه روی هزینهٔ ناحیه، و همان بازه و هزینه در سفارشِ سرور (جمعِ دیده‌شده = جمعِ سرور)
await open();
await addRice();
await toCheckout();
ok(await page.isHidden('#slot-box'), 'بی بازهٔ زمانی ⇒ همان فرم، بی «زمانِ تحویل»');
const sr = await call('PUT', '/api/shop/storefront/delivery', { allowAsap: false, slotRules: [
  { fulfillment: 'delivery', weekdays: [0, 1, 2, 3, 4, 5, 6], startMin: 1380, endMin: 1439, capacity: 5, cutoffMin: 0, feeMinor: 2000 },
] }, T);
ok(sr.status === 200, 'بازهٔ ارسال با هزینهٔ ۲۰ ذخیره شد', JSON.stringify(sr.body).slice(0, 120));
await open();
await toCheckout();
await page.selectOption('#zone-pick', { label: 'کارته‌سه — ارسال ۳۰ افغانی' });
await page.fill('#c-name', 'مشتریِ آزمایشی');
await page.fill('#a-text', 'کارته‌سه، کوچهٔ چهارم');
await page.waitForSelector('#slot-box:not([hidden]) #slot-pick option[value]:not([value=""])', { state: 'attached', timeout: 10000 });
const fee0 = await text('#t-fee');
ok(await page.isDisabled('#submit-order') && (await text('#slot-pick option:first-child')).includes('برگزینید'),
  'بی «هرچه زودتر» و بی بازه ⇒ «ثبت» بسته', await text('#slot-pick option:first-child'));
const slotVal = await page.locator('#slot-pick option:not([disabled])').nth(1).getAttribute('value');
const slotOpt = ((await page.locator(`#slot-pick option[value="${slotVal}"]`).textContent()) || '').trim();
ok(slotOpt.includes('+۲۰'), 'گزینهٔ بازه هزینهٔ اضافه را می‌گوید');
await page.selectOption('#slot-pick', slotVal);
await page.waitForFunction((f) => document.getElementById('t-fee').textContent.trim() !== f, fee0, { timeout: 5000 });
ok(true, 'هزینهٔ ارسال با بازه عوض شد', `${fee0} ⇐ ${await text('#t-fee')}`);
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
{
  const o = await one('SELECT delivery, delivery_fee_minor FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
  const dj = typeof o.delivery === 'string' ? JSON.parse(o.delivery) : o.delivery;
  ok(dj && dj.slot && dj.slot.startsAt === Number(slotVal) && dj.slot.feeMinor === 2000 && Number(o.delivery_fee_minor) >= 2000,
    'CHK-07: همان بازه و هزینهٔ ۲۰ ِ آن در سفارش، با جمعی که وب نشان داد', JSON.stringify(dj && dj.slot) + ' / ' + o.delivery_fee_minor);
}

//  CHK-20 (§۱۹.۲ بندِ ۸): صفحهٔ تأیید — دکانِ مسئول، ناحیهٔ ارسال و همان بازهٔ برگزیده (روز و ساعتِ گزینهٔ فرم، بی هزینه)؛
//  «کپیِ نشانیِ پیگیری» = همین ویترین + `#/o/<توکن>` (بی ?…)؛ کلیپ‌بورد رد کرد ⇒ همان نشانی در کادرِ فقط‌خواندنی
{
  const f = (k) => text(`#o-facts dd[data-f="${k}"]`);
  const had = await page.waitForSelector('#o-facts:not([hidden]) dd[data-f="of.when.delivery"]', { timeout: 15000 }).then(() => true, () => false);
  ok(had, 'CHK-20: صفحهٔ تأیید خلاصهٔ «دکان، ناحیه، زمانِ تحویل» دارد');
  //  رسیدنِ واژه‌نامهٔ خلاصه عنوانِ رسید را به «سفارش» برنمی‌گرداند (ریشهٔ `K.part` فقط گره‌های خودش)
  ok(/^سفارشِ شمارهٔ [۰-۹]+$/.test(await text('#o-title')), 'CHK-20: پس از خلاصه عنوانِ رسید همان «سفارشِ شمارهٔ …»', await text('#o-title'));
  const when = had ? await f('of.when.delivery') : '';
  ok(had && when.includes('۲۳:۰۰–۲۳:۵۹') && slotOpt.startsWith(when) && !when.includes('+'),
    'CHK-20: همان بازهٔ برگزیده (روز و ساعتِ گزینهٔ فرم، بی هزینه) روی صفحهٔ تأیید', `${when} / ${slotOpt}`);
  ok(had && await f('of.shop') === 'ویترینِ ناحیه' && await f('of.zone') === 'کارته‌سه',
    'CHK-20: دکانِ مسئول و ناحیهٔ ارسال روی صفحهٔ تأیید', had ? `${await f('of.shop')} / ${await f('of.zone')}` : '');
  const u = new URL(page.url());
  const want = `${u.origin}${u.pathname}${u.hash}`;
  await ctx.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: base });
  const btn = await page.waitForSelector('#o-link-copy', { timeout: 5000 }).then(() => true, () => false);
  if (btn) await page.click('#o-link-copy');
  const copied = btn && await page.waitForFunction(() => /کپی شد/.test(document.getElementById('o-link-msg').textContent), null, { timeout: 5000 }).then(() => true, () => false);
  const clip = copied ? await page.evaluate(() => navigator.clipboard.readText()) : '';
  ok(copied && clip === want && /^#\/o\/[^?]+$/.test(u.hash) && clip.startsWith(`${base}/store/${slug}#/o/`),
    'CHK-20: «کپیِ نشانیِ پیگیری» همین ویترین + #/o/<توکن> را در کلیپ‌بورد گذاشت', `${clip} / ${want}`);
  ok(btn && /لغو/.test(await text('#o-link-hint')), 'CHK-20: کنارِ دکمه هشدار که دارندهٔ نشانی سفارش را می‌بیند و لغو می‌کند', btn ? await text('#o-link-hint') : '');
  //  کلیپ‌بورد رد کرد (اجازه، زمینهٔ ناامن) ⇒ همان نشانی برگزیده در کادرِ فقط‌خواندنی و پیامِ «کپی نشد»
  await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { configurable: true, get: () => ({ writeText: () => Promise.reject(new Error('denied')) }) }));
  if (btn) await page.click('#o-link-copy');
  const shownBox = btn && await page.waitForSelector('#o-link-row:not([hidden]) #o-link-url', { timeout: 5000 }).then(() => true, () => false);
  ok(shownBox && await page.inputValue('#o-link-url') === want && /کپی نشد/.test(await text('#o-link-msg'))
    && await page.evaluate(() => document.activeElement && document.activeElement.id === 'o-link-url'),
    'CHK-20: کلیپ‌بورد نشد ⇒ همان نشانی در کادرِ فقط‌خواندنی، برگزیده، با پیامِ «کپی نشد»', shownBox ? await page.inputValue('#o-link-url') : '');
  if (axeSrc && had) {
    await page.evaluate(axeSrc);
    const v = await page.evaluate(async () => (await window.axe.run({ include: [['#o-facts'], ['#o-link']] },
      { runOnly: ['color-contrast', 'button-name', 'label', 'definition-list', 'dlitem', 'aria-allowed-attr'] })).violations
      .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
    ok(v.length === 0, 'دسترس‌پذیری (axe-core): خلاصه و «کپیِ نشانیِ پیگیری» ِ صفحهٔ تأیید', v.join(' | '));
  }
}

//  CHK-14: «با کدام اسکناس…» — پرچمِ خاموش ⇒ نیست؛ روشن ⇒ فقط اسکناس‌هایی که سرور می‌پذیرد، و همان در سفارش
await flag('shop.orders.delivery_prefs', false);   //  CHK-24 ِ بالا روشنش کرده بود
await open();
await addRice();
await toCheckout();
ok(await page.isHidden('#tender-row'), 'CHK-14 خاموش: بی «با کدام اسکناس»');
await flag('shop.orders.delivery_prefs', true);
await open();
await toCheckout();
await page.selectOption('#zone-pick', { label: 'کارته‌سه — ارسال ۳۰ افغانی' });
await page.fill('#c-name', 'مشتریِ آزمایشی');
await page.fill('#a-text', 'کارته‌سه، کوچهٔ پنجم');
await page.waitForSelector('#slot-pick option[value]:not([value=""])', { state: 'attached', timeout: 10000 });
await page.selectOption('#slot-pick', await page.locator('#slot-pick option:not([disabled])').nth(1).getAttribute('value'));
await page.waitForSelector('#tender-row:not([hidden]) #d-tender option[value]:not([value=""])', { state: 'attached', timeout: 10000 });
const tv = await page.locator('#d-tender option').nth(1).getAttribute('value');
ok((await page.locator('#d-tender option').nth(1).textContent()).includes('پول خرد'), 'گزینهٔ اسکناس پول خرد را می‌گوید', await page.locator('#d-tender option').nth(1).textContent());
await page.selectOption('#d-tender', tv);
await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10000 });
//  CHK-24 ِ پایین همین‌ها را می‌خواهد: پس از رسیدنِ تخفیفِ سرور (تا آن‌گاه «ثبت» بسته است و جمع بی تخفیف)
const ckOpts = await page.$$eval('#d-tender option', (os) => os.map((x) => x.value));
await page.click('#submit-order');
await page.waitForSelector('#view-order:not([hidden])', { timeout: 15000 });
{
  const o = await one('SELECT delivery, total_minor FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
  const dj = typeof o.delivery === 'string' ? JSON.parse(o.delivery) : o.delivery;
  const total = Number(o.total_minor);
  ok(dj.cashTenderMinor === Number(tv) && dj.cashTenderMinor > total && dj.cashTenderMinor - total < 100000,
    'CHK-14: همان اسکناس در سفارش؛ بیش از جمع و پول خرد کمتر از یک اسکناسِ ۱۰۰۰', `${dj.cashTenderMinor} / ${total}`);
}

//  CHK-24 (وب، پس از ثبت): «با کدام اسکناس…» در «دستورِ تحویل» ِ پیگیری، همزادِ `PrefsRules` ِ برنامهٔ خرید — همان گزینه‌های
//  فرمِ ثبت (`K.tenders`) با اسکناسِ همین سفارش برگزیده؛ «پول خرد لازم نیست» ⇒ null ِ صریح؛ اسکناسِ درستِ بیرون از فهرست
//  می‌ماند؛ اسکناسی که جمعِ امروز دیگر نمی‌پذیرد برداشته می‌شود (⛔ هرگز ردِ سرور)؛ ۴۰۹ و ردِ اسکناس ⇒ پیگیری از نو و پیامِ
//  همان؛ axe؛ و کارت برای سفارشِ ردشده/لغوشده پنهان (پیشتر «از دکان بیرون رفته» نشان می‌داد)
{
  const last = await one('SELECT id, total_minor FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
  const total = Number(last.total_minor);
  const lastToken = require('../src/lib/storefront').tokenOf(shopId, last.id);
  const dlv = async () => { const r = await one('SELECT delivery FROM online_orders WHERE id = $1', [last.id]); return typeof r.delivery === 'string' ? JSON.parse(r.delivery) : r.delivery; };
  const opts = () => page.$$eval('#op-tn option', (os) => os.map((x) => x.value));
  const msgHas = (re) => page.waitForFunction((src) => new RegExp(src).test(document.getElementById('op-msg').textContent), re, { timeout: 10000 }).then(() => true, () => false);
  const fresh = async () => { await page.reload(); await page.waitForSelector('#o-prefs:not([hidden])', { timeout: 15000 }); };
  //  ⛔ ردشده: کارت پنهان (همان `PrefsRules.shown`)، نه «از دکان بیرون رفته» — سفارشِ ارسالِ بازِ دیگر (CHK-07 ِ بالا)
  const other0 = await one(`SELECT id FROM online_orders WHERE shop_id = $1 AND fulfillment = 'delivery' AND status = 'new' AND id <> $2
                             ORDER BY created_at DESC LIMIT 1`, [shopId, last.id]);
  const rj = await call('POST', `/api/shop/orders/${other0.id}/transition`, { to: 'rejected', reason: 'busy' }, T);
  ok(rj.status === 200, 'دکان سفارشِ ارسالِ دیگری را نپذیرفت', JSON.stringify(rj.body).slice(0, 120));
  await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(require('../src/lib/storefront').tokenOf(shopId, other0.id))}`);
  await page.reload();
  await page.waitForFunction(() => /نپذیرفت/.test(document.getElementById('o-status').textContent), null, { timeout: 15000 });
  ok(await page.isHidden('#o-prefs'), 'سفارشِ ردشده ⇒ بی کارتِ «دستورِ تحویل»', await text('#op-msg'));

  await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(lastToken)}`);
  await fresh();
  ok(await page.isVisible('#op-tn'), 'CHK-24 وب: «با کدام اسکناس…» در دستورِ تحویلِ سفارشِ ارسال + نقد');
  ok(JSON.stringify(await opts()) === JSON.stringify(ckOpts), 'همان گزینه‌های فرمِ ثبت («پول خرد لازم نیست» + K.tenders)', `${await opts()} / ${ckOpts}`);
  ok(await page.inputValue('#op-tn').catch(() => '') === tv, 'اسکناسِ همین سفارش برگزیده است', tv);
  await page.selectOption('#op-tn', '', { timeout: 5000 }).catch(() => {});
  await page.click('#op-save');
  let done = await msgHas('ثبت شد');
  ok(done && (await dlv()).cashTenderMinor === undefined, '«پول خرد لازم نیست» ⇒ null ِ صریح، اسکناس برداشته شد', JSON.stringify(await dlv()));
  const other = ckOpts.filter((v) => v && v !== tv).pop() || tv;
  await page.selectOption('#op-tn', other, { timeout: 5000 }).catch(() => {});
  await page.click('#op-save');
  done = await msgHas('ثبت شد');
  ok(done && (await dlv()).cashTenderMinor === Number(other) && await page.isVisible('#op-tn') && await page.inputValue('#op-tn') === other,
    'اسکناسِ تازه همان در سفارشِ سرور؛ کارت با نسخهٔ تازه همان را نشان می‌دهد', `${other} / ${JSON.stringify(await dlv())}`);

  //  دکان مبلغِ دیگری نوشت (همان جمع، درست ولی بیرون از گزینه‌ها) ⇒ در فهرست و برگزیده؛ ذخیرهٔ کادرِ دیگر پاکش نمی‌کند
  const sx = await call('POST', `/api/shop/orders/${last.id}/delivery`, { delivery: { cashTenderMinor: total } }, T);
  ok(sx.status === 200, 'دکان اسکناسِ مشتری را «همان جمع» نوشت', JSON.stringify(sx.body).slice(0, 120));
  await fresh();
  ok((await opts()).includes(String(total)) && await page.inputValue('#op-tn').catch(() => '') === String(total), 'اسکناسِ کنونیِ درست بیرون از فهرست می‌ماند و برگزیده است', String(await opts()));
  await page.check('#op-call');
  await page.click('#op-save');
  done = await msgHas('ثبت شد');
  const d1 = await dlv();
  ok(done && d1.cashTenderMinor === total && d1.callBeforeArrival === true, 'ذخیرهٔ «زنگ بزنید» اسکناسِ دکان را نگه داشت', JSON.stringify(d1));

  //  اسکناسی که جمعِ امروز دیگر نمی‌پذیرد (پول خرد ≥ ۱۰۰۰) ⇒ نه گزینه، نه برگزیده؛ ذخیره null ِ صریح می‌فرستد، نه ردِ سرور
  const bad = { ...d1, cashTenderMinor: total + 150000 };
  await query('UPDATE online_orders SET delivery = $2::jsonb, version = version + 1 WHERE id = $1', [last.id, JSON.stringify(bad)]);
  await fresh();
  ok(!(await opts()).includes(String(total + 150000)) && await page.inputValue('#op-tn').catch(() => 'x') === '', 'اسکناسِ نادرست برای جمعِ امروز ⇒ «پول خرد لازم نیست»', String(await opts()));
  await page.fill('#op-leave', 'همسایه');
  await page.click('#op-save');
  done = await msgHas('ثبت شد');
  const d2 = await dlv();
  ok(done && d2.cashTenderMinor === undefined && d2.leaveWith === 'همسایه', 'ذخیره بی ردِ cash_tender_excess، اسکناسِ نادرست برداشته شد', JSON.stringify(d2));

  //  ۴۰۹: دکان همین حالا عوض کرد ⇒ پیامِ «جای دیگری عوض شد» و کادرها با نسخهٔ تازه
  await fresh();
  const sv = await call('POST', `/api/shop/orders/${last.id}/delivery`, { delivery: { leaveWith: 'نگهبان' } }, T);
  ok(sv.status === 200, 'دکان «تحویل به» را عوض کرد', JSON.stringify(sv.body).slice(0, 120));
  await page.fill('#op-leave', 'پدرم');
  await page.click('#op-save');
  ok(await msgHas('جای دیگری عوض شد'), '۴۰۹ ِ version_conflict ⇒ پیامِ همان', await text('#op-msg'));
  await page.waitForFunction(() => document.getElementById('op-leave').value === 'نگهبان', null, { timeout: 10000 }).catch(() => {});
  ok(await page.inputValue('#op-leave') === 'نگهبان' && await page.isEnabled('#op-save'), 'پیگیری از نو: کادرها با نسخهٔ تازهٔ دکان و «ثبت» باز', await page.inputValue('#op-leave'));
  ok((await dlv()).leaveWith === 'نگهبان', 'نوشتهٔ دکان رونویسی نشد');

  //  ردِ اسکناس از سرور (جمع جای دیگری عوض شد؛ این‌جا بدنه در راه دست‌کاری می‌شود) ⇒ پیامِ خودِ صفحه، نه خطای خام
  await page.route('**/delivery', async (route) => {
    const b = JSON.parse(route.request().postData() || '{}');
    b.delivery = { ...(b.delivery || {}), cashTenderMinor: total - 100 };
    await route.continue({ postData: JSON.stringify(b) });
  });
  await page.selectOption('#op-tn', ckOpts[1], { timeout: 5000 }).catch(() => {});
  await page.click('#op-save');
  ok(await msgHas('اسکناس') && !(await text('#op-msg')).includes('کمتر است'), 'cash_tender_short ⇒ پیامِ «با کدام اسکناس…» ِ پیگیری', await text('#op-msg'));
  await page.unroute('**/delivery');
  ok((await dlv()).cashTenderMinor === undefined, 'اسکناسِ ردشده ننشست');

  //  دسترس‌پذیری ِ کارت با «با کدام اسکناس…»
  if (axeSrc) {
    await page.evaluate(axeSrc);   //  بی bypassCSP: سنجهٔ CSP ِ همین برگه سرِ جای خود (مثلِ web-store-cart-sync)
    const v = await page.evaluate(async () => (await window.axe.run(document.getElementById('o-prefs'),
      { runOnly: ['color-contrast', 'button-name', 'label', 'select-name', 'aria-allowed-attr', 'nested-interactive'] })).violations
      .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
    ok(v.length === 0, 'دسترس‌پذیری (axe-core): دستورِ تحویل با «با کدام اسکناس…»', v.join(' | '));
  } else if (process.env.SKIP_BROWSER_TESTS === '1') console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
  else ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');

  //  ⛔ لغوشده: کارت پنهان
  const cx = await call('POST', `/api/store/${slug}/orders/${encodeURIComponent(lastToken)}/cancel`, {});
  ok(cx.status === 200, 'مشتری همین سفارش را لغو کرد', JSON.stringify(cx.body).slice(0, 120));
  await page.reload();
  await page.waitForFunction(() => /لغو کردید/.test(document.getElementById('o-status').textContent), null, { timeout: 15000 });
  ok(await page.isHidden('#o-prefs'), 'سفارشِ لغوشده ⇒ بی کارتِ «دستورِ تحویل»', await text('#op-msg'));
}

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: فرمِ سفارش — ناحیه، فیِ قلم، دستورِ تحویل و تخفیف');
process.exit(bad ? 1 : 0);
