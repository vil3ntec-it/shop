'use strict';
/**
 * QRِ رسیدِ فروش (RET-01، برگشت با اسکنِ رسید) — سنجشِ مستقل.
 *
 * ماتریس‌های `core/test-vectors/qr-v1.json` را رمزگذارِ کاتلینِ برنامهٔ گوشی و
 * کامپیوتر می‌سازد (`core/qr/QrCode.kt`؛ `QrCodeTest` برابریِ دقیقش را می‌سنجد).
 * این‌جا یک خوانندهٔ مستقل — zxingِ `license/vendor` — همان ماتریس‌ها را از روی
 * تصویر می‌خواند و باید دقیقاً همان متن را بدهد؛ پس درستیِ رمزگذاری با خودش
 * سنجیده نمی‌شود. هیچ پایگاه‌داده‌ای لازم نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const QV = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'qr-v1.json'), 'utf8'));

function zxing() {
  let Z = require(path.join(ROOT, 'license', 'vendor', 'zxing-0.21.3.min.js'));
  if (!Z.QRCodeReader) Z = globalThis.ZXing || Z;
  return Z;
}

/** ماتریس ⇐ تصویرِ خاکستری با ۴ خانه حاشیه و ۴ پیکسل برای هر خانه ⇐ متن */
function decode(Z, rows) {
  const n = rows.length + 8, scale = 4, W = n * scale;
  const lum = new Uint8ClampedArray(W * W).fill(255);
  rows.forEach((row, y) => [...row].forEach((on, x) => {
    if (on !== '1') return;
    for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) lum[((y + 4) * scale + dy) * W + (x + 4) * scale + dx] = 0;
  }));
  const bitmap = new Z.BinaryBitmap(new Z.HybridBinarizer(new Z.RGBLuminanceSource(lum, W, W)));
  return new Z.QRCodeReader().decode(bitmap).getText();
}

test('qr-v1: هر ماتریس مربعِ ۲۱/۲۵/۲۹ خانه است و zxing همان متن را می‌خواند', () => {
  const Z = zxing();
  assert.ok(QV.cases.length >= 4);
  for (const c of QV.cases) {
    const size = c.rows.length;
    assert.ok([21, 25, 29].includes(size), c.text + ' ' + size);
    c.rows.forEach((r) => assert.match(r, new RegExp('^[01]{' + size + '}$')));
    assert.equal(decode(Z, c.rows), c.text);
  }
});

test('qr-v1: متنِ رسیدِ واقعی (TR1: + شناسهٔ ۲۶ نویسه‌ای) در بردار هست', () => {
  assert.ok(QV.cases.some((c) => /^TR1:[0-9A-Z]{26}$/.test(c.text)));
  assert.ok(QV.cases.some((c) => c.text.startsWith('TR1#')));
});
