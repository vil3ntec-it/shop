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
 * ⛔ ورودِ محلی (`getAuth`/`saveAuth`/`applyAuthToUI`) زنده است و باید بماند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
const common = fs.readFileSync(path.join(ROOT, 'android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/Common.kt'), 'utf8');
const defines = (src, name) => new RegExp(`function\\s+${name}\\s*\\(`).test(src);
const uses = (src, name) => (src.match(new RegExp(`(^|[^.\\w])${name}\\(`, 'gm')) || []).length;

test('وب: ورودِ قدیمیِ WebSocket و نشانی/رمزِ دستیِ سرور نیست', () => {
  const dead = ['wsRequest', 'testServerConnection', 'wireConnectButton', 'saveServerUrl', 'saveServerToken', 'getServerToken', 'validatePhone', 'validateEmail']
    .filter((n) => defines(html, n));
  assert.deepEqual(dead, [], `کدِ مرده برگشته: ${dead.join('، ')}`);
  assert.ok(!html.includes('tohid-shop-server-token'), 'رمزِ دستیِ سرورِ قدیمی هنوز در index.html است');
  assert.ok(!/new WebSocket\(\s*(base|clean)\s*\+/.test(html), 'WebSocket به نشانیِ دستیِ کاربر هنوز ساخته می‌شود');
});

test('وب: ورودِ محلیِ زنده سرِ جایش است و صدا زده می‌شود', () => {
  for (const n of ['getAuth', 'saveAuth', 'clearAuth', 'applyAuthToUI']) {
    assert.ok(defines(html, n), `${n} نباید حذف شود`);
    assert.ok(uses(html, n) >= 2, `${n} تعریف شده ولی صدا زده نمی‌شود`);
  }
});

test('نیتیو: ComingSoon() نیست', () => {
  assert.ok(!/fun\s+ComingSoon\s*\(/.test(common), 'ComingSoon() برگشته');
});
