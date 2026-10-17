/**
 * نسخهٔ وب — پیش‌نویسِ فاکتور و فروشِ تکراری، با مرورگرِ واقعی.
 *
 *   POS-13 · سبدِ نیمه‌کاره پس از بسته شدنِ ناگهانی برمی‌گردد و پرسیده
 *            می‌شود (ادامه / دور انداختن)؛ پیش‌نویسِ ثبت‌شده دوباره نمی‌آید (S10)
 *   POS-14 · کلیدِ یکتای فاکتور = شناسهٔ فروش؛ دکمهٔ ثبت تا پایانِ ثبت قفل؛
 *            پنج کلیک/Enter ⇒ یک فروش (S11 مرحلهٔ ۱)
 *   PRD-18 · کالای غیرفعال در فروش نمی‌آید و با اسکن هم رد می‌شود؛ سابقه‌اش می‌ماند
 *
 * «بستنِ ناگهانی» = بستنِ برگه بی beforeunload در همان پروفایل؛ localStorage
 * همان‌جا می‌ماند. (kill -9 ِ خودِ مرورگر در این آزمون نیست.)
 *
 * اجرا:  node test/web-pos-draft.mjs
 * نبودِ پلی‌رایت قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

//  WEB_ROOT فقط برای سنجیدنِ نسخهٔ دیگری از صفحه (مثلاً main پیش از این تغییر)
const ROOT = process.env.WEB_ROOT ? path.resolve(process.env.WEB_ROOT) : path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

const DATA_KEY = 'tohid-shop-data-v1';
const V1 = 'tohid-shop-draft-cart-v1';
const V2 = 'tohid-shop-draft-sale-v2';
const LEDGER = {
  products: [
    { id: 'p1', name: 'برنج', unit: 'عدد', salePrice: 300, purchasePrice: 200, barcodes: ['111'] },
    { id: 'p2', name: 'روغنِ کهنه', unit: 'عدد', salePrice: 150, purchasePrice: 100, barcodes: ['222'], active: false },
  ],
  warehouseEntries: [
    { id: 'w1', productId: 'p1', units: 50, date: '2026-10-01' },
    { id: 'w2', productId: 'p2', units: 50, date: '2026-10-01' },
  ],
  //  فروشِ قدیمیِ کالایی که بعد غیرفعال شد — سابقه باید بماند
  sales: [{ id: 'old1', invoiceNumber: 1, total: 150, finalTotal: 150, discount: 0, paidAmount: 150, remaining: 0, date: '2026-10-02', createdAt: 1, status: 'completed', paymentMethod: 'cash' }],
  saleItems: [{ id: 'oi1', saleId: 'old1', productId: 'p2', quantity: 1, unitPrice: 150, purchasePrice: 100, totalPrice: 150, returnedQty: 0 }],
  nextInvoiceNo: 2,
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

/** پروفایلِ تازه با دفتر و (اختیاری) پیش‌نویسِ آماده */
async function profile(seed = {}) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 800 } });
  await ctx.addInitScript(([seedJson]) => {
    //  فقط یک بار: بارِ بعدی همان چیزی را می‌بیند که صفحه نوشته است
    if (sessionStorage.getItem('seeded')) return;
    if (localStorage.getItem('seeded-profile')) return;
    const s = JSON.parse(seedJson);
    for (const [k, v] of Object.entries(s)) localStorage.setItem(k, typeof v === 'string' ? v : JSON.stringify(v));
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    localStorage.setItem('seeded-profile', '1');
  }, [JSON.stringify({ [DATA_KEY]: LEDGER, ...seed })]);
  return ctx;
}
async function openIn(ctx) {
  const pg = await ctx.newPage();
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.goto(url);
  await pg.waitForFunction(() => typeof window.goTo === 'function' && document.readyState === 'complete');
  await pg.waitForTimeout(600);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(200); }
  return { pg, errors };
}
const store = (pg) => pg.evaluate(([a, b, c]) => ({
  data: JSON.parse(localStorage.getItem(a) || '{}'),
  v1: JSON.parse(localStorage.getItem(b) || 'null'),
  v2: JSON.parse(localStorage.getItem(c) || 'null'),
}), [DATA_KEY, V1, V2]);
const draftOpen = (pg) => pg.locator('#modal-sale-draft.open').count();
const toastText = (pg) => pg.locator('#toast-msg').innerText().catch(() => '');
/** «kill»: برگه بی beforeunload بسته می‌شود */
const kill = (pg) => pg.close({ runBeforeUnload: false });

await step('POS-13: سبدِ نیمه‌کاره پس از بستنِ ناگهانی برمی‌گردد و پرسیده می‌شود؛ «ادامه» همان کلید را نگه می‌دارد', async () => {
  const ctx = await profile();
  let { pg, errors } = await openIn(ctx);
  assert.equal(await draftOpen(pg), 0, 'سبدِ خالی نباید پرسشی بیاورد');
  await pg.evaluate(() => { goTo('sale'); addToCart('p1', 2); addToCart('p1', 2); });
  const before = await store(pg);
  assert.ok(before.v2 && before.v2.key, 'پیش‌نویس باید کلیدِ فاکتور داشته باشد');
  assert.equal(before.v2.lines[0].quantity, 4);
  assert.deepEqual(before.v1, before.v2.lines, 'کلیدِ v1 (برای shop-sync.js) همان ردیف‌ها');
  await kill(pg);
  ({ pg, errors } = await openIn(ctx));
  await pg.waitForSelector('#modal-sale-draft.open', { timeout: 5000 });
  const sub = await pg.locator('#sale-draft-sub').innerText();
  assert.match(sub, /1 قلم/);
  assert.match(sub, /1,200/);
  await pg.click('#btn-sale-draft-resume');
  assert.equal(await draftOpen(pg), 0);
  const after = await store(pg);
  assert.equal(after.v2.key, before.v2.key, 'ادامه کلید را عوض نمی‌کند');
  assert.deepEqual(await pg.evaluate(() => cart.map(c => [c.productId, c.quantity])), [['p1', 4]]);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('POS-13: «دور انداختن» سبد را خالی می‌کند و پرسش دیگر نمی‌آید', async () => {
  const ctx = await profile({ [V2]: { v: 2, key: '01DRAFTDISCARD', lines: [{ productId: 'p1', quantity: 1 }], savedAt: 1 } });
  let { pg } = await openIn(ctx);
  await pg.waitForSelector('#modal-sale-draft.open', { timeout: 5000 });
  await pg.click('#btn-sale-draft-discard');
  const s = await store(pg);
  assert.deepEqual(s.v2.lines, []);
  assert.equal(s.v2.key, null);
  assert.deepEqual(s.v1, []);
  await kill(pg);
  ({ pg } = await openIn(ctx));
  assert.equal(await draftOpen(pg), 0);
  await ctx.close();
});

await step('POS-13 / PRD-18: بازیابی کالای غیرفعال و حذف‌شده را کنار می‌گذارد و می‌گوید', async () => {
  const ctx = await profile({ [V2]: { v: 2, key: '01DRAFTMIXED', lines: [{ productId: 'p1', quantity: 1 }, { productId: 'p2', quantity: 1 }, { productId: 'gone', quantity: 1 }], savedAt: 1 } });
  const { pg } = await openIn(ctx);
  await pg.waitForSelector('#modal-sale-draft.open', { timeout: 5000 });
  assert.match(await pg.locator('#sale-draft-sub').innerText(), /2 قلم که کالایش حذف یا غیرفعال شده/);
  assert.deepEqual(await pg.evaluate(() => cart.map(c => c.productId)), ['p1']);
  await ctx.close();
});

await step('POS-13: پیش‌نویسِ قدیمی (v1، بی کلید) هم برمی‌گردد و کلید می‌گیرد', async () => {
  const ctx = await profile({ [V1]: [{ productId: 'p1', quantity: 3 }] });
  const { pg } = await openIn(ctx);
  await pg.waitForSelector('#modal-sale-draft.open', { timeout: 5000 });
  const s = await store(pg);
  assert.ok(s.v2 && s.v2.key, 'پیش‌نویسِ v1 به v2 با کلید آورده شود');
  assert.equal(s.v2.lines[0].quantity, 3);
  await ctx.close();
});

await step('POS-14: پنج کلیک و پنج Enter روی «تایید و ثبت فروش» ⇒ یک فروش، با شناسهٔ همان کلید؛ دکمه وسطِ ثبت قفل است', async () => {
  const ctx = await profile();
  const { pg, errors } = await openIn(ctx);
  await pg.evaluate(() => { goTo('sale'); addToCart('p1', 1); openSaleCheckoutModal(); });
  const key = (await store(pg)).v2.key;
  //  قفل: دکمه هنگامِ نوشتنِ دفتر خاموش است
  await pg.evaluate(() => {
    const real = window.saveState;
    window.__lockSeen = [];
    window.saveState = function () { window.__lockSeen.push(document.querySelector('#form-sale-checkout [type="submit"]').disabled); return real.apply(this, arguments); };
  });
  await pg.evaluate(() => {
    const btn = document.querySelector('#form-sale-checkout [type="submit"]');
    for (let i = 0; i < 5; i++) btn.click();
  });
  await pg.keyboard.press('Enter'); await pg.keyboard.press('Enter'); await pg.keyboard.press('Enter');
  await pg.waitForTimeout(300);
  const s = await store(pg);
  const fresh = s.data.sales.filter(x => x.id !== 'old1');
  assert.equal(fresh.length, 1, `فروش‌های تازه: ${fresh.length}`);
  assert.equal(fresh[0].id, key, 'شناسهٔ فروش = کلیدِ پیش‌نویس');
  assert.equal(s.data.saleItems.filter(x => x.saleId === key).length, 1);
  assert.deepEqual(await pg.evaluate(() => window.__lockSeen), [true], 'هنگامِ ثبت، دکمه قفل');
  assert.equal(await pg.evaluate(() => document.querySelector('#form-sale-checkout [type="submit"]').disabled), false, 'پس از ثبت، دکمه باز');
  assert.deepEqual(s.v2.lines, []);
  assert.equal(s.v2.key, null, 'سبدِ بعدی کلیدِ تازه می‌گیرد');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('S10: برق میانِ «دفتر نوشته شد» و «پیش‌نویس پاک شد» ⇒ پس از باز شدن دوباره ثبت نمی‌شود', async () => {
  const ctx = await profile();
  let { pg } = await openIn(ctx);
  await pg.evaluate(() => { goTo('sale'); addToCart('p1', 2); });
  const draftBefore = await pg.evaluate((k) => localStorage.getItem(k), V2);
  const key = JSON.parse(draftBefore).key;
  await pg.evaluate(() => { openSaleCheckoutModal(); document.querySelector('#form-sale-checkout [type="submit"]').click(); });
  //  پاک‌کردنِ پیش‌نویس به دیسک نرسیده بود
  await pg.evaluate(([k, v]) => localStorage.setItem(k, v), [V2, draftBefore]);
  await kill(pg);
  ({ pg } = await openIn(ctx));
  await pg.waitForTimeout(300);
  assert.equal(await draftOpen(pg), 0, 'پیش‌نویسِ ثبت‌شده نباید دوباره پیشنهاد شود');
  assert.match(await toastText(pg), /پیش‌تر ثبت شده بود/);
  assert.deepEqual(await pg.evaluate(() => cart), []);
  const s = await store(pg);
  assert.equal(s.data.sales.filter(x => x.id === key).length, 1);
  assert.equal(s.v2.key, null);
  //  و اگر کسی همان کلید را دستی ثبت کند، باز هم یک فروش
  await pg.evaluate((k) => { addToCart('p1', 1); draftSaleKey = k; openSaleCheckoutModal(); document.querySelector('#form-sale-checkout [type="submit"]').click(); }, key);
  const s2 = await store(pg);
  assert.equal(s2.data.sales.filter(x => x.id === key).length, 1, 'همان کلید دو بار ثبت شد');
  await ctx.close();
});

await step('PRD-18: کالای غیرفعال در شبکهٔ فروش نیست، اسکنش رد می‌شود و ثبت نمی‌شود؛ سابقه‌اش می‌ماند', async () => {
  const ctx = await profile();
  const { pg, errors } = await openIn(ctx);
  await pg.evaluate(() => { goTo('sale'); renderQuickSelectGrid(); });
  const grid = await pg.locator('#quick-select-grid').innerText().catch(() => '');
  assert.ok(!grid.includes('روغنِ کهنه'), 'کالای غیرفعال در شبکهٔ فروش آمد');
  await pg.evaluate(() => handleBarcodeScanned('222', { skipDedup: true }));
  assert.deepEqual(await pg.evaluate(() => cart), [], 'کالای غیرفعال با اسکن به سبد رفت');
  const t = await toastText(pg);
  assert.match(t, /غیرفعال است/, `پیام: ${t}`);
  assert.doesNotMatch(t, /اضافه شد/);
  await pg.evaluate(() => handleBarcodeScanned('111', { skipDedup: true }));
  assert.deepEqual(await pg.evaluate(() => cart.map(c => c.productId)), ['p1']);
  //  سابقه: فروشِ قدیمیِ کالای غیرفعال و خودِ کالا در دفتر می‌مانند
  const s = await store(pg);
  assert.ok(s.data.saleItems.some(x => x.productId === 'p2'));
  assert.ok(s.data.products.some(x => x.id === 'p2' && x.active === false));
  assert.deepEqual(errors, []);
  await ctx.close();
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
