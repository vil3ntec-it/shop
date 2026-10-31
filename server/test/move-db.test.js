'use strict';
/**
 * scripts/move-db.js — جابه‌جاییِ دیتابیسِ سرورِ حساب بینِ PGlite و PostgreSQL.
 *
 * مبدأ با همان Migrationهای واقعی ساخته و با ردیف‌هایی به شکلِ واقعی پر می‌شود:
 * کاربر (فارسی، NULL، double)، پلنِ پیش‌فرض که مالک قیمتش را عوض کرده، پرداختِ
 * اشتراک (مبلغ)، زنجیرهٔ هش (ماشهٔ «فقط‌افزودنی»)، ردیفِ sequenceدار، jsonb و bytea.
 * سپس: مبدأ دست نمی‌خورد، کپی همان است (شمار، هش، مبلغ، sequence)، ماشه‌ها پس از
 * کپی دوباره سرِ کارند، مقصدِ پر رد می‌شود، اختلافِ یک واحد دیده می‌شود، پشتیبانِ
 * ‎.pglite.tar.gz‎ هم مبدأ می‌شود، و — اگر TEST_DATABASE_URL ِ PostgreSQL باشد —
 * PGlite ⇐ PostgreSQL ⇐ PGlite در هر دو جهت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');
const { PGlite } = require('@electric-sql/pglite');
const { inspect, copy, diffReports, open, safeUrl } = require('../scripts/move-db');

const SERVER = path.join(__dirname, '..');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'move-db-test-'));
const SRC_DIR = path.join(tmp, 'home-pg');
const SRC = `pglite:${SRC_DIR}`;
const PG_URL = /^postgres/i.test(process.env.TEST_DATABASE_URL || '') ? process.env.TEST_DATABASE_URL : '';

function migrate(url) {
  const r = spawnSync(process.execPath, [path.join(SERVER, 'scripts', 'migrate.js')], {
    env: { ...process.env, DATABASE_URL: url, ENV_FILE: path.join(tmp, 'none.env') }, encoding: 'utf8',
  });
  assert.equal(r.status, 0, r.stderr || r.stdout);
}

/** اثرِ انگشتِ همهٔ فایل‌های یک پوشه — «دست نخورد» یعنی همین یکی بماند */
function treeHash(dir) {
  const h = crypto.createHash('sha256');
  const walk = (d) => {
    for (const n of fs.readdirSync(d).sort()) {
      const p = path.join(d, n);
      const st = fs.statSync(p);
      h.update(path.relative(dir, p));
      if (st.isDirectory()) walk(p); else h.update(fs.readFileSync(p));
    }
  };
  walk(dir);
  return h.digest('hex');
}

const BLOB = crypto.randomBytes(3000);
const CHAIN1 = crypto.createHash('sha256').update('genesis-body').digest('hex');

test.before(async () => {
  migrate(SRC);
  const db = await PGlite.create({ dataDir: SRC_DIR });
  const now = 1761800000000;
  await db.query(`INSERT INTO users (id, name, email, phone, created_at, updated_at, last_lat, last_lng) VALUES
    ('u1', 'صاحبِ دکان', 'a@example.com', NULL, $1, $1, 34.5553, 69.2075),
    ('u2', 'Pump owner', NULL, '+93700000000', $1, $1, NULL, NULL),
    ('u3', 'نامِ "نقل‌قول" و \\ بک‌اسلش', 'c@example.com', NULL, 9007199254740993, $1, -0.000001, 1e-300)`, [now]);
  //  پلنِ پیش‌فرضی که خودِ Migration کاشته و مالک قیمتش را عوض کرده
  await db.query(`UPDATE plans SET amount = coalesce(amount, 0) + 1234, price_afn = 999
    WHERE id = (SELECT id FROM plans ORDER BY id LIMIT 1)`);
  await db.query(`INSERT INTO sub_payments (id, tenant_id, amount, paid_at, created_at) VALUES
    ('p1', 'shop-1', 1500, $1, $1), ('p2', 'shop-1', 2500, $1, $1), ('p3', 'pump-9', 7, $1, $1)`, [now]);
  await db.query(`INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at) VALUES
    ('c1', 1, repeat('0', 64), $1, 'genesis-body', $2)`, [CHAIN1, now]);
  await db.query(`INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at) VALUES
    ('c1', 2, $1, $2, 'second', $3)`, [CHAIN1, 'f'.repeat(64), now]);
  await db.query(`INSERT INTO login_attempts (scope, identifier, created_at) VALUES ('login', 'a@example.com', $1), ('login', 'b', $1)`, [now]);
  await db.query(`INSERT INTO audit_logs (id, action, detail, created_at) VALUES ('a1', 'plan.edit', $1::jsonb, $2)`,
    [JSON.stringify({ fa: 'سلام', list: [1, 2.5, null], nested: { ok: true } }), now]);
  await db.query(`INSERT INTO image_blobs (hash, mime, size, width, height, data, created_at) VALUES ('img1', 'image/png', 3000, 10, 10, $1, $2)`, [BLOB, now]);
  await db.close();
});

test.after(() => { fs.rmSync(tmp, { recursive: true, force: true }); });

test('inspect: شمار، هش و جمعِ مبلغ — و مبدأ بایت‌به‌بایت دست نمی‌خورد', async () => {
  const before = treeHash(SRC_DIR);
  const db = await open(SRC, { readOnly: true });
  const rep = await inspect(db);
  await db.close();
  assert.equal(treeHash(SRC_DIR), before, 'باز کردنِ مبدأ رویش نوشت');
  assert.equal(rep.tables.users.rows, 3);
  assert.equal(rep.tables.sub_payments.sums.amount, '4007');
  assert.equal(rep.tables.hash_chain.rows, 2);
  assert.match(rep.tables.users.hash, /^[0-9a-f]{32}$/);
  assert.ok(!('schema_migrations' in rep.tables), 'جدولِ Migration فقط با فهرستِ نسخه‌ها می‌آید');
  assert.ok(rep.migrations.length > 50);
  assert.ok(Object.keys(rep.sequences).length > 0);
});

test('copy PGlite ⇐ PGlite: همان داده؛ ماشه‌ها دوباره سرِ کار؛ sequence ادامه می‌دهد', async () => {
  const before = treeHash(SRC_DIR);
  const DST = path.join(tmp, 'vps-pg');
  const r = await copy({ from: SRC, to: `pglite:${DST}` });
  assert.deepEqual(r.diffs, [], r.diffs.join(' | '));
  assert.equal(treeHash(SRC_DIR), before, 'کپی مبدأ را عوض کرد');
  assert.equal(r.target.tables.sub_payments.sums.amount, '4007');
  assert.equal(r.target.tables.users.rows, 3);

  const db = await PGlite.create({ dataDir: DST });
  try {
    const plan = (await db.query('SELECT price_afn FROM plans ORDER BY id LIMIT 1')).rows[0];
    assert.equal(plan.price_afn, 999, 'قیمتِ عوض‌شدهٔ مالک جای پیش‌فرضِ Migration نشست');
    const plans = (await db.query('SELECT count(*)::int AS n FROM plans')).rows[0].n;
    assert.equal(plans, r.source.tables.plans.rows, 'پلن‌های پیش‌فرض دوتایی نشدند');
    const blob = (await db.query(`SELECT data FROM image_blobs WHERE hash = 'img1'`)).rows[0].data;
    assert.ok(Buffer.from(blob).equals(BLOB), 'bytea بایت‌به‌بایت');
    const big = (await db.query(`SELECT created_at::text AS c FROM users WHERE id = 'u3'`)).rows[0].c;
    assert.equal(big, '9007199254740993', 'bigintِ بزرگ‌تر از سقفِ JS سالم');
    //  sequence: ردیفِ تازه شناسهٔ تکراری نمی‌گیرد
    await db.query(`INSERT INTO login_attempts (scope, identifier, created_at) VALUES ('login', 'new', 1)`);
    const ids = (await db.query('SELECT id::int AS id FROM login_attempts ORDER BY id')).rows.map((x) => x.id);
    assert.equal(new Set(ids).size, 3);
    //  ماشهٔ «فقط‌افزودنی» پس از کپی دوباره روشن است
    await assert.rejects(db.query(`UPDATE hash_chain SET body = 'x' WHERE seq = 1`), /append-only/);
    await assert.rejects(db.query(`INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at) VALUES ('c1', 3, 'bad', 'h', 'b', 1)`), /does not follow/);
  } finally { await db.close(); }
});

test('مقصدِ پر رد می‌شود و دست نمی‌خورد', async () => {
  const DST = path.join(tmp, 'vps-pg');
  const before = treeHash(DST);
  await assert.rejects(copy({ from: SRC, to: `pglite:${DST}` }), /خالی نیست/);
  assert.equal(treeHash(DST), before);
});

test('اختلافِ یک واحد در مبلغ و یک ردیفِ کم دیده می‌شود', async () => {
  const a = await open(SRC, { readOnly: true });
  const ra = await inspect(a);
  await a.close();
  const changed = JSON.parse(JSON.stringify(ra));
  changed.tables.sub_payments.sums.amount = '4006';
  changed.tables.users.rows = 2;
  const diffs = diffReports(ra, changed);
  assert.ok(diffs.some((d) => d.includes('sub_payments.amount')), diffs.join(' | '));
  assert.ok(diffs.some((d) => d.startsWith('users:')), diffs.join(' | '));
  assert.deepEqual(diffReports(ra, ra), []);
});

test('پشتیبانِ خودِ سرور (‎.pglite.tar.gz‎) هم مبدأ می‌شود', async () => {
  //  همان راهی که lib/backup.js پشتیبانِ PGlite می‌گیرد: dumpDataDir
  const live = await PGlite.create({ dataDir: path.join(tmp, 'vps-pg') });
  const blob = await live.dumpDataDir('gzip');
  await live.close();
  const file = path.join(tmp, 'shop-backup.pglite.tar.gz');
  fs.writeFileSync(file, Buffer.from(await blob.arrayBuffer()));
  const r = await copy({ from: `pglite:${file}`, to: `pglite:${path.join(tmp, 'from-backup')}` });
  assert.deepEqual(r.diffs, [], r.diffs.join(' | '));
  assert.equal(r.target.tables.sub_payments.sums.amount, '4007');
});

test('مبدأیی که Migrationِ ناشناخته دارد (سرورِ تازه‌تر از این ابزار) ⇒ رد، پیش از دست زدن به مقصد', async () => {
  const dir = path.join(tmp, 'future');
  fs.cpSync(SRC_DIR, dir, { recursive: true });
  const db = await PGlite.create({ dataDir: dir });
  await db.query(`INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ('999_future.sql', 'x', 1)`);
  await db.close();
  const DST = path.join(tmp, 'never');
  await assert.rejects(copy({ from: `pglite:${dir}`, to: `pglite:${DST}` }), /Migrationهایی دارد که این کد ندارد/);
  assert.ok(!fs.existsSync(DST) || fs.readdirSync(DST).length === 0, 'مقصد ساخته شد');
});

test('خطِ فرمان: inspect --json و نشانیِ بی رمز', () => {
  const r = spawnSync(process.execPath, [path.join(SERVER, 'scripts', 'move-db.js'), 'inspect', '--db', SRC, '--json'], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  const rep = JSON.parse(r.stdout);
  assert.equal(rep.tables.sub_payments.sums.amount, '4007');
  assert.equal(safeUrl('postgres://shop:secret-pass@db:5432/shop'), 'postgres://shop:***@db:5432/shop');
  const bad = spawnSync(process.execPath, [path.join(SERVER, 'scripts', 'move-db.js'), 'copy', '--from', SRC], { encoding: 'utf8' });
  assert.equal(bad.status, 2);
});

test('PGlite ⇐ PostgreSQL ⇐ PGlite (دو جهت، با تطبیق)', { skip: !PG_URL && 'TEST_DATABASE_URL ِ PostgreSQL نیست' }, async () => {
  const { Client } = require('pg');
  const name = `move_db_${process.pid}_${Date.now()}`;
  const admin = new Client({ connectionString: PG_URL });
  await admin.connect();
  await admin.query(`CREATE DATABASE ${name}`);
  const url = new URL(PG_URL);
  url.pathname = `/${name}`;
  try {
    const there = await copy({ from: SRC, to: url.toString() });
    assert.deepEqual(there.diffs, [], there.diffs.join(' | '));
    assert.equal(there.target.driver, 'postgres');
    assert.equal(there.target.tables.sub_payments.sums.amount, '4007');
    //  و برگشت — همان راهِ rollback اگر سرورِ تازه داده گرفته باشد
    const back = await copy({ from: url.toString(), to: `pglite:${path.join(tmp, 'back-home')}` });
    assert.deepEqual(back.diffs, [], back.diffs.join(' | '));
    const a = await open(SRC, { readOnly: true });
    const b = await open(`pglite:${path.join(tmp, 'back-home')}`, { readOnly: true });
    const d = diffReports(await inspect(a), await inspect(b));
    await a.close(); await b.close();
    assert.deepEqual(d, [], `رفت‌وبرگشت همان مبدأ نیست: ${d.join(' | ')}`);
    await assert.rejects(copy({ from: SRC, to: url.toString() }), /خالی نیست/);
  } finally {
    await admin.query(`DROP DATABASE IF EXISTS ${name} WITH (FORCE)`).catch(() => {});
    await admin.end();
  }
});
