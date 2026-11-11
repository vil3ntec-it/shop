'use strict';
/**
 * O-15 الف (۳-۴) — بی‌نام‌کنندهٔ کپیِ دادهٔ واقعی (`lib/anonymize.js`، `scripts/anonymize-backup.js`).
 *
 *   ① قاعده‌ها (بی دیتابیس): گونهٔ نام‌ها، جای‌گزینِ قطعیِ هم‌شکل (طول، خط، قالبِ تلفن/slug/کد)، ایمیل‌های هم‌نامِ دو دامنه.
 *   ② روی دیتابیس (PGlite و PostgreSQL): نام، تلفن، یادداشت و ایمیل همه‌جا با یک جای‌گزین؛ مبلغ، قرض، شناسه، وضعیت و HLC
 *      دست‌نخورده؛ اثرِ انگشتِ `restore-drill` همان (جز مختصاتِ گرد)؛ هیچ مقدارِ اصلی در هیچ ردیفی نمی‌ماند.
 *   ③ نشت: شناسه‌ای که خودش شمارهٔ تلفن است گزارش می‌شود.
 *   ④ سرتاسری (فقط PostgreSQL، pg_dump): پشتیبانِ واقعی ⇒ `run` ⇒ فایلِ رمزدارِ بی‌نام که `restore-drill` برابر می‌بیند و
 *      متنش هیچ مقدارِ اصلی ندارد؛ دیتابیسِ زنده دست‌نخورده. پشتیبانِ PGlite پذیرفته نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const os = require('node:os');
const path = require('node:path');
const fs = require('node:fs/promises');
const h = require('./helpers');
const db = require('../src/db');
const config = require('../src/config');
const anon = require('../src/lib/anonymize');
const drill = require('../src/lib/restore-drill');

const DIR = path.join(os.tmpdir(), `shop-anon-test-${process.pid}`);
const PASS = 'anon-passphrase-only-for-this-test';
let shopId;
let seq = 0;

const q = (t, p) => db.query(t, p);
/** یک اتصال برای تراکنش‌های هر جدول (PGlite خودش یک اتصال است) */
async function single(fn) {
  if (db.isPglite()) return fn(q);
  const { Client } = require('pg');
  const c = new Client({ connectionString: config.db.url });
  await c.connect();
  try { return await fn((t, p) => c.query(t, p)); } finally { await c.end(); }
}
/** شمارِ ردیف‌هایی در کلِ `public` که متنشان [s] را دارد */
async function anywhere(s) {
  const tables = (await q(`SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'`)).rows;
  let n = 0;
  for (const { table_name: t } of tables) {
    n += Number((await q(`SELECT count(*)::int AS n FROM "${t}" x WHERE strpos(x::text, $1) > 0`, [s])).rows[0].n);
  }
  return n;
}

/** یک مشتری با نام و تلفن در سه جا (فروشگاهِ آنلاین، دفترِ v3، دفترِ قدیم) و یک فاکتورِ پولی */
async function seed(tag) {
  const now = Date.now();
  const o = {
    name: `مریم‌آزمون${tag} احمدی`, phone: `+9370011${String(1000 + seq++).padStart(4, '0')}`, note: `پشتِ مسجدِ آزمون${tag}`,
    email: `maryam.${tag}@mail.test`, local: `0700${String(220000 + seq).padStart(6, '0')}`,
  };
  await q('INSERT INTO store_customers (id, phone, name, created_at, updated_at) VALUES ($1, $2, $3, $4, $4)', [`sc-${tag}`, o.phone, o.name, now]);
  const v3 = (table, rowId, data) => q(
    `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, field_hlc, row_hlc, created_at, updated_at)
     VALUES ('shop', 'shop', $1, $2, $3, $4::jsonb, $5::jsonb, '0000000000001-0000-d', $6, $6)`,
    [shopId, table, rowId, JSON.stringify(data), JSON.stringify({ name: '0000000000001-0000-d' }), now]);
  await v3('customers', `c-${tag}`, { name: o.name, phone: o.phone, note: o.note, email: o.email, creditLimitMinor: 500000, status: 'active', lat: 34.55555, lng: 69.20755 });
  await v3('sales', `s-${tag}`, { customerId: `c-${tag}`, totalMinor: 9876500, discountMinor: 100, note: o.note, items: [{ name: 'چای سبزِ آزمون', qty: 2 }], paymentMethod: 'cash' });
  await q(`INSERT INTO debtors (shop_id, id, rev, created_at, updated_at, data) VALUES ($1, $2, 1, $3, $3, $4::jsonb)`,
    [shopId, `d-${tag}`, now, JSON.stringify({ name: o.name, phone: o.local, debt: 12345.5 })]);
  return o;
}

test.before(async () => {
  await h.start();
  const u = await h.newUser('صاحبِ دکانِ بی‌نام', 'shop');
  shopId = (await h.post('/api/shop', { name: 'دکانِ بی‌نام' }, { token: u.accessToken })).body.shop.id;
});
test.after(async () => { await fs.rm(DIR, { recursive: true, force: true }); await h.stop(); });

/* ═════════ ① ═════════ */

test('قاعده‌ها: گونهٔ نام و جای‌گزینِ هم‌شکل و قطعی', () => {
  const c = (k) => anon.classify(k);
  assert.deepEqual(['name', 'customerPhone', 'note', 'void_reason', 'slug', 'tg_id'].map(c), ['pii', 'pii', 'pii', 'pii', 'pii', 'pii']);
  assert.deepEqual(['id', 'saleId', 'table_name', 'status', 'email_status', 'field_hlc', 'unit', 'hash'].map(c), Array(8).fill('keep'));
  assert.deepEqual(['password_hash', 'access_code', 'token', 'secret_enc'].map(c), Array(4).fill('secret'));
  assert.equal(c('lat'), 'geo');
  assert.equal(c('totalMinor'), 'neutral');
  assert.equal(anon.phoneish('0700123456'), true);
  assert.equal(anon.phoneish('+93700123456'), true);
  assert.equal(anon.phoneish('6260000012345'), false);   //  بارکد
  assert.equal(anon.structural('pending'), true);
  assert.equal(anon.structural('سلام دنیا'), false);
  const p = anon.makePseudo(Buffer.alloc(32, 7));
  const fa = p.pseudo('text', 'احمد کریمی');
  assert.equal(fa, p.pseudo('text', 'احمد کریمی'), 'قطعی');
  assert.notEqual(fa, 'احمد کریمی');
  assert.equal(fa.length, 'احمد کریمی'.length);
  assert.match(fa, /^[؀-ۿ]+ [؀-ۿ]+$/);
  assert.match(p.pseudo('phone', '+93700123456'), /^\+9\d{10}$/);     //  همان قیدِ `^\+[1-9][0-9]{7,14}$`
  assert.match(p.pseudo('text', 'my-shop-1'), /^[a-z]{2}-[a-z]{4}-\d$/);
  assert.match(p.pseudo('code', 'AB12CDEF34'), /^[0-9A-HJKMNP-TV-Z]{10}$/);
  assert.notEqual(p.pseudo('email', 'ali@gmail.com'), p.pseudo('email', 'ali@yahoo.com'));
  assert.match(p.pseudo('email', 'ali@gmail.com'), /^[a-z]{3}@example\.invalid$/);
  //  جای‌گزین هرگز یکی از مقدارهای واقعیِ دیگر نیست
  const q2 = anon.makePseudo(Buffer.alloc(32, 7));
  q2.setOriginals(new Set([p.pseudo('phone', '0700000001')]));
  assert.notEqual(q2.pseudo('phone', '0700000001'), p.pseudo('phone', '0700000001'));
});

/* ═════════ ② ═════════ */

test('روی دیتابیس: جای‌گزین همه‌جا یکی، عدد و شناسه همان، اثرِ انگشت همان جز مختصات، هیچ مقدارِ اصلی نمی‌ماند', async () => {
  const o = await seed('a');
  const before = await drill.fingerprint(q);
  const a = await single((one) => anon.anonymize(one));
  assert.deepEqual(a.leaks, []);
  assert.ok(a.changed.text > 0 && a.changed.phone >= 3 && a.changed.email >= 1 && a.changed.geo >= 2, JSON.stringify(a.changed));
  assert.deepEqual(a.numKeys, ['lat', 'lng']);
  const cmp = drill.compare(before, await drill.fingerprint(q));
  assert.ok(cmp.equal || anon.onlyNumKeys(cmp.diffs, a.numKeys), JSON.stringify(cmp.diffs.slice(0, 5)));
  assert.ok(cmp.diffs.length >= 1, 'مختصاتِ گردشده دیده می‌شود');

  const row = async (t, id) => (await q('SELECT data, field_hlc FROM sync_v3_rows WHERE table_name=$1 AND row_id=$2', [t, id])).rows[0];
  const c = await row('customers', 'c-a');
  const sc = (await q('SELECT phone, name FROM store_customers WHERE id=$1', ['sc-a'])).rows[0];
  assert.notEqual(c.data.name, o.name);
  assert.equal(c.data.name.length, o.name.length);
  assert.equal(c.data.name, sc.name, 'یک نام در دو جدول ⇒ یک جای‌گزین');
  assert.equal(c.data.phone, sc.phone);
  assert.match(sc.phone, /^\+[1-9][0-9]{7,14}$/);
  assert.match(c.data.email, /@example\.invalid$/);
  assert.equal(c.data.creditLimitMinor, 500000);
  assert.equal(c.data.status, 'active');
  assert.equal(c.data.lat, 34.6);
  assert.deepEqual(c.field_hlc, { name: '0000000000001-0000-d' }, 'HLC ِ همگام‌سازی دست‌نخورده');
  const s = await row('sales', 's-a');
  assert.equal(s.data.customerId, 'c-a');
  assert.equal(s.data.totalMinor, 9876500);
  assert.equal(s.data.paymentMethod, 'cash');
  assert.equal(s.data.items[0].qty, 2);
  assert.notEqual(s.data.items[0].name, 'چای سبزِ آزمون');
  assert.equal(s.data.note, c.data.note);
  const d = (await q('SELECT data FROM debtors WHERE id=$1', ['d-a'])).rows[0].data;
  assert.equal(d.debt, 12345.5);
  assert.match(d.phone, /^07\d{8}$/);
  for (const v of [o.name, o.phone, o.note, o.email, o.local, 'چای سبزِ آزمون', 'صاحبِ دکانِ بی‌نام']) assert.equal(await anywhere(v), 0, v);
});

/* ═════════ ③ ═════════ */

test('نشت: شناسه‌ای که خودش شمارهٔ تلفن است گزارش می‌شود', async () => {
  const now = Date.now();
  await q(`INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, created_at, updated_at)
           VALUES ('shop', 'shop', $1, 'customers', '0799888777', $2::jsonb, $3, $3)`, [shopId, JSON.stringify({ phone: '0799888777' }), now]);
  const a = await single((one) => anon.anonymize(one));
  assert.ok(a.leaks.some((l) => l.where === 'sync_v3_rows.row_id'), JSON.stringify(a.leaks));
  await q(`DELETE FROM sync_v3_rows WHERE row_id='0799888777'`);
});

/* ═════════ ④ ═════════ */

test('سرتاسری: پشتیبانِ واقعی ⇒ کپیِ بی‌نامِ رمزدار ⇒ restore-drill برابر؛ متنش هیچ مقدارِ اصلی ندارد؛ دیتابیسِ زنده دست‌نخورده',
  { skip: db.isPglite() ? 'pg_dump و دیتابیسِ جدا لازم است (PostgreSQL)' : false }, async () => {
    const o = await seed('b');
    config.backup.dir = path.join(DIR, 'src');
    config.backup.passphrase = PASS;
    process.env.BACKUP_PASSPHRASE = PASS;
    const backup = require('../src/lib/backup');
    const src = await backup.run({ kind: 'manual' });
    const cli = require('../scripts/anonymize-backup');
    const r = await cli.run({ file: src.file, out: path.join(DIR, 'out'), verify: true });
    assert.ok(r.ok, JSON.stringify(r));
    assert.equal(r.same, true);
    assert.equal(r.verified, true);
    assert.equal(r.encrypted, true);
    const text = (await backup.decode(r.out)).toString('utf8');
    for (const v of [o.name, o.phone, o.note, o.email, o.local]) assert.ok(!text.includes(v), v);
    assert.ok(text.includes('9876500'), 'مبلغ همان');
    //  ⛔ دیتابیسِ زنده دست نخورد؛ کپیِ جدا انداخته شد
    assert.equal((await q('SELECT phone FROM store_customers WHERE id=$1', ['sc-b'])).rows[0].phone, o.phone);
    const left = (await q(`SELECT count(*)::int AS n FROM pg_database WHERE datname LIKE 'shop_drill_%'`)).rows[0].n;
    assert.equal(left, 0);
  });

test('پشتیبانِ PGlite (پوشهٔ خام با نسخه‌های کهنهٔ ردیف) پذیرفته نیست؛ گزینهٔ ناشناخته خطاست', async () => {
  const cli = require('../scripts/anonymize-backup');
  await assert.rejects(cli.run({ file: path.join(DIR, 'x.pglite.tar.gz'), out: path.join(DIR, 'o') }), /PGlite/);
  assert.throws(() => cli.args(['--bogus']), /ناشناخته/);
  assert.deepEqual(cli.args(['f.sql.gz', '--keep', '--no-verify']).verify, false);
});
