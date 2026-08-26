'use strict';
/**
 * دکان‌ها و اعضا.
 *
 * قاعده‌ی کلیدی: shop_id هرگز از بدنه‌ی درخواست خوانده نمی‌شود.
 * سرور خودش از روی کاربرِ توکن، عضویت او را پیدا می‌کند. به همین دلیل
 * کسی نمی‌تواند با فرستادن shop_id دیگری به اطلاعات دکان دیگر برسد.
 */
const { query, one, many, tx, newId, now } = require('../db');
const { forbidden, notFound, conflict, badRequest } = require('../middleware/errors');
const { can } = require('./permissions');

/** عضویت فعال کاربر — کاربر فقط در یک دکان فعال است. */
async function membershipOf(userId) {
  return one(
    `SELECT m.*, s.name AS shop_name, s.status AS shop_status, s.owner_user_id
       FROM shop_members m
       JOIN shops s ON s.id = m.shop_id
      WHERE m.user_id = $1 AND m.status = 'active' AND s.status = 'active'
      ORDER BY (m.role = 'owner') DESC, m.created_at ASC
      LIMIT 1`,
    [userId]
  );
}

/** عضویت لازم است؛ اگر نبود خطای روشن می‌دهد. */
async function requireMembership(userId) {
  const m = await membershipOf(userId);
  if (!m) throw notFound('برای این حساب دکانی ثبت نشده است', 'no_shop');
  return m;
}

function assertCan(member, permission) {
  if (!can(member.role, permission)) {
    throw forbidden('این کار در حد دسترسی شما نیست', 'permission_denied');
  }
}

/** ساخت دکان — سازنده مالک می‌شود. هر کاربر یک دکان می‌سازد. */
async function createShop(userId, name) {
  const existing = await membershipOf(userId);
  if (existing) throw conflict('این حساب از قبل عضو یک دکان است', 'already_member');

  return tx(async (c) => {
    const t = now();
    const shopId = newId('shp');
    await c.query(
      `INSERT INTO shops (id, owner_user_id, name, status, created_at, updated_at)
       VALUES ($1,$2,$3,'active',$4,$4)`,
      [shopId, userId, name || 'دکان من', t]
    );
    await c.query(
      `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
       VALUES ($1,$2,$3,'owner','active',$4,$4)`,
      [newId('mem'), shopId, userId, t]
    );
    await c.query('INSERT INTO shop_rev (shop_id, last_rev) VALUES ($1, 0)', [shopId]);
    await c.query(
      'INSERT INTO shop_settings (shop_id, data, rev, updated_at) VALUES ($1, $2, 0, $3)',
      [shopId, '{}', t]
    );
    return c.query('SELECT * FROM shops WHERE id=$1', [shopId]).then(r => r.rows[0]);
  });
}

async function getShop(shopId) {
  const s = await one('SELECT * FROM shops WHERE id=$1', [shopId]);
  if (!s) throw notFound('دکان پیدا نشد', 'shop_not_found');
  return s;
}

async function updateShop(shopId, patch) {
  const t = now();
  const s = await one(
    'UPDATE shops SET name = COALESCE($2, name), updated_at = $3 WHERE id = $1 RETURNING *',
    [shopId, patch.name ?? null, t]
  );
  if (!s) throw notFound('دکان پیدا نشد', 'shop_not_found');
  return s;
}

/** اعضای دکان همراه نام و شماره — برای صفحه‌ی «شاگردها». */
async function members(shopId) {
  return many(
    `SELECT m.id, m.user_id, m.role, m.status, m.created_at,
            u.name, u.phone, u.email, u.last_login_at
       FROM shop_members m
       JOIN users u ON u.id = m.user_id
      WHERE m.shop_id = $1 AND m.status <> 'removed'
      ORDER BY (m.role='owner') DESC, (m.role='manager') DESC, m.created_at ASC`,
    [shopId]
  );
}

async function memberCount(shopId) {
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM shop_members WHERE shop_id=$1 AND status='active'`, [shopId]
  );
  return r.n;
}

/** تغییر وضعیت یا نقش یک عضو — مالک را نمی‌توان حذف یا کم‌دسترسی کرد. */
async function updateMember(shopId, memberId, patch) {
  const m = await one('SELECT * FROM shop_members WHERE id=$1 AND shop_id=$2', [memberId, shopId]);
  if (!m) throw notFound('این عضو در دکان شما نیست', 'member_not_found');
  if (m.role === 'owner') throw badRequest('صاحب دکان را نمی‌توان تغییر داد', 'owner_immutable');

  const role = patch.role && ['manager', 'staff'].includes(patch.role) ? patch.role : m.role;
  const status = patch.status && ['active', 'suspended', 'removed'].includes(patch.status)
    ? patch.status : m.status;

  const row = await one(
    'UPDATE shop_members SET role=$3, status=$4, updated_at=$5 WHERE id=$1 AND shop_id=$2 RETURNING *',
    [memberId, shopId, role, status, now()]
  );
  // با حذف یا تعلیق، نشست‌های آن شخص همان لحظه باطل می‌شوند
  if (status !== 'active') {
    await query('UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL',
      [now(), m.user_id]);
  }
  return row;
}

module.exports = {
  membershipOf, requireMembership, assertCan, createShop, getShop, updateShop,
  members, memberCount, updateMember,
};
