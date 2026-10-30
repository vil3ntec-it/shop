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
process.env.SYNC_EPOCH_ENABLED = '1';

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
  const quota = { full: false };
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
    localStorage: {
      getItem: (k) => (store.has(k) ? store.get(k) : null),
      //  «حافظهٔ مرورگر پر است»: نوشتنِ صف و سایه شکست می‌خورد، دفتر نه
      setItem: (k, v) => { if (quota.full && /oplog|shadow/.test(k)) throw new Error('QuotaExceededError'); store.set(k, String(v)); },
      removeItem: (k) => store.delete(k),
    },
    navigator: { onLine: true },
    document: { addEventListener: () => {}, hidden: false },
    CustomEvent: class { constructor(type, init) { this.type = type; this.detail = init && init.detail; } },
    //  زمان‌سنج‌های موتور (Pushِ پس از ثبت، تلاشِ دوباره، تیک) این‌جا هرگز نمی‌دوند: آزمون خودش
    //  `sync()` را صدا می‌زند. زمان‌سنجِ واقعی پس از بسته شدنِ سرور در حلقهٔ «تلاشِ دوباره» می‌ماند
    //  و فرایندِ آزمون هرگز تمام نمی‌شد (CI ِ PGlite/Electron تا مهلت ایستاد).
    setTimeout: () => 0, clearTimeout: () => {}, setInterval: () => 0, clearInterval: () => {}, console, URL,
  };
  vm.runInNewContext(ENGINE, sandbox, { filename: 'sync-engine.js' });
  let ledger = {};
  window.TohidSync.bind({ read: () => ledger, write: (d) => { ledger = JSON.parse(JSON.stringify(d)); } });
  return {
    sync: window.TohidSync, calls, quota, get ledger() { return ledger; }, state: () => window.TohidSync._state(),
    /** ذخیرهٔ برنامه: دفتر ⇐ `recordChange` */
    edit(fn) { ledger = fn(JSON.parse(JSON.stringify(ledger))); return window.TohidSync.recordChange('edit'); },
  };
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

test('وب v1: فروشِ پس از پشتیبانِ همین مرورگر پس از بازیابی می‌ماند و «درج» می‌رود؛ حذف‌شدهٔ سرور زنده نمی‌شود؛ ثبت‌نشده زیرِ Snapshot نمی‌رود', async () => {
  //  بازبینِ دوم، B1(c): پیش‌تر دفتر همان Snapshot می‌شد و فروشِ خودِ مرورگر پاک
  const W = webV1(owner.accessToken, 'web-w2');
  await W.sync.firstSync();
  const ins = (row, fields) => ({ op_id: oid(), ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
  assert.equal((await pushV1([ins('p-w2-gone', { name: 'رفتنی' }), ins('p-w2-keep', { name: 'ماندنی', salePrice: 10 })])).status, 200);
  await W.sync.sync();
  assert.ok(W.ledger.products.some((p) => p.id === 'p-w2-gone'));

  const TABLES = ['oplog', 'sync_rows', 'sync_devices', 'sync_conflicts'];
  const last = await backup(TABLES, 'oplog_server_seq_seq');
  assert.equal(W.edit((d) => ({ ...d, sales: [...(d.sales || []), { id: 's-w2-late', total: 70, finalTotal: 70, paidAmount: 70, remaining: 0, status: 'completed' }] })), 1);
  await W.sync.sync();
  assert.equal(W.state().lastError || '', '');
  assert.ok(await one("SELECT 1 AS x FROM sync_rows WHERE row_id='s-w2-late' AND deleted_at IS NULL"));

  await restore(TABLES, 'oplog_server_seq_seq', last);
  assert.equal(await one("SELECT 1 AS x FROM sync_rows WHERE row_id='s-w2-late'"), null, 'سرورِ برگشته فروش را ندارد');
  //  پس از بازیابی برنامهٔ دیگری کالایی را پاک می‌کند که این مرورگر هنوز دارد
  assert.equal((await pushV1([{ op_id: oid(), ts: Date.now(), table: 'products', row_id: 'p-w2-gone', type: 'delete' }])).status, 200);

  //  حافظهٔ پر: ویرایشِ قیمت در دفتر نشست ولی op نشد ⇒ ساختن نه، «در انتظار» می‌ماند
  W.quota.full = true;
  assert.equal(W.edit((d) => ({ ...d, products: d.products.map((p) => (p.id === 'p-w2-keep' ? { ...p, salePrice: 12 } : p)) })), 0);
  await W.sync.sync();
  assert.equal(W.state().restorePending, true, 'تغییرِ ثبت‌نشده زیرِ Snapshot رفت');
  assert.equal(W.ledger.products.find((p) => p.id === 'p-w2-keep').salePrice, 12);
  W.quota.full = false;
  await W.sync.sync();
  await W.sync.sync();

  assert.equal(W.state().restorePending, false);
  assert.ok(W.ledger.sales.some((x) => x.id === 's-w2-late'), 'فروشِ پس از پشتیبانِ مرورگر پاک شد');
  assert.ok(await one("SELECT 1 AS x FROM sync_rows WHERE row_id='s-w2-late' AND deleted_at IS NULL"), 'فروش دوباره «درج» نرفت');
  assert.equal(W.ledger.products.some((p) => p.id === 'p-w2-gone'), false, 'کالای حذف‌شده روی مرورگر ماند');
  assert.ok(await one("SELECT 1 AS x FROM sync_rows WHERE row_id='p-w2-gone' AND deleted_at IS NOT NULL"), 'کالای حذف‌شده زنده شد');
  const keep = await one("SELECT data FROM sync_rows WHERE row_id='p-w2-keep'");
  assert.equal(Number(keep.data.salePrice), 12, 'ویرایشِ ثبت‌نشده گم شد');
  assert.equal(W.sync.queueSize(), 0);
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

test('وب v3: فروشِ پس از پشتیبانِ همین دستگاه پس از بازیابی می‌ماند و «درج» می‌رود؛ حذف‌شدهٔ سرور زنده نمی‌شود', async () => {
  //  بازبینِ دوم، B1(c) برای v3
  const real = V3.FetchTransport(h.base(), owner.accessToken);
  const calls = [];
  const transport = { async request(method, p, body) { calls.push(`${method} ${p.split('?')[0]}`); return real.request(method, p, body); } };
  let state = {};
  const ledger = { read: () => state, write: (d) => { state = core.cloneLedger(d); } };
  const Y = new V3.Engine({ transport, store: new V3.MemoryStore(), ledger, deviceId: 'web-v3-y', random: () => 0.5 });
  Y.keepLocalOnly = true;
  assert.equal((await Y.sync()).ok, true);
  let tick = 0;
  const H = () => hlc.format({ wall: Date.now() + 10_000 + (++tick), counter: 0, node: 'native-q' });
  const push = (ops) => h.api('POST', '/api/sync/v3/push', { token: owner.accessToken, body: { device_id: 'native-q', protocol: 3, ops } });
  assert.equal((await push([{ op_id: oid(), hlc: H(), table: 'products', row_id: 'p-y-gone', kind: 'insert', fields: { name: 'رفتنی' } }])).status, 200);
  assert.equal((await Y.sync()).ok, true);
  assert.ok((state.products || []).some((p) => p.id === 'p-y-gone'));

  const TABLES = ['sync_v3_ops', 'sync_v3_rows', 'sync_v3_devices', 'sync_v3_conflicts'];
  const last = await backup(TABLES, 'sync_v3_ops_server_seq_seq');
  state = { ...state, sales: [...(state.sales || []), { id: 's-y-late', total: 80, finalTotal: 80, paidAmount: 80, remaining: 0, status: 'completed' }] };
  assert.equal((await Y.sync()).ok, true);
  assert.ok(await one("SELECT 1 AS x FROM sync_v3_rows WHERE row_id='s-y-late' AND deleted_at IS NULL"));

  await restore(TABLES, 'sync_v3_ops_server_seq_seq', last);
  assert.equal(await one("SELECT 1 AS x FROM sync_v3_rows WHERE row_id='s-y-late'"), null);
  assert.equal((await push([{ op_id: oid(), hlc: H(), table: 'products', row_id: 'p-y-gone', kind: 'delete', fields: {} }])).status, 200);

  assert.equal((await Y.sync()).ok, true);
  assert.equal((await Y.sync()).ok, true);
  assert.equal(Y.meta.restorePending, false);
  assert.ok(calls.includes('POST /api/sync/v3/deleted'), 'از سرور پرسیده نشد');
  assert.ok(state.sales.some((x) => x.id === 's-y-late'), 'فروشِ پس از پشتیبانِ دستگاه پاک شد');
  assert.ok(await one("SELECT 1 AS x FROM sync_v3_rows WHERE row_id='s-y-late' AND deleted_at IS NULL"), 'فروش دوباره «درج» نرفت');
  assert.equal(state.products.some((p) => p.id === 'p-y-gone'), false, 'کالای حذف‌شده روی دستگاه ماند');
  assert.ok(await one("SELECT 1 AS x FROM sync_v3_rows WHERE row_id='p-y-gone' AND deleted_at IS NOT NULL"), 'کالای حذف‌شده زنده شد');
  assert.equal(Y.outbox.length, 0);
});
