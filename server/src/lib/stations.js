'use strict';
/**
 * پمپ‌بنزین‌ها و اعضایشان — قرینهٔ `lib/shops.js` برای بخشِ پمپ.
 *
 * قاعده‌ی کلیدی همان است: `station_id` هرگز از بدنه‌ی درخواست خوانده
 * نمی‌شود. سرور خودش از روی کاربرِ توکن، عضویت او را پیدا می‌کند. به
 * همین دلیل کسی نمی‌تواند با عوض کردن یک شناسه به دفترِ پمپِ دیگری برسد.
 */
const { query, one, many, tx, newId, now } = require('../db');
const { forbidden, notFound, conflict, badRequest } = require('../middleware/errors');
const { can } = require('./permissions');

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
      `INSERT INTO stations (id, owner_user_id, code, name, status, created_at, updated_at)
       VALUES ($1,$2,$3,$4,'active',$5,$5)`,
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
  const s = await one(
    `UPDATE stations
        SET name      = COALESCE($2, name),
            home_url  = COALESCE($3, home_url),
            home_seen_at = CASE WHEN $3 IS NULL THEN home_seen_at ELSE $4 END,
            updated_at = $4
      WHERE id = $1 RETURNING *`,
    [stationId, patch.name ?? null, patch.homeUrl ?? null, t]
  );
  if (!s) throw notFound('پمپ پیدا نشد', 'station_not_found');
  return s;
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
    status: s.status,
    ownerUserId: s.owner_user_id,
    createdAt: Number(s.created_at),
    updatedAt: Number(s.updated_at),
  };
}

module.exports = {
  cleanCode, membershipOf, requireMembership, assertCan, createStation,
  getStation, byCode, updateStation, members, memberCount, updateMember, shape,
};
