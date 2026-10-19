'use strict';
/**
 * حذفِ کاملِ حساب و دفترِ Sync v3 (`sync_v3_*`) — همان قاعدهٔ دفترِ v1:
 *   • دکانِ خودِ حساب ⇒ هر ردیفِ هر جدولِ `sync_v3_*` (از کاتالوگ) می‌رود
 *   • دکانِ دیگری که فقط عضوش بود ⇒ دست نمی‌خورد، جز `resolved_by`ِ خودش
 *   • دکان‌ها و حساب‌های دیگر دست‌نخورده
 *   • پل‌ها: دکانِ زنده همان cursor و ردیف‌های پل را دارد و همگام‌سازی‌اش
 *     بی تکرار ادامه می‌دهد (پلِ ۰۹۳)
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now, tx } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const userDelete = require('../src/lib/user-delete');

let superTok = '';
let n = 0;
const oid = () => `01JUD${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node, offset = 0) => hlc.format({ wall: Date.now() + offset, counter: ++tick, node });

const v3push = (token, device, ops) => h.api('POST', '/api/sync/v3/push', { token, body: { device_id: device, protocol: 3, ops } });
const v1push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop' }, body: { device_id: device, schema_version: 3, ops },
});

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: `دکانِ ${name}` } });
  const shopId = made.body?.shop?.id || made.body?.id;
  assert.ok(shopId, JSON.stringify(made.body));
  return { ...(await h.signIn(u, 'shop')), shopId };
}

/** شمارِ ردیف‌های هر جدولِ `sync_v3_*` برای یک حساب. */
async function v3Counts(accountId) {
  const tables = await tx((c) => userDelete.syncV3Tables(c));
  const out = {};
  for (const t of tables) out[t] = (await one(`SELECT COUNT(*)::int AS n FROM "${t}" WHERE account_id=$1`, [accountId])).n;
  return out;
}
const total = (o) => Object.values(o).reduce((a, b) => a + b, 0);

/** یک دکانِ زنده با همه چیزِ v3: op، ردیف، تعارضِ بسته‌شده، دستگاه، پلِ v1 ⇒ v3. */
async function fill(o, resolver = o) {
  const [X, Y] = [`${o.shopId}-x`, `${o.shopId}-y`];
  const p = `p-${o.shopId}`;
  assert.equal((await v3push(o.accessToken, X, [{ op_id: oid(), hlc: H(X), table: 'products', row_id: p, kind: 'insert', fields: { name: 'یک', salePriceMinor: 100 } }])).status, 200);
  await v3push(o.accessToken, Y, [{ op_id: oid(), hlc: H(Y), table: 'products', row_id: p, kind: 'update', fields: { name: 'دو' } }]);
  //  X «دو» را ندیده و HLCش کهنه است ⇒ تعارض در صندوق
  await v3push(o.accessToken, X, [{ op_id: oid(), hlc: H(X, -3_600_000), table: 'products', row_id: p, kind: 'update', fields: { name: 'سه' } }]);
  await v1push(o.accessToken, `${o.shopId}-v1`, [{ op_id: oid(), ts: Date.now(), table: 'debtors', row_id: `d-${o.shopId}`, type: 'insert', fields: { name: 'از v1' } }]);
  await h.api('GET', `/api/sync/v3/pull?device_id=${X}&since=0`, { token: o.accessToken });
  const list = await h.api('GET', '/api/sync/v3/conflicts', { token: resolver.accessToken });
  const cf = list.body.conflicts.find(c => c.row_id === p);
  assert.ok(cf, JSON.stringify(list.body));
  const res = await h.api('POST', `/api/sync/v3/conflicts/${cf.id}/resolve`, { token: resolver.accessToken, body: { action: 'keep' } });
  assert.equal(res.status, 200, JSON.stringify(res.body));
}

async function del(user) {
  const r = await h.api('DELETE', `/api/admin/users/${user.user.id}`, { token: superTok, body: { confirmEmail: user.user.email } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  superTok = (await h.post('/api/admin/login', { username: 'root', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

test('کاتالوگ همهٔ جدول‌های v3 را می‌بیند (۰۶۰ و پلِ ۰۹۳)', async () => {
  const tables = await tx((c) => userDelete.syncV3Tables(c));
  for (const t of ['sync_v3_ops', 'sync_v3_rows', 'sync_v3_conflicts', 'sync_v3_devices', 'sync_v3_bridge']) {
    assert.ok(tables.includes(t), t);
  }
  for (const t of ['sync_v3_v1_state', 'sync_v3_v1_out']) assert.ok(tables.includes(t), t);
  //  همهٔ جدول‌های sync_v3_ در پایگاه account_id دارند ⇒ هیچ‌کدام از قلم نمی‌افتد
  const all = (await many(`SELECT table_name AS t FROM information_schema.tables
      WHERE table_schema = current_schema() AND table_name LIKE 'sync\\_v3\\_%'`)).map(r => r.t);
  assert.deepEqual([...all].sort(), [...tables].sort());
});

test('صاحبِ دکان حذف شد ⇒ هیچ ردیفی از دکانش در هیچ جدولِ v3؛ دکان و حسابِ دیگر دست‌نخورده', async () => {
  const a = await shopOwner('حذفیِ v3');
  const b = await shopOwner('همسایهٔ v3');
  await fill(a);
  await fill(b);
  const beforeA = await v3Counts(a.shopId);
  const beforeB = await v3Counts(b.shopId);
  for (const t of ['sync_v3_ops', 'sync_v3_rows', 'sync_v3_conflicts', 'sync_v3_devices', 'sync_v3_bridge']) {
    assert.ok(beforeA[t] > 0, `پیش از حذف ${t} پر است`);
  }
  const v1B = await one(`SELECT (SELECT COUNT(*)::int FROM oplog WHERE account_id=$1) AS o,
                                (SELECT COUNT(*)::int FROM sync_rows WHERE account_id=$1) AS r`, [b.shopId]);

  const out = await del(a);
  assert.equal(out.shops, 1);
  assert.equal(out.rows.sync_v3, total(beforeA));
  assert.equal(total(await v3Counts(a.shopId)), 0, 'هیچ ردیفِ v3 برای دکانِ حذف‌شده');
  assert.equal(total(await v3Counts(a.user.id)), 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1', [a.shopId])).n, 0);

  assert.deepEqual(await v3Counts(b.shopId), beforeB, 'دکانِ همسایه دست‌نخورده');
  assert.deepEqual(await one(`SELECT (SELECT COUNT(*)::int FROM oplog WHERE account_id=$1) AS o,
                                     (SELECT COUNT(*)::int FROM sync_rows WHERE account_id=$1) AS r`, [b.shopId]), v1B);
  //  و همسایه همگام‌سازی‌اش را ادامه می‌دهد
  const r = await h.api('GET', `/api/sync/v3/pull?device_id=${b.shopId}-z&since=0`, { token: b.accessToken });
  assert.equal(r.status, 200);
  assert.ok(r.body.ops.some(o => o.row_id === `p-${b.shopId}`));
});

test('عضوِ دکانِ دیگری حذف شد ⇒ دفترِ آن دکان دست نمی‌خورد، فقط نامش از «بستنِ تعارض» پاک می‌شود', async () => {
  const b = await shopOwner('صاحبِ دکانِ عضو');
  const m0 = await h.newUser('مدیرِ عضو', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'manager','active',$4,$4)`, [newId('mem'), b.shopId, m0.user.id, now()]);
  const m = await h.signIn(m0, 'shop');
  await fill(b, m);                 //  مدیرِ عضو تعارض را بست
  await fill(b);                    //  و صاحبِ دکان یکی دیگر را
  const before = await v3Counts(b.shopId);
  const byM = (await one(`SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE account_id=$1 AND resolved_by=$2`, [b.shopId, m.user.id])).n;
  const byB = (await one(`SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE account_id=$1 AND resolved_by=$2`, [b.shopId, b.user.id])).n;
  assert.ok(byM >= 1 && byB >= 1, `${byM} ${byB}`);

  const out = await del(m);
  assert.equal(out.shops, 0, 'دکانِ کسِ دیگر');
  assert.equal(out.rows.sync_v3, 0, 'هیچ ردیفِ v3ای حذف نشد');
  assert.equal(out.rows.sync_v3_anonymized, byM);
  assert.deepEqual(await v3Counts(b.shopId), before);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE resolved_by=$1`, [m.user.id])).n, 0);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE account_id=$1 AND resolved_by=$2`, [b.shopId, b.user.id])).n, byB,
    'نامِ صاحبِ دکان می‌ماند');
  assert.ok(await one('SELECT id FROM shops WHERE id=$1', [b.shopId]));
});

test('پلِ v3 ⇒ v1 (۰۹۳): دکانِ حذف‌شده هیچ حالتی ندارد؛ دکانِ زنده با همان cursor بی تکرار ادامه می‌دهد', async () => {
  const live = require('../src/lib/live-config');
  const toV1 = require('../src/lib/sync-v3-to-v1');
  const a = await shopOwner('پلِ حذفی');
  const b = await shopOwner('پلِ زنده');
  const m0 = await h.newUser('عضوِ پل', 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'manager','active',$4,$4)`, [newId('mem'), b.shopId, m0.user.id, now()]);
  const m = await h.signIn(m0, 'shop');
  for (const o of [a, b]) await live.set(o.shopId, toV1.FLAG_KEY, true);
  await fill(a);
  await fill(b, m);
  const out = (id) => one(`SELECT (SELECT COUNT(*)::int FROM sync_v3_v1_out WHERE account_id=$1) AS out,
                                  (SELECT COUNT(*)::int FROM sync_v3_v1_state WHERE account_id=$1) AS state`, [id]);
  assert.ok((await out(a.shopId)).out > 0);
  const bBefore = await out(b.shopId);
  assert.ok(bBefore.out > 0 && bBefore.state === 1);

  await del(a);
  assert.deepEqual(await out(a.shopId), { out: 0, state: 0 });
  await del(m);                     //  عضو: دفترِ دکانِ زنده دست نمی‌خورد
  assert.deepEqual(await out(b.shopId), bBefore);

  //  سازگاری: هر ردیفِ پل هنوز به ردیفِ واقعیِ oplog می‌خورد و cursor از سرِ دفتر جلوتر نیست
  const orphan = await one(`SELECT COUNT(*)::int AS n FROM sync_v3_v1_out x
      WHERE x.account_id=$1 AND NOT EXISTS (SELECT 1 FROM oplog o WHERE o.account_id=x.account_id AND o.server_seq=x.v1_seq)`, [b.shopId]);
  assert.equal(orphan.n, 0);
  const st = await one(`SELECT v3_seq FROM sync_v3_v1_state WHERE account_id=$1`, [b.shopId]);
  const head = await one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS h FROM sync_v3_ops WHERE account_id=$1`, [b.shopId]);
  assert.ok(Number(st.v3_seq) <= Number(head.h));
  //  و همگام‌سازی بی تکرار ادامه دارد: opِ تازه یک بار به v1 می‌رسد، چیزی برنمی‌گردد
  const oplogBefore = (await one('SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1', [b.shopId])).n;
  const D = `${b.shopId}-after`;
  await v3push(b.accessToken, D, [{ op_id: oid(), hlc: H(D), table: 'products', row_id: 'p-after', kind: 'insert', fields: { name: 'پس از حذف' } }]);
  await h.api('GET', `/api/sync/v3/pull?device_id=${D}&since=0`, { token: b.accessToken });
  await h.api('GET', `/api/sync/v3/status?device_id=${D}`, { token: b.accessToken });
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1', [b.shopId])).n, oplogBefore + 1);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1 AND origin='v1' AND row_id='p-after'`, [b.shopId])).n, 0);
});
