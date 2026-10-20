'use strict';
/**
 * §۹.۵ — وبِ دکان (منجمد) پس از بازیابیِ سرور: نشاندنِ دوبارهٔ opهای دوباره‌فرستاده بسته است.
 *
 * برنامه‌های بومی پس از بازیابی opهای «اعمال‌شده»ی گم‌شدهٔ خودشان را با همان op_id دوباره
 * می‌فرستند (`RestoreReplay`). مرورگری که همان op را پیش از بازیابی نشانده بود، اگر آن را دوباره
 * Pull کند، `$inc` (پرداخت، مرجوعی…) دو بار جمع می‌شد. حالا وب «دورانِ بازیابی» را می‌خواند و
 * به‌جای Pull از Snapshot از نو می‌سازد — v1 (`license/sync-engine.js`، در vm با localStorage و
 * حسابِ ساختگی ولی سرورِ واقعی) و v3 (`license/sync-v3-engine.js`).
 *
 * «بازیابی» این‌جا: جدول‌های همگام‌سازی به نسخهٔ لحظهٔ پشتیبان برمی‌گردند، شمارنده هم، و
 * `rotate('restore')` (همان کارِ `backup.restore`). بازیابیِ واقعی با فایل: `test/e2e/ops.e2e.mjs`.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_SYNC_MAX = process.env.RATE_SYNC_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const vm = require('node:vm');
const h = require('./helpers');
const { one, query } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const epoch = require('../src/lib/sync-epoch');
const hlc = require('../src/lib/hlc');

const ROOT = path.join(__dirname, '..', '..');
const core = require(path.join(ROOT, 'license', 'sync-core.js'));
const V3 = require(path.join(ROOT, 'license', 'sync-v3-engine.js'));
const ENGINE = fs.readFileSync(path.join(ROOT, 'license', 'sync-engine.js'), 'utf8');

let owner;
let n = 0;
const oid = () => `01JWR${String(Date.now()).slice(-8)}${String(++n).padStart(6, '0')}`;

/** پشتیبان/بازیابیِ جدول‌های همگام‌سازی، با شمارنده — همان چیزی که فایلِ پشتیبان برمی‌گرداند */
async function backup(tables, seqName) {
  for (const t of tables) {
    await query(`DROP TABLE IF EXISTS bk_${t}`);
    await query(`CREATE TABLE bk_${t} AS SELECT * FROM ${t}`);
  }
  return Number((await one(`SELECT last_value FROM ${seqName}`)).last_value);
}
async function restore(tables, seqName, last) {
  for (const t of tables) {
    await query(`DELETE FROM ${t}`);
    await query(`INSERT INTO ${t} SELECT * FROM bk_${t}`);
  }
  await query(`SELECT setval('${seqName}', $1)`, [last]);
  await epoch.rotate('restore');
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ وبِ بازیابی', 'shop');
  await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ وبِ بازیابی' } });
  owner = await h.signIn(owner, 'shop');
});
test.after(async () => { await h.stop(); });

/* -------------------------------------------------------------- وب v1 */

/** همان `license/sync-engine.js` در vm: localStorage، حسابِ ساختگی، سرورِ واقعی */
function webV1(token, deviceId) {
  const store = new Map();
  const calls = [];
  const acc = {
    account: () => ({ userId: 'u-web' }),
    configured: () => true,
    isSignedIn: () => true,
    deviceId: () => deviceId,
    heartbeat: async () => ({}),
    on: () => {},
    async authFetch(p, opts = {}) {
      calls.push(`${opts.method || 'GET'} ${p.split('?')[0]}`);
      const r = await h.api(opts.method || 'GET', p, { token, body: opts.body, headers: { 'X-App': 'shop' } });
      if (r.status < 200 || r.status > 299) throw Object.assign(new Error(`HTTP ${r.status}`), { status: r.status });
      return r.body;
    },
  };
  const window = {
    TohidSyncCore: core,
    TohidAccount: acc,
    dispatchEvent: () => true,
    addEventListener: () => {},
  };
  const sandbox = {
    window,
    localStorage: { getItem: (k) => (store.has(k) ? store.get(k) : null), setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) },
    navigator: { onLine: true },
    document: { addEventListener: () => {}, hidden: false },
    CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init && init.detail; } },
    setTimeout, clearTimeout, setInterval, clearInterval, console, URL,
  };
  vm.runInNewContext(ENGINE, sandbox, { filename: 'sync-engine.js' });
  let ledger = {};
  window.TohidSync.bind({ read: () => ledger, write: (d) => { ledger = JSON.parse(JSON.stringify(d)); } });
  return { sync: window.TohidSync, calls, get ledger() { return ledger; }, state: () => window.TohidSync._state() };
}

const pushV1 = (ops) => h.api('POST', '/api/sync/v1/push', {
  token: owner.accessToken, body: { device_id: 'native-n', schema_version: 1, ops }, headers: { 'X-App': 'shop', 'X-Device': 'native-n' },
});

test('وب v1: پس از بازیابی دلتای دوباره‌فرستادهٔ برنامهٔ بومی دو بار نمی‌نشیند؛ دفتر از Snapshot؛ بالا آمدنِ عادی هیچ', async () => {
  const W = webV1(owner.accessToken, 'web-w');
  await W.sync.firstSync();
  assert.ok(W.state().epoch, 'دستگاهِ تازه دورانِ Snapshot را نگه می‌دارد');
  const ins = { op_id: oid(), ts: Date.now(), table: 'sales', row_id: 's-w1', type: 'insert', fields: { total: 300, finalTotal: 300, paidAmount: 100, remaining: 200, status: 'completed' } };
  assert.equal((await pushV1([ins])).status, 200);
  await W.sync.sync();
  const sale = () => (W.ledger.sales || []).find((s) => s.id === 's-w1');
  assert.equal(sale().paidAmount, 100);

  const TABLES = ['oplog', 'sync_rows', 'sync_devices', 'sync_conflicts'];
  const last = await backup(TABLES, 'oplog_server_seq_seq');
  const delta = { op_id: oid(), ts: Date.now(), table: 'sales', row_id: 's-w1', type: 'update', fields: { paidAmount: { $inc: 50 }, remaining: { $inc: -50 } } };
  assert.deepEqual((await pushV1([delta])).body.results.map((r) => r.status), ['applied']);
  await W.sync.sync();
  assert.equal(sale().paidAmount, 150);

  await restore(TABLES, 'oplog_server_seq_seq', last);
  //  برنامهٔ بومی همان دلتا را با همان op_id دوباره می‌فرستد (`RestoreReplay`)
  assert.deepEqual((await pushV1([delta])).body.results.map((r) => r.status), ['applied']);

  const snaps = W.calls.filter((c) => c === 'GET /api/sync/v1/snapshot').length;
  await W.sync.sync();
  assert.equal(W.state().restorePending, false);
  assert.equal(W.calls.filter((c) => c === 'GET /api/sync/v1/snapshot').length, snaps + 1, 'از Snapshot از نو');
  assert.equal(sale().paidAmount, 150, 'دلتای دوباره‌رسیده دو بار نشست (۲۰۰)');
  assert.equal(sale().remaining, 150);
  assert.equal(W.state().epoch, (await epoch.current()).id);

  //  بالا آمدنِ عادی ⇒ نه Snapshot، نه «در انتظار»
  await epoch.rotate('boot');
  await W.sync.sync();
  await W.sync.sync();
  assert.equal(W.calls.filter((c) => c === 'GET /api/sync/v1/snapshot').length, snaps + 1);
  assert.equal(W.state().restorePending, false);
  assert.equal(sale().paidAmount, 150);
});

/* -------------------------------------------------------------- وب v3 */

test('وب v3: پس از بازیابی دلتای دوباره‌فرستاده دو بار نمی‌نشیند؛ دفتر از Snapshot؛ بالا آمدنِ عادی هیچ', async () => {
  const calls = [];
  const real = V3.FetchTransport(h.base(), owner.accessToken);
  const transport = { async request(method, p, body) { calls.push(`${method} ${p.split('?')[0]}`); return real.request(method, p, body); } };
  let state = {};
  const ledger = { read: () => state, write: (d) => { state = core.cloneLedger(d); } };
  const X = new V3.Engine({ transport, store: new V3.MemoryStore(), ledger, deviceId: 'web-v3-x', random: () => 0.5 });
  X.keepLocalOnly = true;
  assert.equal((await X.sync()).ok, true);

  let tick = 0;
  const H = () => hlc.format({ wall: Date.now() + (++tick), counter: 0, node: 'native-z' });
  const push = (ops) => h.api('POST', '/api/sync/v3/push', { token: owner.accessToken, body: { device_id: 'native-z', protocol: 3, ops } });
  const ins = { op_id: oid(), hlc: H(), table: 'sales', row_id: 's-w3', kind: 'insert', fields: { totalMinor: 30000, paidAmountMinor: 10000, status: 'completed' } };
  assert.equal((await push([ins])).status, 200);
  assert.equal((await X.sync()).ok, true);
  const sale = () => (state.sales || []).find((s) => s.id === 's-w3');
  assert.equal(sale().paidAmount, 100);
  assert.ok(X.meta.epoch, 'دوران از نخستین Pull');

  const TABLES = ['sync_v3_ops', 'sync_v3_rows', 'sync_v3_devices', 'sync_v3_conflicts'];
  const last = await backup(TABLES, 'sync_v3_ops_server_seq_seq');
  const delta = { op_id: oid(), hlc: H(), table: 'sales', row_id: 's-w3', kind: 'delta', fields: { paidAmountMinor: 5000 } };
  assert.deepEqual((await push([delta])).body.results.map((r) => r.status), ['applied']);
  assert.equal((await X.sync()).ok, true);
  assert.equal(sale().paidAmount, 150);

  await restore(TABLES, 'sync_v3_ops_server_seq_seq', last);
  assert.deepEqual((await push([delta])).body.results.map((r) => r.status), ['applied'], 'برنامهٔ بومی دوباره فرستاد');

  const snaps = calls.filter((c) => c.endsWith(' /api/sync/v3/snapshot')).length;
  assert.equal((await X.sync()).ok, true);
  assert.equal(X.meta.restorePending, false);
  assert.equal(calls.filter((c) => c.endsWith(' /api/sync/v3/snapshot')).length, snaps + 1, 'از Snapshot از نو');
  assert.equal(sale().paidAmount, 150, 'دلتای دوباره‌رسیده دو بار نشست (۲۰۰)');

  await epoch.rotate('boot');
  assert.equal((await X.sync()).ok, true);
  assert.equal((await X.sync()).ok, true);
  assert.equal(calls.filter((c) => c.endsWith(' /api/sync/v3/snapshot')).length, snaps + 1, 'بالا آمدنِ عادی: Snapshot ِ دوباره نه');
  assert.equal(sale().paidAmount, 150);
});
