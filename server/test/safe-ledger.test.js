'use strict';
/**
 * گاوصندوقِ دکان (PAY-18، SAFE-V1) — سنجشِ سرور: `src/lib/safe-ledger.js`.
 *   • شکلِ ردیف = `SafeCore.check` (بخشِ `shape` ِ `core/test-vectors/safe-v1.json`) + نوعِ JSON؛
 *   • Sync v1: فقط درج؛ `safe.move` با نقش و لایه؛ «از دخل» برای شاگرد هم؛ جایِ پاسخ‌ها؛ تکرار؛
 *   • Sync v3: همان داوری روی opِ بومی؛
 *   • پمپ و بقیهٔ جدول‌ها بی هیچ کاری از میان‌افزار می‌گذرند.
 */
process.env.SYNC_V3_ENABLED = '1';
//  پیش‌فرضِ سرور `local` است؛ این‌جا `db` تا روی PostgreSQL ِ واقعی قفلِ advisory و استخرِ جدا هم بدوند
process.env.SAFE_LOCK_SCOPE = 'db';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { EventEmitter } = require('node:events');
const { execFileSync } = require('node:child_process');
const h = require('./helpers');
const { one, isPglite } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const safe = require('../src/lib/safe-ledger');
const lock = require('../src/lib/account-lock');
const config = require('../src/config');
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
/** ساختِ A00 به بعد (`caps`): newer-fields کلیدِ null را برنمی‌دارد */
const pushA00 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: migrations.currentVersion('shop'), caps: ['a00'], ops },
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
  const legacy = require('../src/lib/sync');
  assert.ok('safeEntries' in legacy.TABLES, 'فهرستِ سفیدِ v1 ِ دکان');
  assert.equal(legacy.TABLES.safeEntries, null, 'جدولِ راهِ قدیم ندارد');
  assert.ok(legacy.V1_ONLY.has('safeEntries') && !legacy.LEGACY_COLLECTIONS.includes('safeEntries'));
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

test('v1: درجِ دوباره روی ردیفِ موجود با محتوای دیگر ویرایش است و رد می‌شود (شاگرد و صاحب)', async () => {
  const out = op1('rin-o1', { id: 'rin-o1', kind: 'out', amountMinor: 10000, createdAt: at(), reason: 'owner_withdrawal' });
  const cnt = count('rin-c1', 5000);
  assert.deepEqual(statuses(await push1(owner, 'safe-own', [count('rin-c0', 50000), out, cnt])), ['applied', 'applied', 'applied']);
  //  شاگرد «از دخل» را روی ردیفِ برداشتِ صاحب می‌نشاند — ادغامِ v1 ردیف را خراب و از مانده بیرون می‌کرد
  const overDrop = op1('rin-o1', { id: 'rin-o1', kind: 'from_drawer', amountMinor: 1, createdAt: at(), shiftId: 's', auditId: 'a' });
  assert.deepEqual(statuses(await push1(staff, 'safe-staff', [overDrop])), ['rejected:safe_append_only']);
  //  صاحب هم شمارشِ ثبت‌شده را بی‌صدا عوض نمی‌کند
  const bump = op1('rin-c1', { ...cnt.fields, amountMinor: 99999 });
  assert.deepEqual(statuses(await push1(owner, 'safe-own', [bump])), ['rejected:safe_append_only']);
  assert.equal((await rowOf('rin-o1')).data.kind, 'out');
  assert.equal((await rowOf('rin-o1')).data.amountMinor, 10000);
  assert.equal((await rowOf('rin-c1')).data.amountMinor, 5000);
});

test('v1: نامِ دیگرِ جدول (table_name، safe_entries، SafeEntries) هم از نگهبان می‌گذرد، نه از کنارش', async () => {
  assert.deepEqual(statuses(await push1(owner, 'safe-own', [count('alt-c1', 5000)])), ['applied']);
  const DAY = 24 * 60 * 60 * 1000;
  const r = await push1(staff, 'safe-staff', [
    //  شاگرد شمارشِ صاحب را با `table_name` عوض می‌کند
    { op_id: oid(), ts: Date.now(), table_name: 'safeEntries', row_id: 'alt-c1', type: 'insert',
      fields: { kind: 'count', amountMinor: 1, createdAt: at(), expectedMinor: 0 } },
    //  خروجِ بزرگ با تاریخِ ده روزِ بعد، با `safe_entries`
    { op_id: oid(), ts: Date.now(), table: 'safe_entries', row_id: 'alt-o1', type: 'insert',
      fields: { kind: 'out', amountMinor: 99999900, createdAt: Date.now() + 10 * DAY, reason: 'owner_withdrawal' } },
    //  شمارشِ تازهٔ شاگرد با `SafeEntries`
    { op_id: oid(), ts: Date.now(), tableName: 'SafeEntries', row_id: 'alt-c2', type: 'insert',
      fields: { kind: 'count', amountMinor: 1, createdAt: at(), expectedMinor: 0 } },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['rejected:safe_append_only', 'rejected:safe_invalid', 'rejected:safe_not_allowed']);
  assert.equal((await rowOf('alt-c1')).data.amountMinor, 5000);
  assert.equal(await rowOf('alt-o1'), null);
  assert.equal(await rowOf('alt-c2'), null);
});

test('v1: دو push ِ هم‌زمان با یک شناسه — فقط یکی می‌نشیند (قفلِ حساب تا پایانِ پاسخ)', async () => {
  for (let k = 0; k < 5; k++) {
    const a = count(`race-${k}`, 1000);
    const b = op1(`race-${k}`, { ...a.fields, amountMinor: 2000 });
    const [ra, rb] = await Promise.all([push1(owner, 'safe-race-a', [a]), push1(manager, 'safe-race-b', [b])]);
    const sa = statuses(ra)[0];
    assert.deepEqual([sa, statuses(rb)[0]].sort(), ['applied', 'rejected:safe_append_only'], `دور ${k}`);
    assert.equal((await rowOf(`race-${k}`)).data.amountMinor, sa === 'applied' ? 1000 : 2000);
  }
});

/** پاسخِ ساختگی برای `account-lock`: همان سه چیزی که می‌خواند (`end`، `destroyed`، `close`) */
function fakeRes() {
  const r = new EventEmitter();
  r.destroyed = false;
  r.writableEnded = false;
  r.end = function end() { this.writableEnded = true; return this; };
  return r;
}
const pause = (ms = 30) => new Promise((r) => { setTimeout(r, ms); });
/** تا [cond] یا سقف — روی PostgreSQL ِ واقعی آزادسازیِ قفلِ پایگاه چند رفت‌وبرگشت است */
async function until(cond, ms = 3000) {
  const end = Date.now() + ms;
  while (!cond() && Date.now() < end) await pause(5);
}
const settled = (p) => {
  const s = { done: false, value: undefined };
  p.then((v) => { s.done = true; s.value = v; }, (e) => { s.done = true; s.error = e; });
  return s;
};
/** سقف‌های `account-lock` برای یک آزمون، و برگرداندنشان */
async function withLimits(over, fn) {
  const was = { ...lock.limits };
  Object.assign(lock.limits, over);
  try { await fn(); } finally { Object.assign(lock.limits, was); }
}

test('قفلِ حساب: تا پایانِ کارِ گرداننده (res.end) می‌ماند، نه تا قطعِ اتصال؛ حسابِ دیگر منتظر نمی‌ماند', async () => {
  const r1 = fakeRes();
  const free1 = await lock.holdFor(r1, 'acc-lock-1');
  assert.equal(typeof free1, 'function');
  const r2 = fakeRes();
  const p2 = settled(lock.holdFor(r2, 'acc-lock-1'));
  const r3 = fakeRes();
  assert.equal(typeof await lock.holdFor(r3, 'acc-lock-2'), 'function');
  //  اتصالِ اولی پس از گرفتنِ قفل قطع می‌شود: گرداننده هنوز کار می‌کند، پس قفل می‌ماند
  r1.destroyed = true;
  r1.emit('close');
  await pause();
  assert.equal(p2.done, false, 'تا گرداننده پاسخ نداده، دومی منتظر است');
  r1.end();
  r1.end();                                                //  آزاد کردنِ دوباره بی‌اثر است
  await until(() => p2.done);
  assert.equal(p2.done, true);
  assert.equal(typeof p2.value, 'function');
  free1();
  r2.end();
  r3.end();
  const r4 = fakeRes();
  const free4 = await lock.holdFor(r4, 'acc-lock-1');      //  پس از آزاد شدن دوباره گرفتنی است
  free4();                                                 //  خطای گرداننده پیش از پاسخ: آزاد بی `end`
  const r5 = fakeRes();
  assert.equal(typeof await lock.holdFor(r5, 'acc-lock-1'), 'function');
  r5.end();
});

test('قفلِ حساب: درخواستی که در صف قطع شد کاری نمی‌کند و نوبتش را پس می‌دهد', async () => {
  const r1 = fakeRes();
  await lock.holdFor(r1, 'acc-lock-q');
  const r2 = fakeRes();
  const p2 = settled(lock.holdFor(r2, 'acc-lock-q'));
  r2.emit('close');                                        //  قطع در صف (شنونده پیش از صف ثبت شده بود)
  const r3 = fakeRes();
  const p3 = settled(lock.holdFor(r3, 'acc-lock-q'));
  r1.end();
  await until(() => p2.done && p3.done);
  assert.equal(p2.done, true);
  assert.equal(p2.value, null, 'قطع‌شده قفل نمی‌گیرد');
  assert.equal(p3.done, true, 'نوبتِ قطع‌شده بی‌درنگ به بعدی می‌رسد');
  assert.equal(typeof p3.value, 'function');
  r3.end();

  //  همان از درِ نگهبانِ v1: نه داوری، نه `next`
  const ctx = { app: 'shop', accountKind: 'shop', accountId: shopId, role: 'owner', userId: 'u' };
  const held = fakeRes();
  await safe.hold(held, ctx);
  const req = { sync: ctx, body: { ops: [count('gone-c1', 100)] } };
  const before = JSON.stringify(req.body);
  const res = fakeRes();
  res.json = () => assert.fail('پاسخ نباید ساخته شود');
  let called = 0;
  const guard = safe.v1SafeGuard(req, res, () => { called++; });
  res.destroyed = true;
  res.emit('close');
  held.end();
  await guard;
  assert.equal(called, 0);
  assert.equal(JSON.stringify(req.body), before);
});

test('قفلِ حساب: سقفِ انتظار ⇒ 503 SAFE_BUSY، و سقفِ سخت قفلِ گیرکرده را آزاد می‌کند', async () => {
  await withLimits({ WAIT_MS: 40, HOLD_MS: 120 }, async () => {
    const stuck = fakeRes();
    await lock.holdFor(stuck, 'acc-lock-t');               //  گرداننده‌ای که هرگز پاسخ نمی‌دهد
    await assert.rejects(lock.holdFor(fakeRes(), 'acc-lock-t'), (e) => e.status === 503 && e.code === 'SAFE_BUSY');
    lock.limits.WAIT_MS = 3000;                            //  از این‌جا فقط سقفِ سخت سنجیده می‌شود
    await pause(130);
    const r = fakeRes();
    assert.equal(typeof await lock.holdFor(r, 'acc-lock-t'), 'function', 'پس از سقفِ سخت آزاد است');
    r.end();
    stuck.end();                                           //  پایانِ دیرِ گیرکرده چیزی را آزاد نمی‌کند
    const r2 = fakeRes();
    await lock.holdFor(r2, 'acc-lock-t');
    r2.end();
  });
});

test('قفلِ حساب: سقفِ سخت بیش از requestTimeout ِ Node است و رسیدنش با console.error ِ HOLD_EXPIRED گزارش می‌شود', async (t) => {
  const http = require('node:http');
  assert.ok(lock.limits.HOLD_MS > http.createServer().requestTimeout,
    `HOLD_MS (${lock.limits.HOLD_MS}) باید بیش از requestTimeout باشد تا قفل زیرِ گردانندهٔ زنده آزاد نشود`);
  const said = [];
  t.mock.method(console, 'error', (...a) => { said.push(a.join(' ')); });
  await withLimits({ HOLD_MS: 30 }, async () => {
    const stuck = fakeRes();
    await lock.holdFor(stuck, 'acc-hold-warn');
    await pause(50);
    stuck.end();
    const r = fakeRes();
    await lock.holdFor(r, 'acc-hold-warn');
    r.end();
  });
  assert.equal(said.filter((m) => m.includes('HOLD_EXPIRED') && m.includes('acc-hold-warn')).length, 1, said.join('\n'));
});

/**
 * استخرِ ساختگیِ لایهٔ پایگاهِ `account-lock` — قفلِ advisoryِ نشستی مثلِ PostgreSQL: تا unlock یا بسته
 * شدنِ اتصال می‌ماند (پس دادنِ عادیِ اتصال آن را آزاد نمی‌کند).
 */
function fakePool({ connectFails = false, lockFails = false, unlockHangs = false } = {}) {
  const held = new Map();
  const log = [];
  const clients = [];
  return {
    log, held, clients,
    async connect() {
      if (connectFails) throw new Error('connect ECONNREFUSED');
      const me = { id: clients.length + 1, released: null };
      clients.push(me);
      me.query = async (sql, [key]) => {
        if (sql.includes('pg_try_advisory_lock')) {
          if (lockFails) throw new Error('terminating connection');
          log.push(`try:${me.id}`);
          if (held.has(key) && held.get(key) !== me) return { rows: [{ ok: false }] };
          held.set(key, me);
          return { rows: [{ ok: true }] };
        }
        log.push(`unlock:${me.id}`);
        if (unlockHangs) return new Promise(() => {});
        if (held.get(key) === me) held.delete(key);
        return { rows: [{ pg_advisory_unlock: true }] };
      };
      me.release = (destroy) => {
        me.released = destroy ? 'destroyed' : 'ok';
        if (destroy) for (const [k, c] of held) if (c === me) held.delete(k);   //  نشست رفت ⇒ قفل رفت
      };
      return me;
    },
  };
}
async function withPool(pool, fn) {
  lock.testing.usePool(pool);
  try { await fn(); } finally { lock.testing.usePool(null); }
}

test('قفلِ حساب: SAFE_LOCK_SCOPE و SAFE_LOCK_POOL_MAX از env (پیش‌فرض local و ۱۰، db فقط صریح)؛ local و PGlite بی اتصال', async () => {
  const read = (env) => JSON.parse(execFileSync(process.execPath, ['-e',
    "process.stdout.write(JSON.stringify(require('./src/config').accountLock))"], {
    cwd: path.join(__dirname, '..'), env: { ...process.env, SAFE_LOCK_SCOPE: '', SAFE_LOCK_POOL_MAX: '', ...env },
  }).toString());
  assert.deepEqual(read({}), { scope: 'local', poolMax: 10 }, 'بی تنظیم: بی اتصالِ اضافه');
  assert.deepEqual(read({ SAFE_LOCK_SCOPE: 'local', SAFE_LOCK_POOL_MAX: '3' }), { scope: 'local', poolMax: 3 });
  assert.deepEqual(read({ SAFE_LOCK_SCOPE: 'db' }), { scope: 'db', poolMax: 10 });
  assert.deepEqual(read({ SAFE_LOCK_SCOPE: ' DB ' }), { scope: 'db', poolMax: 10 });
  assert.deepEqual(read({ SAFE_LOCK_SCOPE: 'cluster', SAFE_LOCK_POOL_MAX: '0' }), { scope: 'local', poolMax: 1 });
  assert.equal(lock.testing.usesDb(), !isPglite(), 'روی PGlite فقط صفِ فرآیند؛ روی PostgreSQL قفلِ پایگاه هم');
  //  سرورِ تک‌نمونه (`local`): حتی با استخرِ پایگاه، اتصالی گرفته نمی‌شود
  const pool = fakePool();
  const was = config.accountLock.scope;
  config.accountLock.scope = 'local';
  try {
    await withPool(pool, async () => {
      assert.equal(lock.testing.usesDb(), false);
      const r = fakeRes();
      assert.equal(typeof await lock.holdFor(r, 'acc-local'), 'function');
      r.end();
    });
  } finally { config.accountLock.scope = was; }
  assert.equal(pool.clients.length, 0);
});

test('قفلِ حساب (پایگاه): اول صفِ دکان آزاد می‌شود؛ unlock ِ گیرکرده پس از سقف اتصالش را می‌بندد', async () => {
  //  حالتِ عادی: unlock و پس دادنِ اتصال
  const ok = fakePool();
  await withPool(ok, async () => {
    const r = fakeRes();
    await lock.holdFor(r, 'acc-db-ok');
    r.end();
    await until(() => ok.clients[0].released);
    assert.equal(ok.clients[0].released, 'ok');
    assert.equal(ok.held.size, 0);
  });
  const stuck = fakePool({ unlockHangs: true });
  await withPool(stuck, () => withLimits({ UNLOCK_MS: 60, RETRY_MS: 5 }, async () => {
    const r1 = fakeRes();
    await lock.holdFor(r1, 'acc-db-1');
    const r2 = fakeRes();
    const p2 = settled(lock.holdFor(r2, 'acc-db-1'));
    const r3 = fakeRes();
    assert.equal(typeof await lock.holdFor(r3, 'acc-db-2'), 'function', 'دکانِ دیگر منتظر نمی‌ماند');
    r1.end();
    await pause(20);
    assert.ok(stuck.log.includes('try:2'), 'صفِ دکان منتظرِ unlock نماند');
    assert.equal(p2.done, false, 'قفلِ پایگاه تا رفتنِ نشستِ اولی مالِ اولی است');
    await pause(80);
    assert.equal(stuck.clients[0].released, 'destroyed', 'unlock ِ گیرکرده ⇒ اتصال بسته شد');
    assert.equal(p2.done, true);
    assert.equal(typeof p2.value, 'function');
    r2.end();
    r3.end();
    await pause(80);
    assert.equal(stuck.held.size, 0);
  }));
});

test('قفلِ حساب (پایگاه): خطای استخر یا پرس‌وجو ⇒ 503 SAFE_BUSY، نه ۵۰۰؛ نوبتِ دکان پس داده می‌شود', async (t) => {
  t.mock.method(console, 'error', () => {});
  const busy = (e) => e.status === 503 && e.code === 'SAFE_BUSY';
  await withPool(fakePool({ connectFails: true }), async () => {
    await assert.rejects(lock.holdFor(fakeRes(), 'acc-db-bad'), busy);
    //  از درِ HTTP: همان 503
    const r = await push1(owner, 'safe-own', [count('pool-c1', 100)]);
    assert.equal(r.status, 503, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'SAFE_BUSY');
  });
  const broken = fakePool({ lockFails: true });
  await withPool(broken, async () => {
    await assert.rejects(lock.holdFor(fakeRes(), 'acc-db-bad'), busy);
    assert.equal(broken.clients[0].released, 'destroyed');
  });
  const r = fakeRes();
  assert.equal(typeof await lock.holdFor(r, 'acc-db-bad'), 'function', 'صفِ فرآیند گیر نکرد');
  r.end();
  assert.equal(await rowOf('pool-c1'), null);
});

test('v1: ردیفی که فقط در v3 است (پلِ v3⇒v1 هنوز نبرده) هم «هست» — فقط عینِ همان می‌گذرد', async () => {
  const fields = { kind: 'count', amountMinor: 700, createdAt: at(), expectedMinor: 0 };
  const t = Date.now();
  await one(
    `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, inserted, created_at, updated_at)
     VALUES ('shop', 'shop', $1, 'safeEntries', 'v3o-c1', $2::jsonb, true, $3, $3) RETURNING row_id`,
    [shopId, JSON.stringify({ id: 'v3o-c1', ...fields }), t]
  );
  const ins = (f) => op1('v3o-c1', f);
  assert.deepEqual(statuses(await pushA00(owner, 'safe-own-a00', [ins({ ...fields, amountMinor: 701 })])), ['rejected:safe_append_only']);
  //  بخشی از آن ردیفِ ناقص در v1 می‌ساخت
  assert.deepEqual(statuses(await pushA00(staff, 'safe-staff-a00', [ins({ amountMinor: 700 })])), ['rejected:safe_append_only']);
  assert.equal(await rowOf('v3o-c1'), null);
  //  عینِ همان (همان دستگاه از راهِ دیگر): همان ردیفی است که پل می‌برد
  assert.deepEqual(statuses(await pushA00(staff, 'safe-staff-a00', [ins({ id: 'v3o-c1', ...fields })])), ['applied']);
  assert.deepEqual((await rowOf('v3o-c1')).data, { id: 'v3o-c1', ...fields });
});

test('v1 و v3 یک قفل دارند: push ِ گاوصندوق تا پایانِ پاسخِ دیگری می‌ایستد؛ push ِ بی گاوصندوق نه', async () => {
  const ctx = { accountId: shopId };
  const held = fakeRes();
  await safe.hold(held, ctx);
  const v1 = settled(push1(owner, 'safe-lock-v1', [count('lk-c1', 100)]));
  const v3 = settled(push3(owner, 'safe-lock-v3', [{ op_id: oid(), hlc: H('v3-lock'), table: 'safeEntries', row_id: 'lk-c2', kind: 'insert',
    fields: { kind: 'count', amountMinor: 100, createdAt: at(), expectedMinor: 0 } }]));
  const other = await push1(owner, 'safe-lock-other', [{ ...op1('lk-p1', { id: 'lk-p1', name: 'چای' }), table: 'products' }]);
  assert.equal(other.status, 200, JSON.stringify(other.body));
  await pause(80);
  assert.equal(v1.done, false, 'push ِ v1 منتظرِ قفل است');
  assert.equal(v3.done, false, 'push ِ v3 منتظرِ قفل است');
  held.end();
  while (!v1.done || !v3.done) await pause(10);
  assert.deepEqual(statuses(v1.value), ['applied']);
  assert.equal(v3.value.body.results[0].status, 'applied', JSON.stringify(v3.value.body));
});

test('v1: کلیدِ null دیگر راهِ دور زدن نیست — نگهبان پس از newer-fields، و opِ ردشدهٔ validateOp هم safe_invalid', async () => {
  for (const id of ['nul-c1', 'nul-c2']) assert.deepEqual(statuses(await push1(owner, 'safe-own', [count(id, 50000, 0)])), ['applied']);
  const junk = () => {
    const big = { kind: 'out', amountMinor: 777, createdAt: at(), reason: 'owner_withdrawal' };
    for (let k = 0; k < 3000; k++) big[`k${'x'.repeat(20)}${k}`] = null;
    return [
      //  اثرِ انگشتِ غلط + کلیدِ null (newer-fields اثرِ انگشت را از نو می‌سازد)
      { op_id: oid(), ts: Date.now(), table: 'safeEntries', row_id: 'nul-c1', type: 'update', fields: { amountMinor: 1, junk: null }, hash: '0'.repeat(64) },
      //  نامِ `$` با null
      { op_id: oid(), ts: Date.now(), table: 'safeEntries', row_id: 'nul-c2', type: 'update', fields: { amountMinor: 2, $x: null } },
      //  درجِ «برداشت» از شاگرد با بیش از ۶۴KB کلیدِ null
      { op_id: oid(), ts: Date.now(), table: 'safe_entries', row_id: 'nul-o1', type: 'insert', fields: big },
    ];
  };
  //  ساختِ پیش از A00 (newer-fields کلیدهای null را برمی‌دارد): نگهبان opِ پاک‌شده را می‌بیند
  const old = await push1(staff, 'safe-staff-old', junk());
  assert.deepEqual(statuses(old), ['rejected:safe_append_only', 'rejected:safe_append_only', 'rejected:safe_not_allowed'], JSON.stringify(old.body));
  //  ساختِ A00 (بی پالایش): opی که validateOp نمی‌پذیرد، همین‌جا رد است
  const a00 = await pushA00(staff, 'safe-staff-a00', junk());
  assert.deepEqual(statuses(a00), ['rejected:safe_invalid', 'rejected:safe_invalid', 'rejected:safe_invalid'], JSON.stringify(a00.body));
  assert.equal((await rowOf('nul-c1')).data.amountMinor, 50000);
  assert.equal((await rowOf('nul-c2')).data.amountMinor, 50000);
  assert.equal(await rowOf('nul-o1'), null);
});

test('v1: بخشی از همان ردیف بی‌اثر می‌گذرد، حتی از شاگرد؛ null روی فیلدِ پر، `id` ِ دیگر یا فیلدِ تازه ویرایش است', async () => {
  const cnt = count('sub-c1', 4200, 0);
  assert.deepEqual(statuses(await push1(owner, 'safe-own', [cnt])), ['applied']);
  //  ساختِ A00: null همان‌طور که فرستاده شد به نگهبان می‌رسد
  const one = async (fields) => statuses(await pushA00(staff, 'safe-staff-a00', [op1('sub-c1', fields)]))[0];
  assert.equal(await one({ amountMinor: 4200 }), 'applied');
  assert.equal(await one({ kind: 'count', note: null, id: 'sub-c1' }), 'applied');
  assert.equal(await one({ amountMinor: null }), 'rejected:safe_append_only');
  assert.equal(await one({ amountMinor: 4200, id: 'other' }), 'rejected:safe_append_only');
  assert.equal(await one({ amountMinor: 4200, note: 'تازه' }), 'rejected:safe_append_only');
  const live = Object.fromEntries(Object.entries((await rowOf('sub-c1')).data).filter(([, x]) => x !== null));
  assert.deepEqual(live, cnt.fields);
  //  ردیفِ تازه با `id` ِ دیگر در فیلدها شکلِ نادرست است
  const odd = count('sub-c2', 1);
  odd.fields.id = 'sub-c3';
  assert.deepEqual(statuses(await push1(owner, 'safe-own', [odd])), ['rejected:safe_invalid']);
  assert.equal(await rowOf('sub-c2'), null);
});

test('v1: همان ردیف عیناً (فرستادنِ دوباره پس از بازیابی) بی‌اثر می‌گذرد، حتی از شاگرد', async () => {
  const cnt = count('rep-c1', 4200, 0);
  assert.deepEqual(statuses(await push1(owner, 'safe-own', [cnt])), ['applied']);
  //  opِ تازه، همان محتوا، ترتیبِ دیگرِ کلیدها و `id` در فیلدها
  const again = { ...cnt, op_id: oid(), fields: { expectedMinor: 0, createdAt: cnt.fields.createdAt, amountMinor: 4200, kind: 'count', id: 'rep-c1' } };
  assert.deepEqual(statuses(await push1(staff, 'safe-staff-restored', [again])), ['applied']);
  assert.equal((await rowOf('rep-c1')).data.amountMinor, 4200);
});

test('v1: دو درجِ یک ردیف در یک دسته — دومی رد می‌شود', async () => {
  const a = count('two-c1', 3000);
  const b = op1('two-c1', { ...a.fields, amountMinor: 3001 });
  const same = { ...a, op_id: oid() };
  assert.deepEqual(statuses(await push1(owner, 'safe-own', [a, b, same])),
    ['applied', 'rejected:safe_append_only', 'rejected:safe_append_only']);
  assert.equal((await rowOf('two-c1')).data.amountMinor, 3000);
});

test('زمانِ بیش از یک روز جلوتر از سرور رد می‌شود (v1 و v3)؛ تا یک روز پذیرفته است', async () => {
  const far = Date.now() + safe.MAX_AHEAD_MS + 60_000;
  const near = Date.now() + safe.MAX_AHEAD_MS - 60_000;
  const r = await push1(owner, 'safe-own', [
    op1('fut-1', { id: 'fut-1', kind: 'count', amountMinor: 1, createdAt: far, expectedMinor: 0 }),
    op1('fut-2', { id: 'fut-2', kind: 'count', amountMinor: 1, createdAt: near, expectedMinor: 0 }),
  ]);
  assert.deepEqual(statuses(r), ['rejected:safe_invalid', 'applied']);
  const v3 = await push3(owner, 'safe-v3-own', [{ op_id: oid(), hlc: H('v3-safe'), table: 'safeEntries', row_id: 'fut-3', kind: 'insert',
    fields: { kind: 'count', amountMinor: 1, createdAt: far, expectedMinor: 0 } }]);
  assert.equal(v3.body.results[0].reason, 'safe_invalid', JSON.stringify(v3.body));
});

test('v3: همان داوری روی opِ بومی', async () => {
  const v3 = (id, fields) => ({ op_id: oid(), hlc: H('v3-safe'), table: 'safeEntries', row_id: id, kind: 'insert', fields });
  const bad = await push3(staff, 'safe-v3-staff', [v3('v3-c1', { kind: 'count', amountMinor: 100, createdAt: at(), expectedMinor: 0 })]);
  assert.equal(bad.status, 200, JSON.stringify(bad.body));
  assert.equal(bad.body.results[0].status, 'rejected');
  assert.equal(bad.body.results[0].reason, 'safe_not_allowed');
  const dropFields = { kind: 'from_drawer', amountMinor: 100, createdAt: at(), shiftId: 's', auditId: 'a' };
  const drop3 = await push3(staff, 'safe-v3-staff', [v3('v3-d1', dropFields)]);
  assert.equal(drop3.body.results[0].status, 'applied', JSON.stringify(drop3.body));
  //  «از دخل» فقط درجِ تازه است: روی ردیفِ موجود شاگرد تعارض نمی‌سازد؛ بخشی از همان بی‌اثر است
  const dropOther = await push3(staff, 'safe-v3-staff', [v3('v3-d1', { ...dropFields, amountMinor: 5 })]);
  assert.equal(dropOther.body.results[0].reason, 'safe_not_allowed', JSON.stringify(dropOther.body));
  const dropPart = await push3(staff, 'safe-v3-staff', [v3('v3-d1', { amountMinor: 100 })]);
  assert.notEqual(dropPart.body.results[0].status, 'rejected', JSON.stringify(dropPart.body));
  const shape = await push3(owner, 'safe-v3-own', [v3('v3-b1', { kind: 'to_bank', amountMinor: 100, createdAt: at() })]);
  assert.equal(shape.body.results[0].reason, 'safe_invalid');
  const fields = { kind: 'count', amountMinor: 100, createdAt: at(), expectedMinor: 0 };
  const good = await push3(owner, 'safe-v3-own', [v3('v3-c2', fields)]);
  assert.equal(good.body.results[0].status, 'applied', JSON.stringify(good.body));
  //  ردیفِ موجود: عیناً همان بی‌اثر (حتی از شاگرد)؛ محتوای دیگر از صاحب تعارض است و از شاگرد ردِ مجوز
  const same = await push3(staff, 'safe-v3-staff', [v3('v3-c2', fields)]);
  assert.notEqual(same.body.results[0].status, 'rejected', JSON.stringify(same.body));
  const staffOther = await push3(staff, 'safe-v3-staff', [v3('v3-c2', { ...fields, amountMinor: 999 })]);
  assert.equal(staffOther.body.results[0].reason, 'safe_not_allowed', JSON.stringify(staffOther.body));
  const other = await push3(owner, 'safe-v3-own', [v3('v3-c2', { ...fields, amountMinor: 999 })]);
  assert.equal(other.body.results[0].status, 'conflict', JSON.stringify(other.body));
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
  //  راهِ قدیم این مجموعه را نه می‌خواند نه نشان می‌دهد
  const status = await h.api('GET', '/api/sync/status', { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(status.status, 200, JSON.stringify(status.body));
  assert.ok(status.body.collections.includes('products') && !status.body.collections.includes('safeEntries'));
  const pulled = await h.api('GET', '/api/sync?since=0&deviceId=legacy-pull', { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  assert.equal(pulled.status, 200, JSON.stringify(pulled.body));
  assert.ok(!pulled.body.changes.some((c) => c.collection === 'safeEntries'));
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
