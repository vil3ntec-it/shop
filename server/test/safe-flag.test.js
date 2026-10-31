'use strict';
/**
 * پرچمِ گاوصندوق (PAY-18): `live_config` ِ `sync.safe_entries` — `src/lib/safe-flag.js`.
 *   • خاموش (پیش‌فرض) ⇒ سرور همان main: پلهٔ ۳، `safeEntries` در v1 و v3 «جدولِ ناشناخته»،
 *     برنامهٔ پلهٔ ۴ ۴۲۶، راهِ قدیم بی‌صدا، `/status` و `/hello` بی این جدول؛
 *   • مقدارِ خودِ دکان بر `all` جلو می‌افتد و فقط `true` روشن است؛
 *   • روشن ⇒ پلهٔ ۴ و نگهبانِ `safe-ledger.js` (آزمون‌های خودش: `safe-ledger.test.js`).
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const live = require('../src/lib/live-config');
const flag = require('../src/lib/safe-flag');
const migrations = require('../src/lib/sync-v1-migrations');

let shopId;
let owner;
let n = 0;
const oid = () => `01JSFLG${String(++n).padStart(19, '0')}`;
const H = () => hlc.format({ wall: Date.now() + (++n), counter: 0, node: 'flag-v3' });
const auth = () => ({ token: owner.accessToken, headers: { 'X-App': 'shop' } });

const safeRow = (id) => ({ id, kind: 'count', amountMinor: 1000, createdAt: Date.now(), expectedMinor: 0 });
const push1 = (schema, ops) => h.api('POST', '/api/sync/v1/push', {
  ...auth(), body: { device_id: 'flag-dev', schema_version: schema, ops },
});
const ins1 = (table, id, fields) => ({ op_id: oid(), ts: Date.now(), table, row_id: id, type: 'insert', fields });
const stored = (id) => one(
  `SELECT 1 AS x FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
      AND table_name='safeEntries' AND row_id=$2`, [shopId, id]
);
const setFlag = (scope, value) => live.set(scope, flag.FLAG, value);
const statusOf = async () => {
  const r = await h.api('GET', '/api/sync/v1/status?device_id=flag-dev', auth());
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
};

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ پرچم', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ پرچم' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(o, 'shop');
});
test.after(async () => { await h.stop(); });

test('پله: بی پرچم همان main (۳)؛ پمپ هرگز عوض نمی‌شود', () => {
  assert.equal(migrations.CURRENT.shop, 3, 'app_schema همان main');
  assert.equal(migrations.currentVersion('shop'), 3);
  assert.equal(migrations.currentVersion('shop', { safeEntries: false }), 3);
  assert.equal(migrations.currentVersion('shop', { safeEntries: 'true' }), 3, 'فقط true ِ بولی');
  assert.equal(migrations.currentVersion('shop', { safeEntries: true }), 4);
  assert.equal(migrations.currentVersion('pump', { safeEntries: true }), 1);
  assert.ok(!flag.allowed('safeEntries', { safeEntries: false }) && flag.allowed('safeEntries', { safeEntries: true }));
  assert.ok(flag.allowed('products', null), 'جدولِ بیرون از پرچم همیشه');
  const all = syncV1.tablesOf('shop');
  const shopCtx = (on) => ({ app: 'shop', accountKind: 'shop', accountId: 's', safeEntries: on });
  assert.ok(all.includes('safeEntries'), 'فهرستِ سفیدِ v1 همان main');
  assert.deepEqual(flag.tablesFor(shopCtx(false), all), all.filter((t) => t !== 'safeEntries'));
  assert.deepEqual(flag.tablesFor(shopCtx(true), all), all);
  const pump = syncV1.tablesOf('pump');
  assert.equal(flag.tablesFor({ app: 'pump', accountKind: 'station', accountId: 'p' }, pump), pump, 'پمپ همان فهرست');
});

test('`lib/sync-v1.js` ِ مشترک با پمپ دست نمی‌خورد: پله از `ctx` ِ درخواستِ دکان (‎safe-flag.within‎)', async () => {
  //  قاعدهٔ «sync-v1.js، PUMP_TABLES و /api/pump/* دست نمی‌خورند» (RULES، بخشِ همگام‌سازی)
  const src = require('fs').readFileSync(require.resolve('../src/lib/sync-v1'), 'utf8');
  assert.ok(!src.includes('safe-flag') && !src.includes('safeEntries'), 'sync-v1.js پرچمِ گاوصندوق را نمی‌شناسد');

  const shop = (on) => ({ app: 'shop', accountKind: 'shop', accountId: 'scope-shop', safeEntries: on });
  const inScope = (sync, fn) => new Promise((resolve, reject) => {
    flag.within({ sync }, null, () => Promise.resolve().then(fn).then(resolve, reject));
  });
  assert.equal(await inScope(shop(true), () => migrations.currentVersion('shop')), 4);
  assert.equal(await inScope(shop(false), () => migrations.currentVersion('shop')), 3);
  //  پس از await (پرس‌وجو، تراکنش) هم همان درخواست است
  assert.equal(await inScope(shop(true), async () => {
    await new Promise((r) => setImmediate(r));
    return migrations.upgrade('shop', 3, { table: 'products' }) && migrations.currentVersion('shop');
  }), 4);
  assert.equal(await inScope(shop(true), () => migrations.currentVersion('shop', { safeEntries: false })), 3, 'ctx ِ صریح جلو می‌افتد');
  assert.equal(await inScope({ app: 'pump', accountKind: 'station', accountId: 'p1', safeEntries: true },
    () => migrations.currentVersion('shop')), 3, 'پمپ زیرِ ظرف نمی‌رود');
  assert.equal(await inScope(shop(true), () => migrations.currentVersion('pump')), 1, 'پمپ هرگز عوض نمی‌شود');
  assert.equal(migrations.currentVersion('shop'), 3, 'بیرون از درخواست هیچ');
});

test('خاموش (پیش‌فرض): v1 ِ پلهٔ ۳، برنامهٔ پلهٔ ۴ ۴۲۶، و opِ گاوصندوق «جدولِ ناشناخته»', async () => {
  assert.equal(await flag.enabledFor(shopId), false);
  const st = await statusOf();
  assert.equal(st.schema_version, 3);
  assert.ok(!st.tables.includes('safeEntries') && st.tables.includes('products'));

  const ahead = await push1(4, [ins1('products', 'p-ahead', { name: 'چای' })]);
  assert.equal(ahead.status, 426, JSON.stringify(ahead.body));

  const r = await push1(3, [
    ins1('products', 'p-1', { name: 'قند' }),
    ins1('safeEntries', 'off-1', safeRow('off-1')),
    { ...ins1('safeEntries', 'off-2', safeRow('off-2')), op_id: 'bad id!' },
    ins1('products', 'p-2', { name: 'نمک' }),
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.schema_version, 3);
  assert.deepEqual(r.body.results.map((x) => x.status === 'rejected' ? `rejected:${x.reason}` : x.status),
    ['applied', 'rejected:unknown_table', 'rejected:bad_op_id', 'applied']);
  assert.equal(await stored('off-1'), null);

  const pulled = await h.api('GET', '/api/sync/v1/pull?device_id=flag-dev&since=0', auth());
  assert.equal(pulled.status, 200, JSON.stringify(pulled.body));
  assert.equal(pulled.body.schema_version, 3);
  const snap = await h.api('GET', '/api/sync/v1/snapshot', auth());
  assert.equal(snap.status, 200, JSON.stringify(snap.body));
  assert.equal(snap.body.schema_version, 3);
});

test('خاموش: v3 بی این جدول — /hello، opِ بومی و قالبِ v2', async () => {
  const hello = await h.api('GET', '/api/sync/v3/hello', auth());
  assert.equal(hello.status, 200, JSON.stringify(hello.body));
  assert.equal(hello.body.accepts.v2_schema_max, 3);
  assert.ok(!('safeEntries' in hello.body.tables) && 'products' in hello.body.tables);

  const v3 = await h.api('POST', '/api/sync/v3/push', {
    ...auth(),
    body: { device_id: 'flag-v3', protocol: 3, ops: [{ op_id: oid(), hlc: H(), table: 'safeEntries', row_id: 'v3-off', kind: 'insert', fields: safeRow('v3-off') }] },
  });
  assert.equal(v3.status, 200, JSON.stringify(v3.body));
  assert.equal(v3.body.results[0].status, 'rejected');
  assert.equal(v3.body.results[0].reason, 'unknown_table');

  const v2 = await h.api('POST', '/api/sync/v3/push', {
    ...auth(),
    body: { device_id: 'flag-v3', protocol: 3, format: 'v2', schema_version: 3, ops: [ins1('safeEntries', 'v2-off', safeRow('v2-off'))] },
  });
  assert.equal(v2.status, 200, JSON.stringify(v2.body));
  assert.equal(v2.body.results[0].reason, 'unknown_table');
  const ahead = await h.api('POST', '/api/sync/v3/push', {
    ...auth(), body: { device_id: 'flag-v3', protocol: 3, format: 'v2', schema_version: 4, ops: [] },
  });
  assert.equal(ahead.status, 426, JSON.stringify(ahead.body));
  assert.equal(await one(
    `SELECT 1 AS x FROM sync_v3_rows WHERE account_id=$1 AND table_name='safeEntries'`, [shopId]
  ), null);
});

test('خاموش: راهِ قدیم مثلِ main بی‌صدا کنار می‌گذارد (بی sync_v1_only)', async () => {
  const r = await h.api('POST', '/api/sync', {
    ...auth(),
    body: {
      deviceId: 'flag-legacy',
      changes: [
        { collection: 'safeEntries', id: 'leg-off', updatedAt: Date.now(), data: safeRow('leg-off') },
        { collection: 'products', id: 'leg-p', updatedAt: Date.now(), data: { name: 'برنج' } },
      ],
    },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.applied, 1);
  assert.equal(r.body.skipped, 1);
  assert.deepEqual(r.body.conflicts, []);
});

test('مقدارِ دکان بر `all` جلو می‌افتد؛ فقط true روشن است؛ روشن ⇒ پلهٔ ۴', async () => {
  await setFlag('all', true);
  assert.equal(await flag.enabledFor(shopId), true);
  assert.equal(await flag.enabledFor('shop-other'), true);
  await setFlag(shopId, false);
  assert.equal(await flag.enabledFor(shopId), false, 'خاموشِ دکان بر all ِ روشن');
  assert.equal((await statusOf()).schema_version, 3);
  await setFlag('all', false);
  await setFlag(shopId, 'true');
  assert.equal(await flag.enabledFor(shopId), false, 'متنِ "true" روشن نیست');
  await setFlag(shopId, true);
  assert.equal(await flag.enabledFor(shopId), true);
  assert.equal(await flag.enabledFor('shop-other'), false);

  const st = await statusOf();
  assert.equal(st.schema_version, 4);
  assert.ok(st.tables.includes('safeEntries'));
  for (const path of ['/api/sync/v1/pull?device_id=flag-dev&since=0', '/api/sync/v1/snapshot']) {
    const r = await h.api('GET', path, auth());
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.schema_version, 4, path);
  }
  const hello = await h.api('GET', '/api/sync/v3/hello', auth());
  assert.equal(hello.body.accepts.v2_schema_max, 4);
  assert.ok('safeEntries' in hello.body.tables);

  //  برنامهٔ پلهٔ ۳ (یا ۴ِ مذاکره‌کرده پیش از دیدنِ پله) هم می‌نشاند
  for (const [schema, id] of [[4, 'on-4'], [3, 'on-3']]) {
    const r = await push1(schema, [ins1('safeEntries', id, safeRow(id))]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
    assert.ok(await stored(id), id);
  }
  const leg = await h.api('POST', '/api/sync', {
    ...auth(),
    body: { deviceId: 'flag-legacy', changes: [{ collection: 'safeEntries', id: 'leg-on', updatedAt: Date.now(), data: safeRow('leg-on') }] },
  });
  assert.deepEqual(leg.body.conflicts.map((c) => c.reason), ['sync_v1_only']);
  await setFlag(shopId, false);
});
