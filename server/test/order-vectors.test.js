'use strict';
/**
 * بردارِ مشترکِ ماشینِ وضعیتِ سفارش (`core/test-vectors/order-states.json`).
 *
 *   ۱) فایل همان خروجیِ امروزِ `order-states.js` است — ماشینِ وضعیت بی
 *      بردار عوض نمی‌شود (آینهٔ کاتلین، `OrderStatesTest.kt`، همین فایل را می‌خواند).
 *   (بخش‌های ۲ و ۳ — هستهٔ `license/orders-ui.js` ِ وبِ دکان — با برداشتنِ وبِ دکان به تصمیمِ صاحب رفتند، ۱۴۰۵/۰۸/۲۰.)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const gen = require('../scripts/order-vectors');
const S = require('../src/lib/order-states');

const vec = JSON.parse(fs.readFileSync(gen.FIXTURE, 'utf8'));

test('۱) بردار با order-states.js امروز یکی است', () => {
  assert.equal(fs.readFileSync(gen.FIXTURE, 'utf8'), gen.expected(),
    'بردارها کهنه‌اند: cd server && node scripts/order-vectors.js --write');
  //  خودِ سنجه خالی نیست
  //  هر وضعیت × دو نوعِ تحویل × هر نقشِ کارمند (با چهار نقشِ تازهٔ A16)
  assert.ok(vec.check.length > 500 && vec.nextFor.length === S.ORDER_STATUSES.length * 2 * require('../src/lib/permissions').ROLES.length);
});
