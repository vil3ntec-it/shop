'use strict';
/**
 * نمای کارتی/جدولیِ وب (license/view-mode.js) همان `ViewModePref.kt` است —
 * همان بردارهای `ViewModePrefTest.kt`، به‌علاوهٔ انبارِ localStorage.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const vm = require(path.join(__dirname, '..', '..', 'license', 'view-mode.js'));

function fakeStorage(throws = false) {
  const m = new Map();
  return {
    getItem: (k) => { if (throws) throw new Error('blocked'); return m.has(k) ? m.get(k) : null; },
    setItem: (k, v) => { if (throws) throw new Error('blocked'); m.set(k, String(v)); },
    _m: m,
  };
}

test('پیش‌فرض: مدیریت جدول، فروش کارت، گوشی همیشه کارت', () => {
  assert.equal(vm.defaultMode('management', 'large'), 'table');
  assert.equal(vm.defaultMode('sale', 'large'), 'card');
  assert.equal(vm.defaultMode('management', 'phone'), 'card');
  assert.equal(vm.defaultMode('sale', 'phone'), 'card');
});

test('انتخابِ ذخیره‌شده روی پنجرهٔ پهن؛ روی گوشی بی‌اثر؛ مقدارِ خراب = پیش‌فرض', () => {
  assert.equal(vm.resolve('card', 'management', 'large'), 'card');
  assert.equal(vm.resolve('table', 'sale', 'large'), 'table');
  assert.equal(vm.resolve('table', 'management', 'phone'), 'card');
  assert.equal(vm.resolve('grid??', 'management', 'large'), 'table');
  assert.equal(vm.resolve(null, 'management', 'large'), 'table');
  assert.equal(vm.resolve(' TABLE ', 'sale', 'large'), 'table');
  assert.equal(vm.parse(''), null);
});

test('کلید: هر کاربر و هر صفحه جدا؛ بی‌حساب = local؛ «:» دو کلید را یکی نمی‌کند', () => {
  const a = vm.key('u1', 'products');
  assert.equal(a, 'view_mode:u1:products');
  assert.notEqual(a, vm.key('u2', 'products'));
  assert.notEqual(a, vm.key('u1', 'sale'));
  assert.equal(vm.key('', 'products'), 'view_mode:local:products');
  assert.equal(vm.key(null, 'products'), 'view_mode:local:products');
  assert.notEqual(vm.key('a:b', 'c'), vm.key('a', 'b:c'));
  assert.throws(() => vm.key('u1', ' '));
});

test('ذخیره و خواندن در localStorage: جدا برای کاربر و صفحه؛ گوشی چیزی نمی‌نویسد', () => {
  const ls = fakeStorage();
  globalThis.localStorage = ls;
  try {
    vm.save('products', 'card', 'large', 'u1');
    assert.equal(ls._m.get('tohid-ui:view_mode:u1:products'), 'card');
    assert.equal(vm.load('products', 'management', 'large', 'u1'), 'card');
    assert.equal(vm.load('products', 'management', 'large', 'u2'), 'table');
    assert.equal(vm.load('customers', 'management', 'large', 'u1'), 'table');
    assert.equal(vm.load('sale', 'sale', 'large', 'u1'), 'card');

    vm.save('products', 'table', 'large', 'u1');
    vm.save('products', 'card', 'phone', 'u1');   // گوشی: نوشته نمی‌شود
    assert.equal(vm.load('products', 'management', 'large', 'u1'), 'table');
    assert.equal(vm.load('products', 'management', 'phone', 'u1'), 'card');
    assert.equal(vm.toggleVisible('phone'), false);
    assert.equal(vm.toggleVisible('large'), true);

    globalThis.localStorage = fakeStorage(true);   // حافظهٔ بسته: پیش‌فرض، بی خطا
    assert.equal(vm.load('products', 'management', 'large', 'u1'), 'table');
    vm.save('products', 'card', 'large', 'u1');
  } finally { delete globalThis.localStorage; }
});

test('نمای دیگر و مرزِ گوشی', () => {
  assert.equal(vm.other('card'), 'table');
  assert.equal(vm.other('table'), 'card');
  assert.equal(vm.formFactor(360), 'phone');
  assert.equal(vm.formFactor(639), 'phone');
  assert.equal(vm.formFactor(640), 'large');
  assert.equal(vm.formFactor(1280), 'large');
});
