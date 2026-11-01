'use strict';
/**
 * CHK-10 — هستهٔ خالصِ `license/area-requests.js` (فهرستِ «خبرم کن» در بخشِ «سفارش‌ها»ی وب):
 * پاسخِ ناقص هرگز خطا نمی‌دهد؛ خانه‌های هنوز بیرون از ناحیه پیش از پوشیده‌ها و پرتقاضاتر اول؛ حداکثر ۲۰ خانه؛
 * و برچسبِ خانه شماره ندارد (فقط شمار و مختصاتِ مرکزِ خانهٔ ۳۰۰ متری).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const A = require('../../license/area-requests.js');

test('پاسخِ ناقص ⇒ صفر و فهرستِ خالی', () => {
  for (const r of [null, undefined, 'x', {}, { cells: 'x', contacts: null }]) {
    assert.deepEqual(A.view(r), { total: 0, covered: 0, cells: [], contacts: [] });
  }
});

test('بیرون از ناحیه اول، پرتقاضاتر اول، حداکثر ۲۰', () => {
  const cells = [
    { lat: 34.5, lng: 69.1, count: 9, coveredBy: 'کارته‌سه' },
    { lat: 34.6, lng: 69.3, count: 2, coveredBy: null },
    { lat: 34.7, lng: 69.4, count: 5, coveredBy: null },
    ...Array.from({ length: 30 }, (_, i) => ({ lat: 35 + i / 100, lng: 70, count: 1, coveredBy: null })),
  ];
  const v = A.view({ total: 46, covered: 9, cells, contacts: [{ id: 'a', phone: '+93791200003', zone: 'کارته‌سه' }] });
  assert.equal(v.cells.length, 20);
  assert.deepEqual(v.cells.slice(0, 2).map((c) => c.count), [5, 2]);
  assert.ok(v.cells.every((c) => !c.coveredBy), 'پوشیده‌ها پس از بیرونی‌ها (این‌جا از ۲۰ بیرون ماند)');
  assert.equal(v.contacts.length, 1);
  assert.equal(cells[0].count, 9, 'ورودی دست نخورد');
});

test('برچسبِ خانه: شمار، مختصات و ناحیه — بی شماره', () => {
  const out = A.cellLabel({ lat: 34.6, lng: 69.3, count: 2, coveredBy: null });
  assert.match(out, /هنوز بیرون از ناحیه‌ها/);
  assert.doesNotMatch(out, /\+93|07\d/);
  assert.match(A.cellLabel({ lat: 34.5, lng: 69.1, count: 9, coveredBy: 'کارته‌سه' }), /«کارته‌سه»/);
});
