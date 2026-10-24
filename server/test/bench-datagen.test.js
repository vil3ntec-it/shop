'use strict';
/**
 * F0-26 — دادهٔ مرجعِ ابزارِ `bench` قطعی است (بخشِ ۹.۱: «seed ثابت»).
 *
 * اگر همان seed در دو ماشین دو دفترِ متفاوت بسازد، مقایسهٔ خط پایه با
 * اجرای بعدی (و افتِ ۱۰٪ در CI) بی‌معنا می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const load = () => import(pathToFileURL(path.join(__dirname, '..', '..', 'bench', 'datagen.mjs')).href);
const sha = (o) => crypto.createHash('sha256').update(JSON.stringify(o)).digest('hex');

test('bench: یک seed ⇒ همان دفتر، بایت به بایت', async () => {
  const { makeLedger } = await load();
  assert.equal(sha(makeLedger(1000)), sha(makeLedger(1000)));
  assert.notEqual(sha(makeLedger(1000, 1)), sha(makeLedger(1000, 2)));
});

test('bench: فقط تعدادِ کالا عوض می‌شود؛ بارکدها یکتا و EAN-13 ِ درست', async () => {
  const { makeLedger, FIXED } = await load();
  const a = makeLedger(1000), b = makeLedger(3000);
  assert.equal(a.products.length, 1000);
  assert.equal(b.products.length, 3000);
  for (const d of [a, b]) {
    assert.equal(d.sales.length, FIXED.sales);
    assert.equal(d.debtors.length, FIXED.debtors);
    assert.equal(d.warehouseEntries.length, d.products.length);
  }
  const codes = b.products.map((p) => p.barcodes[0]);
  assert.equal(new Set(codes).size, codes.length);
  for (const c of codes.slice(0, 50)) {
    assert.match(c, /^\d{13}$/);
    const sum = [...c.slice(0, 12)].reduce((s, ch, i) => s + Number(ch) * (i % 2 ? 3 : 1), 0);
    assert.equal((10 - (sum % 10)) % 10, Number(c[12]));
  }
});

test('bench: دادهٔ مرجعِ بزرگ (PERF-01) قطعی است، شمارش‌ها دقیق‌اند و جمع‌ها می‌خوانند', async () => {
  const { makeReference, syncRowsOf, SYNC_COLLECTIONS } = await load();
  const o = { products: 400, sales: 3000, saleItems: 4500, customers: 120, activeDebtors: 50, suppliers: 7, expenses: 30 };
  const a = makeReference(o);
  assert.equal(sha(a), sha(makeReference(o)));
  assert.notEqual(sha(a), sha(makeReference({ ...o, seed: 7 })));
  assert.equal(a.products.length, 400);
  assert.equal(a.sales.length, 3000);
  assert.equal(a.saleItems.length, 4500);
  assert.equal(a.debtors.length, 120);
  //  همان ریاضیِ completeSale ِ برنامه
  const bySale = new Map();
  for (const i of a.saleItems) {
    assert.equal(i.totalPrice, Math.round(i.unitPrice * i.quantity));
    assert.ok(i.returnedQty >= 0 && i.returnedQty <= i.quantity);
    assert.equal(Math.round(i.quantity * 1000), i.quantity * 1000, 'مقدار حداکثر سه رقمِ اعشار');
    bySale.set(i.saleId, (bySale.get(i.saleId) || 0) + i.totalPrice);
  }
  for (const s of a.sales) {
    assert.equal(s.total, bySale.get(s.id));
    assert.equal(s.finalTotal, Math.max(0, s.total - s.discount));
    assert.equal(s.paidAmount + s.remaining, s.finalTotal);
  }
  //  موجودی هیچ کالایی منفی نمی‌شود
  const cancelled = new Set(a.sales.filter((s) => s.status === 'cancelled').map((s) => s.id));
  const stock = new Map();
  for (const w of a.warehouseEntries) stock.set(w.productId, (stock.get(w.productId) || 0) + w.units);
  for (const i of a.saleItems) if (!cancelled.has(i.saleId)) stock.set(i.productId, stock.get(i.productId) - i.quantity + i.returnedQty);
  assert.ok([...stock.values()].every((v) => v >= -1e-9));
  const rows = [...syncRowsOf(a)];
  assert.equal(rows.length, SYNC_COLLECTIONS.reduce((s, t) => s + a[t].length, 0));
  assert.ok(rows.every((r) => !('id' in r.data)));
});
