'use strict';
/**
 * توکن‌های نشست — مات (opaque) و تصادفی.
 *
 * فقط SHA-256 توکن در دیتابیس ذخیره می‌شود؛ اگر دیتابیس لو برود نمی‌توان
 * از روی آن توکن معتبر ساخت. برخلاف JWT، این توکن‌ها فوراً قابل باطل
 * کردن‌اند: خروج از حساب یا حذف شاگرد بلافاصله اثر می‌کند.
 */
const { randomBytes, createHash, timingSafeEqual } = require('crypto');
const { query, one, now } = require('../db');

function generateToken() { return randomBytes(32).toString('base64url'); }
function hashToken(token) { return createHash('sha256').update(String(token)).digest('hex'); }

/**
 *  @param appVersion نسخه‌ی برنامه‌ای که این نشست را می‌سازد.
 *
 *  خالی یعنی «نگفت» — نسخه‌های امروزِ دستِ کاربر چیزی نمی‌فرستند و
 *  نباید هم از کار بیفتند. شرحش سرِ `migrations/015`.
 */
async function issue({ kind, subjectId, deviceId = null, ttlMs, app = 'shop', appVersion = '' }) {
  const token = generateToken();
  const issuedAt = now();
  const expiresAt = issuedAt + ttlMs;
  await query(
    `INSERT INTO tokens (token_hash, kind, subject_id, device_id, issued_at, expires_at, app, app_version)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [hashToken(token), kind, subjectId, deviceId, issuedAt, expiresAt, app, String(appVersion || '').slice(0, 32)]
  );
  return { token, expiresAt, app };
}

/**
 * توکن را بررسی می‌کند؛ نامعتبر/منقضی/باطل → null.
 *
 * ── چرا `app` هم سنجیده می‌شود ────────────────────────────────────
 * خواستهٔ صاحب مخزن: «شاپ و پمپ ربطی به هم نداشته باشند، حتی یک ذره.»
 *
 * تا دیروز یک توکن هر دو بخش را باز می‌کرد. یعنی کسی که فقط دکان
 * داشت، با همان توکن می‌توانست مسیرهای پمپ را صدا بزند — و برعکس.
 * حالا توکنِ یک بخش در بخشِ دیگر **انگار اصلاً وجود ندارد**: نه
 * «دسترسی نداری»، بلکه «چنین نشستی نیست».
 *
 * `app = null` یعنی «هر بخشی» و فقط برای کارهای درونیِ خودِ سرور
 * (مثلِ باطل کردن) به کار می‌رود، نه برای مسیرها.
 */
async function verify(token, kind, app = 'shop') {
  if (typeof token !== 'string' || token.length < 20) return null;
  const row = await one(
    'SELECT * FROM tokens WHERE token_hash = $1 AND kind = $2',
    [hashToken(token), kind]
  );
  if (!row) return null;
  if (row.revoked_at) return null;
  if (Number(row.expires_at) < now()) return null;
  //  توکنِ مدیر بخش ندارد؛ پنل یکی است و بالای هر دو می‌نشیند
  if (app !== null && kind !== 'admin' && (row.app || 'shop') !== app) return null;
  return row;
}

/**
 * تازه‌سازیِ **چرخشی** — بندِ ۲.۵ پرامپتِ ورود.
 *
 * توکنِ تازه‌سازیِ قبلی باطل می‌شود و جانشینش را در خودش نگه می‌دارد. اگر
 * برنامه دو بار هم‌زمان تازه‌سازی بزند (که پیش می‌آید: دو درخواستِ موازی
 * هر دو ۴۰۱ می‌گیرند)، توکنِ قبلی تا **سی ثانیه** هنوز پذیرفته می‌شود و
 * همان جفتِ تازه را برمی‌گرداند — وگرنه کاربر بی‌دلیل بیرون می‌افتاد.
 *
 * ⚠️ نام‌گذاری: چیزی که برمی‌گردد `reused` یعنی «این همان چرخشِ قبلی بود»،
 * نه یک نشستِ تازه. بی این، هر بار دو توکنِ تازه ساخته می‌شد.
 */
const GRACE_MS = Number(process.env.REFRESH_GRACE_MS || 30_000);
//  سقفِ دنبال کردنِ زنجیره — تا یک حلقهٔ خراب به گردشِ بی‌پایان نرسد
const GRACE_MAX_HOPS = 12;

async function findRefresh(token) {
  const hash = hashToken(token);
  const row = await one('SELECT * FROM tokens WHERE token_hash=$1 AND kind=$2', [hash, 'refresh']);
  if (!row) return { row: null, hash };
  return { row, hash };
}

async function rotateRefresh(token, { app = null } = {}) {
  const { row, hash } = await findRefresh(token);
  if (!row) return null;

  /*
   *  پنجرهٔ ارفاق — و **زنجیره‌اش دنبال می‌شود، نه یک گام**.
   *
   *  ⚠️ این یک باگِ واقعی بود که PGlite لوش داد: کاربری که در همان سی
   *  ثانیه سه بار تازه‌سازی می‌زد (اینترنتِ لرزان، دو درخواستِ موازی و
   *  بعد یک تلاشِ دستی) بارِ سوم ۴۰۱ می‌گرفت، چون جانشینِ اول خودش
   *  چرخیده بود و ما همان‌جا می‌ایستادیم. PostgreSQL پنهانش می‌کرد چون
   *  درخواست‌های موازی را واقعاً هم‌زمان می‌دواند.
   *
   *  ⚠️ توکنِ خامِ جانشین را نداریم (فقط هشش ذخیره می‌شود)، پس «همان
   *  جفتِ قبلی» پس داده نمی‌شود؛ از **آخرین حلقهٔ زنده** می‌چرخیم.
   */
  if (row.revoked_at) {
    let node = row;
    for (let hop = 0; hop < GRACE_MAX_HOPS; hop++) {
      if (!node.rotated_to || !node.grace_until || Number(node.grace_until) < now()) return null;
      const heir = await one('SELECT * FROM tokens WHERE token_hash=$1 AND kind=$2', [node.rotated_to, 'refresh']);
      if (!heir || Number(heir.expires_at) < now()) return null;
      if (!heir.revoked_at) return { reused: true, row: heir, hash: node.rotated_to };
      node = heir;   //  این یکی هم چرخیده — یک حلقه جلوتر را نگاه کن
    }
    return null;
  }
  if (Number(row.expires_at) < now()) return null;
  if (app !== null && (row.app || 'shop') !== app) return null;
  return { reused: false, row, hash };
}

/** توکنِ قبلی را باطل می‌کند و می‌گوید جانشینش کیست (برای پنجرهٔ ارفاق). */
async function markRotated(previousHash, newToken) {
  await query(
    'UPDATE tokens SET revoked_at=$2, rotated_to=$3, grace_until=$4 WHERE token_hash=$1 AND revoked_at IS NULL',
    [previousHash, now(), hashToken(newToken), now() + GRACE_MS]
  );
}

async function revoke(token, kind) {
  const r = await query(
    'UPDATE tokens SET revoked_at = $1 WHERE token_hash = $2 AND kind = $3 AND revoked_at IS NULL',
    [now(), hashToken(token), kind]
  );
  return r.rowCount > 0;
}

/**
 * خروج از همه‌ی دستگاه‌ها.
 *
 * ⚠️ `app` اگر داده شود فقط نشست‌های همان بخش می‌روند.
 *
 * ⛔ **و باید داده شود، از هر مسیری.** تا دیروز «خروج از همه‌ی
 * دستگاه‌ها» در برنامهٔ دکان، نشستِ **پمپِ** همان آدم را هم می‌کشت —
 * در حالی که کلِ قرارِ این سرور این است که دو بخش دو چیزِ جدا باشند و
 * توکنِ یکی در دیگری اصلاً پیدا نشود. توکنِ دکان هم که لو برود به
 * پمپ نمی‌رسد، پس بستنِ آن یکی هیچ چیزی را امن‌تر نمی‌کند و فقط
 * کاربر را از برنامهٔ دیگرش بیرون می‌اندازد.
 *
 * `app = null` یعنی «هر بخشی» و جای درستش عوض شدنِ رمز و بازیابی
 * است: آن‌جا خودِ **هویت** عوض شده، نه یک نشست.
 */
async function revokeAllForSubject(subjectId, kind = null, app = null) {
  const args = [now(), subjectId];
  let sql = 'UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL';
  if (kind) { args.push(kind); sql += ` AND kind=$${args.length}`; }
  if (app) { args.push(app); sql += ` AND COALESCE(app,'shop')=$${args.length}`; }
  const r = await query(sql, args);
  return r.rowCount;
}

/**
 * خروج از **بقیه‌ی** دستگاه‌ها — همان که بعد از عوض کردنِ رمز لازم است.
 *
 * ⛔ تا دیروز عوض کردنِ رمز از داخلِ برنامه **هیچ نشستی را نمی‌بست**.
 * یعنی کسی که رمزش را عوض می‌کرد چون گمان می‌کرد لو رفته، همان
 * نشستِ لو‌رفته تا نود روز (عمرِ توکنِ تازه‌سازی) زنده می‌ماند. هر
 * سرورِ حرفه‌ای این را می‌بندد.
 *
 * ⚠️ نشستِ **خودِ همین دستگاه** می‌ماند، وگرنه کاربر با عوض کردنِ رمز
 * از برنامهٔ خودش هم بیرون می‌افتاد و گمان می‌کرد کار خراب شد.
 */
async function revokeOthersForSubject(subjectId, { keepDeviceId = null, keepTokenHash = null } = {}) {
  const args = [now(), subjectId];
  let sql = 'UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL';
  if (keepDeviceId) { args.push(keepDeviceId); sql += ` AND COALESCE(device_id,'') <> $${args.length}`; }
  else if (keepTokenHash) { args.push(keepTokenHash); sql += ` AND token_hash <> $${args.length}`; }
  const r = await query(sql, args);
  return r.rowCount;
}

async function revokeAllForDevice(deviceId) {
  const r = await query(
    'UPDATE tokens SET revoked_at=$1 WHERE device_id=$2 AND revoked_at IS NULL', [now(), deviceId]
  );
  return r.rowCount;
}

/** مقایسه‌ی امن (زمان‌ثابت) دو رشته. */
function safeEqual(a, b) {
  const ba = Buffer.from(String(a)), bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return timingSafeEqual(ba, bb);
}

module.exports = {
  rotateRefresh, markRotated, findRefresh, GRACE_MS,
  generateToken, hashToken, issue, verify, revoke,
  revokeAllForSubject, revokeOthersForSubject, revokeAllForDevice, safeEqual,
};
