'use strict';
/**
 * گاوصندوقِ دکان (PAY-18، SAFE-V1) — سنجشِ سرور: `src/lib/safe-ledger.js`.
 *   • شکلِ ردیف = `SafeCore.check` (بخشِ `shape` ِ `core/test-vectors/safe-v1.json`) + نوعِ JSON؛
 *   • Sync v1: فقط درج؛ `safe.move` با نقش و لایه؛ «از دخل» برای شاگرد هم؛ جایِ پاسخ‌ها؛ تکرار؛
 *   • Sync v3: همان داوری روی opِ بومی؛
 *   • پمپ و بقیهٔ جدول‌ها بی هیچ کاری از میان‌افزار می‌گذرند.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const safe = require('../src/lib/safe-ledger');
const migrations = require('../src/lib/sync-v1-migrations');

const ROOT = path.join(__dirname, '..', '..');
const VEC = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'safe-v1.json'), 'utf8'));

let shopId;
let owner;
let manager;
let staff;
let n = 0;
const oid = () => `01JSAFE${String(++n).padStart(19, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: migrations.currentVersion('shop'), ops },
});
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', {
  token: who.accessToken, body: { device_id: device, protocol: 3, ops },
});
const setRole = (role, perms) => h.put(`/api/shop/permissions/roles/${role}`, { perms }, { token: owner.accessToken });
const rowOf = (id) => one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
      AND table_name='safeEntries' AND row_id=$2 AND deleted_at IS NULL`,
  [shopId, id]
);

const at = () => Date.now() + (++tick);
const op1 = (rowId, fields, type = 'insert') => ({
  op_id: oid(), ts: Date.now(), table: 'safeEntries', row_id: rowId, type, ...(type === 'delete' ? {} : { fields }),
});
const count = (id, amountMinor, expectedMinor = 0) => op1(id, { id, kind: 'count', amountMinor, createdAt: at(), expectedMinor });
const drop = (id, amountMinor) => op1(id, { id, kind: 'from_drawer', amountMinor, createdAt: at(), shiftId: 'sh-1', auditId: `a-${id}` });
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ گاوصندوق', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ گاوصندوق' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ گاوصندوق', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ گاوصندوق', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  manager = await h.signIn(m, 'shop');
  staff = await h.signIn(s, 'shop');
});
test.after(async () => { await h.stop(); });

test('شکلِ ردیف = SafeCore.check (core/test-vectors/safe-v1.json)', () => {
  assert.ok(VEC.shape.length >= 30);
  for (const c of VEC.shape) assert.equal(safe.check(c.row), c.code, c.name);
});

test('نوعِ JSON: عددِ اعشاری، متن به جای عدد و فیلدِ غیرِمتنی رد می‌شوند', () => {
  const ok = { id: 's1', kind: 'in', amountMinor: 500, createdAt: 1000, reason: 'deposit' };
  assert.equal(safe.check(ok), null);
  assert.equal(safe.check({ ...ok, amountMinor: 1.5 }), 'INVALID_AMOUNT');
  assert.equal(safe.check({ ...ok, amountMinor: '500' }), 'INVALID_AMOUNT');
  assert.equal(safe.check({ ...ok, createdAt: '1000' }), 'INVALID_TIME');
  assert.equal(safe.check({ ...ok, note: 5 }), 'BAD_FIELD');
  assert.equal(safe.check({ ...ok, kind: undefined }), 'BAD_KIND');
  assert.equal(safe.check({ id: 's1', kind: 'count', amountMinor: 0, createdAt: 1, expectedMinor: 0.5 }), 'BAD_FIELD');
  assert.equal(safe.check(null), 'BAD_FIELD');
  assert.equal(safe.check([]), 'BAD_FIELD');
});

test('جدولِ تازه در هر سه فهرست: v1 ِ دکان، پلهٔ ۴ و v3 با سیاستِ log', () => {
  assert.equal(require('../src/lib/sync').TABLES.safeEntries, 'shop_safe_entries');
  assert.equal(migrations.currentVersion('shop'), 4);
  assert.equal(require('../src/lib/sync-v3-schema').specOf('safeEntries').policy, 'log');
  assert.ok(!syncV1.PUMP_TABLES.includes('safeEntries'), 'جدولِ پمپ نیست');
});

test('v1: صاحب می‌شمارد، واریز می‌کند و به بانک می‌دهد؛ ردیف همان است که فرستاد', async () => {
  const ops = [
    count('own-c1', 100000),
    op1('own-i1', { id: 'own-i1', kind: 'in', amountMinor: 20000, createdAt: at(), reason: 'deposit', by: 'صاحب' }),
    op1('own-b1', { id: 'own-b1', kind: 'to_bank', amountMinor: 50000, createdAt: at(), bagNo: 'K-17' }),
  ];
  const r = await push1(owner, 'safe-own', ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  const row = await rowOf('own-b1');
  assert.equal(row.data.bagNo, 'K-17');
  assert.equal(row.data.amountMinor, 50000);
});

test('v1: فقط درج — ویرایش و حذف رد می‌شوند و ردیف می‌ماند', async () => {
  await push1(owner, 'safe-own', [count('own-c2', 7000, 0)]);
  const r = await push1(owner, 'safe-own', [
    op1('own-c2', { amountMinor: 1 }, 'update'),
    op1('own-c2', null, 'delete'),
  ]);
  assert.deepEqual(statuses(r), ['rejected:safe_append_only', 'rejected:safe_append_only']);
  assert.equal((await rowOf('own-c2')).data.amountMinor, 7000);
});

test('v1: ردیفِ بدشکل نمی‌نشیند (به بانک بی کیسه، مبلغِ اعشاری)', async () => {
  const r = await push1(owner, 'safe-own', [
    op1('bad-b1', { id: 'bad-b1', kind: 'to_bank', amountMinor: 500, createdAt: at() }),
    op1('bad-i1', { id: 'bad-i1', kind: 'in', amountMinor: 12.5, createdAt: at(), reason: 'deposit' }),
  ]);
  assert.deepEqual(statuses(r), ['rejected:safe_invalid', 'rejected:safe_invalid']);
  assert.equal(await rowOf('bad-b1'), null);
  assert.equal(await rowOf('bad-i1'), null);
});

test('v1: شاگرد نمی‌شمارد و برنمی‌دارد، ولی از دخل به گاوصندوق می‌گذارد؛ پاسخ‌ها سرِ جای خودشان', async () => {
  const product = { op_id: oid(), ts: Date.now(), table: 'products', row_id: 'safe-p1', type: 'insert', fields: { id: 'safe-p1', name: 'کالا' } };
  const ops = [
    product,
    count('stf-c1', 1000),
    drop('stf-d1', 3000),
    op1('stf-o1', { id: 'stf-o1', kind: 'out', amountMinor: 100, createdAt: at(), reason: 'owner_withdrawal' }),
  ];
  const r = await push1(staff, 'safe-staff', ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'rejected:safe_not_allowed', 'applied', 'rejected:safe_not_allowed']);
  assert.deepEqual(r.body.results.map((x) => x.op_id), ops.map((o) => o.op_id));
  assert.equal(await rowOf('stf-c1'), null);
  assert.equal((await rowOf('stf-d1')).data.auditId, 'a-stf-d1');
});

test('v1: لایهٔ نقش `safe.move` را از مدیر می‌گیرد؛ opِ پیش‌تر نشسته «تکراری» می‌ماند', async () => {
  const first = count('mgr-c1', 5000);
  assert.deepEqual(statuses(await push1(manager, 'safe-mgr', [first])), ['applied']);
  const set = await setRole('manager', { 'safe.move': { mode: 'deny' } });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  try {
    assert.deepEqual(statuses(await push1(manager, 'safe-mgr', [first])), ['duplicate']);
    assert.deepEqual(statuses(await push1(manager, 'safe-mgr', [count('mgr-c2', 6000), drop('mgr-d1', 100)])),
      ['rejected:safe_not_allowed', 'applied']);
    //  صاحب را هیچ لایه‌ای قفل نمی‌کند
    assert.deepEqual(statuses(await push1(owner, 'safe-own', [count('own-c3', 1)])), ['applied']);
  } finally {
    await setRole('manager', {});
  }
  assert.deepEqual(statuses(await push1(manager, 'safe-mgr', [count('mgr-c3', 6000)])), ['applied']);
});

test('v3: همان داوری روی opِ بومی', async () => {
  const v3 = (id, fields) => ({ op_id: oid(), hlc: H('v3-safe'), table: 'safeEntries', row_id: id, kind: 'insert', fields });
  const bad = await push3(staff, 'safe-v3-staff', [v3('v3-c1', { kind: 'count', amountMinor: 100, createdAt: at(), expectedMinor: 0 })]);
  assert.equal(bad.status, 200, JSON.stringify(bad.body));
  assert.equal(bad.body.results[0].status, 'rejected');
  assert.equal(bad.body.results[0].reason, 'safe_not_allowed');
  const drop3 = await push3(staff, 'safe-v3-staff', [v3('v3-d1', { kind: 'from_drawer', amountMinor: 100, createdAt: at(), shiftId: 's', auditId: 'a' })]);
  assert.equal(drop3.body.results[0].status, 'applied', JSON.stringify(drop3.body));
  const shape = await push3(owner, 'safe-v3-own', [v3('v3-b1', { kind: 'to_bank', amountMinor: 100, createdAt: at() })]);
  assert.equal(shape.body.results[0].reason, 'safe_invalid');
  const good = await push3(owner, 'safe-v3-own', [v3('v3-c2', { kind: 'count', amountMinor: 100, createdAt: at(), expectedMinor: 0 })]);
  assert.equal(good.body.results[0].status, 'applied', JSON.stringify(good.body));
});

test('راهِ قدیمِ /api/sync در گاوصندوق نمی‌نویسد (sync_v1_only)؛ بقیهٔ همان دسته می‌نشیند', async (t) => {
  t.mock.method(console, 'warn', () => {});
  const row = { kind: 'count', amountMinor: 999, createdAt: at(), expectedMinor: 0 };
  for (const [url, who, dev] of [['/api/sync', owner, 'legacy-own'], ['/api/v1/shop/sync/push', staff, 'legacy-stf']]) {
    const r = await h.api('POST', url, {
      token: who.accessToken,
      headers: { 'X-App': 'shop' },
      body: {
        deviceId: dev,
        changes: [
          { collection: 'safeEntries', id: `leg-${dev}`, updatedAt: Date.now(), data: row },
          { collection: 'safeEntries', id: `leg-del-${dev}`, updatedAt: Date.now(), deleted: true },
          { collection: 'products', id: `leg-p-${dev}`, updatedAt: Date.now(), data: { name: 'چای' } },
        ],
      },
    });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.applied, 1, url);
    assert.equal(r.body.skipped, 2, url);
    assert.deepEqual(r.body.conflicts.map((c) => [c.collection, c.id, c.reason]), [
      ['safeEntries', `leg-${dev}`, 'sync_v1_only'],
      ['safeEntries', `leg-del-${dev}`, 'sync_v1_only'],
    ], url);
  }
  assert.equal(Number((await one('SELECT count(*) AS n FROM shop_safe_entries WHERE shop_id=$1', [shopId])).n), 0);
});

test('میان‌افزار فقط دکان است: پمپ و بی‌حساب بی هیچ کاری رد می‌شوند', async () => {
  const body = () => ({ ops: [{ op_id: oid(), table: 'safeEntries', row_id: 'x', type: 'delete' }] });
  for (const sync of [
    { app: 'pump', accountKind: 'station', accountId: 'st-1', role: 'device' },
    { app: 'shop', accountKind: 'shop', accountId: '' },
    undefined,
  ]) {
    const req = { sync, body: body() };
    const before = JSON.stringify(req.body);
    let called = 0;
    const res = { json: () => assert.fail('پاسخ نباید عوض شود') };
    await safe.v1SafeGuard(req, res, () => { called++; });
    assert.equal(called, 1);
    assert.equal(JSON.stringify(req.body), before);
    assert.equal(typeof res.json, 'function');
  }
});
