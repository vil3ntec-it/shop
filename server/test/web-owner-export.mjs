/**
 * OPS-08 — خروجیِ کاملِ مالک در نسخهٔ وب، با مرورگرِ واقعی.
 *
 *   • `index.html`ِ واقعی در کرومیوم باز می‌شود، دفترِ ساختگی در حافظهٔ
 *     مرورگر گذاشته می‌شود، و دو دکمهٔ «خروجی کامل» واقعاً زده می‌شوند
 *   • فایلِ ‎.xlsx‎ِ دانلودشده: ZIPِ درست، و هر XML با DOMParserِ خودِ
 *     مرورگر بی خطا خوانده می‌شود؛ هر مجموعه برگهٔ خودش را دارد
 *   • فایلِ JSON دوباره خوانده می‌شود و مو به مو همان دفتر است
 *   • جز یک ردیفِ «خروجی کامل داده» در سابقهٔ عملیات (مشخصات ۲۱.۲)، دفتر و مُهرِ
 *     «آخرین پشتیبان» دست نمی‌خورند
 *   • شاگرد خروجی نمی‌گیرد (فقط مالک/نقشِ مجاز — ۲۱.۲)
 *   • همهٔ درخواست‌های بیرونی بسته‌اند و خروجی باز هم ساخته می‌شود (بی اینترنت)
 *
 * اجرا:  node test/web-owner-export.mjs
 * نبودِ پلی‌رایت قرمز است، نه «رد» (F0-17).
 * ⚠️ دادهٔ این‌جا ساختگی است — هیچ دادهٔ دکانِ واقعی در آزمون نیست.
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
const { unzip, sheetNames, sheetRows } = require('./xlsx-check.js');
const X = require('../../license/owner-export.js');

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const origin = `http://127.0.0.1:${server.address().port}`;

const LEDGER = {
  debtors: [{ id: 'd1', name: 'احمد آزمون', phone: '0700123456', notes: 'a < b & c', createdAt: 1700000000000 }],
  transactions: [{ id: 't1', debtorId: 'd1', type: 'give', amount: 5000, date: '2026-10-01', notes: '', createdAt: 1700000000000 }],
  expenses: [{ id: 'e1', title: 'کرایه', category: 'کرایه', amount: 3000.5, date: '2026-10-01', notes: '', createdAt: 1700000000000 }],
  expenseCategories: ['کرایه', 'برق'],
  products: [{ id: 'p1', name: 'برنج آزمون', category: 'خوراکی', unit: 'کیلوگرم', salePrice: 1250, purchasePrice: 990.5, minStock: 20, barcodes: ['0001'], photo: false, createdAt: 1700000000000 }],
  productCategories: ['خوراکی'],
  productUnits: ['عدد', 'کیلوگرم'],
  warehouseEntries: [{ id: 'w1', productId: 'p1', units: 10, date: '2026-10-01', expiryDate: '2026-12-05', createdAt: 1700000000000 }],
  sales: [{ id: 's1', invoiceNumber: 1, total: 1250, finalTotal: 1250, date: '2026-10-01', createdAt: 1700000000000, status: 'completed', paymentMethod: 'cash' }],
  saleItems: [{ id: 'si1', saleId: 's1', productId: 'p1', quantity: 1, unitPrice: 1250, totalPrice: 1250 }],
  returns: [],
  nextInvoiceNo: 2,
  suppliers: [{ id: 'su1', name: 'شرکت آزمون', phone: '0788', createdAt: 1700000000000 }],
  purchases: [],
  supplierPayments: [],
  stockMovements: [],
  priceHistory: [],
  auditLog: [],
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.stack || e.message}`); }
};

const pg = await browser.newPage({ viewport: { width: 1366, height: 800 }, acceptDownloads: true });
const errors = [];
pg.on('pageerror', (e) => errors.push(String(e)));
//  بیرون از خودِ برنامه هیچ درخواستی نمی‌رود (فونت، سرور…) — مثلِ گوشیِ بی اینترنت
await pg.route((u) => !u.href.startsWith(origin), (r) => r.abort());
await pg.goto(`${origin}/index.html`);
await pg.evaluate((d) => {
  localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
  localStorage.setItem('tohid-shop-data-v1', d);
}, JSON.stringify(LEDGER));
await pg.reload();
await pg.waitForFunction(() => !!window.TohidOwnerExport, null, { timeout: 15000 });
await pg.evaluate(() => goTo('settings'));
await pg.waitForSelector('#btn-owner-export-xlsx', { state: 'visible' });
const stored = () => pg.evaluate(() => ({ data: localStorage.getItem('tohid-shop-data-v1'), last: localStorage.getItem('tohid-shop-last-backup') }));
const before = await stored();
//  دفترِ درونِ صفحه پیش از هر خروجی — صفحه هنگامِ بار شدن فیلدهای پیش‌فرض را می‌افزاید،
//  پس منبعِ مقایسه همین است (`backupPayload()`)، نه رشتهٔ خامِ کاشته‌شده
const mem = JSON.parse(await pg.evaluate(() => JSON.stringify(backupPayload())));


async function download(id) {
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click(id)]);
  return { name: dl.suggestedFilename(), bytes: fs.readFileSync(await dl.path()) };
}

await step('دکمهٔ «خروجی کامل — اکسل»: فایلِ ‎.xlsx‎ِ درست با یک برگه برای هر مجموعه', async () => {
  const { name, bytes } = await download('#btn-owner-export-xlsx');
  assert.match(name, /^tohid-export-\d{4}-\d{2}-\d{2}\.xlsx$/);
  const files = unzip(bytes);
  //  XML را خودِ مرورگر می‌خواند — DOMParser هر خطای خوش‌ساختی را می‌گیرد
  const bad = await pg.evaluate((parts) => parts.filter(([, xml]) =>
    new DOMParser().parseFromString(xml, 'application/xml').getElementsByTagName('parsererror').length > 0).map(([n]) => n),
  [...files.entries()]);
  assert.deepEqual(bad, [], `XMLِ خراب: ${bad.join(', ')}`);
  const names = sheetNames(files);
  assert.deepEqual(names, [X.INFO_SHEET, ...X.SHEETS.map((s) => s[1])]);
  const rows = (label) => sheetRows(files.get(`xl/worksheets/sheet${names.indexOf(label) + 1}.xml`));
  for (const [key, label] of X.SHEETS) {
    assert.equal(rows(label).length, LEDGER[key].length ? LEDGER[key].length + 1 : 1, label);
  }
  const products = rows('کالاها');
  const head = products[0].map((c) => c.v);
  assert.ok(['شناسه', 'نام', 'دسته', 'قیمت فروش'].every((h) => head.includes(h)), `سرستون‌ها: ${head.join('، ')}`);
  assert.ok(products[1].some((c) => c.v === 'برنج آزمون'));
  const debtors = rows('قرض‌داران');
  assert.equal(debtors[1][2].v, '0700123456');
  assert.equal(debtors[1][3].v, 'a < b & c');
  const info = rows(X.INFO_SHEET).map((r) => r.map((c) => c.v));
  assert.ok(info.some((r) => r[0] === 'برنامه' && r[1] === 'web'));
});

await step('دکمهٔ «خروجی کامل — JSON»: سرآیندِ نسخه‌دار و همان دفتر، مو به مو', async () => {
  const { name, bytes } = await download('#btn-owner-export-json');
  assert.match(name, /^tohid-export-\d{4}-\d{2}-\d{2}\.json$/);
  const t = JSON.parse(bytes.toString('utf8'));
  assert.equal(t.format, 'tohid-owner-export');
  assert.equal(t.formatVersion, 1);
  assert.equal(t.source.app, 'web');
  assert.match(t.exportedAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/);
  //  منبع همان دفترِ درونِ صفحه است؛ ردیفِ سابقهٔ خروجیِ اکسلِ بالا هم حالا در آن است
  const source = JSON.parse(JSON.stringify(mem));
  source.auditLog = JSON.parse(await pg.evaluate(() => JSON.stringify(backupPayload().auditLog))).slice(0, mem.auditLog.length + 1);
  for (const k of Object.keys(LEDGER)) assert.deepEqual(t[k], source[k], k);
  assert.equal(source.products[0].name, 'برنج آزمون');
  assert.equal(t.counts.products, 1);
  const parsed = X.parse(bytes.toString('utf8'));
  for (const k of Object.keys(LEDGER)) assert.deepEqual(parsed.data[k], source[k], k);
  assert.deepEqual(parsed.data.settings, source.settings);
});

//  «بی شبکه»: همهٔ درخواست‌های بیرونی از اول بسته‌اند (route بالا) و خروجی باز هم ساخته شد؛
//  خودِ owner-export.js هم هیچ fetch/XHR ندارد (owner-export.test.js). تپشِ زمان‌دارِ
//  پشتیبانی (support.js) ربطی به خروجی ندارد، پس درخواست‌ها این‌جا شمرده نمی‌شوند.
await step('جز ردیفِ سابقهٔ «خروجی کامل» چیزی نوشته نمی‌شود', async () => {
  const after = await stored();
  assert.equal(after.last, before.last, 'مُهرِ «آخرین پشتیبان» خورد');
  const a = JSON.parse(after.data);
  const added = a.auditLog.slice(mem.auditLog.length);
  assert.deepEqual(added.map((x) => [x.type, x.notes, x.refId]),
    [['data_export', 'خروجی کامل داده — اکسل', null], ['data_export', 'خروجی کامل داده — JSON', null]]);
  const diff = Object.keys(LEDGER).filter((k) => k !== 'auditLog' && JSON.stringify(a[k]) !== JSON.stringify(mem[k]));
  assert.deepEqual(diff, [], 'جز سابقه چیزی در دفتر عوض شد');
  assert.deepEqual(errors, [], `خطای صفحه: ${errors.join(' | ')}`);
});

await step('شاگرد خروجیِ کامل نمی‌گیرد (فقط مالک/نقشِ مجاز)', async () => {
  await pg.evaluate(() => localStorage.setItem('tohid-sync-state-v1',
    JSON.stringify({ rev: 0, lastSyncAt: 0, members: [], shop: { id: 'x', name: 'آزمون', role: 'staff', isOwner: false } })));
  let downloaded = false;
  const onDl = () => { downloaded = true; };
  pg.on('download', onDl);
  const auditBefore = await pg.evaluate(() => JSON.parse(localStorage.getItem('tohid-shop-data-v1')).auditLog.length);
  await pg.click('#btn-owner-export-json');
  await pg.waitForTimeout(800);
  pg.off('download', onDl);
  assert.equal(downloaded, false, 'شاگرد فایل گرفت');
  assert.match(await pg.locator('#toast-wrap').innerText(), /فقط برای صاحب دکان یا مدیر/);
  assert.equal(await pg.evaluate(() => JSON.parse(localStorage.getItem('tohid-shop-data-v1')).auditLog.length), auditBefore);
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
