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
const { createPglite } = require('./lib/pglite-create');

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

/** گزینه‌های هر استخرِ PostgreSQL — استخرِ اصلی، استخرهای کوچکِ کمکی ([extraPool]) و استخرِ نسخهٔ خواندنی */
function poolOptions(max, url = config.db.url) {
  return {
    connectionString: url,
    max,
    idleTimeoutMillis: config.db.idleTimeoutMs,
    connectionTimeoutMillis: config.db.connectTimeoutMs,
    ssl: config.db.ssl ? { rejectUnauthorized: false } : undefined,
    //  اتصالِ بیکار فرآیند را زنده نگه نمی‌دارد. سنجشِ پس‌زمینهٔ `app.js` (هر ۵ ثانیه) اگر
    //  پس از `closeDb` برسد استخرِ تازه‌ای می‌سازد؛ بی این، اتصالِ آن هرگز ۳۰ ثانیه بیکار
    //  نمی‌ماند و فرآیند (پرونده‌ای از آزمون‌ها) تا ابد باز می‌ماند. سرور را شنوندهٔ HTTP زنده نگه می‌دارد.
    allowExitOnIdle: true,
    //  PERF-04: پرس‌وجوی کند یا تراکنشِ رهاشده اتصالِ استخر را تا ابد نگه نمی‌دارد (۰ ⇒ گزینه نیست، همان پیشین)
    ...(config.db.statementTimeoutMs > 0 ? { statement_timeout: config.db.statementTimeoutMs } : {}),
    ...(config.db.idleTxTimeoutMs > 0 ? { idle_in_transaction_session_timeout: config.db.idleTxTimeoutMs } : {}),
  };
}

/**
 * پس‌زدنِ زود (PERF-04): صفِ انتظارِ pg-pool در طول سقف ندارد (فقط هر انتظار تا
 * `PG_CONNECT_TIMEOUT_MS`). با `PG_POOL_MAX_WAITING` > ۰، پرس‌وجو یا تراکنشی که به صفی با همین
 * اندازه برسد بی‌درنگ ۵۰۳ ِ `db_busy` (با `Retry-After`) می‌گیرد، نه چند ثانیه انتظار و بعد همان.
 * فقط مسیرِ `query`/`one`/`many`/`tx`؛ `getPool()` ِ مستقیم (migration، پشتیبان) همان صفِ pg است.
 */
function admit(p) {
  const max = config.db.maxWaiting;
  if (max > 0 && p.waitingCount >= max) throw require('./middleware/errors').busy();
  return p;
}

function getPool() {
  if (isPglite()) return litePool();
  if (pool) return pool;
  const { Pool } = require('pg');
  pool = new Pool(poolOptions(config.db.poolMax));
  // یک اتصال خراب نباید کل سرور را بخواباند
  pool.on('error', (err) => console.error('[db] اتصال بیکار خطا داد:', err.message));
  return pool;
}

// ── PGlite ──────────────────────────────────────────────────────────────────

let lite = null;
let litePromise = null;
const txStore = new AsyncLocalStorage();
//  «درونِ تراکنشِ PostgreSQL» — فقط برای `inTransaction()` (خواندن از نسخهٔ خواندنی هرگز درونِ تراکنش نیست)
const pgTxStore = new AsyncLocalStorage();

/**
 * دامنهٔ «خواندنِ مجاز از نسخهٔ خواندنی» (`lib/read-replica.js`): مسیرِ GET ِ داوطلب آن را باز می‌کند و
 * هر نوشتن یا تراکنشی در همان درخواست `wrote` را روشن می‌کند ⇒ خواندن‌های بعدی همان درخواست از پایگاهِ اصلی.
 * بیرون از این دامنه (همهٔ کد جز همان چند مسیر) هیچ چیزی عوض نمی‌شود.
 */
const readScope = new AsyncLocalStorage();
//  فقط SELECT ِ ساده «خواندن» است؛ قفل، شمارنده، advisory، NOTIFY و set_config نوشتن به حساب می‌آیند
const READ_ONLY_RE = /^\s*select\b/i;
const NOT_PURE_READ_RE = /\bfor\s+(update|share|no\s+key\s+update|key\s+share)\b|\b(nextval|setval|pg_advisory\w*|pg_try_advisory\w*|pg_notify|set_config)\s*\(/i;
function noteStatement(text) {
  const s = readScope.getStore();
  if (!s || s.wrote) return;
  const t = String(text || '');
  if (!READ_ONLY_RE.test(t) || NOT_PURE_READ_RE.test(t)) s.wrote = true;
}

async function getLite() {
  if (lite) return lite;
  if (!litePromise) {
    litePromise = (async () => {
      const { types } = require('@electric-sql/pglite');
      const dir = pgliteDir();
      if (dir) fs.mkdirSync(dir, { recursive: true });
      //  ⛔ نه `PGlite.create` ِ خام: زیرِ Electron گاه برای همیشه می‌ایستد (`lib/pglite-create.js`)
      const db = await createPglite({
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
  noteStatement(text);
  if (isPglite()) {
    const inTx = txStore.getStore();
    return liteQuery(inTx || await getLite(), text, params);
  }
  return admit(getPool()).query(text, params);
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
  const scope = readScope.getStore();
  if (scope) scope.wrote = true;
  if (isPglite()) {
    const db = await getLite();
    return db.transaction((t) => {
      const client = { query: (text, params) => liteQuery(t, text, params), release() {} };
      return txStore.run(t, () => fn(client));
    });
  }
  const client = await admit(getPool()).connect();
  /*
   *  اتصالی که وسطِ تراکنش افتاد (سقفِ تراکنشِ بیکار `PG_IDLE_TX_TIMEOUT_MS`، راه‌اندازیِ دوبارهٔ پایگاه، شبکه) به استخر
   *  برنمی‌گردد: `release(err)` نابودش می‌کند. پیش‌تر سالم برمی‌گشت و پرس‌وجوی بعدیِ کسِ دیگر با همان خطا می‌افتاد؛ و
   *  رخدادِ `error` ِ اتصالِ بیرون‌کشیده شنونده‌ای نداشت ⇒ uncaughtException (`request-deadline.test.js`).
   */
  let broken = null;
  const onLost = (e) => { broken = broken || e; };
  client.on('error', onLost);
  try {
    await client.query('BEGIN');
    const out = await pgTxStore.run(true, () => fn(client));
    await client.query('COMMIT');
    return out;
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch (e) { broken = broken || e; }
    throw err;
  } finally {
    client.off('error', onLost);
    client.release(broken || undefined);
  }
}

/** آیا همین کد درونِ `tx()` اجرا می‌شود (هر دو راه‌انداز). */
function inTransaction() { return Boolean(txStore.getStore() || pgTxStore.getStore()); }

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

/**
 * استخرِ کوچکِ جدا (مثلاً برای قفلِ نشستی که تا پایانِ یک درخواست می‌ماند) تا اتصالِ استخرِ
 * اصلی گرفته نماند. فقط PostgreSQL؛ روی PGlite null. با `closeDb` بسته نمی‌شود — `allowExitOnIdle`.
 */
function extraPool(max, url = config.db.url) {
  if (isPglite()) return null;
  const { Pool } = require('pg');
  const p = new Pool(poolOptions(max, url));
  p.on('error', (err) => console.error('[db] اتصالِ بیکارِ استخرِ کمکی خطا داد:', err.message));
  return p;
}

module.exports = {
  getPool, extraPool, query, one, many, tx, healthy, closeDb, newId, now, pruneExpired,
  isPglite, pgliteDir, driverName, dumpPglite, inTransaction, readScope,
};
