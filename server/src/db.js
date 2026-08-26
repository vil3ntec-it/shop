'use strict';
/**
 * اتصال به PostgreSQL.
 *
 * یک Pool مشترک برای همه‌ی درخواست‌ها. هیچ کوئری‌ای با چسباندن رشته
 * ساخته نمی‌شود؛ همه‌ی مقادیر پارامتری هستند ($1, $2, …) و همین جلوی
 * SQL Injection را می‌گیرد.
 */
const { Pool } = require('pg');
const { randomBytes } = require('crypto');
const config = require('./config');

let pool = null;
let shuttingDown = false;

function getPool() {
  if (pool) return pool;
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

/** اجرای یک کوئری پارامتری. */
async function query(text, params = []) {
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
  if (!pool || shuttingDown) return;
  shuttingDown = true;
  const p = pool;
  pool = null;
  shuttingDown = false;
  await p.end();
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

module.exports = { getPool, query, one, many, tx, healthy, closeDb, newId, now, pruneExpired };
