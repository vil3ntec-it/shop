/**
 * ویترین با کرومیومِ واقعی: بزرگ‌نماییِ عکسِ کالا روی برگهٔ کالا — IMG-11 (نسخهٔ بزرگ).
 *
 *     node test/web-store-zoom.mjs      (npm run test:web-store، پس از web-store-gallery.mjs)
 *
 * `image-zoom.test.js` ساختِ نسخهٔ ۲۴۰۰px ِ سرور را قفل می‌کند؛ این‌جا خودِ صفحه با یک دکانِ واقعی (ثبت‌نام، همگام‌سازیِ کالا،
 * انتشار، عکسِ واقعیِ ۳۰۰۰×۲۰۰۰ ِ بانکِ تصاویر با display و thumb ِ «برنامه»):
 *   • کالای بی عکس ⇒ نه zoom.js، نه دکمه؛ کالای عکس‌دار ⇒ دکمهٔ «بزرگ‌نماییِ عکس»، و هیچ درخواستِ `/zoom` پیش از باز کردن؛
 *   • پرچمِ `image_bank.zoom` خاموش ⇒ `/zoom` ۴۰۴ و بیننده همان display را نشان می‌دهد (نه عکسِ شکسته)؛ روشن ⇒ ۲۴۰۰px؛
 *   • + و −، چرخِ موش، دو بار زدن، کشیدن (هرگز بیرون از قاب)، دو انگشت؛ Esc فقط بیننده را می‌بندد و تمرکز به دکمه برمی‌گردد؛
 *     زدنِ خودِ عکس هم باز می‌کند؛ برگشتِ مرورگر بیننده را هم می‌بندد؛
 *   • axe-core روشن و تاریک، انگلیسی (چپ‌به‌راست)، بی نقضِ CSP (بی style ِ درون‌خطی) و بی خطای جاوااسکریپت.
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
let sharp = null;
try { sharp = require('sharp'); } catch { /* بی sharp: عکسِ واقعی ساخته نمی‌شود */ }
if (!sharp) { console.log('⚠️ sharp روی این ماشین نیست؛ بزرگ‌نماییِ ویترین سنجیده نشد'); process.exit(0); }

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
const slug = 'zoom-a';
const email = 'owner-zoom@zoom.local'; const password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'بزرگ‌نما', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const dev = 'dev-zoom';
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'بزرگ‌نما', password,
  device: { deviceId: dev, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ بزرگ‌نما' }, T)).body.shop.id;
for (const key of require('../src/lib/flag-defaults').DEFAULT_ON) await flag(shopId, key, false);
for (const key of ['shop.orders', 'shop.storefront', 'shop.image_bank']) await flag(shopId, key, true);
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ بزرگ‌نما', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین روشن شد', JSON.stringify(put.body).slice(0, 120));
let n = 0;
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}-${++n}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: dev, schema_version: 2, ops: [
  op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی', active: true }),
  op('tea', { name: 'چای', unit: 'بسته', salePrice: 90, category: 'خوراکی', active: true }),
] }, T, { 'X-App': 'shop', 'X-Device': dev });
ok(pushed.status === 200, 'کالاها از راهِ همگام‌سازیِ واقعی رسیدند');
for (const id of ['rice', 'tea']) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

//  عکسِ واقعی ۳۰۰۰×۲۰۰۰ (نیمهٔ بالا قرمز، پایین آبی) + display/thumb ِ «برنامه»
const src = await sharp({ create: { width: 3000, height: 2000, channels: 3, background: { r: 30, g: 60, b: 210 } } })
  .composite([{ input: { create: { width: 3000, height: 1000, channels: 3, background: { r: 220, g: 30, b: 40 } } }, top: 0, left: 0 }])
  .jpeg({ quality: 80 }).toBuffer();
const derive = (side) => sharp(src).resize({ width: side, height: side, fit: 'inside' }).jpeg({ quality: 80 }).toBuffer();
const up = await raw('POST', '/api/shop/images?product_id=rice&role=main', src, T);
ok(up.status === 201, 'عکسِ بانک بارگذاری شد', JSON.stringify(up.body).slice(0, 100));
for (const [size, side] of [['display', 1200], ['thumb', 400]]) {
  const d = await raw('PUT', `/api/shop/images/${up.body.id}/v/1/${size}`, await derive(side), T);
  ok(d.status === 200, `نسخهٔ ${size}`, JSON.stringify(d.body).slice(0, 100));
}
const sel = await call('POST', '/api/shop/images/select', { product_id: 'rice', source: 'shop', image_id: up.body.id, version: 1 }, T);
ok(sel.status === 200, 'عکسِ ویترینِ برنج', JSON.stringify(sel.body).slice(0, 100));

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const asked = [];
const watch = (page) => {
  page.on('pageerror', (e) => errors.push(String(e) + ' @ ' + String(e.stack || '').split('\n').slice(1, 3).join(' ')));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  page.on('request', (r) => { if (/\/zoom(\?|$)|\/store\/zoom\.js|\/i18n\/zoom\./.test(r.url())) asked.push(r.url()); });
};
async function axe(page, label) {
  if (!axeSrc) { console.log(`⚠️ axe-core پیدا نشد؛ ${label} سنجیده نشد`); return; }
  await page.waitForFunction(() => document.getAnimations().every((x) => x.playState !== 'running'), null, { timeout: 5000 }).catch(() => {});
  if (!(await page.evaluate(() => Boolean(window.axe)))) await page.evaluate(axeSrc);
  const out = await page.evaluate(async () => (await window.axe.run(document.getElementById('zm'),
    { runOnly: ['color-contrast', 'button-name', 'image-alt', 'aria-valid-attr-value', 'aria-required-attr', 'aria-dialog-name', 'duplicate-id-aria', 'duplicate-id', 'nested-interactive', 'target-size'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(out.length === 0, `دسترس‌پذیری (axe-core): ${label}`, out.join(' | '));
}
async function sheet(page, id, lang = '') {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${slug}${lang ? `?lang=${lang}` : ''}#/p/${id}`);
  await page.waitForSelector('#product-sheet:not([hidden]) #ps-name', { timeout: 15000 });
  await page.waitForFunction(() => window.StoreProduct, null, { timeout: 10000 });
}
const zoomState = (page) => page.evaluate(() => {
  const i = document.querySelector('#zm .zm-img');
  const m = /translate\(([-\d.]+)px,\s*([-\d.]+)px\) scale\(([\d.]+)\)/.exec(i ? i.style.transform : '') || [];
  const st = document.querySelector('#zm .zm-stage');
  return { s: Number(m[3] || 0), x: Number(m[1] || 0), y: Number(m[2] || 0), w: i ? i.offsetWidth : 0, h: i ? i.offsetHeight : 0,
    W: st ? st.clientWidth : 0, H: st ? st.clientHeight : 0, level: document.querySelector('#zm .zm-level')?.textContent || '',
    open: !!document.getElementById('zm') && !document.getElementById('zm').hidden, nat: i ? i.naturalWidth : 0, src: i ? i.currentSrc : '' };
});
const loaded = (page) => page.waitForFunction(() => { const i = document.querySelector('#zm .zm-img'); return !!i && i.complete && i.naturalWidth > 0; }, null, { timeout: 10000 }).then(() => true, () => false);
const inBounds = (z) => Math.abs(z.x) <= Math.max(0, (z.w * z.s - z.W) / 2) + 0.5 && Math.abs(z.y) <= Math.max(0, (z.h * z.s - z.H) / 2) + 0.5;

const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
const page = await ctx.newPage(); watch(page);

//  ── کالای بی عکس ──
await sheet(page, 'tea');
await page.waitForTimeout(600);
ok(asked.length === 0 && !(await page.$('#ps-zoom:not([hidden])')), 'کالای بی عکس ⇒ نه zoom.js، نه واژه‌نامه‌اش، نه دکمه', asked.join(' '));

//  ── کالای عکس‌دار، پرچمِ zoom خاموش ⇒ همان display بزرگ می‌شود ──
await sheet(page, 'rice');
await page.waitForSelector('#ps-zoom:not([hidden])', { timeout: 10000 });
ok((await page.textContent('#ps-zoom')).includes('بزرگ‌نماییِ عکس'), 'دکمهٔ «بزرگ‌نماییِ عکس»');
ok(!asked.some((u) => /\/zoom(\?|$)/.test(u)), 'هیچ درخواستِ نسخهٔ بزرگ پیش از باز کردن', asked.join(' '));
await page.click('#ps-zoom');
await page.waitForSelector('#zm:not([hidden])', { timeout: 5000 });
ok(await loaded(page), 'بیننده عکس را نشان داد (نه عکسِ شکسته)');
let z = await zoomState(page);
ok(/\/products\/rice\/photo\?v=/.test(z.src) && z.nat === 1200, 'zoom ِ خاموش (۴۰۴) ⇒ همان display ِ ۱۲۰۰px', `${z.src} ${z.nat}`);
ok(asked.some((u) => /\/products\/rice\/zoom\?v=[0-9a-f]{12}$/.test(u)), 'نخست نسخهٔ بزرگِ همان نسخهٔ عکس (`?v=`) خواسته شد', asked.join(' '));
ok(z.s === 1 && z.level === '۱۰۰٪', 'آغاز ۱۰۰٪', z.level);
ok((await page.textContent('#zm .zm-hint')).includes('نسخهٔ بزرگ‌تری از این عکس نیست'), 'گفته شد که نسخهٔ بزرگ‌تری نیست');
ok(await page.evaluate(() => document.activeElement?.classList.contains('zm-close')), 'تمرکز روی «بستن» ِ بیننده');
ok(await page.$eval('#zm', (e) => e.getAttribute('role') === 'dialog' && e.getAttribute('aria-modal') === 'true' && e.getAttribute('aria-label') === 'عکسِ بزرگِ کالا'),
  'بیننده یک dialog ِ نام‌دار است');
ok(await page.$eval('#zm .zm-out', (b) => b.disabled), '«کوچک‌تر» در ۱۰۰٪ خاموش');
await axe(page, 'بینندهٔ بزرگ‌نمایی');

await page.keyboard.press('+');
z = await zoomState(page);
ok(Math.abs(z.s - 1.5) < 0.01 && z.level === '۱۵۰٪', '+ ⇒ ۱۵۰٪', `${z.s} ${z.level}`);
await page.keyboard.press('Escape');
await page.waitForFunction(() => document.getElementById('zm').hidden, null, { timeout: 3000 }).catch(() => {});
ok(!(await zoomState(page)).open && await page.$eval('#product-sheet', (e) => !e.hidden), 'Esc فقط بیننده را بست، برگهٔ کالا باز ماند');
ok(await page.evaluate(() => document.activeElement?.id === 'ps-zoom'), 'تمرکز به دکمهٔ بزرگ‌نمایی برگشت');

//  ── پرچمِ روشن ⇒ نسخهٔ ۲۴۰۰px ِ سرور ──
await flag(shopId, 'image_bank.zoom', true);
asked.length = 0;
await page.click('#ps-img img');   //  زدنِ خودِ عکس هم باز می‌کند
await page.waitForSelector('#zm:not([hidden])', { timeout: 5000 });
ok(await loaded(page), 'نسخهٔ بزرگ رسید');
z = await zoomState(page);
ok(/\/products\/rice\/zoom\?v=/.test(z.src) && z.nat === 2400, 'پرچمِ روشن ⇒ همان عکس در ۲۴۰۰px', `${z.src} ${z.nat}`);
ok((await page.textContent('#zm .zm-hint')).includes('دو انگشت'), 'راهنمای بزرگ‌نمایی');

//  چرخِ موش گردِ نقطه
const stage = await page.$eval('#zm .zm-stage', (e) => { const r = e.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2, w: r.width, h: r.height }; });
await page.mouse.move(stage.x, stage.y);
await page.mouse.wheel(0, -200);
await page.waitForTimeout(100);
z = await zoomState(page);
ok(z.s > 1.1 && inBounds(z), 'چرخِ موش ⇒ بزرگ‌تر، درونِ قاب', JSON.stringify(z).slice(0, 160));

//  تا ۴۰۰٪ و کشیدن تا بیرون ⇒ مرز نگه داشته می‌شود
for (let i = 0; i < 6; i++) await page.keyboard.press('+');
z = await zoomState(page);
ok(z.s === 4 && z.level === '۴۰۰٪' && await page.$eval('#zm .zm-in', (b) => b.disabled), 'سقفِ ۴۰۰٪ و «بزرگ‌تر» خاموش', `${z.s} ${z.level}`);
await page.mouse.move(stage.x, stage.y);
await page.mouse.down();
await page.mouse.move(stage.x + 3000, stage.y + 3000, { steps: 6 });
await page.mouse.up();
z = await zoomState(page);
ok(z.x > 0 && z.y > 0 && inBounds(z), 'کشیدن ⇒ جابه‌جا، هرگز بیرون از قاب', JSON.stringify(z).slice(0, 160));
for (let i = 0; i < 40; i++) await page.keyboard.press('ArrowRight');
z = await zoomState(page);
ok(inBounds(z), 'جهت‌های صفحه‌کلید هم بیرون نمی‌برند', JSON.stringify(z).slice(0, 160));

//  دو بار زدن ⇒ برگشت؛ دو بار زدنِ دوباره ⇒ ۲۵۰٪ گردِ همان نقطه
await page.mouse.click(stage.x, stage.y);
await page.mouse.click(stage.x, stage.y, { delay: 20 });
z = await zoomState(page);
ok(z.s === 1 && z.x === 0 && z.y === 0, 'دو بار زدن در بزرگی ⇒ ۱۰۰٪', JSON.stringify(z).slice(0, 120));
await page.mouse.click(stage.x + 40, stage.y);
await page.mouse.click(stage.x + 40, stage.y, { delay: 20 });
z = await zoomState(page);
ok(Math.abs(z.s - 2.5) < 0.01 && inBounds(z), 'دو بار زدن ⇒ ۲۵۰٪', JSON.stringify(z).slice(0, 120));
await page.click('#zm .zm-reset');
ok((await zoomState(page)).s === 1, '«اندازهٔ نخست» ⇒ ۱۰۰٪');

//  دو انگشت (رویدادِ اشاره‌گرِ لمسیِ دوتایی): فاصلهٔ ۱۰۰ ⇒ ۲۰۰ ⇒ دو برابر
await page.evaluate(({ x, y }) => {
  const st = document.querySelector('#zm .zm-stage');
  const fire = (type, id, cx) => st.dispatchEvent(new PointerEvent(type, { pointerId: id, pointerType: 'touch', clientX: cx, clientY: y, bubbles: true, isPrimary: id === 1 }));
  fire('pointerdown', 1, x - 50); fire('pointerdown', 2, x + 50);
  fire('pointermove', 1, x - 75); fire('pointermove', 2, x + 75);
  fire('pointermove', 1, x - 100); fire('pointermove', 2, x + 100);
  fire('pointerup', 1, x - 100); fire('pointerup', 2, x + 100);
}, stage);
z = await zoomState(page);
ok(Math.abs(z.s - 2) < 0.05 && inBounds(z), 'دو انگشت ⇒ دو برابر', JSON.stringify(z).slice(0, 120));

//  Tab درونِ بیننده می‌چرخد
await page.focus('#zm .zm-close');
await page.keyboard.press('Tab');
ok(await page.evaluate(() => document.getElementById('zm').contains(document.activeElement)), 'Tab از بیننده بیرون نمی‌رود');

//  برگشتِ مرورگر (درونِ همان صفحه: چای ⇐ برنج) ⇒ بیننده بسته و برگهٔ کالای پیشین
await page.keyboard.press('Escape');
await page.evaluate(() => { location.hash = '#/p/tea'; });
await page.waitForFunction(() => document.getElementById('ps-name').textContent === 'چای', null, { timeout: 10000 });
await page.evaluate(() => { location.hash = '#/p/rice'; });
await page.waitForFunction(() => document.getElementById('ps-name').textContent === 'برنج', null, { timeout: 10000 });
await page.waitForSelector('#ps-zoom:not([hidden])', { timeout: 10000 });
await page.click('#ps-zoom');
await page.waitForSelector('#zm:not([hidden])', { timeout: 5000 });
await page.goBack();
await page.waitForFunction(() => document.getElementById('ps-name').textContent === 'چای', null, { timeout: 10000 }).catch(() => {});
ok(await page.evaluate(() => !!document.getElementById('zm') && document.getElementById('zm').hidden && location.hash === '#/p/tea'),
  'برگشتِ مرورگر بیننده را هم بست', await page.evaluate(() => location.hash));
ok(await page.$eval('#ps-zoom', (b) => b.hidden), 'کالای بی عکس ⇒ دکمهٔ بزرگ‌نمایی پنهان');

//  ── تاریک ──
const dark = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'fa-AF', colorScheme: 'dark' });
const dp = await dark.newPage(); watch(dp);
await sheet(dp, 'rice');
await dp.waitForSelector('#ps-zoom:not([hidden])', { timeout: 10000 });
await dp.click('#ps-zoom');
await dp.waitForSelector('#zm:not([hidden])', { timeout: 5000 });
await loaded(dp);
await axe(dp, 'بینندهٔ بزرگ‌نمایی (تاریک)');
await dark.close();

//  ── انگلیسی (چپ‌به‌راست) ──
const en = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'en-GB' });
const ep = await en.newPage(); watch(ep);
await sheet(ep, 'rice', 'en');
await ep.waitForSelector('#ps-zoom:not([hidden])', { timeout: 10000 });
await ep.click('#ps-zoom');
await ep.waitForSelector('#zm:not([hidden])', { timeout: 5000 });
const enText = await ep.evaluate(() => [document.getElementById('ps-zoom').textContent, document.getElementById('zm').getAttribute('aria-label'),
  ...Array.from(document.querySelectorAll('#zm button')).map((b) => b.getAttribute('aria-label') || b.textContent), document.querySelector('#zm .zm-level').textContent,
  document.querySelector('#zm .zm-hint').textContent].join(' | '));
ok(/Zoom photo/.test(enText) && /Large product photo/.test(enText) && /Zoom in/.test(enText) && /100%/.test(enText) && !/[؀-ۿ]/.test(enText),
  'انگلیسی: نام‌ها و راهنما ترجمه شده', enText.slice(0, 200));
await axe(ep, 'بینندهٔ بزرگ‌نمایی (انگلیسی)');
await en.close();

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: بزرگ‌نماییِ عکسِ کالا');
process.exit(bad ? 1 : 0);
