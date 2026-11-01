'use strict';
/**
 * هویتِ سرویس برای پلِ پنلِ خانگی — به‌جای رمزِ مشترکِ یک مدیرِ ارشد (یافتهٔ S4 ِ ممیزیِ پنلِ مرکزی).
 *
 *   پنل ──Authorization: Bearer vsk_…──────────────▶ /api/admin/*
 *        X-Actor-Id:   شناسهٔ کاربرِ پنل (اجباری)
 *        X-Actor-Role: superadmin | admin | finance | viewer (اجباری)
 *        X-Actor-Name: نامِ کاربر، percent-encoded (اختیاری)
 *
 * ⛔ نقشِ مؤثر = کم‌رتبه‌ترِ «نقشِ انجام‌دهنده» و «سقفِ کلید» (`role_cap`) — و مرزش را خودِ سرور
 *    می‌سنجد (`lib/admin-roles.js`)، نه پل. پلِ دزدیده‌شده از سقفِ کلیدش بالاتر نمی‌رود.
 * ⛔ هر ردیفِ سابقه‌ای که در این درخواست نوشته شود «انجام‌دهنده» را دارد (`detail.actor`) — از راهِ
 *    `AsyncLocalStorage`، پس هیچ‌کدام از صد و چند `audit.log` ِ درهای مدیر عوض نشد.
 * ⛔ کلید فقط هنگامِ ساختن یک بار برمی‌گردد؛ این‌جا فقط SHA-256 اش. هرگز در لاگ، نشانی یا پاسخ.
 * ⚠️ راهِ پیشین (ورودِ ارشد با رمز) دست نخورده — تا صاحبِ سامانه پل را عوض کند هر دو کار می‌کنند.
 */
const { AsyncLocalStorage } = require('node:async_hooks');
const { randomBytes, createHash } = require('crypto');
const { one, many, query, newId, now } = require('../db');
const { badRequest } = require('../middleware/errors');
const roles = require('./admin-roles');

const PREFIX = 'vsk_';
const ID_RE = /^[A-Za-z0-9_.:@-]{1,80}$/;
const NAME_MAX = 80;
/** «آخرین استفاده» را هر بار ننویس — یک دقیقه بس است. */
const TOUCH_MS = 60 * 1000;

const store = new AsyncLocalStorage();

const hashOf = (key) => createHash('sha256').update(String(key)).digest('hex');
const isServiceKey = (token) => typeof token === 'string' && token.startsWith(PREFIX) && token.length > PREFIX.length + 20;

function shape(r) {
  return {
    id: r.id, name: r.name, prefix: r.key_prefix, roleCap: r.role_cap, status: r.status,
    createdBy: r.created_by, createdAt: Number(r.created_at),
    lastUsedAt: r.last_used_at === null || r.last_used_at === undefined ? null : Number(r.last_used_at),
    revokedAt: r.revoked_at === null || r.revoked_at === undefined ? null : Number(r.revoked_at),
    revokedBy: r.revoked_by || null,
  };
}

/** ⇒ { key (فقط همین یک بار), row } */
async function create({ name, roleCap, createdBy }) {
  const key = `${PREFIX}${randomBytes(32).toString('base64url')}`;
  const row = await one(
    `INSERT INTO admin_service_keys (id, name, key_hash, key_prefix, role_cap, status, created_by, created_at)
     VALUES ($1,$2,$3,$4,$5,'active',$6,$7) RETURNING *`,
    [newId('svk'), name, hashOf(key), key.slice(0, PREFIX.length + 6), roleCap, createdBy, now()]
  );
  return { key, row: shape(row) };
}

async function list() {
  return (await many('SELECT * FROM admin_service_keys ORDER BY created_at DESC')).map(shape);
}

async function get(id) {
  const r = await one('SELECT * FROM admin_service_keys WHERE id=$1', [id]);
  return r ? shape(r) : null;
}

/** کلیدِ فعال، یا null. */
async function verify(key) {
  if (!isServiceKey(key)) return null;
  const r = await one(`SELECT * FROM admin_service_keys WHERE key_hash=$1 AND status='active'`, [hashOf(key)]);
  if (!r) return null;
  const t = now();
  if (!r.last_used_at || t - Number(r.last_used_at) > TOUCH_MS) {
    query('UPDATE admin_service_keys SET last_used_at=$2 WHERE id=$1', [r.id, t]).catch(() => {});
  }
  return r;
}

async function revoke(id, by) {
  const r = await one(
    `UPDATE admin_service_keys SET status='revoked', revoked_at=$2, revoked_by=$3
      WHERE id=$1 AND status='active' RETURNING *`,
    [id, now(), by]
  );
  return r ? shape(r) : null;
}

function header(req, name) {
  const v = req.headers[name];
  return typeof v === 'string' ? v.trim() : '';
}

/** «انجام‌دهنده» از سرآیندها — نبود یا نادرست ⇒ ۴۰۰ (هر درخواستِ کلیدِ سرویس باید بگوید کیست). */
function actorOf(req) {
  const id = header(req, 'x-actor-id');
  const role = header(req, 'x-actor-role').toLowerCase();
  if (!ID_RE.test(id) || !roles.ROLES.includes(role)) {
    throw badRequest('درخواستِ کلیدِ سرویس باید «انجام‌دهنده» (X-Actor-Id و X-Actor-Role) را بگوید', 'actor_required');
  }
  let name = '';
  const raw = header(req, 'x-actor-name');
  if (raw) {
    try { name = decodeURIComponent(raw); } catch { name = ''; }
    name = require('./validate').stripControl(name).trim().slice(0, NAME_MAX);
  }
  return { id, role, name };
}

/** اجرای ادامهٔ درخواست با «انجام‌دهنده» در بافت (برای `audit.log`). */
function run(actor, fn) { return store.run(actor, fn); }

/** «انجام‌دهنده» ِ درخواستِ جاری (فقط راهِ کلیدِ سرویس)، یا null. */
function currentActor() { return store.getStore() || null; }

/** برچسبِ کوتاه برای ستون‌هایی مثلِ `live_config.updated_by`. */
function label(req) {
  if (req.actor) return `${req.actor.name || req.actor.id} (${req.actor.kind}:${req.actor.id} via ${req.admin.id})`.slice(0, 80);
  return String(req.admin?.username || req.admin?.id || '');
}

/** انجام‌دهندهٔ کامل برای ستون‌های jsonb (حالت‌های دکان، تاریخچهٔ پرچم‌ها). */
function actorRecord(req) {
  if (req.actor) return { ...req.actor };
  return { kind: 'admin', id: req.admin.id, name: req.admin.name || req.admin.username || '', role: req.admin.role };
}

module.exports = {
  PREFIX, hashOf, isServiceKey, create, list, get, verify, revoke, actorOf, run, currentActor, label, actorRecord,
};
