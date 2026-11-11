'use strict';
/**
 * ۲-۸ ِ صاحب‌کار (CRM-15 الف؛ `lib/orders.js` ‹`maskerOf`›، پرچمِ خاموشِ `shop.orders.phone_mask`): شمارهٔ مشتری در سفارش.
 *
 *   خاموش ⇒ همان امروز: کارتِ هر بیننده چهار نویسهٔ نخست و سه رقمِ آخر (`phoneMasked` ِ ۴+۳).
 *   روشن ⇒ بیننده‌ای که «دیدنِ تماس» (`customers.contact`) ندارد — جمع‌آور، انباردار، یا نقشی که صاحب برایش بسته — فقط دو
 *          رقمِ آخر را می‌بیند (همان `ContactMask` ِ برنامه) در فهرست و جزئیات؛ دارندهٔ مجوز همان کارتِ امروز و شمارهٔ کامل
 *          فقط همان `orders.contact` (صاحب و مدیر). پیکِ همان سفارش شماره را با درِ تماس می‌گیرد (دست نخورد).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const digits = (s) => String(s || '').replace(/[^0-9]/g, '');

test('پرچمِ خاموش همان ۴+۳؛ روشن: بی «دیدنِ تماس» فقط دو رقمِ آخر در فهرست و جزئیات، دارندهٔ مجوز همان امروز', async () => {
  const u = await h.newUser(`مالک-${uniq()}`, 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ پوشاندن' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  const add = async (role) => {
    const m = await h.newUser(`${role}-${uniq()}`, 'shop');
    await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
      [newId('mem'), shopId, m.user.id, role, now()]);
    return m.accessToken;
  };
  const picker = await add('picker');
  const manager = await add('manager');
  const staff = await add('staff');
  const o = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری', phone: '0791234567' },
    lines: [{ name: 'برنج', unit: 'کیلو', qtyMilli: 1000, unitPriceMinor: 12_000 }],
  }, { token: u.accessToken, headers: { 'Idempotency-Key': `pm-${uniq()}` } });
  assert.equal(o.status, 201, JSON.stringify(o.body));
  const id = o.body.order.id;
  const card = async (token) => {
    const r = await h.get('/api/shop/orders?tab=new', { token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.items.find((x) => x.id === id).customer;
  };
  const detail = async (token) => {
    const r = await h.get(`/api/shop/orders/${id}`, { token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.order.customer;
  };

  const today = (await card(picker)).phoneMasked;
  assert.equal(today, '+937•••••567', 'امروز: چهار نویسهٔ نخست و سه رقمِ آخر');
  assert.equal((await detail(picker)).phoneMasked, today);
  assert.equal((await card(manager)).phoneMasked, today);

  await setFlag(shopId, 'shop.orders.phone_mask', true);
  const strong = (await card(picker)).phoneMasked;
  assert.equal(digits(strong), '67', `جمع‌آور فقط دو رقمِ آخر: ${strong}`);
  assert.ok(strong.includes('•'));
  const pd = await detail(picker);
  assert.deepEqual([pd.phoneMasked, pd.phone], [strong, undefined], 'جزئیات هم');
  assert.equal((await card(staff)).phoneMasked, today, 'شاگرد «دیدنِ تماس» دارد ⇒ همان امروز');
  assert.equal((await card(manager)).phoneMasked, today);
  assert.equal(digits((await detail(manager)).phone).slice(-9), '791234567', 'مدیر شمارهٔ کامل');
  assert.equal(digits((await detail(u.accessToken)).phone).slice(-9), '791234567', 'صاحب شمارهٔ کامل');

  //  صاحب «دیدنِ تماس» را برای شاگرد می‌بندد ⇒ همان پوششِ تنگ
  const lay = await h.put('/api/shop/permissions/roles/staff', { perms: { 'customers.contact': { mode: 'deny' } } }, { token: u.accessToken });
  assert.equal(lay.status, 200, JSON.stringify(lay.body));
  assert.equal(digits((await card(staff)).phoneMasked), '67', 'لایهٔ نقش');

  await setFlag(shopId, 'shop.orders.phone_mask', false);
  assert.equal((await card(picker)).phoneMasked, today, 'خاموش ⇒ همان امروز');
});
