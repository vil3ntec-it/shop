'use strict';
/**
 * S28b — «تاریخِ تصحیح‌شده» ِ سند در Sync v3 (`lib/sync-v3-time.js`): فقط با اختلافِ بیش از ۵ دقیقه، فقط از سرور،
 * و `createdAt` ِ دستگاه دست نمی‌خورد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const hlc = require('../src/lib/hlc');
const { corrected } = require('../src/lib/sync-v3-time');

const NOW = Date.UTC(2026, 10, 2, 9, 0, 0);
const eff = (wall) => hlc.format({ wall, counter: 0, node: 'srv' });

test('ساعتِ سالِ ۲۰۰۰ ⇒ correctedAt = زمانِ تصحیح‌شده؛ createdAt و date همان', () => {
  const y2k = Date.UTC(2000, 0, 1);
  const d = corrected({ createdAt: y2k, date: '2000-01-01', totalMinor: 5000 }, eff(NOW));
  assert.deepEqual(d, { createdAt: y2k, date: '2000-01-01', totalMinor: 5000, correctedAt: NOW });
});

test('ساعتِ یک روز جلو ⇒ همان دیواریِ HLCِ مؤثرِ سرور', () => {
  assert.equal(corrected({ createdAt: NOW + 86_400_000 }, eff(NOW)).correctedAt, NOW);
});

test('اختلافِ تا ۵ دقیقه یا سندِ بی تاریخ ⇒ هیچ فیلدِ تازه‌ای', () => {
  assert.deepEqual(corrected({ createdAt: NOW - hlc.SKEW_MS }, eff(NOW)), { createdAt: NOW - hlc.SKEW_MS });
  assert.deepEqual(corrected({ createdAt: NOW + hlc.SKEW_MS }, eff(NOW)), { createdAt: NOW + hlc.SKEW_MS });
  assert.deepEqual(corrected({ quantityMilli: 1000 }, eff(NOW)), { quantityMilli: 1000 });
  assert.deepEqual(corrected({ createdAt: 'x' }, eff(NOW)), { createdAt: 'x' });
});

test('correctedAt ِ نوشتهٔ دستگاه پذیرفته نمی‌شود', () => {
  assert.deepEqual(corrected({ createdAt: NOW, correctedAt: 1 }, eff(NOW)), { createdAt: NOW });
  assert.equal(corrected({ createdAt: 1000, correctedAt: 1 }, eff(NOW)).correctedAt, NOW);
});
