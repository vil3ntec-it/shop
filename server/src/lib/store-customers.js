'use strict';
/**
 * حسابِ مشتریِ ویترین (فاز ۴؛ ۲۳.۲–۲۳.۵، CHK-01/02/09، S134/S138/S140).
 * شرحِ جدول‌ها: ‎migrations/112_store_customers.sql‎ و ‎881_store_email_identity.sql‎.
 *
 *   ساختن/ورود ── فقط با بلیتِ تازهٔ «ایمیل تأیید شد» (کدِ یک‌بارمصرفِ ۲۳ به ایمیل؛ «فقط ایمیل»، ۱۴۰۵/۰۸/۲۳).
 *                 همان ایمیل ⇒ همان حساب؛ هیچ گذرواژه‌ای نیست. سفارش‌های مهمانِ همان ایمیل به حساب گره می‌خورند.
 *   شماره ────── فقط «شمارهٔ تماس برای پیک»: بی کد، فقط شکل (`validate.phone`)، نه یکتا (`setContactPhone`).
 *   نشست ─────── `sc1.<۴۳ نویسهٔ base64url>` (۲۵۶ بیت)؛ در سرور فقط SHA-256.
 *                 انقضای مطلق (`STORE_CUSTOMER_SESSION_DAYS`، پیش‌فرض ۱۸۰ روز)،
 *                 لغو با «خروج»، «خروج از همه‌جا»، تغییرِ ایمیل یا پاک کردنِ حساب؛ حسابِ مسدود ⇒
 *                 نشستی کار نمی‌کند. «خروج از همه‌جا»، تغییرِ ایمیل و پاک کردن نسلِ بلیت‌های ایمیل را هم بالا می‌برند.
 *   حسابِ کهنه ─ فقط‌شماره (پیش از ۸۸۱، email NULL): داده می‌ماند؛ نشستِ زنده‌اش با کدِ ایمیلِ تازه یک بار ایمیل را
 *                 وصل می‌کند (`changeEmail`) و سفارش‌های ویترینیِ همان شمارهٔ تأییدشدهٔ پیشین به حساب می‌آیند.
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
const { stripControl, phoneForms } = require('./validate');
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
  return { id: c.id, email: c.email || null, phone: c.phone || null, name: c.name, createdAt: Number(c.created_at) };
}

const EMAIL_RE = /^[^\s@A-Z]+@[^\s@A-Z]+$/;
const assertEmail = (email) => {
  if (!EMAIL_RE.test(String(email || '')) || String(email).length > 190) throw badRequest('ایمیل معتبر نیست', 'bad_email');
};

/** حسابِ همین ایمیل — یا null. */
async function byEmail(email, run = db.query) {
  return (await run('SELECT * FROM store_customers WHERE email = $1', [String(email || '')])).rows[0] || null;
}

/**
 * سفارش‌های ویترینیِ بی‌حساب به حساب گره می‌خورند (فقط پیوند؛ سندِ دکان دست نمی‌خورد): همان ایمیل، و برای وصلِ
 * حسابِ کهنهٔ فقط‌شماره، سفارش‌های همان شمارهٔ تأییدشدهٔ پیشین (هر شکلِ آن، `phoneForms`) که ایمیل نداشتند.
 */
async function adoptOrders(c, customerId, { email = '', legacyPhone = '' } = {}) {
  let n = 0;
  if (email) {
    n += (await c.query(
      `UPDATE online_orders SET store_customer_id = $1
        WHERE source = 'storefront' AND customer_email = $2 AND store_customer_id IS NULL`, [customerId, email]
    )).rowCount;
  }
  if (legacyPhone) {
    n += (await c.query(
      `UPDATE online_orders SET store_customer_id = $1
        WHERE source = 'storefront' AND customer_email = '' AND store_customer_id IS NULL AND customer_phone = ANY($2::text[])`,
      [customerId, phoneForms(legacyPhone)]
    )).rowCount;
  }
  return n;
}

/**
 * ورود یا ساختنِ حساب برای ایمیلِ تأییدشده ⇒ نشستِ تازه. نامِ آمده فقط وقتی
 * نشانده می‌شود که حساب هنوز نام ندارد (ورودِ دوباره نامِ قبلی را عوض نمی‌کند).
 */
async function signIn(email, { name } = {}) {
  assertEmail(email);
  const nm = text(name, 80);
  const token = `sc1.${crypto.randomBytes(32).toString('base64url')}`;
  return tx(async (c) => {
    const at = now();
    const cust = (await c.query(
      `INSERT INTO store_customers (id, email, name, created_at, updated_at) VALUES ($1,$2,$3,$4,$4)
       ON CONFLICT (email) WHERE email IS NOT NULL DO UPDATE SET
         name = CASE WHEN store_customers.name = '' THEN EXCLUDED.name ELSE store_customers.name END,
         updated_at = EXCLUDED.updated_at
       RETURNING *`,
      [ulid(at), email, nm, at]
    )).rows[0];
    if (cust.status !== 'active') throw forbidden('این حساب مسدود است؛ با دکان تماس بگیرید', 'customer_blocked');
    await adoptOrders(c, cust.id, { email });
    return { ...(await openSession(c, cust.id, token, at)), customer: view(cust) };
  });
}

/** نشستِ تازه برای حساب (درونِ تراکنش): کهنه‌ها پاک و بیش از سقف، قدیمی‌ترین لغو. */
async function openSession(c, customerId, token, at) {
  await c.query('DELETE FROM store_customer_sessions WHERE customer_id = $1 AND (expires_at <= $2 OR revoked_at IS NOT NULL)',
    [customerId, at]);
  await c.query(
    `UPDATE store_customer_sessions SET revoked_at = $2 WHERE id IN (
       SELECT id FROM store_customer_sessions WHERE customer_id = $1 AND revoked_at IS NULL
        ORDER BY created_at DESC, id DESC OFFSET $3)`,
    [customerId, at, MAX_SESSIONS - 1]
  );
  const expiresAt = at + sessionMs();
  await c.query(
    `INSERT INTO store_customer_sessions (id, customer_id, token_hash, created_at, last_used_at, expires_at)
     VALUES ($1,$2,$3,$4,$4,$5)`,
    [ulid(at), customerId, hashOf(token), at, expiresAt]
  );
  return { token, expiresAt };
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

// ── نسلِ بلیت‌های ایمیل (store_phone_revocations) ──────────────────
//  همان جدولِ نسلِ شماره‌های پیشین با دامنهٔ HMAC ِ جدا (`email\n…`)؛ کلید HMAC است، نه خودِ ایمیل — پس از پاک کردنِ
//  حساب ایمیلِ خام نمی‌ماند.

function identityKey(email) {
  const secret = config.secrets.api || config.secrets.jwt;
  if (!secret) throw new ApiError(503, 'storefront_unavailable', 'ویترین هنوز روی این سرور آماده نیست');
  const key = crypto.createHash('sha256').update(`store-phone-revocation\n${secret}`).digest();
  return crypto.createHmac('sha256', key).update(`email\n${String(email)}`).digest('hex');
}

/** نسلِ کنونیِ بلیت‌های این ایمیل (۰ = هرگز لغو نشده). */
async function ticketGen(email, run = db.query) {
  const r = (await run('SELECT gen FROM store_phone_revocations WHERE phone_key = $1', [identityKey(email)])).rows[0];
  return r ? Number(r.gen) : 0;
}

/** همهٔ بلیت‌های پیشینِ این ایمیل بی‌اعتبار ⇒ نسلِ تازه. */
async function revokeTickets(email, run = db.query) {
  if (!email) return 0;
  const r = (await run(
    `INSERT INTO store_phone_revocations (phone_key, gen, updated_at) VALUES ($1, 1, $2)
     ON CONFLICT (phone_key) DO UPDATE SET gen = store_phone_revocations.gen + 1, updated_at = EXCLUDED.updated_at
     RETURNING gen`,
    [identityKey(email), now()]
  )).rows[0];
  return Number(r.gen);
}

/** «خروج از همه‌جا»: همهٔ نشست‌های این حساب و همهٔ بلیت‌های ایمیلش. */
async function signOutAll(customer) {
  return tx(async (c) => {
    const run = (sql, params) => c.query(sql, params);
    await revokeTickets(customer.email, run);
    const r = await c.query(
      'UPDATE store_customer_sessions SET revoked_at = $2 WHERE customer_id = $1 AND revoked_at IS NULL RETURNING id',
      [customer.id, now()]
    );
    return { signedOut: r.rows.length, ticketsRevoked: true };
  });
}

/**
 * شمارهٔ تماس برای پیک (§۲۳.۴ ⇐ «فقط ایمیل»): فقط نشستِ همین حساب؛ بی کد، فقط شکل؛ "" ⇒ پاک (فقط حسابِ ایمیل‌دار).
 * ⛔ نه هویت و نه یکتا: نه «گرفته شده» دارد و نه بلیت/نشستی را باطل می‌کند. سفارش‌ها دست نمی‌خورند.
 */
async function setContactPhone(customer, raw) {
  const phone = String(raw ?? '').trim() ? require('./validate').phone(raw, { required: true }) : null;
  return tx(async (c) => {
    const cur = (await c.query('SELECT * FROM store_customers WHERE id = $1 FOR UPDATE', [customer.id])).rows[0];
    if (!cur || cur.status !== 'active') throw forbidden('این حساب مسدود است؛ با دکان تماس بگیرید', 'customer_blocked');
    if (!phone && !cur.email) throw badRequest('پیش از پاک کردنِ شماره، ایمیل را وصل کنید', 'email_first');
    if ((cur.phone || null) === phone) return { customer: view(cur), unchanged: true };
    const up = (await c.query('UPDATE store_customers SET phone = $2, updated_at = $3 WHERE id = $1 RETURNING *',
      [cur.id, phone, now()])).rows[0];
    return { customer: view(up), unchanged: false };
  });
}

/**
 * تغییرِ ایمیلِ حساب — یا وصلِ نخستینِ ایمیل به حسابِ کهنهٔ فقط‌شماره (S9، §۲۳.۴ و §۲۹.۲ بندِ ۱۰، «فقط ایمیل»):
 * دو اثبات — نشستِ همین حساب و بلیتِ تازهٔ ایمیلِ نو (`email` از همان). در یک تراکنش:
 *   سفارش‌های بی‌حسابِ ایمیلِ نو (و برای حسابِ کهنه، سفارش‌های همان شمارهٔ تأییدشدهٔ پیشین) به حساب گره می‌خورند؛
 *   سفارش‌های ایمیلِ پیشین با `store_customer_id` در «سفارش‌های من» می‌مانند؛ گفت‌وگوهای کلیدِ پیشین به همین حساب
 *   گره می‌خورند (`shop-chat.adoptForPhoneChange`)، بلیت‌های ایمیلِ پیشین بی‌اعتبار و نشست‌های دیگرِ حساب لغو
 *   می‌شوند؛ همین نشست می‌ماند.
 * ایمیلِ نو با حسابِ دیگر ⇒ ۴۰۹ (درخواست‌کننده مالکیتِ آن ایمیل را همین حالا اثبات کرده).
 * ⛔ سفارش‌ها سندِ دکان‌اند: فقط پیوندِ حساب (`store_customer_id`) نوشته می‌شود، نه ایمیل/شمارهٔ سفارش.
 */
async function changeEmail(customer, sessionId, email) {
  assertEmail(email);
  return tx(async (c) => {
    const cur = (await c.query('SELECT * FROM store_customers WHERE id = $1 FOR UPDATE', [customer.id])).rows[0];
    if (!cur || cur.status !== 'active') throw forbidden('این حساب مسدود است؛ با دکان تماس بگیرید', 'customer_blocked');
    if (cur.email === email) throw badRequest('این همان ایمیلِ کنونیِ حساب است', 'same_email');
    if ((await c.query('SELECT 1 FROM store_customers WHERE email = $1 AND id <> $2', [email, cur.id])).rows[0]) {
      throw conflict('این ایمیل حسابِ دیگری دارد', 'email_taken');
    }
    const at = now();
    const legacy = !cur.email;
    const previousKey = cur.email || cur.phone;
    const adopted = previousKey ? await require('./shop-chat').adoptForPhoneChange(c, cur.id, previousKey, email) : 0;
    //  سفارش‌های کلیدِ پیشین که هنوز حساب نداشتند (ایمیلِ پیشین؛ یا شمارهٔ تأییدشدهٔ حسابِ کهنه) + ایمیلِ نو
    if (cur.email) await adoptOrders(c, cur.id, { email: cur.email });
    const orders = await adoptOrders(c, cur.id, { email, legacyPhone: legacy ? cur.phone : '' });
    let up;
    try {
      up = (await c.query('UPDATE store_customers SET email = $2, updated_at = $3 WHERE id = $1 RETURNING *',
        [cur.id, email, at])).rows[0];
    } catch (err) {
      if (err && err.code === '23505') throw conflict('این ایمیل حسابِ دیگری دارد', 'email_taken');
      throw err;
    }
    await revokeTickets(cur.email, (sql, params) => c.query(sql, params));
    const r = await c.query(
      'UPDATE store_customer_sessions SET revoked_at = $3 WHERE customer_id = $1 AND id <> $2 AND revoked_at IS NULL RETURNING id',
      [cur.id, String(sessionId || ''), at]
    );
    return {
      customer: view(up), previousEmail: cur.email || null, linked: legacy, orders, conversations: adopted, sessionsRevoked: r.rows.length,
    };
  });
}

/**
 * پاک کردنِ حساب: نشست‌ها و نشانی‌ها هم (cascade)، همهٔ بلیت‌های ایمیلش و اشتراک‌های پوشِ «خبرم کن» ِ کلیدِ همین
 * حساب (ایمیل؛ و شمارهٔ تأییدشدهٔ حسابِ کهنه) در همهٔ دکان‌ها (مهاجرتِ ۴۷۵)؛ سفارش‌ها سندِ دکان‌اند و می‌مانند.
 */
async function remove(customer) {
  const addrMedia = require('./store-address-media');
  let gone = [];
  const out = await tx(async (c) => {
    await revokeTickets(customer.email, (sql, params) => c.query(sql, params));
    await c.query('DELETE FROM store_alert_push WHERE phone = ANY($1::text[])',
      [[customer.email, customer.email ? '' : customer.phone].filter(Boolean)]);
    //  CRM-09: عکسِ دروازه و یادداشتِ صوتیِ نشانی‌ها — شیءِ انبار هم، پس از commit
    gone = await addrMedia.detach(c, customer.id);
    await c.query('DELETE FROM store_customers WHERE id = $1', [customer.id]);
    return { deleted: true };
  });
  await addrMedia.drop(gone);
  return out;
}

// ── نشانی‌ها (CHK-09) ─────────────────────────────────────────────

/**
 * CRM-09 (§۴.۴، مهاجرتِ ۴۹۰): کلیدهای کمکیِ فرمِ ثبت ⇐ ستون. سنجش و سقف همان `orders.addressExtras` ِ ثبت (یک قاعده)؛
 * جای پین/نشانه/متن را نمی‌گیرند. پاسخ همیشه هر هفت کلید را دارد (نبوده ⇒ '').
 */
const EXTRA_COLS = Object.freeze({
  area: 'area', gateColor: 'gate_color', province: 'province', street: 'street', house: 'house', phone2: 'phone2', plusCode: 'plus_code',
});
const EXTRA_KEYS = Object.keys(EXTRA_COLS);

/** S33: `pinCode` (پرچمِ `shop.orders.plus_code` ِ ویترینِ همان دکان) از پینِ ذخیره‌شده — `lib/plus-code.js`؛ خاموش ⇒ همان پاسخ */
function addressView(r, { pinCode = false } = {}) {
  const lat = r.lat === null ? null : Number(r.lat);
  const lng = r.lng === null ? null : Number(r.lng);
  const code = pinCode ? require('./plus-code').codeOf({ lat, lng }) : null;
  return {
    id: r.id, label: r.label, text: r.text, landmark: r.landmark, lat, lng,
    ...Object.fromEntries(EXTRA_KEYS.map((k) => [k, r[EXTRA_COLS[k]] || ''])),
    ...(code ? { pinCode: code } : {}),
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
  //  ⛔ کلیدِ نیامده (برنامهٔ پیشین: `AddressInput` ِ برنامهٔ خرید) ⇒ null یعنی «دست نزن» در PUT و '' در افزودن؛
  //  کلیدِ فرستاده (حتی خالی/null) جایگزین می‌شود
  const norm = require('./orders').addressExtras(b);
  out.extra = Object.fromEntries(EXTRA_KEYS.map((k) => [k, Object.prototype.hasOwnProperty.call(b, k) ? norm[k] : null]));
  return out;
}

async function addresses(customer, opts = {}) {
  return (await many('SELECT * FROM store_customer_addresses WHERE customer_id = $1 ORDER BY created_at, id', [customer.id]))
    .map((r) => addressView(r, opts));
}

async function addAddress(customer, body, opts = {}) {
  const a = parseAddress(body);
  return tx(async (c) => {
    await c.query('SELECT id FROM store_customers WHERE id = $1 FOR UPDATE', [customer.id]);
    const n = Number((await c.query('SELECT COUNT(*)::int AS n FROM store_customer_addresses WHERE customer_id = $1',
      [customer.id])).rows[0].n);
    if (n >= MAX_ADDRESSES) throw conflict(`حداکثر ${MAX_ADDRESSES} نشانی`, 'too_many_addresses');
    const at = now();
    const r = (await c.query(
      `INSERT INTO store_customer_addresses (id, customer_id, label, text, landmark, lat, lng, created_at, updated_at,
                                             area, gate_color, province, street, house, phone2, plus_code)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$8,$9,$10,$11,$12,$13,$14,$15) RETURNING *`,
      [ulid(at), customer.id, a.label, a.text, a.landmark, a.lat, a.lng, at, ...EXTRA_KEYS.map((k) => a.extra[k] ?? '')]
    )).rows[0];
    return addressView(r, opts);
  });
}

async function updateAddress(customer, id, body, opts = {}) {
  const a = parseAddress(body);
  const r = await one(
    `UPDATE store_customer_addresses SET label = $3, text = $4, landmark = $5, lat = $6, lng = $7, updated_at = $8,
            area = COALESCE($9, area), gate_color = COALESCE($10, gate_color), province = COALESCE($11, province),
            street = COALESCE($12, street), house = COALESCE($13, house), phone2 = COALESCE($14, phone2),
            plus_code = COALESCE($15, plus_code)
      WHERE id = $1 AND customer_id = $2 RETURNING *`,
    [String(id || ''), customer.id, a.label, a.text, a.landmark, a.lat, a.lng, now(), ...EXTRA_KEYS.map((k) => a.extra[k])]
  );
  if (!r) throw notFound('این نشانی پیدا نشد', 'address_not_found');
  return addressView(r, opts);
}

async function removeAddress(customer, id) {
  const addrMedia = require('./store-address-media');
  let gone = [];
  const r = await tx(async (c) => {
    //  CRM-09: رسانهٔ همین نشانی (شیءِ انبار پس از commit)
    gone = await addrMedia.detach(c, customer.id, String(id || ''));
    return (await c.query('DELETE FROM store_customer_addresses WHERE id = $1 AND customer_id = $2 RETURNING id',
      [String(id || ''), customer.id])).rows[0];
  });
  if (!r) throw notFound('این نشانی پیدا نشد', 'address_not_found');
  await addrMedia.drop(gone);
  return { deleted: true };
}

module.exports = {
  MAX_ADDRESSES, MAX_SESSIONS, TOKEN_RE,
  view, byEmail, adoptOrders, signIn, auth, signOut, signOutAll, rename, setContactPhone, changeEmail, remove, ticketGen, revokeTickets,
  addresses, addAddress, updateAddress, removeAddress,
};
