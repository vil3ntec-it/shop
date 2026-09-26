'use strict';
/**
 * پمپ‌بنزین‌ها و اعضایشان — قرینهٔ `lib/shops.js` برای بخشِ پمپ.
 *
 * قاعده‌ی کلیدی همان است: `station_id` هرگز از بدنه‌ی درخواست خوانده
 * نمی‌شود. سرور خودش از روی کاربرِ توکن، عضویت او را پیدا می‌کند. به
 * همین دلیل کسی نمی‌تواند با عوض کردن یک شناسه به دفترِ پمپِ دیگری برسد.
 */
const crypto = require('crypto');
const { query, one, many, tx, newId, now } = require('../db');
const config = require('../config');
const { forbidden, notFound, conflict, badRequest } = require('../middleware/errors');
const { can } = require('./permissions');

/* ──────────────────────────────────────────────────────────────────
   رمزِ فقط‌خواندنیِ سرورِ خانگی

   برخلافِ کدِ شش‌رقمی، این یکی باید **پس داده شود**: کارمند آن را به
   سرورِ خانگیِ پمپ نشان می‌دهد. پس هشِ یک‌طرفه به کار نمی‌آید و
   رمزگذاریِ برگشت‌پذیر لازم است.

   AES-256-GCM: هم پنهان می‌کند هم دست‌کاری را لو می‌دهد. اگر ردیفی
   دست‌کاری شده باشد، رمزگشایی می‌افتد و ما رشتهٔ خالی برمی‌گردانیم —
   نه چیزی که ممکن است نیمه‌درست باشد.

   ── کلید کجاست، و چرا آن‌جا ─────────────────────────────────────
   در `app_config`، دقیقاً همان‌جا که کلیدِ خصوصیِ مجوز می‌نشیند
   (`lib/license.js`) — و به همان دلیل.

   اولین نسخه کلید را از `API_SECRET` می‌ساخت. آن یک تله بود: صاحب
   سامانه دیتابیس را پشتیبان می‌گرفت، روی کامپیوترِ دیگری برمی‌گرداند،
   و چون `API_SECRET`ِ تازه‌ای می‌ساخت، رمزِ **همهٔ** پمپ‌ها ناخوانا
   می‌شد. دیتابیس سالم، ولی هیچ اپِ کارمندی دیگر وصل نمی‌شد — و هیچ
   خطایی هم نمی‌گفت چرا.

   حالا کلید با همان `pg_dump` سفر می‌کند. یک بار ساخته می‌شود و
   می‌ماند؛ `STATION_KEY` در محیط، اگر باشد، جایش را می‌گیرد (برای
   وقتی که چند سرور باید یک کلید داشته باشند).

   ⚠️ پس پشتیبانِ دیتابیس خودش رمزها را هم دارد. این آگاهانه است:
   بی آن، «سرور را جابه‌جا کن» کار نمی‌کرد. خودِ فایلِ پشتیبان است که
   باید محافظت شود — `BACKUP_PASSPHRASE` همین کار را می‌کند.
   ────────────────────────────────────────────────────────────────── */
const KEY_STATION = 'station_read_key';

let cachedKey = null;

/** کلیدِ رمزگذاری. یک بار ساخته و در `app_config` نگه داشته می‌شود. */
async function secretKey() {
  if (cachedKey) return cachedKey;

  const fromEnv = process.env.STATION_KEY;
  if (fromEnv && fromEnv.trim()) {
    cachedKey = crypto.createHash('sha256').update(fromEnv.trim()).digest();
    return cachedKey;
  }

  const plans = require('./plans');
  let stored = await plans.getConfig(KEY_STATION, '');

  if (!stored) {
    /*
     *  ⚠️ ساختنِ کلید باید اتمی باشد، و `setConfig` نیست: آن روی‌نویسی
     *  می‌کند. روی سرورِ تازه، دو درخواستِ هم‌زمان هر کدام کلیدی
     *  می‌ساختند و دومی اولی را پاک می‌کرد — و رمزِ هر پمپی که با کلیدِ
     *  اول نوشته شده بود برای همیشه ناخوانا می‌شد، بی هیچ خطایی.
     *
     *  `DO NOTHING` یعنی فقط اولی می‌نشیند؛ بعد همان را می‌خوانیم، نه
     *  چیزی را که خودمان ساختیم.
     */
    await query(
      `INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,$3)
       ON CONFLICT (key) DO NOTHING`,
      [KEY_STATION, crypto.randomBytes(32).toString('base64'), now()]
    );
    stored = await plans.getConfig(KEY_STATION, '');
    if (!stored) throw new Error('کلیدِ رمزگذاریِ پمپ ساخته نشد');
  }

  cachedKey = crypto.createHash('sha256').update(stored).digest();
  return cachedKey;
}

/**
 * کلیدِ نسلِ اول — از `API_SECRET`.
 *
 * فقط برای خواندنِ ردیف‌هایی که پیش از این تغییر نوشته شده‌اند. نوشتنِ
 * تازه هرگز با این انجام نمی‌شود.
 */
function legacyKey() {
  const raw = config.secrets.api || config.secrets.jwt || 'shop-station-read-key';
  return crypto.createHash('sha256').update(String(raw)).digest();
}

function seal(key, plain) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', key, iv);
  const body = Buffer.concat([c.update(plain, 'utf8'), c.final()]);
  return `v1.${iv.toString('base64url')}.${body.toString('base64url')}.${c.getAuthTag().toString('base64url')}`;
}

function open(key, stored) {
  const [, ivB, bodyB, tagB] = stored.split('.');
  const d = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(ivB, 'base64url'));
  d.setAuthTag(Buffer.from(tagB, 'base64url'));
  return Buffer.concat([d.update(Buffer.from(bodyB, 'base64url')), d.final()]).toString('utf8');
}

async function encryptKey(plain) {
  const s = String(plain || '');
  if (!s) return '';
  return seal(await secretKey(), s);
}

async function decryptKey(stored) {
  const s = String(stored || '');
  if (!s.startsWith('v1.')) return '';
  try {
    return open(await secretKey(), s);
  } catch {
    //  شاید با کلیدِ نسلِ اول نوشته شده باشد
    try { return open(legacyKey(), s); } catch { /* نه */ }
    //  کلید عوض شده یا ردیف دست‌کاری شده. هر دو یعنی «نداریم».
    return '';
  }
}

/**
 * کدِ پمپ — همان چیزی که برنامهٔ کامپیوتر و اپِ کارمند با آن خودشان را
 * معرفی می‌کنند. در نشانی می‌آید، پس فقط حروفِ کوچکِ انگلیسی و رقم.
 */
function cleanCode(raw) {
  const s = String(raw || '').trim().toLowerCase()
    .replace(/[^a-z0-9-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  if (s.length < 2 || s.length > 40) {
    throw badRequest('کدِ پمپ باید بین ۲ تا ۴۰ حرف انگلیسی یا رقم باشد', 'bad_station_code');
  }
  return s;
}

/** عضویت فعال کاربر — کاربر فقط در یک پمپ فعال است. */
async function membershipOf(userId) {
  return one(
    `SELECT m.*, s.name AS station_name, s.code AS station_code,
            s.status AS station_status, s.owner_user_id, s.home_url
       FROM station_members m
       JOIN stations s ON s.id = m.station_id
      WHERE m.user_id = $1 AND m.status = 'active' AND s.status = 'active'
      ORDER BY (m.role = 'owner') DESC, m.created_at ASC
      LIMIT 1`,
    [userId]
  );
}

/** عضویت لازم است؛ اگر نبود خطای روشن می‌دهد. */
async function requireMembership(userId) {
  const m = await membershipOf(userId);
  if (!m) throw notFound('برای این حساب پمپی ثبت نشده است', 'no_station');
  return m;
}

function assertCan(member, permission) {
  if (!can(member.role, permission)) {
    throw forbidden('این کار در حد دسترسی شما نیست', 'permission_denied');
  }
}

/**
 * ساخت پمپ — سازنده مالک می‌شود. هر کاربر یک پمپ می‌سازد.
 *
 * اگر کد نیامده باشد از روی شناسه ساخته می‌شود، تا کسی که فقط برنامه را
 * باز کرده مجبور نباشد چیزی بنویسد. اگر آمده باشد و گرفته شده باشد،
 * خطای روشن می‌گیرد نه یک کدِ عوضی.
 */
async function createStation(userId, { name = '', code = '' } = {}) {
  const existing = await membershipOf(userId);
  if (existing) throw conflict('این حساب از قبل عضو یک پمپ است', 'already_member');

  const stationId = newId('stn');
  const wanted = code ? cleanCode(code) : `p${stationId.slice(-8).toLowerCase()}`;

  const taken = await one('SELECT 1 FROM stations WHERE code=$1', [wanted]);
  if (taken) throw conflict('این کد قبلاً گرفته شده است', 'code_taken');

  return tx(async (c) => {
    const t = now();
    await c.query(
      //  ⛔ آغازِ دورهٔ آزمایشی همین لحظه، روی خودِ سرور (مهاجرتِ ۰۳۲)
      `INSERT INTO stations (id, owner_user_id, code, name, status, created_at, updated_at, trial_started_at)
       VALUES ($1,$2,$3,$4,'active',$5,$5,$5)`,
      [stationId, userId, wanted, name || 'پمپ من', t]
    );
    await c.query(
      `INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
       VALUES ($1,$2,$3,'owner','active',$4,$4)`,
      [newId('mem'), stationId, userId, t]
    );
    await c.query('INSERT INTO station_rev (station_id, last_rev) VALUES ($1, 0)', [stationId]);
    return c.query('SELECT * FROM stations WHERE id=$1', [stationId]).then(r => r.rows[0]);
  });
}

/**
 * پمپی که برنامهٔ کامپیوتر با کد ساخته — هنوز بی صاحبِ گوگل.
 *
 * ⚠️ `owner_user_id` خالی می‌ماند و این عمدی است: برنامهٔ کامپیوتر هیچ
 * حسابی ندارد. اولین کسی که با کدِ پیوستن می‌آید صاحب می‌شود
 * (`station-devices.redeemJoinCode`).
 */
async function createStationForDevice({ code = '', name = '' } = {}) {
  const stationId = newId('stn');
  const wanted = code ? cleanCode(code) : `p${stationId.slice(-8).toLowerCase()}`;
  if (await one('SELECT 1 FROM stations WHERE code=$1', [wanted])) {
    throw conflict('این کد قبلاً گرفته شده است', 'code_taken');
  }
  return tx(async (c) => {
    const t = now();
    await c.query(
      `INSERT INTO stations (id, owner_user_id, code, name, status, created_at, updated_at, trial_started_at)
       VALUES ($1, NULL, $2, $3, 'active', $4, $4, $4)`,
      [stationId, wanted, name || 'پمپ من', t]
    );
    await c.query('INSERT INTO station_rev (station_id, last_rev) VALUES ($1, 0)', [stationId]);
    return c.query('SELECT * FROM stations WHERE id=$1', [stationId]).then(r => r.rows[0]);
  });
}

async function getStation(stationId) {
  const s = await one('SELECT * FROM stations WHERE id=$1', [stationId]);
  if (!s) throw notFound('پمپ پیدا نشد', 'station_not_found');
  return s;
}

async function byCode(code) {
  return one('SELECT * FROM stations WHERE code=$1', [String(code || '').trim().toLowerCase()]);
}

/**
 * به‌روزرسانیِ پمپ.
 *
 * `homeUrl` نشانیِ سرورِ خانگیِ همان پمپ است. برنامهٔ کامپیوتر هر بار که
 * وصل می‌شود آن را می‌فرستد، و اپِ کارمند از سرور می‌خواندش — به همین
 * دلیل دیگر لازم نیست از کسی نشانی پرسیده شود.
 */
async function updateStation(stationId, patch = {}) {
  const t = now();
  //  رمز فقط وقتی عوض می‌شود که آمده باشد؛ `undefined` یعنی «دست نزن»
  const encKey = patch.readKey === undefined ? null : await encryptKey(patch.readKey);
  const home = patch.homeStation === undefined ? null : cleanHomeStation(patch.homeStation);
  const s = await one(
    `UPDATE stations
        SET name      = COALESCE($2, name),
            home_url  = COALESCE($3, home_url),
            read_key_enc = COALESCE($5, read_key_enc),
            home_station = COALESCE($6, home_station),
            home_seen_at = CASE WHEN $3 IS NULL THEN home_seen_at ELSE $4 END,
            updated_at = $4
      WHERE id = $1 RETURNING *`,
    [stationId, patch.name ?? null, patch.homeUrl ?? null, t, encKey, home]
  );
  if (!s) throw notFound('پمپ پیدا نشد', 'station_not_found');
  return s;
}

/**
 * کدِ پوشهٔ سرورِ خانگی — همان شکلی که سرورِ خانگی می‌پذیرد
 * (`[a-z0-9_-]`، تا ۶۴). خالی ⇒ `null` یعنی «دست نزن»، نه «پاکش کن».
 */
function cleanHomeStation(raw) {
  const s = String(raw ?? '').trim().toLowerCase();
  if (!s) return null;
  if (!/^[a-z0-9_-]{2,64}$/.test(s)) {
    throw badRequest('کدِ پوشهٔ سرورِ خانگی نامعتبر است', 'bad_home_station');
  }
  return s;
}

/**
 * پوشه‌ای که گوشی روی سرورِ خانگی باید بپرسد. ⛔ همانی که برنامهٔ کامپیوتر
 * گفته رویش می‌نویسد؛ تا نگفته، همان کدِ پمپ.
 */
function homeStationOf(s) {
  return (s && s.home_station) || (s && s.code) || '';
}

/**
 * رمزِ فقط‌خواندنیِ این پمپ، به شکلِ خام.
 *
 * ⚠️ فقط برای کسی صدا زده شود که عضوِ همین پمپ است. مسیرها این را
 * پیش از صدا زدن سنجیده‌اند (`requireStation`)، ولی خودِ این تابع
 * چیزی نمی‌سنجد — پس جای دیگری صدایش نزنید.
 */
async function readKeyOf(stationRow) {
  return decryptKey(stationRow && stationRow.read_key_enc);
}

/** اعضای پمپ همراه نام و شماره — برای صفحه‌ی «کارمندها». */
async function members(stationId) {
  return many(
    `SELECT m.id, m.user_id, m.role, m.status, m.created_at,
            u.name, u.phone, u.email, u.last_login_at
       FROM station_members m
       JOIN users u ON u.id = m.user_id
      WHERE m.station_id = $1 AND m.status <> 'removed'
      ORDER BY (m.role='owner') DESC, (m.role='manager') DESC, m.created_at ASC`,
    [stationId]
  );
}

async function memberCount(stationId) {
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM station_members WHERE station_id=$1 AND status='active'`,
    [stationId]
  );
  return r.n;
}

/** تغییر وضعیت یا نقش یک عضو — مالک را نمی‌توان حذف یا کم‌دسترسی کرد. */
async function updateMember(stationId, memberId, patch = {}) {
  const m = await one('SELECT * FROM station_members WHERE id=$1 AND station_id=$2',
    [memberId, stationId]);
  if (!m) throw notFound('این عضو در پمپ شما نیست', 'member_not_found');
  if (m.role === 'owner') throw badRequest('صاحب پمپ را نمی‌توان تغییر داد', 'owner_immutable');

  const role = patch.role && ['manager', 'staff'].includes(patch.role) ? patch.role : m.role;
  const status = patch.status && ['active', 'suspended', 'removed'].includes(patch.status)
    ? patch.status : m.status;

  const row = await one(
    `UPDATE station_members SET role=$3, status=$4, updated_at=$5
      WHERE id=$1 AND station_id=$2 RETURNING *`,
    [memberId, stationId, role, status, now()]
  );
  // با حذف یا تعلیق، نشست‌های آن شخص همان لحظه باطل می‌شوند
  if (status !== 'active') {
    await query('UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL',
      [now(), m.user_id]);
    //  ⛔ و کامپیوتری که خودِ او بند کرده بود — توکنِ دستگاه انقضا ندارد
    await require('./station-devices').revokeBoundBy(stationId, m.user_id);
  }
  return row;
}

/** چیزی که برنامه و پنل می‌بینند. */
function shape(s) {
  return {
    id: s.id,
    code: s.code,
    name: s.name || '',
    homeUrl: s.home_url || '',
    homeSeenAt: s.home_seen_at ? Number(s.home_seen_at) : null,
    //  فقط «دارد یا ندارد». خودِ رمز هرگز از این تابع بیرون نمی‌رود —
    //  `shape` در پنلِ مدیریت هم به کار می‌رود.
    hasReadKey: !!(s.read_key_enc || ''),
    status: s.status,
    ownerUserId: s.owner_user_id,
    createdAt: Number(s.created_at),
    updatedAt: Number(s.updated_at),
  };
}

/**
 * فراموش کردنِ کلیدِ در حافظه.
 *
 * فقط برای آزمونِ «سرور را جابه‌جا کن»: کامپیوترِ تازه حافظه‌ای ندارد و
 * باید کلید را از دیتابیس پیدا کند. بی این، آزمون همان چیزی را که
 * می‌خواهد ثابت کند فرض می‌گرفت.
 */
function _forgetKey() { cachedKey = null; }

module.exports = {
  _forgetKey,
  cleanCode, cleanHomeStation, homeStationOf, createStationForDevice, membershipOf, requireMembership, assertCan, createStation,
  getStation, byCode, updateStation, members, memberCount, updateMember, shape,
  readKeyOf, encryptKey, decryptKey,
};
