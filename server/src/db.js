'use strict';
/**
 * اتصال به دیتابیس — دو راه‌انداز، یک قرارداد.
 *
 *   PostgreSQL (پیش‌فرض)   DATABASE_URL=postgres://…
 *   PGlite (درون‌فرآیندی)  DATABASE_URL=pglite:<پوشه>   یا  pglite:memory
 *
 * چرا PGlite: سرورِ حساب روی کامپیوترِ خانگیِ صاحبِ سامانه می‌نشیند و آن
 * کامپیوتر ویندوز است — نه داکر دارد نه PostgreSQL. تا پیش از این «سرورِ
 * حساب روی سرورِ خانگی روشن نیست» تنها چیزی بود که برنامه‌ها می‌دیدند،
 * چون هیچ راهی بی داکر نبود. PGlite همان PostgreSQL است (WASM) داخلِ همین
 * فرآیندِ Node، با پوشه‌ای روی دیسک؛ پنلِ خانگی می‌تواند این سرور را مثلِ
 * هر پروسهٔ دیگری بالا بیاورد.
 *
 * ⚠️ همان SQL، همان $1 و $2، همان شکلِ نتیجه (`rows` و `rowCount`) — کدِ
 * بالاتر از این فایل نمی‌داند کدام راه‌انداز زیرش است. سه فرقِ PGlite
 * همین‌جا پوشانده می‌شود:
 *   • bigint را BigInt می‌دهد؛ pg رشته می‌دهد ⇒ رشته (مثلِ pg).
 *   • bytea را Uint8Array می‌دهد؛ pg Buffer ⇒ Buffer.
 *   • یک اتصال بیشتر ندارد ⇒ تراکنش قفلِ خودش را می‌گیرد و هر پرس‌وجویی
 *     که داخلِ همان تراکنش (حتی از راهِ `query`ِ سراسری) زده شود، به همان
 *     تراکنش می‌رود (AsyncLocalStorage) — وگرنه پشتِ قفلِ خودش می‌ماند.
 *
 * هیچ کوئری‌ای با چسباندن رشته ساخته نمی‌شود؛ همه‌ی مقادیر پارامتری هستند
 * ($1, $2, …) و همین جلوی SQL Injection را می‌گیرد.
 */
const { randomBytes } = require('crypto');
const { AsyncLocalStorage } = require('async_hooks');
const fs = require('fs');
const config = require('./config');

/** آیا DATABASE_URL راه‌اندازِ درون‌فرآیندی را می‌خواهد. */
const PGLITE_RE = /^pglite:(\/\/)?/i;
function isPglite(url = config.db.url) { return PGLITE_RE.test(String(url || '')); }

/** پوشهٔ دادهٔ PGlite از روی DATABASE_URL؛ خالی یا «memory» یعنی فقط در حافظه. */
function pgliteDir(url = config.db.url) {
  const target = String(url || '').replace(PGLITE_RE, '').trim();
  return (!target || target === 'memory' || target === 'memory://') ? '' : target;
}

let pool = null;
let shuttingDown = false;

// ── PostgreSQL ──────────────────────────────────────────────────────────────

function getPool() {
  if (isPglite()) return litePool();
  if (pool) return pool;
  const { Pool } = require('pg');
  pool = new Pool({
    connectionString: config.db.url,
    max: config.db.poolMax,
    idleTimeoutMillis: config.db.idleTimeoutMs,
    connectionTimeoutMillis: config.db.connectTimeoutMs,
    ssl: config.db.ssl ? { rejectUnauthorized: false } : undefined,
  });
  // یک اتصال خراب نباید کل سرور را بخواباند
  pool.on('error', (err) => console.error('[db] اتصال بیکار خطا داد:', err.message));
  return pool;
}

// ── PGlite ──────────────────────────────────────────────────────────────────

let lite = null;
let litePromise = null;
const txStore = new AsyncLocalStorage();

async function getLite() {
  if (lite) return lite;
  if (!litePromise) {
    litePromise = (async () => {
      const { PGlite, types } = require('@electric-sql/pglite');
      const dir = pgliteDir();
      if (dir) fs.mkdirSync(dir, { recursive: true });
      const db = await PGlite.create({
        dataDir: dir || 'memory://',
        //  مثلِ pg: عددِ بزرگ و اعشاری به شکلِ رشته می‌آیند، نه BigInt
        parsers: { [types.INT8]: (v) => v, [types.NUMERIC]: (v) => v },
      });
      return db;
    })();
  }
  lite = await litePromise;
  return lite;
}

/** نتیجهٔ PGlite به شکلِ نتیجهٔ pg. */
function liteResult(r) {
  const rows = (r?.rows || []).map((row) => {
    for (const k of Object.keys(row)) {
      const v = row[k];
      if (v instanceof Uint8Array && !Buffer.isBuffer(v)) row[k] = Buffer.from(v);
    }
    return row;
  });
  const rowCount = typeof r?.affectedRows === 'number' && r.affectedRows > 0
    ? r.affectedRows
    : (typeof r?.rowCount === 'number' ? r.rowCount : rows.length);
  return { rows, rowCount, command: r?.command || '' };
}

/**
 * یک پرس‌وجو روی یک دستهٔ PGlite (خودِ دیتابیس یا یک تراکنش).
 * بی‌پارامتر ⇒ `exec` (چند دستور در یک متن مجاز است — فایل‌های migration)؛
 * با پارامتر ⇒ `query` (یک دستورِ آماده).
 */
async function liteQuery(handle, text, params) {
  if (!params || !params.length) {
    const results = await handle.exec(text);
    return liteResult(results[results.length - 1]);
  }
  return liteResult(await handle.query(text, params));
}

/** «استخر»ِ ساختگی برای کدی که client می‌خواهد (migrate.js). */
function litePool() {
  return {
    async connect() {
      const db = await getLite();
      return { query: (text, params) => liteQuery(db, text, params), release() {} };
    },
    async end() { await closeDb(); },
  };
}

// ── قراردادِ مشترک ──────────────────────────────────────────────────────────

/** اجرای یک کوئری پارامتری. */
async function query(text, params = []) {
  if (isPglite()) {
    const inTx = txStore.getStore();
    return liteQuery(inTx || await getLite(), text, params);
  }
  return getPool().query(text, params);
}

/** اولین ردیف یا null. */
async function one(text, params = []) {
  const r = await query(text, params);
  return r.rows[0] || null;
}

/** همه‌ی ردیف‌ها. */
async function many(text, params = []) {
  const r = await query(text, params);
  return r.rows;
}

/**
 * اجرای چند دستور در یک تراکنش.
 * اگر هر بخشی شکست بخورد، همه‌چیز برمی‌گردد و دیتابیس نیمه‌کاره نمی‌ماند.
 */
async function tx(fn) {
  if (isPglite()) {
    const db = await getLite();
    return db.transaction((t) => {
      const client = { query: (text, params) => liteQuery(t, text, params), release() {} };
      return txStore.run(t, () => fn(client));
    });
  }
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    const out = await fn(client);
    await client.query('COMMIT');
    return out;
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch { /* اتصال از دست رفته */ }
    throw err;
  } finally {
    client.release();
  }
}

async function healthy() {
  try {
    const r = await query('SELECT 1 AS ok');
    return r.rows[0].ok === 1;
  } catch {
    return false;
  }
}

async function closeDb() {
  if (isPglite()) {
    if (!lite) return;
    const db = lite;
    lite = null;
    litePromise = null;
    await db.close();
    return;
  }
  if (!pool || shuttingDown) return;
  shuttingDown = true;
  const p = pool;
  pool = null;
  shuttingDown = false;
  await p.end();
}

/** نامِ راه‌انداز — برای /api/health و پیام‌های راه‌اندازی. */
function driverName() { return isPglite() ? 'pglite' : 'postgres'; }

/** پشتیبانِ کاملِ پوشهٔ PGlite (tar.gz) — همتای pg_dump برای این راه‌انداز. */
async function dumpPglite() {
  if (!isPglite()) throw new Error('فقط برای راه‌اندازِ PGlite');
  const db = await getLite();
  const blob = await db.dumpDataDir('gzip');
  return Buffer.from(await blob.arrayBuffer());
}

/** شناسه‌ی یکتا با پیشوند خوانا — مستقل از دیتابیس و قابل ساخت در هر جا. */
function newId(prefix) {
  return `${prefix}_${randomBytes(12).toString('hex')}`;
}

function now() { return Date.now(); }

/** پاک‌سازی داده‌های موقت — هنگام راه‌اندازی و هر چند ساعت یک بار. */
async function pruneExpired() {
  const cutoff = now();
  const t = await query('DELETE FROM tokens WHERE expires_at < $1', [cutoff]);
  const o = await query('DELETE FROM otp_codes WHERE expires_at < $1', [cutoff - 24 * 3600 * 1000]);
  const a = await query('DELETE FROM login_attempts WHERE created_at < $1', [cutoff - 7 * 24 * 3600 * 1000]);
  const s = await query('DELETE FROM sync_operations WHERE created_at < $1', [cutoff - 30 * 24 * 3600 * 1000]);
  return { tokens: t.rowCount, otp: o.rowCount, attempts: a.rowCount, operations: s.rowCount };
}

module.exports = {
  getPool, query, one, many, tx, healthy, closeDb, newId, now, pruneExpired,
  isPglite, pgliteDir, driverName, dumpPglite,
};
