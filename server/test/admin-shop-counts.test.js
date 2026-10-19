'use strict';
/**
 * پنلِ مدیر، صفحهٔ دکان، «اطلاعاتِ ثبت‌شده» — شمارِ کالا، فروش، اقلام و … باید دفترِ Sync v1 را
 * هم ببیند.
 *
 * سناریوی سرتاسریِ `test/e2e/admin.e2e.mjs` روی main نشان داد: دکانی که برنامه‌هایش (اندروید و
 * کامپیوتر، همه روی Sync v1) کالا و فروش فرستاده‌اند، در پنل «محصولات: ۰، فروش‌ها: ۰» دارد؛
 * چون `GET /api/admin/shops/:id` فقط جدول‌های راهِ قدیم (`lib/sync.js → TABLES`) را می‌شمرد.
 * حالا شمار = ردیف‌های زندهٔ یکتا از هر دو دفتر (راهِ قدیم ∪ `sync_rows`)، با همان کلیدها.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

let admin = '';
test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

let n = 0;
const op = (type, table, rowId, fields = {}) => ({ op_id: `01JAC${String(Date.now()).slice(-8)}${String(++n).padStart(8, '0')}`, ts: Date.now(), table, row_id: rowId, type, fields });

test('دکانِ برنامه‌ای (Sync v1): شمارِ پنل همان ردیف‌های زندهٔ دفتر است؛ حذف‌شده شمرده نمی‌شود', async () => {
  const u = await h.newUser('صاحبِ شمار', 'shop');
  const shop = (await h.post('/api/shop', { name: 'دکانِ شمار' }, { token: u.accessToken })).body.shop;
  const s = await h.signIn(u, 'shop');
  const push = (ops) => h.api('POST', '/api/sync/v1/push', {
    token: s.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-count' }, body: { device_id: 'dev-count', schema_version: 3, ops },
  });
  const r = await push([
    op('insert', 'products', 'p1', { name: 'چای', salePrice: 50 }),
    op('insert', 'products', 'p2', { name: 'قند', salePrice: 40 }),
    op('insert', 'products', 'p3', { name: 'پاک‌شدنی', salePrice: 1 }),
    op('insert', 'sales', 's1', { status: 'completed', finalTotal: 50 }),
    op('insert', 'saleItems', 'i1', { saleId: 's1', productId: 'p1', quantity: 1, returnedQty: 0 }),
    op('insert', 'debtors', 'd1', { name: 'احمد' }),
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await push([op('delete', 'products', 'p3')])).status, 200);
  const d = await h.get(`/api/admin/shops/${shop.id}`, { token: admin });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  assert.equal(d.body.counts.products, 2, JSON.stringify(d.body.counts));
  assert.equal(d.body.counts.sales, 1);
  assert.equal(d.body.counts.saleItems, 1);
  assert.equal(d.body.counts.debtors, 1);
  assert.equal(d.body.counts.expenses, 0);
});

test('راهِ قدیم هنوز شمرده می‌شود و ردیفِ مشترکِ دو دفتر دو بار شمرده نمی‌شود', async () => {
  const u = await h.newUser('صاحبِ قدیمی', 'shop');
  const shop = (await h.post('/api/shop', { name: 'دکانِ قدیمی' }, { token: u.accessToken })).body.shop;
  const s = await h.signIn(u, 'shop');
  const legacy = await h.post('/api/products', { id: 'old-1', data: { name: 'کالای قدیمی', price: 10 } }, { token: s.accessToken });
  assert.ok([200, 201].includes(legacy.status), JSON.stringify(legacy.body));
  //  همان شناسه از راهِ v1 هم آمده (کوچ) + یک ردیفِ فقط v1
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-old' },
    body: { device_id: 'dev-old', schema_version: 3, ops: [op('insert', 'products', 'old-1', { name: 'کالای قدیمی' }), op('insert', 'products', 'new-1', { name: 'تازه' })] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const d = await h.get(`/api/admin/shops/${shop.id}`, { token: admin });
  assert.equal(d.body.counts.products, 2, JSON.stringify(d.body.counts));
});
