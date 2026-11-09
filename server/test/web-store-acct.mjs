/**
 * ویترین با کرومیومِ واقعی: حسابِ مشتری در وب — CHK-01/03/09، WEB-14 (صفحه: `store/acct.js`؛ سرور:
 * `lib/store-customers.js`، `lib/store-cart.js`؛ قرارداد: `storefront-web-account.test.js`).
 *
 *     npm run test:web-store      (پس از web-store-lines.mjs)
 *
 *   • ⛔ پرچمِ `shop.storefront.web_account` خاموش ⇒ acct.js خواسته نمی‌شود و صفحه همان امروز؛
 *   • CHK-01: پس از ثبتِ سفارشِ مهمان «حساب بسازید؟» زیرِ «سفارش ثبت شد»؛ بلیتِ کهنه ⇒ همان‌جا کد و تأیید ⇒ نشست؛
 *     «نه، ممنون» پیشنهاد را برای سفارشِ بعد نمی‌آورد؛ بی بلیت، فقط با نشست، شماره «تأییدشده» است و سفارش ثبت می‌شود؛
 *   • CHK-09: «این نشانی را با نام نگه دار» ⇒ نشانی (با پین) در حساب؛ ثبتِ بعدی «نشانی‌های من» ⇒ همان متن، نشانه و پین؛ حذف؛
 *   • CHK-03/WEB-14: سبدِ این دستگاه روی سرور (فقط شناسه، مقدار، یادداشت)؛ بی اینترنت ⇒ در صف، هیچ چیز به سرور نمی‌رسد؛
 *     برگشتِ شبکه ⇒ همان صف نوشته می‌شود؛ صفِ ماندهٔ پس از بستنِ صفحه با بارِ بعد؛ دستگاهِ دوم پس از ورود همان سبد را می‌خواند؛
 *     «خروج از حساب» ⇒ نشست باطل؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
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
process.env.RATE_STORE_CHECKOUT_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const config = require('../src/config');
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
const email = 'owner@acct.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ حساب' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.cart']) await flag(key, true);
const slug = 'acct-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ حساب', contactPhone: '0700000001',
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

const phone = '0791114455';
const ticket = async (p = phone) => {
  const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone: p });
  const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone: p, code: vs.body.devCode });
  return { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt };
};
const line = (name, priceMinor, qtyMilli = 1000) => ({ qtyMilli, name, unit: 'دانه', priceMinor, maxQtyMilli: null, photo: false, unavailable: false });
const serverCart = async () => {
  const r = await one(`SELECT c.items, c.version FROM store_customer_carts c JOIN store_customers s ON s.id = c.customer_id
                        WHERE c.shop_id = $1 AND s.phone = '+93791114455'`, [shopId]);
  if (!r) return null;
  const items = typeof r.items === 'string' ? JSON.parse(r.items) : r.items;
  return { version: Number(r.version), items: Object.fromEntries(items.map((i) => [i.productId, i.qtyMilli])) };
};

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const acctLoads = [];
const watch = (page) => {
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  page.on('request', (r) => { if (/\/store\/acct\.js/.test(r.url())) acctLoads.push(r.url()); });
  return page;
};
const seed = async (ctx, t, cart) => ctx.addInitScript(([s, tk, c]) => {
  //  یک بار در هر «مرورگر» (نه هر برگه): برگهٔ تازهٔ همان مرورگر سبدِ ماندهٔ خودش را دارد
  if (localStorage.getItem('seeded')) return;
  localStorage.setItem('seeded', '1');
  if (tk) localStorage.setItem('store-ticket', JSON.stringify(tk));
  if (c) localStorage.setItem(`store-cart:${s}`, JSON.stringify(c));
}, [slug, t, cart]);

let loads = 0;
const go = (page, hash) => page.goto(`${base}/store/${slug}?r=${++loads}${hash}`);
const placeOrder = async (page, { delivery = false } = {}) => {
  await go(page, '#/checkout');
  await page.waitForSelector('#view-checkout:not([hidden]) #submit-order', { timeout: 15000 });
  await page.fill('#c-name', 'مشتری');
  if (delivery) await page.check('input[name="fulfillment"][value="delivery"]');
  else await page.check('input[name="fulfillment"][value="pickup"]');
};
const submit = async (page) => {
  await page.click('#submit-order');
  await page.waitForFunction(() => /^#\/o\//.test(location.hash), null, { timeout: 15000 });
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
};

//  ── ⛔ پرچمِ خاموش ⇒ همان صفحهٔ امروز ──
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await seed(ctx, await ticket(), { rice: line('برنج', 30000) });
const page = watch(await ctx.newPage());
await placeOrder(page);
await submit(page);
await page.waitForTimeout(600);
ok(acctLoads.length === 0 && await page.$('#ac-offer') === null, 'پرچمِ خاموش ⇒ acct.js خواسته نشد و «حساب بسازید؟» نیست', acctLoads.join(' '));

//  ── CHK-01: پیشنهادِ حساب پس از ثبت؛ بلیتِ کهنه ⇒ کد همان‌جا ──
await flag('shop.storefront.web_account', true);
await page.evaluate((s) => localStorage.setItem(`store-cart:${s}`, JSON.stringify({ rice: { qtyMilli: 2000, name: 'برنج', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false } })), slug);
await placeOrder(page);
await submit(page);
await page.waitForSelector('#ac-offer', { timeout: 10000 });
const offerText = await page.textContent('#ac-offer');
const placedFirst = await page.evaluate(() => { const p = document.getElementById('o-placed'); return p.nextElementSibling && p.nextElementSibling.id; });
ok(/حساب بسازید/.test(offerText) && /0791114455/.test(offerText) && placedFirst === 'ac-offer', '«حساب بسازید؟» درست زیرِ «سفارش ثبت شد»، با همان شماره', offerText);
const keepAge = config.storefront.accountTicketMaxMs;
config.storefront.accountTicketMaxMs = 1;   //  بلیتِ همین سفارش برای ساختنِ حساب دیگر تازه نیست
await page.click('#ac-offer button:has-text("ساختنِ حساب")');
await page.waitForFunction(() => /دوباره|همین حالا با کد/.test(document.getElementById('ac-offer').textContent), null, { timeout: 5000 });
config.storefront.accountTicketMaxMs = keepAge;
ok(await page.isVisible('#ac-offer input[type="tel"]') && await page.evaluate(() => !localStorage.getItem('store-session')),
  'بلیتِ کهنه ⇒ هنوز نشستی نیست؛ شماره و «فرستادنِ کد» همان‌جا');
await page.click('#ac-offer button:has-text("فرستادنِ کد")');
await page.waitForFunction(() => /کدِ آزمایشی: \d+/.test(document.getElementById('ac-offer').textContent), null, { timeout: 5000 });
const code = /کدِ آزمایشی: (\d+)/.exec(await page.textContent('#ac-offer'))[1];
await page.fill('#ac-offer input[autocomplete="one-time-code"]', code);
await page.click('#ac-offer button:has-text("تأیید")');
await page.waitForFunction(() => /حسابِ شما ساخته شد/.test(document.getElementById('ac-offer').textContent), null, { timeout: 5000 });
const sess = await page.evaluate(() => JSON.parse(localStorage.getItem('store-session')));
const cust = await one(`SELECT name FROM store_customers WHERE phone = '+93791114455'`);
ok(sess && /^sc1\./.test(sess.token) && sess.phone === '+93791114455' && sess.expiresAt > Date.now() + 100 * 864e5 && cust && cust.name === 'مشتری',
  'کد ⇒ حساب ساخته شد: نشستِ بلند روی همین مرورگر، نامِ ثبت در حساب', JSON.stringify({ sess: sess && sess.phone, cust }));

//  «نه، ممنون» (مرورگرِ دیگر، همان شماره): پیشنهاد برای سفارشِ بعد نمی‌آید
const ctxNo = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await seed(ctxNo, await ticket('0791114466'), { oil: line('روغن', 25000) });
const pNo = watch(await ctxNo.newPage());
await placeOrder(pNo);
await pNo.fill('#c-phone', '0791114466');
await submit(pNo);
await pNo.waitForSelector('#ac-offer', { timeout: 10000 });
await pNo.click('#ac-offer button:has-text("نه، ممنون")');
await pNo.evaluate((s) => localStorage.setItem(`store-cart:${s}`, JSON.stringify({ oil: { qtyMilli: 1000, name: 'روغن', unit: 'دانه', priceMinor: 25000, maxQtyMilli: null, photo: false, unavailable: false } })), slug);
await placeOrder(pNo);
await pNo.fill('#c-phone', '0791114466');
await submit(pNo);
await pNo.waitForTimeout(800);
ok(await pNo.$('#ac-offer') === null && (await one(`SELECT COUNT(*)::int AS n FROM store_customers WHERE phone = '+93791114466'`)).n === 0,
  '«نه، ممنون» ⇒ سفارشِ بعد بی پیشنهاد و هیچ حسابی ساخته نشد');
await ctxNo.close();

//  ── CHK-09: نشانیِ نام‌دار؛ فقط با نشست (بلیت پاک) ──
await page.evaluate((s) => {
  localStorage.removeItem('store-ticket');
  localStorage.setItem(`store-cart:${s}`, JSON.stringify({ rice: { qtyMilli: 1000, name: 'برنج', unit: 'دانه', priceMinor: 30000, maxQtyMilli: null, photo: false, unavailable: false } }));
}, slug);
await placeOrder(page, { delivery: true });
await page.waitForSelector('#ac-keep-row', { timeout: 10000 });
ok(await page.textContent('#verify-msg').then((x) => /تأیید شد|تأییدشده/.test(x)) && await page.inputValue('#c-phone') === '0791114455',
  'بی بلیت، فقط با نشست: شماره پر و «تأییدشده»', await page.textContent('#verify-msg'));
ok(await page.isHidden('#ac-addr'), 'هنوز نشانیِ نام‌داری نیست ⇒ گزینش پنهان');
await page.fill('#a-text', 'کارتهٔ ۴، سرکِ دوم');
await page.fill('#a-landmark', 'روبه‌روی نانوایی');
await page.check('#ac-keep');
await page.fill('#ac-label', 'خانه');
await submit(page);
await page.waitForFunction(() => /«خانه»/.test((document.getElementById('toast') || {}).textContent || ''), null, { timeout: 5000 });
const saved = await many(`SELECT a.label, a.text, a.landmark FROM store_customer_addresses a JOIN store_customers s ON s.id = a.customer_id WHERE s.phone = '+93791114455'`);
ok(saved.length === 1 && saved[0].label === 'خانه' && saved[0].text === 'کارتهٔ ۴، سرکِ دوم' && saved[0].landmark === 'روبه‌روی نانوایی',
  'سفارشِ ارسالی با نشست ثبت شد و «خانه» در حساب نگه داشته شد', JSON.stringify(saved));
//  پینِ همان نشانی (برنامهٔ دیگری از همین حساب) ⇒ برگهٔ ثبت پین را هم می‌نشاند
await call('PUT', `/api/store/${slug}/account/addresses/${(await one(`SELECT id FROM store_customer_addresses`)).id}`,
  { label: 'خانه', text: 'کارتهٔ ۴، سرکِ دوم', landmark: 'روبه‌روی نانوایی', lat: 34.5553, lng: 69.2075 }, null, { 'X-Store-Session': sess.token });
await page.evaluate((s) => localStorage.setItem(`store-cart:${s}`, JSON.stringify({ oil: { qtyMilli: 1000, name: 'روغن', unit: 'دانه', priceMinor: 25000, maxQtyMilli: null, photo: false, unavailable: false } })), slug);
await placeOrder(page, { delivery: true });
await page.waitForSelector('#ac-addr:not([hidden]) #ac-pick', { timeout: 10000 });
await page.fill('#a-text', ''); await page.fill('#a-landmark', '');
await page.selectOption('#ac-pick', { label: 'خانه' });
ok(await page.inputValue('#a-text') === 'کارتهٔ ۴، سرکِ دوم' && await page.inputValue('#a-landmark') === 'روبه‌روی نانوایی'
  && /پینِ همین نشانی/.test(await page.textContent('#pin-text')) && await page.isHidden('#ac-keep-row'),
  '«نشانی‌های من» ⇒ همان متن، نشانه و پین؛ «نگه دار» پنهان');
await submit(page);
const last = await one(`SELECT address FROM online_orders WHERE shop_id = $1 ORDER BY created_at DESC LIMIT 1`, [shopId]);
const addr = typeof last.address === 'string' ? JSON.parse(last.address) : last.address;
ok(addr && addr.text === 'کارتهٔ ۴، سرکِ دوم' && Math.abs(Number(addr.lat) - 34.5553) < 1e-6 && Math.abs(Number(addr.lng) - 69.2075) < 1e-6,
  'سفارش با نشانیِ برگزیده و پینِ آن ثبت شد', JSON.stringify(addr).slice(0, 160));
await page.evaluate((s) => localStorage.setItem(`store-cart:${s}`, JSON.stringify({ oil: { qtyMilli: 1000, name: 'روغن', unit: 'دانه', priceMinor: 25000, maxQtyMilli: null, photo: false, unavailable: false } })), slug);
await placeOrder(page, { delivery: true });
await page.waitForSelector('#ac-addr:not([hidden]) #ac-pick', { timeout: 10000 });
await page.selectOption('#ac-pick', { label: 'خانه' });
await page.click('#ac-addr button:has-text("حذفِ این نشانی")');
await page.waitForFunction(() => document.getElementById('ac-addr').hidden, null, { timeout: 5000 });
ok((await one(`SELECT COUNT(*)::int AS n FROM store_customer_addresses`)).n === 0 && await page.inputValue('#a-text') === '', 'حذف ⇒ از حساب پاک و فرم خالی');

//  ── CHK-03/WEB-14: سبدِ حساب، صفِ بی‌اینترنت و دستگاهِ دوم ──
const settle = () => page.waitForTimeout(1200);
const addCard = async (p, id) => { await p.click(`#products li[data-id="${id}"] .p-add`); };
await go(page, '#/');
await page.waitForSelector('#products li[data-id="rice"] .p-add', { timeout: 15000 });
await settle();
const c0 = await serverCart();
ok(c0 && Object.keys(c0.items).length === 1 && c0.items.oil === 1000, 'سبدِ این دستگاه (روغن) پس از ورود روی سرور', JSON.stringify(c0));
await addCard(page, 'rice');
await settle();
const c1 = await serverCart();
ok(c1.items.rice === 1000 && c1.items.oil === 1000 && c1.version > c0.version, 'افزودن ⇒ همان سبد روی سرور با نسخهٔ تازه', JSON.stringify(c1));
await ctx.setOffline(true);
await addCard(page, 'rice');
await settle();
const c2 = await serverCart();
ok(c2.version === c1.version && c2.items.rice === 1000 && await page.isVisible('#offline, .offline'), 'بی اینترنت ⇒ تغییر در صف، سرور همان', JSON.stringify(c2));
await ctx.setOffline(false);
await settle();
const c3 = await serverCart();
ok(c3.items.rice === 2000 && c3.version === c2.version + 1, 'برگشتِ شبکه ⇒ صف نوشته شد (یک بار)', JSON.stringify(c3));
//  صفِ ماندهٔ پس از بستنِ صفحه
await ctx.setOffline(true);
await addCard(page, 'oil');
await page.waitForTimeout(300);
await page.close();
await ctx.setOffline(false);
const page2 = watch(await ctx.newPage());
await go(page2, '#/');
await page2.waitForSelector('#products li[data-id="rice"]', { timeout: 15000 });
await page2.waitForTimeout(1500);
const c4 = await serverCart();
ok(c4.items.oil === 2000 && c4.items.rice === 2000, 'صفِ ماندهٔ صفحهٔ بسته‌شده با بارِ بعد نوشته شد', JSON.stringify(c4));

//  دستگاهِ دوم: ورود از «سفارش‌های من» ⇒ همان سبد
const ctx2 = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
const p2 = watch(await ctx2.newPage());
await go(p2, '#/orders');
await p2.waitForSelector('#ac-mine button:has-text("ورود به حساب")', { timeout: 15000 });
await p2.click('#ac-mine button:has-text("ورود به حساب")');
await p2.fill('#ac-mine input[type="tel"]', '0791114455');
await p2.click('#ac-mine button:has-text("فرستادنِ کد")');
await p2.waitForFunction(() => /کدِ آزمایشی: \d+/.test(document.getElementById('ac-mine').textContent), null, { timeout: 5000 });
const code2 = /کدِ آزمایشی: (\d+)/.exec(await p2.textContent('#ac-mine'))[1];
await p2.fill('#ac-mine input[autocomplete="one-time-code"]', code2);
await p2.click('#ac-mine button:has-text("تأیید")');
await p2.waitForFunction(() => /خروج از حساب/.test(document.getElementById('ac-mine').textContent), null, { timeout: 5000 });
await p2.waitForFunction((s) => { try { return Object.keys(JSON.parse(localStorage.getItem(`store-cart:${s}`)) || {}).length === 2; } catch (e) { return false; } }, slug, { timeout: 5000 });
const cart2 = await p2.evaluate((s) => JSON.parse(localStorage.getItem(`store-cart:${s}`)), slug);
ok(cart2.rice.qtyMilli === 2000 && cart2.oil.qtyMilli === 2000 && cart2.rice.priceMinor === 30000 && /0791114455/.test(await p2.textContent('#ac-mine')),
  'دستگاهِ دوم پس از ورود همان سبد را از سرور خواند (قیمتِ امروزِ سرور)', JSON.stringify(cart2));

//  «خروج از حساب» ⇒ نشست باطل (نسخهٔ سبدِ حساب از دستگاه پاک: web-store-cart-sync.mjs)
const token2 = await p2.evaluate(() => JSON.parse(localStorage.getItem('store-session')).token);
await p2.click('#ac-mine button:has-text("خروج از حساب")');
await p2.waitForFunction(() => /ورود به حساب/.test(document.getElementById('ac-mine').textContent), null, { timeout: 5000 });
const dead = await call('GET', `/api/store/${slug}/account`, null, null, { 'X-Store-Session': token2 });
ok(dead.status === 401 && await p2.evaluate(() => !localStorage.getItem('store-session')), 'خروج ⇒ نشستِ سرور باطل و از مرورگر پاک', String(dead.status));
await ctx2.close();

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: حسابِ مشتری در وب — پیشنهاد پس از ثبت، نشانی‌های نام‌دار و سبدِ هم‌گام');
process.exit(bad ? 1 : 0);
