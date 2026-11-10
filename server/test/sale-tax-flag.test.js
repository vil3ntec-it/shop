'use strict';
/**
 * POS-37 — مالیاتِ فاکتور، سمتِ سرور (پرچمِ پیش‌فرض خاموشِ `shop.sale_tax`).
 *
 * سنجه‌ها: (۱) `GET /api/shop/stock-ledger/config` کلیدِ `saleTax` را فقط با پرچمِ روشنِ همان دکان دارد (خاموش ⇒ پاسخ
 * بیت‌به‌بیت همان؛ دکانِ دیگر نمی‌بیند). (۲) با سقفِ قیمت (`shop.price_cap`) و پرچمِ مالیات، مالیاتِ ادعاشدهٔ فاکتور جزوِ
 * نهاییِ «باید» است: مالیاتی که در نهایی نیامد همان تخفیفِ پنهان است (شاگردِ بی تخفیف ⇒ `discount_not_allowed`)؛ مالیاتِ
 * آمده در نهایی آزاد. پرچمِ مالیات خاموش ⇒ ادعای مالیات هیچ اثری ندارد (همان امروز). v3 و راهِ قدیمِ REST.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let shopId;
let shopB;
let owner;
let staff;
let ownerB;
let n = 0;
const oid = () => `01JTX${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
const config = (who) => { resetAll(); return h.get('/api/shop/stock-ledger/config', { token: who.accessToken }); };

/** فاکتورِ v3 ِ یک‌قلمی (۱ × ۱۰۰۰ افغانی) با سرِ دلخواه */
function sale3(id, head = {}) {
  const tx = `tx.${oid()}`;
  const fields = { totalMinor: 100000, discountMinor: 0, finalTotalMinor: 100000, paidAmountMinor: 100000, remainingMinor: 0,
    debtGivenMinor: 0, paymentMethod: 'cash', status: 'completed', date: '2026-11-08', ...head };
  return [
    { op_id: oid(), hlc: H('pos'), table: 'sales', row_id: id, kind: 'insert', tx, fields },
    { op_id: oid(), hlc: H('pos'), table: 'saleItems', row_id: `${id}-i0`, kind: 'insert', tx,
      fields: { saleId: id, productId: 'tx-rice', unitPriceMinor: 100000, quantityMilli: 1000, totalPriceMinor: 100000 } },
  ];
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'tx-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'tx-admin', password: 'Admin!12345' })).body.token;
  async function shopWithStaff(label) {
    const o = await h.newUser(`صاحبِ ${label}`, 'shop');
    const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
    const id = shop.body?.shop?.id || shop.body?.id;
    const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
    const s = await h.newUser(`شاگردِ ${label}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    return { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop') };
  }
  ({ id: shopId, owner, staff } = await shopWithStaff('مالیات'));
  ({ id: shopB, owner: ownerB } = await shopWithStaff('مالیاتِ ب'));
  const r = await push3(owner, 'own', [{ op_id: oid(), hlc: H('own'), table: 'products', row_id: 'tx-rice', kind: 'insert',
    fields: { name: 'برنج', salePriceMinor: 100000 } }]);
  assert.deepEqual(why(r), ['applied'], JSON.stringify(r.body));
  const rest = await h.post('/api/products', { id: 'tx-rice', name: 'برنج', salePrice: 1000, wholesalePrice: 0 }, { token: owner.accessToken });
  assert.ok([200, 201].includes(rest.status), JSON.stringify(rest.body));
  await liveFlag('shop.price_cap', true, shopId);
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): کلیدِ `saleTax` در پیکربندیِ صندوق نیست و ادعای مالیات اثری ندارد', async () => {
  const c = await config(staff);
  assert.equal(c.status, 200, JSON.stringify(c.body));
  assert.ok(!('saleTax' in c.body), JSON.stringify(c.body));
  //  مالیاتِ ادعاشده که در نهایی نیامد: بی پرچم همان امروز (هیچ تخفیفی نیست)
  assert.deepEqual(why(await push3(staff, 'st', sale3('tx-off-1', { taxMinor: 10000 }))), ['applied', 'applied']);
  //  مالیاتِ آمده در نهایی هم آزاد (نهاییِ بیشتر هرگز تخفیف نیست)
  assert.deepEqual(why(await push3(staff, 'st', sale3('tx-off-2', { taxMinor: 10000, finalTotalMinor: 110000, paidAmountMinor: 110000 }))), ['applied', 'applied']);
});

test('پرچمِ روشن: `saleTax: true` فقط برای همان دکان', async () => {
  await liveFlag('shop.sale_tax', true, shopId);
  for (const who of [owner, staff]) {
    const c = await config(who);
    assert.equal(c.status, 200);
    assert.equal(c.body.saleTax, true);
  }
  const b = await config(ownerB);
  assert.equal(b.status, 200);
  assert.ok(!('saleTax' in b.body), 'دکانِ دیگر پرچم را نمی‌بیند');
});

test('پرچمِ روشن، v3: مالیاتی که در نهایی نیامد همان تخفیفِ پنهان است؛ مالیاتِ آمده آزاد؛ صاحب هرگز', async () => {
  await liveFlag('shop.sale_tax', true, shopId);
  assert.deepEqual(why(await push3(staff, 'st', sale3('tx-on-ok', { taxMinor: 10000, finalTotalMinor: 110000, paidAmountMinor: 110000 }))),
    ['applied', 'applied']);
  assert.deepEqual(why(await push3(staff, 'st', sale3('tx-on-gap', { taxMinor: 10000 }))), ['discount_not_allowed', 'tx_rejected']);
  //  مالیاتِ منفی صفر شمرده می‌شود (همان امروز)
  assert.deepEqual(why(await push3(staff, 'st', sale3('tx-on-neg', { taxMinor: -5000 }))), ['applied', 'applied']);
  assert.deepEqual(why(await push3(owner, 'own', sale3('tx-on-own', { taxMinor: 10000 }))), ['applied', 'applied']);
});

test('پرچمِ روشن، راهِ قدیمِ REST: همان قاعده (۴۰۳ ِ همه یا هیچ)', async () => {
  await liveFlag('shop.sale_tax', true, shopId);
  const send = (id, sale) => { resetAll(); return h.post('/api/sales/full', {
    sale: { id, total: 1000, discount: 0, finalTotal: 1000, status: 'completed', date: '2026-11-08', ...sale },
    items: [{ id: `${id}-i`, productId: 'tx-rice', quantity: 1, unitPrice: 1000, totalPrice: 1000 }],
  }, { token: staff.accessToken }); };
  const gap = await send('tx-rest-gap', { taxMinor: 10000 });
  assert.equal(gap.status, 403, JSON.stringify(gap.body));
  assert.equal(gap.body.error.code, 'discount_not_allowed');
  const ok = await send('tx-rest-ok', { taxMinor: 10000, finalTotal: 1100 });
  assert.ok([200, 201].includes(ok.status), JSON.stringify(ok.body));
  await liveFlag('shop.sale_tax', false, shopId);
  const off = await send('tx-rest-off', { taxMinor: 10000 });
  assert.ok([200, 201].includes(off.status), JSON.stringify(off.body));
});
