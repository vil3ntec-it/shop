/**
 * ویترین با کرومیومِ واقعی: گالریِ عکس‌های بیشترِ کالا روی برگهٔ کالا — PRD-17/27 (تصمیمِ ۴-۲۲ ِ صاحب‌کار، ۱۴۰۵/۰۸/۲۰).
 *
 *     npm run test:web-store      (پس از web-store-same.mjs)
 *
 * `storefront-gallery.test.js` قاعدهٔ سرور را قفل می‌کند؛ این‌جا خودِ صفحه با یک دکانِ واقعی (ثبت‌نام، همگام‌سازیِ کالا،
 * انتشار، بانکِ تصاویر با عکسِ ویترین و دو عکسِ تکمیلی):
 *   • پرچمِ `shop.storefront.gallery` خاموش ⇒ نه gallery.js، نه واژه‌نامه‌اش، نه درخواستِ گالری، نه `#ps-gal`؛
 *   • روشن ⇒ سه دکمه (اصلی + دو عکس)، هیچ عکسِ گالری پیش از زدن بار نمی‌شود؛ زدن ⇒ همان عکس با `aria-current`؛
 *     برگشت به «۱» ⇒ عکسِ اصلیِ امروز؛ کشیدن روی عکس (راست‌به‌چپ: به راست ⇒ بعدی)؛ کالای بی گالری ⇒ بخش پنهان؛
 *   • axe-core روشن و تاریک، انگلیسی، بی نقضِ CSP و بی خطای جاوااسکریپت.
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
const { query, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const F = require('./image-fixtures');

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
const raw = async (method, p, buf, t) => {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'image/jpeg', Authorization: `Bearer ${t}` }, body: buf });
  return { status: r.status, body: await r.json().catch(() => null) };
};
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (scope, key, value) => call('PUT', '/api/admin/pump/live-config', { scope, key, value }, A);

//  ── یک دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const slug = 'gal-a';
const email = 'owner-gal@gallery.local'; const password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'گالری', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const dev = 'dev-gal';
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'گالری', password,
  device: { deviceId: dev, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ گالری' }, T)).body.shop.id;
for (const key of ['shop.orders', 'shop.storefront', 'shop.image_bank']) await flag(shopId, key, true);
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ گالری', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین روشن شد', JSON.stringify(put.body).slice(0, 120));
let n = 0;
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++n}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: dev, schema_version: 2, ops: [
  op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی', active: true }),
  op('tea', { name: 'چای', unit: 'بسته', salePrice: 90, category: 'خوراکی', active: true }),
] }, T, { 'X-App': 'shop', 'X-Device': dev });
ok(pushed.status === 200, 'کالاها از راهِ همگام‌سازیِ واقعی رسیدند');
for (const id of ['rice', 'tea']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

let seed = 4400;
async function image(pid, role) {
  const u = await raw('POST', `/api/shop/images?product_id=${pid}&role=${role}`, F.jpeg(800, 600, { seed: ++seed }), T);
  ok(u.status === 201, `عکسِ ${role} بارگذاری شد`, JSON.stringify(u.body).slice(0, 100));
  for (const [size, w, h] of [['display', 600, 450], ['thumb', 300, 225]]) {
    const d = await raw('PUT', `/api/shop/images/${u.body.id}/v/1/${size}`, F.jpeg(w, h, { seed: ++seed }), T);
    ok(d.status === 200, `نسخهٔ ${size} ِ ${role}`);
  }
  return u.body.id;
}
const main = await image('rice', 'promo');
const sel = await call('POST', '/api/shop/images/select', { product_id: 'rice', source: 'shop', image_id: main, version: 1 }, T);
ok(sel.status === 200, 'عکسِ ویترینِ برنج', JSON.stringify(sel.body).slice(0, 100));
await image('rice', 'back');
await image('rice', 'label');

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const asked = [];
const watch = (page) => {
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  page.on('request', (r) => { if (/\/gallery(\/|$)|\/store\/gallery\.js|\/i18n\/gallery\./.test(r.url())) asked.push(r.url()); });
};
async function axe(page, label) {
  if (!axeSrc) { console.log(`⚠️ axe-core پیدا نشد؛ ${label} سنجیده نشد`); return; }
  await page.waitForFunction(() => document.getAnimations().every((x) => x.playState !== 'running'), null, { timeout: 5000 }).catch(() => {});
  if (!(await page.evaluate(() => Boolean(window.axe)))) await page.evaluate(axeSrc);
  const out = await page.evaluate(async () => (await window.axe.run(document.getElementById('product-sheet'),
    { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name', 'list', 'listitem', 'aria-valid-attr-value', 'aria-required-attr', 'duplicate-id-aria', 'duplicate-id', 'nested-interactive', 'landmark-unique', 'target-size'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(out.length === 0, `دسترس‌پذیری (axe-core): ${label}`, out.join(' | '));
}
async function sheet(page, id, lang = '') {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${slug}${lang ? `?lang=${lang}` : ''}#/p/${id}`);
  await page.waitForSelector('#product-sheet:not([hidden]) #ps-name', { timeout: 15000 });
  await page.waitForFunction(() => window.StoreProduct, null, { timeout: 10000 });
}
const imgSrc = (page) => page.$eval('#ps-img img', (i) => i.getAttribute('src')).catch(() => '');
const current = (page) => page.$$eval('#ps-gal .dot', (ds) => ds.findIndex((d) => d.getAttribute('aria-current') === 'true'));

const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
const page = await ctx.newPage(); watch(page);

//  ── خاموش ──
await sheet(page, 'rice');
await page.waitForTimeout(500);
ok(asked.length === 0 && !(await page.$('script[src="/store/gallery.js"]')) && !(await page.$('#ps-gal')),
  'پرچم خاموش ⇒ نه gallery.js، نه واژه‌نامه و درخواستِ گالری، نه #ps-gal', asked.join(' '));
ok(/\/products\/rice\/photo/.test(await imgSrc(page)), 'عکسِ اصلیِ امروز سرِ جایش');

//  ── روشن ──
await flag(shopId, 'shop.storefront.gallery', true);
asked.length = 0;
await sheet(page, 'rice');
await page.waitForSelector('#ps-gal:not([hidden]) .dot', { timeout: 10000 });
const dots = await page.$$eval('#ps-gal .dot', (ds) => ds.map((d) => ({ text: d.textContent, label: d.getAttribute('aria-label') })));
ok(dots.length === 3 && dots[0].text === '۱' && dots[2].text === '۳', 'سه دکمه: اصلی + دو عکس', JSON.stringify(dots));
ok(dots[1].label.includes('عکسِ ۲ از ۳') && dots[1].label.includes('پشتِ بسته') && dots[2].label.includes('برچسب'), 'نامِ دسترس‌پذیرِ هر دکمه با نقشِ عکس', JSON.stringify(dots));
ok(!asked.some((u) => /\/gallery\/[0-9a-f]{12}\//.test(u)), 'هیچ عکسِ گالری پیش از زدن بار نشد', asked.join(' '));
ok(await current(page) === 0 && /\/products\/rice\/photo/.test(await imgSrc(page)), 'آغاز با عکسِ اصلی');
await axe(page, 'برگهٔ کالا با گالری');

await page.locator('#ps-gal .dot').nth(1).click();
await page.waitForFunction(() => /\/gallery\/[0-9a-f]{12}\/display$/.test(document.querySelector('#ps-img img')?.getAttribute('src') || ''), null, { timeout: 5000 });
ok(await current(page) === 1, 'زدنِ «۲» ⇒ عکسِ دوم با aria-current');
ok(asked.filter((u) => /\/gallery\/[0-9a-f]{12}\//.test(u)).length === 1, 'فقط همان یک عکس بار شد', asked.join(' '));
ok((await page.textContent('#ps-gal')).includes('عکسِ ۲ از ۳'), 'شمارهٔ عکس برای صفحه‌خوان');
ok(await page.$eval('#ps-img img', (i) => i.complete && i.naturalWidth > 0), 'عکسِ گالری واقعاً باز شد');
await page.locator('#ps-gal .dot').nth(0).click();
ok(await current(page) === 0 && /\/products\/rice\/photo/.test(await imgSrc(page)), 'برگشت به «۱» ⇒ همان عکسِ اصلی');

//  کشیدن روی عکس: راست‌به‌چپ، کشیدن به راست ⇒ عکسِ بعدی
const box = await page.$eval('#ps-img', (e) => { const r = e.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
await page.mouse.move(box.x - 60, box.y);
await page.mouse.down();
await page.mouse.move(box.x + 60, box.y, { steps: 4 });
await page.mouse.up();
await page.waitForFunction(() => /\/gallery\//.test(document.querySelector('#ps-img img')?.getAttribute('src') || ''), null, { timeout: 5000 }).catch(() => {});
ok(await current(page) === 1, 'کشیدن به راست ⇒ عکسِ بعدی');
await page.click('#ps-gal button[aria-label="عکسِ بعدی"]');
ok(await current(page) === 2, '«عکسِ بعدی» ⇒ سومی');
await page.click('#ps-gal button[aria-label="عکسِ بعدی"]');
ok(await current(page) === 2, 'پس از آخرین عکس جلوتر نمی‌رود');

//  کالای بی گالری ⇒ بخش پنهان و خالی
await page.evaluate(() => { location.hash = '#/p/tea'; });
await page.waitForFunction(() => document.getElementById('ps-name').textContent === 'چای', null, { timeout: 10000 });
await page.waitForTimeout(500);
ok(await page.$eval('#ps-gal', (e) => e.hidden && !e.children.length), 'کالای بی گالری ⇒ بخش پنهان و خالی');
await page.evaluate(() => { location.hash = '#/p/rice'; });
await page.waitForSelector('#ps-gal:not([hidden]) .dot', { timeout: 10000 });
ok(await current(page) === 0 && /\/products\/rice\/photo/.test(await imgSrc(page)), 'برگشت به برنج ⇒ گالری از عکسِ اصلی');

//  ── تاریک ──
const dark = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'fa-AF', colorScheme: 'dark' });
const dp = await dark.newPage(); watch(dp);
await sheet(dp, 'rice');
await dp.waitForSelector('#ps-gal:not([hidden]) .dot', { timeout: 10000 });
await axe(dp, 'برگهٔ کالا با گالری (تاریک)');
await dark.close();

//  ── انگلیسی (چپ‌به‌راست) ──
const en = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'en-GB' });
const ep = await en.newPage(); watch(ep);
await sheet(ep, 'rice', 'en');
await ep.waitForSelector('#ps-gal:not([hidden]) .dot', { timeout: 10000 });
const enLabels = await ep.$$eval('#ps-gal button', (bs) => bs.map((b) => b.getAttribute('aria-label')).join(' | '));
const enText = await ep.textContent('#ps-gal');
ok(await ep.evaluate(() => document.documentElement.lang) === 'en' && /Photo 2 of 3/.test(enLabels) && /Back of the pack/.test(enLabels) && /Next photo/.test(enLabels)
  && !/[؀-ۿ]/.test(enLabels + enText), 'انگلیسی: نام‌ها و راهنما ترجمه شده', enLabels.slice(0, 200));
await axe(ep, 'برگهٔ کالا با گالری (انگلیسی)');
await en.close();

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: گالریِ عکس‌های بیشترِ کالا');
process.exit(bad ? 1 : 0);
