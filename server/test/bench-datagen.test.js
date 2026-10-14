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
