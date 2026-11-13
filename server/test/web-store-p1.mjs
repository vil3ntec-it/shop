/**
 * ویترین با کرومیومِ واقعی: دو ردیفِ P1 ِ این دسته (صفحه: `store/acct.js`، `store/store.js`؛ سرور:
 * `lib/store-customers.js`، `lib/storefront-funnel.js`؛ قرارداد: `store-address-extra.test.js`، `storefront-funnel.test.js`).
 *
 *     TEST_DATABASE_URL=pglite:memory node test/web-store-p1.mjs
 *
 *   • CRM-09: «این نشانی را با نام نگه دار» ناحیه، رنگِ دروازه، ولایت، گذر، منزل/طبقه، شمارهٔ دوم و Plus Code را هم
 *     نگه می‌دارد؛ ثبتِ بعدی «نشانی‌های من» همهٔ کادرها را پر می‌کند و سفارش با همان‌ها ثبت می‌شود؛ «نشانیِ تازه» خالی؛
 *   • RPT-14: ⛔ پرچمِ گزارش خاموش ⇒ هیچ تپشی؛ روشن ⇒ بازدید، دیدنِ کالا و سبد هر کدام یک بار (بارِ دوم هم یک بار) و
 *     همان عدد در «گزارشِ فروشِ اینترنتی»؛
 *   • axe (wcag2a/aa) روی برگهٔ ثبت با کادرهای پرشده، بی نقضِ CSP و بی خطای جاوااسکریپت؛ بارِ نخست زیرِ بودجه.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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
process.env.RATE_STORE_CHECKOUT_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const { FIRST_LOAD } = require('./storefront-budget');

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
const email = 'owner@p1.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ P1' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
//  پیش‌فرض‌روشن‌ها (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) `false` ِ صریح ⇒ همان ویترینِ پیشین؛ سپس کلیدهای همین آزمون
for (const key of require('../src/lib/flag-defaults').DEFAULT_ON) await flag(key, false);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.web_account', 'shop.storefront.address_extra']) await flag(key, true);
const slug = 'p1-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ P1', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
let n = 0;
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++n}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('rice', { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true }),
  op('oil', { name: 'روغن', unit: 'دانه', salePrice: 250, category: 'خوراکی', active: true }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
for (const id of ['rice', 'oil']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

const feat = (await call('GET', `/api/store/${slug}`)).body.features || {};
ok(feat.addressExtra === true && feat.webAccount === true && !feat.funnel, 'پرچم‌ها: نشانیِ کامل و حساب روشن، تپشِ قیف خاموش', JSON.stringify(feat));
//  نشستِ حسابِ مشتری از همان درهای واقعی (کد ⇐ بلیت ⇐ حساب)
const phone = '0791117788';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { email: mailOf(phone) });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { email: mailOf(phone), code: vs.body.devCode });
const acct = await call('POST', `/api/store/${slug}/account`, {}, null, { 'X-Store-Ticket': vc.body.ticket });
ok(acct.status === 201 && acct.body.session, 'نشستِ حساب ساخته شد', String(acct.status));
const session = { token: acct.body.session, phone: vc.body.phone, expiresAt: acct.body.expiresAt };

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const beats = [];
const watch = (page) => {
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  page.on('request', (r) => { const m = /\/funnel\/([a-z]+)$/.exec(r.url()); if (m) beats.push(m[1]); });
  return page;
};
const line = (name, priceMinor) => ({ qtyMilli: 1000, name, unit: 'دانه', priceMinor, maxQtyMilli: null, photo: false, unavailable: false });
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript(([s, sess, c]) => {
  if (localStorage.getItem('seeded')) return;
  localStorage.setItem('seeded', '1');
  localStorage.setItem('store-session', JSON.stringify(sess));
  localStorage.setItem(`store-cart:${s}`, JSON.stringify(c));
}, [slug, session, { rice: line('برنج', 30000) }]);
const page = watch(await ctx.newPage());
let loads = 0;
const go = (hash) => page.goto(`${base}/store/${slug}?r=${++loads}${hash}`);
const cartOf = (c) => page.evaluate(([s, x]) => localStorage.setItem(`store-cart:${s}`, JSON.stringify(x)), [slug, c]);
const toCheckout = async ({ more = false } = {}) => {
  await go('#/checkout');
  await page.waitForSelector('#view-checkout:not([hidden]) #submit-order', { timeout: 15000 });
  await page.fill('#c-name', 'مشتری');
  await page.check('input[name="fulfillment"][value="delivery"]');
  await page.waitForFunction(() => { const x = document.getElementById('addr-extra'); return x && !x.hidden && document.getElementById('a-house'); }, null, { timeout: 10000 });
  //  کادرهای کمکی در «جزئیاتِ بیشتر (اختیاری)» ِ بسته‌اند؛ مشتری بازش می‌کند
  if (more) await page.click('#addr-more summary');
};
const submit = async () => {
  await page.click('#submit-order');
  await page.waitForFunction(() => /^#\/o\//.test(location.hash), null, { timeout: 15000 });
};
const lastAddress = async () => {
  const r = await one('SELECT address FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1', [shopId]);
  return typeof r.address === 'string' ? JSON.parse(r.address) : r.address;
};

//  ── RPT-14 ِ خاموش: پرچمِ گزارش خاموش ⇒ هیچ تپشی (همان صفحهٔ امروز) ──
await go('#/');
await page.waitForSelector('#products li[data-id="rice"] .p-add', { timeout: 15000 });
await page.click('#products li[data-id="oil"] .p-add');
await page.waitForTimeout(500);
ok(beats.length === 0, 'پرچمِ گزارش خاموش ⇒ هیچ تپشی فرستاده نشد', beats.join(','));
await cartOf({ rice: line('برنج', 30000) });

//  ── CRM-09: نگه داشتنِ نشانی با کلیدهای کمکی ──
const FIELDS = { '#a-text': 'کارتهٔ ۴، سرکِ دوم', '#a-landmark': 'روبه‌روی نانوایی', '#a-area': 'کارتهٔ چهار', '#a-gate': 'سبز',
  '#a-phone2': '0792223344', '#a-plus': '8FVC9G8F+6W', '#a-prov': 'کابل', '#a-street': 'کوچهٔ گل‌ها', '#a-house': 'منزلِ ۳، طبقهٔ دوم' };
await toCheckout({ more: true });
await page.waitForSelector('#ac-keep-row', { timeout: 10000 });
for (const [sel, v] of Object.entries(FIELDS)) await page.fill(sel, v);
await page.check('#ac-keep');
await page.fill('#ac-label', 'خانه');
await submit();
await page.waitForFunction(() => /«خانه»/.test((document.getElementById('toast') || {}).textContent || ''), null, { timeout: 5000 });
const saved = await one(`SELECT a.* FROM store_customer_addresses a JOIN store_customers c ON c.id = a.customer_id WHERE c.phone = '+93791117788'`);
ok(saved && saved.area === 'کارتهٔ چهار' && saved.gate_color === 'سبز' && saved.province === 'کابل' && saved.street === 'کوچهٔ گل‌ها'
  && saved.house === 'منزلِ ۳، طبقهٔ دوم' && saved.phone2 === '+93792223344' && saved.plus_code === '8FVC9G8F+6W',
  '«خانه» با ناحیه، دروازه، ولایت، گذر، منزل/طبقه، شمارهٔ دوم و Plus Code در حساب نشست', JSON.stringify(saved).slice(0, 200));

//  ثبتِ بعدی: «نشانی‌های من» ⇒ همهٔ کادرها پر؛ axe روی همین برگه
await cartOf({ oil: line('روغن', 25000) });
await toCheckout();
await page.waitForSelector('#ac-addr:not([hidden]) #ac-pick', { timeout: 10000 });
ok(await page.evaluate(() => !document.getElementById('addr-more').open), 'پیش از گزینش «جزئیاتِ بیشتر» بسته است');
await page.selectOption('#ac-pick', { label: 'خانه' });
ok(await page.evaluate(() => document.getElementById('addr-more').open), 'نشانیِ برگزیده با کلیدِ کمکی ⇒ «جزئیاتِ بیشتر» باز می‌شود تا مشتری ببیند چه پر شد');
const got = {};
for (const sel of Object.keys(FIELDS)) got[sel] = await page.inputValue(sel);
const want = { ...FIELDS, '#a-phone2': '+93792223344' };
ok(JSON.stringify(got) === JSON.stringify(want), '«نشانی‌های من» ⇒ همهٔ نُه کادر پر شد', JSON.stringify(got).slice(0, 220));
if (axeSrc) {
  await page.evaluate(axeSrc);
  const v = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa'] }, resultTypes: ['violations'] }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nn) => nn.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'axe (wcag2a/aa) روی برگهٔ ثبت با نشانیِ برگزیده: بی نقض', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
await page.selectOption('#ac-pick', { value: '' });
ok(await page.inputValue('#a-gate') === '' && await page.inputValue('#a-house') === '' && await page.inputValue('#a-phone2') === '',
  '«نشانیِ تازه» ⇒ کادرهای کمکی هم خالی');
await page.selectOption('#ac-pick', { label: 'خانه' });
await submit();
const addr = await lastAddress();
ok(addr && addr.gateColor === 'سبز' && addr.area === 'کارتهٔ چهار' && addr.house === 'منزلِ ۳، طبقهٔ دوم' && addr.phone2 === '+93792223344'
  && addr.plusCode === '8FVC9G8F+6W' && addr.province === 'کابل' && addr.street === 'کوچهٔ گل‌ها',
  'سفارش با همان کلیدهای نشانیِ برگزیده ثبت شد', JSON.stringify(addr).slice(0, 220));

//  ── RPT-14 ِ روشن: بازدید ⇐ دیدنِ کالا ⇐ سبد، هر کدام یک بار ──
await flag('shop.orders.online_report', true);
beats.length = 0;
await cartOf({});
await go('#/');
await page.waitForSelector('#products li[data-id="rice"] .p-add', { timeout: 15000 });
await page.evaluate(() => { location.hash = '#/p/rice'; });   //  همان `onOpen` ِ کارت
await page.waitForSelector('#product-sheet #ps-add:not([disabled])', { timeout: 10000 });
await page.click('#ps-add');
await page.click('#ps-add');
await page.waitForTimeout(600);
ok(JSON.stringify(beats.slice().sort()) === JSON.stringify(['cart', 'view', 'visit']), 'هر گام یک تپش (دو بار افزودن ⇒ یک «سبد»)', beats.join(','));
await go('#/');
await page.waitForSelector('#products li[data-id="rice"] .p-add', { timeout: 15000 });
await page.waitForTimeout(400);
const rep = await call('GET', '/api/shop/orders/online-report', null, T);
ok(rep.status === 200 && rep.body.visits === 1 && rep.body.top && rep.body.top.productViews === 1 && rep.body.top.cartAdds === 1 && rep.body.top.web === true,
  'گزارش: همان مرورگر بارِ دوم هم یک بازدیدکننده — بازدید ۱، دیدنِ کالا ۱، سبد ۱', JSON.stringify(rep.body && rep.body.top));

ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | ').slice(0, 300));
ok(violations.length === 0, 'بی نقضِ CSP', violations.join(' | ').slice(0, 300));
const BUDGETED = ['store/store.js', 'kit/kit.js', 'kit/boot.js'];
const first = BUDGETED.reduce((s, f) => s + fs.statSync(path.join(import.meta.dirname, '..', 'public', f)).size, 0);
ok(first < FIRST_LOAD, `بارِ نخست روی دیسک (${BUDGETED.join(' + ')}) زیرِ ${FIRST_LOAD} بایت`, `${first}`);

await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ نشانیِ کاملِ حساب (CRM-09) و بالای قیفِ ویترین (RPT-14) در مرورگر');
process.exit(bad ? 1 : 0);
