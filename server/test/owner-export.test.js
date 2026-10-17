'use strict';
/**
 * OPS-08 — خروجیِ کاملِ داده برای مالک، نسخهٔ وب (`license/owner-export.js`).
 *
 *   • JSON ⇐ دوباره خوانده می‌شود و مو به مو همان دادهٔ دفتر است
 *   • ‎.xlsx‎ ⇐ ZIPِ درست (CRC و اندازه‌ها)، XMLِ خوش‌ساخت، یک برگه برای هر مجموعه
 *   • فقط می‌خواند: دفترِ یخ‌زده (Object.freeze) هیچ خطایی نمی‌دهد و عوض نمی‌شود
 *   • همان نام‌های فارسیِ برنامهٔ نصبی (`OwnerExport.kt`) — دو فهرست یکی‌اند
 *
 * ⚠️ دادهٔ این‌جا ساختگی است — هیچ دادهٔ دکانِ واقعی در آزمون نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const X = require('../../license/owner-export.js');
const { unzip, wellFormed, sheetRows, sheetNames } = require('./xlsx-check');

const ROOT = path.join(__dirname, '..', '..');

/** همان شکلِ `backupPayload()`ِ صفحه */
function ledger() {
  return {
    exportedAt: '2026-10-14T08:00:00.000Z',
    debtors: [{ id: 'd1', name: 'احمد', phone: '0700123456', notes: 'همسایه <&>', createdAt: 1700000000000 }],
    transactions: [{ id: 't1', debtorId: 'd1', type: 'give', amount: 500, date: '2026-08-28', notes: 'نسیه', createdAt: 1700000000000 }],
    expenses: [{ id: 'e1', title: 'کرایه', category: 'کرایه', amount: 3000.75, date: '2026-08-01', notes: '', createdAt: 1700000000000 }],
    expenseCategories: ['کرایه', 'برق'],
    products: [
      { id: 'p1', name: 'برنج "ممتاز"', category: 'خوراکه', unit: 'کیلوگرم', purchasePrice: 200.5, salePrice: 300, minStock: 5,
        notes: 'ی'.repeat(40000), barcodes: ['6291234567890', '0001'], photo: true, active: false, createdAt: 1700000000000 },
      { id: 'p2', name: 'روغن', webOnlyField: { nested: [1, 2] } },
    ],
    productCategories: ['خوراکه'],
    productUnits: ['عدد', 'کیلوگرم'],
    warehouseEntries: [{ id: 'w1', productId: 'p1', cartons: 4, perCarton: 25, units: 100, unit: 'کیلوگرم', price: 200, date: '2026-08-02', purchaseId: null, createdAt: 1700000000000 }],
    sales: [{ id: 's1', invoiceNumber: 1, total: 600, discountType: 'percent', discountValue: 10, discount: 60, finalTotal: 540,
      paymentMethod: 'credit', debtorId: 'd1', paidAmount: 40, remaining: 500, status: 'completed', createdAt: 1700000000000, date: '2026-08-28' }],
    saleItems: [{ id: 'si1', saleId: 's1', productId: 'p1', quantity: 2, unitPrice: 300, purchasePrice: 200, totalPrice: 600, returnedQty: 0.5 }],
    returns: [{ id: 'r1', saleId: 's1', saleItemId: 'si1', productId: 'p1', quantity: 0.5, amount: 150, reason: 'خراب', date: '2026-08-28', createdAt: 1700000000000 }],
    nextInvoiceNo: 2,
    suppliers: [{ id: 'su1', name: 'شرکت نور', phone: '0788', address: 'کابل', notes: '', createdAt: 1700000000000 }],
    purchases: [],
    supplierPayments: [],
    stockMovements: [{ id: 'm1', productId: 'p1', type: 'purchase_in', qty: 100, date: '2026-08-02', refId: 'w1', createdAt: 1700000000000 }],
    priceHistory: [],
    auditLog: [{ id: 'a1', type: 'shift_open', date: '2026-08-28', refId: null, notes: 'صندوق', createdAt: 1700000000000, amount: 1000 }],
    settings: { storeName: 'دکان آزمون', theme: 'dark', motion: 'full' },
  };
}
const COLLECTIONS = X.SHEETS.map((s) => s[0]);
const META = { exportedAt: 1760000000000, app: 'web', appVersion: '2.14.2' };

function deepFreeze(o) {
  if (o && typeof o === 'object' && !Object.isFrozen(o)) {
    Object.freeze(o);
    Object.values(o).forEach(deepFreeze);
  }
  return o;
}

test('JSON دوباره خوانده می‌شود و همان داده است', () => {
  const src = ledger();
  const back = JSON.parse(X.json(src, META));
  for (const k of COLLECTIONS) assert.deepEqual(back[k], src[k], k);
  assert.equal(back.nextInvoiceNo, 2);
  assert.deepEqual(back.settings, src.settings);
  const parsed = X.parse(X.json(src, META));
  assert.equal(parsed.formatVersion, 1);
  for (const k of COLLECTIONS) assert.deepEqual(parsed.data[k], src[k], k);
});

test('سرآیندِ نسخه‌دار جلوی داده است', () => {
  const t = JSON.parse(X.json(ledger(), META));
  assert.deepEqual(Object.keys(t).slice(0, 5), ['format', 'formatVersion', 'exportedAt', 'source', 'counts']);
  assert.equal(t.format, 'tohid-owner-export');
  assert.equal(t.formatVersion, 1);
  assert.equal(t.exportedAt, '2025-10-09T08:53:20Z', 'همان شکلِ برنامهٔ نصبی، بی میلی‌ثانیه');
  assert.deepEqual(t.source, { app: 'web', appVersion: '2.14.2' });
  assert.equal(t.counts.products, 2);
  assert.equal(t.counts.purchases, 0);
  assert.equal(t.counts.settings, undefined);
});

test('فایلِ دیگر یا قالبِ تازه‌تر رد می‌شود', () => {
  for (const raw of ['', '[]', '{}', JSON.stringify(ledger()), '{"format":"tohid-owner-export","formatVersion":99}']) {
    assert.throws(() => X.parse(raw), Error, raw.slice(0, 40));
  }
});

test('فقط می‌خواند: دفترِ یخ‌زده خطا نمی‌دهد و عوض نمی‌شود', async () => {
  const src = deepFreeze(ledger());
  const before = JSON.stringify(src);
  const t = X.tree(src, META);
  X.json(src, META);
  await X.xlsx(X.sheets(t));
  assert.equal(JSON.stringify(src), before);
});

async function book(src = ledger(), opts) {
  const files = unzip(await X.xlsx(X.sheets(X.tree(src, META)), opts));
  return { files, names: sheetNames(files) };
}

test('اکسل: ZIP و XMLِ درست، یک برگه برای هر مجموعه', async () => {
  const { files, names } = await book();
  assert.deepEqual([...files.keys()].slice(0, 5),
    ['[Content_Types].xml', '_rels/.rels', 'xl/workbook.xml', 'xl/_rels/workbook.xml.rels', 'xl/styles.xml']);
  for (const [name, text] of files) wellFormed(text, name);
  assert.deepEqual(names, [X.INFO_SHEET, ...X.SHEETS.map((s) => s[1])]);
  assert.ok(names.every((n) => n.length <= 31));
  const types = files.get('[Content_Types].xml');
  const rels = files.get('xl/_rels/workbook.xml.rels');
  names.forEach((_, i) => {
    assert.ok(files.has(`xl/worksheets/sheet${i + 1}.xml`));
    assert.ok(types.includes(`/xl/worksheets/sheet${i + 1}.xml`));
    assert.ok(rels.includes(`Id="rId${i + 1}"`));
  });
  assert.ok(rels.includes(`Id="rId${names.length + 1}"`) && rels.includes('styles.xml'));
  const rows = (label) => sheetRows(files.get(`xl/worksheets/sheet${names.indexOf(label) + 1}.xml`));
  const src = ledger();
  for (const [key, label] of X.SHEETS) {
    const n = src[key].length;
    assert.equal(rows(label).length, n ? n + 1 : 1, label);
  }
});

test('اکسل: سرستونِ فارسی، و هر نوع نوعِ خودش', async () => {
  const { files, names } = await book();
  const rows = (label) => sheetRows(files.get(`xl/worksheets/sheet${names.indexOf(label) + 1}.xml`));
  const products = rows('کالاها');
  const head = products[0].map((c) => c.v);
  assert.deepEqual(head.slice(0, 4), ['شناسه', 'نام', 'دسته', 'واحد']);
  assert.ok(head.includes('webOnlyField'), 'فیلدِ ناشناخته با نامِ خودش می‌آید، نمی‌افتد');
  assert.ok(products[0].every((c) => c.s === '1' && c.t === 'inlineStr'));
  //  خانهٔ خالی (null) در XML نوشته نمی‌شود، پس ستون از روی نشانیِ خانه است نه جای آن در ردیف
  const colOf = (ref) => [...ref.replace(/\d+$/, '')].reduce((n, ch) => n * 26 + ch.charCodeAt(0) - 64, 0) - 1;
  const byHead = (row, h) => row.find((c) => colOf(c.ref) === head.indexOf(h));
  const p1 = products[1];
  assert.deepEqual(byHead(p1, 'بارکدها'), { ref: byHead(p1, 'بارکدها').ref, t: 'inlineStr', s: '0', v: '6291234567890، 0001' });
  assert.equal(byHead(p1, 'قیمت خرید').v, '200.5');
  assert.equal(byHead(p1, 'قیمت فروش').s, '2');
  assert.equal(byHead(p1, 'عکس دارد').t, 'b');
  assert.equal(byHead(p1, 'زمان ثبت (UTC)').s, '3');
  assert.equal(Number(byHead(p1, 'زمان ثبت (UTC)').v), 1700000000000 / 86400000 + 25569);
  assert.equal(byHead(p1, 'یادداشت').v, 'ی'.repeat(X.MAX_CELL_TEXT - 1) + '…', 'متنِ بلند به سقفِ اکسل کوتاه می‌شود');
  assert.equal(byHead(products[2], 'webOnlyField').v, '{"nested":[1,2]}');
  const debtors = rows('قرض‌داران');
  assert.equal(debtors[1][2].v, '0700123456');
  assert.equal(debtors[1][2].t, 'inlineStr', 'تلفن متن است، صفرِ اولش نمی‌افتد');
  assert.equal(debtors[1][3].v, 'همسایه <&>');
  assert.deepEqual(rows('خریدها').map((r) => r.map((c) => c.v)), [['هیچ ردیفی ثبت نشده']]);
  assert.deepEqual(rows('دسته‌های مصارف').map((r) => r.map((c) => c.v)), [['مقدار'], ['کرایه'], ['برق']]);
  const info = rows(X.INFO_SHEET).map((r) => r.map((c) => c.v));
  assert.deepEqual(info.slice(1, 7), [['قالب', 'tohid-owner-export'], ['نسخهٔ قالب', '1'], ['زمان خروجی (UTC)', '2025-10-09T08:53:20Z'],
    ['برنامه', 'web'], ['نسخهٔ برنامه', '2.14.2'], ['نام فروشگاه', 'دکان آزمون']]);
  assert.ok(info.some((r) => r[0] === 'شمارهٔ فاکتور بعدی' && r[1] === '2'));
  assert.ok(info.some((r) => r[0] === 'تنظیمات — theme' && r[1] === 'dark'));
  assert.ok(info.some((r) => r[0] === 'ردیف‌های «کالاها»' && r[1] === '2'));
});

test('اکسل: بی CompressionStream (Safariِ قدیمی) همان محتوا بی فشرده‌سازی', async () => {
  const a = await book();
  const b = await book(ledger(), { compress: false });
  assert.deepEqual([...b.files.entries()], [...a.files.entries()]);
});

test('برگهٔ هم‌نام و نامِ ممنوع درست می‌شود', async () => {
  const files = unzip(await X.xlsx([
    { name: 'a/b:c', rows: [] }, { name: 'A B C', rows: [] }, { name: 'x'.repeat(40), rows: [] }, { name: 'x'.repeat(40), rows: [] },
  ]));
  const names = sheetNames(files);
  assert.deepEqual(names, ['a b c', 'A B C (2)', 'x'.repeat(31), 'x'.repeat(27) + ' (2)']);
});

test('CRC32 همان CRCِ استاندارد است', () => {
  assert.equal(X.crc32(new TextEncoder().encode('123456789')), 0xCBF43926);
});

test('همان نام‌های فارسیِ برنامهٔ نصبی (OwnerExport.kt)', () => {
  const kt = fs.readFileSync(path.join(ROOT, 'android-native/app/src/main/java/ir/vil3ntec/tohid/data/OwnerExport.kt'), 'utf8');
  const block = (name) => {
    const m = kt.match(new RegExp(`val ${name}: Map<String, String> = (?:linkedMapOf|mapOf)\\(([\\s\\S]*?)\\n  \\)`));
    assert.ok(m, `${name} در OwnerExport.kt پیدا نشد`);
    return [...m[1].matchAll(/"([^"]+)" to "([^"]+)"/g)].map((x) => [x[1], x[2]]);
  };
  assert.deepEqual(block('SHEETS'), X.SHEETS);
  assert.deepEqual(Object.fromEntries(block('HEADERS')), X.HEADERS);
  const konst = (name) => (kt.match(new RegExp(`const val ${name} = "?([^"\\n]+)"?`)) || [])[1];
  assert.equal(konst('FORMAT'), X.FORMAT);
  assert.equal(Number(konst('FORMAT_VERSION')), X.FORMAT_VERSION);
  assert.equal(konst('INFO_SHEET'), X.INFO_SHEET);
  assert.equal(konst('ARCHIVE_PREFIX'), X.ARCHIVE_PREFIX);
  assert.equal(Number(konst('MAX_ROWS').replace(/_/g, '')), X.MAX_ROWS);
});

test('صفحه دکمه‌ها را دارد و فایل را بار می‌کند؛ سرویس‌ورکر هم آن را نگه می‌دارد', () => {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const sw = fs.readFileSync(path.join(ROOT, 'sw.js'), 'utf8');
  assert.match(html, /<script src="license\/owner-export\.js\?v=\d+" defer><\/script>/);
  assert.ok(html.includes('id="btn-owner-export-xlsx"') && html.includes('id="btn-owner-export-json"'));
  assert.ok(sw.includes("'./license/owner-export.js'"));
  const handler = html.slice(html.indexOf("'btn-owner-export-'"), html.indexOf("'btn-owner-export-'") + 600);
  assert.ok(!/markBackupTaken|localStorage\.setItem|saveData\(/.test(handler), 'خروجی نباید چیزی بنویسد');
});

test('F0-25: خروجیِ مالک پشتِ قفلِ اشتراک نمی‌رود', () => {
  //  «دسترسی خواندن و export دادهٔ مالک… نباید به‌خاطر انقضا از بین برود»
  const lic = fs.readFileSync(path.join(ROOT, 'license/license-client.js'), 'utf8');
  const gated = lic.slice(lic.indexOf('const FEATURE_ELEMENTS'), lic.indexOf('};', lic.indexOf('const FEATURE_ELEMENTS')));
  assert.ok(gated.length > 20, 'FEATURE_ELEMENTS پیدا نشد');
  assert.ok(!gated.includes('btn-owner-export'), 'دکمهٔ خروجیِ کامل به قابلیتِ اشتراکی بسته شده');
});
