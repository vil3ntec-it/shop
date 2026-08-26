'use strict';
/**
 * اجرای Migrationهای شماره‌دار.
 *
 * هر فایل در پوشه‌ی migrations یک بار و فقط یک بار اجرا می‌شود و نامش در
 * جدول schema_migrations می‌ماند. به همین دلیل انتقال دیتابیس از یک سرور
 * به سرور دیگر بدون از دست رفتن اطلاعات ممکن است: روی سرور تازه همان
 * Migrationها به همان ترتیب اجرا می‌شوند.
 */
const fs = require('fs');
const path = require('path');
const { createHash } = require('crypto');
const { getPool } = require('./db');

const DIR = path.join(__dirname, '..', 'migrations');

function files() {
  if (!fs.existsSync(DIR)) return [];
  return fs.readdirSync(DIR).filter(f => f.endsWith('.sql')).sort();
}

async function run({ log = () => {} } = {}) {
  const client = await getPool().connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        version     text PRIMARY KEY,
        checksum    text NOT NULL,
        applied_at  bigint NOT NULL
      )
    `);
    // قفل مشورتی: اگر دو نمونه‌ی سرور همزمان بالا بیایند، فقط یکی Migration می‌زند
    await client.query('SELECT pg_advisory_lock($1)', [727311]);

    const applied = new Map(
      (await client.query('SELECT version, checksum FROM schema_migrations')).rows
        .map(r => [r.version, r.checksum])
    );

    const done = [];
    for (const f of files()) {
      const sql = fs.readFileSync(path.join(DIR, f), 'utf8');
      const sum = createHash('sha256').update(sql).digest('hex').slice(0, 32);
      if (applied.has(f)) {
        if (applied.get(f) !== sum) {
          throw new Error(`فایل Migration «${f}» بعد از اجرا تغییر کرده است. فایل تازه بسازید، این را دست نزنید.`);
        }
        continue;
      }
      log(`↑ ${f}`);
      try {
        await client.query('BEGIN');
        await client.query(sql);
        await client.query(
          'INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)',
          [f, sum, Date.now()]
        );
        await client.query('COMMIT');
      } catch (err) {
        await client.query('ROLLBACK');
        throw new Error(`Migration «${f}» شکست خورد: ${err.message}`);
      }
      done.push(f);
    }
    return done;
  } finally {
    try { await client.query('SELECT pg_advisory_unlock($1)', [727311]); } catch { /* بی‌اهمیت */ }
    client.release();
  }
}

module.exports = { run, files };
