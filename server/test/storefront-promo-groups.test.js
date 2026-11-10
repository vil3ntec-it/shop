'use strict';
/**
 * PRM-11 — گروهِ مشتری در تخفیفِ ویترین (پرچمِ خاموشِ `shop.storefront.promo_groups`، همراهِ `shop.storefront.promos`;
 * `lib/storefront-promo-groups.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموش ⇒ قاعدهٔ گروه‌دار در ویترین هرگز اعمال نمی‌شود (همان پیشین)، حتی برای مشتریِ همان گروه.
 *   ۲) روشن ⇒ گروه فقط از قرض‌دارِ دفترِ همین دکان با **همان** شمارهٔ تأییدشده (بلیت/نشست)، با همان `compareKey` ِ
 *      صندوق («ي/ی» و فاصله)؛ پیش‌نمایش = ثبت.
 *   ۳) ⛔ گروه هرگز از بدنه نمی‌آید؛ بی شمارهٔ تأییدشده یا شمارهٔ دیگر ⇒ بی گروه.
 *   ۴) دو قرض‌دارِ همان شماره با دو گروهِ ناهم‌سان ⇒ بی گروه (شک ⇒ بی تخفیف)؛ قرض‌دارِ ادغام‌شده/پاک‌شده نامزد نیست.
 *   ۵) قاعدهٔ گروهِ «فقط صندوق» در ویترین نه؛ فهرستِ عمومی هیچ قاعدهٔ گروه‌داری را نشان نمی‌دهد؛ دکانِ دیگر نه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const FLAG = 'shop.storefront.promo_groups';
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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (s, ops) => h.api('POST', '/api/sync/v1/push', {
  token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-grp' },
  body: { device_id: 'dev-grp', schema_version: 2, ops },
});
const op = (table, rowId, fields, type = 'insert') => ({ op_id: `op-${uniq()}`, ts: Date.now(), table, row_id: rowId, type, fields });

async function liveShop(label, { groups = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `g-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.promos']) await setFlag(s.shopId, k, true);
  if (groups) await setFlag(s.shopId, FLAG, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const r = await push(s, [
    op('products', 'rice', { name: 'برنج', unit: 'بسته', salePrice: 120, category: 'خوراکی', purchasePrice: 100 }),
    op('products', 'soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const id of ['rice', 'soap']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

async function rule(s, id, data) {
  const r = await h.put(`/api/shop/promos/rules/${id}`, { rule: { name: id, stack: 'best', active: true, ...data } }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.rule;
}

async function ticketFor(slug, phone) {
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

const LINES = [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'soap', qtyMilli: 1000 }];   //  ۲۴۰ + ۴۰ = ۲۸۰ افغانی
const VIP = '0791112233';
const quote = (s, body = {}, ticket = '') => h.post(`/api/store/${s.slug}/promos/quote`, { lines: LINES, ...body },
  ticket ? { headers: { 'X-Store-Ticket': ticket } } : {});
async function checkout(s, phone, ticket, extra = {}) {
  return h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone }, address: { landmark: 'پشتِ مسجد' }, lines: LINES, ...extra,
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });
}
const discountOf = async (s, r) => Number((await one('SELECT discount_minor FROM online_orders WHERE shop_id=$1 AND order_no=$2',
  [s.shopId, r.body.order.orderNo])).discount_minor);

/* ══════════════════════════════════════════════════════════════════ */

test('PRM-11 خاموش: قاعدهٔ گروهِ «عمده» در ویترین اعمال نمی‌شود — حتی برای مشتریِ همان گروه با بلیت', async () => {
  const s = await liveShop('گروه-خاموش', { groups: false });
  await rule(s, 'bulk10', { kind: 'percent', bp: 1000, channels: ['online'], groups: ['عمده'] });
  assert.equal((await push(s, [op('debtors', 'd-vip', { name: 'مشتریِ عمده', phone: VIP, group: 'عمده' })])).status, 200);
  const tk = await ticketFor(s.slug, VIP);
  const q = await quote(s, {}, tk);
  assert.equal(q.status, 200, JSON.stringify(q.body));
  assert.deepEqual([q.body.discountMinor, q.body.applied], [0, []]);
  const r = await checkout(s, VIP, tk);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(await discountOf(s, r), 0, 'همان پیشین');
});

test('PRM-11: گروه از قرض‌دارِ همان شمارهٔ تأییدشده (با «ي/ی» و فاصله) — پیش‌نمایش = ثبت؛ فهرستِ عمومی بی قاعدهٔ گروه', async () => {
  const s = await liveShop('گروه-روشن');
  await rule(s, 'bulk10', { kind: 'percent', bp: 1000, channels: ['online'], groups: ['مشتری عمده'] });
  await rule(s, 'bulkpos', { kind: 'percent', bp: 5000, channels: ['pos'], groups: ['مشتری عمده'] });
  //  املای دفترِ دستگاه با «ي» ِ عربی و نیم‌فاصله؛ قاعده با «ی» و فاصله — همان `compareKey` ِ صندوق
  assert.equal((await push(s, [op('debtors', 'd-vip', { name: 'مشتریِ عمده', phone: '۰۷۹۱ ۱۱۲ ۲۳۳', group: ' مشتري‌عمده ' })])).status, 200);
  const tk = await ticketFor(s.slug, VIP);
  const q = await quote(s, {}, tk);
  assert.equal(q.status, 200, JSON.stringify(q.body));
  assert.deepEqual([q.body.subtotalMinor, q.body.discountMinor, q.body.totalMinor], [28000, 2800, 25200]);
  assert.deepEqual(q.body.applied, [{ name: 'bulk10', discountMinor: 2800 }], 'قاعدهٔ «فقط صندوق» نه');

  const fee = Number((await one('SELECT delivery_fee_minor FROM storefronts WHERE shop_id=$1', [s.shopId])).delivery_fee_minor);
  const r = await checkout(s, VIP, tk, { expectedTotalMinor: 25200 + fee });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(await discountOf(s, r), 2800, 'ثبت همان پیش‌نمایش');
  assert.equal(r.body.order.discountMinor, 2800);

  const list = await h.get(`/api/store/${s.slug}/promos`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.deepEqual(list.body.promos, [], 'قاعدهٔ گروه‌دار عمومی نیست');
  assert.ok(!JSON.stringify(list.body).includes('عمده'), 'نامِ گروه بیرون نمی‌رود');
});

test('PRM-11: ⛔ گروه از بدنه نه؛ بی بلیت یا شمارهٔ دیگر ⇒ بی گروه؛ دکانِ دیگر قرض‌دارِ این دکان را نمی‌بیند', async () => {
  const s = await liveShop('گروه-سد');
  await rule(s, 'bulk10', { kind: 'percent', bp: 1000, channels: ['online'], groups: ['عمده'] });
  assert.equal((await push(s, [op('debtors', 'd-vip', { name: 'مشتریِ عمده', phone: VIP, group: 'عمده' })])).status, 200);
  //  بی بلیت، با گروه و شمارهٔ ساختگی در بدنه
  let q = await quote(s, { group: 'عمده', customer: { phone: VIP }, phone: VIP });
  assert.equal(q.status, 200, JSON.stringify(q.body));
  assert.equal(q.body.discountMinor, 0, 'بی شمارهٔ تأییدشده');
  //  شمارهٔ دیگر با بلیتِ خودش و گروه در بدنه
  const other = '0793334455';
  const tk = await ticketFor(s.slug, other);
  q = await quote(s, { group: 'عمده' }, tk);
  assert.equal(q.body.discountMinor, 0, 'شمارهٔ دیگر');
  const r = await checkout(s, other, tk, { group: 'عمده' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(await discountOf(s, r), 0);
  //  ۹ رقمِ آخرِ یکسان با پیش‌شمارهٔ کشورِ دیگر ⇒ همان شماره نیست
  assert.equal((await push(s, [op('debtors', 'd-near', { name: 'x', phone: '+1 793334455', group: 'عمده' })])).status, 200);
  q = await quote(s, {}, tk);
  assert.equal(q.body.discountMinor, 0, 'تطبیقِ دقیقِ شماره، نه چند رقمِ آخر');

  //  دکانِ دیگر با همان قاعده و همان شماره ولی بی قرض‌دار
  const t = await liveShop('گروه-دکانِ-دیگر');
  await rule(t, 'bulk10', { kind: 'percent', bp: 1000, channels: ['online'], groups: ['عمده'] });
  q = await quote(t, {}, await ticketFor(t.slug, VIP));
  assert.equal(q.body.discountMinor, 0, 'قرض‌دارِ دکانِ دیگر');
});

test('PRM-11: دو گروهِ ناهم‌سان برای یک شماره ⇒ بی گروه؛ ادغام‌شده و پاک‌شده نامزد نیستند', async () => {
  const s = await liveShop('گروه-شک');
  await rule(s, 'bulk10', { kind: 'percent', bp: 1000, channels: ['online'], groups: ['عمده'] });
  await rule(s, 'vip20', { kind: 'percent', bp: 2000, channels: ['online'], groups: ['ویژه'] });
  const tk = await ticketFor(s.slug, VIP);
  assert.equal((await push(s, [
    op('debtors', 'd-a', { name: 'الف', phone: VIP, group: 'عمده' }),
    op('debtors', 'd-b', { name: 'ب', phone: VIP, group: 'ویژه' }),
  ])).status, 200);
  let q = await quote(s, {}, tk);
  assert.equal(q.status, 200, JSON.stringify(q.body));
  assert.deepEqual([q.body.discountMinor, q.body.applied], [0, []], 'شک ⇒ بی تخفیف');

  //  «ب» در «الف» ادغام شد ⇒ فقط «عمده»
  assert.equal((await push(s, [op('debtors', 'd-b', { name: 'ب', phone: VIP, group: 'ویژه', mergedInto: 'd-a' }, 'update')])).status, 200);
  q = await quote(s, {}, tk);
  assert.deepEqual(q.body.applied, [{ name: 'bulk10', discountMinor: 2800 }]);

  //  قرض‌دارِ بی گروه کنارِ گروه‌دار شک نمی‌سازد؛ «الف» پاک شد ⇒ فقط بی‌گروه ⇒ بی تخفیف
  assert.equal((await push(s, [op('debtors', 'd-c', { name: 'ج', phone: VIP })])).status, 200);
  q = await quote(s, {}, tk);
  assert.equal(q.body.discountMinor, 2800, 'قرض‌دارِ بی گروه شک نیست');
  assert.equal((await push(s, [op('debtors', 'd-a', {}, 'delete')])).status, 200);
  q = await quote(s, {}, tk);
  assert.deepEqual([q.body.discountMinor, q.body.applied], [0, []], 'پاک‌شده نامزد نیست');
});

test('PRM-11 + CHK-21: افزودنِ قلم به سفارش همان گروهِ ثبت را می‌گیرد؛ پرچمِ خاموش ⇒ همان پیشین', async () => {
  const s = await liveShop('گروه-افزودن');
  await setFlag(s.shopId, 'shop.orders.add_items', true);
  await rule(s, 'bulk10', { kind: 'percent', bp: 1000, channels: ['online'], groups: ['عمده'] });
  assert.equal((await push(s, [op('debtors', 'd-vip', { name: 'مشتریِ عمده', phone: VIP, group: 'عمده' })])).status, 200);
  const tk = await ticketFor(s.slug, VIP);
  const add = (token) => h.post(`/api/store/${s.slug}/orders/${token}/items`, { lines: [{ productId: 'soap', qtyMilli: 1000 }] },
    { headers: { 'Idempotency-Key': `ad-${uniq()}-key`, 'X-Store-Ticket': tk } });
  const row = async (r) => one('SELECT discount_minor, subtotal_minor FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo]);

  const r = await checkout(s, VIP, tk);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(Number((await row(r)).discount_minor), 2800);
  const a = await add(r.body.order.token);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.deepEqual([Number((await row(r)).subtotal_minor), Number((await row(r)).discount_minor)], [32000, 3200], '۱۰٪ ِ صابونِ افزوده هم');

  await setFlag(s.shopId, FLAG, false);
  const r2 = await checkout(s, VIP, tk);
  assert.equal(r2.status, 201, JSON.stringify(r2.body));
  const a2 = await add(r2.body.order.token);
  assert.equal(a2.status, 201, JSON.stringify(a2.body));
  assert.deepEqual([Number((await row(r2)).subtotal_minor), Number((await row(r2)).discount_minor)], [32000, 0], 'خاموش ⇒ بی گروه');
});
