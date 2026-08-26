'use strict';
/**
 * پشتیبان‌گیری از دیتابیس.
 *
 * خروجی، فایل SQL فشرده (و در صورت تنظیم، رمزشده) است — نه یک فرمت
 * وابسته به این سرور. به همین دلیل پشتیبانِ کامپیوتر خانگی را می‌شود
 * روی VPS برگرداند و برعکس.
 *
 * مسیر ذخیره از BACKUP_PATH خوانده می‌شود؛ می‌تواند هارد داخلی، هارد
 * بیرونی یا هر مسیری باشد که سیستم‌عامل mount کرده است.
 */
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const zlib = require('zlib');
const { spawn } = require('child_process');
const { pipeline } = require('stream/promises');
const { createCipheriv, createDecipheriv, randomBytes, scryptSync, createHash } = require('crypto');
const config = require('../config');

const KINDS = ['daily', 'weekly', 'monthly', 'manual'];
const MAGIC = 'SHOPBK1';

function stamp(d = new Date()) {
  const p = (n, w = 2) => String(n).padStart(w, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}-${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}`;
}

function dirFor(kind) { return path.join(config.backup.dir, kind); }

async function ensureDirs() {
  for (const k of KINDS) await fsp.mkdir(dirFor(k), { recursive: true });
}

/** کلید رمزنگاری از عبارت عبور — با salt مخصوص همان فایل. */
function keyFrom(passphrase, salt) {
  return scryptSync(passphrase, salt, 32, { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
}

/**
 * گرفتن یک پشتیبان.
 * @returns {{file:string, bytes:number, kind:string, createdAt:number, encrypted:boolean}}
 */
async function run({ kind = 'manual', dir = null } = {}) {
  if (!KINDS.includes(kind)) throw new Error('نوع پشتیبان معتبر نیست');
  await ensureDirs();

  const encrypted = !!config.backup.passphrase;
  const base = `shop-${stamp()}.sql.gz${encrypted ? '.enc' : ''}`;
  const target = path.join(dir || dirFor(kind), base);
  const tmp = `${target}.part`;

  const dump = spawn(config.backup.pgDump, [
    config.db.url,
    '--no-owner', '--no-privileges', '--format=plain', '--encoding=UTF8',
  ], { stdio: ['ignore', 'pipe', 'pipe'] });

  let stderr = '';
  dump.stderr.on('data', d => { stderr += d.toString(); });
  // این را پیش از خواندن خروجی می‌سازیم؛ وگرنه ممکن است رویداد close
  // زودتر از شنونده برسد و منتظر چیزی بمانیم که قبلاً اتفاق افتاده.
  const closed = new Promise((resolve, reject) => {
    dump.on('close', resolve);
    dump.on('error', reject);
  });

  const gzip = zlib.createGzip({ level: 6 });

  if (encrypted) {
    const salt = randomBytes(16);
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', keyFrom(config.backup.passphrase, salt), iv);
    // سرآیند: نشانه + salt + iv؛ با همین، فایل روی هر سرور دیگری هم باز می‌شود
    await fsp.writeFile(tmp, Buffer.concat([Buffer.from(MAGIC), salt, iv]));
    await pipeline(dump.stdout, gzip, cipher, fs.createWriteStream(tmp, { flags: 'a' }));
    // برچسب احراز اصالت در انتها — دست‌کاری فایل لو می‌رود
    await fsp.appendFile(tmp, cipher.getAuthTag());
  } else {
    await pipeline(dump.stdout, gzip, fs.createWriteStream(tmp));
  }

  const code = await closed;
  if (code !== 0) {
    await fsp.rm(tmp, { force: true });
    throw new Error(`pg_dump شکست خورد: ${stderr.trim().slice(0, 300)}`);
  }

  await fsp.rename(tmp, target);
  const stat = await fsp.stat(target);
  const sha = createHash('sha256').update(await fsp.readFile(target)).digest('hex');
  await fsp.writeFile(`${target}.meta.json`, JSON.stringify({
    file: base, kind, createdAt: Date.now(), bytes: stat.size,
    encrypted, sha256: sha, format: 'pg_dump-plain-gzip', app: 'shop-server', version: 2,
  }, null, 2));

  await prune(kind);
  return { file: target, bytes: stat.size, kind, createdAt: Date.now(), encrypted, sha256: sha };
}

/** نگه داشتن تعداد مشخصی از هر نوع و پاک کردن قدیمی‌ترها. */
async function prune(kind) {
  const keep = {
    daily: config.backup.keepDaily,
    weekly: config.backup.keepWeekly,
    monthly: config.backup.keepMonthly,
    manual: 50,
  }[kind] || 10;

  const dir = dirFor(kind);
  const files = (await fsp.readdir(dir).catch(() => []))
    .filter(f => f.startsWith('shop-') && !f.endsWith('.meta.json') && !f.endsWith('.part'))
    .sort();
  const extra = files.slice(0, Math.max(0, files.length - keep));
  for (const f of extra) {
    await fsp.rm(path.join(dir, f), { force: true });
    await fsp.rm(path.join(dir, `${f}.meta.json`), { force: true });
  }
  return extra.length;
}

async function list() {
  await ensureDirs();
  const out = [];
  for (const kind of KINDS) {
    const dir = dirFor(kind);
    for (const f of (await fsp.readdir(dir).catch(() => []))) {
      if (f.endsWith('.meta.json') || f.endsWith('.part')) continue;
      const full = path.join(dir, f);
      const stat = await fsp.stat(full);
      let meta = null;
      try { meta = JSON.parse(await fsp.readFile(`${full}.meta.json`, 'utf8')); } catch { /* بدون meta */ }
      out.push({
        kind, file: f, path: full, bytes: stat.size,
        createdAt: meta?.createdAt || stat.mtimeMs, encrypted: meta?.encrypted ?? f.endsWith('.enc'),
        sha256: meta?.sha256 || '',
      });
    }
  }
  return out.sort((a, b) => b.createdAt - a.createdAt);
}

/** باز کردن یک فایل پشتیبان به SQL خام (رمزگشایی + از حالت فشرده). */
async function decode(file) {
  const buf = await fsp.readFile(file);
  let gz = buf;
  if (buf.subarray(0, MAGIC.length).toString() === MAGIC) {
    if (!config.backup.passphrase) throw new Error('این پشتیبان رمز دارد؛ BACKUP_PASSPHRASE لازم است');
    const salt = buf.subarray(7, 23);
    const iv = buf.subarray(23, 35);
    const tag = buf.subarray(buf.length - 16);
    const body = buf.subarray(35, buf.length - 16);
    const decipher = createDecipheriv('aes-256-gcm', keyFrom(config.backup.passphrase, salt), iv);
    decipher.setAuthTag(tag);
    gz = Buffer.concat([decipher.update(body), decipher.final()]);
  }
  return zlib.gunzipSync(gz);
}

/**
 * بازگرداندن پشتیبان روی همین دیتابیس یا هر دیتابیس دیگر.
 * (روی دیتابیسِ پر، اول باید خالی شود — این کار عمداً دستی است.)
 */
async function restore(file, { databaseUrl = config.db.url } = {}) {
  const sql = await decode(file);
  return new Promise((resolve, reject) => {
    const psql = spawn(config.backup.pgRestore, [databaseUrl, '-v', 'ON_ERROR_STOP=1', '-q', '-f', '-'],
      { stdio: ['pipe', 'pipe', 'pipe'] });
    let err = '';
    psql.stderr.on('data', d => { err += d.toString(); });
    psql.on('close', code => code === 0
      ? resolve({ ok: true, bytes: sql.length })
      : reject(new Error(`بازیابی شکست خورد: ${err.trim().slice(0, 500)}`)));
    psql.stdin.end(sql);
  });
}

/** زمان‌بندی خودکار: روزانه، هفتگی (یکشنبه) و ماهانه (اول ماه). */
function schedule() {
  if (!config.backup.enabled) return null;
  const tick = async () => {
    try {
      const d = new Date();
      const kind = d.getUTCDate() === 1 ? 'monthly' : (d.getUTCDay() === 0 ? 'weekly' : 'daily');
      const done = await run({ kind });
      console.log(`[backup] ${kind}: ${path.basename(done.file)} (${Math.round(done.bytes / 1024)}KB)`);
    } catch (err) {
      console.error('[backup] شکست خورد:', err.message);
    }
  };
  const timer = setInterval(tick, config.backup.intervalMs);
  if (timer.unref) timer.unref();
  return timer;
}

module.exports = { run, list, prune, restore, decode, schedule, KINDS, dirFor };
