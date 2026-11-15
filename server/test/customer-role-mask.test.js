'use strict';
/**
 * CRM-15 — شماره و نشانیِ مشتری بر پایهٔ نقش (`lib/customer-role-mask.js`، پرچمِ خاموشِ `shop.customer_role_mask`؛ تصمیمِ
 * هماهنگ‌کننده به سپردهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴). پوشاندن در صفحه بس نیست: خودِ پاسخِ سرور به نقش‌های جز صاحب/مدیر پوشیده است یا
 * فیلد را ندارد.
 *
 *   خاموش ⇒ همان امروز (صندوق‌دار و حسابدار شماره را در همگام‌سازی می‌گیرند).
 *   روشن ⇒ همگام‌سازیِ مشتری‌ها: صندوق‌دار/شاگرد/حسابدار بی شماره و نشانی؛ صاحب و مدیر کامل؛ اجازهٔ صریحِ صاحب می‌چربد.
 *          `policy` ِ برنامه `customerMask` و `effective` ِ تازه دارد.
 *          سفارش: کارتِ فهرست «•••• ۵۶۷»؛ جزئیات بی شماره و بی نشانی، مگر سفارشِ بازی که خودِ همان کارمند پذیرفت؛ حسابدار هرگز.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const roleMask = require('../src/lib/customer-role-mask');

const PHONE = '0799123456';
const ADDR = { area: 'کارتهٔ چهار', landmark: 'مسجدِ جامع', gate: 'سبز' };
const T = {};
let shopId = '';
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const tok = (who) => ({ token: T[who].token });

async function member(role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken, userId: u.user.id };
}
async function setFlag(key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'role-mask-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'role-mask-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  const o = await h.newUser('صاحبِ دکانِ نقش‌ها', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ نقش‌ها' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = { ...o, token: o.accessToken, userId: o.user.id };
  for (const role of ['manager', 'staff', 'cashier', 'accountant']) T[role] = await member(role, `کارمندِ ${role}`);
  T.cashier2 = await member('cashier', 'صندوق‌دارِ دوم');
  T.cashierOk = await member('cashier', 'صندوق‌دارِ باز');
  //  اجازهٔ صریحِ صاحب برای همین کاربر
  const r = await h.put(`/api/shop/permissions/members/${T.cashierOk.userId}`,
    { perms: { 'customers.contact': { mode: 'allow' } } }, { token: T.owner.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const seeded = await h.seed(T.owner.token, 'debtors', { id: 'd-mask', data: { name: 'علی', phone: PHONE, deliveryAddress: ADDR } }, { updatedAt: Date.now() });
  assert.equal(seeded.status, 200, JSON.stringify(seeded.body));
  await setFlag('shop.orders', true);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('قالب همان CustomerMask ِ برنامه: «•••• ۰۰۸»، درازای شماره پیدا نیست', () => {
  assert.equal(roleMask.maskPhone('+93791200008'), '•••• 008');
  assert.equal(roleMask.maskPhone('۰۷۹۱۲۰۰۰۰۸'), '•••• ۰۰۸');
  assert.equal(roleMask.maskPhone('+93 79 120 0008'), roleMask.maskPhone('0791200008'));
  assert.equal(roleMask.maskPhone('\u20660791200008\u2069'), '\u2066•••• 008\u2069');
  assert.equal(roleMask.maskPhone('123'), '••••');
  assert.equal(roleMask.maskPhone(''), '');
  assert.equal(roleMask.maskPhone('ali@mail.af'), 'a•••@mail.af');
  assert.ok(require('../src/lib/customer-contact').isMasked(roleMask.maskPhone(PHONE)));
});

test('همگام‌سازیِ مشتری‌ها: خاموش همان امروز؛ روشن ⇒ جز صاحب و مدیر بی شماره و نشانی، اجازهٔ صریحِ صاحب می‌چربد', async () => {
  const row = (who) => h.legacyRow(T[who].token, 'debtors', 'd-mask');
  await setFlag(roleMask.FLAG, false);
  for (const who of ['owner', 'manager', 'staff', 'cashier', 'accountant']) {
    assert.equal((await row(who)).phone, PHONE, `${who}: خاموش ⇒ همان امروز`);
  }
  await setFlag(roleMask.FLAG, true);
  for (const who of ['owner', 'manager', 'cashierOk']) {
    const r = await row(who);
    assert.equal(r.phone, PHONE, who);
    assert.deepEqual(r.deliveryAddress, ADDR, who);
  }
  for (const who of ['staff', 'cashier', 'accountant']) {
    const r = await row(who);
    assert.equal(r.name, 'علی', `${who}: نام و پرونده می‌آید`);
    assert.equal(r.phone, undefined, `${who} phone`);
    assert.equal(r.deliveryAddress, undefined, `${who} address`);
    assert.ok(!JSON.stringify(r).includes('799123'), `${who}: ${JSON.stringify(r)}`);
  }
  await setFlag(roleMask.FLAG, false);
});

test('policy ِ برنامه: پرچم روشن ⇒ customerMask و effective ِ تازه؛ خاموش ⇒ بی کلید', async () => {
  const policy = async (who) => {
    const r = await h.get('/api/shop', tok(who));
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.policy;
  };
  assert.equal((await policy('cashier')).customerMask, undefined);
  assert.equal((await policy('cashier')).effective['customers.contact'].mode, 'allow');
  await setFlag(roleMask.FLAG, true);
  const c = await policy('cashier');
  assert.equal(c.customerMask, true);
  assert.equal(c.effective['customers.contact'].mode, 'deny');
  assert.equal((await policy('accountant')).effective['customers.contact'].mode, 'deny');
  assert.equal((await policy('cashierOk')).effective['customers.contact'].mode, 'allow');
  assert.equal((await policy('manager')).effective['customers.contact'].mode, 'allow');
  await setFlag(roleMask.FLAG, false);
});

test('سفارش: کارتِ «•••• ۴۵۶»؛ جزئیات بی شماره و نشانی جز سفارشِ بازی که خودِ کارمند در دست دارد؛ حسابدار هرگز', async () => {
  const create = async () => {
    const o = await h.post('/api/shop/orders', {
      source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
      customer: { name: 'مشتری', phone: PHONE }, address: { text: 'کارتهٔ چهار، کوچهٔ دوم', landmark: 'مسجد' },
      lines: [{ name: 'برنج', unit: 'کیلو', qtyMilli: 1000, unitPriceMinor: 12_000 }],
    }, { token: T.owner.token, headers: { 'Idempotency-Key': `rm-${uniq()}` } });
    assert.equal(o.status, 201, JSON.stringify(o.body));
    return o.body.order.id;
  };
  const a = await create();
  const b = await create();
  const c = await create();
  const card = async (who, id) => {
    const r = await h.get('/api/shop/orders?tab=new', tok(who));
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.items.find((x) => x.id === id)?.customer;
  };
  const detail = async (who, id) => {
    const r = await h.get(`/api/shop/orders/${id}`, tok(who));
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.order;
  };
  const move = async (who, id, to, reason) => {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to, ...(reason ? { reason } : {}) }, tok(who));
    assert.equal(r.status, 200, JSON.stringify(r.body));
  };

  //  خاموش ⇒ همان امروز: کارتِ ۴+۳ و نشانیِ سفارش برای هر بیننده
  const today = (await card('cashier', a)).phoneMasked;
  assert.equal(today, '+937•••••456');
  assert.equal((await detail('cashier', a)).address.text, 'کارتهٔ چهار، کوچهٔ دوم');

  await setFlag(roleMask.FLAG, true);
  assert.equal((await card('cashier', a)).phoneMasked, '•••• 456', 'صندوق‌دار: سه رقمِ آخر');
  assert.equal((await card('accountant', a)).phoneMasked, '•••• 456', 'حسابدار');
  assert.equal((await card('manager', a)).phoneMasked, today, 'مدیر همان کارتِ امروز');
  for (const who of ['cashier', 'cashier2', 'staff', 'accountant']) {
    const d = await detail(who, a);
    assert.equal(d.customer.phone, undefined, `${who} phone`);
    assert.equal(d.canContact, false, who);
    assert.deepEqual(d.address, {}, `${who} address`);
    assert.ok(!JSON.stringify(d).includes('کوچهٔ دوم') && !JSON.stringify(d).includes('799123'), who);
  }
  const m = await detail('manager', a);
  assert.equal(m.customer.phone.replace(/\D/g, '').slice(-9), '799123456', 'مدیر شمارهٔ کامل');
  assert.equal(m.address.text, 'کارتهٔ چهار، کوچهٔ دوم');

  //  صندوق‌دار سفارش را می‌پذیرد ⇒ همین سفارش برای خودش کامل؛ صندوق‌دارِ دیگر و حسابدار نه؛ سفارشِ دیگر نه
  await move('cashier', a, 'accepted');
  const mine = await detail('cashier', a);
  assert.equal(mine.customer.phone.replace(/\D/g, '').slice(-9), '799123456');
  assert.equal(mine.canContact, true);
  assert.equal(mine.address.text, 'کارتهٔ چهار، کوچهٔ دوم');
  assert.deepEqual((await detail('cashier2', a)).address, {});
  assert.deepEqual((await detail('accountant', a)).address, {});
  assert.deepEqual((await detail('cashier', b)).address, {}, 'سفارشِ دیگر');

  //  سفارشِ بسته‌شده دیگر «در دست» نیست
  await move('cashier', b, 'rejected', 'out_of_stock');
  const closed = await detail('cashier', b);
  assert.equal(closed.customer.phone, undefined);
  assert.deepEqual(closed.address, {});

  await setFlag(roleMask.FLAG, false);
  assert.equal((await card('cashier', c)).phoneMasked, today, 'خاموش ⇒ همان امروز');
});

test('⛔ صندوق‌دار با «در دست داشتن» درهای شمارهٔ کامل را باز نمی‌کند (۴۰۳ همان امروز)', async () => {
  await setFlag(roleMask.FLAG, true);
  const r = await h.get(`/api/shop/orders/customer?phone=${PHONE}`, tok('cashier'));
  assert.equal(r.status, 403, JSON.stringify(r.body));
  await setFlag(roleMask.FLAG, false);
});
