'use strict';
/**
 * PERF-02 — درستی زیرِ بار: آمیزهٔ `scale` ِ `scripts/load-test.mjs` (فروش، ویرایشِ بهای کالا از چند دستگاه، حرکتِ کالا،
 * مشتری، نگه‌داشته، ویترین و سفارش، اشتراک، پنلِ مدیر) چند ثانیه روی همین پایگاهِ آزمون با `--check`، سپس
 * `scripts/load-check.js`: هیچ op ِ گم یا دوتایی، pull ِ کامل، جمعِ فروش و حرکتِ هر دکان، برندهٔ LWW، جدایی دکان‌ها و
 * هیچ ۵xx ِ کنترل‌نشده.
 *
 * ⛔ هیچ سنجهٔ زمانی (همان قاعدهٔ `load-smoke.test.js`): بارِ کم، مهلتِ بلندِ درخواست؛ فقط درستی.
 * شمارش (انبارگردانی): شمارش‌های بستهٔ پیشینِ هر دکان (`--count-history`) و شمارشِ بازِ آمیزه؛ پس از هر شمارش دفترِ کالا تا HLC ِ آن =
 * شمرده‌شده (`counts`).
 * آزمونِ دوم نشان می‌دهد سنجه تهی نیست: روی همان پایگاه یک op پاک، جمعِ یک فروش، بهای یک کالا، شمردهٔ یک شمارش و دکانِ یک ردیف عوض،
 * یک insert ِ دوتایی و در دفتر یک op ِ رسیده و یک ۵۰۰ کم/زیاد می‌شود ⇒ همان سنجه‌ها سرخ و بقیه سبز.
 * PGlite: پایگاه در پوشه (نه حافظه) تا پس از بیرون رفتنِ سرورِ آزمون از همین فرآیند هم باز شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const URL0 = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'load-correct-'));
const DB = /^pglite:/i.test(URL0) ? `pglite:${path.join(TMP, 'db')}` : URL0;
const OUT = path.join(TMP, 'out.json');
const LEDGER = path.join(TMP, 'ledger.json');

let run = null;
test.after(async () => {
  if (require.cache[require.resolve('../src/db')]) await require('../src/db').closeDb().catch(() => {});
  fs.rmSync(TMP, { recursive: true, force: true });
});

test('آمیزهٔ scale زیرِ بار: هیچ op ِ گم یا دوتایی، جمع‌های هر دکان، LWW و جدایی دکان‌ها سبز', { timeout: 240_000 }, () => {
  const r = spawnSync(process.execPath, [
    'scripts/load-test.mjs', '--db', DB, '--profile', 'scale', '--check', '--no-budget', '--quiet',
    '--shops', '4', '--devices', '3', '--scale', '0.002', '--small-scale', '0.001', '--history', '300',
    '--store-shops', '3', '--checkout-shops', '2', '--warmup', '2', '--rate', '12', '--duration', '12', '--count-history', '60,40,30',
    '--timeout', '120000', '--json', OUT, '--ledger', LEDGER,
  ], { cwd: ROOT, encoding: 'utf8', timeout: 220_000 });
  assert.equal(r.status, 0, `${r.stdout.slice(-2500)}\n${r.stderr.slice(-1500)}`);
  run = JSON.parse(fs.readFileSync(OUT, 'utf8'));
  const by = Object.fromEntries(run.check.checks.map((c) => [c.name, c]));
  for (const name of ['ops.lost', 'ops.unresolved', 'ops.dup', 'pull.complete', 'totals.sales', 'totals.stock', 'counts', 'lww.prices',
    'customers', 'held', 'orders', 'tenant', 'http.5xx']) {
    assert.ok(by[name], `سنجهٔ ${name} اجرا شد`);
    assert.equal(by[name].ok, true, `${name}: ${by[name].detail} ${JSON.stringify(by[name].samples || [])}`);
  }
  //  تهی نیست: تاریخچه و گرم‌کردن همیشه فروش، op ِ دستگاهِ دیگر برای pull و سفارشِ ویترین دارند
  const L = JSON.parse(fs.readFileSync(LEDGER, 'utf8'));
  assert.ok(L.ops.length > 500, `op در دفتر: ${L.ops.length}`);
  assert.ok(Object.values(L.sales).reduce((s, x) => s + x.n, 0) > 50);
  assert.ok(Object.values(L.counts).reduce((s, n) => s + n, 0) >= 60 + 3 * 40, `ردیفِ شمارش: ${JSON.stringify(L.counts)}`);
  assert.ok(L.devices.every((d) => d.seen.length > 0), 'هر دستگاه op ِ دستگاه‌های دیگر را دید');
  assert.ok(Object.values(L.checkouts).reduce((s, x) => s + x.ok, 0) >= 6, 'سفارشِ ویترین');
  assert.ok(run.profiles[0].endpoints['sync3.push'].n > 0 && run.profiles[0].endpoints['sync3.pull'].n > 0);
});

test('سنجهٔ درستی تهی نیست: خطای کاشته در پایگاه و دفتر همان سنجه را سرخ می‌کند', { timeout: 60_000 }, async (t) => {
  if (!run) { t.skip('اجرای بار نشد'); return; }
  process.env.DATABASE_URL = DB;
  const db = require('../src/db');
  const { check } = require('../scripts/load-check.js');
  const L = JSON.parse(fs.readFileSync(LEDGER, 'utf8'));
  const ids = L.shops.map((s) => s.shopId);
  const before = await check(db, L);
  assert.equal(before.ok, true, JSON.stringify(before.checks.filter((c) => !c.ok)));

  const [shopA, shopB] = L.shops;
  const one = async (sql, p) => (await db.query(sql, p)).rows[0];
  //  ۱) op ِ پذیرفتهٔ یک فروش پاک ⇒ ops.lost
  const sale = await one(`SELECT op_id FROM sync_v3_ops WHERE account_id=$1 AND op_id LIKE 'lo-ld-%-s' ORDER BY server_seq LIMIT 1`, [shopA.shopId]);
  await db.query('DELETE FROM sync_v3_ops WHERE account_id=$1 AND op_id=$2', [shopA.shopId, sale.op_id]);
  //  ۲) جمعِ یک فروش + ۱ ⇒ totals.sales
  await db.query(`UPDATE sync_v3_rows SET data = jsonb_set(data, '{finalTotalMinor}', to_jsonb((data->>'finalTotalMinor')::bigint + 1))
                   WHERE account_id=$1 AND table_name='sales' AND row_id = (SELECT row_id FROM sync_v3_rows WHERE account_id=$1
                     AND table_name='sales' AND row_id LIKE 'ls-%' ORDER BY row_id LIMIT 1)`, [shopB.shopId]);
  //  ۳) بهای کالای دست‌نخورده ⇒ lww.prices
  const pid = Object.keys(L.seedPrices[shopA.index])[0];
  await db.query(`UPDATE sync_v3_rows SET data = jsonb_set(data, '{salePriceMinor}', '1') WHERE account_id=$1 AND table_name='products' AND row_id=$2`,
    [shopA.shopId, pid]);
  //  ۴) ردیفِ حرکتِ دکانِ A در دکانِ B ⇒ tenant و totals.stock
  await db.query(`UPDATE sync_v3_rows SET account_id=$2 WHERE account_id=$1 AND table_name='stockMovements' AND row_id = (SELECT row_id
                    FROM sync_v3_rows WHERE account_id=$1 AND table_name='stockMovements' AND row_id LIKE 'ls-%' ORDER BY row_id LIMIT 1)`,
  [shopA.shopId, shopB.shopId]);
  //  ۵) دو insert برای یک ردیف ⇒ ops.dup
  await db.query(`INSERT INTO sync_v3_ops (app, account_kind, account_id, op_id, device_id, origin, tx_id, hlc, client_hlc, skewed,
                    table_name, row_id, kind, fields, effect, status, received_at)
                  SELECT app, account_kind, account_id, op_id || 'x', device_id, origin, tx_id, hlc, client_hlc, skewed,
                    table_name, row_id, kind, fields, effect, status, received_at
                    FROM sync_v3_ops WHERE app='shop' AND account_id = ANY($1) AND op_id LIKE 'lo-ld-%-m0' ORDER BY server_seq LIMIT 1`, [ids]);
  //  ۶) شمردهٔ تازه‌ترین شمارشِ یک کالا ⇒ counts
  await db.query(`UPDATE sync_v3_rows SET data = jsonb_set(data, '{countedQtyMilli}', to_jsonb((data->>'countedQtyMilli')::bigint + 1000))
                   WHERE account_id=$1 AND table_name='stockCounts' AND row_id = (SELECT row_id FROM sync_v3_rows WHERE account_id=$1
                     AND table_name='stockCounts' AND row_id LIKE 'lk-%' ORDER BY row_hlc DESC LIMIT 1)`, [shopA.shopId]);
  //  ۷) دفتر: op ای که دستگاه دید کم، و یک ۵۰۰ ⇒ pull.complete و http.5xx
  const d = L.devices.find((x) => x.seen.length);
  d.seen = d.seen.slice(1);
  L.http['sync3.pull'] = { ...(L.http['sync3.pull'] || {}), '500:internal': 1 };

  const after = await check(db, L);
  const red = after.checks.filter((c) => !c.ok).map((c) => c.name).sort();
  assert.deepEqual(red, ['counts', 'http.5xx', 'lww.prices', 'ops.dup', 'ops.lost', 'pull.complete', 'tenant', 'totals.sales', 'totals.stock'],
    JSON.stringify(after.checks.map((c) => [c.name, c.ok, c.detail])));
});
