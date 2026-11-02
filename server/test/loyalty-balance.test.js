'use strict';
/**
 * PRM-13 — پیادهٔ JS ِ ماندهٔ امتیاز با انقضای FIFO (`lib/loyalty-balance.js`) همان `Loyalty.state` ِ هستهٔ برنامه است:
 * بردارهای همین پرونده همان بردارهای `core/.../promo/LoyaltyExpiryTest.kt` با همان عددها‌اند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../src/lib/loyalty-balance');

const DAY = 86400000;
const YEAR = 365 * DAY;
const rule = { expiryMonths: 12 };
const never = { expiryMonths: 0 };
const ev = (id, kind, points, at, saleId = '') => ({ id, kind, points, at, saleId });
const plain = (e) => e.reduce((a, x) => a + L.signed(x), 0);

test('۱۲ ماه دقیقاً همان سالِ سطح است', () => {
  assert.equal(12 * L.MONTH_MS, YEAR);
});

test('کهنه‌ترین دسته اول؛ درست در مرز می‌سوزد', () => {
  const e = [ev('e1', 'earn', 100, 0, 's1'), ev('e2', 'earn', 50, 100 * DAY, 's2'), ev('e3', 'spend', 30, 200 * DAY, 's3')];
  assert.deepEqual(L.state(e, rule, YEAR - 1), { balance: 120, expired: 0, soon: 70, soonAt: YEAR });
  assert.deepEqual(L.state(e, rule, YEAR), { balance: 50, expired: 70, soon: 0, soonAt: null });
  assert.equal(L.state(e, never, 10 * YEAR).balance, plain(e));
});

test('پس‌گرفتنِ کسب اول از دستهٔ همان فاکتور', () => {
  const e = [ev('e1', 'earn', 100, 0, 's1'), ev('e2', 'earn', 50, 100 * DAY, 's2'), ev('e3', 'earn_back', 20, 150 * DAY, 's2')];
  const s = L.state(e, rule, YEAR);
  assert.equal(s.balance, 30);
  assert.equal(s.expired, 100);
});

test('کسریِ دو دستگاه پیش از دستهٔ تازه صاف می‌شود', () => {
  const e = [ev('e1', 'spend', 10, 0, 's1'), ev('e2', 'earn', 25, 10 * DAY, 's2')];
  assert.equal(L.state(e, rule, 10 * DAY + YEAR - 1).balance, 15);
  const s = L.state(e, rule, 10 * DAY + YEAR);
  assert.equal(s.balance, 0);
  assert.equal(s.expired, 15);
  assert.equal(L.state([ev('x', 'spend', 10, 0, 's1')], rule, 5).balance, -10);
});

test('برگشتِ مصرف از لحظهٔ همان مصرف می‌شمارد', () => {
  const e = [ev('e1', 'earn', 100, 0, 's1'), ev('e2', 'spend', 40, 300 * DAY, 's3'), ev('e3', 'spend_back', 40, 400 * DAY, 's3')];
  assert.deepEqual([L.state(e, rule, YEAR + 1).balance, L.state(e, rule, YEAR + 1).expired], [40, 60]);
  assert.deepEqual([L.state(e, rule, 300 * DAY + YEAR).balance, L.state(e, rule, 300 * DAY + YEAR).expired], [0, 100]);
});

test('اصلاحِ دستی با علامت و FIFO', () => {
  const e = [ev('e1', 'earn', 10, 0, 's1'), ev('a1', 'adjust', 5, DAY), ev('a2', 'adjust', -12, 2 * DAY)];
  assert.equal(plain(e), 3);
  assert.equal(L.state(e, rule, YEAR).balance, 3);
  assert.deepEqual([L.state(e, rule, DAY + YEAR).balance, L.state(e, rule, DAY + YEAR).expired], [0, 3]);
});

test('هیچ دسته‌ای پس از now نمی‌سوزد، حتی با ردیفِ «آینده»', () => {
  const e = [ev('e1', 'earn', 100, 0, 's1'), ev('e2', 'earn', 1, 2 * YEAR, 's2')];
  assert.equal(L.state(e, rule, YEAR - 1).balance, 101);
});

test('سطح از خریدِ ۱۲ ماهه', () => {
  const r = { silverAfn: 50000, goldAfn: 200000 };
  assert.equal(L.tier(r, 49999), 'bronze');
  assert.equal(L.tier(r, 50000), 'silver');
  assert.equal(L.tier(r, 200000), 'gold');
});
