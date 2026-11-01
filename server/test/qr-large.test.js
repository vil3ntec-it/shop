'use strict';
/**
 * QR ِ بزرگِ حساب‌پی (PAY-21): ماتریس‌های `core/test-vectors/qr-large-v1.json` را رمزگذارِ کاتلین
 * (`core/qr/QrCode.kt`، نسخهٔ ۷ تا ۱۳) می‌سازد (`QrLargeVectorsTest`)؛ این‌جا خوانندهٔ مستقلِ zxing
 * (`license/vendor`) همان‌ها را از روی تصویر می‌خواند — با و بی فرضِ «تصویرِ تمیز».
 */
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const QV = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'qr-large-v1.json'), 'utf8'));

function zxing() {
  let Z = require(path.join(ROOT, 'license', 'vendor', 'zxing-0.21.3.min.js'));
  if (!Z.QRCodeReader) Z = globalThis.ZXing || Z;
  return Z;
}

function decode(Z, rows, scale, pure) {
  const n = rows.length + 8;
  const W = n * scale;
  const lum = new Uint8ClampedArray(W * W).fill(255);
  rows.forEach((row, y) => [...row].forEach((on, x) => {
    if (on !== '1') return;
    for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) lum[((y + 4) * scale + dy) * W + (x + 4) * scale + dx] = 0;
  }));
  const bitmap = new Z.BinaryBitmap(new Z.HybridBinarizer(new Z.RGBLuminanceSource(lum, W, W)));
  const hints = pure ? new Map([[Z.DecodeHintType.PURE_BARCODE, true]]) : new Map();
  return new Z.QRCodeReader().decode(bitmap, hints).getText();
}

test('qr-large-v1: نسخهٔ ۷ تا ۱۳ با اندازهٔ درست، و zxing همان نشانی را می‌خواند', () => {
  const Z = zxing();
  assert.deepEqual([...new Set(QV.cases.map((c) => c.version))].sort((a, b) => a - b), [7, 9, 10, 12, 13]);
  for (const c of QV.cases) {
    const size = 17 + 4 * c.version;
    assert.equal(c.rows.length, size, c.text);
    c.rows.forEach((r) => assert.match(r, new RegExp('^[01]{' + size + '}$')));
    for (const scale of [3, 5, 8]) {
      assert.equal(decode(Z, c.rows, scale, true), c.text, `${c.text} @${scale}`);
      assert.equal(decode(Z, c.rows, scale, false), c.text, `${c.text} @${scale} (detector)`);
    }
  }
});

test('qr-large-v1: درازترین نشانی از سقفِ فاکتور (۱۰۶ بایت) بیشتر است', () => {
  assert.ok(QV.cases.every((c) => Buffer.byteLength(c.text, 'utf8') > 106));
  assert.ok(QV.cases.some((c) => Buffer.byteLength(c.text, 'utf8') > 300));
});
