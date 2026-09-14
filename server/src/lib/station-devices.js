'use strict';
/**
 * دستگاه‌های ثبت‌شدهٔ یک پمپ — و «کدِ شش‌رقمی، بعد اشتراک».
 *
 * ── مشکلی که این حل می‌کند ─────────────────────────────────────────
 * خواستهٔ صاحب مخزن: «نشانی توی خودِ برنامه باشد و دیده نشود، و اشتراک
 * هم از سرور برایش برود.»
 *
 * برنامهٔ کامپیوترِ پمپ یک برنامهٔ ویندوزیِ تک‌کاربره است. ورودِ گوگل
 * آن‌جا یعنی مرورگر باز کن، به سایتِ گوگل برو، برگرد — برای کسی که فقط
 * می‌خواهد دفترش را باز کند، سه پلهٔ اضافه و یک وابستگیِ تازه به
 * اینترنتِ گوگل.
 *
 * پس: صاحبِ پمپ همان **شش رقمی** را که خریده در برنامه می‌زند. از همان
 * لحظه برنامه یک توکنِ دستگاه دارد، اشتراکش فعال است، و مجوزِ
 * امضاشده‌اش را می‌گیرد. هیچ حسابی، هیچ مرورگری، هیچ نشانی‌ای.
 *
 * ── چرا جدولِ جدا و نه `devices`ِ دکان‌ها ──────────────────────────
 * چون قرار است دو بخش «حتی یک ذره» به هم ربط نداشته باشند. اگر همان
 * جدول را به کار می‌بردیم، دوباره یک ریسمان بینشان بسته بودیم.
 *
 * ── توکن ──────────────────────────────────────────────────────────
 * مات و تصادفی، و فقط هشش ذخیره می‌شود — مثل هر توکنِ دیگری در این
 * سامانه. لو رفتنِ دیتابیس، توکنِ زنده‌ای به کسی نمی‌دهد.
 *
 * ⚠️ این توکن **تاریخِ انقضا ندارد** و این عمدی است: برنامهٔ کامپیوترِ
 * پمپ ممکن است ماه‌ها بی‌اینترنت کار کند و بعد وصل شود. چیزی که
 * انقضا دارد **مجوز** است (ده روز)، و اشتراکِ تمام‌شده همان‌جا جلوی
 * کار را می‌گیرد — نه این‌جا. باطل کردنِ دستی هم هست (`revoke`).
 */
const { createHash, randomBytes, randomInt, createHmac } = require('crypto');
const { query, one, many, newId, now } = require('../db');
const config = require('../config');
const { badRequest, notFound, forbidden, conflict } = require('../middleware/errors');

function hashToken(t) { return createHash('sha256').update(String(t)).digest('hex'); }

/** توکنِ دستگاه. پیشوند دارد تا در لاگ و گزارشِ خطا شناخته شود. */
function newToken() { return `pd_${randomBytes(32).toString('base64url')}`; }

/**
 * ثبت یا به‌روزرسانیِ یک دستگاه روی یک پمپ.
 *
 * اگر همان `deviceUid` از قبل روی همین پمپ باشد، توکنِ تازه می‌گیرد و
 * قبلی می‌افتد — یعنی نصبِ دوبارهٔ برنامه روی همان کامپیوتر، ردیفِ
 * تازه نمی‌سازد و فهرستِ دستگاه‌ها بی‌جهت بلند نمی‌شود.
 */
async function register(stationId, { uid, name = '', platform = '', ip = '' } = {}) {
  const deviceUid = String(uid || '').trim();
  if (!deviceUid || deviceUid.length > 120) {
    throw badRequest('شناسهٔ دستگاه معتبر نیست', 'bad_device');
  }
  const token = newToken();
  const t = now();
  const row = await one(
    `INSERT INTO station_devices
       (id, station_id, token_hash, device_uid, name, platform, status, created_at, last_seen_at, last_ip)
     VALUES ($1,$2,$3,$4,$5,$6,'active',$7,$7,$8)
     ON CONFLICT (station_id, device_uid) DO UPDATE SET
       token_hash = excluded.token_hash,
       name       = COALESCE(NULLIF(excluded.name,''), station_devices.name),
       platform   = COALESCE(NULLIF(excluded.platform,''), station_devices.platform),
       status     = 'active',
       last_seen_at = excluded.last_seen_at,
       last_ip    = excluded.last_ip
     RETURNING *`,
    [newId('sdv'), stationId, hashToken(token), deviceUid,
      String(name).slice(0, 80), String(platform).slice(0, 40), t, String(ip).slice(0, 60)]
  );
  //  توکنِ خام فقط همین یک بار برمی‌گردد
  return { token, device: shape(row) };
}

/** دستگاهی که این توکن مالِ اوست — یا null. */
async function bySecret(rawToken) {
  const clean = String(rawToken || '').trim();
  if (!clean.startsWith('pd_')) return null;
  const row = await one(
    `SELECT d.*, s.status AS station_status
       FROM station_devices d
       JOIN stations s ON s.id = d.station_id
      WHERE d.token_hash = $1`,
    [hashToken(clean)]
  );
  if (!row) return null;
  if (row.status !== 'active') return null;
  //  پمپِ خاموش‌شده یعنی همهٔ دستگاه‌هایش هم خاموش‌اند
  if (row.station_status !== 'active') return null;
  query('UPDATE station_devices SET last_seen_at=$2 WHERE id=$1', [row.id, now()]).catch(() => {});
  return row;
}

async function list(stationId) {
  return (await many(
    `SELECT * FROM station_devices WHERE station_id=$1 ORDER BY created_at DESC`, [stationId]
  )).map(shape);
}

async function revoke(stationId, deviceId) {
  const row = await one(
    `UPDATE station_devices SET status='revoked' WHERE id=$1 AND station_id=$2 RETURNING *`,
    [deviceId, stationId]
  );
  if (!row) throw notFound('این دستگاه پیدا نشد', 'device_not_found');
  return shape(row);
}

function shape(r) {
  return {
    id: r.id,
    deviceUid: r.device_uid,
    name: r.name || '',
    platform: r.platform || '',
    status: r.status,
    createdAt: Number(r.created_at),
    lastSeenAt: r.last_seen_at ? Number(r.last_seen_at) : null,
  };
}

/* ══════════════════════════════════════════════════════════════════
   کدِ پیوستن — راهِ گوشیِ کارمند به پمپی که با کد فعال شده
   ══════════════════════════════════════════════════════════════════

   پمپی که برنامهٔ کامپیوترش با کدِ شش‌رقمی فعال شده، هیچ حسابِ گوگلی
   ندارد. کارمند که می‌خواهد با گوشی ببیندش، باید راهی داشته باشد.

   پس برنامهٔ کامپیوتر یک کدِ کوتاه نشان می‌دهد و کارمند همان را در
   اپ می‌زند. برخلافِ کدِ اشتراک، این یکی چندبار مصرف است (چند کارمند
   با یک کد می‌آیند) ولی سقف و مهلت دارد.
*/
const JOIN_DIGITS = 6;

function joinPepper() {
  return config.secrets.api || config.secrets.jwt || 'station-join';
}
function hashJoin(code) {
  return createHmac('sha256', joinPepper()).update(String(code || '').replace(/\D/g, '')).digest('hex');
}
function randomJoin() {
  let s = String(randomInt(1, 10));
  for (let i = 1; i < JOIN_DIGITS; i++) s += String(randomInt(10));
  return s;
}

/** کدِ پیوستنِ تازه. کدِ قبلیِ همان پمپ باطل می‌شود. */
async function mintJoinCode(stationId, { role = 'staff', hours = 24, maxUses = 10 } = {}) {
  await query(
    `UPDATE station_join_codes SET status='revoked' WHERE station_id=$1 AND status='active'`,
    [stationId]
  );
  const t = now();
  for (let attempt = 0; attempt < 12; attempt++) {
    const code = randomJoin();
    const h = hashJoin(code);
    if (await one(`SELECT 1 FROM station_join_codes WHERE code_hash=$1 AND status='active'`, [h])) continue;
    const row = await one(
      `INSERT INTO station_join_codes
         (id, station_id, code_hash, code_hint, role, created_at, expires_at, max_uses, used_count, status)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,0,'active') RETURNING *`,
      [newId('sjc'), stationId, h, code.slice(-2),
        ['owner', 'manager', 'staff'].includes(role) ? role : 'staff',
        t, t + hours * 3600 * 1000, Math.max(1, Math.min(100, maxUses))]
    );
    return { code, expiresAt: Number(row.expires_at), role: row.role };
  }
  throw conflict('ساخت کد ممکن نشد، دوباره تلاش کنید', 'join_code_failed');
}

/**
 * پیوستنِ یک حسابِ گوگل به پمپ.
 *
 * ⚠️ اولین کسی که می‌پیوندد و پمپ صاحب ندارد، **صاحب** می‌شود — چون
 * پمپی که با کد فعال شده هیچ صاحبی ندارد و باید یکی پیدا کند. بقیه
 * همان نقشی را می‌گیرند که کد می‌گوید.
 */
async function redeemJoinCode(rawCode, userId) {
  const clean = String(rawCode || '').replace(/\D/g, '');
  if (clean.length !== JOIN_DIGITS) throw badRequest('کد باید شش رقم باشد', 'bad_code');

  const row = await one(
    `SELECT * FROM station_join_codes WHERE code_hash=$1 FOR UPDATE`, [hashJoin(clean)]
  );
  if (!row) throw notFound('این کد معتبر نیست', 'bad_code');
  if (row.status !== 'active') throw forbidden('این کد دیگر کار نمی‌کند', 'code_inactive');
  if (row.expires_at && Number(row.expires_at) < now()) {
    await query(`UPDATE station_join_codes SET status='revoked' WHERE id=$1`, [row.id]);
    throw forbidden('مهلت این کد تمام شده است', 'code_expired');
  }
  if (Number(row.used_count) >= Number(row.max_uses)) {
    await query(`UPDATE station_join_codes SET status='exhausted' WHERE id=$1`, [row.id]);
    throw forbidden('این کد به سقفش رسیده است', 'code_exhausted');
  }

  const station = await one('SELECT * FROM stations WHERE id=$1', [row.station_id]);
  if (!station || station.status !== 'active') throw notFound('پمپ پیدا نشد', 'station_not_found');

  //  کسی که از قبل عضوِ پمپِ دیگری است نمی‌تواند دو جا باشد
  const elsewhere = await one(
    `SELECT 1 FROM station_members WHERE user_id=$1 AND status='active' AND station_id <> $2`,
    [userId, station.id]
  );
  if (elsewhere) throw conflict('این حساب از قبل عضو پمپ دیگری است', 'already_member');

  const firstOne = !station.owner_user_id;
  const role = firstOne ? 'owner' : row.role;
  const t = now();

  await query(
    `INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)
     ON CONFLICT (station_id, user_id) DO UPDATE SET status='active', updated_at=excluded.updated_at`,
    [newId('mem'), station.id, userId, role, t]
  );
  if (firstOne) {
    await query('UPDATE stations SET owner_user_id=$2, updated_at=$3 WHERE id=$1',
      [station.id, userId, t]);
  }
  await query(
    `UPDATE station_join_codes SET used_count = used_count + 1,
            status = CASE WHEN used_count + 1 >= max_uses THEN 'exhausted' ELSE status END
      WHERE id=$1`,
    [row.id]
  );

  return { stationId: station.id, role, becameOwner: firstOne };
}

module.exports = {
  register, bySecret, list, revoke, shape, hashToken,
  mintJoinCode, redeemJoinCode, JOIN_DIGITS,
};
