'use strict';
/**
 * PERF-06 — «نتایجِ PGlite و PostgreSQL جدا ثبت شود» (§۲۹.۸؛ §۲۹.۶ بند ۸: یکی را به دیگری تعمیم نده).
 *
 * `bench/db-compare.mjs` دو خروجیِ `bench/server.mjs` (یکی روی PostgreSQL، یکی روی PGlite) را
 * کنارِ هم می‌گذارد، بی میانگین گرفتن و بی جابه‌جا کردن:
 *   ۱) هر ستون فقط از پروندهٔ خودش؛ نسبت = PGlite ÷ PostgreSQL روی p50.
 *   ۲) پروندهٔ نادرست (درایورِ جابه‌جا) یا مقیاسِ ناهمسان ⇒ خطا، نه جدولِ گمراه‌کننده.
 *   ۳) سنجه‌ای که فقط در یکی هست ⇒ «—» در دیگری، نه صفر.
 *   ۴) انفجار و نقشهٔ پرس‌وجو هم جدا، با نشانِ Seq Scan.
 *   ۵) خطِ فرمان: خروجیِ Markdown، و کدِ ۲ برای ورودیِ نادرست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');

const FILE = path.join(__dirname, '..', '..', 'bench', 'db-compare.mjs');
const load = () => import(pathToFileURL(FILE).href);

const s = (p50, p95, p99, extra = {}) => ({ n: 30, p50, p95, p99, max: p99, ...extra });
function result(driver, over = {}) {
  return {
    at: '2026-10-26T07:52:00.000Z',
    env: { driver, cores: 4, node: 'v22.23.3', ...(driver === 'postgres' ? { server: '16.15' } : {}) },
    scale: 1,
    dataset: { syncRows: 595717 },
    sync: {
      pullFirstPage: driver === 'postgres' ? s(7.1, 11.2, 13.3) : s(17.3, 22.2, 24),
      pushSale: driver === 'postgres' ? s(7.7, 10.3, 13.3) : s(15.4, 16.4, 16.9),
      catchUp: driver === 'postgres'
        ? { ops: 598277, pages: 599, totalMs: 5979, page: s(8, 12.3, 18.2) }
        : { ops: 598277, pages: 599, totalMs: 16925, page: s(25.6, 30.8, 33.1) },
    },
    catalog: driver === 'postgres' ? { publicSearch: s(47.2, 50.3, 53.4) } : { publicSearch: s(110.3, 115.2, 116.5), onlyLite: s(1, 2, 3) },
    orders: {},
    burst: driver === 'postgres'
      ? { devices: 10, p50: 65.5, p95: 77.4, p99: 91.7, rps: 144, errors: 0 }
      : { devices: 10, p50: 130.9, p95: 260.4, p99: 367.9, rps: 67, errors: 0 },
    plans: driver === 'postgres'
      ? { snapshot: { ms: 721.4, seqScan: true, nodes: 'Gather Merge > Sort > Seq Scan(sync_rows)' } }
      : { snapshot: { ms: 1382.2, seqScan: false, nodes: 'Index Scan(sync_rows_pkey)' } },
    memory: { rssMB: driver === 'postgres' ? 495 : 931 },
    totalMs: driver === 'postgres' ? 62000 : 122000,
    ...over,
  };
}

test('PERF-06: هر ستون از پروندهٔ خودش، نسبت روی p50، نبودن «—»', async () => {
  const { compare, markdown } = await load();
  const c = compare(result('postgres'), result('pglite'));
  const row = (k) => c.rows.find((r) => r.key === k);
  assert.deepEqual([row('sync.pullFirstPage').pg.p95, row('sync.pullFirstPage').pglite.p95], [11.2, 22.2]);
  assert.equal(row('sync.pullFirstPage').ratio, 2.4);              // 17.3 / 7.1
  assert.equal(row('catalog.publicSearch').ratio, 2.3);            // 110.3 / 47.2
  //  همگام‌سازیِ کامل: کلِ زمان و صفحه، هر دو جدا
  assert.deepEqual([row('sync.catchUp').pg.totalMs, row('sync.catchUp').pglite.totalMs], [5979, 16925]);
  assert.equal(row('sync.catchUp').pglite.p95, 30.8);
  //  فقط در PGlite ⇒ PostgreSQL تهی است، نه صفر
  assert.equal(row('catalog.onlyLite').pg, null);
  assert.equal(row('catalog.onlyLite').ratio, null);

  const md = markdown(c);
  assert.match(md, /PostgreSQL 16\.15/);
  assert.match(md, /PGlite/);
  assert.match(md, /\| sync\.pullFirstPage \| 7\.1 \/ 11\.2 \/ 13\.3 \| 17\.3 \/ 22\.2 \/ 24 \| ×2\.4 \|/);
  assert.match(md, /\| catalog\.onlyLite \| — \| 1 \/ 2 \/ 3 \| — \|/);
  //  انفجار و نقشه‌ها جدا، با نشانِ Seq Scan فقط برای همان پایگاه
  assert.match(md, /burst.*65\.5 \/ 77\.4 \/ 91\.7.*144.*130\.9 \/ 260\.4 \/ 367\.9.*67/);
  assert.match(md, /\| snapshot \| 721\.4ms ⚠️ Seq Scan \| 1382\.2ms \|/);
  //  هیچ عددی میانگینِ دو پایگاه نیست
  assert.doesNotMatch(md, /میانگینِ دو/);
});

test('PERF-06: پروندهٔ جابه‌جا یا مقیاسِ ناهمسان ⇒ خطا', async () => {
  const { compare } = await load();
  assert.throws(() => compare(result('pglite'), result('pglite')), /PostgreSQL/);
  assert.throws(() => compare(result('postgres'), result('postgres')), /PGlite/);
  assert.throws(() => compare(result('postgres'), result('pglite', { scale: 0.1 })), /مقیاس/);
  assert.throws(() => compare({}, result('pglite')), /PostgreSQL/);
});

test('PERF-06: خطِ فرمان — Markdown به stdout و پرونده؛ ورودیِ نادرست کدِ ۲', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'db-compare-'));
  try {
    const pg = path.join(dir, 'pg.json');
    const lite = path.join(dir, 'lite.json');
    const out = path.join(dir, 'out.md');
    fs.writeFileSync(pg, JSON.stringify(result('postgres')));
    fs.writeFileSync(lite, JSON.stringify(result('pglite')));
    const ok = spawnSync(process.execPath, [FILE, '--pg', pg, '--pglite', lite, '--md', out], { encoding: 'utf8' });
    assert.equal(ok.status, 0, ok.stderr);
    assert.match(ok.stdout, /sync\.pushSale/);
    assert.equal(fs.readFileSync(out, 'utf8'), ok.stdout);
    const bad = spawnSync(process.execPath, [FILE, '--pg', lite, '--pglite', pg], { encoding: 'utf8' });
    assert.equal(bad.status, 2);
    assert.match(bad.stderr, /PostgreSQL/);
    const missing = spawnSync(process.execPath, [FILE, '--pg', pg], { encoding: 'utf8' });
    assert.equal(missing.status, 2);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
