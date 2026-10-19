/**
 * نسخهٔ وب — رقمِ فارسی/پشتو/عربی در کادرِ عدد و بارکدخوان (F0-06)، با مرورگرِ واقعی.
 *
 *   • کادرِ `type=number` با «۵۰۰»، «٥٠٠» و «۱٫۵» — کروم رقمِ فارسی را نمی‌پذیرفت و
 *     کادر خالی می‌ماند؛ تخفیف بی‌صدا صفر می‌شد
 *   • تخفیفِ نامعتبر خطای کنارِ کادر است و فروش ثبت نمی‌شود — هرگز صفر
 *   • بارکدخوان با چیدمانِ فارسی و پشتو (`e.key` «۶»/«ش»، `e.code` Digit6/KeyA)
 *   • بارکدِ دستی با رقمِ عربی
 *
 * اجرا:  node test/web-digits.mjs
 */
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) { console.log('⏭  پلی‌رایت پیدا نشد — این سنجه رد شد'); process.exit(0); }

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

const LEDGER = {
  products: [
    { id: 'p1', name: 'نوشابه', unit: 'عدد', salePrice: 1000, purchasePrice: 800, category: 'نوشیدنی', minStock: 0, barcodes: ['6260100120014'] },
    { id: 'p2', name: 'صابون', unit: 'عدد', salePrice: 500, purchasePrice: 300, category: 'بهداشتی', minStock: 0, barcodes: ['AB12'] },
  ],
  warehouseEntries: [
    { id: 'w1', productId: 'p1', units: 100, date: '2026-10-01' },
    { id: 'w2', productId: 'p2', units: 100, date: '2026-10-01' },
  ],
  debtors: [], transactions: [], sales: [], saleItems: [],
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open() {
  const pg = await browser.newPage({ viewport: { width: 1366, height: 800 } });
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.goto(url);
  await pg.evaluate(d => localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d)), LEDGER);
  await pg.reload();
  await pg.waitForTimeout(1200);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }
  await pg.evaluate(() => { goTo('sale'); cart.length = 0; renderCart(); });
  await pg.waitForTimeout(200);
  return { pg, errors };
}

/** بارکدخوان: کلید پشتِ کلید، همان `key`/`code`ی که مرورگر با این چیدمان می‌سازد */
async function scan(pg, keys) {
  await pg.evaluate((ks) => {
    if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
    for (const [key, code, shiftKey] of ks) {
      document.dispatchEvent(new KeyboardEvent('keydown', { key, code, shiftKey: !!shiftKey, bubbles: true }));
    }
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true }));
  }, keys);
  await pg.waitForTimeout(100);
}
const qtyOf = (pg, id) => pg.evaluate(x => (cart.find(c => c.productId === x) || { quantity: 0 }).quantity, id);
const FA = '۰۱۲۳۴۵۶۷۸۹';

await step('بارکدخوان با چیدمانِ فارسی (رقم‌ها «۶۲۶…») کالا را به سبد می‌برد', async () => {
  const { pg, errors } = await open();
  await scan(pg, [...'6260100120014'].map(d => [FA[+d], `Digit${d}`]));
  assert.equal(await qtyOf(pg, 'p1'), 1);
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('بارکدخوان با چیدمانِ پشتو — Code128 با حرف (KeyA/KeyB + Shift) و رقمِ ماشین‌حساب', async () => {
  const { pg } = await open();
  //  پشتو: کلیدِ A «ښ»/Shift «آ»، کلیدِ B «ذ»؛ رقم‌ها همان ۰-۹
  await scan(pg, [['آ', 'KeyA', true], ['ئ', 'KeyB', true], ['۱', 'Numpad1'], ['۲', 'Digit2']]);
  assert.equal(await qtyOf(pg, 'p2'), 1);
  await pg.close();
});

await step('چیدمانِ انگلیسی مثلِ پیش', async () => {
  const { pg } = await open();
  await scan(pg, [...'6260100120014'].map(d => [d, `Digit${d}`]));
  assert.equal(await qtyOf(pg, 'p1'), 1);
  await pg.close();
});

await step('بارکدِ دستی با رقمِ عربی «٦٢٦…»', async () => {
  const { pg } = await open();
  //  کادرِ «بارکدِ دستی» از ۶۰۰ پیکسل به بالا جایش را به یک جست‌وجوی صندوق داده (DESIGN v2: یک جست‌وجو)؛
  //  همان کادر روی گوشی سرِ جایش است و همان‌جا سنجیده می‌شود
  await pg.setViewportSize({ width: 390, height: 844 });
  await pg.fill('#in-manual-barcode', '');
  await pg.focus('#in-manual-barcode');
  await pg.keyboard.insertText('٦٢٦٠١٠٠١٢٠٠١٤');
  assert.equal(await pg.inputValue('#in-manual-barcode'), '6260100120014');
  //  خودِ مسیرِ بارکد هم (دکمه پشتِ قفلِ VIP است؛ همان تابعی که دکمه صدا می‌زند)
  await pg.evaluate(() => handleBarcodeScanned('٦٢٦٠١٠٠١٢٠٠١٤', { skipDedup: true }));
  await pg.waitForTimeout(100);
  assert.equal(await qtyOf(pg, 'p1'), 1);
  await pg.close();
});

await step('جست‌وجوی صندوق (کامپیوتر) با رقمِ عربی و فارسی: بارکد + Enter همان کالا را می‌آورد', async () => {
  const { pg } = await open();
  await pg.focus('#pos-search');
  await pg.keyboard.insertText('٦٢٦٠١٠٠١٢٠٠١٤');
  await pg.keyboard.press('Enter');
  await pg.keyboard.insertText('۶۲۶۰۱۰۰۱۲۰۰۱۴');
  await pg.keyboard.press('Enter');
  await pg.waitForTimeout(100);
  assert.equal(await qtyOf(pg, 'p1'), 2);
  await pg.close();
});

await step('تسویه: «۵۰۰»، «٥٠٠» و «۱٫۵» در کادرهای عدد خوانده می‌شوند، نه صفر', async () => {
  const { pg, errors } = await open();
  await pg.evaluate(() => { addToCart('p1', 2); renderCart(); openSaleCheckoutModal(); });
  await pg.waitForTimeout(200);
  //  نسیه — در نقدی «پرداختی» خودش کلِ مبلغ می‌شود
  await pg.evaluate(() => document.querySelector('#co-payment-type .seg-btn[data-val=credit]').click());
  for (const [txt, want] of [['۵۰۰', '500'], ['٥٠٠', '500'], ['۱٫۵', '1.5']]) {
    for (const id of ['#co-discount-value', '#co-paid-amount']) {
      await pg.fill(id, '');
      await pg.focus(id);
      await pg.keyboard.insertText(txt);
      assert.equal(await pg.inputValue(id), want, `${id} ← ${txt}`);
    }
  }
  //  ۱٫۵٪ از ۲۰۰۰ = ۳۰
  await pg.fill('#co-discount-value', '');
  await pg.focus('#co-discount-value');
  await pg.keyboard.type('۱٫۵');
  const t = await pg.evaluate(() => computeCheckoutTotals());
  assert.equal(t.discountValue, 1.5);
  assert.equal(t.discount, 30);
  assert.deepEqual(errors, []);
  await pg.close();
});

//  کادرِ `type=number` در فرمی بی `novalidate` است: مرورگر خودش کنارِ کادر
//  خطا نشان می‌دهد و `submit` اصلاً نمی‌رسد — پس تخفیفِ نامعتبر صفر نمی‌شود.
await step('تخفیفِ نامعتبر خطای کنارِ کادر است و فروش ثبت نمی‌شود', async () => {
  const { pg } = await open();
  await pg.evaluate(() => { addToCart('p1', 1); renderCart(); openSaleCheckoutModal(); });
  await pg.waitForTimeout(200);
  const before = await pg.evaluate(() => sales.length);
  await pg.fill('#co-discount-value', '');
  await pg.focus('#co-discount-value');
  //  کروم نقطهٔ دوم را خودش نمی‌گذارد؛ «5-» عدد نیست
  await pg.keyboard.type('5-');
  assert.equal(await pg.evaluate(() => document.getElementById('co-discount-value').validity.badInput), true, 'badInput');
  await pg.click('#modal-sale-checkout button[type=submit]');
  await pg.waitForTimeout(200);
  assert.equal(await pg.evaluate(() => sales.length), before, 'فروش نباید ثبت شود');
  assert.equal(await pg.evaluate(() => document.getElementById('form-sale-checkout').checkValidity()), false, 'فرم نامعتبر');
  await pg.close();
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
