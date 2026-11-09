'use strict';
/**
 * «هر فیلدی که دکان‌دار وارد کند کامل به سرور می‌رسد و با نصبِ دوباره برمی‌گردد» — سمتِ سرور (DATA-SAFETY §۸).
 *
 * بردارِ `core/test-vectors/ledger-sync-v1.json` دفترِ کاملاً پر است (هر فیلدِ هر مدلِ `ShopData`، تودرتو هم) و `ops`
 * همان بایت‌هایی است که برنامهٔ کاتلین روی خط می‌فرستد، با `hash` ِ خودِ کاتلین (`LedgerSyncCoverageTest` همان را
 * می‌سازد و کهنه بودنش را می‌گیرد). این‌جا:
 *   • v1: push ِ واقعی (همان زنجیرهٔ میان‌افزارهای دکان) ⇒ همه `applied`، هیچ `hash_mismatch`؛ Pull ِ دستگاهِ دیگر و
 *     Snapshot ِ دستگاهِ تازه هر ردیف را **بایت‌به‌بایت** (JSON ِ مرتب و همان اثرِ انگشت) برمی‌گردانند — هیچ فیلدی کم یا زیاد؛
 *   • شاهدِ پیش از رفع: همان op با اثرِ انگشتِ شکلِ نماییِ جاوا (`4.0E-4`، آنچه برنامه پیش از `JsNumber` می‌فرستاد)
 *     `hash_mismatch` می‌گیرد — یعنی آن ردیف هرگز به سرور نمی‌رسید؛
 *   • v3 (`SYNC_V3_ENABLED`): همان دفتر با opهای بومیِ برنامه (`v3` ِ بردار) ⇒ Snapshot ِ v3 همان `fields`، جز
 *     `correctedAt` ِ سند که فقط سرور می‌نویسد (S28b، `lib/sync-v3-time.js`).
 * روی PGlite و PostgreSQL (`TEST_DATABASE_URL`). ⛔ `lib/sync-v1.js` ِ مشترک با پمپ فقط خوانده می‌شود.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const hlc = require('../src/lib/hlc');

const VEC = JSON.parse(fs.readFileSync(path.join(__dirname, '../../core/test-vectors/ledger-sync-v1.json'), 'utf8'));
const app = { headers: { 'X-App': 'shop' } };
let n = 0;
const oid = () => `01JLSC${String(++n).padStart(20, '0')}`;
const key = (o) => `${o.table}/${o.row_id}`;

async function admin() {
  return (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
}

/** دکانِ تازه با اشتراک و پرچمِ گاوصندوق (پلهٔ ۴ ِ Sync v1) */
async function shop(name) {
  let owner = await h.newUser(`صاحبِ ${name}`);
  const id = (await h.post('/api/shop', { name }, { token: owner.accessToken })).body.shop.id;
  await h.post('/api/admin/subscriptions', { shopId: id, plan: 'm1', days: 30 }, { token: await admin() });
  await require('../src/lib/live-config').set(id, 'sync.safe_entries', true);
  owner = await h.signIn(owner, 'shop');
  return { owner, id };
}

const push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, ...app, body: { device_id: device, schema_version: 4, ops, caps: ['a00'] },
});

/** شکلِ نماییِ `Double.toString` ِ جاوا/کاتلین (همان که jshell می‌نویسد) — فقط برای شاهدِ پیش از رفع */
function javaText(x) {
  const a = Math.abs(x);
  if (Number.isInteger(x) || (a >= 1e-3 && a < 1e7)) return String(x);
  const [m, e] = x.toExponential().split('e');
  return `${m.includes('.') ? m : `${m}.0`}E${Number(e)}`;
}
function javaCanonical(v) {
  if (typeof v === 'number') return javaText(v);
  if (v === null || typeof v !== 'object') return JSON.stringify(v);
  if (Array.isArray(v)) return `[${v.map(javaCanonical).join(',')}]`;
  return `{${Object.keys(v).sort().map((k) => `${JSON.stringify(k)}:${javaCanonical(v[k])}`).join(',')}}`;
}
const javaHash = (o) => crypto.createHash('sha256')
  .update(`${o.table}|${o.row_id}|${o.type}|${javaCanonical(o.fields)}`).digest('hex');

let v1;
let v3;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
  [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  await syncV1.syncSchemaTable();
  v1 = await shop('دکانِ دفترِ کامل');
  v3 = await shop('دکانِ دفترِ کامل v3');
});
test.after(async () => { await h.stop(); });

test('بردار: هر مجموعهٔ همگام op دارد و هر op همان ردیفِ کاملِ دفتر است', () => {
  assert.ok(VEC.ops.length >= 20, 'ops ِ بردار خالی است — TOHID_WRITE_VECTORS=ledger-sync');
  const tables = new Set(VEC.ops.map((o) => o.table));
  for (const t of Object.keys(VEC.ledger)) {
    if (t in VEC.deviceOnly) { assert.ok(!tables.has(t), `${t} فقط-همین-دستگاه است ولی op دارد`); continue; }
    assert.ok(tables.has(t), `${t} op ندارد`);
  }
  for (const o of VEC.ops) {
    assert.equal(o.type, 'insert');
    const rows = VEC.ledger[o.table];
    const row = typeof rows[0] === 'string' ? { name: rows.find((x) => x === o.fields.name) } : rows.find((r) => r.id === o.row_id);
    assert.ok(row, key(o));
    const { id: _id, ...body } = row;
    assert.equal(syncV1.canonical(o.fields), syncV1.canonical(body), key(o));
  }
});

test('شاهدِ پیش از رفع: اثرِ انگشتِ شکلِ نماییِ جاوا ⇒ hash_mismatch (ردیف هرگز نمی‌رسید)', async () => {
  const hit = VEC.ops.filter((o) => javaCanonical(o.fields) !== syncV1.canonical(o.fields));
  assert.deepEqual(hit.map(key).sort(), ['products/p-full', 'purchases/pu-full', 'sales/s-full']);
  const r = await push(v1.owner.accessToken, 'cov-old', hit.map((o) => ({
    op_id: oid(), ts: Date.now(), table: o.table, row_id: o.row_id, type: o.type, fields: o.fields, hash: javaHash(o),
  })));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.status, x.reason]), hit.map(() => ['rejected', 'hash_mismatch']));
  const live = await h.api('GET', '/api/sync/v1/snapshot', { token: v1.owner.accessToken, ...app });
  assert.equal(live.body.rows, 0, 'هیچ ردیفی ننشست');
});

test('v1: push ِ کاملِ برنامه پذیرفته؛ Pull و Snapshot هر فیلد را بایت‌به‌بایت برمی‌گردانند', async () => {
  const ops = VEC.ops.map((o) => ({ op_id: oid(), ts: Date.now(), ...o }));
  const r = await push(v1.owner.accessToken, 'cov-a', ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.filter((x) => x.status !== 'applied'), [], 'همه applied');

  //  Pull ِ دستگاهِ دیگر
  const pulled = [];
  for (let since = 0, i = 0; i < 20; i++) {
    const p = await h.api('GET', `/api/sync/v1/pull?device_id=cov-b&since=${since}`, { token: v1.owner.accessToken, ...app });
    assert.equal(p.status, 200, JSON.stringify(p.body));
    pulled.push(...p.body.ops);
    since = p.body.cursor;
    if (!p.body.has_more) break;
  }
  assert.equal(pulled.length, VEC.ops.length);
  for (const o of VEC.ops) {
    const got = pulled.find((x) => key(x) === key(o));
    assert.ok(got, `Pull: ${key(o)}`);
    assert.equal(syncV1.canonical(got.fields), syncV1.canonical(o.fields), `Pull: ${key(o)}`);
    assert.equal(syncV1.hashOf(got), o.hash, `Pull hash: ${key(o)}`);
  }

  //  Snapshot ِ دستگاهِ تازه (نصبِ دوباره، ورودِ همان حساب)
  const s = await h.api('GET', '/api/sync/v1/snapshot', { token: v1.owner.accessToken, ...app });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.equal(s.body.rows, VEC.ops.length, 'هیچ ردیفِ کم یا زیاد');
  for (const o of VEC.ops) {
    const row = (s.body.tables[o.table] || []).find((x) => x.id === o.row_id);
    assert.ok(row, `Snapshot: ${key(o)}`);
    assert.equal(syncV1.canonical(row.data), syncV1.canonical(o.fields), `Snapshot: ${key(o)}`);
    assert.equal(syncV1.hashOf({ ...o, fields: row.data }), o.hash, `Snapshot hash: ${key(o)}`);
  }
});

test('v3: همان دفتر با opهای بومیِ برنامه ⇒ Snapshot ِ v3 همان fields (جز correctedAt ِ سرور)', async () => {
  assert.equal(VEC.v3.length, VEC.ops.length);
  const BASE = 1762500000000;
  const ops = VEC.v3.map((o, i) => {
    const wall = Number.isSafeInteger(o.fields.createdAt) ? o.fields.createdAt : BASE;
    return { op_id: oid(), tx: oid(), hlc: hlc.format({ wall, counter: i, node: 'cov-v3' }), ...o };
  });
  const r = await h.api('POST', '/api/sync/v3/push', {
    token: v3.owner.accessToken, body: { device_id: 'cov-v3', protocol: 3, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.filter((x) => x.status !== 'applied'), [], 'همه applied');
  const s = await h.api('GET', '/api/sync/v3/snapshot', { token: v3.owner.accessToken });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.equal(s.body.rows, VEC.v3.length);
  for (const o of VEC.v3) {
    const row = (s.body.tables[o.table] || []).find((x) => x.id === o.row_id);
    assert.ok(row, `v3 Snapshot: ${key(o)}`);
    const { correctedAt: mine, ...want } = o.fields;
    const { correctedAt: srv, ...got } = row.data;
    assert.equal(syncV1.canonical(got), syncV1.canonical(want), `v3 Snapshot: ${key(o)}`);
    //  S28b: `correctedAt` ِ سند فقط از سرور؛ ساعتِ HLC همان `createdAt` ⇒ تصحیحی نیست و نوشتهٔ دستگاه کنار می‌رود
    if (mine !== undefined) assert.equal(srv, undefined, key(o));
  }
});
