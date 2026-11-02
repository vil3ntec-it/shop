'use strict';
/**
 * STF-06 — دسترسیِ کارمند محدود به شعبه یا انبار (v3 §۶.۹).
 *
 * دادهٔ امروز شعبه ندارد (`branch_id` همیشه '')؛ «مکان»‌های واقعیِ موجودیِ سرور همان دو سرِ انتقالِ
 * دومرحله‌ای‌اند (`stock-transfers.ENDPOINTS`): قفسهٔ فروشگاه (`shop_floor`) و انبار (`warehouse`).
 * «خراب» و «در مسیر» انبارِ مشترکِ همهٔ دکان‌اند، نه یک مکان؛ کاری که فقط به آن‌ها می‌رسد مکانِ لنگر ندارد.
 *
 * قاعده (سرور مرجع است؛ برنامه فقط پنهان می‌کند):
 *   ⛔ پرچمِ `shop.staff_scope` (live_config، پیش‌فرض خاموش) خاموش ⇒ هیچ سنجشی و هیچ خواندنی از
 *      `shop_member_scopes`؛ مجوزها و پاسخ‌ها بایت‌به‌بایت همان امروز.
 *   ⛔ مالک هرگز محدود نیست. فهرستِ خالی (یا همهٔ مکان‌ها) = بی‌محدودیت = همان امروز.
 *   ⛔ کارِ عضوِ محدود: همهٔ مکان‌هایی که کار نام می‌برد در محدوده و دست‌کم یکی ⇒ وگرنه ۴۰۳ `location_out_of_scope`.
 *   ⛔ فهرست‌ها فقط مکان‌های محدوده را نشان می‌دهند؛ گزارشِ تطبیق و تنظیمِ موجودی (همهٔ مکان‌ها) بسته است.
 *   ⛔ محدوده در هر درخواست از نو خوانده می‌شود ⇒ تغییرِ صاحب از درخواستِ بعد اثر دارد.
 */
const { one, many, query, now } = require('../db');
const { flagOf } = require('./orders');
const stock = require('./stock');
const { ENDPOINTS } = require('./stock-transfers');
const { badRequest, forbidden, notFound } = require('../middleware/errors');

const FLAG = 'shop.staff_scope';
const PLACES = ENDPOINTS;
const OUT = 'location_out_of_scope';

function decode(v) {
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return null; }
}
/** فقط مکان‌های شناخته، به ترتیبِ `PLACES` */
function placesOf(v) {
  const a = decode(v);
  return Array.isArray(a) ? PLACES.filter((p) => a.includes(p)) : [];
}

const enabled = (shopId) => flagOf(shopId, FLAG);

/** مکان‌های مجازِ یک عضو؛ `null` = بی‌محدودیت (پرچم خاموش، مالک، فهرستِ خالی یا همهٔ مکان‌ها) */
async function scopeOf(shopId, member) {
  if (!shopId || !member || member.role === 'owner') return null;
  if (!(await enabled(shopId))) return null;
  const r = await one('SELECT places FROM shop_member_scopes WHERE shop_id=$1 AND user_id=$2', [shopId, String(member.user_id || '')]);
  const places = placesOf(r && r.places);
  return places.length && places.length < PLACES.length ? places : null;
}

/** یک بار در هر درخواست، نه بیشتر */
async function scopeOfReq(req) {
  if (req._staffScope === undefined) req._staffScope = await scopeOf(req.shopId, req.member);
  return req._staffScope;
}

/** آیا کاری که این مکان‌ها را نام می‌برد در محدوده است */
function allows(scope, named) {
  if (!scope) return true;
  const places = named.filter((l) => PLACES.includes(l));
  return places.length > 0 && places.every((p) => scope.includes(p));
}

function check(scope, named) {
  if (!allows(scope, named)) throw forbidden('این مکانِ موجودی در محدودهٔ دسترسیِ شما نیست', OUT);
}

/** کاری که همهٔ مکان‌ها را در بر می‌گیرد (گزارشِ تطبیق، تنظیمِ موجودی) */
function requireAll(scope) {
  if (scope) throw forbidden('این کار همهٔ مکان‌های موجودی را در بر می‌گیرد و برای دسترسیِ محدود باز نیست', OUT);
}

/** مکانِ بدنه فقط اگر معتبر باشد سنجیده می‌شود؛ نامعتبر همان ۴۰۰ ِ امروز را از تابعِ اصلی می‌گیرد */
function checkLocation(scope, raw) {
  const l = typeof raw === 'string' ? raw.trim() : '';
  if (scope && stock.LOCATIONS.includes(l)) check(scope, [l]);
}

/** مکان‌هایی که حرکتِ دستی نام می‌برد — خروجیِ `catalog.parseManual` (همان پیش‌فرض‌ها) */
function manualPlaces(m) {
  if (m.kind === 'transfer') return [m.from, m.to];
  if (m.kind === 'damage') return [m.from];
  return [m.location];
}

/** وظیفه: مکانِ خودش؛ کسریِ انتقال (`in_transit`) از آنِ مقصد است — همان شرطِ `catalog.tasks` */
function taskPlaces(t) {
  const d = decode(t.detail) || {};
  return t.kind === 'transfer_short' ? [t.location, d.to] : [t.location];
}

async function taskOf(shopId, id) {
  return one('SELECT kind, location, detail FROM stock_tasks WHERE shop_id=$1 AND id=$2', [shopId, String(id || '')]);
}

async function transferTo(shopId, id) {
  const r = await one('SELECT to_location FROM stock_transfers WHERE shop_id=$1 AND id=$2', [shopId, String(id || '')]);
  return r ? r.to_location : null;
}

/** نمای کالا فقط با مانده‌های محدوده؛ «قابلِ فروش» و نشان همان کلِ دکان می‌ماند (فروش به آن نیاز دارد) */
function trimProduct(p, scope) {
  if (!scope || !p || !p.stock) return p;
  const levels = {};
  for (const l of scope) if (l in p.stock.levels) levels[l] = p.stock.levels[l];
  return { ...p, stock: { ...p.stock, levels } };
}

function trimLows(lows, scope) {
  if (!scope) return lows;
  const out = {};
  for (const l of scope) if (l in lows) out[l] = lows[l];
  return out;
}

// ── سمتِ صاحب ────────────────────────────────────────────────────────

/** همهٔ اعضای غیرِ مالک با محدوده‌شان (`[]` = همه) */
async function members(shopId) {
  const rows = await many(
    `SELECT m.id, m.user_id, m.role, m.status, u.name, s.places
       FROM shop_members m
       JOIN users u ON u.id = m.user_id
       LEFT JOIN shop_member_scopes s ON s.shop_id = m.shop_id AND s.user_id = m.user_id
      WHERE m.shop_id=$1 AND m.status <> 'removed' AND m.role <> 'owner'
      ORDER BY m.created_at ASC, m.id ASC`,
    [shopId]
  );
  return rows.map((r) => ({ id: r.id, userId: r.user_id, name: r.name || '', role: r.role, status: r.status, places: placesOf(r.places) }));
}

/** محدودهٔ یک عضو؛ خالی یا همهٔ مکان‌ها ⇒ ردیف پاک (= همه) */
async function setMember(shopId, idOrUserId, raw, byUserId) {
  const m = await one(
    `SELECT id, user_id, role FROM shop_members WHERE shop_id=$2 AND (id=$1 OR user_id=$1) AND status <> 'removed'`,
    [String(idOrUserId || ''), shopId]
  );
  if (!m) throw notFound('این عضو در دکان شما نیست', 'member_not_found');
  if (m.role === 'owner') throw badRequest('مالک همیشه به همهٔ مکان‌ها دسترسی دارد', 'owner_fixed');
  if (!Array.isArray(raw) || raw.length > 20) throw badRequest('فهرستِ مکان‌ها معتبر نیست', 'bad_places');
  for (const p of raw) if (!PLACES.includes(p)) throw badRequest('مکانِ موجودی معتبر نیست', 'bad_location');
  const places = PLACES.filter((p) => raw.includes(p));
  if (!places.length || places.length === PLACES.length) {
    await query('DELETE FROM shop_member_scopes WHERE shop_id=$1 AND user_id=$2', [shopId, m.user_id]);
    return { member: m, places: [] };
  }
  await query(
    `INSERT INTO shop_member_scopes (shop_id, user_id, places, updated_by, updated_at)
     VALUES ($1,$2,$3::jsonb,$4,$5)
     ON CONFLICT (shop_id, user_id) DO UPDATE SET places=excluded.places, updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
    [shopId, m.user_id, JSON.stringify(places), String(byUserId || ''), now()]
  );
  return { member: m, places };
}

module.exports = {
  FLAG, PLACES, OUT,
  enabled, scopeOf, scopeOfReq, allows, check, requireAll, checkLocation, manualPlaces, taskPlaces,
  taskOf, transferTo, trimProduct, trimLows, members, setMember,
};
