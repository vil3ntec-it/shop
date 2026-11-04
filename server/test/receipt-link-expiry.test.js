'use strict';
/**
 * TEN-03 / O-08 (گرفته‌شده ۱۴۰۵/۰۸/۱۳، OWNER-DECISIONS: ب، یک سال) — لینکِ QR ِ فاکتورِ کاغذی (`/api/receipt/:tag/:token`).
 *
 * با پرچمِ `shop.storefront.link_expiry` ِ همان دکان لینک یک سال (`STORE_PAPER_LINK_DAYS`) پس از رسیدنِ فروش به سرور
 * (`sync_rows.created_at`، ساعتِ سرور، نه ساعتِ برنامه) کهنه می‌شود: ۴۱۰ `link_expired` و همان سرآیندهای noindex.
 * پرچمِ خاموش ⇒ همان رفتارِ امروز، بی انقضا (آزمونِ مرجع). خودِ فاکتور در دفترِ دکان می‌ماند. حالت‌های بسته
 * (پاک‌شده، QR ِ خاموش، نشانِ دیگر) همان ۴۰۴ ِ یکسان می‌مانند، حتی کهنه.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_RECEIPT_READ_MAX = '100000';
process.env.RATE_RECEIPT_READ_BURST_MAX = '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { query, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');
const R = require('../src/lib/receipt-token');

const DAY = 24 * 60 * 60 * 1000;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

let n = 0;
const opId = () => `01JRX${String(Date.now()).slice(-8)}${String(++n).padStart(8, '0')}`;
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const newToken = () => R.encodeToken(crypto.randomBytes(16));

async function shop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  const shopId = made.body.shop.id;
  return { token: s.accessToken, shopId, tag: R.shopTag(shopId), device: `dev-${shopId}`.slice(0, 60) };
}

async function sell(s, saleId, token) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
    body: { device_id: s.device, schema_version: 2, ops: [
      op('insert', 'sales', saleId, {
        invoiceNumber: 7, series: 'B1', total: 100, discount: 0, finalTotal: 100, paymentMethod: 'cash', paidAmount: 100,
        remaining: 0, status: 'completed', createdAt: Date.now(), date: '2026-10-24', receiptToken: token,
      }),
      op('insert', 'saleItems', `${saleId}-i1`, { saleId, productId: '', name: 'قند', quantity: 1, unitPrice: 100, totalPrice: 100, returnedQty: 0 }),
    ] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}

/** رسیدنِ فروش به سرور `days` روز پیش */
const arrived = (s, saleId, days) => query(
  `UPDATE sync_rows SET created_at=$3 WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='sales' AND row_id=$2`,
  [s.shopId, saleId, now() - days * DAY]
);
const flag = (scope, value) => query(
  "INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.storefront.link_expiry',$2,1) "
  + 'ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value',
  [String(scope), value],
);
const read = (s, token) => h.get(`/api/receipt/${s.tag}/${token}`);

test('پرچمِ خاموش (پیش‌فرض): فاکتورِ دوساله هنوز باز است — رفتارِ امروز', async () => {
  const a = await shop('بی‌انقضا');
  const tok = newToken();
  await sell(a, 'old-a1', tok);
  await arrived(a, 'old-a1', 730);
  const r = await read(a, tok);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.receipt.no, 'B1-7');
});

test('پرچمِ روشن: زیرِ یک سال باز؛ بیش از یک سال ۴۱۰ link_expired با noindex؛ پرچمِ دکانِ دیگر اثری ندارد', async () => {
  const a = await shop('انقضا');
  const b = await shop('دکانِ دیگر');
  const fresh = newToken();
  const old = newToken();
  await sell(a, 'exp-1', fresh);
  await sell(a, 'exp-2', old);
  await arrived(a, 'exp-1', 364);
  await arrived(a, 'exp-2', 366);

  await flag(b.shopId, true);
  assert.equal((await read(a, old)).status, 200, 'پرچمِ دکانِ دیگر نباید این دکان را عوض کند');

  await flag(a.shopId, true);
  try {
    assert.equal((await read(a, fresh)).status, 200, '۳۶۴ روز ⇒ هنوز باز');
    const r = await read(a, old);
    assert.equal(r.status, 410, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'link_expired');
    assert.match(r.body.error.message, /دفترِ دکان/);
    assert.equal(r.headers.get('x-robots-tag'), 'noindex, nofollow, noarchive');
    assert.equal(r.headers.get('cache-control'), 'no-store');
    //  ناشناخته همان ۴۰۴ ِ یکسان
    assert.equal((await read(a, newToken())).status, 404);
  } finally {
    await flag(a.shopId, false);
  }
  assert.equal((await read(a, old)).status, 200, 'خاموش کردنِ پرچم همان را برمی‌گرداند');
});

test('پرچمِ روشن: پاک‌شده و QR ِ خاموش همان ۴۰۴ می‌مانند، حتی کهنه', async () => {
  const a = await shop('بسته‌ها');
  const gone = newToken();
  await sell(a, 'gone-1', gone);
  await arrived(a, 'gone-1', 400);
  await query(`UPDATE sync_rows SET deleted_at=$2 WHERE account_id=$1 AND table_name='sales' AND row_id='gone-1'`, [a.shopId, now()]);
  const off = newToken();
  await sell(a, 'off-1', off);
  await arrived(a, 'off-1', 400);
  await flag(a.shopId, true);
  try {
    assert.equal((await read(a, gone)).status, 404);
    const qr = await h.put('/api/shop/receipt-profile', { qr: false }, { token: a.token });
    assert.equal(qr.status, 200, JSON.stringify(qr.body));
    assert.equal((await read(a, off)).status, 404);
  } finally {
    await flag(a.shopId, false);
  }
});
