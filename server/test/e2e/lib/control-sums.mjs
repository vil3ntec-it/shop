/**
 * «شمارش و جمعِ کنترل» ِ یک پایگاه — REC-01 و REL-01 (`recovery.e2e.mjs`).
 *
 *   • هر جدولِ public: شمارِ ردیف، هشِ بی‌ترتیبِ متنِ کاملِ ردیف‌ها (`to_jsonb`) و جمعِ هر
 *     ستونِ عددیِ `*_minor` (پول)؛
 *   • دفترِ همگام‌شدهٔ دکان‌ها (`sync_rows`): برای هر مجموعه (sales، saleItems، stockMovements،
 *     transactions، …) شمارِ ردیفِ زنده و جمعِ **هر** فیلدِ عددی (finalTotal، qty، amount، …) —
 *     بی‌نیاز به دانستنِ شکلِ مدل، پس فیلدِ تازهٔ برنامه هم شمرده می‌شود.
 *
 * درون‌فرایند: `fingerprint((sql, params) => rows)`. فرایندِ جدا («محیطِ جدا»):
 *   node control-sums.mjs load <پوشه> <tar.gz>   پوشهٔ تازه از پشتیبانِ رمزگشایی‌شده (PGlite)
 *   node control-sums.mjs sums <پوشه>            JSON ِ fingerprint روی stdout
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';

const require = createRequire(import.meta.url);
const ident = (s) => `"${String(s).replace(/"/g, '""')}"`;

/**
 * @param {(sql:string, params?:any[]) => Promise<any[]>} q
 * @param {{ skipKeys?: Record<string,string[]> }} [o] ستون‌هایی که در هشِ یک جدول نمی‌آیند
 *   (ستونِ تازهٔ یک مهاجرت؛ شمار و جمع همچنان سنجیده می‌شوند)
 */
export async function fingerprint(q, { skipKeys = {} } = {}) {
  const tables = (await q(
    "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name",
  )).map((r) => r.table_name);
  const money = await q(
    `SELECT table_name, column_name FROM information_schema.columns
      WHERE table_schema='public' AND column_name LIKE '%\\_minor' ESCAPE '\\'
        AND data_type IN ('bigint','integer','smallint','numeric') ORDER BY 1, 2`,
  );
  const tablesOut = {};
  for (const t of tables) {
    const skip = skipKeys[t] || [];
    const [r] = await q(
      `SELECT count(*)::text AS n, md5(coalesce(string_agg(h, '' ORDER BY h), '')) AS hash
         FROM (SELECT md5((to_jsonb(x) - $1::text[])::text) AS h FROM ${ident(t)} x) s`,
      [skip],
    );
    const sums = {};
    for (const c of money.filter((m) => m.table_name === t)) {
      const [s] = await q(`SELECT coalesce(sum(${ident(c.column_name)}), 0)::text AS s FROM ${ident(t)}`);
      sums[c.column_name] = s.s;
    }
    tablesOut[t] = { rows: Number(r.n), hash: r.hash, sums };
  }
  const ledger = {};
  if (tables.includes('sync_rows')) {
    for (const r of await q(
      `SELECT account_id, table_name, count(*)::text AS n FROM sync_rows WHERE deleted_at IS NULL GROUP BY 1, 2 ORDER BY 1, 2`,
    )) (ledger[`${r.account_id}/${r.table_name}`] ||= { rows: 0, sums: {} }).rows = Number(r.n);
    for (const r of await q(
      `SELECT s.account_id, s.table_name, f.key, sum((f.value #>> '{}')::numeric)::text AS total
         FROM sync_rows s, jsonb_each(s.data) f
        WHERE s.deleted_at IS NULL AND jsonb_typeof(f.value) = 'number'
        GROUP BY 1, 2, 3 ORDER BY 1, 2, 3`,
    )) (ledger[`${r.account_id}/${r.table_name}`] ||= { rows: 0, sums: {} }).sums[r.key] = r.total;
  }
  return { tables: tablesOut, ledger };
}

/**
 * تفاوت‌های دو fingerprint (خالی = یکسان).
 * @param {{ only?: string[], ignore?: string[], countsOnly?: string[] }} [o]
 *   only: فقط همین جدول‌ها؛ ignore: این جدول‌ها نه؛ countsOnly: فقط شمار و جمع، نه هش
 */
export function diff(a, b, { only = null, ignore = [], countsOnly = [] } = {}) {
  const out = [];
  const names = [...new Set([...Object.keys(a.tables), ...Object.keys(b.tables)])].sort()
    .filter((t) => (!only || only.includes(t)) && !ignore.includes(t));
  for (const t of names) {
    const x = a.tables[t]; const y = b.tables[t];
    if (!x || !y) { out.push(`${t}: ${x ? 'در دومی نیست' : 'در اولی نیست'}`); continue; }
    if (x.rows !== y.rows) out.push(`${t}: شمار ${x.rows} ⇐ ${y.rows}`);
    for (const k of new Set([...Object.keys(x.sums), ...Object.keys(y.sums)])) {
      if (x.sums[k] !== y.sums[k]) out.push(`${t}.${k}: جمع ${x.sums[k]} ⇐ ${y.sums[k]}`);
    }
    if (!countsOnly.includes(t) && x.rows === y.rows && x.hash !== y.hash) out.push(`${t}: محتوا عوض شد`);
  }
  if (!only) {
    for (const k of [...new Set([...Object.keys(a.ledger), ...Object.keys(b.ledger)])].sort()) {
      const x = a.ledger[k] || { rows: 0, sums: {} }; const y = b.ledger[k] || { rows: 0, sums: {} };
      if (x.rows !== y.rows) out.push(`دفتر ${k}: شمار ${x.rows} ⇐ ${y.rows}`);
      for (const f of new Set([...Object.keys(x.sums), ...Object.keys(y.sums)])) {
        if (x.sums[f] !== y.sums[f]) out.push(`دفتر ${k}.${f}: جمع ${x.sums[f]} ⇐ ${y.sums[f]}`);
      }
    }
  }
  return out;
}

/* ════════════════════════ CLI: فرایندِ جدا روی پوشهٔ PGlite ════════════════════════ */

async function cli([cmd, dir, file]) {
  const { PGlite } = require('@electric-sql/pglite');
  if (cmd === 'load') {
    if (fs.existsSync(dir) && fs.readdirSync(dir).length) throw new Error(`پوشهٔ مقصد خالی نیست: ${dir}`);
    const blob = new File([fs.readFileSync(file)], 'restore.tar.gz', { type: 'application/x-gzip' });
    const lite = await PGlite.create(dir, { loadDataDir: blob });
    await lite.close();
    console.log('LOADED');
    return;
  }
  if (cmd === 'sums') {
    const lite = await PGlite.create(dir);
    const fp = await fingerprint(async (sql, params) => (params ? await lite.query(sql, params) : (await lite.exec(sql)).at(-1)).rows);
    await lite.close();
    console.log(JSON.stringify(fp));
    return;
  }
  throw new Error(`فرمانِ ناشناخته: ${cmd}`);
}

if (process.argv[1] && fs.realpathSync(process.argv[1]) === fs.realpathSync(new URL(import.meta.url).pathname)) {
  cli(process.argv.slice(2)).catch((e) => { console.error(e.stack || e.message); process.exit(1); });
}
