'use strict';
/**
 * زنجیرهٔ هش روی دیتابیس — migration 070، lib/hash-chain-store.js، audit.js
 * و scripts/verify-chain.js (STF-09، SEC-02، SEC-03 سمتِ سرور).
 *
 * آن‌چه این‌جا قفل می‌شود:
 *   ۱) پیش‌فرض خاموش: سابقهٔ عملیات هیچ حلقه‌ای نمی‌سازد.
 *   ۲) با `security / hashchain.audit` = true (همان درِ پنلِ تنظیماتِ زنده)
 *      هر کاربر زنجیرهٔ خودش را دارد و سنجش سالم است.
 *   ۳) جدول فقط افزودنی است: UPDATE، DELETE، TRUNCATE، شکاف و پیوندِ غلط رد.
 *   ۴) دست‌کاری (حتی با خاموش کردنِ تریگر) و دست‌کاریِ `audit_logs` را
 *      سنجش با شمارهٔ **نخستین** حلقهٔ شکسته می‌گوید؛ ابزارِ CLI کدِ ۲ می‌دهد.
 *   ۵) نوشتنِ هم‌زمان روی یک زنجیره انشعاب نمی‌سازد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const audit = require('../src/lib/audit');
const store = require('../src/lib/hash-chain-store');
const live = require('../src/lib/live-config');
const cli = require('../scripts/verify-chain');

const SHOP = 'shp_hc_test';
const chainOf = (userId) => store.auditChainId({ shopId: SHOP, actorType: 'user', userId });
const count = async (chain) => Number((await query('SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain=$1', [chain])).rows[0].n);

async function flag(on) {
  await live.set(store.FLAG_SCOPE, store.FLAG_AUDIT, on, 'test');
  store._forget();
}

async function tamper(sql, params) {
  await query('ALTER TABLE hash_chain DISABLE TRIGGER hash_chain_immutable');
  try { await query(sql, params); } finally {
    await query('ALTER TABLE hash_chain ENABLE TRIGGER hash_chain_immutable');
  }
}

async function runCli(args) {
  const lines = [];
  const code = await cli.main(args, (s) => lines.push(s));
  return { code, out: lines.join('\n') };
}

test.before(async () => { await h.start(); store._forget(); });
test.after(async () => { await h.stop(); });

test('۱) پیش‌فرض خاموش: سابقه نوشته می‌شود، زنجیره نه', async () => {
  assert.equal(await store.auditEnabled(), false);
  const id = await audit.log({ shopId: SHOP, userId: 'u_off', action: 'sale.create', detail: { total: 5 } });
  const row = (await query('SELECT action FROM audit_logs WHERE id=$1', [id])).rows[0];
  assert.equal(row.action, 'sale.create');
  assert.equal(Number((await query('SELECT COUNT(*)::int AS n FROM hash_chain')).rows[0].n), 0);
  const r = await runCli([]);
  assert.equal(r.code, 0);
  assert.match(r.out, /زنجیره‌ای نیست/);
});

test('۲) پرچم از درِ پنل روشن می‌شود و هر کاربر زنجیرهٔ خودش را دارد', async () => {
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'hcadmin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const login = await h.post('/api/admin/login', { username: 'hcadmin', password: 'Admin!12345' });
  assert.equal(login.status, 200, JSON.stringify(login.body));
  const put = await h.put('/api/admin/pump/live-config',
    { scope: store.FLAG_SCOPE, key: store.FLAG_AUDIT, value: true }, { token: login.body.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  store._forget();
  assert.equal(await store.auditEnabled(), true);

  const ids = [];
  ids.push(await audit.log({ shopId: SHOP, userId: 'u_a', action: 'sale.create', detail: { total: 22000, at: new Date(0), nested: { b: 1, a: [1, 'x'] } }, ip: '10.0.0.1' }));
  ids.push(await audit.log({ shopId: SHOP, userId: 'u_a', action: 'sale.void', detail: { reason: 'اشتباه', ratio: 0.5 } }));
  ids.push(await audit.log({ shopId: SHOP, userId: 'u_b', action: 'login', detail: {} }));
  ids.push(await audit.log({ shopId: SHOP, userId: 'u_a', action: 'settings.change', detail: { token: 'secret-never-stored', key: 'x' } }));

  assert.equal(await count(chainOf('u_a')), 3);
  assert.equal(await count(chainOf('u_b')), 1);
  const rows = (await query('SELECT seq, body FROM hash_chain WHERE chain=$1 ORDER BY seq', [chainOf('u_a')])).rows;
  assert.deepEqual(rows.map((r) => Number(r.seq)), [1, 2, 3]);
  const first = JSON.parse(rows[0].body);
  assert.equal(first.id, ids[0]);
  assert.equal(first.kind, 'audit');
  assert.equal(first.chain, chainOf('u_a'));
  //  دادهٔ شخصی و راز در بدنه نیست
  for (const r of rows) {
    assert.ok(!r.body.includes('10.0.0.1'), 'IP در زنجیره آمد');
    assert.ok(!r.body.includes('اشتباه'), 'متنِ جزئیات در زنجیره آمد');
    assert.ok(!r.body.includes('secret-never-stored'));
  }

  for (const c of [chainOf('u_a'), chainOf('u_b')]) {
    const v = await store.verifyChain(c);
    assert.equal(v.ok, true, JSON.stringify(v));
  }
  const r = await runCli([]);
  assert.equal(r.code, 0, r.out);
  assert.match(r.out, new RegExp(`✔ ${chainOf('u_a')} — 3 حلقه`));
});

test('۳) جدول فقط افزودنی است', async () => {
  const c = chainOf('u_a');
  await assert.rejects(query('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=1', [c, '{}']), /append-only/);
  await assert.rejects(query('DELETE FROM hash_chain WHERE chain=$1', [c]), /append-only/);
  await assert.rejects(query('TRUNCATE hash_chain'), /append-only/);
  const z = '0'.repeat(64);
  const f = 'f'.repeat(64);
  //  شکاف، پیوندِ غلط، آغازِ غیرِ genesis
  await assert.rejects(query('INSERT INTO hash_chain VALUES ($1,9,$2,$2,$3,1)', [c, f, '{}']), /does not follow/);
  await assert.rejects(query('INSERT INTO hash_chain VALUES ($1,4,$2,$2,$3,1)', [c, f, '{}']), /does not follow/);
  await assert.rejects(query('INSERT INTO hash_chain VALUES ($1,1,$2,$2,$3,1)', ['x/new', f, '{}']), /genesis/);
  await assert.rejects(query('INSERT INTO hash_chain VALUES ($1,1,$2,$3,$4,1)', ['x/new', z, 'NOTHEX', '{}']));
  assert.equal(await count(c), 3);
  assert.equal((await store.verifyChain(c)).ok, true);
});

test('۴) دست‌کاری: نخستین حلقهٔ شکسته و کدِ ۲', async () => {
  const c = chainOf('u_a');
  const two = (await query('SELECT body FROM hash_chain WHERE chain=$1 AND seq=2', [c])).rows[0].body;
  await tamper('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=2', [c, two.replace('"sale.void"', '"sale.keep"')]);
  let v = await store.verifyChain(c);
  assert.equal(v.ok, false);
  assert.equal(v.seq, 2);
  assert.equal(v.reason, 'hash_mismatch');
  assert.equal(v.length, 1);
  let r = await runCli(['--chain', c]);
  assert.equal(r.code, 2);
  assert.match(r.out, /نخستین حلقهٔ شکسته #2/);
  //  زنجیرهٔ کاربرِ دیگر سالم می‌ماند
  assert.equal((await store.verifyChain(chainOf('u_b'))).ok, true);
  await tamper('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=2', [c, two]);
  assert.equal((await store.verifyChain(c)).ok, true);

  //  هشِ ذخیره‌شده عوض شود ⇒ حلقهٔ بعدی پیوندش را از دست می‌دهد
  await tamper(`UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=3`, [c, ' ' + (await query('SELECT body FROM hash_chain WHERE chain=$1 AND seq=3', [c])).rows[0].body]);
  v = await store.verifyChain(c);
  assert.equal(v.seq, 3);
  assert.equal(v.reason, 'not_canonical');
  await tamper(`UPDATE hash_chain SET body=ltrim(body) WHERE chain=$1 AND seq=3`, [c]);

  //  پاک کردنِ حلقهٔ میانی
  await tamper('DELETE FROM hash_chain WHERE chain=$1 AND seq=2', [c]);
  v = await store.verifyChain(c);
  assert.equal(v.seq, 2);
  assert.equal(v.reason, 'seq_gap');
  r = await runCli(['--json']);
  assert.equal(r.code, 2);
  const report = JSON.parse(r.out);
  assert.equal(report.ok, false);
  assert.equal(report.results.find((x) => x.chain === c).reason, 'seq_gap');
});

test('۴-ب) دست‌کاریِ خودِ audit_logs هم دیده می‌شود', async () => {
  const c = chainOf('u_b');
  const id = JSON.parse((await query('SELECT body FROM hash_chain WHERE chain=$1 AND seq=1', [c])).rows[0].body).id;
  await query(`UPDATE audit_logs SET detail='{"x":1}'::jsonb WHERE id=$1`, [id]);
  let v = await store.verifyChain(c);
  assert.equal(v.reason, 'row_changed');
  assert.equal(v.seq, 1);
  assert.equal((await store.verifyChain(c, { rows: false })).ok, true);
  assert.equal((await runCli(['--chain', c, '--no-rows'])).code, 0);
  await query('DELETE FROM audit_logs WHERE id=$1', [id]);
  v = await store.verifyChain(c);
  assert.equal(v.reason, 'row_missing');
});

test('۵) نوشتنِ هم‌زمان انشعاب نمی‌سازد', async () => {
  const c = 'doc/shp_hc_test/K2';
  const docs = Array.from({ length: 25 }, (_, i) => ({ id: `K2-${i}`, total: i * 100, status: 'posted' }));
  const out = await Promise.all(docs.map((d) => store.append(c, d)));
  assert.deepEqual(out.map((o) => o.seq).sort((a, b) => a - b), Array.from({ length: 25 }, (_, i) => i + 1));
  const v = await store.verifyChain(c);
  assert.equal(v.ok, true, JSON.stringify(v));
  assert.equal(v.length, 25);
  await assert.rejects(store.append(c, { amount: 1.5 }), (e) => e.code === 'not_integer');
  await assert.rejects(store.append('bad chain!', {}), (e) => e.code === 'bad_chain');
});

test('۶) خاموش کردن ⇒ دیگر حلقه‌ای نوشته نمی‌شود', async () => {
  await flag(false);
  const before = await count(chainOf('u_c'));
  await audit.log({ shopId: SHOP, userId: 'u_c', action: 'login' });
  assert.equal(await count(chainOf('u_c')), before);
  await flag(true);
  await audit.log({ shopId: SHOP, userId: 'u_c', action: 'login' });
  assert.equal(await count(chainOf('u_c')), before + 1);
  assert.equal((await store.verifyChain(chainOf('u_c'))).ok, true);
});
