/**
 * دفترِ قرض‌داران در وب (v3 §22.2-A5، §21.11، CRM-03، VISUAL-DIRECTION §۱٫۵) — با مرورگرِ واقعی.
 *
 *   • دفترِ قدیمی (بی dueDate/creditLimit) همان مانده را نشان می‌دهد و ردیف‌هایش دست نمی‌خورند
 *   • زیرِ بردِ فروشِ نسیه: اقلام با قیمتِ همان لحظه و وزن، سررسید، باقیِ همان برد، مانده پس از ردیف
 *   • رسیدِ هر ردیف (چاپ/فرستادن) و سررسیدِ بعدی؛ ردِ دفترچهٔ ثبت
 *   • سقفِ اعتبار: بردِ بیش از سقف فقط با تأیید، با ردِ دفترچهٔ ثبت
 *   • فهرستِ قرض‌داران: نمای جدولی/کارتی، ماندگار؛ گوشی همیشه کارتی
 *
 * اجرا:  node test/web-debtors-ledger.mjs
 * نبودِ پلی‌رایت قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
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
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

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

/** دفترِ قدیمی — همان شکلی که نسخه‌های قبل نوشته‌اند: نه dueDate، نه creditLimit */
const LEDGER = {
  products: [{ id: 'p1', name: 'برنج', unit: 'کیلو', salePrice: 999, purchasePrice: 200, category: 'خوراکی', minStock: 0 }],
  warehouseEntries: [{ id: 'w', productId: 'p1', units: 100, date: '2020-01-01' }],
  debtors: [{ id: 'c1', name: 'احمد کریمی', phone: '0790000000', notes: '', createdAt: 1 }],
  sales: [{ id: 's1', invoiceNumber: 1, debtorId: 'c1', total: 375, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: 375, paymentMethod: 'credit', paidAmount: 75, remaining: 300, status: 'completed', debtGiven: 300, debtSettled: 0, date: '2020-01-01', createdAt: 1 }],
  saleItems: [{ id: 'i1', saleId: 's1', productId: 'p1', quantity: 1.25, unitPrice: 300, purchasePrice: 200, totalPrice: 375, returnedQty: 0 }],
  transactions: [
    { id: 't1', debtorId: 'c1', type: 'give', amount: 300, date: '2020-01-01', notes: 'فروش نسیه — فاکتور #1', createdAt: 1 },
    { id: 't2', debtorId: 'c1', type: 'receive', amount: 100, date: '2020-01-05', notes: '', createdAt: 2 },
  ],
  auditLog: [],
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open(width, ledger = LEDGER) {
  const pg = await browser.newPage({ viewport: { width, height: 900 } });
  const errors = [];
  pg.on('pageerror', (e) => errors.push(String(e)));
  await pg.goto(url);
  await pg.evaluate((d) => {
    localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
    //  اشتراکِ فعالِ کش‌شده — همان چیزی که `vip.js` برای دکانِ پولیِ آفلاین می‌خواند؛
    //  بی آن صفحهٔ قرض‌داران زیرِ پردهٔ «مشاهده اشتراک‌ها» می‌ماند
    localStorage.setItem('tohid-entitlement-v1', JSON.stringify({ source: 'paid', isPaid: true, features: ['sales', 'debtors', 'barcode'], trial: { used: true, active: false, daysLeft: 0 } }));
  }, ledger);
  await pg.reload();
  await pg.waitForTimeout(1200);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }
  return { pg, errors };
}
const stored = (pg) => pg.evaluate(() => JSON.parse(localStorage.getItem('tohid-shop-data-v1')));

await step('دفترِ قدیمی: همان مانده؛ اقلام، سررسید، باقی و مانده پس از هر ردیف؛ رسید', async () => {
  const old = JSON.parse(JSON.stringify(LEDGER));
  old.transactions[0].dueDate = '2020-01-10';      // سررسیدِ گذشته ⇒ عقب‌افتاده
  const { pg, errors } = await open(1366, old);
  await pg.evaluate(() => openAccount('c1'));
  await pg.waitForTimeout(200);
  assert.equal((await pg.locator('#acc-amount').innerText()).trim(), '200');
  const list = await pg.locator('#tx-list').innerText();
  assert.match(list, /برنج — 1\.25 کیلو × 300 = 375/);
  assert.match(list, /پرداختِ همان لحظه 75/);
  assert.match(list, /سررسید .* — گذشته/);
  assert.match(list, /باز 200/);
  assert.match(list, /بابتِ فاکتور #1 \(100\)/);
  assert.match(list, /مانده پس از این ردیف: 300 افغانی بدهکار/);
  assert.match(list, /مانده پس از این ردیف: 200 افغانی بدهکار/);
  assert.match(await pg.locator('#acc-book-info').innerText(), /1 برد از سررسید گذشته — 200 افغانی/);

  await pg.locator('[data-open-receipt="t1"]').click();
  await pg.waitForSelector('#modal-debt-receipt.open');
  const r = await pg.locator('#debt-receipt-rows').innerText();
  for (const want of [/قرضِ این ردیف\s*300 افغانی/, /باقیِ این قرض\s*200 افغانی/, /پرداختِ همان لحظه\s*75 افغانی/, /مانده پس از این ردیف\s*300 افغانی بدهکار/]) assert.match(r, want);
  assert.ok(await pg.locator('#btn-debt-receipt-due').isVisible(), 'دکمهٔ سررسید برای برد');

  //  سررسیدِ تازه ⇒ دیگر عقب‌افتاده نیست؛ ردش در دفترچهٔ ثبت؛ ردیفِ رسید دست نمی‌خورد
  await pg.locator('#btn-debt-receipt-due').click();
  await pg.waitForSelector('#modal-debt-due.open');
  await pg.fill('#in-debt-due', '2099-12-31');
  await pg.click('#btn-debt-due-save');
  await pg.waitForTimeout(200);
  const d = await stored(pg);
  assert.equal(d.transactions[0].dueDate, '2099-12-31');
  assert.equal(d.transactions[0].amount, 300);
  assert.deepEqual(d.transactions[1], LEDGER.transactions[1], 'ردیفِ قدیمی بایت‌به‌بایت همان');
  assert.equal(d.auditLog.at(-1).type, 'debt_due_set');
  const info = await pg.locator('#acc-book-info').innerText();
  assert.doesNotMatch(info, /گذشته/);
  assert.match(info, /سررسیدِ بعدی/);

  //  رسیدِ دریافت: پیش و پس؛ بی دکمهٔ سررسید
  await pg.locator('[data-open-receipt="t2"]').click();
  await pg.waitForSelector('#modal-debt-receipt.open');
  const p = await pg.locator('#debt-receipt-rows').innerText();
  assert.match(p, /مانده پیش از دریافت\s*300 افغانی بدهکار/);
  assert.match(p, /بابتِ فاکتور #1\s*100 افغانی/);
  assert.equal(await pg.locator('#btn-debt-receipt-due').isVisible(), false);
  await pg.click('[data-close-modal="modal-debt-receipt"]');
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('سررسید در ثبتِ برد: پیش از تاریخ رد، درست ثبت؛ رسید سررسید نمی‌گیرد', async () => {
  const { pg, errors } = await open(1366);
  await pg.evaluate(() => { openAccount('c1'); openTxModal('give'); });
  await pg.fill('#in-tx-amount', '50');
  await pg.fill('#in-tx-date', '2020-02-01');
  await pg.fill('#in-tx-due', '2020-01-15');
  await pg.click('#btn-tx-submit');
  await pg.waitForTimeout(150);
  assert.ok(await pg.locator('#fg-tx-due.error').count(), 'سررسیدِ پیش از تاریخ خطا می‌دهد');
  assert.equal((await stored(pg)).transactions.length, 2);
  await pg.fill('#in-tx-due', '2020-03-01');
  await pg.click('#btn-tx-submit');
  await pg.waitForTimeout(200);
  const d = await stored(pg);
  assert.equal(d.transactions.length, 3);
  assert.equal(d.transactions[2].dueDate, '2020-03-01');
  //  رسید: کادرِ سررسید پنهان و چیزی نوشته نمی‌شود
  await pg.evaluate(() => openTxModal('receive'));
  assert.equal(await pg.locator('#fg-tx-due').isVisible(), false);
  await pg.fill('#in-tx-amount', '10');
  await pg.click('#btn-tx-submit');
  await pg.waitForTimeout(200);
  const after = (await stored(pg)).transactions.at(-1);
  assert.equal(after.type, 'receive');
  assert.equal('dueDate' in after, false);
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('سقفِ اعتبار: بی سقف همان رفتار؛ بیش از سقف فقط با تأیید و ردش می‌ماند', async () => {
  const { pg, errors } = await open(1366);
  await pg.evaluate(() => { openAccount('c1'); openEditDebtor('c1'); });
  await pg.fill('#in-debtor-limit', '250');
  await pg.click('#form-debtor button[type="submit"]');
  await pg.waitForTimeout(200);
  let d = await stored(pg);
  assert.equal(d.debtors[0].creditLimit, 250);
  assert.equal(d.auditLog.at(-1).type, 'credit_limit_set');
  assert.match(await pg.locator('#acc-book-info').innerText(), /سقفِ اعتبار 250 — تا سقف 50 افغانی/);

  await pg.evaluate(() => openTxModal('give'));
  await pg.fill('#in-tx-amount', '100');
  await pg.click('#btn-tx-submit');
  await pg.waitForSelector('#modal-confirm.open');
  assert.match(await pg.locator('#modal-confirm').innerText(), /50 افغانی بیش از سقفِ اعتبار \(250\)/);
  assert.equal((await stored(pg)).transactions.length, 2, 'پیش از تأیید چیزی ثبت نمی‌شود');
  await pg.click('#btn-confirm-ok');
  await pg.waitForTimeout(200);
  d = await stored(pg);
  assert.equal(d.transactions.length, 3);
  assert.equal(d.auditLog.at(-1).type, 'credit_over_limit');
  assert.equal((await pg.locator('#acc-amount').innerText()).trim(), '300');
  assert.match(await pg.locator('#acc-book-info').innerText(), /50 افغانی بیش از سقفِ اعتبار/);

  //  برداشتنِ سقف ⇒ کلید پاک می‌شود و برد بی پرسش
  await pg.evaluate(() => openEditDebtor('c1'));
  await pg.fill('#in-debtor-limit', '');
  await pg.click('#form-debtor button[type="submit"]');
  await pg.waitForTimeout(200);
  d = await stored(pg);
  assert.equal('creditLimit' in d.debtors[0], false);
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('فهرست: پیش‌فرضِ کامپیوتر جدولی، کارتی ماندگار؛ گوشی همیشه کارتی', async () => {
  const { pg, errors } = await open(1366);
  await pg.evaluate(() => goTo('debtors'));
  await pg.waitForTimeout(150);
  assert.equal(await pg.locator('#debtor-list.is-table .debtor-row').count(), 1);
  assert.match(await pg.locator('#debtor-list').innerText(), /احمد کریمی[\s\S]*200/);
  await pg.click('[data-debtor-view="card"]');
  assert.equal(await pg.locator('#debtor-list .debtor-card').count(), 1);
  await pg.reload();
  await pg.waitForTimeout(1000);
  await pg.evaluate(() => goTo('debtors'));
  await pg.waitForTimeout(150);
  assert.equal(await pg.locator('#debtor-list .debtor-card').count(), 1, 'انتخابِ کارتی پس از بارگذاری می‌ماند');
  await pg.click('[data-debtor-view="table"]');
  await pg.locator('.debtor-row').click();
  await pg.waitForTimeout(150);
  assert.ok(await pg.locator('#page-debtor-account.active').count(), 'کلیکِ ردیف حساب را باز می‌کند');
  assert.deepEqual(errors, []);
  await pg.close();

  const phone = await open(390);
  await phone.pg.evaluate(() => goTo('debtors'));
  await phone.pg.waitForTimeout(150);
  assert.equal(await phone.pg.locator('#debtor-list .debtor-card').count(), 1);
  assert.equal(await phone.pg.locator('#debtor-view-toggle').isVisible(), false);
  assert.deepEqual(phone.errors, []);
  await phone.pg.close();
});

await step('دسترس‌پذیری (axe): حساب با جزئیات، رسید و جدولِ قرض‌داران — روز و شب', async () => {
  if (!axeSrc) {
    if (SKIP) { console.log('⏭  axe-core پیدا نشد و SKIP_BROWSER_TESTS=1 است — آگاهانه رد شد'); return; }
    throw new Error('axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH) — «قبول» نیست');
  }
  const old = JSON.parse(JSON.stringify(LEDGER));
  old.transactions[0].dueDate = '2020-01-10';
  old.debtors[0].creditLimit = 150;
  const bad = [];
  for (const scheme of ['light', 'dark']) {
    const { pg } = await open(1366, old);
    await pg.emulateMedia({ colorScheme: scheme });
    await pg.addScriptTag({ content: axeSrc });
    const run = (sel) => pg.evaluate(async (q) => {
      const out = await window.axe.run(document.querySelector(q), { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'] });
      return out.violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' ; ')}`);
    }, sel);
    await pg.evaluate(() => goTo('debtors'));
    await pg.waitForTimeout(900);
    (await run('#page-debtors')).forEach((v) => bad.push(`${scheme} جدول — ${v}`));
    await pg.evaluate(() => openAccount('c1'));
    await pg.waitForTimeout(900);
    (await run('#page-debtor-account')).forEach((v) => bad.push(`${scheme} حساب — ${v}`));
    await pg.locator('[data-open-receipt="t1"]').click();
    await pg.waitForSelector('#modal-debt-receipt.open');
    await pg.waitForTimeout(500);
    (await run('#modal-debt-receipt')).forEach((v) => bad.push(`${scheme} رسید — ${v}`));
    await pg.close();
  }
  assert.deepEqual(bad, [], bad.join('\n    '));
});

await browser.close();
server.close();
if (failed) { console.log(`\n${failed} سنجه ناموفق`); process.exit(1); }
console.log('\nهمهٔ سنجه‌های دفترِ قرض‌داران گذشت');
