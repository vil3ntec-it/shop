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
const { Transform } = require('stream');
const { createCipheriv, createDecipheriv, randomBytes, scryptSync, createHash } = require('crypto');
const config = require('../config');
const db = require('../db');

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
/** متنِ هشدار برای پنل و لاگ — یک جا، تا همه یک چیز بگویند (F0-08) */
const UNENCRYPTED_WARNING = 'BACKUP_PASSPHRASE تنظیم نشده است: پشتیبان‌های سرور رمز ندارند و هر کس فایل را داشته باشد دادهٔ همهٔ حساب‌ها را دارد. رمز را صاحبِ سامانه در محیطِ سرور بگذارد.';

/**
 * F0-08 — بی رمز، پشتیبان **هرگز** نمی‌ایستد؛ ولی هر اجرا یک خبرِ سامانه‌ای
 * (`backup.unencrypted` در «سابقه»ی پنل) و یک خطِ هشدار در لاگِ سرور
 * می‌گذارد — همان راهِ «هشدار به مدیر» در ‎lib/login-outbox.js‎.
 * ⛔ نه `await` لازم دارد نه شکستش چیزی را می‌خواباند.
 */
function warnUnencrypted(kind) {
  console.warn(`[backup] ⚠️ ${kind}: ${UNENCRYPTED_WARNING}`);
  return require('./audit').log({
    actorType: 'system', action: 'backup.unencrypted', detail: { kind },
  }).catch(() => {});
}

/**
 * اثرِ انگشتِ دیتابیس برای `meta.json` (`lib/restore-drill.js`: شمار، جمعِ پول و مقدار) در **همان**
 * snapshot ای که pg_dump می‌خواند (`pg_export_snapshot` ⇒ `pg_dump --snapshot`)؛ پس تمرینِ بازیابی
 * بعدها بی دیتابیسِ زنده می‌داند فایل باید دقیقاً چه چیزی را برگرداند.
 * ⛔ هیچ‌وقت پشتیبان را نمی‌خواباند: شکستِ این بخش ⇒ پشتیبانِ بی manifest، همان رفتارِ پیشین.
 */
async function openSnapshot() {
  let client = null;
  try {
    client = await db.getPool().connect();
    await client.query('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY');
    const id = (await client.query('SELECT pg_export_snapshot() AS id')).rows[0].id;
    const manifest = await require('./restore-drill').fingerprint((t, p) => client.query(t, p));
    const c = client;
    return {
      id, manifest,
      async close() {
        try { await c.query('COMMIT'); } catch { /* اتصال رفته */ }
        c.release();
      },
    };
  } catch (err) {
    console.warn(`[backup] manifest ساخته نشد (پشتیبان بی آن ادامه می‌دهد): ${err.message}`);
    if (client) { try { await client.query('ROLLBACK'); } catch { /* بی‌اهمیت */ } client.release(); }
    return null;
  }
}

/** همان، برای PGlite: پیش و پس از برداشتن؛ یکی نبود ⇒ «ناسازگار» علامت می‌خورد، نه پنهان */
async function pgliteManifest() {
  try { return await require('./restore-drill').fingerprint((t, p) => db.query(t, p)); } catch (err) {
    console.warn(`[backup] manifest ساخته نشد: ${err.message}`);
    return null;
  }
}

async function run({ kind = 'manual', dir = null } = {}) {
  if (!KINDS.includes(kind)) throw new Error('نوع پشتیبان معتبر نیست');
  await ensureDirs();

  const encrypted = !!config.backup.passphrase;
  const warned = encrypted ? null : warnUnencrypted(kind);
  //  PGlite: ردیفِ همین هشدار نباید میانِ دو اثرِ انگشتِ manifest بنشیند (شکستش هم چیزی را نمی‌خواباند)
  if (db.isPglite()) { await warned; return runPglite({ kind, dir, encrypted }); }
  const base = `shop-${stamp()}.sql.gz${encrypted ? '.enc' : ''}`;
  const target = path.join(dir || dirFor(kind), base);
  const tmp = `${target}.part`;

  //  ⚠️ پیش از spawn (قاعدهٔ «هیچ await میانِ spawn و وصل شدن به stdout» در `runPg`)
  const snap = await openSnapshot();
  try {
    return await runPg({ kind, encrypted, base, target, tmp, snap });
  } finally {
    if (snap) await snap.close();
  }
}

/** pg_dump ⇒ gzip ⇒ (رمز) ⇒ فایل؛ با `snap` همان snapshot ِ manifest را می‌خواند */
async function runPg({ kind, encrypted, base, target, tmp, snap }) {
  const dump = spawn(config.backup.pgDump, [
    config.db.url,
    '--no-owner', '--no-privileges', '--format=plain', '--encoding=UTF8',
    ...(snap ? [`--snapshot=${snap.id}`] : []),
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

  /*
   *  شمارنده‌ی بایت‌های خام.
   *
   *  اگر pg_dump چیزی ندهد ولی با کد ۰ تمام شود، فایل خالی می‌شود و
   *  همه‌چیز سالم به نظر می‌رسد. پشتیبانی که خالی است از نبودنِ پشتیبان
   *  بدتر است، چون روز خرابی تازه معلوم می‌شود.
   */
  let rawBytes = 0;
  const counter = new Transform({
    transform(chunk, _enc, cb) { rawBytes += chunk.length; cb(null, chunk); },
  });

  /*
   *  هیچ `await`ی بین spawn و وصل شدن به stdout نباید باشد.
   *
   *  اینجا یک اشکالِ واقعی بود: در مسیرِ رمزشده، اول سرآیند با
   *  `await fsp.writeFile` نوشته می‌شد و بعد pipeline به stdout وصل
   *  می‌شد. اگر pg_dump در همان فاصله تمام می‌شد — که روی دیتابیسِ کوچک
   *  یا سرورِ تازه راه‌افتاده پیش می‌آید — خروجی‌اش رفته بود و pipeline
   *  به جریانی وصل می‌شد که قبلاً بسته شده. نتیجه: فایلِ خالی، با کدِ
   *  خروجِ ۰ و بدون هیچ خطایی.
   *
   *  حالا جریانِ نوشتن همان لحظه ساخته می‌شود و سرآیند داخلِ خودش
   *  می‌نشیند، بی‌آنکه چیزی منتظر بماند.
   */
  const file = fs.createWriteStream(tmp);

  if (encrypted) {
    const salt = randomBytes(16);
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', keyFrom(config.backup.passphrase, salt), iv);
    // سرآیند: نشانه + salt + iv؛ با همین، فایل روی هر سرور دیگری هم باز می‌شود
    file.write(Buffer.concat([Buffer.from(MAGIC), salt, iv]));
    await pipeline(dump.stdout, counter, gzip, cipher, file);
    // برچسب احراز اصالت در انتها — دست‌کاری فایل لو می‌رود
    await fsp.appendFile(tmp, cipher.getAuthTag());
  } else {
    await pipeline(dump.stdout, counter, gzip, file);
  }

  const code = await closed;
  if (code !== 0) {
    await fsp.rm(tmp, { force: true });
    throw new Error(`pg_dump شکست خورد: ${stderr.trim().slice(0, 300)}`);
  }

  //  کدِ خروجِ ۰ کافی نیست؛ باید چیزی هم بیرون آمده باشد
  if (rawBytes < 100) {
    await fsp.rm(tmp, { force: true });
    throw new Error(`pg_dump چیزی نداد — پشتیبان ساخته نشد. ${stderr.trim().slice(0, 300)}`);
  }

  await fsp.rename(tmp, target);
  const stat = await fsp.stat(target);
  const sha = createHash('sha256').update(await fsp.readFile(target)).digest('hex');
  await fsp.writeFile(`${target}.meta.json`, JSON.stringify({
    file: base, kind, createdAt: Date.now(), bytes: stat.size,
    encrypted, sha256: sha, format: 'pg_dump-plain-gzip', app: 'shop-server', version: 2,
    //  فقط شمار و جمع — بی هیچ دادهٔ شخصی (lib/restore-drill.js)
    ...(snap ? { manifest: snap.manifest, manifestConsistent: true } : {}),
  }, null, 2));

  await prune(kind);
  noteSuccess(kind);
  return { file: target, bytes: stat.size, kind, createdAt: Date.now(), encrypted, sha256: sha, manifest: snap ? snap.manifest.digest : null };
}

/**
 * پشتیبان روی راه‌اندازِ PGlite — کلِ پوشهٔ دیتابیس، همان‌طور که خودِ PGlite
 * بیرون می‌دهد (tar.gz). pg_dump این‌جا نیست و لازم هم نیست: این فایل با
 * `PGlite.create({ loadDataDir })` عیناً همان دیتابیس می‌شود.
 *
 * ⚠️ همان رمزنگاری و همان meta.json، تا فهرست و دانلودِ پنل فرقی نبیند؛
 * فقط پسوند `.pglite.tar.gz` است، نه `.sql.gz`، چون SQLِ متنی نیست.
 */
async function runPglite({ kind, dir, encrypted }) {
  const base = `shop-${stamp()}.pglite.tar.gz${encrypted ? '.enc' : ''}`;
  const target = path.join(dir || dirFor(kind), base);
  const tmp = `${target}.part`;

  //  یک اتصال، بی snapshot: اثرِ انگشت پیش و پس از برداشتن؛ نوشتنی در میانه ⇒ یک بارِ دیگر، و باز
  //  ناسازگار ⇒ پشتیبان بی manifest (تمرین «بی manifest» می‌گوید، نه «برابر»)
  let before = await pgliteManifest();
  let raw = await db.dumpPglite();
  let after = before ? await pgliteManifest() : null;
  if (before && after && before.digest !== after.digest) {
    before = after;
    raw = await db.dumpPglite();
    after = await pgliteManifest();
  }
  if (raw.length < 100) throw new Error('PGlite چیزی نداد — پشتیبان ساخته نشد.');
  const consistent = !!(before && after && before.digest === after.digest);

  let out = raw;
  if (encrypted) {
    const salt = randomBytes(16);
    const iv = randomBytes(12);
    const cipher = createCipheriv('aes-256-gcm', keyFrom(config.backup.passphrase, salt), iv);
    out = Buffer.concat([Buffer.from(MAGIC), salt, iv, cipher.update(raw), cipher.final(), cipher.getAuthTag()]);
  }
  await fsp.writeFile(tmp, out);
  await fsp.rename(tmp, target);
  const stat = await fsp.stat(target);
  const sha = createHash('sha256').update(out).digest('hex');
  await fsp.writeFile(`${target}.meta.json`, JSON.stringify({
    file: base, kind, createdAt: Date.now(), bytes: stat.size,
    encrypted, sha256: sha, format: 'pglite-datadir-tar-gzip', app: 'shop-server', version: 2,
    //  دیتابیس میانِ دو اثرِ انگشت عوض شد ⇒ manifest نمی‌آید (تمرین «بی manifest» می‌گوید، نه «برابر»)
    ...(consistent ? { manifest: before, manifestConsistent: true } : (before ? { manifestConsistent: false } : {})),
  }, null, 2));
  await prune(kind);
  noteSuccess(kind);
  return { file: target, bytes: stat.size, kind, createdAt: Date.now(), encrypted, sha256: sha, manifest: consistent ? before.digest : null };
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
  if (db.isPglite() || /\.pglite\.tar\.gz(\.enc)?$/.test(String(file))) {
    throw new Error(
      'پشتیبانِ PGlite از داخلِ سرورِ روشن برنمی‌گردد: سرور را خاموش کنید، پوشهٔ دیتابیس را ' +
      'خالی کنید و فایل را با PGlite.create({ loadDataDir }) بار کنید — یا پوشهٔ pg را از یک نسخهٔ قبلی جایگزین کنید.'
    );
  }
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

/* ───────────── شکستِ پشتیبان بی‌صدا نمی‌ماند (SEC-DATA-05) ───────────── */

/**
 * تا پیش از این شکستِ پشتیبانِ خودکار فقط یک خطِ `console.error` بود: نه سابقه، نه پنل. سرور
 * می‌توانست هفته‌ها بی پشتیبان بدود و «سلامت» فقط «دیر شده» (زرد) می‌گفت. حالا هر شکست:
 *   • سابقهٔ سامانه `backup.failed` (همان راهِ `backup.unencrypted`)،
 *   • `status()` ⇒ `lib/system-health.js` «پشتیبان» را قرمز (`down`) می‌کند تا موفقِ بعدی،
 *   • و `setAlertHandler` برای راهِ دیگرِ خبر (همان الگوی `login-outbox`).
 * پیام کوتاه و بی نشانیِ دیتابیس است (`postgres://…` پاک می‌شود).
 */
const state = { lastFailure: null, lastSuccess: null };
let alertHandler = null;
function setAlertHandler(fn) { alertHandler = typeof fn === 'function' ? fn : null; }

function safeMessage(err) {
  return String(err?.message || err || '').replace(/\b(postgres(ql)?|pglite):\/\/\S+/gi, '<db>').slice(0, 300);
}

function noteSuccess(kind) { state.lastSuccess = { at: Date.now(), kind }; }

function noteFailure(kind, err) {
  const entry = { at: Date.now(), kind, message: safeMessage(err) };
  state.lastFailure = entry;
  console.error(`[backup] ⛔ ${kind} شکست خورد: ${entry.message}`);
  require('./audit').log({ actorType: 'system', action: 'backup.failed', detail: { kind, message: entry.message } }).catch(() => {});
  if (alertHandler) { try { alertHandler(entry); } catch { /* هشداردهنده خودش خراب بود */ } }
  return entry;
}

function status() { return { lastFailure: state.lastFailure, lastSuccess: state.lastSuccess }; }

/** نوبتِ خودکار: نوعِ امروز، و هر شکست ⇒ `noteFailure` (هرگز throw نمی‌کند) */
async function tick(d = new Date()) {
  const kind = d.getUTCDate() === 1 ? 'monthly' : (d.getUTCDay() === 0 ? 'weekly' : 'daily');
  try {
    const done = await run({ kind });
    console.log(`[backup] ${kind}: ${path.basename(done.file)} (${Math.round(done.bytes / 1024)}KB)`);
    return done;
  } catch (err) {
    noteFailure(kind, err);
    return null;
  }
}

/** زمان‌بندی خودکار: روزانه، هفتگی (یکشنبه) و ماهانه (اول ماه). */
function schedule() {
  if (!config.backup.enabled) return null;
  const timer = setInterval(() => { tick(); }, config.backup.intervalMs);
  if (timer.unref) timer.unref();
  return timer;
}

module.exports = {
  run, list, prune, restore, decode, schedule, tick, status, setAlertHandler, noteFailure,
  KINDS, dirFor, UNENCRYPTED_WARNING,
};
