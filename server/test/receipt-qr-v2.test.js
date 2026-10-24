'use strict';
/**
 * QRِ فاکتورِ نسخهٔ ۲ (نسخهٔ ۱ تا ۶، چند بلوکِ رید-سولومون) — سنجشِ مستقل.
 *
 * ماتریس‌های `core/test-vectors/receipt-qr-v1.json` را رمزگذارِ کاتلین می‌سازد
 * (`core/qr/QrCode.kt`؛ `QrCodeVersionsTest` برابریِ دقیقش را می‌سنجد). این‌جا خوانندهٔ
 * مستقلِ zxing (`license/vendor`) همان‌ها را از روی تصویر می‌خواند و باید دقیقاً همان
 * نشانی را بدهد — با هر اندازهٔ خانه. هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const R = require('../src/lib/receipt-token');

const ROOT = path.join(__dirname, '..', '..');
const QV = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'receipt-qr-v1.json'), 'utf8'));

function zxing() {
  let Z = require(path.join(ROOT, 'license', 'vendor', 'zxing-0.21.3.min.js'));
  if (!Z.QRCodeReader) Z = globalThis.ZXing || Z;
  return Z;
}

/** ماتریس ⇐ تصویرِ خاکستری با ۴ خانه حاشیه و [scale] پیکسل برای هر خانه ⇐ متن */
function decode(Z, rows, scale) {
  const n = rows.length + 8;
  const W = n * scale;
  const lum = new Uint8ClampedArray(W * W).fill(255);
  rows.forEach((row, y) => [...row].forEach((on, x) => {
    if (on !== '1') return;
    for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) lum[((y + 4) * scale + dy) * W + (x + 4) * scale + dx] = 0;
  }));
  const bitmap = new Z.BinaryBitmap(new Z.HybridBinarizer(new Z.RGBLuminanceSource(lum, W, W)));
  //  تصویرِ ساخته‌شده بی نویز است؛ PURE_BARCODE حدسِ اندازه از نشانگرها را کنار می‌گذارد
  return new Z.QRCodeReader().decode(bitmap, new Map([[Z.DecodeHintType.PURE_BARCODE, true]])).getText();
}

test('receipt-qr-v1: هر ماتریس اندازهٔ نسخهٔ خودش را دارد و zxing همان نشانی را می‌خواند', () => {
  const Z = zxing();
  assert.deepEqual([...new Set(QV.cases.map((c) => c.version))].sort(), [1, 4, 5, 6]);
  for (const c of QV.cases) {
    const size = 17 + 4 * c.version;
    assert.equal(c.rows.length, size, c.text);
    c.rows.forEach((r) => assert.match(r, new RegExp('^[01]{' + size + '}$')));
    for (const scale of [4, 6, 8]) assert.equal(decode(Z, c.rows, scale), c.text, `${c.text} @${scale}`);
  }
});

test('receipt-qr-v1: نشانیِ واقعیِ فاکتور (ریشه + /r/<نشان>/<توکن>) در بردار هست و سرور آن را می‌شناسد', () => {
  const real = QV.cases.filter((c) => /\/r\/[0-9a-z]{8}\/[0-9a-z]{26}$/.test(c.text));
  assert.ok(real.length >= 3);
  for (const c of real) {
    const [, tag, token] = /\/r\/([0-9a-z]{8})\/([0-9a-z]{26})$/.exec(c.text);
    assert.equal(R.normalizeTag(tag), tag);
    assert.equal(R.normalizeToken(token), token);
  }
});
