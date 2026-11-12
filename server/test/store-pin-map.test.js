'use strict';
/**
 * CHK-08 / ۴-۳ — ریاضیِ «انتخاب روی نقشه» ِ ویترینِ وب (`public/store/pin-map.js`: `StorePinMap.toPx`/`toLL`) همان است که
 * برنامهٔ خریدِ بومی (`core/.../shopper/ShopperPinMap.kt`، `PinMapVectorsTest`) با بردارِ مشترکِ
 * `core/test-vectors/pin-map-v1.json` می‌سازد: همان نشانهٔ وسط ⇒ همان پین در وب و برنامه.
 *
 * بی مرورگر، بی پایگاه و بی شبکه: پرونده در vm با `K` ِ ساختگی (فقط `$`/`el` که هنگامِ بار خوانده می‌شوند) اجرا می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SRC = fs.readFileSync(path.join(__dirname, '..', 'public', 'store', 'pin-map.js'), 'utf8');
const vec = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'pin-map-v1.json'), 'utf8'));
const box = { window: {}, K: { $: () => null, el: () => null } };
vm.runInNewContext(SRC, box);
const M = box.window.StorePinMap;

const near = (a, b, eps) => Math.abs(a - b) <= eps;

test('بردار: مختصات ⇐ پیکسلِ جهان (toPx) همان برنامهٔ خرید', () => {
  assert.equal(vec.version, 1);
  assert.ok(vec.toPx.length >= 8);
  for (const c of vec.toPx) {
    const p = M.toPx(c.lat, c.lng, c.z);
    assert.ok(near(p.x, c.want[0], 1e-6) && near(p.y, c.want[1], 1e-6), `${c.lat},${c.lng}@${c.z}: ${p.x},${p.y}`);
  }
});

test('بردار: پیکسل ⇐ پین (toLL، شش رقمِ اعشار، طول دور می‌زند، عرض در لبه می‌ایستد)', () => {
  assert.ok(vec.toLL.length >= 6);
  for (const c of vec.toLL) {
    const p = M.toLL(c.x, c.y, c.z);
    assert.deepEqual([p.lat, p.lng], c.want, `${c.x},${c.y}@${c.z}`);
  }
});

test('رفت‌وبرگشت در بزرگنماییِ ۱۶ تا ۱۹ همان پین را می‌دهد و همان گردِ پینِ ثبت را دارد', () => {
  const orders = require('../src/lib/orders');
  for (const c of vec.roundTrip) {
    const a = M.toPx(c.lat, c.lng, c.z);
    const b = M.toLL(a.x, a.y, c.z);
    assert.deepEqual([b.lat, b.lng], c.want);
    assert.deepEqual([b.lat, b.lng], [c.lat, c.lng], 'نشانه روی همان پین می‌ماند');
    const s = orders.parseAddress({ lat: b.lat, lng: b.lng });
    assert.deepEqual([s.lat, s.lng], [b.lat, b.lng], 'سرور همان پین را نگه می‌دارد');
  }
});
