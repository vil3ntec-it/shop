'use strict';
/**
 * تمرینِ بازیابی و تطبیق — §۱۲ِ برنامهٔ امنیت، DATA-07 و DATA-10 (`docs/security/data-fa.md`).
 *
 * «فایلِ پشتیبان هست» یعنی هیچ؛ پشتیبان وقتی پشتیبان است که برگردد و **همان داده** باشد.
 *
 *   fingerprint(q)   اثرِ انگشتِ دیتابیس: شمارِ ردیفِ هر جدول، جمعِ هر ستونِ پول/مقدار (`*_minor`،
 *                    `*_milli`، `price`، `amount`…) و، برای هر جدولی که ستونِ `data` ِ jsonb دارد، شمار و
 *                    جمعِ هر کلیدِ عددیِ دفتر (جمعِ فاکتورها، مبلغِ برگشت‌ها، مقدارِ ورودِ انبار، …) به تفکیکِ
 *                    `table_name` (دفترِ Sync). فقط عدد — هیچ نام، تلفن، متن یا شناسه‌ای.
 *   compare(a, b)    تفاوت‌ها؛ خالی یعنی برابر.
 *   drill(...)       پشتیبان را در دیتابیسی **جدا** برمی‌گرداند (PGlite در حافظه، یا دیتابیسِ تازهٔ
 *                    PostgreSQL)، اثرِ انگشتش را با `manifest` ِ همان پشتیبان (در `meta.json`، گرفته‌شده
 *                    در همان snapshot ِ pg_dump) می‌سنجد، زمانِ بازیابی (RTO) و سنِ پشتیبان (RPO) را
 *                    می‌گوید، و با `migrate` مهاجرت‌های این نسخه را روی همان کپی می‌زند و دوباره تطبیق می‌دهد.
 *
 * ⛔ هرگز روی دیتابیسِ زنده نمی‌نویسد: هدف یا PGlite ِ تازه در حافظه است یا دیتابیسی که خودش می‌سازد
 *    (`shop_drill_<زمان>`) و در پایان می‌اندازد؛ نشانیِ هدفی که برابرِ DATABASE_URL باشد رد می‌شود.
 */
const fsp = require('fs/promises');
const path = require('path');
const { createHash } = require('crypto');

const NUMERIC_TYPES = new Set(['smallint', 'integer', 'bigint', 'numeric']);
/** ستون‌هایی که پول یا مقدارند (قاعدهٔ نام‌گذاریِ مهاجرت‌ها: `_minor` پول، `_milli` مقدار) */
const MONEY_COLUMN = /(_minor|_milli)$|^(price|final_price|prev_price|amount|commission_bp)$/;

const ident = (n) => `"${String(n).replace(/"/g, '""')}"`;
/** عددِ متنیِ numeric بی صفرهای بی‌معنا، تا 3.50 و 3.5 یکی باشند */
function normNum(s) {
  if (s === null || s === undefined) return '0';
  let t = String(s);
  if (t.includes('.')) t = t.replace(/0+$/, '').replace(/\.$/, '');
  return t === '-0' ? '0' : t;
}

function sortKeys(o) {
  return Object.keys(o).sort().reduce((acc, k) => { acc[k] = o[k]; return acc; }, {});
}

/**
 * @param {(text:string, params?:any[]) => Promise<{rows:any[]}>} q  پرس‌وجو روی دیتابیسِ هدف
 */
async function fingerprint(q) {
  const tables = (await q(
    `SELECT table_name FROM information_schema.tables
      WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name`
  )).rows.map((r) => r.table_name);
  const cols = (await q(
    `SELECT table_name, column_name, data_type FROM information_schema.columns
      WHERE table_schema = 'public' ORDER BY table_name, ordinal_position`
  )).rows;
  const byTable = new Map();
  for (const c of cols) {
    if (!byTable.has(c.table_name)) byTable.set(c.table_name, []);
    byTable.get(c.table_name).push(c);
  }

  const counts = {};
  const columns = {};
  const ledger = {};
  for (const t of tables) {
    counts[t] = normNum((await q(`SELECT count(*)::bigint AS n FROM ${ident(t)}`)).rows[0].n);
    const cs = byTable.get(t) || [];
    const money = cs.filter((c) => NUMERIC_TYPES.has(c.data_type) && MONEY_COLUMN.test(c.column_name));
    if (money.length) {
      const sel = money.map((c, i) => `COALESCE(sum(${ident(c.column_name)}), 0)::text AS c${i}`).join(', ');
      const row = (await q(`SELECT ${sel} FROM ${ident(t)}`)).rows[0];
      money.forEach((c, i) => { columns[`${t}.${c.column_name}`] = normNum(row[`c${i}`]); });
    }
    if (cs.some((c) => c.column_name === 'data' && c.data_type === 'jsonb')) {
      const group = cs.some((c) => c.column_name === 'table_name') ? 'x.table_name' : `''`;
      const rows = (await q(
        `SELECT ${group} AS g, e.key AS k, count(*)::bigint AS n, sum((e.value)::text::numeric)::text AS s
           FROM ${ident(t)} x,
                jsonb_each(CASE WHEN jsonb_typeof(x.data) = 'object' THEN x.data ELSE '{}'::jsonb END) e
          WHERE jsonb_typeof(e.value) = 'number'
          GROUP BY 1, 2`
      )).rows;
      for (const r of rows) {
        ledger[`${t}${r.g ? `/${r.g}` : ''}.${r.k}`] = `${normNum(r.n)}:${normNum(r.s)}`;
      }
    }
  }
  const body = { version: 1, tables: sortKeys(counts), columns: sortKeys(columns), ledger: sortKeys(ledger) };
  const digest = createHash('sha256').update(JSON.stringify(body)).digest('hex');
  const rows = Object.values(counts).reduce((s, n) => s + BigInt(n), 0n).toString();
  return { ...body, digest, summary: { tables: tables.length, rows, moneyColumns: Object.keys(columns).length, ledgerKeys: Object.keys(ledger).length } };
}

/**
 * تفاوتِ دو اثرِ انگشت. `allowAdded` (پس از مهاجرت): جدول، ستون یا کلیدِ تازه تفاوت شمرده نمی‌شود،
 * ولی در `added` گزارش می‌شود؛ هر چیزِ ازدست‌رفته یا عوض‌شده همیشه تفاوت است.
 */
function compare(expected, actual, { allowAdded = false } = {}) {
  const diffs = [];
  const added = [];
  for (const part of ['tables', 'columns', 'ledger']) {
    const a = expected?.[part] || {};
    const b = actual?.[part] || {};
    for (const k of Object.keys(a)) {
      if (!(k in b)) diffs.push({ part, key: k, expected: a[k], actual: null });
      else if (a[k] !== b[k]) diffs.push({ part, key: k, expected: a[k], actual: b[k] });
    }
    for (const k of Object.keys(b)) {
      if (k in a) continue;
      if (allowAdded) added.push({ part, key: k, actual: b[k] });
      else diffs.push({ part, key: k, expected: null, actual: b[k] });
    }
  }
  return { equal: diffs.length === 0, diffs, added };
}

/* ───────────── بازگرداندن در دیتابیسِ جدا ───────────── */

/** دستهٔ PGlite به شکلِ `q` و «استخر»ی که `migrate.run` می‌خواهد */
function liteHandle(lite) {
  const run = async (text, params) => {
    if (!params || !params.length) {
      const rs = await lite.exec(text);
      return { rows: rs.length ? rs[rs.length - 1].rows : [] };
    }
    return lite.query(text, params);
  };
  return {
    q: run,
    pool: { async connect() { return { query: run, release() {} }; } },
    async close() { await lite.close(); },
  };
}

function isPgliteFile(file) { return /\.pglite\.tar\.gz(\.enc)?$/.test(String(file)); }

/** نشانیِ دیتابیسِ تمرین روی همان سرورِ PostgreSQL: `…/shop_drill_<زمان>` */
function drillUrl(base) {
  const name = `shop_drill_${Date.now()}_${process.pid}`;
  return { name, url: String(base).replace(/\/[^/?]+(\?|$)/, `/${name}$1`), admin: String(base).replace(/\/[^/?]+(\?|$)/, '/postgres$1') };
}

async function pgAdmin(url, text) {
  const { Client } = require('pg');
  const c = new Client({ connectionString: url });
  await c.connect();
  try { return await c.query(text); } finally { await c.end(); }
}

/**
 * پشتیبان را در دیتابیسی جدا برمی‌گرداند.
 * @returns {Promise<{q, pool, close, where:string}>}
 */
async function restoreIsolated(file, { targetUrl = '', keep = false } = {}) {
  const config = require('../config');
  const backup = require('./backup');
  if (isPgliteFile(file)) {
    const { PGlite, types } = require('@electric-sql/pglite');
    const tar = await backup.decode(file);
    const lite = await PGlite.create({
      dataDir: 'memory://',
      loadDataDir: new Blob([tar]),
      parsers: { [types.INT8]: (v) => v, [types.NUMERIC]: (v) => v },
    });
    return { ...liteHandle(lite), where: 'pglite:memory' };
  }
  const base = config.db.url;
  const own = !targetUrl;
  const target = own ? drillUrl(base) : { url: targetUrl, name: '', admin: '' };
  if (String(target.url) === String(base)) throw new Error('هدفِ تمرین همان دیتابیسِ زنده است — رد شد');
  if (own) await pgAdmin(target.admin, `CREATE DATABASE ${ident(target.name)}`);
  try {
    await backup.restore(file, { databaseUrl: target.url });
  } catch (err) {
    if (own && !keep) await pgAdmin(target.admin, `DROP DATABASE IF EXISTS ${ident(target.name)}`).catch(() => {});
    throw err;
  }
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: target.url, max: 2 });
  return {
    q: (text, params) => pool.query(text, params),
    pool,
    where: own ? `postgres:${target.name}` : 'postgres:target',
    async close() {
      await pool.end();
      if (own && !keep) await pgAdmin(target.admin, `DROP DATABASE IF EXISTS ${ident(target.name)}`);
    },
  };
}

async function readMeta(file) {
  try { return JSON.parse(await fsp.readFile(`${file}.meta.json`, 'utf8')); } catch { return null; }
}

/**
 * تمرینِ کامل روی یک فایلِ پشتیبان.
 * @param {object} o
 * @param {string} o.file         فایلِ پشتیبان
 * @param {object} [o.expected]   اثرِ انگشتِ مورد انتظار؛ نبود ⇒ `manifest` ِ meta.json
 * @param {boolean} [o.migrate]   مهاجرت‌های این نسخه روی کپی، و تطبیقِ دوباره
 * @param {string} [o.targetUrl]  دیتابیسِ خالیِ PostgreSQL برای بازگرداندن (بی آن: می‌سازد و می‌اندازد)
 * @param {boolean} [o.keep]      دیتابیسِ ساخته‌شده نگه داشته شود
 * @param {Function} [o.now]
 */
async function drill({ file, expected = null, migrate = false, targetUrl = '', keep = false, now = Date.now } = {}) {
  if (!file) throw new Error('فایلِ پشتیبان لازم است');
  const meta = await readMeta(file);
  const want = expected || meta?.manifest || null;
  const t0 = now();
  const iso = await restoreIsolated(file, { targetUrl, keep });
  try {
    const restoredMs = now() - t0;
    const got = await fingerprint(iso.q);
    const verifiedMs = now() - t0;
    const restore = want ? compare(want, got) : null;
    let migration = null;
    if (migrate) {
      const applied = await require('../migrate').run({ pool: iso.pool });
      const after = await fingerprint(iso.q);
      migration = { applied, ...compare(got, after, { allowAdded: true }), digest: after.digest };
    }
    //  پشتیبانی که هیچ جدولی برنمی‌گرداند هرگز «سالم» نیست، حتی اگر با manifest ِ خالی‌اش برابر باشد
    const empty = got.summary.tables === 0;
    const ok = !empty && (restore ? restore.equal : false) && (!migration || migration.equal);
    return {
      ok,
      empty,
      file: path.basename(String(file)),
      where: iso.where,
      //  RTO ِ اندازه‌گرفته: از آغازِ بازگرداندن تا دیتابیسِ قابلِ پرس‌وجو، و تا پایانِ تطبیق
      restoredMs,
      verifiedMs,
      //  RPO ِ این پشتیبان: چقدر داده (به زمان) اگر همین الان لازم می‌شد از دست می‌رفت
      backupAgeMs: meta?.createdAt ? Math.max(0, now() - Number(meta.createdAt)) : null,
      manifest: want ? (meta?.manifest && !expected ? 'meta' : 'given') : 'none',
      consistent: meta?.manifestConsistent ?? null,
      summary: got.summary,
      digest: got.digest,
      restore,
      migration,
    };
  } finally {
    await iso.close();
  }
}

module.exports = { fingerprint, compare, drill, restoreIsolated, normNum, MONEY_COLUMN };
