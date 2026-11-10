#!/usr/bin/env node
'use strict';
/**
 * سنجشِ «شمارشِ دیررسیده» ِ Sync v3 (`v3.lateCount`) در سه اندازهٔ دکان — پیش و پس از نمایهٔ ۴۶۵ (`docs/PERF-LOAD-fa.md` ۱۴۰۵/۰۸/۱۹ (سوم)).
 *
 *   TEST_DATABASE_URL=postgres://…/x_bench node scripts/late-count-bench.js [بار=400]
 *
 * سه دکان با ۱۰۰، ۱٬۰۰۰ و ۱۰٬۰۰۰ ردیفِ شمارش (انبارگردانی‌های بستهٔ کامل + یک شمارشِ بازِ نیمه‌کاره)، کنارِ دفترِ
 * واقع‌نمای فروش و حرکت؛ برای هر دکان [بار] پرسشِ `sync-v3-store.lateCount` (۸۰٪ حرکتِ تازه پس از همهٔ شمارش‌ها، ۲۰٪
 * حرکتِ دیررسیده) و شاخهٔ شمارشِ `STOCK_SQL` ِ راهِ دسته‌ای (۱ تا ۴ کالا). دو دور به نوبت: نمایه برداشته (پیش) و ساخته (پس)،
 * تا بارِ ماشین هر دو را یکسان بزند. خروجی: نقشه، میانگینِ بافر و ردیفِ دورریخته (`EXPLAIN (ANALYZE, BUFFERS)`)،
 * و p50/p95 ِ زمانِ اجرای خودِ پرس‌وجو. ⛔ پایگاه پاک می‌شود (`test/helpers`): فقط نامی با test/bench (PGlite نه: نقشه).
 */
process.env.SYNC_V3_ENABLED = '1';
const url = process.env.TEST_DATABASE_URL || '';
const dbName = () => { try { return decodeURIComponent(new URL(url).pathname.slice(1)); } catch { return ''; } };
if (url.startsWith('pglite:') || !/test|bench/.test(dbName())) {
  console.error('⛔ فقط PostgreSQL و پایگاهی که نامش test یا bench دارد (پاک می‌شود)');
  process.exit(2);
}
const os = require('os');
const { performance } = require('perf_hooks');
const h = require('../test/helpers');
const db = require('../src/db');

const CALLS = Math.max(20, Number(process.argv[2]) || 400);
const SIZES = [[100, 100], [1000, 400], [10000, 1000]];   //  [ردیفِ شمارش، کالا]
const INDEX = 'ix_sync_v3_rows_counts_product';
const CREATE = `CREATE INDEX ${INDEX} ON sync_v3_rows (account_id, (data->>'productId'), row_hlc) WHERE app = 'shop' AND table_name = 'stockCounts'`;
//  همان متنِ `sync-v3-store.lateCount` و شاخهٔ شمارشِ `STOCK_SQL` ِ `sync-v3-batch`
const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const LATE = `SELECT row_id, row_hlc FROM sync_v3_rows
      WHERE ${ACC} AND table_name='stockCounts' AND inserted AND data->>'productId' = $4
        AND row_hlc >= $5 AND row_id <> $6
      ORDER BY row_hlc DESC LIMIT 1`;
const COUNTED = `SELECT 'c', data->>'productId', '0', 0
    FROM sync_v3_rows WHERE ${ACC} AND table_name = 'stockCounts' AND inserted AND data->>'productId' = ANY($4::text[])
   GROUP BY 2`;
const hlcOf = (wall) => `${String(wall).padStart(13, '0')}-0000-bench`;

let rs = 20261109;
const rnd = () => { rs = (rs * 1103515245 + 12345) % 2147483648; return rs / 2147483648; };
const pick = (n) => Math.floor(rnd() * n);

async function seed() {
  const shops = [];
  for (const [lines, products] of SIZES) {
    const ctx = { app: 'shop', accountKind: 'shop', accountId: `shp_lcb_${lines}` };
    const rounds = Math.ceil(lines / products);
    //  شمارش‌ها: دورِ r برای کالای p با HLC ِ 10^9 + r·10^6 + p؛ دورِ آخر «باز» و نیمه‌کاره
    await db.query(
      `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, row_hlc, inserted, created_at, updated_at)
       SELECT $1, $2, $3, 'stockCounts', 'k' || i, jsonb_build_object('productId', 'p' || (i % $5), 'countedQtyMilli', 1000 * (i % 7),
              'session', 'cs' || (i / $5), 'status', CASE WHEN i / $5 = $6 - 1 THEN 'open' ELSE 'closed' END),
              lpad((1000000000 + (i / $5) * 1000000 + (i % $5))::text, 13, '0') || '-0000-bench', true, 1, 1
         FROM generate_series(0, $4::int - 1) AS i`,
      [ctx.app, ctx.accountKind, ctx.accountId, lines, products, rounds]);
    //  دفترِ کنارش: ۲۰ حرکت و ۵ قلمِ فروش برای هر کالا (همان جدول، جدولِ دیگر)
    for (const [table, per] of [['stockMovements', 20], ['saleItems', 5], ['products', 1]]) {
      await db.query(
        `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, row_hlc, inserted, created_at, updated_at)
         SELECT $1, $2, $3, $4, $4 || i, jsonb_build_object('productId', 'p' || (i % $5), 'qtyMilli', -1000),
                lpad((1000000000 + i)::text, 13, '0') || '-0000-bench', true, 1, 1
           FROM generate_series(0, $5::int * $6::int - 1) AS i`,
        [ctx.app, ctx.accountKind, ctx.accountId, table, products, per]);
    }
    shops.push({ ctx, lines, products, rounds, lastHlc: 1000000000 + (rounds - 1) * 1000000 + products });
  }
  await db.query('ANALYZE sync_v3_rows');
  return shops;
}

const walk = (n, out = []) => { out.push(n); for (const k of n.Plans || []) walk(k, out); return out; };
const q = (s, p) => s[Math.min(s.length - 1, Math.max(0, Math.ceil(p * s.length) - 1))];
const r2 = (v) => Math.round(v * 100) / 100;

async function measure(sh, kind) {
  const args = () => {
    if (kind === 'late') {
      const fresh = rnd() < 0.8;
      const eff = fresh ? sh.lastHlc + 1 + pick(1000) : 1000000000 + pick(sh.rounds) * 1000000;
      return [LATE, [...Object.values(sh.ctx), `p${pick(sh.products)}`, hlcOf(eff), '']];
    }
    return [COUNTED, [...Object.values(sh.ctx), Array.from({ length: 1 + pick(4) }, () => `p${pick(sh.products)}`)]];
  };
  const ms = [];
  let buffers = 0, removed = 0, plans = 0;
  const shapes = new Map();
  for (let i = 0; i < CALLS; i++) {
    const [sql, params] = args();
    const t0 = performance.now();
    await db.query(sql, params);
    ms.push(performance.now() - t0);
    if (i % 4 === 0) {
      const nodes = walk((await db.query(`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${sql}`, params)).rows[0]['QUERY PLAN'][0].Plan);
      plans++;
      buffers += nodes[0]['Shared Hit Blocks'] + nodes[0]['Shared Read Blocks'];
      removed += nodes.reduce((s, x) => s + (x['Rows Removed by Filter'] || 0), 0);
      const shape = nodes.filter((x) => /Scan/.test(x['Node Type'])).map((x) => `${x['Node Type']}(${x['Index Name'] || x['Relation Name']})`).join('+');
      shapes.set(shape, (shapes.get(shape) || 0) + 1);
    }
  }
  ms.sort((a, b) => a - b);
  return { p50: r2(q(ms, 0.5)), p95: r2(q(ms, 0.95)), buffers: r2(buffers / plans), removed: r2(removed / plans),
    plan: [...shapes.entries()].sort((a, b) => b[1] - a[1])[0][0] };
}

(async () => {
  await h.start();
  const shops = await seed();
  const out = [];
  //  دو دور به نوبت (پیش، پس، پیش، پس): بارِ ماشین هر دو را یکسان بزند
  for (const round of [0, 1]) {
    for (const withIndex of [false, true]) {
      await db.query(`DROP INDEX IF EXISTS ${INDEX}`);
      if (withIndex) await db.query(CREATE);
      await db.query('ANALYZE sync_v3_rows');
      const load = os.loadavg()[0].toFixed(1);
      for (const sh of shops) for (const kind of ['late', 'batch']) {
        const r = await measure(sh, kind);
        out.push({ round, index: withIndex, lines: sh.lines, kind, load, ...r });
        console.log(`${withIndex ? 'پس ' : 'پیش'} دور ${round} بار ${load}  ${String(sh.lines).padStart(6)} ردیف  ${kind.padEnd(5)}  ` +
          `p50 ${r.p50}ms p95 ${r.p95}ms  بافر ${r.buffers}  دورریخته ${r.removed}  ${r.plan}`);
      }
    }
  }
  if (process.env.BENCH_JSON) require('fs').writeFileSync(process.env.BENCH_JSON, JSON.stringify({ at: new Date().toISOString(), calls: CALLS, cpu: os.cpus()[0].model, out }, null, 2));
  await h.stop();
})().catch((e) => { console.error(e); process.exit(1); });
