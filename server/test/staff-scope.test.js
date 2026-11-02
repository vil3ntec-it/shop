'use strict';
/**
 * STF-06 — دسترسیِ کارمند محدود به شعبه یا انبار (`lib/staff-scope.js`، v3 §۶.۹).
 *
 * از راهِ واقعی: کالا و ورودِ انبار را «دستگاه» با `POST /api/sync/v1/push` می‌فرستد (مانده روی قفسه)،
 * صاحب محدوده را با `PUT /api/shop/staff-scope/members/:id` می‌دهد و همه چیز با درخواستِ مستقیمِ
 * `/api/shop/catalog` سنجیده می‌شود (نه از راهِ برنامه). آن‌چه قفل می‌شود:
 *   ⛔ پرچمِ `shop.staff_scope` خاموش ⇒ حتی با ردیفِ محدوده همان پاسخ و همان اجازهٔ امروز.
 *   ⛔ درونِ محدوده مجاز؛ بیرونش ۴۰۳ `location_out_of_scope` بی هیچ ردیف؛ فهرست‌ها فقط محدوده.
 *   ⛔ مالک هرگز محدود نیست؛ محدودهٔ خالی (یا همهٔ مکان‌ها) = همه.
 *   ⛔ تغییرِ محدوده از درخواستِ بعد اثر دارد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

// ── کمکی‌ها (همان راهِ catalog-transfers.test.js) ──────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-s-${made.body.shop.id}`.slice(0, 60) };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), s.shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}
async function flag(s, key, value = true) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let opN = 0;
const opId = () => `01JXS${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: s.device, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': s.device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}
const op = (type, table, rowId, fields = {}) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const product = (id) => op('insert', 'products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
});
const entry = (id, pid, units) => op('insert', 'warehouseEntries', id, {
  productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now(),
});

let keyN = 0;
const fresh = () => `scope-test-${++keyN}-${Date.now()}`;
const cat = (p, token) => h.get(`/api/shop/catalog${p}`, { token });
const move = (token, body) => h.post('/api/shop/catalog/moves', body, { token, headers: { 'Idempotency-Key': fresh() } });
const adjust = (token, location, qtyMilli = 1000) => move(token, { kind: 'adjust', productId: 'x1', location, qtyMilli, reason: 'آزمون' });
const send = (token, body) => h.post('/api/shop/catalog/transfers', body, { token, headers: { 'Idempotency-Key': fresh() } });
const close = (token, id, what, body = {}) =>
  h.post(`/api/shop/catalog/transfers/${id}/${what}`, body, { token, headers: { 'Idempotency-Key': fresh() } });
const setScope = (s, who, places, token = s.token) => h.put(`/api/shop/staff-scope/members/${who.user.id}`, { places }, { token });
const moveCount = async (s) => (await one('SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1', [s.shopId])).n;
const denied = (r) => {
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'location_out_of_scope');
};
const L = (productId, qtyMilli) => ({ productId, qtyMilli });

async function seeded(name) {
  const s = await shop(name);
  await flag(s, 'shop.catalog');
  await push(s, [product('x1'), entry('x1-e', 'x1', 6), product('x2'), entry('x2-e', 'x2', 6)]);
  assert.equal((await cat('/products/x1', s.token)).status, 200);   //  خواندنِ نخستِ دفترِ دستگاه
  return s;
}
/** پاسخ‌های خواندنی بی `freshness` (زمان‌دار) — برای سنجشِ بایت‌به‌بایت */
async function reads(token) {
  const out = {};
  for (const p of ['/config', '/products', '/products/x1', '/moves?limit=100', '/tasks', '/transfers']) {
    const r = await cat(p, token);
    assert.equal(r.status, 200, `${p} ${JSON.stringify(r.body)}`);
    const { freshness, ...rest } = r.body;
    out[p] = JSON.stringify(rest);
  }
  return out;
}

// ── آزمون‌ها ─────────────────────────────────────────────────────────

test('STF-06: پرچم خاموش ⇒ ردیفِ محدوده بی‌اثر، پاسخ‌ها بایت‌به‌بایت همان و درهای تازه ۴۰۳', async () => {
  const s = await seeded('خاموش');
  const k = await member(s, 'storekeeper', 'انباردارِ خاموش');
  const before = await reads(k.token);
  await query(
    `INSERT INTO shop_member_scopes (shop_id, user_id, places, updated_by, updated_at) VALUES ($1,$2,'["warehouse"]'::jsonb,'t',$3)`,
    [s.shopId, k.user.id, now()]
  );
  assert.deepEqual(await reads(k.token), before);
  const cfg = await cat('/config', k.token);
  assert.deepEqual(cfg.body.locations, ['shop_floor', 'warehouse', 'damaged', 'in_transit']);
  assert.equal('scope' in cfg.body, false);
  assert.equal((await adjust(k.token, 'shop_floor')).status, 201);

  const conf = await h.get('/api/shop/staff-scope/config', { token: k.token });
  assert.deepEqual(conf.body, { enabled: false, places: ['shop_floor', 'warehouse'], mine: [] });
  const list = await h.get('/api/shop/staff-scope/members', { token: s.token });
  assert.equal(list.status, 403);
  assert.equal(list.body.error.code, 'staff_scope_disabled');
  const put = await setScope(s, k, ['shop_floor']);
  assert.equal(put.status, 403);
  assert.equal(put.body.error.code, 'staff_scope_disabled');
});

test('STF-06: درونِ محدوده مجاز، بیرونش با درخواستِ مستقیم ۴۰۳ و بی هیچ ردیف', async () => {
  const s = await seeded('محدود');
  await flag(s, 'shop.staff_scope');
  const k = await member(s, 'storekeeper', 'انباردارِ انبار');
  const set = await setScope(s, k, ['warehouse']);
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.deepEqual(set.body.places, ['warehouse']);
  assert.deepEqual(set.body.members.find((m) => m.userId === k.user.id).places, ['warehouse']);
  const mine = await h.get('/api/shop/staff-scope/config', { token: k.token });
  assert.deepEqual(mine.body, { enabled: true, places: ['shop_floor', 'warehouse'], mine: ['warehouse'] });

  //  نوشتن درونِ محدوده
  assert.equal((await adjust(k.token, 'warehouse', 3000)).status, 201);
  //  نوشتن بیرونِ محدوده — هر نوع
  const n = await moveCount(s);
  denied(await adjust(k.token, 'shop_floor'));
  denied(await move(k.token, { kind: 'count', productId: 'x1', location: 'shop_floor', countedMilli: 0, reason: 'آزمون' }));
  denied(await move(k.token, { kind: 'damage', productId: 'x1', qtyMilli: 1000, reason: 'شکست' }));          //  پیش‌فرض: قفسه
  denied(await move(k.token, { kind: 'writeoff', productId: 'x1', qtyMilli: 1000, reason: 'دور' }));        //  «خراب» مکان نیست
  denied(await move(k.token, { kind: 'transfer', productId: 'x1', from: 'shop_floor', to: 'warehouse', qtyMilli: 1000 }));
  denied(await send(k.token, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 1000)] }));
  assert.equal(await moveCount(s), n, 'ردشده نباید حرکت بسازد');
  //  بدنهٔ نامعتبر همان ۴۰۰ ِ امروز را می‌گیرد، نه ۴۰۳
  assert.equal((await adjust(k.token, 'nowhere')).status, 400);

  //  انتقالِ دومرحله‌ای: ارسال از مبدأِ محدوده؛ دریافت و رد فقط در مقصدِ محدوده
  const out = await send(k.token, { from: 'warehouse', to: 'shop_floor', lines: [L('x1', 1000)] });
  assert.equal(out.status, 201, JSON.stringify(out.body));
  denied(await close(k.token, out.body.transfer.id, 'receive'));
  denied(await close(k.token, out.body.transfer.id, 'reject', { reason: 'نه' }));
  const inn = await send(s.token, { from: 'shop_floor', to: 'warehouse', lines: [L('x1', 2000)] });
  assert.equal(inn.status, 201, JSON.stringify(inn.body));
  const got = await close(k.token, inn.body.transfer.id, 'receive', { lines: [{ productId: 'x1', receivedMilli: 1500 }], reason: 'کسری' });
  assert.equal(got.status, 200, JSON.stringify(got.body));

  //  فهرست‌ها فقط محدوده
  const cfg = await cat('/config', k.token);
  assert.deepEqual(cfg.body.locations, ['warehouse']);
  assert.deepEqual(cfg.body.scope, ['warehouse']);
  assert.deepEqual(Object.keys((await cat('/products/x1', k.token)).body.product.stock.levels), ['warehouse']);
  for (const p of (await cat('/products', k.token)).body.items) assert.deepEqual(Object.keys(p.stock.levels), ['warehouse']);
  const moves = (await cat('/moves?limit=100', k.token)).body.items;
  assert.ok(moves.length > 0);
  assert.ok(moves.every((m) => m.location === 'warehouse'), JSON.stringify(moves.map((m) => m.location)));
  assert.ok((await cat('/moves?limit=100', s.token)).body.items.some((m) => m.location === 'shop_floor'));
  assert.equal((await cat('/transfers', k.token)).body.transfers.length, 2);
  //  کسریِ انتقال (`in_transit`) از آنِ مقصد است: انباردارِ انبار می‌بیند، صندوقِ قفسه نه
  const tasks = (await cat('/tasks', k.token)).body.tasks;
  assert.deepEqual(tasks.map((t) => t.kind), ['transfer_short']);

  //  مدیرِ محدود به قفسه: گزارشِ تطبیق و تنظیمِ موجودی (همهٔ مکان‌ها) بسته؛ کسریِ انبار را نه می‌بیند نه می‌بندد
  const m = await member(s, 'manager', 'مدیرِ قفسه');
  assert.equal((await setScope(s, m, ['shop_floor'])).status, 200);
  denied(await cat('/report', m.token));
  denied(await h.put('/api/shop/catalog/settings', { negativePolicy: 'block' }, { token: m.token }));
  denied(await h.put('/api/shop/catalog/products/x1/low', { location: 'warehouse', lowMilli: 1000 }, { token: m.token }));
  assert.equal((await h.put('/api/shop/catalog/products/x1/low', { location: 'shop_floor', lowMilli: 1000 }, { token: m.token })).status, 200);
  assert.deepEqual((await cat('/tasks', m.token)).body.tasks, []);
  denied(await h.post(`/api/shop/catalog/tasks/${tasks[0].id}/resolve`, { action: 'resolved', note: 'نه' }, { token: m.token }));
  const ok = await h.post(`/api/shop/catalog/tasks/${tasks[0].id}/resolve`, { action: 'resolved', note: 'شمردم' }, { token: k.token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await cat('/report', s.token)).body.ok, true, 'دفتر و مانده یکی می‌مانند');
});

test('STF-06: مالک هرگز محدود نیست — نه با API، نه با ردیفِ دستی', async () => {
  const s = await seeded('مالک');
  await flag(s, 'shop.staff_scope');
  const own = await h.put(`/api/shop/staff-scope/members/${s.user.id}`, { places: ['warehouse'] }, { token: s.token });
  assert.equal(own.status, 400);
  assert.equal(own.body.error.code, 'owner_fixed');
  await query(
    `INSERT INTO shop_member_scopes (shop_id, user_id, places, updated_by, updated_at) VALUES ($1,$2,'["warehouse"]'::jsonb,'t',$3)`,
    [s.shopId, s.user.id, now()]
  );
  assert.equal((await adjust(s.token, 'shop_floor')).status, 201);
  assert.equal((await cat('/report', s.token)).status, 200);
  const cfg = await cat('/config', s.token);
  assert.equal(cfg.body.locations.length, 4);
  assert.equal('scope' in cfg.body, false);
  assert.deepEqual((await h.get('/api/shop/staff-scope/config', { token: s.token })).body.mine, []);
  //  فقط صاحب می‌دهد؛ مدیر فقط می‌بیند؛ عضوِ دکانِ دیگر پیدا نمی‌شود
  const m = await member(s, 'manager', 'مدیرِ مالک');
  const k = await member(s, 'storekeeper', 'انباردارِ مالک');
  assert.equal((await setScope(s, k, ['warehouse'], m.token)).status, 403);
  assert.equal((await h.get('/api/shop/staff-scope/members', { token: m.token })).status, 200);
  assert.equal((await h.get('/api/shop/staff-scope/members', { token: k.token })).status, 403);
  const other = await seeded('دیگر');
  await flag(other, 'shop.staff_scope');
  const cross = await setScope(other, k, ['warehouse']);
  assert.equal(cross.status, 404);
  assert.equal(cross.body.error.code, 'member_not_found');
  const bad = await setScope(s, k, ['damaged']);
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_location');
  assert.equal((await setScope(s, k, 'warehouse')).body.error.code, 'bad_places');
});

test('STF-06: محدودهٔ خالی یا همهٔ مکان‌ها = همه، بایت‌به‌بایت همان عضوِ بی‌محدوده', async () => {
  const s = await seeded('همه');
  await flag(s, 'shop.staff_scope');
  const k = await member(s, 'storekeeper', 'انباردارِ همه');
  const before = await reads(k.token);
  const both = await setScope(s, k, ['warehouse', 'shop_floor']);
  assert.equal(both.status, 200);
  assert.deepEqual(both.body.places, []);
  assert.equal(await one('SELECT 1 FROM shop_member_scopes WHERE shop_id=$1 AND user_id=$2', [s.shopId, k.user.id]), null);
  assert.deepEqual(await reads(k.token), before);
  assert.equal((await setScope(s, k, ['warehouse'])).status, 200);
  denied(await adjust(k.token, 'shop_floor'));
  assert.deepEqual((await setScope(s, k, [])).body.places, []);
  assert.deepEqual(await reads(k.token), before);
  assert.equal((await adjust(k.token, 'shop_floor')).status, 201);
  assert.equal((await move(k.token, { kind: 'writeoff', productId: 'x1', location: 'shop_floor', qtyMilli: 1000, reason: 'دور' })).status, 201);
});

test('STF-06: تغییرِ محدوده از همان درخواستِ بعد اثر دارد', async () => {
  const s = await seeded('تغییر');
  await flag(s, 'shop.staff_scope');
  const k = await member(s, 'storekeeper', 'انباردارِ تغییر');
  assert.equal((await setScope(s, k, ['warehouse'])).status, 200);
  denied(await adjust(k.token, 'shop_floor'));
  assert.equal((await adjust(k.token, 'warehouse')).status, 201);
  assert.equal((await setScope(s, k, ['shop_floor'])).status, 200);
  assert.equal((await adjust(k.token, 'shop_floor')).status, 201);
  denied(await adjust(k.token, 'warehouse'));
  assert.deepEqual((await cat('/config', k.token)).body.scope, ['shop_floor']);
  //  پرچم خاموش ⇒ از درخواستِ بعد همه
  await flag(s, 'shop.staff_scope', false);
  assert.equal((await adjust(k.token, 'warehouse')).status, 201);
  const a = await one(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='perms.scope_updated' ORDER BY created_at DESC LIMIT 1`, [s.shopId]);
  assert.ok(a, 'تغییرِ محدوده در سابقه');
});
