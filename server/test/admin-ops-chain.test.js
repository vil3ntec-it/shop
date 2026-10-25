'use strict';
/**
 * بررسیِ زنجیرهٔ هش از پنل — SEC-02 (نیمهٔ پنل).
 *
 *   ۱) فقط مدیرِ ارشد؛ بی توکن ۴۰۱، مدیرِ عادی ۴۰۳.
 *   ۲) همان `verifyAll`ِ `scripts/verify-chain.js`: سالم ⇒ طول و «سر»؛ دست‌کاری ⇒
 *      نخستین حلقهٔ شکسته و دلیل؛ ردیفِ پاک‌شدهٔ سابقه ⇒ row_missing (و با rows=false نه).
 *   ۳) فقط یک زنجیره با `chain`؛ نامِ نادرست ۴۰۰.
 *   ۴) هر بررسی یک ردیفِ سابقه (پس از سنجش).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const store = require('../src/lib/hash-chain-store');
const live = require('../src/lib/live-config');
const audit = require('../src/lib/audit');

let root = '';
let clerk = '';
const SHOP = 'shp_ops_chain';
const verify = (token, body = {}) => h.post('/api/admin/system/chain-verify', body, { token });

async function tamper(sql, params) {
  await query('ALTER TABLE hash_chain DISABLE TRIGGER hash_chain_immutable');
  try { await query(sql, params); } finally {
    await query('ALTER TABLE hash_chain ENABLE TRIGGER hash_chain_immutable');
  }
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  const made = await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

test('فقط مدیرِ ارشد', async () => {
  assert.equal((await verify(null)).status, 401);
  assert.equal((await verify(clerk)).status, 403);
});

test('پرچمِ خاموش ⇒ زنجیره‌ای نیست؛ روشن ⇒ سالم با طول و سر؛ سابقهٔ بررسی', async () => {
  const off = await verify(root);
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal(off.headers.get('cache-control'), 'no-store');
  assert.deepEqual([off.body.ok, off.body.enabled, off.body.total], [true, false, 0]);

  await live.set(store.FLAG_SCOPE, store.FLAG_AUDIT, true, 'test');
  for (const action of ['sale.create', 'sale.void', 'stock.adjust']) {
    await audit.log({ shopId: SHOP, actorType: 'user', userId: 'usr_a', action, detail: { n: 1 } });
  }
  const chain = store.auditChainId({ shopId: SHOP, actorType: 'user', userId: 'usr_a' });
  const r = await verify(root, { chain });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.ok, r.body.enabled, r.body.total, r.body.brokenCount], [true, true, 1, 0]);
  assert.equal(r.body.healthy[0].chain, chain);
  assert.equal(r.body.healthy[0].length, 3);
  assert.match(r.body.healthy[0].head, /^[0-9a-f]{64}$/);
  assert.equal(r.body.reasons.hash_mismatch.length > 0, true);
  const logged = await one(`SELECT detail FROM audit_logs WHERE action='admin.chain_verified' ORDER BY created_at DESC LIMIT 1`);
  const d = typeof logged.detail === 'string' ? JSON.parse(logged.detail) : logged.detail;
  assert.deepEqual([d.ok, d.total, d.broken], [true, 1, 0]);
});

test('دست‌کاری ⇒ نخستین حلقهٔ شکسته و دلیل؛ ردیفِ پاک‌شده ⇒ row_missing', async () => {
  const chain = store.auditChainId({ shopId: SHOP, actorType: 'user', userId: 'usr_a' });
  const two = (await one('SELECT body FROM hash_chain WHERE chain=$1 AND seq=2', [chain])).body;
  await tamper('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=2', [chain, two.replace('"sale.void"', '"sale.keep"')]);
  let r = await verify(root, { chain });
  assert.equal(r.body.ok, false);
  assert.deepEqual(r.body.broken.map((b) => [b.chain, b.seq, b.reason, b.length]), [[chain, 2, 'hash_mismatch', 1]]);
  await tamper('UPDATE hash_chain SET body=$2 WHERE chain=$1 AND seq=2', [chain, two]);

  //  ردیفِ سابقهٔ حلقهٔ ۳ پاک شود: با rows ⇒ row_missing؛ بی rows ⇒ سالم
  const three = JSON.parse((await one('SELECT body FROM hash_chain WHERE chain=$1 AND seq=3', [chain])).body);
  await query('DELETE FROM audit_logs WHERE id=$1', [three.id]);
  r = await verify(root, { chain });
  assert.deepEqual(r.body.broken.map((b) => [b.seq, b.reason]), [[3, 'row_missing']]);
  r = await verify(root, { chain, rows: false });
  assert.deepEqual([r.body.ok, r.body.rows], [true, false]);
  //  همهٔ زنجیره‌ها: زنجیرهٔ خودِ مدیر (سابقهٔ بررسی‌ها) هم هست و شکسته فقط همین است
  r = await verify(root);
  assert.ok(r.body.total >= 2, String(r.body.total));
  assert.deepEqual(r.body.broken.map((b) => b.chain), [chain]);
});

test('نامِ زنجیرهٔ نادرست ⇒ ۴۰۰', async () => {
  const r = await verify(root, { chain: 'bad chain!' });
  assert.equal(r.status, 400, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'bad_chain');
});
