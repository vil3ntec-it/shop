'use strict';
/**
 * راه‌اندازِ PGlite — همان قراردادِ pg، بی هیچ PostgreSQLی.
 *
 * چیزی که باید ثابت شود: عددِ بزرگ رشته می‌آید (مثلِ pg)، bytea یک Buffer
 * است، پرس‌وجوی سراسری داخلِ تراکنش به همان تراکنش می‌رود (وگرنه پشتِ قفلِ
 * خودش می‌ماند)، rowCount درست است، پشتیبان یک tar.gz واقعی می‌دهد، و مدیر
 * از محیط ساخته می‌شود. بندهای مخصوصِ PGlite روی PostgreSQL رد می‌شوند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');

const onPglite = db.isPglite();

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('نوع‌ها مثلِ pg برمی‌گردند: bigint رشته، bytea بافر، jsonb شیء', { skip: !onPglite }, async () => {
  await db.query('CREATE TABLE IF NOT EXISTS t_types (id text PRIMARY KEY, n bigint, d jsonb, b bytea)');
  //  عددی بزرگ‌تر از سقفِ عددِ JS، به شکلِ رشته — مثلِ pg باید رشته برگردد،
  //  نه BigInt (که JSON.stringify نمی‌تواند) و نه number (که رقم گم می‌کند).
  await db.query('INSERT INTO t_types VALUES ($1,$2,$3,$4)', ['a', '9007199254740993', { k: [1, 2] }, Buffer.from('hi')]);
  const row = await db.one('SELECT * FROM t_types WHERE id=$1', ['a']);
  assert.equal(typeof row.n, 'string');
  assert.equal(row.n, '9007199254740993');
  assert.equal(JSON.stringify({ n: row.n }), '{"n":"9007199254740993"}');
  assert.ok(Buffer.isBuffer(row.b));
  assert.equal(row.b.toString(), 'hi');
  assert.deepEqual(row.d, { k: [1, 2] });
});

test('rowCount برای DELETE/UPDATE شمارِ ردیف‌های واقعی است', { skip: !onPglite }, async () => {
  await db.query('CREATE TABLE IF NOT EXISTS t_cnt (id int)');
  await db.query('INSERT INTO t_cnt VALUES (1),(2),(3)');
  const u = await db.query('UPDATE t_cnt SET id = id + 10 WHERE id > $1', [1]);
  assert.equal(u.rowCount, 2);
  const d = await db.query('DELETE FROM t_cnt WHERE id > $1', [100]);
  assert.equal(d.rowCount, 0);
  const s = await db.query('SELECT * FROM t_cnt');
  assert.equal(s.rowCount, 3);
});

test('پرس‌وجوی سراسری داخلِ تراکنش به همان تراکنش می‌رود و با ROLLBACK برمی‌گردد', { skip: !onPglite }, async () => {
  await db.query('CREATE TABLE IF NOT EXISTS t_tx (id text PRIMARY KEY)');
  await assert.rejects(db.tx(async (c) => {
    await c.query('INSERT INTO t_tx VALUES ($1)', ['x']);
    //  ⚠️ نه از راهِ c — از راهِ query سراسری. بی ALS این‌جا تا ابد می‌ماند.
    const seen = await db.one('SELECT id FROM t_tx WHERE id=$1', ['x']);
    assert.equal(seen?.id, 'x');
    throw new Error('عمداً');
  }), /عمداً/);
  assert.equal(await db.one('SELECT id FROM t_tx WHERE id=$1', ['x']), null);
  const ok = await db.tx(async (c) => {
    await c.query('INSERT INTO t_tx VALUES ($1)', ['y']);
    return (await db.one('SELECT count(*)::int AS c FROM t_tx')).c;
  });
  assert.equal(ok, 1);
});

test('پشتیبان روی PGlite یک tar.gz واقعی با meta است، و بازگردانی راهش را می‌گوید', { skip: !onPglite }, async () => {
  const backup = require('../src/lib/backup');
  const out = await backup.run({ kind: 'manual' });
  assert.ok(out.file.endsWith('.pglite.tar.gz'));
  assert.ok(out.bytes > 1000);
  const fs = require('fs');
  const head = fs.readFileSync(out.file).subarray(0, 2);
  assert.equal(head[0], 0x1f); assert.equal(head[1], 0x8b); // gzip
  const list = await backup.list();
  assert.ok(list.some((b) => b.path === out.file && b.sha256 === out.sha256));
  await assert.rejects(backup.restore(out.file), /PGlite/);
});

test('مدیر از محیط ساخته می‌شود و رمزِ عوض‌شده به محیط برمی‌گردد', async () => {
  const { ensureAdmin } = require('../src/lib/admin-bootstrap');
  const first = await ensureAdmin({ username: 'Boot-Admin', password: 'Boot!12345' });
  assert.equal(first.created, true);
  const again = await ensureAdmin({ username: 'boot-admin', password: 'Boot!12345' });
  assert.equal(again.created, false); assert.equal(again.updated, false);
  const login = await h.post('/api/admin/login', { username: 'boot-admin', password: 'Boot!12345' });
  assert.equal(login.status, 200);
  const changed = await ensureAdmin({ username: 'boot-admin', password: 'Other!12345' });
  assert.equal(changed.updated, true);
  const old = await h.post('/api/admin/login', { username: 'boot-admin', password: 'Boot!12345' });
  assert.equal(old.status, 401);
  const fresh = await h.post('/api/admin/login', { username: 'boot-admin', password: 'Other!12345' });
  assert.equal(fresh.status, 200);
  await assert.rejects(ensureAdmin({ username: 'weak', password: '1234' }), /ADMIN_BOOTSTRAP_PASSWORD/);
  assert.equal(await ensureAdmin({}), null);
});

test('/api/health روی هر دو راه‌انداز «وصل» می‌گوید', async () => {
  const r = await h.get('/api/health');
  assert.equal(r.status, 200);
  assert.equal(r.body.database, 'connected');
});
