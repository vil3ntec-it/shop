'use strict';
/**
 * ⛔ STF-01 — نقشِ سفارشی (بخشِ ۱۰.۱ «مالک می‌تواند نقش سفارشی بسازد»؛ پرچمِ خاموشِ `shop.custom_roles`).
 *
 * نقشِ سفارشی = **نام** + یکی از نقش‌های ثابت (`base_role`؛ نه صاحب، نه پیک) + یک لایهٔ مجوز با همان قالبِ
 * `perm-core` (حالت و سقف). هیچ قدرتِ تازه‌ای نمی‌سازد:
 *   • عضوِ دارای نقشِ سفارشی در `shop_members.role` همان نقشِ پایه را دارد، پس `can(role)` ِ امروز، جدول‌های
 *     نوشتنی (`canWrite`)، قاعدهٔ ردیف و هر درِ REST دقیقاً همان نقشِ پایه است؛
 *   • لایهٔ نقشِ سفارشی میانِ لایهٔ نقش در این دکان و لایهٔ همان کاربر می‌نشیند (`staff-perms.layersFor`:
 *     [نقش، نقشِ سفارشی، کاربر]) و مثلِ هر لایه از `cleanLayer` می‌گذرد ⇒ هرگز گشادتر از پیش‌فرضِ نقشِ پایه نیست؛
 *   • همهٔ سنجش‌های امروز (تخفیف، لغو، برگشت، قرض، گاوصندوق، `requirePermission` ِ کلیدهای لایه‌پذیر، شماره‌ی
 *     مشتری، «درخواستِ تأیید») همان `layersFor` را می‌خوانند ⇒ اجرا همان راه‌های امروز است، نه راهِ تازه.
 * دادنِ نقشِ سفارشی به عضو نقشِ او را همان نقشِ پایه می‌کند (`shops.updateMember`، همان درِ «تغییرِ نقش» با
 * خبرِ سوکتِ زنده). اگر بعد نقشِ عضو جای دیگر عوض شود، نقشِ سفارشی بی‌اثر می‌ماند (`base_role` باید برابر باشد).
 * پاک کردنِ نقشِ سفارشی عضوها را روی همان نقشِ پایه با لایه‌های امروزشان می‌گذارد.
 *
 * پرچمِ خاموش (پیش‌فرض) ⇒ درها جز `/config` ۴۰۳ و هیچ لایهٔ سفارشی‌ای اثر ندارد (`layersFor` پرچم را فقط وقتی
 * می‌خواند که عضو نقشِ سفارشی دارد) ⇒ نقش‌های پیش‌فرض بایت‌به‌بایت همان امروز.
 */
const core = require('./perm-core');
const { one, many, query, tx, newId, now } = require('../db');
const { badRequest, conflict, notFound } = require('../middleware/errors');

const FLAG = 'shop.custom_roles';
const MAX_ROLES = 30;
const NAME_MAX = 40;
const LINE_BREAK = /[\r\n\t]/;
/** نقش‌های پایه: همهٔ نقش‌های هسته جز صاحب (پیک در هسته نیست و لایه ندارد) */
const BASE_ROLES = Object.freeze(core.ROLES.filter((r) => r !== 'owner'));

const enabled = (shopId, run = null) => require('./orders').flagOf(shopId, FLAG, run);

function parsePerms(v) {
  if (!v) return {};
  if (typeof v === 'string') { try { return JSON.parse(v) || {}; } catch { return {}; } }
  return typeof v === 'object' ? v : {};
}

function cleanName(v) {
  const s = typeof v === 'string' ? v.trim().replace(/\s+/g, ' ') : '';
  if (!s || s.length > NAME_MAX || LINE_BREAK.test(s)) throw badRequest(`نامِ نقش لازم است (حداکثر ${NAME_MAX} نویسه)`, 'bad_role_name');
  return s;
}

/** همان سنجشِ لایهٔ نقش (`cleanLayer`) برای نقشِ پایه، و کلیدِ پرچم‌دار فقط با پرچمِ خودش */
async function cleanPerms(shopId, base, perms) {
  const staffPerms = require('./staff-perms');
  const layer = staffPerms.cleanLayer(base, perms === undefined ? {} : perms);
  await staffPerms.requireEnforced(shopId, layer);
  return layer;
}

function shape(r, members = []) {
  return {
    id: r.id, name: r.name, baseRole: r.base_role, perms: parsePerms(r.perms),
    members, updatedAt: Number(r.updated_at),
  };
}

/** همهٔ نقش‌های سفارشیِ دکان، با اعضای فعالی که نقشِ پایه‌شان هنوز همان است */
async function list(shopId) {
  const roles = await many('SELECT * FROM shop_custom_roles WHERE shop_id=$1 ORDER BY created_at ASC, id ASC', [shopId]);
  if (!roles.length) return [];
  const links = await many(
    `SELECT a.role_id, a.user_id FROM shop_member_custom_roles a
       JOIN shop_custom_roles c ON c.id = a.role_id AND c.shop_id = a.shop_id
       JOIN shop_members m ON m.shop_id = a.shop_id AND m.user_id = a.user_id AND m.status <> 'removed' AND m.role = c.base_role
      WHERE a.shop_id=$1`, [shopId]
  );
  return roles.map((r) => shape(r, links.filter((l) => l.role_id === r.id).map((l) => l.user_id)));
}

async function find(shopId, id, run = null) {
  const sql = 'SELECT * FROM shop_custom_roles WHERE id=$1 AND shop_id=$2';
  const r = run ? (await run(sql, [String(id || ''), shopId])).rows[0] : await one(sql, [String(id || ''), shopId]);
  if (!r) throw notFound('این نقش در دکانِ شما نیست', 'custom_role_not_found');
  return r;
}

const taken = (err) => err && (err.code === '23505' || /unique|duplicate/i.test(String(err.message || '')));

async function create(shopId, body, byUserId) {
  const b = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const name = cleanName(b.name);
  const base = typeof b.baseRole === 'string' ? b.baseRole : '';
  if (!BASE_ROLES.includes(base)) throw badRequest('نقشِ پایه باید یکی از نقش‌های کارمند باشد', 'unknown_role');
  const perms = await cleanPerms(shopId, base, b.perms);
  const count = Number((await one('SELECT COUNT(*)::int AS n FROM shop_custom_roles WHERE shop_id=$1', [shopId])).n);
  if (count >= MAX_ROLES) throw conflict(`هر دکان حداکثر ${MAX_ROLES} نقشِ سفارشی دارد`, 'too_many_roles');
  const t = now();
  try {
    const r = await one(
      `INSERT INTO shop_custom_roles (id, shop_id, name, base_role, perms, created_by, created_at, updated_by, updated_at)
       VALUES ($1,$2,$3,$4,$5::jsonb,$6,$7,$6,$7) RETURNING *`,
      [newId('crl'), shopId, name, base, JSON.stringify(perms), String(byUserId || ''), t]
    );
    return shape(r);
  } catch (err) {
    if (taken(err)) throw conflict('نقشی با همین نام هست', 'role_name_taken');
    throw err;
  }
}

/** نام و/یا لایه؛ نقشِ پایه عوض نمی‌شود (نقشِ تازه بسازید) */
async function update(shopId, id, body, byUserId) {
  const b = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
  const r = await find(shopId, id);
  if (b.baseRole !== undefined && b.baseRole !== r.base_role) throw badRequest('نقشِ پایه عوض نمی‌شود؛ نقشِ تازه بسازید', 'base_role_fixed');
  const name = b.name === undefined ? r.name : cleanName(b.name);
  const perms = b.perms === undefined ? parsePerms(r.perms) : await cleanPerms(shopId, r.base_role, b.perms);
  try {
    const row = await one(
      `UPDATE shop_custom_roles SET name=$3, perms=$4::jsonb, updated_by=$5, updated_at=$6 WHERE id=$1 AND shop_id=$2 RETURNING *`,
      [r.id, shopId, name, JSON.stringify(perms), String(byUserId || ''), now()]
    );
    return shape(row);
  } catch (err) {
    if (taken(err)) throw conflict('نقشی با همین نام هست', 'role_name_taken');
    throw err;
  }
}

/** پاک کردن؛ عضوها روی همان نقشِ پایه می‌مانند (ردیف‌های پیوند با FK پاک می‌شوند) */
async function remove(shopId, id) {
  const r = await find(shopId, id);
  await tx(async (c) => {
    await c.query('DELETE FROM shop_member_custom_roles WHERE shop_id=$1 AND role_id=$2', [shopId, r.id]);
    await c.query('DELETE FROM shop_custom_roles WHERE id=$1 AND shop_id=$2', [r.id, shopId]);
  });
  return { id: r.id, name: r.name };
}

/**
 * دادن یا برداشتنِ نقشِ سفارشیِ یک عضو (شناسهٔ عضویت یا کاربر). `roleId` = null ⇒ برداشتن (عضو روی نقشِ پایه
 * می‌ماند). نقشِ عضو اگر با نقشِ پایه فرق دارد، با همان `shops.updateMember` ِ «تغییرِ نقش» عوض می‌شود.
 */
async function assign(shopId, idOrUserId, roleId, byUserId) {
  const staffPerms = require('./staff-perms');
  const m = await staffPerms.editableMember(shopId, idOrUserId);
  if (roleId === null || roleId === undefined || roleId === '') {
    await query('DELETE FROM shop_member_custom_roles WHERE shop_id=$1 AND user_id=$2', [shopId, m.user_id]);
    await require('./sync-v1-live').accessChanged({ userId: m.user_id }).catch(() => {});
    return { member: m, role: null, baseRole: m.role };
  }
  if (typeof roleId !== 'string') throw badRequest('شناسهٔ نقش نادرست است', 'bad_role_id');
  const r = await find(shopId, roleId);
  if (!core.ROLES.includes(m.role)) throw badRequest('این عضو لایهٔ مجوز ندارد', 'unknown_role');
  if (m.role !== r.base_role) await require('./shops').updateMember(shopId, m.id, { role: r.base_role });
  await query(
    `INSERT INTO shop_member_custom_roles (shop_id, user_id, role_id, assigned_by, assigned_at) VALUES ($1,$2,$3,$4,$5)
     ON CONFLICT (shop_id, user_id) DO UPDATE SET role_id=excluded.role_id, assigned_by=excluded.assigned_by, assigned_at=excluded.assigned_at`,
    [shopId, m.user_id, r.id, String(byUserId || ''), now()]
  );
  await require('./sync-v1-live').accessChanged({ userId: m.user_id }).catch(() => {});
  return { member: { ...m, role: r.base_role }, role: shape(r), baseRole: r.base_role };
}

/** نقشِ سفارشیِ مؤثرِ هر عضو (فقط با پرچم و نقشِ پایهٔ برابر) — userId ⇒ {id, name, perms} */
async function effectiveByUser(shopId) {
  if (!(await enabled(shopId))) return null;
  const rows = await many(
    `SELECT a.user_id, c.id, c.name, c.perms FROM shop_member_custom_roles a
       JOIN shop_custom_roles c ON c.id = a.role_id AND c.shop_id = a.shop_id
       JOIN shop_members m ON m.shop_id = a.shop_id AND m.user_id = a.user_id AND m.role = c.base_role
      WHERE a.shop_id=$1`, [shopId]
  );
  return new Map(rows.map((r) => [r.user_id, { id: r.id, name: r.name, perms: parsePerms(r.perms) }]));
}

module.exports = { FLAG, MAX_ROLES, NAME_MAX, BASE_ROLES, enabled, list, find, create, update, remove, assign, effectiveByUser, parsePerms };
