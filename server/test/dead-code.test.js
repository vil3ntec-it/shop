'use strict';
/**
 * F0-20 — کدِ مرده برنگردد.
 *
 * ورودِ قدیمیِ WebSocket ِ وب (`wsRequest`، `testServerConnection`،
 * `wireConnectButton` و نشانی/رمزِ دستیِ سرور) از روزی که ورود با کدِ
 * ایمیلی و `license/api-config.js` آمد هیچ‌جا صدا زده نمی‌شد، و
 * `ComingSoon()` ِ برنامهٔ نیتیو هم هیچ صفحه‌ای نداشت. کدِ مرده‌ای که
 * به نشانیِ دلخواه وصل می‌شود، دلیلِ بازماندنِ `connect-src` بود (F0-22).
 *
 * (وبِ دکان — `index.html` — به تصمیمِ صاحب برداشته شد، ۱۴۰۵/۰۸/۲۰؛ دو سنجهٔ وب با خودِ آن رفتند و صفحهٔ
 * جانشین هیچ کدِ ورود یا WebSocketی ندارد: `web-shop-gone.mjs`.)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const common = fs.readFileSync(path.join(ROOT, 'android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/Common.kt'), 'utf8');

test('نیتیو: ComingSoon() نیست', () => {
  assert.ok(!/fun\s+ComingSoon\s*\(/.test(common), 'ComingSoon() برگشته');
});
