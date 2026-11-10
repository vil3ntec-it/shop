'use strict';
/**
 * PERF-02 (`docs/PERF-LOAD-fa.md` ۱۴۰۵/۰۸/۱۹ (سوم)) — «شمارشِ دیررسیده» ِ Sync v3 (`v3.lateCount`).
 *
 * هر حرکتِ کالا (هر قلمِ فروش، خرید، تعدیل، و تعدیلِ خودِ شمارش) تازه‌ترین شمارشِ همان کالا با HLC ≥ حرکت را می‌جوید
 * (`sync-v3-store.lateCount`، راهِ تک‌تک)؛ راهِ دسته‌ای (`sync-v3-batch`، `STOCK_SQL`) «این کالا شمارش دارد؟» را برای
 * کالاهای همان push می‌پرسد. پیش از مهاجرتِ ۴۶۵ تنها راه پیشوندِ کلیدِ `sync_v3_rows` تا `table_name` بود ⇒ **همهٔ**
 * ردیف‌های شمارشِ دکان خوانده و `productId` فیلتر می‌شد: خطی با شمارِ ردیف‌های شمارش (دکانِ ۱۰ هزار ردیفی ~۱۰ هزار ردیفِ
 * دورریخته برای هر حرکت).
 *
 * ⛔ نقشه فقط روی PostgreSQL (planner ِ PGlite را تعمیم نده)؛ رفتار (همان شمارش، نه دکانِ دیگر، نه کالای دیگر،
 *    نه خودِ شمارشِ همان حرکت، نه پیش از حرکت) روی هر دو.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const store = require('../src/lib/sync-v3-store');

const A = { app: 'shop', accountKind: 'shop', accountId: 'shp_late_a' };
const B = { app: 'shop', accountKind: 'shop', accountId: 'shp_late_b' };
const PRODUCTS = 1000;
const ROUNDS = 10;                        //  ۱۰ شمارشِ بسته برای هر کالا ⇒ ۱۰ هزار ردیفِ شمارش در دکانِ A
const hlcOf = (wall) => `${String(wall).padStart(13, '0')}-0000-dev`;
//  همان متنِ `sync-v3-store.lateCount` و شاخهٔ شمارشِ `STOCK_SQL` ِ `sync-v3-batch`
const LATE = `SELECT row_id, row_hlc FROM sync_v3_rows
      WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name='stockCounts' AND inserted AND data->>'productId' = $4
        AND row_hlc >= $5 AND row_id <> $6
      ORDER BY row_hlc DESC LIMIT 1`;
const COUNTED = `SELECT 'c', data->>'productId', '0', 0
    FROM sync_v3_rows WHERE app=$1 AND account_kind=$2 AND account_id=$3 AND table_name = 'stockCounts' AND inserted AND data->>'productId' = ANY($4::text[])
   GROUP BY 2`;

test.before(async () => {
  await h.start();
  const seed = (ctx, prefix, products, rounds, wall0) => db.query(
    `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, row_hlc, inserted, created_at, updated_at)
     SELECT $1, $2, $3, 'stockCounts', $4 || r || '-' || p,
            jsonb_build_object('productId', 'p' || p, 'countedQtyMilli', 1000 * r, 'session', $4 || r),
            lpad(($5::bigint + r * 1000 + p)::text, 13, '0') || '-0000-dev', true, 1, 1
       FROM generate_series(0, $6::int - 1) AS p, generate_series(0, $7::int - 1) AS r`,
    [ctx.app, ctx.accountKind, ctx.accountId, prefix, wall0, products, rounds]);
  await seed(A, 'kc-', PRODUCTS, ROUNDS, 1_000_000);
  await seed(B, 'kb-', 50, 2, 9_000_000);                     //  همان کالاها در دکانِ دیگر، با HLC ِ دیرتر
  //  ردیفِ نیمه (درج نشده: فقط update رسیده) هرگز «شمارش» نیست
  await db.query(`INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, row_hlc, inserted, created_at, updated_at)
                  VALUES ($1,$2,$3,'stockCounts','kc-ghost', '{"productId":"p7","countedQtyMilli":1}'::jsonb, $4, false, 1, 1)`,
  [A.app, A.accountKind, A.accountId, hlcOf(8_000_000)]);
  if (!db.isPglite()) await db.query('ANALYZE sync_v3_rows');
});
test.after(async () => { await h.stop(); });

test('شمارشِ دیررسیده: تازه‌ترین شمارشِ همان کالا با HLC ≥ حرکت؛ نه دکانِ دیگر، نه خودِ شمارش، نه ردیفِ نیمه', async () => {
  const late = (ctx, pid, eff, countId = '') => db.tx((c) => store.lateCount(c, ctx, pid, eff, countId));
  //  p7: شمارش‌های ۱٬۰۰۰٬۰۰۷ تا ۱٬۰۰۹٬۰۰۷ (گامِ ۱۰۰۰)
  assert.deepEqual(await late(A, 'p7', hlcOf(1_000_000)), { row_id: 'kc-9-7', row_hlc: hlcOf(1_009_007) });
  assert.deepEqual(await late(A, 'p7', hlcOf(1_009_007)), { row_id: 'kc-9-7', row_hlc: hlcOf(1_009_007) }, 'HLC ِ برابر هم «دیررسیده» است');
  assert.deepEqual(await late(A, 'p7', hlcOf(1_000_000), 'kc-9-7'), { row_id: 'kc-8-7', row_hlc: hlcOf(1_008_007) }, 'خودِ شمارشِ همان حرکت کنار می‌رود');
  assert.equal(await late(A, 'p7', hlcOf(1_009_008)), undefined, 'حرکتِ پس از آخرین شمارش');
  assert.equal(await late(A, 'p-none', hlcOf(0)), undefined, 'کالای بی‌شمارش');
  assert.equal(await late(A, 'p999', hlcOf(1_010_000)), undefined, 'همهٔ شمارش‌های این کالا پیش از حرکت‌اند');
  assert.equal(await late(A, 'p998', hlcOf(1_009_999)), undefined, 'شمارشِ کالای دیگر (p999 در همان HLC) به حساب نمی‌آید');
  //  دکانِ B همان p7 را دیرتر شمرده است؛ A هرگز آن را نمی‌بیند و B فقط شمارشِ خودش را
  assert.deepEqual(await late(B, 'p7', hlcOf(0)), { row_id: 'kb-1-7', row_hlc: hlcOf(9_001_007) });
  assert.equal(await late(B, 'p70', hlcOf(0)), undefined);

  const counted = async (ctx, pids) => (await db.query(`SELECT p FROM (${COUNTED}) AS x(k, p, s, odd)`, [ctx.app, ctx.accountKind, ctx.accountId, pids])).rows.map((r) => r.p).sort();
  assert.deepEqual(await counted(A, ['p1', 'p999', 'p1000', 'p-none']), ['p1', 'p999']);
  assert.deepEqual(await counted(B, ['p1', 'p999']), ['p1']);
});

test('PERF-02: شمارشِ دیررسیده فقط ردیف‌های همان کالا را می‌خواند، نه همهٔ شمارش‌های دکان (مهاجرتِ ۴۶۵)', { skip: db.isPglite() && 'PGlite: نقشه فقط روی PostgreSQL' }, async () => {
  const plan = async (sql, params) => (await db.query(`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${sql}`, params)).rows[0]['QUERY PLAN'][0].Plan;
  const walk = (n, out = []) => { out.push(n); for (const k of n.Plans || []) walk(k, out); return out; };
  //  سقفِ بافر: هر کالای پرسیده یک فرودِ نمایه (~۳) + ردیف‌های همان کالا (این‌جا ۱۰)؛ پیش از ۴۶۵ همهٔ ۱۰ هزار ردیف (صدها بافر)
  for (const [name, sql, params, maxBuffers] of [
    ['lateCount: حرکتِ تازه (بی شمارشِ پس از آن)', LATE, [A.app, A.accountKind, A.accountId, 'p500', hlcOf(2_000_000), ''], 16],
    ['lateCount: حرکتِ دیررسیده', LATE, [A.app, A.accountKind, A.accountId, 'p999', hlcOf(1_000_000), ''], 16],
    ['batch STOCK_SQL (شمارش)', COUNTED, [A.app, A.accountKind, A.accountId, ['p5', 'p500', 'p999', 'p-none']], 4 * 16],
  ]) {
    const nodes = walk(await plan(sql, params));
    const scan = nodes.find((x) => x['Relation Name'] === 'sync_v3_rows');
    const removed = nodes.reduce((s, x) => s + (x['Rows Removed by Filter'] || 0) + (x['Rows Removed by Index Recheck'] || 0), 0);
    const buffers = nodes[0]['Shared Hit Blocks'] + nodes[0]['Shared Read Blocks'];
    const shape = nodes.map((x) => `${x['Node Type']}(${x['Index Name'] || x['Relation Name'] || ''})`).join(' > ');
    assert.equal(scan && scan['Index Name'], 'ix_sync_v3_rows_counts_product', `${name}: ${shape}`);
    assert.equal(removed, 0, `${name}: ردیفِ دورریخته (${shape})`);
    assert.ok(buffers <= maxBuffers, `${name}: ${buffers} بافر برای ${PRODUCTS * ROUNDS} ردیفِ شمارش (${shape})`);
  }
});
