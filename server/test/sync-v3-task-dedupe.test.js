'use strict';
/**
 * PERF-02 (اجرای ۲۰۰ دکان × ۵ دستگاه، `docs/PERF-LOAD-fa.md` ۱۴۰۵/۰۸/۱۹) — «وظیفهٔ بازِ تکراری‌نشو» ِ Sync v3.
 *
 * هر حرکتِ کالا که موجودی را منفی کند (هر قلمِ فروش روی کالای کم‌موجود) وظیفهٔ `negative_stock` را با `dedupe_key`
 * می‌جوید (`sync-v3-store.addConflict`، راهِ دسته‌ای `sync-v3-batch`، `staff-approval`). پیش از مهاجرتِ ۴۵۰ نمایه فقط
 * (app, kind, account, status, at) بود ⇒ همهٔ وظیفه‌های بازِ دکان خوانده و کلید فیلتر می‌شد: در بار ۲۱٬۲۳۳ بار در ۱۰
 * دقیقه، دکانِ بزرگ ۳۴۲ ردیفِ دورریخته و ۹۶ بافر برای هر بار — و با شمارِ کالاهای منفیِ دکان خطی بالا می‌رفت.
 *
 * ⛔ نقشه فقط روی PostgreSQL (planner ِ PGlite را تعمیم نده)؛ رفتار (همان وظیفه، نه دوتا؛ بسته‌شده دوباره باز نمی‌شود؛
 *    کلیدِ دکانِ دیگر هرگز) روی هر دو.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const store = require('../src/lib/sync-v3-store');

const A = { app: 'shop', accountKind: 'shop', accountId: 'shp_dedupe_a' };
const B = { app: 'shop', accountKind: 'shop', accountId: 'shp_dedupe_b' };
const OPEN = 3000;
//  همان متنِ `sync-v3-store.addConflict` و `sync-v3-batch` (راهِ دسته‌ای)
const ONE = `SELECT id FROM sync_v3_conflicts WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND dedupe_key=$4 AND status='open' LIMIT 1`;
const MANY = `SELECT dedupe_key, id FROM sync_v3_conflicts WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND dedupe_key = ANY($4::text[]) AND status='open'`;

test.before(async () => {
  await h.start();
  const seed = (ctx, prefix, n, status, from = 0) => db.query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, dedupe_key, status, at)
     SELECT $4 || g, $1, $2, $3, 'negative_stock', 'products', 'p' || g, 'neg:p' || g, $5, 1000 + g
       FROM generate_series($6::int, $6::int + $7::int - 1) AS g`,
    [ctx.app, ctx.accountKind, ctx.accountId, prefix, status, from, n]);
  await seed(A, 'cf_a_', OPEN, 'open');
  await seed(A, 'cf_ar_', 500, 'dismissed', OPEN);          //  بسته‌شده‌ها: کلیدِ `neg:p3000…` فقط بسته است
  await seed(B, 'cf_b_', 200, 'open');                      //  همان کلیدها در دکانِ دیگر
  if (!db.isPglite()) await db.query('ANALYZE sync_v3_conflicts');
});
test.after(async () => { await h.stop(); });

async function inTx(fn) {
  return db.tx(fn);
}

test('وظیفهٔ باز با همان کلید دوباره ساخته نمی‌شود؛ بسته‌شده باز نمی‌شود؛ کلیدِ دکانِ دیگر هرگز', async () => {
  const cf = { table: 'products', row_id: 'p7' };
  const again = await inTx((c) => store.addConflict(c, A, cf, 'negative_stock', 5000, { dedupe: 'neg:p7', detail: { qty: -3 } }));
  assert.equal(again, 'cf_a_7');
  const row = await db.one('SELECT detail FROM sync_v3_conflicts WHERE id=$1', ['cf_a_7']);
  assert.deepEqual(typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail, { qty: -3 });

  const fresh = await inTx((c) => store.addConflict(c, A, { table: 'products', row_id: 'p3001' }, 'negative_stock', 5001, { dedupe: 'neg:p3001' }));
  assert.notEqual(fresh, 'cf_ar_3001', 'وظیفهٔ بسته‌شده دوباره باز نشد');
  assert.equal((await db.one(`SELECT count(*)::int AS n FROM sync_v3_conflicts WHERE account_id=$1 AND dedupe_key='neg:p3001' AND status='open'`, [A.accountId])).n, 1);

  const other = await inTx((c) => store.addConflict(c, B, { table: 'products', row_id: 'p9' }, 'negative_stock', 5002, { dedupe: 'neg:p9' }));
  assert.equal(other, 'cf_b_9', 'دکانِ B وظیفهٔ خودش را گرفت، نه وظیفهٔ A');
  const n = await db.one(`SELECT count(*)::int AS n FROM sync_v3_conflicts WHERE account_id=$1 AND status='open'`, [A.accountId]);
  assert.equal(n.n, OPEN + 1);

  const many = (await db.query(MANY, [A.app, A.accountKind, A.accountId, ['neg:p1', 'neg:p2', 'neg:p3001', 'neg:none']])).rows;
  assert.deepEqual(many.map((r) => r.dedupe_key).sort(), ['neg:p1', 'neg:p2', 'neg:p3001']);
});

test('PERF-02: جست‌وجوی وظیفهٔ باز با کلید فقط همان ردیف را می‌خواند (مهاجرتِ ۴۵۰)', { skip: db.isPglite() && 'PGlite: نقشه فقط روی PostgreSQL' }, async () => {
  const plan = async (sql, params) => (await db.query(`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${sql}`, params)).rows[0]['QUERY PLAN'][0].Plan;
  const walk = (n, out = []) => { out.push(n); for (const k of n.Plans || []) walk(k, out); return out; };
  for (const [name, sql, params] of [
    ['addConflict', ONE, [A.app, A.accountKind, A.accountId, 'neg:p2999']],
    ['batch', MANY, [A.app, A.accountKind, A.accountId, ['neg:p5', 'neg:p2500', 'neg:p2999']]],
  ]) {
    const nodes = walk(await plan(sql, params));
    const scan = nodes.find((x) => x['Relation Name'] === 'sync_v3_conflicts');
    const removed = nodes.reduce((s, x) => s + (x['Rows Removed by Filter'] || 0) + (x['Rows Removed by Index Recheck'] || 0), 0);
    const buffers = nodes[0]['Shared Hit Blocks'] + nodes[0]['Shared Read Blocks'];
    assert.equal(scan && scan['Index Name'], 'ix_sync_v3_conflicts_open_dedupe', `${name}: ${nodes.map((x) => `${x['Node Type']}(${x['Index Name'] || x['Relation Name'] || ''})`).join(' > ')}`);
    assert.equal(removed, 0, `${name}: ردیفِ دورریخته`);
    assert.ok(buffers <= 12, `${name}: ${buffers} بافر برای ${OPEN} وظیفهٔ باز`);
  }
});
