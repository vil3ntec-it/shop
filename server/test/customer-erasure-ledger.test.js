'use strict';
/**
 * CRM-15/SEC-10 (د۱۸) — فرمانِ ناشناس کردنِ پرونده در دفترِ دستگاه پس از حذفِ دادهٔ مشتری (`lib/customer-erasure-ledger.js`،
 * مهاجرتِ ۸۹۵): حذف (با پرچمِ `shop.customer_erasure_ledger`) پرونده‌های همان شماره را صف می‌کند؛ برنامهٔ صاحب/مدیر
 * `GET /api/shop/customer-erasure/ledger` را می‌خواند، دفترِ خودش را پاک می‌کند (op ِ عادیِ v1)، و `POST …/ledger/applied`
 * فقط وقتی «انجام شد» می‌پذیرد که دفترِ سرور دیگر شماره/نشانی/یادداشت ندارد.
 * ⛔ خاموش ⇒ هیچ صف و ۴۰۴؛ ۴۰۱ بی توکن، ۴۰۳ شاگرد، ۴۰۰ بدنهٔ بدشکل، ۴۲۹ سطلِ دکان؛ دکانِ دیگر هیچ.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, many, one, newId, now } = require('../src/db');
const live = require('../src/lib/live-config');
const syncV1 = require('../src/lib/sync-v1');
const erasure = require('../src/lib/customer-erasure');
const L = require('../src/lib/customer-erasure-ledger');
const { resetAll } = require('../src/middleware/ratelimit');

const PHONE = '0799777123';
const T = {};
let seq = 0;
const oid = () => `01JCEL${String(++seq).padStart(20, '0')}`;
const push1 = (who, ops) => h.api('POST', '/api/sync/v1/push', { token: who.token, body: { device_id: `cel-${who.userId}`, schema_version: 1, caps: ['a00'], ops } });
const v1 = (table, rowId, fields, type = 'insert') => ({ op_id: oid(), table, row_id: rowId, type, ts: Date.now(), fields });
const ledgerRows = (shopId) => many('SELECT * FROM customer_erasure_ledger WHERE shop_id=$1 ORDER BY debtor_id', [shopId]);

async function shop(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const signed = await h.signIn(o, 'shop');
  return { token: signed.accessToken, userId: o.user.id, shopId: made.body.shop.id };
}
async function member(shopId, role) {
  const u = await h.newUser(`کارمندِ ${role}`, 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]);
  return { token: u.accessToken, userId: u.user.id };
}
async function seedDebtors(s) {
  const r = await push1(s, [
    v1('debtors', 'cel-1', { name: 'علی احمدی', phone: PHONE, notes: 'کوچهٔ دوم', deliveryAddress: { line: 'کابل' } }),
    v1('debtors', 'cel-2', { name: 'علیِ دوم', phone: '0700000001', phone2: PHONE }),
    v1('debtors', 'cel-3', { name: 'دیگری', phone: '0700000002' }),
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  T.a = await shop('الف');
  T.b = await shop('ب');
  T.staff = await member(T.a.shopId, 'staff');
  T.manager = await member(T.a.shopId, 'manager');
  await live.set(T.a.shopId, erasure.FLAG || 'shop.customer_erasure', true);
  await seedDebtors(T.a);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('پرچمِ خاموش (پیش‌فرض) ⇒ حذف هیچ فرمانی نمی‌سازد و درها ۴۰۴ — همان امروز', async () => {
  const out = await erasure.erase(T.a.shopId, { phone: PHONE }, { by: 'owner', userId: T.a.userId });
  assert.equal(out.ledgerDebtors, 0);
  assert.equal((await ledgerRows(T.a.shopId)).length, 0);
  const r = await h.get('/api/shop/customer-erasure/ledger', { token: T.a.token });
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'erasure_ledger_off');
});

test('روشن: پرونده‌های همان شماره (اول یا دوم) صف می‌شوند؛ «انجام شد» فقط پس از پاک شدنِ دفترِ سرور', async () => {
  await live.set(T.a.shopId, L.FLAG, true);
  const out = await erasure.erase(T.a.shopId, { phone: PHONE }, { by: 'owner', userId: T.a.userId });
  assert.equal(out.ledgerDebtors, 2);
  const g = await h.get('/api/shop/customer-erasure/ledger', { token: T.manager.token });
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.deepEqual(g.body.debtors.map((d) => d.debtorId), ['cel-1', 'cel-2']);
  assert.ok(g.body.debtors.every((d) => d.erasureId === out.erasureId && d.at > 0));
  assert.ok(!JSON.stringify(g.body).includes(PHONE) && !JSON.stringify(g.body).includes('علی'), 'بی دادهٔ شخصی');
  //  دستگاه هنوز پاک نکرده ⇒ می‌ماند
  let a = await h.post('/api/shop/customer-erasure/ledger/applied', { debtorIds: ['cel-1', 'cel-2', 'cel-3'] }, { token: T.a.token });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.deepEqual(a.body, { applied: [], pending: ['cel-1', 'cel-2'] });
  //  برنامهٔ صاحب پاک می‌کند (همان op ِ `CustomerErasureLedger`) ⇒ پذیرفته؛ پروندهٔ نیمه‌پاک می‌ماند
  const scrub = { name: 'مشتریِ حذف‌شده', phone: '', phone2: null, notes: '', deliveryAddress: null, deliveryPin: null, cardCode: null };
  assert.equal((await push1(T.a, [v1('debtors', 'cel-1', scrub, 'update'), v1('debtors', 'cel-2', { phone: '', notes: '' }, 'update')])).status, 200);
  a = await h.post('/api/shop/customer-erasure/ledger/applied', { debtorIds: ['cel-1', 'cel-2'] }, { token: T.a.token });
  assert.deepEqual(a.body, { applied: ['cel-1'], pending: ['cel-2'] });
  assert.equal((await push1(T.a, [v1('debtors', 'cel-2', scrub, 'update')])).status, 200);
  a = await h.post('/api/shop/customer-erasure/ledger/applied', { debtorIds: ['cel-2'] }, { token: T.a.token });
  assert.deepEqual(a.body, { applied: ['cel-2'], pending: [] });
  assert.deepEqual((await h.get('/api/shop/customer-erasure/ledger', { token: T.a.token })).body.debtors, []);
  assert.ok((await ledgerRows(T.a.shopId)).every((r) => Number(r.applied_at) > 0));
  //  دکانِ دیگر هیچ
  await live.set(T.b.shopId, 'shop.customer_erasure', true);
  await live.set(T.b.shopId, L.FLAG, true);
  assert.deepEqual((await h.get('/api/shop/customer-erasure/ledger', { token: T.b.token })).body.debtors, []);
  assert.deepEqual((await h.post('/api/shop/customer-erasure/ledger/applied', { debtorIds: ['cel-1'] }, { token: T.b.token })).body,
    { applied: [], pending: [] });
});

test('۴۰۱ بی توکن؛ ۴۰۳ شاگرد؛ ۴۰۰ بدنهٔ بدشکل؛ ۴۲۹ سطلِ دکان', async () => {
  await live.set(T.a.shopId, L.FLAG, true);
  assert.equal((await h.get('/api/shop/customer-erasure/ledger')).status, 401);
  assert.equal((await h.get('/api/shop/customer-erasure/ledger', { token: T.staff.token })).status, 403);
  assert.equal((await h.post('/api/shop/customer-erasure/ledger/applied', { debtorIds: ['x'] }, { token: T.staff.token })).status, 403);
  for (const body of [{}, { debtorIds: [] }, { debtorIds: 'x' }, { debtorIds: ['a b'] }, { debtorIds: [5] }, { debtorIds: Array.from({ length: 201 }, (_, i) => `d${i}`) }]) {
    const r = await h.post('/api/shop/customer-erasure/ledger/applied', body, { token: T.a.token });
    assert.equal(r.status, 400, JSON.stringify(body).slice(0, 60));
  }
  let last = 0;
  for (let i = 0; i < 35 && last !== 429; i++) last = (await h.get('/api/shop/customer-erasure/ledger', { token: T.a.token })).status;
  assert.equal(last, 429);
});

test('مهاجرتِ ۸۹۵: rollback جدول را برمی‌دارد و ردیفِ schema_migrations را پاک می‌کند', async () => {
  const sql = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '895_customer_erasure_ledger.down.sql'), 'utf8');
  assert.match(sql, /DELETE FROM schema_migrations WHERE version = '895_customer_erasure_ledger\.sql';/);
  assert.match(sql, /DROP TABLE IF EXISTS customer_erasure_ledger;/);
});
