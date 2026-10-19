/**
 * بانکِ تصاویر در نسخهٔ وب — روی سرورِ واقعی (PGlite) و کرومیوم (v3 ۳۴، ۳۶.۸، ۳۷.۵ تا ۳۷.۷).
 *
 *   • پرچم خاموش ⇒ دکمهٔ «انتخاب از بانکِ تصاویر» پنهان و هیچ ردیفی در بانک نوشته نمی‌شود
 *   • جست‌وجو با بارکد ⇒ «تطبیقِ دقیق»؛ انتخاب پس از ذخیره روی سرور = exact
 *   • جست‌وجو با نام ⇒ «محتمل» و «چند نوع»؛ بی تیکِ «همین کالاست» انتخاب نمی‌شود
 *   • اینترنت قطع ⇒ پیامِ روشن، نه فهرستِ خالی
 *   • آماده‌سازی روی دستگاه: چرخشِ EXIF روی پیکسل‌ها، بی EXIF/GPS، اندازه‌های سرور؛
 *     سرور نسخه‌های کوچک را می‌پذیرد (orientation/نسبت/حجم را خودش می‌سنجد)
 *   • ورودِ گروهی: تطبیق با بارکد/نام/CSV، تکراری، مبهم، بی‌تطبیق، عکس‌دار؛
 *     بستنِ برنامه وسطِ کار و ادامه بی بارگذاریِ تکراری
 *
 * هیچ چیزِ ساختگی‌ای در مسیرِ برنامه نیست: درخواست‌های صفحه به همان سرورِ
 * آزمون می‌روند (‎helpers.start‎). اجرا:
 *   TEST_DATABASE_URL=pglite:memory node test/web-image-bank.mjs
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

process.env.TEST_DATABASE_URL = process.env.TEST_DATABASE_URL || 'pglite:memory';
const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const F = require('./image-fixtures');

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const statics = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => statics.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${statics.address().port}/index.html`;

/* ------------------------------ سرور و داده ------------------------------ */
const real = await h.start();
const pwlib = require('../src/lib/password');
await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
  VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`, [newId('adm'), await pwlib.hashPassword('Root!12345'), now()]);
const root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
const clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;
const owner = await h.newUser('صاحبِ دکان', 'shop');
const made = await h.post('/api/shop', { name: 'دکانِ آزمون' }, { token: owner.accessToken });
assert.equal(made.status, 201, JSON.stringify(made.body));
const shopId = made.body.shop.id;
const setFlag = async (scope, key, value) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const S = (method, p, body, who = owner) => h.api(method, '/api/shop/images' + p, { token: who.accessToken, body });
const count = async (t) => Number((await one(`SELECT COUNT(*)::int AS n FROM ${t}`)).n);

const EAN_TEA = F.ean13('590123412345');
const UPC = '036000291452';          // UPC-A معتبر
const EAN_OF_UPC = '0' + UPC;        // همان کالا، ۱۳ رقمی ⇒ دو کالای دکان با یک GTIN

/* ------------------------------ کرومیوم ------------------------------ */
const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.stack || e.message}`); }
};

const LEDGER = {
  products: [
    { id: 'p-rice', name: 'برنج', unit: 'کیلوگرم', category: 'خوراکی', salePrice: 100, purchasePrice: 80, barcodes: ['6260000000017'] },
    { id: 'p-oil', name: 'روغن', unit: 'عدد', category: 'خوراکی', salePrice: 200, purchasePrice: 150, barcodes: [] },
    { id: 'p-soda-a', name: 'نوشابه قوطی', unit: 'عدد', category: 'خوراکی', salePrice: 30, purchasePrice: 20, barcodes: [UPC] },
    { id: 'p-soda-b', name: 'نوشابه قوطی وارداتی', unit: 'عدد', category: 'خوراکی', salePrice: 35, purchasePrice: 25, barcodes: [EAN_OF_UPC] },
    { id: 'p-salt', name: 'نمک', unit: 'عدد', category: 'خوراکی', salePrice: 10, purchasePrice: 5, barcodes: ['6260000000024'] },
    { id: 'p-sugar', name: 'شکر', unit: 'کیلوگرم', category: 'خوراکی', salePrice: 90, purchasePrice: 70, barcodes: [], photo: true },
  ],
  productCategories: ['خوراکی'],
  debtors: [], transactions: [], sales: [], saleItems: [],
};
const ACCOUNT = {
  accessToken: owner.accessToken, refreshToken: owner.refreshToken,
  accessExpiresAt: Date.now() + 86400000, userId: owner.user.id, userLabel: 'صاحبِ دکان',
};

let gate = null;   // تأخیرِ دستی برای «بستنِ برنامه وسطِ کار»
async function open(width = 1280) {
  const pg = await browser.newPage({ viewport: { width, height: 900 } });
  const errors = [];
  pg.on('pageerror', (e) => errors.push(String(e)));
  pg.on('dialog', (d) => d.accept());
  await pg.goto(url);
  const api = await pg.evaluate(() => window.TohidApiConfig.baseUrl());
  await pg.route(`${api}/**`, async (route) => {
    const u = new URL(route.request().url());
    if (gate && gate(u, route.request().method())) await new Promise((r) => setTimeout(r, 900));
    try {
      const resp = await route.fetch({ url: real + u.pathname + u.search });
      await route.fulfill({ response: resp, headers: { ...resp.headers(), 'access-control-allow-origin': '*' } });
    } catch { /* صفحه رفت */ }
  });
  return { pg, errors, api };
}
async function signIn(pg, ledger = LEDGER, account = ACCOUNT) {
  await pg.evaluate(([d, a]) => {
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
    localStorage.setItem('tohid-license-v1', JSON.stringify(a));
  }, [ledger, account]);
  await pg.reload();
  await pg.waitForFunction(() => window.TohidImageBank && window.TohidAccount && window.TohidAccount.isSignedIn());
}

/** JPEGِ واقعی از بومِ مرورگر — مربعِ قرمز در گوشهٔ بالا-چپ */
async function jpegFromPage(pg, w, h, { noise = false } = {}) {
  const b64 = await pg.evaluate(async ([w, h, noise]) => {
    const cv = document.createElement('canvas'); cv.width = w; cv.height = h;
    const c = cv.getContext('2d');
    c.fillStyle = '#2a6'; c.fillRect(0, 0, w, h);
    if (noise) {
      const im = c.getImageData(0, 0, w, h);
      let x = 12345;
      for (let i = 0; i < im.data.length; i += 4) { x = (x * 1103515245 + 12345) >>> 0; im.data[i] = x & 255; im.data[i + 1] = (x >> 8) & 255; im.data[i + 2] = (x >> 16) & 255; }
      c.putImageData(im, 0, 0);
    }
    c.fillStyle = '#f00'; c.fillRect(0, 0, Math.round(w / 6), Math.round(h / 6));
    const b = await new Promise((r) => cv.toBlob(r, 'image/jpeg', 0.9));
    const a = new Uint8Array(await b.arrayBuffer());
    let s = ''; for (let i = 0; i < a.length; i++) s += String.fromCharCode(a[i]);
    return btoa(s);
  }, [w, h, noise]);
  return Buffer.from(b64, 'base64');
}

/** APP1ِ Exif با orientation=6 و مختصاتِ GPS، درست پس از APP0 */
function withExif(jpeg, orientation = 6) {
  const t = Buffer.alloc(92);
  t.write('MM', 0, 'latin1'); t.writeUInt16BE(42, 2); t.writeUInt32BE(8, 4);
  t.writeUInt16BE(2, 8);
  t.writeUInt16BE(0x0112, 10); t.writeUInt16BE(3, 12); t.writeUInt32BE(1, 14); t.writeUInt16BE(orientation, 18);
  t.writeUInt16BE(0x8825, 22); t.writeUInt16BE(4, 24); t.writeUInt32BE(1, 26); t.writeUInt32BE(38, 30);
  t.writeUInt32BE(0, 34);
  t.writeUInt16BE(2, 38);
  t.writeUInt16BE(0x0001, 40); t.writeUInt16BE(2, 42); t.writeUInt32BE(2, 44); t.write('N\0', 48, 'latin1');
  t.writeUInt16BE(0x0002, 52); t.writeUInt16BE(5, 54); t.writeUInt32BE(3, 56); t.writeUInt32BE(68, 60);
  t.writeUInt32BE(0, 64);
  [34, 1, 31, 1, 7, 1].forEach((v, i) => t.writeUInt32BE(v, 68 + i * 4));
  const body = Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), t]);
  const seg = Buffer.concat([Buffer.from([0xff, 0xe1, (body.length + 2) >> 8, (body.length + 2) & 255]), body]);
  let at = 2;
  if (jpeg[2] === 0xff && jpeg[3] === 0xe0) at = 4 + jpeg.readUInt16BE(4);
  return Buffer.concat([jpeg.subarray(0, at), seg, jpeg.subarray(at)]);
}
/** نشانگرهای پیش از SOS */
function markers(buf) {
  const out = []; let i = 2;
  while (i + 4 <= buf.length && buf[i] === 0xff) {
    const m = buf[i + 1]; const len = buf.readUInt16BE(i + 2);
    out.push(m); if (m === 0xda) break; i += 2 + len;
  }
  return out;
}

/* ------------------------------ داده‌های بانک ------------------------------ */
const pages = await open();
await signIn(pages.pg);
const bankImg = await jpegFromPage(pages.pg, 800, 600);
const bankThumb = await jpegFromPage(pages.pg, 400, 300);
await pages.pg.close();

async function central(product) {
  const p = await h.api('POST', '/api/admin/image-bank/products', { token: clerk, body: product });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const up = await h.raw('POST', `/api/admin/image-bank/images?bank_product_id=${p.body.id}&license_status=licensed&commercial_use=true&license_source=supplier-feed`, bankImg, { token: clerk });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const t = await h.raw('PUT', `/api/admin/image-bank/images/${up.body.id}/v/1/thumb`, bankThumb, { token: clerk });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  const ok = await h.api('POST', `/api/admin/image-bank/images/${up.body.id}/approve`, { token: root, body: { reason: 'مجوزِ تأمین‌کننده بررسی شد' } });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  return { productId: p.body.id, imageId: up.body.id };
}

/* ================================ آزمون‌ها ================================ */

await step('پرچم خاموش (پیش‌فرض): دکمهٔ بانک پنهان، ورودِ گروهی فقط عکسِ دستگاه، هیچ ردیفی در بانک', async () => {
  const { pg, errors } = await open();
  await signIn(pg);
  await pg.evaluate(() => window.TohidImageBank.refreshConfig());
  assert.equal(await pg.evaluate(() => window.TohidImageBank.enabled()), false);
  await pg.evaluate(() => goTo('products'));
  await pg.locator('#btn-add-product-top').click();
  await pg.waitForSelector('#modal-bulk-product.open');
  assert.equal(await pg.locator('[data-bp-bank-wrap]').first().isHidden(), true, 'دکمهٔ بانک با پرچمِ خاموش پنهان است');
  for (const t of ['images', 'image_blobs', 'shop_product_images']) assert.equal(await count(t), 0, t);
  assert.deepEqual(errors, []);
  await pg.close();
});

await setFlag('all', 'image_bank.central', true);
const TEA = await central({ name: 'چای سبز گلستان', brand: 'گلستان', gtin: EAN_TEA, size_value: '500', size_unit: 'g', category: 'نوشیدنی' });
await central({ name: 'شیر پاستوریزه', brand: 'کاله', size_value: '1', size_unit: 'l' });
await central({ name: 'شیر پاستوریزه', brand: 'کاله', size_value: '2', size_unit: 'l' });
await setFlag(shopId, 'shop.image_bank', true);

const bpFill = async (pg, row, { purchase = '10', sale = '12' } = {}) => {
  await row.locator('[data-bp-purchase]').fill(purchase);
  await row.locator('[data-bp-sale]').fill(sale);
};
const waitServer = async (fn, ms = 8000) => {
  const end = Date.now() + ms;
  let last;
  while (Date.now() < end) { last = await fn(); if (last) return last; await new Promise((r) => setTimeout(r, 150)); }
  throw new Error('سرور به حالِ مورد انتظار نرسید: ' + JSON.stringify(last));
};

await step('IMG-BANK ۳۷.۵/۳۷.۶: بارکد ⇒ «تطبیقِ دقیق»، پیش‌نمایش، انتخاب و ثبتِ exact پس از ذخیره', async () => {
  const { pg, errors } = await open();
  await signIn(pg);
  assert.equal(await pg.evaluate(() => window.TohidImageBank.refreshConfig()), true);
  await pg.evaluate(() => goTo('products'));
  await pg.locator('#btn-add-product-top').click();
  const row = pg.locator('.bulk-row').first();
  await row.locator('[data-bp-barcode]').fill(EAN_TEA);
  assert.equal(await row.locator('[data-bp-bank-wrap]').isVisible(), true);
  await row.locator('[data-bp-bank]').click();
  await pg.waitForSelector('#ib-picker .ib-card');
  const card = pg.locator('#ib-picker .ib-card').first();
  assert.match(await card.innerText(), /چای سبز گلستان/);
  assert.match(await card.locator('.ib-pill.ok').innerText(), /تطبیقِ دقیق: بارکدِ دقیق/);
  assert.equal(await pg.locator('#ib-picker .ib-card').count(), 1);
  await pg.waitForSelector('#ib-picker .ib-card .ib-thumb.has');   // عکسِ کوچک با نشستِ کاربر آمد
  await card.click();
  await pg.waitForSelector('#ib-picker [data-ib-detail]:not([hidden])');
  assert.equal(await pg.locator('#ib-picker [data-ib-confirm]').count(), 0, 'تطبیقِ دقیق تیک نمی‌خواهد');
  assert.match(await pg.locator('#ib-picker .ib-note').first().innerText(), /قیمت، موجودی/);
  //  بازگشت عبارتِ جست‌وجو را نگه می‌دارد
  await pg.locator('#ib-picker [data-ib-back]').click();
  assert.equal(await pg.locator('#ib-picker [data-ib-q]').inputValue(), EAN_TEA);
  await card.click();
  await pg.locator('#ib-picker [data-ib-choose]').click();
  await pg.waitForSelector('#ib-picker', { state: 'detached' });
  assert.equal(await row.locator('[data-bp-name]').inputValue(), 'چای سبز گلستان', 'نامِ خالی از بانک پر شد');
  assert.match(await row.locator('[data-bp-bank-note]').innerText(), /تطبیقِ دقیق/);
  await bpFill(pg, row);
  await pg.locator('#btn-bp-save').click();
  const pid = await pg.waitForFunction(() => (products.find((p) => p.name === 'چای سبز گلستان') || {}).id).then((x) => x.jsonValue());
  const r = await waitServer(async () => { const x = await S('GET', '/products/' + pid); return x.body.source === 'central' ? x : null; });
  assert.equal(r.body.image_id, TEA.imageId);
  assert.equal(r.body.match_status, 'exact');
  assert.equal(r.body.confirmed_by_user, false);
  assert.ok(await pg.evaluate((id) => photoGet(id).then((b) => !!b), pid), 'عکس روی دستگاه هم نشست');
  assert.equal(await pg.evaluate(() => window.TohidImageBank.pending()), 0);
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('IMG-BANK ۳۷.۶ بندِ ۸: نامِ مشابه ⇒ «محتمل» و «چند نوع»؛ بی تیک انتخاب نمی‌شود؛ با تیک = تأییدِ کاربر', async () => {
  const { pg, errors } = await open();
  await signIn(pg);
  await pg.evaluate(() => window.TohidImageBank.refreshConfig());
  await pg.evaluate(() => goTo('products'));
  await pg.locator('#btn-add-product-top').click();
  const row = pg.locator('.bulk-row').first();
  await row.locator('[data-bp-name]').fill('شیر پاستوریزه');
  await row.locator('[data-bp-bank]').click();
  await pg.waitForFunction(() => document.querySelectorAll('#ib-picker .ib-card').length === 2);
  for (const c of await pg.locator('#ib-picker .ib-card').all()) {
    const text = await c.innerText();
    assert.match(text, /محتمل: نامِ کاملِ یکسان/);
    assert.match(text, /چند نوع — فرق در وزن\/حجم/);
    assert.doesNotMatch(text, /تطبیقِ دقیق|٪|%/, 'امتیازِ عددی یا «دقیق» نشان داده نمی‌شود');
  }
  await pg.locator('#ib-picker .ib-card').nth(1).click();
  const choose = pg.locator('#ib-picker [data-ib-choose]');
  assert.equal(await choose.isDisabled(), true, 'بی تیک بسته است');
  assert.match(await pg.locator('#ib-picker .ib-why').innerText(), /نه بارکد/);
  await pg.locator('#ib-picker [data-ib-confirm]').check();
  assert.equal(await choose.isDisabled(), false);
  await choose.click();
  await pg.waitForSelector('#ib-picker', { state: 'detached' });
  assert.match(await row.locator('[data-bp-bank-note]').innerText(), /محتمل/);
  await bpFill(pg, row);
  await pg.locator('#btn-bp-save').click();
  const pid = await pg.waitForFunction(() => (products.find((p) => p.name === 'شیر پاستوریزه') || {}).id).then((x) => x.jsonValue());
  const r = await waitServer(async () => { const x = await S('GET', '/products/' + pid); return x.body.source === 'central' ? x : null; });
  assert.equal(r.body.match_status, 'probable');
  assert.equal(r.body.confirmed_by_user, true, 'تطبیقِ غیرِ دقیق با تأییدِ صریحِ کاربر ثبت شد');
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('۳۷.۵: اینترنت قطع ⇒ پیامِ روشن و «دوباره»، نه فهرستِ خالی', async () => {
  const { pg, api } = await open(390);
  await signIn(pg);
  await pg.evaluate(() => window.TohidImageBank.refreshConfig());
  await pg.route(`${api}/api/shop/images/search**`, (r) => r.abort('internetdisconnected'));
  await pg.evaluate(() => goTo('products'));
  await pg.locator('#btn-add-product-top').click();
  const row = pg.locator('.bulk-row').first();
  await row.locator('[data-bp-name]').fill('چای');
  await row.locator('[data-bp-bank]').click();
  await pg.waitForSelector('#ib-picker [data-ib-state].error');
  assert.match(await pg.locator('#ib-picker [data-ib-state]').innerText(), /اینترنت قطع است/);
  assert.equal(await pg.locator('#ib-picker [data-ib-retry]').count(), 1);
  const over = await pg.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert.ok(over <= 0, 'روی گوشی اسکرولِ افقی نیست');
  await pg.keyboard.press('Escape');
  await pg.waitForSelector('#ib-picker', { state: 'detached' });
  await pg.close();
});

await step('۳۴.۳/۳۶.۹: آماده‌سازی — چرخشِ EXIF روی پیکسل، بی EXIF/GPS، ۱۲۰۰/۴۰۰؛ سرور نسخه‌های کوچک را می‌پذیرد', async () => {
  const { pg, errors } = await open();
  await signIn(pg);
  await pg.evaluate(() => window.TohidImageBank.refreshConfig());
  const src = withExif(await jpegFromPage(pg, 600, 400), 6);
  assert.ok(src.includes(Buffer.from('Exif\0\0', 'latin1')));
  const out = await pg.evaluate(async (b64) => {
    const bin = atob(b64); const a = new Uint8Array(bin.length); for (let i = 0; i < a.length; i++) a[i] = bin.charCodeAt(i);
    const prep = await window.TohidImageBank.prepare(new Blob([a], { type: 'image/jpeg' }));
    const bm = await createImageBitmap(prep.display);
    const cv = document.createElement('canvas'); cv.width = bm.width; cv.height = bm.height;
    const c = cv.getContext('2d'); c.drawImage(bm, 0, 0);
    const px = (x, y) => Array.from(c.getImageData(x, y, 1, 1).data.slice(0, 3));
    const bytes = async (b) => { const u = new Uint8Array(await b.arrayBuffer()); let s = ''; for (let i = 0; i < u.length; i++) s += String.fromCharCode(u[i]); return btoa(s); };
    window.__prep = prep;
    return { w: prep.width, h: prep.height, tw: prep.thumbWidth, th: prep.thumbHeight, small: prep.small,
      topRight: px(bm.width - 5, 5), topLeft: px(5, 5), display: await bytes(prep.display), thumb: await bytes(prep.thumb) };
  }, src.toString('base64'));
  assert.deepEqual([out.w, out.h], [400, 600], 'orientation=6 ⇒ ابعاد جابه‌جا');
  assert.ok(out.topRight[0] > 200 && out.topRight[1] < 80, 'گوشهٔ قرمز پس از چرخش بالا-راست است: ' + out.topRight);
  assert.ok(out.topLeft[0] < 120, 'بالا-چپ دیگر قرمز نیست: ' + out.topLeft);
  const display = Buffer.from(out.display, 'base64');
  const thumb = Buffer.from(out.thumb, 'base64');
  for (const b of [display, thumb]) {
    assert.equal(b[0], 0xff); assert.equal(b[1], 0xd8);
    assert.ok(!markers(b).includes(0xe1), 'هیچ APP1 (EXIF/XMP) نمانده');
    assert.ok(!b.includes(Buffer.from('Exif', 'latin1')));
  }
  assert.ok(display.length <= 600 * 1024 && thumb.length <= 150 * 1024);

  //  عکسِ بزرگ ⇒ ۱۲۰۰ و ۴۰۰، زیرِ سقفِ حجم
  const big = await pg.evaluate(async () => {
    const cv = document.createElement('canvas'); cv.width = 3000; cv.height = 2000;
    const c = cv.getContext('2d'); const im = c.createImageData(3000, 2000);
    let x = 7; for (let i = 0; i < im.data.length; i += 4) { x = (x * 1103515245 + 12345) >>> 0; im.data[i] = x & 255; im.data[i + 1] = (x >> 9) & 255; im.data[i + 2] = (x >> 17) & 255; im.data[i + 3] = 255; }
    c.putImageData(im, 0, 0);
    const b = await new Promise((r) => cv.toBlob(r, 'image/jpeg', 0.95));
    const p = await window.TohidImageBank.prepare(b);
    return { src: b.size, w: p.width, h: p.height, d: p.display.size, tw: p.thumbWidth, th: p.thumbHeight, t: p.thumb.size };
  });
  assert.ok(big.w <= 1200 && big.h <= 800 && big.d <= 600 * 1024, JSON.stringify(big));
  assert.ok(big.tw <= 400 && big.t <= 150 * 1024, JSON.stringify(big));
  assert.ok(Math.abs(big.w / big.h - 1.5) < 0.02 && Math.abs(big.tw / big.th - 1.5) < 0.02, 'نسبت ثابت ماند');

  //  سرور: اصل + نسخه‌های کوچک + انتخاب به‌عنوانِ عکسِ خودِ دکان
  const st = await pg.evaluate(() => window.TohidImageBank.uploadOwn('p-rice', window.__prep, null));
  assert.ok(st.imageId && st.display && st.thumb && st.selected, JSON.stringify(st));
  const mine = await S('GET', '/mine?product_id=p-rice');
  assert.equal(mine.body.items.length, 1);
  assert.ok(mine.body.items[0].urls.thumb && mine.body.items[0].urls.display);
  const res = await S('GET', '/products/p-rice');
  assert.equal(res.body.source, 'shop');
  const file = await h.download(mine.body.items[0].urls.thumb, { token: owner.accessToken });
  assert.equal(file.status, 200);
  assert.ok(!file.buffer.includes(Buffer.from('Exif', 'latin1')));
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('۳۶.۸/IMG-01/IMG-02/IMG-14: ورودِ گروهی — تطبیق، تکراری، مبهم، بی‌تطبیق، عکس‌دار؛ ادامه پس از بستنِ برنامه بی تکرار', async () => {
  //  حسابِ تازه: همگام‌سازیِ آزمون‌های قبل فهرستِ کالای این صفحه را عوض نکند
  const o2 = await h.newUser('صاحبِ دکانِ دوم', 'shop');
  const m2 = await h.post('/api/shop', { name: 'دکانِ دوم' }, { token: o2.accessToken });
  assert.equal(m2.status, 201, JSON.stringify(m2.body));
  await setFlag(m2.body.shop.id, 'shop.image_bank', true);
  const acc2 = { ...ACCOUNT, accessToken: o2.accessToken, refreshToken: o2.refreshToken, userId: o2.user.id };
  const { pg, errors } = await open();
  await signIn(pg, LEDGER, acc2);
  await pg.evaluate(() => window.TohidImageBank.refreshConfig());
  const before = await count('images');
  const a = await jpegFromPage(pg, 640, 480);
  const b = await jpegFromPage(pg, 500, 500);
  const c = await jpegFromPage(pg, 480, 640);
  const d = await jpegFromPage(pg, 320, 320);
  const e = await jpegFromPage(pg, 700, 400);
  const f = await jpegFromPage(pg, 300, 450);
  const csv = 'image_filename,barcode\nlabel-salt.jpg,6260000000024\n';
  await pg.evaluate(() => goTo('products'));
  //  همگام‌سازیِ همان حساب در پس‌زمینه می‌دود؛ فهرستِ کالا باید سر جایش باشد
  await pg.waitForFunction(() => products.some((p) => p.id === 'p-rice' && (p.barcodes || []).includes('6260000000017')) && products.some((p) => p.id === 'p-sugar' && p.photo));
  await pg.locator('#btn-image-bulk').click();
  await pg.waitForSelector('#ib-bulk');
  await pg.locator('#ib-bulk [data-ib-files]').setInputFiles([
    { name: '6260000000017.jpg', mimeType: 'image/jpeg', buffer: a },          // بارکدِ برنج
    { name: 'روغن.jpg', mimeType: 'image/jpeg', buffer: b },                    // نامِ کالا
    { name: 'copy-of-rice.jpg', mimeType: 'image/jpeg', buffer: a },           // همان بایت‌ها ⇒ تکراری
    { name: `${UPC}.jpg`, mimeType: 'image/jpeg', buffer: c },                 // دو کالا با یک GTIN ⇒ مبهم
    { name: 'نامعلوم.jpg', mimeType: 'image/jpeg', buffer: d },                // بی‌تطبیق
    { name: 'label-salt.jpg', mimeType: 'image/jpeg', buffer: e },             // از CSV
    { name: 'map.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) },
    { name: 'شکر.jpg', mimeType: 'image/jpeg', buffer: f },                    // کالا عکس دارد ⇒ بی جایگزینی
    { name: 'readme.txt', mimeType: 'text/plain', buffer: Buffer.from('not an image') },
  ]);
  await pg.waitForFunction(() => document.querySelectorAll('#ib-bulk [data-ib-row]').length === 8);
  const status = async () => pg.evaluate(() => Array.from(document.querySelectorAll('#ib-bulk [data-ib-row]'))
    .map((r) => [r.querySelector('.ib-bfile').textContent, r.querySelector('.ib-pill').textContent]));
  const s0 = Object.fromEntries(await status());
  assert.equal(s0['6260000000017.jpg'], 'آمادهٔ ورود');
  assert.equal(s0['روغن.jpg'], 'آمادهٔ ورود');
  assert.equal(s0['copy-of-rice.jpg'], 'تکراری');
  assert.equal(s0[`${UPC}.jpg`], 'مبهم — انتخاب کنید');
  assert.equal(s0['نامعلوم.jpg'], 'بی‌تطبیق');
  assert.equal(s0['label-salt.jpg'], 'آمادهٔ ورود');
  assert.equal(s0['شکر.jpg'], 'کالا عکس دارد', 'عکسِ موجود بی تیکِ جایگزینی عوض نمی‌شود');
  assert.equal(s0['readme.txt'], 'عکس نیست');
  //  مبهم: هیچ‌کدام خودکار نه — انتخابِ انسان
  await pg.locator(`#ib-bulk [data-ib-row]:has-text("${UPC}") select`).selectOption('p-soda-b');
  await pg.waitForFunction((u) => {
    const r = Array.from(document.querySelectorAll('#ib-bulk [data-ib-row]')).find((x) => x.textContent.includes(u));
    return r && r.querySelector('.ib-pill').textContent === 'آمادهٔ ورود';
  }, UPC);

  //  کندیِ ساختگیِ شبکه فقط برای این‌که «بستنِ برنامه» وسطِ کار بیفتد
  gate = (u, m) => m === 'POST' && /\/api\/shop\/images$/.test(u.pathname);
  await pg.locator('#ib-bulk [data-ib-start]').click();
  await pg.waitForFunction(() => Array.from(document.querySelectorAll('#ib-bulk .ib-pill')).some((p) => p.textContent === 'انجام شد'), null, { timeout: 15000 });
  await pg.reload();   // ⇐ برنامه بسته و باز شد
  gate = null;
  await pg.waitForFunction(() => window.TohidImageBank && window.TohidAccount.isSignedIn());
  await pg.evaluate(() => window.TohidImageBank.refreshConfig());
  await pg.evaluate(() => goTo('products'));
  await pg.locator('#btn-image-bulk').click();
  await pg.waitForSelector('#ib-bulk [data-ib-resume]:not([hidden])');
  assert.match(await pg.locator('#ib-bulk [data-ib-resume]').innerText(), /نیمه‌کاره/);
  assert.equal(await pg.locator('#ib-bulk [data-ib-start]').innerText(), 'ادامهٔ ورود');
  await pg.locator('#ib-bulk [data-ib-start]').click();
  await pg.waitForFunction(() => {
    const t = Array.from(document.querySelectorAll('#ib-bulk .ib-pill')).map((p) => p.textContent);
    return t.filter((x) => x === 'انجام شد').length === 4 && !t.includes('آمادهٔ ورود') && !t.includes('در حالِ کار…');
  }, null, { timeout: 20000 }).catch(async (e) => {
    throw new Error(e.message + ' — ' + JSON.stringify(await status()) + ' ' + JSON.stringify(await pg.evaluate(() => Array.from(document.querySelectorAll('#ib-bulk .ib-bnote')).map((n) => n.textContent))));
  });
  const kpi = await pg.locator('#ib-bulk [data-ib-counts]').innerText();
  assert.match(kpi, /4\s*موفق/); assert.match(kpi, /1\s*ناموفق/); assert.match(kpi, /1\s*تکراری/); assert.match(kpi, /1\s*بی‌تطبیق/); assert.match(kpi, /1\s*عکس دارد/);

  const done = ['p-rice', 'p-oil', 'p-soda-b', 'p-salt'];
  for (const id of done) {
    assert.ok(await pg.evaluate((x) => photoGet(x).then((bl) => !!bl), id), 'عکسِ دستگاه: ' + id);
    const r = await S('GET', '/products/' + id, null, o2);
    assert.equal(r.body.source, 'shop', id);
  }
  assert.equal(await pg.evaluate(() => photoGet('p-soda-a').then((bl) => !!bl)), false, 'کالای دیگرِ مبهم دست نخورد');
  assert.equal(await count('images') - before, done.length, 'ادامه هیچ عکسی را دو بار بارگذاری نکرد');
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('منطقِ خالصِ تطبیق: پسوندِ شماره، CSV، GTIN ۱۲/۱۳ رقمی', async () => {
  const { pg } = await open();
  const r = await pg.evaluate(() => {
    const B = window.TohidImageBank;
    const list = [
      { id: 'a', name: 'چای سبز', barcodes: ['6260000000017'] },
      { id: 'b', name: 'شیر', barcodes: ['036000291452'] },
      { id: 'c', name: 'شیر وارداتی', barcodes: ['0036000291452'] },
    ];
    return {
      suffix: B.matchFile('6260000000017_2.jpg', list).productId,
      paren: B.matchFile('چای سبز (3).png', list).productId,
      underscore: B.matchFile('چای_سبز.jpg', list).productId,
      persianDigits: B.matchFile('۶۲۶۰۰۰۰۰۰۰۰۱۷.jpg', list).productId,
      gtin: B.matchFile('036000291452.jpg', list).candidates.sort(),
      csv: B.matchFile('x.jpg', list, B.parseCsv('file;name\nX.JPG;شیر\n')).productId,
      none: B.matchFile('شیرینی.jpg', list).status,
      badGtin: B.gtin14('6260000000018'),
    };
  });
  assert.deepEqual(r, { suffix: 'a', paren: 'a', underscore: 'a', persianDigits: 'a', gtin: ['b', 'c'], csv: 'b', none: 'unmatched', badGtin: '' });
  await pg.close();
});

await browser.close();
statics.close();
await h.stop();
if (failed) { console.log(`\n${failed} آزمون شکست خورد`); process.exit(1); }
console.log('\nهمهٔ آزمون‌های بانکِ تصاویرِ وب گذشتند');
