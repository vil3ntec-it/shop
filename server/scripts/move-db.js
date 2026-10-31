#!/usr/bin/env node
'use strict';
/**
 * جابه‌جایی و تطبیقِ دیتابیسِ سرورِ حساب — بینِ PGlite و PostgreSQL، در هر دو جهت.
 *
 *   node scripts/move-db.js inspect --db <نشانی> [--json]
 *   node scripts/move-db.js copy    --from <نشانی> --to <نشانی> [--json]
 *   node scripts/move-db.js compare --a <نشانی> --b <نشانی> [--json]
 *
 *   نشانی:  pglite:<پوشه>                       (مثلِ DATABASE_URL)
 *           pglite:<فایلِ ‎.pglite.tar.gz‎>      (پشتیبانِ خودِ سرور — فقط مبدأ)
 *           postgres://کاربر:رمز@میزبان:پورت/دیتابیس
 *
 * چرا: صاحب سامانه سرور را از کامپیوترِ خانه (PGlite) به VPS/هاست می‌برد —
 * گاهی همان PGlite (پوشه کپی می‌شود و این ابزار فقط «همان است؟» را می‌گوید)،
 * گاهی PostgreSQL (این ابزار جدول‌به‌جدول می‌برد). سندِ کامل در ریپوی server:
 * docs/DEPLOY-ANYWHERE-fa.md، بخشِ «جابه‌جاییِ داده».
 *
 * قاعده‌ها (همه را test/move-db.test.js می‌سنجد):
 *  ⛔ **مبدأ هرگز نوشته نمی‌شود**: PGlite از رونوشتی در پوشهٔ موقت باز می‌شود
 *     (باز کردنِ خودِ پوشه حتی برای خواندن رویش می‌نویسد)، PostgreSQL در تراکنشِ
 *     READ ONLY با عکسِ یکسان (REPEATABLE READ).
 *  ⛔ مقصد باید **تازه** باشد (PGlite: پوشهٔ خالی؛ PostgreSQL: هیچ جدولی) — روی دادهٔ
 *     کسی چیزی ریخته نمی‌شود. ساختارِ مقصد با همان Migrationهای همین کد ساخته می‌شود
 *     و ردیف‌هایی که Migrationها کاشته‌اند جای خود را به ردیف‌های مبدأ می‌دهند؛ مجموعهٔ Migrationهای
 *     دو طرف باید یکی باشد (نسخهٔ یکسانِ سرور در دو طرف) وگرنه کاری نمی‌کند.
 *  ⛔ همه در **یک تراکنش**: یا همه آمد یا هیچ. ماشه‌ها (زنجیرهٔ هش، دفترِ فقط‌افزودنی)
 *     و کلیدهای خارجی هنگامِ کپی با ‎session_replication_role = replica‎ خاموش‌اند —
 *     داده همان است که در مبدأ پذیرفته شده بود، دوباره داوری نمی‌شود.
 *  ⛔ پس از کپی، تطبیق: برای هر جدول شمارِ ردیف، هشِ همهٔ ردیف‌ها (بی‌ترتیب) و
 *     جمعِ هر ستونِ پولی؛ و مقدارِ هر sequence. یک اختلاف ⇒ کدِ خروجِ ۱.
 *  ⛔ هیچ رازی چاپ نمی‌شود: نشانیِ PostgreSQL در خروجی بی رمز است.
 */
const fs = require('fs');
const fsp = require('fs/promises');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const MIGRATIONS_TABLE = 'schema_migrations';
/** ستون‌های پولی/مقداری — جمعشان در تطبیق می‌آید (فقط نوعِ عددی) */
const MONEY_RE = /(amount|total|price|balance|paid|cost|fee|debt|credit|debit|cash|change|discount|tax|refund|_minor|_cents|money|sum|qty|quantity)/i;
const NUMERIC_TYPES = new Set(['smallint', 'integer', 'bigint', 'numeric', 'real', 'double precision']);

/** نشانی بی رمز — برای چاپ */
function safeUrl(url) {
  return String(url).replace(/(postgres(?:ql)?:\/\/[^:/@]+):[^@]*@/i, '$1:***@');
}

const PGLITE_RE = /^pglite:(\/\/)?/i;

/* ──────────────────────────── باز کردنِ دیتابیس ──────────────────────────── */

/**
 * @returns {Promise<{ kind: 'pglite'|'postgres', label: string,
 *   q: (sql: string, params?: any[]) => Promise<any[]>, close: () => Promise<void> }>}
 */
async function open(url, { readOnly = false, create = false, migrate = false } = {}) {
  url = String(url || '').trim();
  if (!url) throw new Error('نشانیِ دیتابیس داده نشده');
  const label = safeUrl(url);
  if (PGLITE_RE.test(url)) {
    const { PGlite } = await import('@electric-sql/pglite');
    const target = url.replace(PGLITE_RE, '');
    let tmp = null;
    let db;
    if (/\.tar\.gz$/i.test(target)) {
      if (!readOnly) throw new Error('پشتیبانِ ‎.pglite.tar.gz‎ فقط مبدأ است، نه مقصد');
      const buf = await fsp.readFile(target);
      db = await PGlite.create({ loadDataDir: new Blob([buf]) });
    } else if (readOnly) {
      if (!fs.existsSync(path.join(target, 'PG_VERSION'))) throw new Error(`این پوشه دیتابیسِ PGlite نیست: ${target}`);
      //  ⛔ رونوشت، نه خودِ پوشه — باز کردنِ PGlite رویش می‌نویسد
      tmp = await fsp.mkdtemp(path.join(os.tmpdir(), 'move-db-src-'));
      await fsp.cp(target, path.join(tmp, 'pg'), { recursive: true });
      //  مبدأِ قدیمی‌تر از این کد ⇒ همان Migrationهایی که سرورِ تازه هنگامِ بالا آمدن
      //  می‌زد، روی **رونوشت** — خودِ مبدأ همچنان دست‌نخورده
      if (migrate) migrateTarget(`pglite:${path.join(tmp, 'pg')}`);
      db = await PGlite.create({ dataDir: path.join(tmp, 'pg') });
    } else {
      if (!create && !fs.existsSync(target)) throw new Error(`پوشهٔ مقصد نیست: ${target}`);
      await fsp.mkdir(target, { recursive: true });
      db = await PGlite.create({ dataDir: target });
    }
    const q = async (sql, params = []) => (await db.query(sql, params)).rows;
    await prepareSession(q);
    return {
      kind: 'pglite', label, q,
      exec: (sql) => db.exec(sql),
      close: async () => { await db.close(); if (tmp) await fsp.rm(tmp, { recursive: true, force: true }); },
    };
  }
  if (!/^postgres(ql)?:\/\//i.test(url)) throw new Error(`نشانیِ ناشناخته: ${label} — pglite:<پوشه> یا postgres://…`);
  const { Client } = require('pg');
  const client = new Client({ connectionString: url, ssl: /sslmode=require/i.test(url) ? { rejectUnauthorized: false } : undefined });
  //  همه متن — همان چیزی که PGlite هم با ‎::text‎ می‌دهد
  await client.connect();
  const q = async (sql, params = []) => (await client.query(sql, params)).rows;
  await prepareSession(q);
  if (readOnly) await q('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY');
  return {
    kind: 'postgres', label, q,
    exec: (sql) => client.query(sql),
    close: async () => { try { if (readOnly) await q('COMMIT'); } catch { /* بسته */ } await client.end(); },
  };
}

/** نمایشِ متنیِ یکسان در دو طرف — وگرنه هشِ ردیف‌ها از فرقِ قالب، نه داده، فرق می‌کرد */
async function prepareSession(q) {
  for (const s of [
    "SET TimeZone = 'UTC'", "SET DateStyle = 'ISO, YMD'", "SET IntervalStyle = 'postgres'",
    'SET extra_float_digits = 1', "SET bytea_output = 'hex'", "SET client_encoding = 'UTF8'",
  ]) await q(s);
}

const ident = (name) => `"${String(name).replace(/"/g, '""')}"`;

async function listTables(db) {
  const rows = await db.q(`SELECT c.relname AS name FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') ORDER BY c.relname`);
  return rows.map((r) => r.name);
}

async function listColumns(db, table) {
  return db.q(`SELECT column_name AS name, data_type AS type, is_generated AS generated, is_identity AS identity
    FROM information_schema.columns WHERE table_schema = 'public' AND table_name = $1 ORDER BY ordinal_position`, [table]);
}

async function listSequences(db) {
  const rows = await db.q(`SELECT c.relname AS name FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind = 'S' ORDER BY c.relname`);
  return rows.map((r) => r.name);
}

async function migrationVersions(db) {
  const tables = await listTables(db);
  if (!tables.includes(MIGRATIONS_TABLE)) return [];
  return (await db.q(`SELECT version::text AS v FROM ${MIGRATIONS_TABLE} ORDER BY version`)).map((r) => r.v);
}

/* ───────────────────────────────── inspect ───────────────────────────────── */

/**
 * شمار، هش و جمعِ پولیِ هر جدول + مقدارِ sequenceها.
 * ⚠️ جدولِ ‎schema_migrations‎ فقط با فهرستِ نسخه‌ها می‌آید (زمانِ اجرا در دو طرف فرق دارد).
 */
async function inspect(db) {
  const tables = {};
  let rows = 0;
  for (const t of await listTables(db)) {
    if (t === MIGRATIONS_TABLE) continue;
    const n = Number((await db.q(`SELECT count(*)::text AS n FROM ${ident(t)}`))[0].n);
    const h = (await db.q(`SELECT coalesce(md5(string_agg(x.h, '' ORDER BY x.h)), '') AS h
      FROM (SELECT md5(r::text) AS h FROM ${ident(t)} r) x`))[0].h;
    const sums = {};
    for (const c of await listColumns(db, t)) {
      if (!NUMERIC_TYPES.has(c.type) || !MONEY_RE.test(c.name)) continue;
      sums[c.name] = (await db.q(`SELECT coalesce(sum(${ident(c.name)}), 0)::text AS s FROM ${ident(t)}`))[0].s;
    }
    tables[t] = { rows: n, hash: h, sums };
    rows += n;
  }
  const sequences = {};
  for (const s of await listSequences(db)) {
    const r = (await db.q(`SELECT last_value::text AS v, is_called AS c FROM ${ident(s)}`))[0];
    sequences[s] = `${r.v}${r.c ? '' : '*'}`;
  }
  return {
    tool: 'move-db', version: 1,
    driver: db.kind,
    migrations: await migrationVersions(db),
    tables, sequences,
    totals: { tables: Object.keys(tables).length, rows },
  };
}

/** دو گزارش یکی‌اند؟ فهرستِ اختلاف‌ها به زبانِ آدم */
function diffReports(a, b) {
  const diffs = [];
  if (JSON.stringify(a.migrations) !== JSON.stringify(b.migrations)) diffs.push('مجموعهٔ Migrationها فرق دارد (نسخهٔ سرور یکی نیست)');
  for (const t of new Set([...Object.keys(a.tables), ...Object.keys(b.tables)])) {
    const x = a.tables[t];
    const y = b.tables[t];
    if (!x || !y) { diffs.push(`${t}: فقط در یک طرف`); continue; }
    if (x.rows !== y.rows) diffs.push(`${t}: ${x.rows} ردیف ⇐ ${y.rows}`);
    else if (x.hash !== y.hash) diffs.push(`${t}: محتوای ردیف‌ها فرق دارد`);
    for (const c of new Set([...Object.keys(x.sums), ...Object.keys(y.sums)])) {
      if (x.sums[c] !== y.sums[c]) diffs.push(`${t}.${c}: جمع ${x.sums[c]} ⇐ ${y.sums[c]}`);
    }
  }
  for (const s of new Set([...Object.keys(a.sequences), ...Object.keys(b.sequences)])) {
    if (a.sequences[s] !== b.sequences[s]) diffs.push(`sequence ${s}: ${a.sequences[s]} ⇐ ${b.sequences[s]}`);
  }
  return diffs;
}

/* ────────────────────────────────── copy ────────────────────────────────── */

/** Migrationهای همین کد روی مقصد — همان راهی که خودِ سرور هنگامِ بالا آمدن می‌رود */
function migrateTarget(url) {
  const r = spawnSync(process.execPath, [path.join(__dirname, 'migrate.js')], {
    env: { ...process.env, DATABASE_URL: url, ENV_FILE: path.join(os.tmpdir(), 'move-db-no-env') },
    encoding: 'utf8', timeout: 10 * 60_000,
  });
  if (r.status !== 0) throw new Error(`ساختنِ ساختارِ مقصد شکست خورد: ${(r.stderr || r.stdout || '').trim().slice(0, 500)}`);
}

const BATCH = 500;

/** Migrationهای همین کد (نامِ فایل = version در schema_migrations) */
function codeVersions() {
  const dir = path.join(__dirname, '..', 'migrations');
  return fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => f.endsWith('.sql')).sort() : [];
}

/** مقصد تازه است؟ PGlite: پوشهٔ نبوده/خالی · PostgreSQL: هیچ جدولی در public */
async function assertFresh(url) {
  if (PGLITE_RE.test(url)) {
    const dir = url.replace(PGLITE_RE, '');
    if (fs.existsSync(dir) && fs.readdirSync(dir).length) {
      throw new Error(`پوشهٔ مقصد خالی نیست: ${dir} — روی دیتابیسِ موجود چیزی ریخته نمی‌شود (پوشهٔ تازه بدهید).`);
    }
    return;
  }
  const db = await open(url);
  try {
    const tables = await listTables(db);
    if (tables.length) throw new Error(`دیتابیسِ مقصد خالی نیست (${tables.length} جدول) — یک دیتابیسِ تازه بسازید؛ روی دادهٔ کسی چیزی ریخته نمی‌شود.`);
  } finally { await db.close(); }
}

async function copy({ from, to, log = () => {} }) {
  //  ۱) مبدأ (فقط‌خواندنی) و هم‌سنیِ Migrationها با همین کد — پیش از هر کاری روی مقصد
  const pgliteDirSource = PGLITE_RE.test(from) && !/\.tar\.gz$/i.test(from);
  let src = await open(from, { readOnly: true });
  let dst = null;
  try {
    const code = codeVersions();
    let vs = await migrationVersions(src);
    const unknown = vs.filter((v) => !code.includes(v));
    if (unknown.length) {
      throw new Error(`مبدأ Migrationهایی دارد که این کد ندارد (${unknown.slice(0, 5).join('، ')}) — همین ابزار را از نسخهٔ هم‌سن یا تازه‌ترِ سرور اجرا کنید.`);
    }
    if (vs.length !== code.length) {
      if (!pgliteDirSource) {
        throw new Error('مبدأ از این کد قدیمی‌تر است — اول سرورِ مبدأ را به همین نسخه به‌روز کنید (تا Migrationهایش اجرا شوند)، بعد کپی.');
      }
      log(`  مبدأ ${vs.length} Migration دارد و این کد ${code.length} — رونوشتِ موقتش به‌روز می‌شود (خودِ مبدأ دست نمی‌خورد)`);
      await src.close();
      src = await open(from, { readOnly: true, migrate: true });
      vs = await migrationVersions(src);
    }

    //  ۲) مقصد: تازه، و ساختار با Migrationهای همین کد
    await assertFresh(to);
    migrateTarget(to);
    dst = await open(to, { create: true });

    //  ۳) یک تراکنش؛ ماشه و کلیدِ خارجی خاموش (داده پیش‌تر در مبدأ داوری شده)
    await dst.q('BEGIN');
    try {
      try {
        await dst.q("SET LOCAL session_replication_role = 'replica'");
      } catch (e) {
        throw new Error(`کاربرِ مقصد اجازهٔ ‎session_replication_role‎ ندارد (superuser لازم است): ${e.message}`);
      }
      //  ردیف‌هایی که خودِ Migrationها کاشته‌اند (پلن‌های پیش‌فرض، …) جای خود را به
      //  نسخهٔ مبدأ می‌دهند — مبدأ همان‌ها را دارد، شاید با قیمتِ عوض‌شده.
      const dstTables = (await listTables(dst)).filter((t) => t !== MIGRATIONS_TABLE);
      if (dstTables.length) await dst.q(`TRUNCATE ${dstTables.map(ident).join(', ')}`);
      const srcTables = await listTables(src);
      const missing = srcTables.filter((t) => t !== MIGRATIONS_TABLE && !dstTables.includes(t));
      if (missing.length) throw new Error(`جدول‌هایی در مبدأ هست که Migrationها نمی‌سازند: ${missing.join('، ')}`);
      for (const t of srcTables) {
        if (t === MIGRATIONS_TABLE) continue;
        const cols = (await listColumns(dst, t)).filter((c) => c.generated !== 'ALWAYS');
        if (!cols.length) continue;
        const list = cols.map((c) => ident(c.name)).join(', ');
        const overriding = cols.some((c) => c.identity === 'YES') ? ' OVERRIDING SYSTEM VALUE' : '';
        let copied = 0;
        let last = null;
        for (;;) {
          //  کلیدِ ctid: صفحه‌به‌صفحه، بی OFFSETِ کند و بی وابستگی به کلیدِ اصلی
          const batch = await src.q(
            `SELECT ctid::text AS k, row_to_json(r)::text AS j FROM ${ident(t)} r
              ${last ? 'WHERE ctid > $1::tid' : ''} ORDER BY ctid LIMIT ${BATCH}`,
            last ? [last] : [],
          );
          if (!batch.length) break;
          const json = `[${batch.map((b) => b.j).join(',')}]`;
          await dst.q(`INSERT INTO ${ident(t)} (${list})${overriding}
            SELECT ${list} FROM json_populate_recordset(NULL::${ident(t)}, $1::json)`, [json]);
          copied += batch.length;
          last = batch[batch.length - 1].k;
          if (batch.length < BATCH) break;
        }
        if (copied) log(`  ${t}: ${copied}`);
      }
      //  ۴) sequenceها — شمارهٔ بعدیِ هر شناسه همان که در مبدأ بود
      const dstSeqs = new Set(await listSequences(dst));
      for (const s of await listSequences(src)) {
        if (!dstSeqs.has(s)) continue;
        const r = (await src.q(`SELECT last_value::text AS v, is_called AS c FROM ${ident(s)}`))[0];
        await dst.q('SELECT setval($1::regclass, $2::bigint, $3::boolean)', [ident(s), r.v, r.c]);
      }
      await dst.q('COMMIT');
    } catch (e) {
      await dst.q('ROLLBACK').catch(() => {});
      throw e;
    }

    //  ۵) تطبیق: هر جدول، هر مبلغ، هر sequence
    const before = await inspect(src);
    const after = await inspect(dst);
    return { from: src.label, to: dst.label, source: before, target: after, diffs: diffReports(before, after) };
  } finally {
    await src.close().catch(() => {});
    if (dst) await dst.close().catch(() => {});
  }
}

/* ──────────────────────────────── خطِ فرمان ──────────────────────────────── */

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const eq = a.indexOf('=');
      if (eq > 0) out[a.slice(2, eq)] = a.slice(eq + 1);
      else if (argv[i + 1] && !argv[i + 1].startsWith('--')) out[a.slice(2)] = argv[++i];
      else out[a.slice(2)] = true;
    } else out._.push(a);
  }
  return out;
}

function summary(rep) {
  const money = {};
  for (const t of Object.values(rep.tables)) {
    for (const [c, v] of Object.entries(t.sums)) money[c] = (money[c] || 0n) + BigInt(String(v).split('.')[0] || '0');
  }
  return `${rep.totals.tables} جدول · ${rep.totals.rows} ردیف · ${rep.migrations.length} Migration`
    + (Object.keys(money).length ? ` · جمعِ ستون‌های پولی: ${Object.entries(money).map(([k, v]) => `${k}=${v}`).join(' ')}` : '');
}

const HELP = `جابه‌جایی و تطبیقِ دیتابیسِ سرورِ حساب
  inspect --db <نشانی> [--json]
  copy    --from <نشانی> --to <نشانی> [--json]
  compare --a <نشانی> --b <نشانی> [--json]
نشانی: pglite:<پوشه> · pglite:<پشتیبان.pglite.tar.gz> · postgres://…`;

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const cmd = args._[0];
  const json = !!args.json;
  if (cmd === 'inspect' && args.db) {
    const db = await open(args.db, { readOnly: true });
    try {
      const rep = await inspect(db);
      console.log(json ? JSON.stringify(rep) : `✅ ${db.label}\n   ${summary(rep)}`);
    } finally { await db.close(); }
    return 0;
  }
  if (cmd === 'compare' && args.a && args.b) {
    const a = await open(args.a, { readOnly: true });
    const b = await open(args.b, { readOnly: true });
    try {
      const [ra, rb] = [await inspect(a), await inspect(b)];
      const diffs = diffReports(ra, rb);
      if (json) console.log(JSON.stringify({ same: !diffs.length, diffs, a: ra.totals, b: rb.totals }));
      else console.log(diffs.length ? `❌ ${diffs.length} اختلاف:\n   ${diffs.slice(0, 40).join('\n   ')}` : `✅ یکی‌اند — ${summary(ra)}`);
      return diffs.length ? 1 : 0;
    } finally { await a.close(); await b.close(); }
  }
  if (cmd === 'copy' && args.from && args.to) {
    const r = await copy({ from: args.from, to: args.to, log: json ? () => {} : (m) => console.log(m) });
    if (json) console.log(JSON.stringify({ same: !r.diffs.length, diffs: r.diffs, source: r.source.totals, target: r.target.totals }));
    else {
      console.log(r.diffs.length
        ? `❌ کپی شد ولی ${r.diffs.length} اختلاف:\n   ${r.diffs.slice(0, 40).join('\n   ')}`
        : `✅ ${r.from} ⇐ ${r.to}\n   ${summary(r.target)} — شمار، هشِ ردیف‌ها، جمعِ مبلغ‌ها و sequenceها همان مبدأ`);
    }
    return r.diffs.length ? 1 : 0;
  }
  console.error(HELP);
  return 2;
}

module.exports = { open, inspect, copy, diffReports, safeUrl, MONEY_RE };

if (require.main === module) {
  main().then((code) => process.exit(code)).catch((e) => { console.error(`✖ ${e.message}`); process.exit(1); });
}
