'use strict';
/**
 * فهرستِ سیاه و سفیدِ سفارشِ ویترینِ هر دکان — RSK-04 (§۶.۲۱)؛ جدول در ‎migrations/166_shop_risk_lists.sql‎.
 *
 *   دکان (صاحب و مدیر، `orders.contact`) ─▶ GET/POST /api/shop/orders/risk/lists، DELETE …/:entryId
 *   ثبتِ ویترین ─▶ order-risk.checkIn ─▶ matchIn: بسته ⇒ ۴۰۳ ِ خنثی؛ مطمئن ⇒ بی سقفِ مشتریِ تازه
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • هر ردیف دلیلِ نوشته (۳ تا ۲۰۰ نویسه) و انقضا (۱ تا ۳۶۵ روز، ساعتِ سرور) دارد؛ ردیفِ گذشته
 *     هیچ اثری ندارد و در فهرست نمی‌آید.
 *   • شماره به شکلِ یکتا (‎+930…‎ = ‎+93…‎، `order-rate.canon`)؛ یک شمارهٔ فعال فقط در یکی از دو فهرست.
 *   • پین فقط «بسته» است (شعاعِ ۱۰ تا ۲۰۰۰ متر): «مطمئن» برای جا معنایی ندارد — هر کسی آن‌جا سفارش می‌دهد.
 *   • «دستگاه» هنوز نیست: ویترین شناسهٔ دستگاهِ مشتری ندارد (برنامهٔ خریدِ جدا، §۴۰.۱).
 *   • برداشتن نرم است (`removed_at`، کننده) تا سابقه بماند؛ سقفِ ۵۰۰ ردیفِ فعال برای هر دکان.
 *   • ⛔ دلیل هرگز به مشتری نمی‌رسد؛ پیامِ ردِ ویترین خنثی است و نمی‌گوید شماره بود یا جا.
 */
const { one, many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const LISTS = Object.freeze(['block', 'allow']);
const KINDS = Object.freeze(['phone', 'pin']);
const MAX_DAYS = 365;
const MAX_ACTIVE = 500;
const DAY_MS = 24 * 60 * 60 * 1000;

const str = (v) => (typeof v === 'string' ? v.trim() : '');

function canonPhone(v) {
  const p = require('./validate').phone(v, { required: true });
  return require('./order-rate').canon(p);
}

function num(v, field, min, max) {
  const n = typeof v === 'number' ? v : Number.NaN;
  if (!Number.isFinite(n) || n < min || n > max) throw badRequest(`${field} معتبر نیست`, 'bad_risk_entry');
  return n;
}

/** ورودیِ یک ردیف — هر چیزِ ناجور ⇒ ۴۰۰ `bad_risk_entry`. */
function parse(body, at) {
  const b = body && typeof body === 'object' ? body : {};
  const list = str(b.list);
  const kind = str(b.kind);
  if (!LISTS.includes(list)) throw badRequest('فهرست باید block یا allow باشد', 'bad_risk_entry');
  if (!KINDS.includes(kind)) throw badRequest('نوع باید phone یا pin باشد', 'bad_risk_entry');
  const reason = require('./validate').stripControl(str(b.reason)).replace(/\s+/g, ' ');
  if (reason.length < 3 || reason.length > 200) throw badRequest('دلیل (۳ تا ۲۰۰ نویسه) لازم است', 'reason_required');
  const days = b.expiresInDays;
  if (!Number.isSafeInteger(days) || days < 1 || days > MAX_DAYS) {
    throw badRequest(`انقضا باید ۱ تا ${MAX_DAYS} روز باشد`, 'bad_expiry');
  }
  const out = { list, kind, reason, expiresAt: at + days * DAY_MS, phone: '', lat: null, lng: null, radiusM: null };
  if (kind === 'phone') {
    out.phone = canonPhone(b.phone);
  } else {
    if (list !== 'block') throw badRequest('پین فقط در فهرستِ بسته', 'bad_risk_entry');
    out.lat = Math.round(num(b.lat, 'عرضِ جغرافیایی', -90, 90) * 1e6) / 1e6;
    out.lng = Math.round(num(b.lng, 'طولِ جغرافیایی', -180, 180) * 1e6) / 1e6;
    out.radiusM = b.radiusM === undefined ? 100 : num(b.radiusM, 'شعاع', 10, 2000);
    if (!Number.isInteger(out.radiusM)) throw badRequest('شعاع باید عددِ صحیحِ متر باشد', 'bad_risk_entry');
  }
  return out;
}

function view(r) {
  return {
    id: r.id,
    list: r.list,
    kind: r.kind,
    ...(r.kind === 'phone' ? { phone: r.phone } : { pin: { lat: Number(r.lat), lng: Number(r.lng), radiusM: Number(r.radius_m) } }),
    reason: r.reason,
    expiresAt: Number(r.expires_at),
    createdBy: r.created_by,
    createdAt: Number(r.created_at),
  };
}

/** ردیف‌های فعال (نه برداشته، نه گذشته) — تازه‌ترین بالا. */
async function list(shopId, { at = now() } = {}) {
  const rows = await many(
    `SELECT * FROM shop_risk_lists WHERE shop_id=$1 AND removed_at IS NULL AND expires_at > $2
      ORDER BY created_at DESC, id DESC LIMIT ${MAX_ACTIVE}`,
    [shopId, at]
  );
  return { items: rows.map(view), maxActive: MAX_ACTIVE, maxDays: MAX_DAYS };
}

async function add(shopId, body, userId) {
  const at = now();
  const e = parse(body, at);
  return tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`risk-lists:${shopId}`]);
    //  ردیفِ گذشتهٔ همین شماره جا را برای ردیفِ تازه خالی می‌کند (یکتاییِ ردیفِ فعال)
    if (e.phone) {
      await c.query(
        `UPDATE shop_risk_lists SET removed_at=$3, removed_by='expired'
          WHERE shop_id=$1 AND phone=$2 AND kind='phone' AND removed_at IS NULL AND expires_at <= $3`,
        [shopId, e.phone, at]
      );
      const taken = (await c.query(
        `SELECT list FROM shop_risk_lists WHERE shop_id=$1 AND phone=$2 AND kind='phone' AND removed_at IS NULL`,
        [shopId, e.phone]
      )).rows[0];
      if (taken) {
        const err = conflict('این شماره همین حالا در یکی از فهرست‌هاست؛ نخست آن را بردارید', 'already_listed');
        err.data = { list: taken.list };
        throw err;
      }
    }
    const n = (await c.query(
      'SELECT COUNT(*)::int AS n FROM shop_risk_lists WHERE shop_id=$1 AND removed_at IS NULL AND expires_at > $2',
      [shopId, at]
    )).rows[0].n;
    if (n >= MAX_ACTIVE) throw conflict(`حداکثر ${MAX_ACTIVE} ردیفِ فعال`, 'risk_list_full');
    const id = ulid(at);
    await c.query(
      `INSERT INTO shop_risk_lists (id, shop_id, list, kind, phone, lat, lng, radius_m, reason, expires_at,
                                    created_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)`,
      [id, shopId, e.list, e.kind, e.phone, e.lat, e.lng, e.radiusM, e.reason, e.expiresAt, userId || '', at]
    );
    return view((await c.query('SELECT * FROM shop_risk_lists WHERE id=$1', [id])).rows[0]);
  });
}

/** برداشتنِ نرم؛ تکرار ⇒ همان نتیجه با `removed: false`. دکانِ دیگر ⇒ ۴۰۴. */
async function remove(shopId, entryId, userId) {
  const id = String(entryId || '').slice(0, 40);
  const row = await one('SELECT id, removed_at FROM shop_risk_lists WHERE shop_id=$1 AND id=$2', [shopId, id]);
  if (!row) throw notFound('این ردیف پیدا نشد', 'risk_entry_not_found');
  if (row.removed_at !== null && row.removed_at !== undefined) return { id, removed: false };
  const r = await one(
    `UPDATE shop_risk_lists SET removed_at=$3, removed_by=$4 WHERE shop_id=$1 AND id=$2 AND removed_at IS NULL RETURNING id`,
    [shopId, id, now(), userId || '']
  );
  return { id, removed: !!r };
}

/** فاصلهٔ دو نقطه به متر (هاورسین). */
function meters(lat1, lng1, lat2, lng2) {
  const R = 6_371_000;
  const rad = (d) => (d * Math.PI) / 180;
  const a = Math.sin(rad(lat2 - lat1) / 2) ** 2
    + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lng2 - lng1) / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

/**
 * درونِ تراکنشِ ثبت: شماره (و پینِ ارسال، اگر هست) در کدام فهرستِ فعالِ همین دکان است؟
 * @returns {{block: object|null, allow: object|null}}
 */
async function matchIn(run, shopId, { phone, address, at = now() }) {
  const out = { block: null, allow: null };
  if (phone) {
    const r = (await run(
      `SELECT * FROM shop_risk_lists WHERE shop_id=$1 AND kind='phone' AND phone=$2 AND removed_at IS NULL AND expires_at > $3`,
      [shopId, require('./order-rate').canon(phone), at]
    )).rows[0];
    if (r) out[r.list] = r;
  }
  const lat = address && Number(address.lat);
  const lng = address && Number(address.lng);
  if (!out.block && address && address.lat !== undefined && address.lat !== null && Number.isFinite(lat) && Number.isFinite(lng)) {
    const pins = (await run(
      `SELECT * FROM shop_risk_lists WHERE shop_id=$1 AND kind='pin' AND list='block' AND removed_at IS NULL AND expires_at > $2`,
      [shopId, at]
    )).rows;
    const hit = pins.find((p) => meters(lat, lng, Number(p.lat), Number(p.lng)) <= Number(p.radius_m));
    if (hit) out.block = hit;
  }
  return out;
}

module.exports = { LISTS, KINDS, MAX_DAYS, MAX_ACTIVE, parse, list, add, remove, matchIn, meters };
