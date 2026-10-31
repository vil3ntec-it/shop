/**
 * SEC-API-01 — XSS ِ ذخیره‌شده در نسخهٔ وب از راهِ دادهٔ همگام‌شده (برنامهٔ امنیت §۱۱، `docs/security/api-web-fa.md`).
 *
 * دفترِ وب از همگام‌سازی، پشتیبان و دستگاهِ دیگرِ همان دکان پر می‌شود؛ شاگردی که «ثبتِ اطلاعات» دارد
 * می‌تواند ردیفی با شناسه، نوع یا تاریخِ دلخواه بفرستد (سرور شناسه را تا ۸۰ نویسه هر چیزی می‌پذیرد و
 * `data` را دست نمی‌زند). تا ۲.۱۴.۵ همین مقدارها خام در `innerHTML` می‌نشستند (‎data-debtor-id="${d.id}"‎،
 * ‎class="tx-item ${t.type}"‎، `formatDate` که تاریخِ ناجور را خام برمی‌گرداند…) و CSP ِ وب
 * `'unsafe-inline'` دارد ⇒ اسکریپتِ شاگرد در مرورگرِ صاحبِ دکان با توکنِ او اجرا می‌شد.
 *
 * این آزمون دفتری می‌سازد که **همهٔ** شناسه‌ها، نوع‌ها، تاریخ‌ها و مقدارها در آن بارِ HTML دارند، همهٔ
 * صفحه‌ها را باز می‌کند و می‌سنجد هیچ عنصری تزریق نشد و هیچ اسکریپتی نرفت — و ردیف‌ها واقعاً کشیده شدند.
 *
 * اجرا:  node test/web-xss-sync.mjs
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

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

/** بارِ تزریق — در متن، در ویژگیِ "…" و '…'؛ هر کدام شماره‌ی خودش را دارد تا معلوم شود کدام راه باز است */
const X = (n) => `x${n}'"><img src=x data-xss=${n} onerror=__xss.push(${n})>`;
assert.ok(X(99).length <= 80, 'شناسه‌ی همگام‌سازی تا ۸۰ نویسه');

const LEDGER = {
  products: [
    { id: X(1), name: 'برنجِ نشانه', unit: 'کیلو', salePrice: 999, purchasePrice: 200, category: 'خوراکی', minStock: 5 },
    { id: X(2), name: 'روغنِ نشانه', unit: X(3), salePrice: 50, purchasePrice: 20, category: 'خوراکی', minStock: 0 },
  ],
  warehouseEntries: [
    { id: X(4), productId: X(1), units: 3, date: X(5), notes: '', batch: '', expiryDate: X(6) },
    { id: X(7), productId: X(2), units: 2, date: '2020-01-01', notes: '' },
  ],
  debtors: [{ id: X(8), name: 'احمدِ نشانه', phone: '0790000000', notes: '', createdAt: 1 }],
  sales: [{
    id: X(9), invoiceNumber: 1, debtorId: X(8), total: 999, discountType: 'amount', discountValue: 0, discount: 0,
    finalTotal: 999, paymentMethod: 'credit', paidAmount: 0, remaining: 999, status: 'completed',
    debtGiven: 999, debtSettled: 0, date: X(10), createdAt: 1,
  }],
  saleItems: [{ id: X(11), saleId: X(9), productId: X(1), quantity: 1, unitPrice: 999, purchasePrice: 200, totalPrice: 999, returnedQty: 0 }],
  transactions: [
    { id: X(12), debtorId: X(8), type: 'give', amount: 999, date: X(13), notes: 'فروش نسیه', createdAt: 1 },
    { id: X(14), debtorId: X(8), type: X(15), amount: 5, date: '2020-01-02', notes: '', createdAt: 2 },
  ],
  expenses: [{ id: X(16), title: 'کرایهٔ نشانه', amount: 50, category: 'سایر', date: X(17), notes: '', createdAt: 1 }],
  suppliers: [{ id: X(18), name: 'شرکتِ نشانه', phone: '', notes: '', createdAt: 1 }],
  purchases: [{ id: X(19), supplierId: X(18), title: 'خریدِ نشانه', total: 100, paid: 0, date: X(20), notes: '', items: [], createdAt: 1 }],
  supplierPayments: [{ id: X(21), supplierId: X(18), amount: 10, date: X(22), notes: '', createdAt: 2 }],
  stockMovements: [{ id: X(23), productId: X(1), type: X(24), qty: 1, date: X(25), notes: '', createdAt: 1 }],
  returns: [],
  auditLog: [{ id: X(26), type: X(27), date: X(28), notes: 'نشانهٔ سابقه', createdAt: 1 }],
  //  فهرست‌های تنظیماتِ همگام‌شده (SETTING_LISTS) و دسته‌ها
  productCategories: ['خوراکی', X(30)],
  productUnits: ['کیلو', X(31)],
  expenseCategories: ['سایر', X(32)],
};
LEDGER.products.push({ id: X(33), name: 'صابونِ نشانه', unit: 'دانه', salePrice: 5, purchasePrice: 2, category: X(34), subcategory: X(35), minStock: 0 });
LEDGER.expenses.push({ id: X(36), title: 'برقِ نشانه', amount: X(37), category: X(38), date: '2020-01-03', notes: '', createdAt: 2 });

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

const pg = await browser.newPage({ viewport: { width: 1366, height: 900 } });
await pg.addInitScript(() => { window.__xss = []; });
await pg.goto(url);
await pg.evaluate((d) => {
  localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
  localStorage.setItem('tohid-entitlement-v1', JSON.stringify({ source: 'paid', isPaid: true, features: ['sales', 'debtors', 'barcode', 'reports', 'purchasing'], trial: { used: true, active: false, daysLeft: 0 } }));
}, LEDGER);
await pg.reload();
await pg.waitForTimeout(1200);
const skip = pg.getByText('ادامه بدون حساب');
if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }

/** همهٔ تزریق‌ها تا این لحظه: اسکریپتِ اجراشده و عنصرِ ساخته‌شده */
const injected = () => pg.evaluate(() => ({
  ran: [...new Set(window.__xss)].sort((a, b) => a - b),
  nodes: [...document.querySelectorAll('[data-xss]')].map((e) => e.getAttribute('data-xss')),
}));
const clean = async (where) => {
  await pg.waitForTimeout(250);   // onerror ِ عکسِ شکسته ناهمگام است
  const out = await injected();
  assert.deepEqual(out, { ran: [], nodes: [] }, `${where}: تزریق از راهِ بارهای ${JSON.stringify(out)}`);
};
const page = async (id) => {
  await pg.evaluate((h) => { location.hash = h; }, `#${id}`);
  await pg.waitForTimeout(500);
};
const visible = async (text) => pg.evaluate((t) => document.body.innerText.includes(t), text);

for (const [id, mustSee] of [
  ['dashboard', null], ['debtors', 'احمدِ نشانه'], ['warehouse', 'برنجِ نشانه'], ['expenses', 'کرایهٔ نشانه'],
  ['products', 'روغنِ نشانه'], ['sales', 'احمدِ نشانه'], ['purchasing', 'شرکتِ نشانه'], ['reports', null],
  ['audit-log', 'نشانهٔ سابقه'], ['sale', null], ['quick-sale', null], ['settings', null], ['receipts', null],
  ['team', null], ['devices', null], ['profile', null], ['orders', null], ['admin', null], ['more', null],
]) {
  await step(`صفحهٔ ${id}: ردیف‌ها کشیده شدند و هیچ تزریقی نرفت`, async () => {
    await page(id);
    if (mustSee) assert.ok(await visible(mustSee), `${id}: «${mustSee}» کشیده نشد — آزمون چیزی را نمی‌سنجد`);
    await clean(id);
  });
}

await step('حسابِ قرض‌دار (ردیف‌ها، بردِ فروش، رسید) و فهرستِ جدولی', async () => {
  await page('debtors');
  await pg.evaluate((id) => openAccount(id), X(8));
  await pg.waitForTimeout(400);
  assert.ok(await visible('فروش نسیه'), 'ردیفِ حساب کشیده نشد');
  await clean('حساب');
  const table = pg.locator('[data-debtor-view="table"]');
  if (await table.count()) { await table.first().click().catch(() => {}); await pg.waitForTimeout(300); }
  await clean('جدولِ قرض‌داران');
});

await step('تأمین‌کننده و تاریخچهٔ خرید', async () => {
  await page('purchasing');
  for (const tab of await pg.locator('[data-pur-tab], [data-tab]').all()) { await tab.click().catch(() => {}); await pg.waitForTimeout(150); }
  await clean('خرید');
});

await step('سبدِ فروش با کالای نشانه، و فروشِ سریع', async () => {
  await page('sale');
  await pg.evaluate((ids) => { for (const id of ids) addToCart(id); }, [X(1), X(2), X(33)]);
  await pg.waitForTimeout(400);
  //  کشوی سبد بسته است (innerText پنهان را نمی‌بیند) ⇒ textContent ِ خودِ فهرست
  assert.ok(await pg.evaluate(() => (document.getElementById('cart-list') || {}).textContent?.includes('برنجِ نشانه')), 'کالا در سبد کشیده نشد');
  await clean('سبد');
  await page('quick-sale');
  for (const v of await pg.locator('#quick-select-category option').evaluateAll((os) => os.map((o) => o.value))) {
    await pg.selectOption('#quick-select-category', v).catch(() => {});
    await pg.waitForTimeout(150);
  }
  await clean('فروشِ سریع');
});

await step('جزئیاتِ کالا، حسابِ تأمین‌کننده و فرمِ مصرف', async () => {
  for (const id of [X(1), X(2), X(33)]) {
    await pg.evaluate((i) => openProductDetail(i), id).catch(() => {});
    await pg.waitForTimeout(250);
  }
  await clean('جزئیاتِ کالا');
  await pg.evaluate((i) => openSupplierAccount(i), X(18));
  await pg.waitForTimeout(300);
  assert.ok(await visible('شرکتِ نشانه'));
  await clean('حسابِ تأمین‌کننده');
  await page('expenses');
  for (const b of await pg.locator('[data-edit-expense], [data-exp-edit], #btn-add-expense').all()) {
    await b.click().catch(() => {}); await pg.waitForTimeout(200);
    await pg.keyboard.press('Escape').catch(() => {});
  }
  await clean('فرمِ مصرف');
});

await pg.close();
await browser.close();
server.close();
if (failed) { console.log(`\n${failed} گام شکست خورد`); process.exit(1); }
console.log('\nهمه سبز');
