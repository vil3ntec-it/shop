'use strict';
/**
 * نمونهٔ عمداً ناموفق برای `test/test-failures-summary.test.js` — `.test.js` نیست تا
 * در `npm test` اجرا نشود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');

test('سبزِ نمونه', () => { assert.equal(1, 1); });

test('گروهِ نمونه', async (t) => {
  await t.test('زیرآزمونِ سبز', () => {});
  await t.test('زیرآزمونِ قرمز', () => { assert.equal(2 + 2, 5, 'جمعِ نادرست'); });
});

test('قرمزِ سطحِ بالا', () => { throw new Error('خطای نمونه\nخطِ دوم'); });
