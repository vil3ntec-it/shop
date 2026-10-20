'use strict';
/**
 * حسابِ مشتریِ ویترین (فاز ۴؛ ۲۳.۲–۲۳.۵، CHK-01/02/09، S134/S138/S140).
 * شرحِ جدول‌ها: ‎migrations/112_store_customers.sql‎.
 *
 *   ساختن/ورود ── فقط با بلیتِ تازهٔ «شماره تأیید شد» (کدِ یک‌بارمصرفِ ۲۳).
 *                 همان شماره ⇒ همان حساب؛ هیچ گذرواژه‌ای نیست.
 *   نشست ─────── `sc1.<۴۳ نویسهٔ base64url>` (۲۵۶ بیت)؛ در سرور فقط SHA-256.
 *                 انقضای مطلق (`STORE_CUSTOMER_SESSION_DAYS`، پیش‌فرض ۱۸۰ روز)،
 *                 لغو با «خروج»، «خروج از همه‌جا» یا پاک کردنِ حساب؛ حسابِ مسدود ⇒
 *                 نشستی کار نمی‌کند. دو تای آخر نسلِ بلیت‌های شماره را هم بالا می‌برند.
 *   نشانی ────── حداکثر ۱۰ نشانیِ نام‌دار؛ هر خواندن و نوشتن به شناسهٔ همین
 *                 مشتری بسته است (شناسهٔ نشانیِ دیگری ⇒ «پیدا نشد»).
 *
 * ⛔ این حساب از حسابِ دکان و کارمند جداست (۲۳.۴) و هیچ دسترسی‌ای به پنل نمی‌دهد.
 * ⛔ سبدِ مهمان در مرورگر است؛ ساختنِ حساب به آن دست نمی‌زند (S140).
 */
const crypto = require('crypto');
const db = require('../db');
const { one, many, now, tx } = db;
const config = require('../config');
const { ulid } = require('./ulid');
const { stripControl } = require('./validate');
const { badRequest, notFound, conflict, forbidden, ApiError } = require('../middleware/errors');

const MAX_ADDRESSES = 10;
const MAX_SESSIONS = 10;
const TOUCH_MS = 60 * 60 * 1000;
const TOKEN_RE = /^sc1\.[A-Za-z0-9_-]{43}$/;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const text = (v, max) => stripControl(str(v)).slice(0, max);
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const hashOf = (token) => crypto.createHash('sha256').update(`store-customer\n${token}`).digest('hex');
const sessionMs = () => Math.max(1, Number(config.storefront.customerSessionDays) || 180) * 24 * 60 * 60 * 1000;

function view(c) {
  return { id: c.id, phone: c.phone, name: c.name, createdAt: Number(c.created_at) };
}

/**
 * ورود یا ساختنِ حساب برای شمارهٔ تأییدشده ⇒ نشستِ تازه. نامِ آمده فقط وقتی
 * نشانده می‌شود که حساب هنوز نام ندارد (ورودِ دوباره نامِ قبلی را عوض نمی‌کند).
 */
async function signIn(phone, { name } = {}) {
  if (!/^\+[1-9]\d{7,14}$/.test(String(phone || ''))) throw badRequest('شماره معتبر نیست', 'bad_phone');
  const nm = text(name, 80);
  const token = `sc1.${crypto.randomBytes(32).toString('base64url')}`;
  return tx(async (c) => {
    const at = now();
    const cust = (await c.query(
      `INSERT INTO store_customers (id, phone, name, created_at, updated_at) VALUES ($1,$2,$3,$4,$4)
       ON CONFLICT (phone) DO UPDATE SET
         name = CASE WHEN store_customers.name = '' THEN EXCLUDED.name ELSE store_customers.name END,
         updated_at = EXCLUDED.updated_at
       RETURNING *`,
      [ulid(at), phone, nm, at]
    )).rows[0];
    if (cust.status !== 'active') throw forbidden('این حساب مسدود است؛ با دکان تماس بگیرید', 'customer_blocked');
    //  نشست‌های کهنه پاک و بیش از سقف، قدیمی‌ترین لغو
    await c.query('DELETE FROM store_customer_sessions WHERE customer_id = $1 AND (expires_at <= $2 OR revoked_at IS NOT NULL)',
      [cust.id, at]);
    await c.query(
      `UPDATE store_customer_sessions SET revoked_at = $2 WHERE id IN (
         SELECT id FROM store_customer_sessions WHERE customer_id = $1 AND revoked_at IS NULL
          ORDER BY created_at DESC, id DESC OFFSET $3)`,
      [cust.id, at, MAX_SESSIONS - 1]
    );
    const expiresAt = at + sessionMs();
    await c.query(
      `INSERT INTO store_customer_sessions (id, customer_id, token_hash, created_at, last_used_at, expires_at)
       VALUES ($1,$2,$3,$4,$4,$5)`,
      [ulid(at), cust.id, hashOf(token), at, expiresAt]
    );
    return { token, expiresAt, customer: view(cust) };
  });
}

/** مشتریِ این نشست — یا null (بی‌شکل، ناشناخته، منقضی، لغوشده یا مسدود). */
async function auth(token, at = now()) {
  const t = String(token || '');
  if (!TOKEN_RE.test(t)) return null;
  const r = await one(
    `SELECT s.id AS session_id, s.last_used_at, s.expires_at, s.revoked_at, c.*
       FROM store_customer_sessions s JOIN store_customers c ON c.id = s.customer_id
      WHERE s.token_hash = $1`,
    [hashOf(t)]
  );
  if (!r || r.revoked_at !== null || !(Number(r.expires_at) > at) || r.status !== 'active') return null;
  if (at - Number(r.last_used_at) > TOUCH_MS) {
    await db.query('UPDATE store_customer_sessions SET last_used_at = $2 WHERE id = $1', [r.session_id, at]);
  }
  return { sessionId: r.session_id, expiresAt: Number(r.expires_at), customer: r };
}

async function signOut(token) {
  const t = String(token || '');
  if (!TOKEN_RE.test(t)) return { signedOut: false };
  const r = await one('UPDATE store_customer_sessions SET revoked_at = $2 WHERE token_hash = $1 AND revoked_at IS NULL RETURNING id',
    [hashOf(t), now()]);
  return { signedOut: Boolean(r) };
}

async function rename(customer, body) {
  const name = text(obj(body).name, 80);
  const r = await one('UPDATE store_customers SET name = $2, updated_at = $3 WHERE id = $1 RETURNING *', [customer.id, name, now()]);
  return view(r);
}

// ── نسلِ بلیت‌های شماره (store_phone_revocations) ──────────────────

function phoneKey(phone) {
  const secret = config.secrets.api || config.secrets.jwt;
  if (!secret) throw new ApiError(503, 'storefront_unavailable', 'ویترین هنوز روی این سرور آماده نیست');
  const key = crypto.createHash('sha256').update(`store-phone-revocation\n${secret}`).digest();
  return crypto.createHmac('sha256', key).update(String(phone)).digest('hex');
}

/** نسلِ کنونیِ بلیت‌های این شماره (۰ = هرگز لغو نشده). */
async function ticketGen(phone, run = db.query) {
  const r = (await run('SELECT gen FROM store_phone_revocations WHERE phone_key = $1', [phoneKey(phone)])).rows[0];
  return r ? Number(r.gen) : 0;
}

/** همهٔ بلیت‌های پیشینِ این شماره بی‌اعتبار ⇒ نسلِ تازه. */
async function revokeTickets(phone, run = db.query) {
  const r = (await run(
    `INSERT INTO store_phone_revocations (phone_key, gen, updated_at) VALUES ($1, 1, $2)
     ON CONFLICT (phone_key) DO UPDATE SET gen = store_phone_revocations.gen + 1, updated_at = EXCLUDED.updated_at
     RETURNING gen`,
    [phoneKey(phone), now()]
  )).rows[0];
  return Number(r.gen);
}

/** «خروج از همه‌جا»: همهٔ نشست‌های این حساب و همهٔ بلیت‌های این شماره. */
async function signOutAll(customer) {
  return tx(async (c) => {
    const run = (sql, params) => c.query(sql, params);
    await revokeTickets(customer.phone, run);
    const r = await c.query(
      'UPDATE store_customer_sessions SET revoked_at = $2 WHERE customer_id = $1 AND revoked_at IS NULL RETURNING id',
      [customer.id, now()]
    );
    return { signedOut: r.rows.length, ticketsRevoked: true };
  });
}

/**
 * پاک کردنِ حساب: نشست‌ها و نشانی‌ها هم (cascade) و همهٔ بلیت‌های این شماره؛
 * سفارش‌ها سندِ دکان‌اند و می‌مانند.
 */
async function remove(customer) {
  return tx(async (c) => {
    await revokeTickets(customer.phone, (sql, params) => c.query(sql, params));
    await c.query('DELETE FROM store_customers WHERE id = $1', [customer.id]);
    return { deleted: true };
  });
}

// ── نشانی‌ها (CHK-09) ─────────────────────────────────────────────

function addressView(r) {
  return {
    id: r.id, label: r.label, text: r.text, landmark: r.landmark,
    lat: r.lat === null ? null : Number(r.lat), lng: r.lng === null ? null : Number(r.lng),
    updatedAt: Number(r.updated_at),
  };
}

function coord(v, min, max) {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  if (!Number.isFinite(n) || n < min || n > max) throw badRequest('مختصاتِ پین معتبر نیست', 'bad_address');
  return Math.round(n * 1e6) / 1e6;
}

function parseAddress(body) {
  const b = obj(body);
  const label = text(b.label, 40);
  if (!label) throw badRequest('نامی برای نشانی بنویسید (مثلاً «خانه»)', 'bad_address');
  const lat = coord(b.lat, -90, 90);
  const lng = coord(b.lng, -180, 180);
  if ((lat === null) !== (lng === null)) throw badRequest('پین ناقص است', 'bad_address');
  const out = { label, text: text(b.text, 300), landmark: text(b.landmark, 200), lat, lng };
  if (lat === null && !out.text && !out.landmark) throw badRequest('نشانی، نشانه یا پین لازم است', 'bad_address');
  return out;
}

async function addresses(customer) {
  return (await many('SELECT * FROM store_customer_addresses WHERE customer_id = $1 ORDER BY created_at, id', [customer.id]))
    .map(addressView);
}

async function addAddress(customer, body) {
  const a = parseAddress(body);
  return tx(async (c) => {
    await c.query('SELECT id FROM store_customers WHERE id = $1 FOR UPDATE', [customer.id]);
    const n = Number((await c.query('SELECT COUNT(*)::int AS n FROM store_customer_addresses WHERE customer_id = $1',
      [customer.id])).rows[0].n);
    if (n >= MAX_ADDRESSES) throw conflict(`حداکثر ${MAX_ADDRESSES} نشانی`, 'too_many_addresses');
    const at = now();
    const r = (await c.query(
      `INSERT INTO store_customer_addresses (id, customer_id, label, text, landmark, lat, lng, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$8) RETURNING *`,
      [ulid(at), customer.id, a.label, a.text, a.landmark, a.lat, a.lng, at]
    )).rows[0];
    return addressView(r);
  });
}

async function updateAddress(customer, id, body) {
  const a = parseAddress(body);
  const r = await one(
    `UPDATE store_customer_addresses SET label = $3, text = $4, landmark = $5, lat = $6, lng = $7, updated_at = $8
      WHERE id = $1 AND customer_id = $2 RETURNING *`,
    [String(id || ''), customer.id, a.label, a.text, a.landmark, a.lat, a.lng, now()]
  );
  if (!r) throw notFound('این نشانی پیدا نشد', 'address_not_found');
  return addressView(r);
}

async function removeAddress(customer, id) {
  const r = await one('DELETE FROM store_customer_addresses WHERE id = $1 AND customer_id = $2 RETURNING id',
    [String(id || ''), customer.id]);
  if (!r) throw notFound('این نشانی پیدا نشد', 'address_not_found');
  return { deleted: true };
}

module.exports = {
  MAX_ADDRESSES, MAX_SESSIONS, TOKEN_RE,
  view, signIn, auth, signOut, signOutAll, rename, remove, ticketGen, revokeTickets, addresses, addAddress, updateAddress, removeAddress,
};
