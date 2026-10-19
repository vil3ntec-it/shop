/**
 * نسخهٔ وب — پرداختِ ترکیبی و صندوقِ شیفت (v3 A4)، با مرورگرِ واقعی.
 *
 *   پرچمِ «پرداخت و صندوق» خاموش (پیش‌فرض) ⇒ همان نقد/نسیه و صندوقِ یک‌عددیِ امروز؛
 *     فروش هیچ فیلدِ تازه‌ای ندارد و بستنِ صندوق `z` ندارد.
 *   پرچم روشن:
 *     S01 · Enter روی نقدِ خالی = نقدِ دقیق؛ تایپِ ۱۰۰۰ و Enter ⇒ فروش با پولِ پسِ درست
 *     S06 · ۱۰۰۰ نقد، PgDn ⇒ HesabPay، مرجع، Enter ⇒ مبلغ، ۱۴۵۰، Enter ⇒ دو ردیفِ پرداخت،
 *           HesabPay «ثبتِ دستی، در انتظار»؛ گزارشِ X نقد و HesabPay را جدا نشان می‌دهد
 *     PAY-05 · باقی‌مانده با مشتری به حسابش می‌رود؛ بی مشتری ثبت نمی‌شود
 *     S12 · خروجِ نقد با دلیل، کشو بی فروش با تأیید، بستن با شمارشِ اسکناس؛ اختلافِ بالای
 *           آستانه بی دلیل بسته نمی‌شود؛ Z ذخیره و نشان داده می‌شود
 *
 * اجرا:  node test/web-pay-flow.mjs
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
const SKIP = process.env.SKIP_BROWSER_TESTS === '1';
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

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
const LEDGER = {
  products: [
    { id: 'p1', name: 'برنج', unit: 'عدد', salePrice: 850, purchasePrice: 600, barcodes: ['111'] },
    { id: 'p2', name: 'روغن', unit: 'عدد', salePrice: 2450, purchasePrice: 2000, barcodes: ['222'] },
  ],
  warehouseEntries: [
    { id: 'w1', productId: 'p1', units: 50, date: '2026-10-01' },
    { id: 'w2', productId: 'p2', units: 50, date: '2026-10-01' },
  ],
  debtors: [{ id: 'd1', name: 'نوشا', phone: '', createdAt: 1 }],
  sales: [], saleItems: [], nextInvoiceNo: 1,
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.stack || e.message}`); }
};

async function profile(payV2) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 800 } });
  await ctx.addInitScript(([seedJson, on]) => {
    if (localStorage.getItem('seeded-profile')) return;
    localStorage.setItem('tohid-shop-data-v1', seedJson);
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    if (on) localStorage.setItem('tohid-pay-v2', '1');
    localStorage.setItem('seeded-profile', '1');
  }, [JSON.stringify(LEDGER), payV2]);
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
  //  پیش‌نویسِ قبلی نپرسد
  if (await pg.locator('#modal-sale-draft.open').count()) await pg.click('#btn-sale-draft-discard');
  return { pg, errors };
}
const data = (pg) => pg.evaluate((k) => JSON.parse(localStorage.getItem(k) || '{}'), DATA_KEY);
async function openShift(pg, amount) {
  await pg.evaluate(() => { goTo('sales'); openShiftModal('open'); });
  await pg.fill('#in-shift-amount', String(amount));
  await press(pg, '#btn-shift-save');
}
async function checkout(pg, productId) {
  await pg.evaluate((id) => { goTo('sale'); addToCart(id, 1); openSaleCheckoutModal(); }, productId);
  await pg.waitForSelector('#modal-sale-checkout.open');
  await pg.waitForTimeout(50);
}
/** صفحهٔ «فروش‌ها» پشتِ قفلِ اشتراک است (vip.js)؛ این‌جا خودِ دکمهٔ صندوق را می‌زنیم، همان رویدادِ کلیک */
const press = (pg, sel) => pg.evaluate((s) => document.querySelector(s).click(), sel);
const shown = (pg, sel) => pg.evaluate((s) => { const el = document.querySelector(s); return !!el && getComputedStyle(el).display !== 'none' && !el.hidden; }, sel);
const noOverflow = (pg) => pg.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);

await step('پرچم خاموش (پیش‌فرض): همان نقد/نسیه؛ فروش و بستنِ صندوق هیچ فیلدِ تازه‌ای ندارند', async () => {
  const ctx = await profile(false);
  const { pg, errors } = await openIn(ctx);
  await openShift(pg, 1000);
  assert.equal(await shown(pg, '#btn-shift-move'), false, 'ورود/خروج بی پرچم دیده نمی‌شود');
  assert.equal(await shown(pg, '#btn-shift-x'), false);
  await checkout(pg, 'p1');
  assert.equal(await pg.isVisible('#co-pay-v2'), false, 'پنلِ ترکیبی پنهان');
  assert.equal(await pg.isVisible('#co-payment-type'), true, 'نقد/نسیهٔ امروز دیده می‌شود');
  await pg.click('#form-sale-checkout [type="submit"]');
  await pg.waitForTimeout(200);
  const d = await data(pg);
  const s = d.sales[0];
  assert.equal(s.paymentMethod, 'cash');
  assert.equal(s.paidAmount, 850);
  for (const k of ['payments', 'tenderedMinor', 'changeMinor']) assert.equal(k in s, false, 'فیلدِ تازه بی پرچم نوشته نمی‌شود: ' + k);
  //  بستن: کادرِ یک‌عددیِ امروز
  await pg.keyboard.press('Escape');
  await pg.evaluate(() => { document.querySelectorAll('.modal-scrim.open').forEach(m => m.classList.remove('open')); goTo('sales'); openShiftModal('close'); });
  assert.equal(await pg.isVisible('#in-shift-amount'), true);
  assert.equal(await pg.isVisible('#shift-denoms-wrap'), false);
  await pg.fill('#in-shift-amount', '1850');
  await pg.click('#btn-shift-save');
  const close = (await data(pg)).auditLog.find(a => a.type === 'shift_close');
  assert.equal(close.amount, 1850);
  assert.equal('z' in close, false);
  assert.match(close.notes, /بی اختلاف/);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('S01 (پرچم روشن): Enter روی نقدِ خالی = نقدِ دقیق؛ ۱۰۰۰ و Enter ⇒ فروش با پولِ پسِ ۱۵۰', async () => {
  const ctx = await profile(true);
  const { pg, errors } = await openIn(ctx);
  await openShift(pg, 2000);
  await checkout(pg, 'p1');
  assert.equal(await pg.isVisible('#co-pay-v2'), true);
  assert.equal(await pg.isVisible('#co-payment-type'), false);
  assert.equal(await pg.evaluate(() => document.activeElement.id), 'co-cash', 'فوکوس روی نقد');
  await pg.keyboard.press('Enter');
  assert.equal(await pg.inputValue('#co-cash'), '850', 'Enterِ خالی = نقدِ دقیق، فروش ثبت نشد');
  assert.equal((await data(pg)).sales.length, 0);
  await pg.fill('#co-cash', '');
  await pg.keyboard.type('1000');
  assert.match(await pg.innerText('#co-pay-change'), /150/);
  assert.ok(await noOverflow(pg), 'پنل در ۳۹۰ پیکسل بیرون نمی‌زند');
  await pg.keyboard.press('Enter');
  await pg.waitForTimeout(200);
  const s = (await data(pg)).sales[0];
  assert.ok(s, 'فروش ثبت شد');
  assert.deepEqual(s.payments, [{ method: 'cash', kind: 'cash', amountMinor: 85000, status: 'confirmed' }]);
  assert.equal(s.tenderedMinor, 100000);
  assert.equal(s.changeMinor, 15000);
  assert.equal(s.paidAmount, 850, 'معنای امروزِ paidAmount');
  assert.equal(s.paymentMethod, 'cash');
  assert.equal(s.debtGiven, 0);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('S06 (پرچم روشن): ۱۰۰۰ نقد + PgDn ⇒ HesabPay با مرجع ۱۴۵۰؛ دو ردیف، «ثبتِ دستی»؛ X جدا نشان می‌دهد', async () => {
  const ctx = await profile(true);
  const { pg, errors } = await openIn(ctx);
  await openShift(pg, 2000);
  await checkout(pg, 'p2');
  await pg.keyboard.type('1000');
  await pg.keyboard.press('PageDown');
  assert.equal(await pg.evaluate(() => document.activeElement.dataset.payF), 'ref', 'PgDn ⇒ کادرِ مرجعِ HesabPay');
  assert.equal(await pg.inputValue('[data-pay-row="0"] select'), 'hesabpay');
  await pg.keyboard.press('Enter');                 // بی مرجع: فقط به مبلغ می‌رود، ثبت نمی‌کند
  await pg.keyboard.type('1450');
  await pg.keyboard.press('Enter');
  await pg.waitForTimeout(150);
  assert.equal((await data(pg)).sales.length, 0, 'بی شمارهٔ مرجع ثبت نمی‌شود');
  assert.match(await pg.innerText('#co-pay-error'), /مرجع/);
  await pg.fill('[data-pay-row="0"] [data-pay-f="ref"]', 'HP-77');
  await pg.focus('[data-pay-row="0"] [data-pay-f="amount"]');
  await pg.keyboard.press('Enter');
  await pg.waitForTimeout(200);
  const s = (await data(pg)).sales[0];
  assert.ok(s, 'فروش ثبت شد');
  assert.deepEqual(s.payments, [
    { method: 'cash', kind: 'cash', amountMinor: 100000, status: 'confirmed' },
    { method: 'hesabpay', kind: 'wallet', amountMinor: 145000, status: 'pending', ref: 'HP-77', verification: 'manual' },
  ]);
  assert.equal(s.payments.reduce((a, p) => a + p.amountMinor, 0), 245000, 'مجموع = کلِ فاکتور');
  //  فاکتور: HesabPay «ثبتِ دستی»
  assert.match(await pg.innerText('#invoice-print-area'), /HesabPay \(ثبتِ دستی\)/);
  await pg.evaluate(() => { document.querySelectorAll('.modal-scrim.open').forEach(m => m.classList.remove('open')); goTo('sales'); renderShiftPanel(); });
  assert.ok(await shown(pg, '#btn-shift-x'));
  await press(pg, '#btn-shift-x');
  const x = await pg.innerText('#shift-print-area');
  assert.match(x, /گزارش X/);
  assert.match(x, /HesabPay — فروش\s*1,450/);
  assert.match(x, /نقد — فروش\s*1,000/);
  assert.match(x, /مورد انتظار — نقد\s*3,000/);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('PAY-05 (پرچم روشن): باقی‌مانده با مشتری به حسابش؛ بی مشتری ثبت نمی‌شود', async () => {
  const ctx = await profile(true);
  const { pg, errors } = await openIn(ctx);
  await checkout(pg, 'p1');
  await pg.keyboard.type('500');
  await pg.keyboard.press('Enter');
  await pg.waitForTimeout(150);
  assert.equal((await data(pg)).sales.length, 0, 'باقی‌ماندهٔ بی مشتری ثبت نمی‌شود');
  assert.match(await pg.innerText('#co-pay-error'), /مشتری/);
  await pg.selectOption('#co-pay-customer', 'd1');
  assert.match(await pg.innerText('#co-pay-left-label'), /حسابِ مشتری/);
  await pg.click('#form-sale-checkout [type="submit"]');
  await pg.waitForTimeout(200);
  const d = await data(pg);
  const s = d.sales[0];
  assert.equal(s.paymentMethod, 'credit');
  assert.equal(s.debtorId, 'd1');
  assert.equal(s.paidAmount, 500);
  assert.equal(s.debtGiven, 350);
  assert.deepEqual(s.payments.map(p => [p.method, p.amountMinor]), [['cash', 50000], ['account', 35000]]);
  const give = d.transactions.filter(t => t.type === 'give');
  assert.equal(give.length, 1);
  assert.equal(give[0].amount, 350, 'بدهی با همان منطقِ قرض‌داران');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('S12 (پرچم روشن): خروجِ نقد با دلیل، کشو بی فروش با تأیید، بستن با اسکناس؛ کسریِ بالای آستانه دلیل می‌خواهد؛ Z', async () => {
  const ctx = await profile(true);
  const { pg, errors } = await openIn(ctx);
  await openShift(pg, 2000);
  await checkout(pg, 'p2');
  await pg.keyboard.press('Enter');                 // نقدِ دقیق
  await pg.keyboard.press('Enter');                 // ثبت
  await pg.waitForTimeout(200);
  await pg.evaluate(() => { document.querySelectorAll('.modal-scrim.open').forEach(m => m.classList.remove('open')); goTo('sales'); renderShiftPanel(); });
  //  خروجِ نقد: «دیگر» بی توضیح رد، با توضیح ثبت
  assert.ok(await shown(pg, '#btn-shift-move'));
  await press(pg, '#btn-shift-move');
  await pg.selectOption('#in-move-reason', 'other');
  await pg.fill('#in-move-amount', '500');
  await pg.click('#btn-shift-move-save');
  assert.match(await pg.innerText('#shift-move-error'), /توضیح/);
  await pg.fill('#in-move-note', 'خرید شیر');
  await pg.click('#btn-shift-move-save');
  //  کشو بی فروش
  assert.ok(await shown(pg, '#btn-shift-nosale'));
  await press(pg, '#btn-shift-nosale');
  assert.match(await pg.innerText('#shift-move-approver'), /تأییدکننده: آزمون/);
  await pg.click('#btn-shift-move-save');
  assert.match(await pg.innerText('#shift-move-error'), /دلیل/);
  await pg.fill('#in-move-note', 'پولِ خرد برای مشتری');
  await pg.click('#btn-shift-move-save');
  const log = (await data(pg)).auditLog;
  const open = log.find(a => a.type === 'shift_open');
  const out = log.find(a => a.type === 'cash_out');
  const ns = log.find(a => a.type === 'no_sale');
  assert.deepEqual([out.refId, out.amount, out.reason, out.note], [open.id, 500, 'other', 'خرید شیر']);
  assert.deepEqual([ns.refId, ns.reason, ns.approvedBy, ns.by], [open.id, 'پولِ خرد برای مشتری', 'آزمون', 'آزمون']);
  assert.match(await pg.innerText('#shift-sub'), /3,950/, 'مورد انتظار: ۲۰۰۰ + ۲۴۵۰ − ۵۰۰');
  //  بستن: ۳۸۵۰ شمرده ⇒ کسریِ ۱۰۰ (بالای ۵۰) ⇒ دلیل لازم
  await pg.evaluate(() => openShiftModal('close'));
  assert.equal(await pg.isVisible('#in-shift-amount'), false, 'کادرِ یک‌عددی جایش را به شمارش داده');
  await pg.fill('[data-denom="100000"]', '3');
  await pg.fill('[data-denom="50000"]', '1');
  await pg.fill('[data-denom="10000"]', '3');
  await pg.fill('[data-denom="5000"]', '1');
  assert.match(await pg.innerText('#shift-denoms-total'), /3,850/);
  assert.ok(await noOverflow(pg));
  await pg.click('#btn-shift-save');
  assert.match(await pg.innerText('#shift-v2-error'), /دلیل/);
  assert.equal((await data(pg)).auditLog.filter(a => a.type === 'shift_close').length, 0, 'بی دلیل بسته نشد');
  await pg.fill('#in-shift-note', 'پولِ خرد کم بود');
  await pg.click('#btn-shift-save');
  await pg.waitForSelector('#modal-shift-report.open');
  const zText = await pg.innerText('#shift-print-area');
  assert.match(zText, /گزارش Z/);
  assert.match(zText, /کسری 100/);
  const close = (await data(pg)).auditLog.find(a => a.type === 'shift_close');
  assert.equal(close.refId, open.id);
  assert.equal(close.amount, 3850, 'amount همان معنای امروز: نقدِ شمرده');
  assert.equal(close.z.expected.cash, 395000);
  assert.equal(close.z.variance.cash, -10000);
  assert.equal(close.z.needsReview, true);
  assert.equal(close.reason, 'پولِ خرد کم بود');
  assert.deepEqual(close.z.denominations, { 100000: 3, 50000: 1, 10000: 3, 5000: 1 });
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('خاموش کردنِ دوبارهٔ پرچم ⇒ همان نقد/نسیهٔ امروز، فاکتورِ ترکیبیِ قبلی خوانا می‌ماند', async () => {
  const ctx = await profile(true);
  const { pg, errors } = await openIn(ctx);
  await checkout(pg, 'p1');
  await pg.keyboard.press('Enter'); await pg.keyboard.press('Enter');
  await pg.waitForTimeout(200);
  await pg.evaluate(() => { document.querySelectorAll('.modal-scrim.open').forEach(m => m.classList.remove('open')); goTo('settings'); });
  await pg.click('[data-pay-mode="legacy"]');
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-pay-v2')), '0');
  await checkout(pg, 'p1');
  assert.equal(await pg.isVisible('#co-pay-v2'), false);
  await pg.click('#form-sale-checkout [type="submit"]');
  await pg.waitForTimeout(200);
  const d = await data(pg);
  assert.equal(d.sales.length, 2);
  assert.ok(Array.isArray(d.sales[0].payments));
  assert.equal('payments' in d.sales[1], false);
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('دسترس‌پذیری (axe): پنلِ پرداخت، شمارشِ اسکناس، ورود/خروج و گزارشِ X — روز و شب', async () => {
  if (!axeSrc) {
    if (SKIP) { console.log('⏭  axe-core پیدا نشد و SKIP_BROWSER_TESTS=1 است — آگاهانه رد شد'); return; }
    throw new Error('axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH) — «قبول» نیست');
  }
  const bad = [];
  for (const scheme of ['light', 'dark']) {
    const ctx = await profile(true);
    const { pg } = await openIn(ctx);
    await pg.emulateMedia({ colorScheme: scheme });
    await pg.addScriptTag({ content: axeSrc });
    const run = (sel) => pg.evaluate(async (q) => {
      const out = await window.axe.run(document.querySelector(q), { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'] });
      return out.violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' ; ')}`);
    }, sel);
    const closeAll = () => pg.evaluate(() => document.querySelectorAll('.modal-scrim.open').forEach(m => m.classList.remove('open')));
    await openShift(pg, 2000);
    await checkout(pg, 'p2');
    await pg.keyboard.type('1000');
    await pg.keyboard.press('PageDown');
    await pg.waitForTimeout(300);
    (await run('#modal-sale-checkout')).forEach((v) => bad.push(`${scheme} پنلِ پرداخت — ${v}`));
    await closeAll();
    await pg.evaluate(() => { goTo('sales'); renderShiftPanel(); });
    await press(pg, '#btn-shift-move');
    await pg.waitForTimeout(300);
    (await run('#modal-shift-move')).forEach((v) => bad.push(`${scheme} ورود/خروج — ${v}`));
    await closeAll();
    await press(pg, '#btn-shift-x');
    await pg.waitForTimeout(300);
    (await run('#modal-shift-report')).forEach((v) => bad.push(`${scheme} گزارش X — ${v}`));
    await closeAll();
    await pg.evaluate(() => openShiftModal('close'));
    await pg.waitForTimeout(300);
    (await run('#modal-shift')).forEach((v) => bad.push(`${scheme} شمارش — ${v}`));
    await ctx.close();
  }
  assert.deepEqual(bad, [], bad.join('\n    '));
});

await browser.close();
server.close();
if (failed) { console.log(`\n${failed} آزمون شکست خورد`); process.exit(1); }
console.log('\nهمه سبز');
