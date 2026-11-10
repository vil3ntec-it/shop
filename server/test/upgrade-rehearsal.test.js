'use strict';
/**
 * OPS-03@29 — تمرینِ ارتقا روی دادهٔ واقعی‌نما (`scripts/upgrade-rehearsal.js`، ورک‌فلوی `staging-rehearsal.yml`).
 *
 *   ۱) مقایسه: جدول/ستونِ برداشته‌شده، کم شدنِ ردیف و عوض شدنِ محتوای جدولِ حیاتی ⇒ خطا؛
 *      جدولِ دیگر ⇒ هشدار؛ تازه‌ها ⇒ فقط گزارش.
 *   ۲) seed فقط از راهِ HTTP روی سرورِ واقعی؛ دودآزمایی همهٔ opها را با دستگاهِ تازه پس می‌گیرد.
 *   ۳) مهاجرتِ بی‌خطر (ستون با پیش‌فرض، جدولِ تازه) سبز؛ مهاجرتِ خراب (حذفِ op، دست بردن در
 *      دفتر) سرخ — با اثرِ انگشتِ واقعیِ همین پایگاه.
 *   ۴) ⛔ ابزار روی پایگاهی که نامش test/bench/staging ندارد نمی‌نویسد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const rh = require('../scripts/upgrade-rehearsal');

const q = (s, p) => db.many(s, p);
const fp = (tables) => ({ tables });
const T = (rows, columns, hash, hashed = columns) => ({ rows, columns, hashed, hash });

test('مقایسه: برداشتن، کم شدن و دست بردن در حیاتی ⇒ خطا؛ دیگری ⇒ هشدار؛ تازه ⇒ گزارش', () => {
  const before = fp({
    oplog: T(10, ['id', 'op_id'], 'a'),
    notes: T(3, ['id', 'body'], 'b'),
    gone: T(1, ['id'], 'c'),
    shops: T(2, ['id', 'name', 'old'], 'd'),
  });
  const after = fp({
    oplog: T(9, ['id', 'op_id', 'extra'], 'z', ['id', 'op_id']),
    notes: T(3, ['id', 'body'], 'changed'),
    shops: T(2, ['id', 'name'], 'd', ['id', 'name']),
    fresh: T(0, ['id'], 'e'),
  });
  const r = rh.compare(before, after);
  assert.deepEqual(r.errors.sort(), [
    'oplog: 1 ردیف کم شد (10 ⇐ 9)',
    'جدولِ gone پس از مهاجرت نیست',
    'ستونِ old از shops برداشته شد',
  ].sort());
  assert.deepEqual(r.warnings, ['محتوای ستون‌های پیشینِ notes عوض شد']);
  assert.ok(r.info.includes('ستونِ تازه در oplog: extra'));
  assert.ok(r.info.includes('جدولِ تازه: fresh'));
  //  جدولِ حیاتی با همان شمار ولی محتوای دیگر ⇒ خطا
  const r2 = rh.compare(fp({ sync_rows: T(4, ['id'], 'a') }), fp({ sync_rows: T(4, ['id'], 'b') }));
  assert.deepEqual(r2.errors, ['محتوای ستون‌های پیشینِ sync_rows عوض شد']);
  //  هیچ تغییر ⇒ هیچ پیام
  assert.deepEqual(rh.compare(before, before), { errors: [], warnings: [], info: [] });
});

test('⛔ فقط پایگاهِ test/bench/staging یا PGlite', () => {
  assert.throws(() => rh.guardDatabase('postgres://u:p@db:5432/shop'), /test، bench یا staging/);
  assert.throws(() => rh.guardDatabase('postgres://u:p@db:5432/production'), /test، bench یا staging/);
  for (const ok of ['postgres://u:p@db:5432/shop_test', 'postgres://u:p@db/shop_staging', 'pglite:memory', 'pglite:/tmp/x']) {
    assert.doesNotThrow(() => rh.guardDatabase(ok), ok);
  }
});

//  دودآزمایی زیرِ بار «fetch failed» می‌داد: درخواست روی سوکتِ keep-alive ِ بیکاری رفت که مهلتِ سرور همان دم بستش
//  (ECONNRESET). بازتولید: کارِ همگامِ ۵٫۵ ثانیه‌ای پیش از `/api/ready`. حالا هر درخواست اتصالِ تازه دارد و بستنِ واقعی
//  از سوی سرور همچنان خطاست (بی تلاشِ دوباره).
test('⛔ هر درخواستِ تمرین اتصالِ تازه؛ بستنِ اتصال از سوی سرور پنهان نمی‌شود', async () => {
  const nodeHttp = require('node:http');
  const seen = [];
  let drop = false;
  const srv = nodeHttp.createServer((req, res) => {
    seen.push({ port: req.socket.remotePort, connection: req.headers.connection });
    if (drop) { req.socket.destroy(); return; }
    res.setHeader('Content-Type', 'application/json');
    res.end('{}');
  });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  try {
    const base = `http://127.0.0.1:${srv.address().port}`;
    assert.deepEqual(await rh.smoke(base, { shops: [] }), []);
    assert.deepEqual(await rh.smoke(base, { shops: [] }), []);
    assert.deepEqual(seen.map((s) => s.connection), ['close', 'close']);
    assert.notEqual(seen[0].port, seen[1].port, 'سوکتِ keep-alive ِ پیشین دوباره به کار نرفت');
    drop = true;
    await assert.rejects(rh.smoke(base, { shops: [] }), /fetch failed/);
    assert.equal(seen.length, 3, 'بستنِ اتصال از سوی سرور بی تلاشِ دوباره خطاست');
  } finally {
    srv.closeAllConnections();
    await new Promise((r) => srv.close(r));
  }
});

let state = null;
let before = null;
const columnsOf = (f) => Object.fromEntries(Object.entries(f.tables).map(([t, v]) => [t, v.columns]));

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
});
test.after(async () => { await h.stop(); });

test('seed از راهِ HTTP و دودآزمایی: همهٔ opها با دستگاهِ تازه برمی‌گردد', async () => {
  state = await rh.seed(h.base(), { shops: 2, products: 12, sales: 30 });
  assert.equal(state.shops.length, 2);
  //  ۱۲ کالا + ۱۰ قرض‌دار + ۳۰ فروش با ۳۰ `$inc` + ۴ نسیه + ویرایش و حذف
  assert.equal(state.shops[0].ops, 12 + 10 + 30 * 2 + 4 + 2);
  const sale = await db.one(
    `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='products' AND row_id='p0'`, [state.shops[0].shopId]);
  //  p0 فقط در فروش‌های ۰، ۱۲ و ۲۴ (s*7 % 12 = 0)، هر کدام ۱ عدد (1 + s % 3)
  assert.equal(sale.data.stock, 97);
  assert.deepEqual(await rh.smoke(h.base(), state), []);
  before = await rh.fingerprint(q);
  assert.ok(before.tables.oplog.rows >= state.shops[0].ops * 2);
});

test('مهاجرتِ بی‌خطر (ستون با پیش‌فرض، جدولِ تازه) ⇒ سبز', async () => {
  await db.query(`ALTER TABLE shops ADD COLUMN rh_note text NOT NULL DEFAULT 'x'`);
  await db.query('CREATE TABLE rh_new (id int PRIMARY KEY)');
  const after = await rh.fingerprint(q, { columnsOf: columnsOf(before) });
  const r = rh.compare(before, after);
  assert.deepEqual(r.errors, []);
  assert.deepEqual(r.warnings, []);
  assert.ok(r.info.includes('ستونِ تازه در shops: rh_note'), r.info.join(' | '));
  assert.ok(r.info.includes('جدولِ تازه: rh_new'));
  assert.deepEqual(await rh.smoke(h.base(), state), []);
});

test('مهاجرتِ خراب (حذفِ یک op، دست بردن در دفتر) ⇒ سرخ؛ دودآزمایی کمبود را می‌بیند', async () => {
  const shop = state.shops[1].shopId;
  await db.query(`DELETE FROM oplog WHERE id = (SELECT id FROM oplog WHERE account_id=$1 ORDER BY server_seq DESC LIMIT 1)`, [shop]);
  await db.query(`UPDATE sync_rows SET data = data || '{"price": 1}'::jsonb WHERE account_id=$1 AND row_id='p2'`, [shop]);
  const after = await rh.fingerprint(q, { columnsOf: columnsOf(before) });
  const r = rh.compare(before, after);
  assert.ok(r.errors.includes('oplog: 1 ردیف کم شد (' + before.tables.oplog.rows + ' ⇐ ' + (before.tables.oplog.rows - 1) + ')'), r.errors.join(' | '));
  assert.ok(r.errors.includes('محتوای ستون‌های پیشینِ sync_rows عوض شد'), r.errors.join(' | '));
  const smoke = await rh.smoke(h.base(), state);
  assert.equal(smoke.length, 1);
  assert.match(smoke[0], new RegExp(`${shop}: ${state.shops[1].ops - 1} op از ${state.shops[1].ops}`));
});

test('خطِ فرمان: check ⇒ کدِ ۲ برای خطا، ۰ برای سالم', async () => {
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rehearsal-'));
  try {
    const a = path.join(dir, 'a.json');
    const b = path.join(dir, 'b.json');
    fs.writeFileSync(a, JSON.stringify(fp({ oplog: T(2, ['id'], 'x') })));
    fs.writeFileSync(b, JSON.stringify(fp({ oplog: T(1, ['id'], 'y') })));
    const log = console.log;
    console.log = () => {};
    try {
      assert.equal(await rh.main(['check', '--before', a, '--after', b]), 2);
      assert.equal(await rh.main(['check', '--before', a, '--after', a]), 0);
    } finally {
      console.log = log;
    }
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

/*
 * «به‌روزرسانی‌ای که برنمی‌گردد» (OPS-03@29): طرحِ برگشتِ سرور «نسخهٔ پیش روی همان پایگاه» است. پس نسخهٔ
 * پیش باید روی پایگاهِ مهاجرت‌شده هم بخواند و هم **بنویسد**. ستونِ NOT NULL ِ بی پیش‌فرض که نسخهٔ تازه پر
 * می‌کند و نسخهٔ پیش نمی‌شناسد، هر نوشتنِ نسخهٔ پیش را می‌شکند — دودآزماییِ «فقط خواندن» آن را نمی‌دید.
 * این‌جا «نسخهٔ پیش» همین کد است و «مهاجرتِ تازه» ستونی که این کد نمی‌شناسد. (آخرِ پرونده و فقط دکانِ
 * نخست: آزمون‌های بالا اثرِ انگشتِ «پیش» را با پایگاهِ بی نوشتنِ تازه می‌سنجند و دکانِ دوم را عمداً خراب کرده‌اند.)
 */
test('برگشت: نسخهٔ پیش روی پایگاهِ مهاجرت‌شده می‌خواند و می‌نویسد؛ ستونِ NOT NULL ِ بی پیش‌فرض ⇒ سرخ', async () => {
  const one = { ...state, shops: [{ ...state.shops[0] }] };
  const ops = one.shops[0].ops;
  assert.deepEqual(await rh.rollback(h.base(), one), []);
  //  دکان یک op ِ تازه از «نسخهٔ پیش» گرفت و دودآزمایی همان را هم پس گرفت
  assert.equal(one.shops[0].ops, ops + 1);
  //  دوباره ⇒ همان op (تکراری)، نه op ِ دوم
  assert.deepEqual(await rh.rollback(h.base(), one), []);
  assert.equal(one.shops[0].ops, ops + 1);

  await db.query(`ALTER TABLE oplog ADD COLUMN rh_required text NOT NULL DEFAULT 'x'`);
  await db.query('ALTER TABLE oplog ALTER COLUMN rh_required DROP DEFAULT');
  try {
    const errors = await rh.rollback(h.base(), one, { tag: 2 });
    assert.ok(errors.length >= 1, 'نوشتنِ نسخهٔ پیش شکست و برگشت سرخ نشد');
    assert.match(errors.join(' | '), /نوشتنِ نسخهٔ پیش/);
    assert.equal(one.shops[0].ops, ops + 1, 'نوشتنِ شکست‌خورده شمرده نمی‌شود');
  } finally {
    await db.query('ALTER TABLE oplog DROP COLUMN rh_required');
  }
});

/*
 * «تا آخرین شماره»: seed ِ بالا روی پایگاهی است که `h.start` با **همهٔ** پرونده‌های `migrations/` ساخت؛ این‌جا
 * سنجیده می‌شود که واقعاً همه (تا بزرگ‌ترین شماره) اعمال شده‌اند، و دُمِ تازهٔ مهاجرت‌ها — از آخرین پرونده به عقب،
 * هر کدام که `rollback/<نام>.down.sql` دارد — یک بار برگشت و دوباره **روی همین دادهٔ seed** اجرا می‌شود؛ همان راهِ
 * «سرورِ نسخهٔ پیش ⇐ `npm run migrate` ِ نسخهٔ تازه» ِ ورک‌فلوی staging، بی نسخهٔ دوم. آخرین مهاجرت باید در این
 * دُم باشد (بی rollback ⇒ سرخ با نامِ پرونده). جدول/ستونِ تازه آزاد است؛ ردیفِ گم‌شده، ستونِ برداشته یا دست بردن در
 * جدولِ حیاتی خطا. (آخرِ پرونده: آزمون‌های بالا اثرِ انگشتِ خودشان را دارند؛ دودآزمایی فقط دکانِ نخست، دومی عمداً خراب است.)
 */
test('مهاجرت‌ها تا آخرین شماره: همه اعمال شده‌اند و دُمِ تازه روی دادهٔ seed دوباره بالا می‌آید', async () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const migrate = require('../src/migrate');
  const files = migrate.files();
  const latest = files[files.length - 1];
  const applied = (await q('SELECT version FROM schema_migrations ORDER BY version')).map((r) => r.version);
  assert.deepEqual(applied, files, 'پایگاهِ تمرین همهٔ پرونده‌های migrations را ندارد');

  const dir = path.join(__dirname, '..', 'migrations', 'rollback');
  const tail = [];
  for (const f of [...files].reverse()) {
    const down = path.join(dir, f.replace(/\.sql$/, '.down.sql'));
    if (!fs.existsSync(down)) break;
    tail.push({ f, down });
  }
  assert.equal(tail[0]?.f, latest, `آخرین مهاجرت (${latest}) پروندهٔ rollback/${latest.replace(/\.sql$/, '.down.sql')} ندارد`);

  //  شمارِ op ِ دکانِ نخست پیش از برگشت (آزمونِ «برگشت» یک فروش افزوده بود)
  const shop = state.shops[0];
  const ops = Number((await db.one('SELECT COUNT(*)::int AS n FROM oplog WHERE account_id = $1', [shop.shopId])).n);
  assert.ok(ops >= shop.ops);

  for (const { down } of tail) await db.query(fs.readFileSync(down, 'utf8'));
  const back = (await q('SELECT version FROM schema_migrations ORDER BY version')).map((r) => r.version);
  assert.deepEqual(back, files.slice(0, files.length - tail.length), 'rollback ردیفِ schema_migrations ِ خودش را برنداشت');

  const old = await rh.fingerprint(q);
  const done = await migrate.run();
  assert.deepEqual(done, tail.map((t) => t.f).reverse());
  const after = await rh.fingerprint(q, { columnsOf: columnsOf(old) });
  const r = rh.compare(old, after);
  assert.deepEqual(r.errors, [], r.errors.join(' | '));
  assert.deepEqual((await q('SELECT version FROM schema_migrations ORDER BY version')).map((x) => x.version), files);
  //  پس از دوباره بالا آمدن همان سرور همهٔ opهای دکان را پس می‌دهد
  assert.deepEqual(await rh.smoke(h.base(), { ...state, shops: [{ ...shop, ops }] }), []);
  //  و بارِ دوم چیزی نمی‌زند
  assert.deepEqual(await migrate.run(), []);
});
