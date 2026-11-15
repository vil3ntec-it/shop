'use strict';
/**
 * SYN-07 / S28 — صندوقِ تعارضِ v3 در برنامه، نیمهٔ سرور (`lib/sync-v3-inbox.js`)، از راهِ HTTP ِ واقعی:
 *   • پرچمِ `shop.sync_v3_inbox` خاموش ⇒ فهرست بی `inbox`، `merge` همان ۴۰۰ ِ `bad_action`، بستنِ دوباره همان ۴۰۹؛
 *   • روشن ⇒ `inbox` در فهرست؛ `merge` فقط فیلدهای برگزیدهٔ بازنده را برمی‌گرداند (بقیه برنده)، با همان opِ `srv.rv.`؛
 *   • تکرارِ همان تصمیمِ همان کاربر ⇒ ۲۰۰ ِ `duplicate` بی opِ دوم؛ تصمیمِ دیگر یا کاربرِ دیگر ⇒ ۴۰۹؛
 *   • ادغامِ تعارضِ تک‌فیلدی یا فیلدِ ناشناخته ⇒ ۴۰۰؛ شاگرد ⇒ ۴۰۳.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');

const A = 'inbox-dev-a';
const B = 'inbox-dev-b';
const C = 'inbox-dev-c';
const FLAG = 'shop.sync_v3_inbox';

let owner;
let manager;
let staff;
let shopId;
let n = 0;
const oid = () => `01JIN${String(++n).padStart(21, '0')}`;
const BASE = Date.now() - 3_600_000;
let tick = 0;
const H = (node, wall = BASE + (++tick) * 10) => hlc.format({ wall, counter: 0, node });

const push = (token, device, ops) => h.api('POST', '/api/sync/v3/push', { token, body: { device_id: device, protocol: 3, ops } });
const list = (token, status = 'open') => h.api('GET', `/api/sync/v3/conflicts?status=${status}`, { token });
const resolve = (token, id, body) => h.api('POST', `/api/sync/v3/conflicts/${id}/resolve`, { token, body });
const rowOf = (id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name='products' AND row_id=$2`, [shopId, id]
);

async function setFlag(value) {
  await query(
    `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,$4)
     ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value`, [String(shopId), FLAG, JSON.stringify(value), Date.now()],
  );
  require('../src/lib/hot-cache').invalidate('flag');
}

async function member(label, role) {
  const u = await h.newUser(label, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`, [newId('mem'), shopId, u.user.id, role, now()],
  );
  return h.signIn(u, 'shop');
}

/** یک کالا که دستگاهِ ساعت‌کج دو فیلدش را عوض کرد ⇒ تعارضِ `clock_skew` با `field = '*'` */
async function skewed(rowId) {
  await push(owner.accessToken, A, [{
    op_id: oid(), hlc: H(A), table: 'products', row_id: rowId, kind: 'insert',
    fields: { name: 'چای سبز', salePriceMinor: 10000, barcode: '111' },
  }]);
  const future = hlc.format({ wall: Date.now() + 10 * 60_000, counter: 0, node: C });
  const r = await push(owner.accessToken, C, [{
    op_id: oid(), hlc: future, table: 'products', row_id: rowId, kind: 'update', fields: { name: 'چای سبزِ تازه', salePriceMinor: 12500 },
  }]);
  assert.equal(r.body.results[0].status, 'conflict', JSON.stringify(r.body));
  const box = (await list(owner.accessToken)).body.conflicts.find((c) => c.row_id === rowId && c.kind === 'clock_skew');
  assert.ok(box, 'تعارضِ ساعتِ کج در صندوق');
  assert.equal(box.field, '*');
  return box;
}

/** دو دستگاه یک فیلد ⇒ تعارضِ تک‌فیلدیِ `lww` */
async function lww(rowId) {
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: rowId, kind: 'insert', fields: { name: 'برنج', salePriceMinor: 1000 } }]);
  const lose = H(B);
  await push(owner.accessToken, A, [{ op_id: oid(), hlc: H(A), table: 'products', row_id: rowId, kind: 'update', fields: { salePriceMinor: 2000 } }]);
  await push(owner.accessToken, B, [{ op_id: oid(), hlc: lose, table: 'products', row_id: rowId, kind: 'update', fields: { salePriceMinor: 1500 } }]);
  return (await list(owner.accessToken)).body.conflicts.find((c) => c.row_id === rowId && c.kind === 'lww');
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ صندوقِ تعارض', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ صندوقِ تعارض' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
  manager = await member('مدیرِ دکان', 'manager');
  staff = await member('شاگردِ دکان', 'staff');
});
test.after(async () => { await h.stop(); });

test('پرچم خاموش ⇒ فهرست بی inbox، merge همان bad_action و بستنِ دوباره همان ۴۰۹', async () => {
  await setFlag(false);
  const box = await skewed('p-off');
  const l = await list(owner.accessToken);
  assert.equal(l.status, 200);
  assert.equal('inbox' in l.body, false, 'پاسخِ فهرست همان امروز');
  const m = await resolve(owner.accessToken, box.id, { action: 'merge', fields: ['name'] });
  assert.equal(m.status, 400);
  assert.equal(m.body.error.code, 'bad_action');
  assert.equal((await rowOf('p-off')).data.name, 'چای سبز', 'هیچ چیزی برنگشت');
  const k = await resolve(owner.accessToken, box.id, { action: 'keep' });
  assert.equal(k.status, 200);
  assert.equal(k.body.duplicate, undefined);
  const again = await resolve(owner.accessToken, box.id, { action: 'keep' });
  assert.equal(again.status, 409, 'بی پرچم تکرار همان ۴۰۹ ِ امروز');
  assert.equal(again.body.error.code, 'already_resolved');
});

test('پرچم روشن ⇒ inbox در فهرست؛ ادغام فقط فیلدِ برگزیده را برمی‌گرداند و بقیه برنده می‌مانند', async () => {
  await setFlag(true);
  const box = await skewed('p-merge');
  const l = await list(owner.accessToken);
  assert.deepEqual(l.body.inbox, { v: 1, merge: true });
  assert.deepEqual(box.loser_value, { name: 'چای سبزِ تازه', salePriceMinor: 12500 });
  assert.deepEqual(box.winner_value, { name: 'چای سبز', salePriceMinor: 10000 });

  const m = await resolve(manager.accessToken, box.id, { action: 'merge', fields: ['salePriceMinor'] });
  assert.equal(m.status, 200, JSON.stringify(m.body));
  assert.equal(m.body.conflict.status, 'reverted');
  assert.deepEqual(m.body.conflict.detail.merged, ['salePriceMinor']);
  assert.equal(m.body.op.op_id, `srv.rv.${box.id}`);
  const row = await rowOf('p-merge');
  assert.equal(row.data.salePriceMinor, 12500, 'فیلدِ برگزیده از بازنده');
  assert.equal(row.data.name, 'چای سبز', 'فیلدِ دیگر همان برنده');
  assert.equal(row.data.barcode, '111');

  //  دستگاه‌های دیگر فقط همان یک فیلد را با opِ سرور می‌گیرند
  const p = await h.api('GET', `/api/sync/v3/pull?device_id=${A}&since=0`, { token: owner.accessToken });
  const srv = p.body.ops.filter((o) => o.row_id === 'p-merge' && o.origin === 'server');
  assert.equal(srv.length, 1);
  assert.deepEqual(srv[0].effect.set, { salePriceMinor: 12500 });
});

test('تکرارِ همان تصمیمِ همان کاربر ⇒ duplicate بی opِ دوم؛ تصمیم یا کاربرِ دیگر ⇒ ۴۰۹', async () => {
  await setFlag(true);
  const box = await skewed('p-retry');
  const first = await resolve(owner.accessToken, box.id, { action: 'merge', fields: ['name'] });
  assert.equal(first.status, 200);
  const again = await resolve(owner.accessToken, box.id, { action: 'merge', fields: ['name'] });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.duplicate, true);
  assert.equal(again.body.op, null);
  assert.equal(again.body.conflict.status, 'reverted');
  const ops = await many(`SELECT op_id FROM sync_v3_ops WHERE account_id=$1 AND op_id=$2`, [shopId, `srv.rv.${box.id}`]);
  assert.equal(ops.length, 1, 'یک op، نه دو');
  assert.equal((await rowOf('p-retry')).data.name, 'چای سبزِ تازه');

  const other = await resolve(owner.accessToken, box.id, { action: 'keep' });
  assert.equal(other.status, 409, 'تصمیمِ دیگر روی ردیفِ بسته');
  const otherUser = await resolve(manager.accessToken, box.id, { action: 'merge', fields: ['name'] });
  assert.equal(otherUser.status, 409, 'کاربرِ دیگر');

  const keepBox = await lww('p-keep');
  assert.equal((await resolve(owner.accessToken, keepBox.id, { action: 'keep' })).status, 200);
  const keepAgain = await resolve(owner.accessToken, keepBox.id, { action: 'keep' });
  assert.equal(keepAgain.status, 200);
  assert.equal(keepAgain.body.duplicate, true);
  assert.equal(keepAgain.body.conflict.status, 'kept');
});

test('ادغامِ نادرست ⇒ ۴۰۰ و هیچ اثری؛ شاگرد ⇒ ۴۰۳', async () => {
  await setFlag(true);
  const single = await lww('p-single');
  const ns = await resolve(owner.accessToken, single.id, { action: 'merge', fields: ['salePriceMinor'] });
  assert.equal(ns.status, 400);
  assert.equal(ns.body.error.code, 'not_mergeable');

  const box = await skewed('p-bad');
  for (const fields of [[], ['nope'], 'name', [1], undefined]) {
    const r = await resolve(owner.accessToken, box.id, { action: 'merge', fields });
    assert.equal(r.status, 400, JSON.stringify(fields));
    assert.equal(r.body.error.code, 'bad_fields');
  }
  const denied = await resolve(staff.accessToken, box.id, { action: 'merge', fields: ['name'] });
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'resolve_not_allowed');
  const still = (await list(owner.accessToken)).body.conflicts.find((c) => c.id === box.id);
  assert.equal(still.status, 'open', 'هنوز باز');
  assert.equal((await rowOf('p-bad')).data.name, 'چای سبز');
});
