'use strict';
/**
 * CRM-15 (د۱۸) — دیدنِ پروندهٔ مشتری به دستِ نقشی که `auditLog` نمی‌نویسد (حسابدار) وقتی **بی‌اینترنت** است: برنامه آن را
 * در صفِ خودش نگه می‌دارد (`core/crm/CustomerAccessOutbox`) و پس از وصل یک‌جا می‌فرستد:
 *   POST /api/shop/customer-access/offline   { notes: [{ id, customerId, kind, at }] } (≤ ۵۰)
 *     ⇒ { results: [{ id, status: recorded | duplicate | rejected, reason? }] }
 *
 * ⛔ پشتِ پرچمِ خاموشِ `shop.customer_access_offline` (خاموش ⇒ ۴۰۴ و برنامه صف را دور می‌ریزد — همان امروز: دیدنِ بی‌اینترنت
 *    ثبت نمی‌شد). ⛔ زمانِ دیدن (`at`) فقط در پنجرهٔ ۳۰ روزِ گذشته تا ۵ دقیقهٔ آینده؛ همان دیدن دوباره (پاسخِ گم‌شده) ⇒
 *    `duplicate` و شمار بالا نمی‌رود. بیننده، نقش، دکان و دستگاه فقط از توکن؛ `phone` ِ اصلِ بی `customers.contact` ⇐ `detail`.
 * ⛔ ۴۰۱ بی توکن، ۴۰۰ بدنهٔ بدشکل یا بیش از ۵۰، ۴۲۹ سطلِ هر کاربر (همان سطلِ درِ `app`)؛ جداسازیِ دکان.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');
const access = require('../src/lib/customer-access');
const live = require('../src/lib/live-config');
const { resetAll } = require('../src/middleware/ratelimit');

const FLAG = 'shop.customer_access_offline';
const URL = '/api/shop/customer-access/offline';
const T = {};
const B = {};
const MIN = 60_000;

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { token: u.accessToken, userId: u.user.id };
}
async function shop(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: o.accessToken, userId: o.user.id, shopId: made.body.shop.id };
}
const send = (who, notes) => h.post(URL, { notes }, { token: who.token });
const rows = (shopId) => many("SELECT * FROM customer_access_log WHERE shop_id=$1 AND door='app' ORDER BY first_at, id", [shopId]);
let n = 0;
const nid = () => `n${Date.now().toString(36)}${++n}`;

test.before(async () => {
  await h.start();
  Object.assign(T, { owner: await shop('الف') });
  for (const role of ['accountant', 'storekeeper']) T[role] = await member(T.owner.shopId, role, `کارمندِ ${role}`);
  Object.assign(B, { owner: await shop('ب') });
  const d = await h.seed(T.owner.token, 'debtors', { id: 'd-off', data: { name: 'مشتریِ دیده‌شده', phone: '0799555124' } });
  assert.equal(d.status, 200, JSON.stringify(d.body));
});
test.after(async () => { await access.settle(); await h.stop(); });
test.beforeEach(() => resetAll());

test('پرچمِ خاموش (پیش‌فرض) ⇒ ۴۰۴ و هیچ ردیف — همان امروز', async () => {
  const r = await send(T.accountant, [{ id: nid(), customerId: 'd-off', kind: 'detail', at: now() - 5 * MIN }]);
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'customer_access_offline_off');
  assert.equal((await rows(T.owner.shopId)).length, 0);
});

test('روشن: هر دیدن با زمانِ خودش؛ بیننده/نقش/دکان از توکن؛ تکرارِ همان دیدن duplicate؛ زمانِ بیرون از پنجره و شناسهٔ بد rejected', async () => {
  await live.set(T.owner.shopId, FLAG, true);
  const t0 = now();
  const a = { id: nid(), customerId: 'd-off', kind: 'detail', at: t0 - 3 * 24 * 60 * MIN };
  const b = { id: nid(), customerId: '*', kind: 'export', at: t0 - 2 * MIN };
  const r = await send(T.accountant, [
    a, b,
    { id: nid(), customerId: 'd-off', kind: 'detail', at: t0 - 31 * 24 * 60 * MIN },
    { id: nid(), customerId: 'd-off', kind: 'detail', at: t0 + 10 * MIN },
    { id: nid(), customerId: 'erased:x', kind: 'detail', at: t0 },
    { id: nid(), customerId: '*', kind: 'detail', at: t0 },
    { id: nid(), customerId: 'd-off', kind: 'weird', at: t0 },
    { id: nid(), customerId: 'd-off', kind: 'detail', at: '123' },
    { id: nid(), customerId: 'd-off', kind: 'detail', at: 1.5 },
    { id: nid(), customerId: 'd-off', kind: 'detail', at: t0, shopId: B.owner.shopId, viewerId: 'usr_x', role: 'owner' },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.status, x.reason || '']), [
    ['recorded', ''], ['recorded', ''], ['rejected', 'bad_at'], ['rejected', 'bad_at'], ['rejected', 'bad_customer'],
    ['rejected', 'bad_customer'], ['rejected', 'bad_kind'], ['rejected', 'bad_at'], ['rejected', 'bad_at'], ['recorded', ''],
  ]);
  const got = await rows(T.owner.shopId);
  assert.equal(got.length, 3);
  assert.ok(got.every((x) => x.viewer_id === T.accountant.userId && x.role === 'accountant' && x.door === 'app'));
  assert.equal(Number(got[0].first_at), a.at, 'زمانِ خودِ دیدن، نه زمانِ رسیدن');
  assert.equal((await rows(B.owner.shopId)).length, 0, 'دکانِ بدنه بی‌اثر');
  //  همان دیدن‌ها دوباره (پاسخِ گم‌شده) ⇒ duplicate و شمار همان
  const again = await send(T.accountant, [a, b]);
  assert.deepEqual(again.body.results.map((x) => x.status), ['duplicate', 'duplicate']);
  assert.deepEqual((await rows(T.owner.shopId)).map((x) => Number(x.hits)), got.map((x) => Number(x.hits)));
  //  دیدنِ تازه در ۱۰ دقیقهٔ همان دیدن ⇒ همان ردیف با شمارِ بیشتر
  const c = await send(T.accountant, [{ id: nid(), customerId: 'd-off', kind: 'detail', at: a.at + 2 * MIN }]);
  assert.deepEqual(c.body.results.map((x) => x.status), ['recorded']);
  assert.equal(Number((await rows(T.owner.shopId))[0].hits), 2);
});

test('«phone» ِ اصلِ بی customers.contact ⇐ «detail»', async () => {
  await live.set(T.owner.shopId, FLAG, true);
  const r = await send(T.storekeeper, [{ id: nid(), customerId: 'd-off', kind: 'phone', at: now() - MIN }]);
  assert.equal(r.body.results[0].status, 'recorded');
  const mine = (await rows(T.owner.shopId)).filter((x) => x.viewer_id === T.storekeeper.userId);
  assert.deepEqual(mine.map((x) => x.kind), ['detail']);
});

test('۴۰۱ بی توکن؛ ۴۰۰ بدنهٔ بدشکل یا بیش از ۵۰؛ ۴۲۹ سطلِ هر کاربر', async () => {
  await live.set(T.owner.shopId, FLAG, true);
  assert.equal((await h.post(URL, { notes: [] })).status, 401);
  for (const body of [{}, { notes: 'x' }, { notes: {} }, { notes: Array.from({ length: 51 }, () => ({ id: nid(), customerId: 'd-off', kind: 'detail', at: now() })) }]) {
    const r = await h.post(URL, body, { token: T.accountant.token });
    assert.equal(r.status, 400, JSON.stringify(body).slice(0, 80));
  }
  let last = 0;
  for (let i = 0; i < 125 && last !== 429; i++) last = (await send(T.accountant, [])).status;
  assert.equal(last, 429);
});
