'use strict';
/**
 * CHK-08 — پین از پیوند یا مختصاتِ برنامهٔ نقشه در ویترینِ وب (`public/store/pin-text.js`، دیربار از checkout.js با
 * `store.addressExtra`)، بی شبکه و بی ژئوکدر.
 *
 * آن‌چه این پرونده قفل می‌کند (بی مرورگر و بی پایگاه):
 *   ۱) بردارِ مشترکِ `core/test-vectors/pin-text-v1.json` — همان که `PinTextVectorsTest` با `PinText.parse` ِ برنامهٔ خرید
 *      می‌خواند: هر دو از همان نوشته همان پین (یا هیچ) را می‌سازند.
 *   ۲) گردِ شش‌رقمیِ همان پینِ ثبت (`orders.parseAddress`) و همان خانهٔ «کجا؟» (`storefront-delivery.pinCell`).
 *   ۳) پرونده هیچ تماسِ شبکه و هیچ HTML ِ خامی ندارد؛ checkout.js فقط با `addressExtra` بارش می‌کند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const PUB = path.join(__dirname, '..', 'public');
const SRC = fs.readFileSync(path.join(PUB, 'store', 'pin-text.js'), 'utf8');
const vec = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'pin-text-v1.json'), 'utf8'));
const box = { window: {} };
vm.runInNewContext(SRC, box);
const P = box.window.StorePinText;

test('بردار: همان پینِ `PinText.parse` ِ برنامهٔ خرید از هر نوشته (پیوندِ گوگل/OSM/اپل، geo:، رقمِ فارسی) یا هیچ', () => {
  assert.equal(vec.version, 1);
  assert.ok(vec.parse.length >= 20);
  for (const c of vec.parse) {
    const p = P.parse(c.text);
    assert.deepEqual(p ? [p.lat, p.lng] : null, c.want, c.name);
  }
});

test('پینِ چسبانده همان پینِ ثبت و همان خانهٔ «کجا؟» را دارد', () => {
  const orders = require('../src/lib/orders');
  const sfd = require('../src/lib/storefront-delivery');
  for (const c of vec.parse.filter((x) => x.want)) {
    const p = P.parse(c.text);
    const a = orders.parseAddress({ lat: p.lat, lng: p.lng });
    assert.deepEqual([a.lat, a.lng], [p.lat, p.lng], c.name);
    assert.deepEqual(sfd.pinCell(p.lat, p.lng), sfd.snapPin(a.lat, a.lng), c.name);
  }
});

test('بی شبکه و بی HTML ِ خام؛ checkout.js فقط با `addressExtra` و با کلیک بارش می‌کند', () => {
  const code = SRC.split('\n').filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l)).join('\n');
  assert.doesNotMatch(code, /fetch\(|XMLHttpRequest|K\.api|\bapi\(|innerHTML|outerHTML|insertAdjacentHTML|document\.write|\beval\(|new Function\(/);
  const ck = fs.readFileSync(path.join(PUB, 'store', 'checkout.js'), 'utf8');
  assert.match(ck, /K\.js\('\/store\/pin-text\.js'\)/);
  assert.match(ck, /c\.store\.addressExtra && K\.has\('pin\.paste\.l'\)/);
});
