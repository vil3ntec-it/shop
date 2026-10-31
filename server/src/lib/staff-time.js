'use strict';
/**
 * STF-07 (ورود و خروجِ کارمند) و STF-08 (نرخ‌های پورسانت) — سمتِ سرور.
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.staff_time` (live_config، دکان بر `all`). خاموش ⇒ فقط `/config`.
 * ⛔ قاعدهٔ زدن همان `core/staff/TimeClock.check` است و سرور مرجع است: زدنِ پیش از آخرین زدنِ
 *    همان عضو (`punch_before_last`) و دو زدنِ هم‌نوعِ پشتِ هم (`punch_same_kind`) ۴۰۹ می‌گیرند؛
 *    همان شناسه با همان محتوا دوباره‌فرستادنِ بی‌خطر است (صفِ آفلاینِ دستگاه).
 * ⛔ زمانِ زدن از دستگاه می‌آید (کارِ آفلاین)، ولی نه بیش از ۵ دقیقه جلوتر از ساعتِ سرور و نه
 *    کهنه‌تر از ۷ روز: ساعتِ دستگاه راهِ ساختنِ ساعتِ کارِ دلخواه نیست.
 * ⛔ هر عضو برای خودش می‌زند؛ زدن برای عضوِ دیگر (اصلاحِ «خروجِ جاافتاده») فقط صاحب و مدیر،
 *    و `by_member_id` ثبت می‌کند چه کسی زد. هم‌زمانی زیرِ قفلِ ردیفِ عضو است.
 * ⛔ نرخ‌ها به پایه‌امتیاز (۰ تا ۱۰٬۰۰۰، `Commission.Plan`)؛ نوشتن با `version` ِ فعلی (۴۰۹ کهنه).
 */
const { one, many, tx, now } = require('../db');
const { badRequest, conflict, forbidden, notFound } = require('../middleware/errors');
const orders = require('./orders');

const FLAG = 'shop.staff_time';
const FUTURE_SKEW_MS = 5 * 60 * 1000;
const MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_RANGE_MS = 62 * 24 * 60 * 60 * 1000;
const MAX_KEYS = 200;
const ID = /^[A-Za-z0-9_-]{8,64}$/;
const MANAGERS = ['owner', 'manager'];

const flagOf = (shopId) => orders.flagOf(shopId, FLAG);

function shapePunch(r) {
  return {
    id: r.id, memberId: r.member_id, kind: r.kind, at: Number(r.at),
    deviceUid: r.device_uid, byMemberId: r.by_member_id, name: r.name ?? undefined,
  };
}

/**
 * ثبتِ یک زدن. `actor` = عضوِ توکن ({ id, role }). ⇒ { punch, duplicate }.
 */
async function punch(shopId, actor, body = {}) {
  const id = typeof body.id === 'string' ? body.id : '';
  if (!ID.test(id)) throw badRequest('شناسهٔ زدن نادرست است', 'punch_invalid');
  const kind = body.kind;
  if (kind !== 'in' && kind !== 'out') throw badRequest('kind باید in یا out باشد', 'punch_invalid');
  const at = Number(body.at);
  if (!Number.isSafeInteger(at) || at <= 0) throw badRequest('زمانِ زدن نادرست است', 'punch_invalid');
  const t = now();
  if (at > t + FUTURE_SKEW_MS) throw badRequest('زمانِ زدن از ساعتِ سرور جلوتر است', 'punch_in_future');
  if (at < t - MAX_AGE_MS) throw badRequest('زدنِ کهنه‌تر از ۷ روز پذیرفته نمی‌شود', 'punch_too_old');
  const memberId = body.memberId ? String(body.memberId) : actor.id;
  if (memberId !== actor.id && !MANAGERS.includes(actor.role)) {
    throw forbidden('زدن برای عضوِ دیگر فقط با صاحب یا مدیر است', 'punch_other_forbidden');
  }
  const deviceUid = String(body.deviceUid || '').slice(0, 120);

  return tx(async (c) => {
    const m = (await c.query(
      `SELECT id FROM shop_members WHERE id=$1 AND shop_id=$2 AND status='active' FOR UPDATE`, [memberId, shopId],
    )).rows[0];
    if (!m) throw notFound('این عضو در دکان نیست', 'member_not_found');
    const same = (await c.query('SELECT * FROM staff_punches WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
    if (same) {
      if (same.member_id === memberId && same.kind === kind && Number(same.at) === at) return { punch: shapePunch(same), duplicate: true };
      throw conflict('این شناسه پیش‌تر برای زدنِ دیگری به کار رفته است', 'punch_id_conflict');
    }
    const last = (await c.query(
      'SELECT kind, at FROM staff_punches WHERE shop_id=$1 AND member_id=$2 ORDER BY at DESC, id DESC LIMIT 1', [shopId, memberId],
    )).rows[0];
    if (last && at < Number(last.at)) throw conflict('زدن پیش از آخرین زدنِ همین عضو است', 'punch_before_last');
    if (last && last.kind === kind) throw conflict(kind === 'in' ? 'این عضو همین حالا سرِ کار است' : 'این عضو همین حالا بیرون است', 'punch_same_kind');
    const row = (await c.query(
      `INSERT INTO staff_punches (shop_id, id, member_id, kind, at, device_uid, by_member_id, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [shopId, id, memberId, kind, at, deviceUid, actor.id, t],
    )).rows[0];
    return { punch: shapePunch(row), duplicate: false };
  });
}

/**
 * زدن‌های یک بازه. شاگرد فقط زدن‌های خودش را می‌بیند؛ صاحب و مدیر همه را (یا یک عضو).
 * بازه حداکثر ۶۲ روز است.
 */
async function list(shopId, actor, { from, to, memberId } = {}) {
  const t = now();
  const end = to === undefined || to === '' ? t : Number(to);
  const start = from === undefined || from === '' ? end - 7 * 24 * 60 * 60 * 1000 : Number(from);
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end) throw badRequest('بازه نادرست است', 'range_invalid');
  if (end - start > MAX_RANGE_MS) throw badRequest('بازه بیش از ۶۲ روز است', 'range_invalid');
  const manager = MANAGERS.includes(actor.role);
  const who = manager ? (memberId ? String(memberId) : null) : actor.id;
  const rows = await many(
    `SELECT p.*, COALESCE(NULLIF(u.name,''), u.phone, '') AS name
       FROM staff_punches p
       LEFT JOIN shop_members m ON m.id = p.member_id AND m.shop_id = p.shop_id
       LEFT JOIN users u ON u.id = m.user_id
      WHERE p.shop_id=$1 AND p.at BETWEEN $2 AND $3 AND ($4::text IS NULL OR p.member_id=$4)
      ORDER BY p.at, p.id LIMIT 5000`,
    [shopId, start, end, who],
  );
  return { from: start, to: end, serverTime: t, punches: rows.map(shapePunch) };
}

function cleanRates(v, field) {
  if (v === undefined || v === null) return {};
  if (typeof v !== 'object' || Array.isArray(v)) throw badRequest(`${field} باید شیء باشد`, 'commission_invalid');
  const keys = Object.keys(v);
  if (keys.length > MAX_KEYS) throw badRequest(`${field}: بیش از ${MAX_KEYS} نرخ`, 'commission_invalid');
  const out = {};
  for (const k of keys) {
    const key = k.trim();
    if (!key || key.length > 80) throw badRequest(`${field}: نامِ «${k.slice(0, 20)}» نادرست است`, 'commission_invalid');
    out[key] = bp(v[k], `${field}.${key}`);
  }
  return out;
}

function bp(v, field) {
  if (!Number.isInteger(v) || v < 0 || v > 10000) throw badRequest(`${field} باید عددِ صحیحِ ۰ تا ۱۰٬۰۰۰ باشد`, 'commission_invalid');
  return v;
}

function shapePlan(r) {
  if (!r) return { defaultBp: 0, sellerBp: {}, categoryBp: {}, version: 0, updatedAt: 0 };
  const obj = (x) => (typeof x === 'string' ? JSON.parse(x) : x || {});
  return { defaultBp: r.default_bp, sellerBp: obj(r.seller_bp), categoryBp: obj(r.category_bp), version: r.version, updatedAt: Number(r.updated_at) };
}

async function plan(shopId) {
  return shapePlan(await one('SELECT * FROM shop_commission_plans WHERE shop_id=$1', [shopId]));
}

async function savePlan(shopId, actor, body = {}) {
  const defaultBp = bp(body.defaultBp, 'defaultBp');
  const sellerBp = cleanRates(body.sellerBp, 'sellerBp');
  const categoryBp = cleanRates(body.categoryBp, 'categoryBp');
  const version = body.version;
  if (!Number.isInteger(version) || version < 0) throw badRequest('version (نسخهٔ فعلی) لازم است', 'commission_invalid');
  return tx(async (c) => {
    const cur = (await c.query('SELECT version FROM shop_commission_plans WHERE shop_id=$1 FOR UPDATE', [shopId])).rows[0];
    if ((cur ? cur.version : 0) !== version) throw conflict('نرخ‌ها در این میان عوض شده‌اند؛ دوباره بخوانید', 'commission_version');
    const row = (await c.query(
      `INSERT INTO shop_commission_plans (shop_id, default_bp, seller_bp, category_bp, version, updated_by, updated_at)
       VALUES ($1,$2,$3::jsonb,$4::jsonb,$5,$6,$7)
       ON CONFLICT (shop_id) DO UPDATE SET default_bp=excluded.default_bp, seller_bp=excluded.seller_bp,
         category_bp=excluded.category_bp, version=excluded.version, updated_by=excluded.updated_by, updated_at=excluded.updated_at
       RETURNING *`,
      [shopId, defaultBp, JSON.stringify(sellerBp), JSON.stringify(categoryBp), version + 1, actor.id, now()],
    )).rows[0];
    return shapePlan(row);
  });
}

module.exports = { FLAG, flagOf, punch, list, plan, savePlan, FUTURE_SKEW_MS, MAX_AGE_MS };
