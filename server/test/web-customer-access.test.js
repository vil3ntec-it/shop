'use strict';
/**
 * CRM-15 در نسخهٔ وبِ دکان — منطقِ `license/customer-access.js` بی مرورگر (صفحه تا سرور: `web-customer-access.mjs`).
 *
 *   ۱) پوشاندن = `maskPhone` ِ سرور (`lib/customer-contact.js`) = `ContactMask.phone` ِ برنامه: هر رقمِ یونیکد جز دو
 *      رقمِ آخر «•»؛ نشانه، فاصله و جهت‌نما همان.
 *   ۲) مجوز از `policy.effective` ِ سرور (همان `ShopRole.allows`)؛ صاحب همیشه؛ بی `policy` پیش‌فرضِ سه نقشِ قدیم؛ بی دکان بله.
 *   ۳) بدنهٔ درخواست فقط شناسه و نوع؛ شناسهٔ ناامن هیچ؛ `*` فقط با `export` — همان `CustomerAccess.noteBody` و `noteApp` ِ سرور.
 *   ۴) ردیفِ فهرست: نام دادهٔ خام، نقش و نوع کلیدِ واژه‌نامه، «برگهٔ همه» با `all`، شمار فقط بیش از یک.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const CA = require('../../license/customer-access.js');
const { maskPhone } = require('../src/lib/customer-contact');
const core = require('../src/lib/perm-core');

test('پوشاندنِ شماره همان maskPhone ِ سرور است (رقمِ فارسی و عربی، ‎+93 با جهت‌نما، کوتاه، خالی)', () => {
  const samples = ['0799123456', '+93 799 123 456', '‎+93 79 912 3456', '۰۷۹۹۱۲۳۴۵۶', '٠٧٩٩١٢٣٤٥٦', '07-99', '7', '', 'بی رقم', '+93799123456'];
  for (const s of samples) assert.equal(CA.mask(s), maskPhone(s), JSON.stringify(s));
  assert.equal(CA.mask('0799123456'), '••••••••56');
  assert.equal(CA.mask('‎+93 79 912 3456'), '‎+•• •• ••• ••56');
  assert.equal(CA.mask(null), '');
});

test('مجوز از policy ِ سرور؛ صاحب همیشه؛ بی policy پیش‌فرضِ نقشِ قدیم؛ بی دکان بله', () => {
  const policy = (role, layers) => ({ v: 1, role, layers, effective: core.effective(role, layers) });
  assert.equal(CA.allows(null, 'customers.contact'), true, 'بی حساب: دفترِ خودِ همین مرورگر');
  assert.equal(CA.allows({ role: 'owner', isOwner: true, policy: { effective: { 'customers.contact': { mode: 'deny' } } } }, 'customers.contact'), true);
  //  نقشِ تازه در `role` ِ قدیم «staff» است؛ تصمیم با `policy.effective` (همان `actualRole`)
  assert.equal(CA.allows({ role: 'staff', policy: policy('storekeeper', [{}, {}]) }, 'customers.contact'), false);
  assert.equal(CA.allows({ role: 'staff', policy: policy('cashier', [{}, {}]) }, 'customers.contact'), true);
  assert.equal(CA.allows({ role: 'staff', policy: policy('cashier', [{}, { 'customers.contact': { mode: 'deny' } }]) }, 'customers.contact'), false);
  assert.equal(CA.allows({ role: 'staff', policy: policy('accountant', [{}, {}]) }, 'audit.view'), true);
  assert.equal(CA.allows({ role: 'staff', policy: policy('cashier', [{}, {}]) }, 'audit.view'), false);
  assert.equal(CA.allows({ role: 'manager', policy: policy('manager', [{}, { 'audit.view': { mode: 'deny' } }]) }, 'audit.view'), false);
  //  حالتِ کهنه بی policy: همان سه نقشِ قدیم
  assert.equal(CA.allows({ role: 'staff' }, 'customers.contact'), true);
  assert.equal(CA.allows({ role: 'staff' }, 'audit.view'), false);
  assert.equal(CA.allows({ role: 'manager' }, 'audit.view'), true);
  assert.equal(CA.allows({ role: 'staff' }, 'unknown.key'), false);
  //  `effective` ِ نادرست یا کلیدِ ارثی ⇒ پیش‌فرض، نه پرتاب
  assert.equal(CA.allows({ role: 'staff', policy: { effective: 'x' } }, 'customers.contact'), true);
  assert.equal(CA.allows({ role: 'staff', policy: { effective: {} } }, 'toString'), false);
});

test('بدنهٔ درخواست فقط شناسه و نوع؛ شناسهٔ ناامن هیچ؛ «*» فقط با export', () => {
  assert.deepEqual(CA.noteBody(' d-1 ', 'phone'), { customerId: 'd-1', kind: 'phone' });
  assert.deepEqual(CA.noteBody('01JCX9-a_b.c', 'detail'), { customerId: '01JCX9-a_b.c', kind: 'detail' });
  assert.deepEqual(CA.noteBody('*', 'export'), { customerId: '*', kind: 'export' });
  for (const [id, kind] of [['*', 'detail'], ['*', 'phone'], ['d-1', 'peek'], ['d-1', undefined], ['', 'detail'], ['a b', 'detail'],
    ['x&y=1', 'detail'], ['../d', 'detail'], ['erased:era1', 'detail'], ['ph:abc', 'detail'], ['a'.repeat(81), 'detail'], ['دفتر', 'detail'],
    [42, 'detail'], [null, 'detail'], [['d-1'], 'detail']]) {
    assert.equal(CA.noteBody(id, kind), null, JSON.stringify([id, kind]));
  }
});

test('ردیفِ فهرست: نام خام، نقش و نوع کلیدِ واژه‌نامه، برگهٔ همه، شمار فقط بیش از یک', () => {
  const fmt = (at) => `t${at}`;
  assert.deepEqual(CA.parts({ viewerName: ' احمد ', role: 'accountant', kind: 'export', all: true, lastAt: 9, count: 3 }, fmt), [
    { text: 'احمد', raw: true }, { text: 'حسابدار (فقط دیدن)' }, { text: 'خروجیِ همهٔ مشتریان' }, { text: 't9', raw: true }, { text: '3 بار' },
  ]);
  assert.deepEqual(CA.parts({ viewerName: '', role: 'nope', kind: 'peek', firstAt: 4, count: 1 }, fmt), [
    { text: 'ناشناس' }, { text: 't4', raw: true },
  ]);
  assert.deepEqual(CA.parts({ role: 'cashier', kind: 'phone', count: 1 }, fmt).map((p) => p.text), ['ناشناس', 'صندوق‌دار', 'شماره و نشانی']);
  assert.deepEqual(CA.parts({ role: 'owner', kind: 'export', all: 'true' }, fmt).map((p) => p.text), ['ناشناس', 'صاحب دکان', 'خروجی'], 'فقط بولیِ true');
  //  همان نوع‌های سرور
  assert.deepEqual(Object.keys(CA.TEXT.kind).filter((k) => k !== 'all'), require('../src/lib/customer-access').KINDS.slice());
});

test('بارکننده و پیش‌کشِ آفلاین: shop-sync.js پرونده را بار می‌کند و policy را نگه می‌دارد؛ سرویس‌ورکر آن را دارد', () => {
  const root = path.join(__dirname, '..', '..');
  const sync = fs.readFileSync(path.join(root, 'license', 'shop-sync.js'), 'utf8');
  assert.match(sync, /src: 'license\/customer-access\.js\?v=1'/);
  assert.match(sync, /policy: r\.policy \|\| null/);
  assert.match(fs.readFileSync(path.join(root, 'sw.js'), 'utf8'), /'\.\/license\/customer-access\.js'/);
});
