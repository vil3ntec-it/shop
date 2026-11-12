'use strict';
/**
 * پسرفتِ «else ِ سرگردان» در `store/checkout.js` (بازبینیِ دستهٔ ۵۵): خطِ CRM-09 ِ `address.savedId` میانِ `if (pin)` و
 * `else if (zones) zoneId` نشسته بود ⇒ `else` به `savedId` چسبید: نشانیِ ذخیره‌شدهٔ بی پین در دکانِ ناحیه‌دار بی `zoneId` رفت و
 * سرور ۴۰۰ `zone_required` داد (`lib/storefront-delivery.js`)؛ و با پین هم `zoneId` می‌رفت.
 *
 * همان چند خطِ سازندهٔ نشانیِ `checkout.js` (از `if (pin)` تا `body.delivery`) بی تغییر با ورودی‌های ساختگی اجرا می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const SRC = fs.readFileSync(path.join(__dirname, '..', 'public', 'store', 'checkout.js'), 'utf8');

function addressPart() {
  const from = SRC.indexOf('if (pin) { body.address.lat');
  assert.ok(from > 0, 'سرِ بخشِ نشانی (`if (pin)`) در checkout.js پیدا نشد');
  const to = SRC.indexOf('body.delivery = {', from);
  assert.ok(to > from, 'پایانِ بخشِ نشانی (`body.delivery`) پیدا نشد');
  return SRC.slice(from, to);
}

// eslint-disable-next-line no-new-func
const run = new Function('pin', 'ac', 'c', 'body', `${addressPart()}\nreturn body.address;`);
const zones = { zones: () => true, zone: { id: 'z1' } };
const noZones = { zones: () => false, zone: null };
const saved = (id) => ({ saved: () => id });

test('نشانیِ ذخیره‌شدهٔ بی پین در دکانِ ناحیه‌دار ⇒ هم savedId هم zoneId (نه zone_required)', () => {
  assert.deepEqual(run(null, saved('addr1'), zones, { address: {} }), { zoneId: 'z1', savedId: 'addr1' });
});

test('با پین ناحیه از پین است ⇒ zoneId نمی‌رود، با یا بی نشانیِ ذخیره‌شده', () => {
  assert.deepEqual(run({ lat: 1, lng: 2 }, saved('addr1'), zones, { address: {} }), { lat: 1, lng: 2, savedId: 'addr1' });
  assert.deepEqual(run({ lat: 1, lng: 2 }, saved(''), zones, { address: {} }), { lat: 1, lng: 2 });
  assert.deepEqual(run({ lat: 1, lng: 2 }, null, zones, { address: {} }), { lat: 1, lng: 2 });
});

test('بی پین و بی نشانیِ ذخیره‌شده ⇒ ناحیهٔ برگزیده؛ دکانِ بی ناحیه ⇒ هیچ', () => {
  assert.deepEqual(run(null, saved(''), zones, { address: {} }), { zoneId: 'z1' });
  assert.deepEqual(run(null, undefined, zones, { address: {} }), { zoneId: 'z1' });
  assert.deepEqual(run(null, saved('addr1'), noZones, { address: {} }), { savedId: 'addr1' });
});
