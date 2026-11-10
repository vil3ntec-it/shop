/**
 * ویترین با کرومیومِ واقعی: «همین کالا در دکان‌های دیگر» روی برگهٔ کالا — S70 (§۱۹.۱۲، «دو فروشگاه با واحدهای متفاوت»).
 *
 *     npm run test:web-store      (پس از web-store.mjs)
 *
 * `market-same-units.test.js` قاعده را روی API قفل می‌کند؛ این‌جا خودِ صفحه، با سه دکانِ واقعی (ثبت‌نام، همگام‌سازیِ
 * کالا، انتشار، فهرستِ بازار) که کالایشان به یک کالای بانک وصل است:
 *   • پرچم‌های `market.directory` و `market.product_details` خاموش ⇒ نه same.js، نه واژه‌نامه‌اش، نه `#ps-same`؛
 *   • روشن ⇒ بخشِ «همین کالا در دکان‌های دیگر»: «همین دکان» بی پیوند، دکانِ دیگر با پیوند به برگهٔ همان کالا، «هر دانه»
 *     از سرور (کارتنِ ۲۴ عددی ⇒ ۱۸٫۷۵، شیشه با بستهٔ «کارتن ۱۲ عددی» ⇒ یک دانه)، «ارزان‌ترین» فقط روی پیشنهادِ ارزان‌ترِ
 *     هم‌سنج، و «کارتن» ِ بی عدد «اندازهٔ واحد روشن نیست؛ سنجیده نمی‌شود» (هرگز ارزان‌ترین، با وجودِ قیمتِ خامِ کمتر)؛
 *   • کالای بی پیوندِ بانک ⇒ بخش پنهان و خالی (پاسخِ دیررسِ کالای پیشین آن را پر نمی‌کند)؛
 *   • axe-core روشن و تاریک، انگلیسی (چپ‌به‌راست)، بی نقضِ CSP و بی خطای جاوااسکریپت.
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
process.env.RATE_MARKET_SEARCH_MAX = '100000';
process.env.RATE_MARKET_SEARCH_BURST_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';

const { createApp } = require('../src/app');
const { query, newId, now, closeDb } = require('../src/db');
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
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (scope, key, value) => call('PUT', '/api/admin/pump/live-config', { scope, key, value }, A);

//  ── سه دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
let n = 0;
async function store(label, slug, products) {
  const email = `owner-${slug}@same.local`; const password = 'Passw0rd!test';
  const s1 = await call('POST', '/api/auth/register/start', { name: label, email, password });
  const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
  const dev = `dev-${slug}`;
  const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: label, password,
    device: { deviceId: dev, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
  const T = d1.body.accessToken;
  const shopId = (await call('POST', '/api/shop', { name: `دکانِ ${label}` }, T)).body.shop.id;
  for (const key of ['shop.orders', 'shop.storefront']) await flag(shopId, key, true);
  const put = await call('PUT', '/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0,
  }, T);
  ok(put.status === 200, `ویترینِ ${label} روشن شد`, JSON.stringify(put.body).slice(0, 120));
  const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++n}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
  const pushed = await call('POST', '/api/sync/v1/push', { device_id: dev, schema_version: 2,
    ops: products.map(([id, f]) => op(id, { category: 'نوشیدنی', active: true, ...f })) }, T, { 'X-App': 'shop', 'X-Device': dev });
  ok(pushed.status === 200, `کالاهای ${label} از راهِ همگام‌سازیِ واقعی رسیدند`);
  for (const [id] of products) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);
  const listed = await call('PUT', '/api/shop/market', { listed: true, category: 'grocery' }, T);
  ok(listed.status === 200, `${label} در فهرستِ بازار`, JSON.stringify(listed.body).slice(0, 120));
  return { shopId, slug };
}
const BANK = 'bp-same-cola';
const link = (shopId, productId) => query(
  `INSERT INTO shop_product_images (shop_id, product_id, source, bank_product_id, updated_at) VALUES ($1,$2,'none',$3,$4)
   ON CONFLICT (shop_id, product_id) DO UPDATE SET bank_product_id = EXCLUDED.bank_product_id`, [shopId, productId, BANK, now()]);

const a = await store('الف', 'same-a', [['cola', { name: 'نوشابه قوطی', unit: 'عدد', salePrice: 20 }], ['tea', { name: 'چای', unit: 'بسته', salePrice: 90 }]]);
const b = await store('ب', 'same-b', [['c24', { name: 'نوشابه قوطی کارتنی', unit: 'کارتن ۲۴ عددی', salePrice: 450 }]]);
const c = await store('ج', 'same-c', [
  ['box', { name: 'نوشابه کارتن', unit: 'کارتن', salePrice: 300 }],
  ['jar', { name: 'نوشابه شیشه', unit: 'شیشه', salePrice: 25, packs: [{ name: 'کارتن ۱۲ عددی', sizeMilli: 12000, barcodes: ['6260000000123'] }] }],
]);
await link(a.shopId, 'cola');
await link(b.shopId, 'c24');
await link(c.shopId, 'box');
await link(c.shopId, 'jar');

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const asked = [];
const watch = (page) => {
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  page.on('request', (r) => { if (/\/api\/market\/same\/|\/store\/same\.js|\/i18n\/same\./.test(r.url())) asked.push(r.url()); });
};
async function axe(page, label) {
  if (!axeSrc) { console.log(`⚠️ axe-core پیدا نشد؛ ${label} سنجیده نشد`); return; }
  await page.waitForFunction(() => document.getAnimations().every((x) => x.playState !== 'running'), null, { timeout: 5000 }).catch(() => {});
  if (!(await page.evaluate(() => Boolean(window.axe)))) await page.evaluate(axeSrc);
  const out = await page.evaluate(async () => (await window.axe.run(document.getElementById('product-sheet'),
    { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name', 'list', 'listitem', 'aria-valid-attr-value', 'aria-required-attr', 'duplicate-id-aria', 'duplicate-id', 'nested-interactive', 'heading-order'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(out.length === 0, `دسترس‌پذیری (axe-core): ${label}`, out.join(' | '));
}
async function sheet(page, slug, id, lang = '') {
  await page.goto('about:blank');   //  همان نشانی با همان # بارِ دوباره نمی‌شود
  await page.goto(`${base}/store/${slug}${lang ? `?lang=${lang}` : ''}#/p/${id}`);
  await page.waitForSelector('#product-sheet:not([hidden]) #ps-name', { timeout: 15000 });
  //  جزئیات (product.js) و — اگر هست — بخشِ «همین کالا» پس از نامِ کالا
  await page.waitForFunction(() => window.StoreProduct, null, { timeout: 10000 });
}
const rows = (page) => page.$$eval('#ps-same-list > li', (ls) => ls.map((li) => ({
  text: li.textContent.replace(/\s+/g, ' ').trim(), link: li.querySelector('a') ? li.querySelector('a').getAttribute('href') : null,
  cheapest: Boolean(li.querySelector('.pill.ok')),
})));

const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
const page = await ctx.newPage(); watch(page);

//  ── خاموش ──
await sheet(page, a.slug, 'cola');
await page.waitForTimeout(400);
ok(asked.length === 0 && !(await page.$('script[src="/store/same.js"]')) && !(await page.$('#ps-same')),
  'پرچم‌های بازار خاموش ⇒ نه same.js، نه واژه‌نامه و درخواستِ «همین کالا»، نه #ps-same', asked.join(' '));

//  ── روشن ──
await flag('all', 'market.directory', true);
await flag('all', 'market.product_details', true);
await sheet(page, a.slug, 'cola');
await page.waitForSelector('#ps-same:not([hidden]) #ps-same-list li', { timeout: 10000 });
ok((await page.textContent('#ps-same-t')).trim() === 'همین کالا در دکان‌های دیگر', 'عنوانِ بخش');
const list = await rows(page);
ok(list.length === 4, 'چهار پیشنهاد (همین دکان + سه پیشنهادِ دیگر)', JSON.stringify(list));
const here = list.find((r) => r.text.includes('همین دکان'));
ok(Boolean(here) && here.link === null && here.text.includes('هر دانه') && !here.cheapest, '«همین دکان» بی پیوند، با قیمتِ هر دانه', JSON.stringify(here));
const rb = list.find((r) => r.text.includes('ویترینِ ب'));
ok(Boolean(rb) && rb.link === '/store/same-b#/p/c24' && rb.text.includes('هر دانه: ۱۸٫۷۵ افغانی') && rb.cheapest,
  'کارتنِ ۲۴ عددی: پیوند به برگهٔ همان کالا، «هر دانه: ۱۸٫۷۵ افغانی» و «ارزان‌ترین»', JSON.stringify(rb));
const box = list.find((r) => r.text.includes('/ کارتن') && !r.text.includes('۲۴'));
ok(Boolean(box) && box.text.includes('اندازهٔ واحد روشن نیست؛ سنجیده نمی‌شود') && !box.cheapest && !box.text.includes('هر دانه'),
  '«کارتن» ِ بی عدد: «سنجیده نمی‌شود» و هرگز ارزان‌ترین (با قیمتِ خامِ کمتر از کارتنِ ۲۴ عددی)', JSON.stringify(box));
const jar = list.find((r) => r.text.includes('/ شیشه'));
ok(Boolean(jar) && jar.text.includes('هر دانه: ۲۵ افغانی') && !jar.cheapest, 'شیشه با بستهٔ «کارتن ۱۲ عددی» ⇒ هر دانه ۲۵', JSON.stringify(jar));
ok(list.filter((r) => r.cheapest).length === 1, 'فقط یک «ارزان‌ترین»');
ok((await page.textContent('#ps-same')).includes('کمترین قیمتِ هر دانه میانِ ۳ پیشنهاد: ۱۸٫۷۵ افغانی'), 'خلاصهٔ گروهِ هم‌سنج');
ok(!(await page.textContent('#ps-same')).includes('6260000000123'), 'بارکدِ بسته در صفحه نیست');
await axe(page, 'برگهٔ کالا با «همین کالا»');

//  کالای بی پیوندِ بانک ⇒ بخش پنهان و خالی
await page.evaluate(() => { location.hash = '#/p/tea'; });
await page.waitForFunction(() => document.getElementById('ps-name').textContent === 'چای', null, { timeout: 10000 });
await page.waitForTimeout(500);
ok(await page.$eval('#ps-same', (e) => e.hidden && !e.children.length), 'کالای بی پیوندِ بانک ⇒ «همین کالا» پنهان و خالی');
//  برگشت به نوشابه ⇒ دوباره
await page.evaluate(() => { location.hash = '#/p/cola'; });
await page.waitForSelector('#ps-same:not([hidden]) #ps-same-list li', { timeout: 10000 });
ok((await rows(page)).length === 4, 'برگشت به کالای پیوسته ⇒ همان چهار پیشنهاد');

//  ── تاریک ──
const dark = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'fa-AF', colorScheme: 'dark' });
const dp = await dark.newPage(); watch(dp);
await sheet(dp, b.slug, 'c24');
await dp.waitForSelector('#ps-same:not([hidden]) #ps-same-list li', { timeout: 10000 });
await axe(dp, 'برگهٔ کالا با «همین کالا» (تاریک)');
const darkRows = await rows(dp);
ok(darkRows.some((r) => r.text.includes('همین دکان') && r.cheapest), 'از دکانِ ب: «همین دکان» همان ارزان‌ترین', JSON.stringify(darkRows));
await dark.close();

//  ── انگلیسی (چپ‌به‌راست) ──
const en = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'en-GB' });
const ep = await en.newPage(); watch(ep);
await sheet(ep, a.slug, 'cola', 'en');
await ep.waitForSelector('#ps-same:not([hidden]) #ps-same-list li', { timeout: 10000 });
const enText = await ep.textContent('#ps-same');
const enLang = await ep.evaluate(() => document.documentElement.lang);
const enWant = ['Same item in other shops', 'This shop', 'Per piece: 18.75', 'Lowest per piece among 3 offers', 'Cheapest', 'Unit size is not clear; not compared'];
ok(enLang === 'en' && enWant.every((w) => enText.includes(w)) && !/همین|سنجیده|هر دانه|ارزان/.test(enText),
  'انگلیسی: متنِ بخش ترجمه شده (نامِ دکان و واحد دادهٔ دکان‌اند)', enText.slice(0, 200));
await axe(ep, 'برگهٔ کالا با «همین کالا» (انگلیسی)');
await en.close();

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «همین کالا در دکان‌های دیگر» با سنجشِ هم‌سنج');
process.exit(bad ? 1 : 0);
