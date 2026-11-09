'use strict';
/**
 * بازفرستِ opهای `hash_mismatch` ِ عددیِ ساخت‌های پیشین (DATA-SAFETY §۷، `sync/v1/DroppedResend.kt`) — سمتِ سرور.
 *
 * بردارِ `core/test-vectors/resend-dropped-v1.json` را `DroppedResendTest` ِ برنامه می‌نویسد: `dropped` دفترِ ردشدهٔ یک
 * دستگاه با بایت‌های ساختِ پیشین (نما: `4.0E-4`، `5.684341886080802E-14`، `1.23456785E7`؛ رقمِ اضافهٔ جاوا ۱۷ و اندروید:
 * `0.0020`، `5.6843418860808015E-14`؛ و اثرِ انگشتِ کاتلین روی همان)،
 * `accepted` آنچه سرور پیش‌تر پذیرفته بود (درجِ کالای `p-edit`، ویرایشِ بعدیِ فروشِ نیمهٔ `s-half`)، `resend` همان
 * opهایی که برنامه با همان op_id دوباره جلوی صف می‌گذارد و `rows` دفترِ دستگاه. این‌جا روی push ِ واقعیِ `/sync/v1`
 * (همان زنجیرهٔ میان‌افزارهای دکان):
 *   • شاهد: همان بایت‌های کهنه ⇒ `hash_mismatch`؛
 *   • بازفرست ⇒ همه دقیقاً یک بار `applied` (یک ردیف در `oplog` برای هر op_id)، اثرِ انگشت = `hashOf` ِ خودِ سرور؛
 *   • Pull ِ دستگاهِ دیگر و Snapshot ِ دستگاهِ تازه هر ردیف را برمی‌گردانند و ردیفِ سرور = دفترِ دستگاه، فیلدبه‌فیلد
 *     (فروشِ نیمه با `seller` ِ تازه‌ترِ سرور و شمارنده‌های درست؛ قیمتِ ویرایش‌شدهٔ کالای موجود)؛
 *   • بازفرستِ دوباره (برنامه پیش از «پذیرفته» بسته شد) ⇒ همه `duplicate` و هیچ تغییری؛
 *   • ویرایشِ تازه‌ترِ دستگاهِ دیگر پس از آن با بازفرستِ سوم بازنویسی نمی‌شود و هیچ تعارضی ساخته نمی‌شود.
 * روی PGlite و PostgreSQL (`TEST_DATABASE_URL`). ⛔ `lib/sync-v1.js` ِ مشترک با پمپ فقط خوانده می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const RAW = fs.readFileSync(path.join(__dirname, '../../core/test-vectors/resend-dropped-v1.json'), 'utf8');
const VEC = JSON.parse(RAW);
const app = { headers: { 'X-App': 'shop' } };
const key = (o) => `${o.table}/${o.row_id}`;
const canon = (v) => syncV1.canonical(v);

let owner;

async function admin() {
  return (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
}

const push = (device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: owner.accessToken, ...app, body: { device_id: device, schema_version: 4, ops, caps: ['a00'] },
});

async function snapshotRows() {
  const s = await h.api('GET', '/api/sync/v1/snapshot', { token: owner.accessToken, ...app });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const out = {};
  for (const [t, rows] of Object.entries(s.body.tables)) for (const r of rows) out[`${t}/${r.id}`] = r.data;
  return out;
}

const oplogCount = async (ids) => Number((await query(
  'SELECT COUNT(*)::int AS n FROM oplog WHERE op_id = ANY($1::text[])', [ids]
)).rows[0].n);

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
  [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ بازفرست');
  const id = (await h.post('/api/shop', { name: 'دکانِ بازفرست' }, { token: owner.accessToken })).body.shop.id;
  await h.post('/api/admin/subscriptions', { shopId: id, plan: 'm1', days: 30 }, { token: await admin() });
  //  schema ِ ۴ ِ برنامه (پلهٔ گاوصندوق) — همان پرچمِ `sync-ledger-coverage.test.js`؛ بی آن push ۴۲۶ می‌گیرد
  await require('../src/lib/live-config').set(id, 'sync.safe_entries', true);
  owner = await h.signIn(owner, 'shop');
});
test.after(async () => { await h.stop(); });

test('بردار: بازفرست همان op_id/ts ِ دفترِ ردشده و اثرِ انگشتِ خودِ سرور', () => {
  const dropped = new Map(VEC.dropped.filter((e) => e.reason === 'hash_mismatch').map((e) => [e.op.op_id, e.op]));
  assert.equal(VEC.resend.length, 7);
  for (const o of VEC.resend) {
    const old = dropped.get(o.op_id);
    assert.ok(old, `${o.op_id} در دفترِ ردشده نیست`);
    assert.equal(o.ts, old.ts, o.op_id);
    assert.equal(key(o), key(old), o.op_id);
    assert.equal(syncV1.hashOf(o), o.hash, `${o.op_id}: اثرِ انگشتِ سرور`);
    assert.notEqual(syncV1.hashOf(old), old.hash, `${o.op_id}: بایت‌های کهنه باید ناجور باشند`);
  }
  //  ردهای دلیلِ دیگر هرگز در بازفرست نیستند
  const others = VEC.dropped.filter((e) => e.reason !== 'hash_mismatch').map((e) => e.op.op_id);
  assert.ok(others.length >= 5);
  assert.deepEqual(VEC.resend.filter((o) => others.includes(o.op_id)), []);
});

test('بردار: رقمِ اضافهٔ جاوا ۱۷ و اندروید (بی نما هم) در دفترِ ردشده، و بازفرستش با رقم‌های String(x)', () => {
  //  JSON.parse شکلِ متن را می‌اندازد ⇒ همان بایت‌های پرونده
  assert.ok(RAW.includes('"purchasePrice":0.0020'), 'p-art: 0.0020');
  assert.ok(RAW.includes('"discountValue":5.6843418860808015E-14'), 's-art: 5.6843418860808015E-14');
  const by = new Map(VEC.resend.map((o) => [key(o), o]));
  assert.equal(by.get('products/p-art').fields.purchasePrice, 0.002);
  assert.equal(String(by.get('products/p-art').fields.purchasePrice), '0.002');
  assert.equal(String(by.get('sales/s-art').fields.discountValue), '5.684341886080802e-14');
  for (const k of ['products/p-art', 'sales/s-art']) assert.equal(by.get(k).type, 'insert', k);
});

test('پیش از رفع: بایت‌های کهنه ⇒ hash_mismatch و هیچ ردیفی', async () => {
  const before = await push('dev-a', VEC.accepted);
  assert.equal(before.status, 200, JSON.stringify(before.body));
  assert.deepEqual(before.body.results.map((x) => x.status), VEC.accepted.map(() => 'applied'));
  const ids = new Set(VEC.resend.map((o) => o.op_id));
  const seen = new Set();
  const old = VEC.dropped.filter((e) => e.reason === 'hash_mismatch' && ids.has(e.op.op_id) && !seen.has(e.op.op_id) && seen.add(e.op.op_id))
    .map((e) => e.op);
  assert.equal(old.length, ids.size);
  const r = await push('dev-a', old);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.op_id, x.status, x.reason]), old.map((o) => [o.op_id, 'rejected', 'hash_mismatch']));
  assert.equal(await oplogCount([...ids]), 0, 'hash_mismatch در oplog چیزی نمی‌نویسد ⇒ op_id آزاد');
});

test('بازفرست ⇒ یک بار applied؛ Pull و Snapshot ردیف را برمی‌گردانند و ردیف = دفترِ دستگاه', async () => {
  const r = await push('dev-a', VEC.resend);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.op_id, x.status]), VEC.resend.map((o) => [o.op_id, 'applied']));
  assert.equal(await oplogCount(VEC.resend.map((o) => o.op_id)), VEC.resend.length, 'هر op_id یک ردیف');

  //  Pull ِ دستگاهِ دیگر
  const pulled = [];
  for (let since = 0, i = 0; i < 20; i++) {
    const p = await h.api('GET', `/api/sync/v1/pull?device_id=dev-b&since=${since}`, { token: owner.accessToken, ...app });
    assert.equal(p.status, 200, JSON.stringify(p.body));
    pulled.push(...p.body.ops);
    since = p.body.cursor;
    if (!p.body.has_more) break;
  }
  for (const o of VEC.resend) {
    const got = pulled.filter((x) => x.op_id === o.op_id);
    assert.equal(got.length, 1, `Pull: ${o.op_id}`);
    assert.equal(canon(got[0].fields), canon(o.fields), `Pull: ${o.op_id}`);
    assert.equal(syncV1.hashOf(got[0]), o.hash, `Pull hash: ${o.op_id}`);
  }

  //  Snapshot ِ دستگاهِ تازه: هر ردیف همان دفترِ دستگاه
  const rows = await snapshotRows();
  for (const [k, want] of Object.entries(VEC.rows)) {
    assert.ok(rows[k], `Snapshot: ${k}`);
    assert.equal(canon(rows[k]), canon(want), `Snapshot: ${k}`);
  }
  assert.equal(Object.keys(rows).length, Object.keys(VEC.rows).length, 'هیچ ردیفِ دوتایی یا اضافه');
  assert.equal(rows['sales/s-half'].seller, 'u-owner', 'seller ِ تازه‌ترِ سرور دست نخورد');
});

test('بازفرستِ دوباره ⇒ duplicate و هیچ تغییری؛ ویرایشِ تازه‌ترِ دستگاهِ دیگر بازنویسی نمی‌شود', async () => {
  const before = await snapshotRows();
  const again = await push('dev-a', VEC.resend);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.deepEqual(again.body.results.map((x) => [x.op_id, x.status]), VEC.resend.map((o) => [o.op_id, 'duplicate']));
  assert.deepEqual(await snapshotRows(), before);
  assert.equal(await oplogCount(VEC.resend.map((o) => o.op_id)), VEC.resend.length);

  //  دستگاهِ دیگر پس از آن نام و قیمتِ کالا را عوض می‌کند
  const fields = { name: 'نامِ دستگاهِ دیگر', purchasePrice: 0.75 };
  const edit = { op_id: '01JRSNDB0000000000000B0001', ts: Date.now(), table: 'products', row_id: 'p-edit', type: 'update', fields };
  const e = await push('dev-b', [{ ...edit, hash: syncV1.hashOf(edit) }]);
  assert.deepEqual(e.body.results.map((x) => x.status), ['applied']);

  //  برنامهٔ دستگاهِ نخست باز پیش از «پذیرفته» بسته شده بود ⇒ همان opها دوباره
  const third = await push('dev-a', VEC.resend);
  assert.deepEqual(third.body.results.map((x) => x.status), VEC.resend.map(() => 'duplicate'));
  const rows = await snapshotRows();
  assert.equal(rows['products/p-edit'].name, 'نامِ دستگاهِ دیگر');
  assert.equal(rows['products/p-edit'].purchasePrice, 0.75);
  const conflicts = await query('SELECT COUNT(*)::int AS n FROM sync_conflicts');
  assert.equal(Number(conflicts.rows[0].n), 0, 'هیچ تعارضی');
});
