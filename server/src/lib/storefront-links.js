'use strict';
/**
 * لینکِ پیگیریِ سفارشِ ویترین (توکنِ v1 و v2 ِ زمان‌دار و باطل‌شدنی، TEN-03) — از `lib/storefront.js` جدا شد تا
 * زیرِ سقفِ اندازه بماند؛ رفتار همان. وابستگی‌های همان پرونده (کلید و HMAC ِ v1، پرچم، پایگاه‌داده) تزریق می‌شوند.
 */
const crypto = require('crypto');

module.exports = ({ tokenKey, macOf, orders, one, now, config, ApiError, notFound }) => {
/*
 *  ── TEN-03: لینکِ خصوصیِ زمان‌دار و باطل‌شدنی (پرچمِ `shop.storefront.link_expiry`) ──
 *  §۲۹.۷ بندِ ۴: «لینک پیگیری … کوتاه‌عمر … و در صورت امکان باطل‌کردنی». توکنِ v1
 *  (`<ULID>.<HMAC>`) نه منقضی می‌شد نه باطل. با پرچمِ روشنِ دکان:
 *    • توکنِ تازه `<ULID>.<صدور>.<نسل>.<HMAC>` (پایهٔ ۳۶) — صدور و نسل زیرِ همان HMAC؛
 *    • `صدور + trackLinkTtlMs` (پیش‌فرض ۳۰ روز) گذشته ⇒ ۴۱۰ `link_expired`؛
 *    • نسلِ توکن ≠ `online_orders.track_gen` (مهاجرتِ ۱۴۰؛ دکان با «باطل کردنِ لینک» یکی
 *      بالا می‌برد) ⇒ ۴۱۰ `link_revoked`؛
 *    • توکنِ v1 ِ پیشین = نسلِ صفر با صدورِ همان زمانِ ساختِ سفارش.
 *  مشتری لینکِ تازه را از «سفارش‌های من» (شمارهٔ تأییدشده) می‌گیرد — همان‌جا توکنِ تازه با
 *  نسلِ جاری صادر می‌شود. HMAC همیشه پیش از هر پرسش و زمان‌ثابت سنجیده می‌شود؛ پس ۴۱۰ فقط به
 *  دارندهٔ توکنی می‌رسد که روزی معتبر بوده. پرچمِ خاموش ⇒ هر توکنِ سالم مثلِ امروز، بی انقضا.
 */
const LINK_FLAG = 'shop.storefront.link_expiry';
const TOKEN_V1 = /^([0-9A-Z]{26})\.([A-Za-z0-9_-]{32})$/;
const TOKEN_V2 = /^([0-9A-Z]{26})\.([0-9a-z]{1,12})\.([0-9a-z]{1,7})\.([A-Za-z0-9_-]{32})$/;

function macV2(shopId, orderId, iat, gen) {
  return crypto.createHmac('sha256', tokenKey()).update(`track-v2\n${shopId}\n${orderId}\n${iat}\n${gen}`)
    .digest('base64url').slice(0, 32);
}

/** توکنِ پیگیری: بی `link` همان v1 ِ امروز؛ با `{ gen, at }` توکنِ زمان‌دارِ v2 */
function tokenOf(shopId, orderId, link = null) {
  if (!link) return `${orderId}.${macOf(shopId, orderId)}`;
  const iat = Math.max(0, Math.trunc(Number(link.at ?? now())));
  const gen = Math.max(0, Math.trunc(Number(link.gen) || 0));
  return `${orderId}.${iat.toString(36)}.${gen.toString(36)}.${macV2(shopId, orderId, iat, gen)}`;
}

/** پرچمِ لینکِ زمان‌دار برای این دکان */
function linkPolicyOn(shopId) {
  return orders.flagOf(shopId, LINK_FLAG);
}

/** توکنِ v2 برای ردیفِ سفارشِ دارای `track_gen` اگر پرچم روشن است؛ وگرنه v1 */
function listTokenOf(shopId, row, on) {
  return on ? tokenOf(shopId, row.id, { gen: row.track_gen, at: now() }) : tokenOf(shopId, row.id);
}

/** توکن ⇒ `{ orderId, iat, gen }` (v1: `iat` تهی، `gen` صفر) — یا null. HMAC زمان‌ثابت. */
function parseToken(shopId, token) {
  const t = String(token || '');
  let m = TOKEN_V2.exec(t);
  if (m) {
    const [iat, gen] = [m[2], m[3]].map((x) => Number.parseInt(x, 36));
    const a = Buffer.from(m[4]);
    const b = Buffer.from(macV2(shopId, m[1], iat, gen));
    return a.length === b.length && crypto.timingSafeEqual(a, b) ? { orderId: m[1], iat, gen } : null;
  }
  m = TOKEN_V1.exec(t);
  if (!m) return null;
  const a = Buffer.from(m[2]);
  const b = Buffer.from(macOf(shopId, m[1]));
  return a.length === b.length && crypto.timingSafeEqual(a, b) ? { orderId: m[1], iat: null, gen: 0 } : null;
}

/** شناسهٔ سفارش از توکن — یا null (زمان‌ثابت). بی سنجشِ انقضا/نسل؛ برای آن `orderIdOfLink`. */
function orderIdOf(shopId, token) {
  const p = parseToken(shopId, token);
  return p ? p.orderId : null;
}

/**
 * شناسهٔ سفارشِ لینکِ **هنوز معتبر** — توکنِ نادرست ⇒ ۴۰۴؛ با پرچمِ روشن، منقضی یا
 * باطل ⇒ ۴۱۰ (TEN-03). پرچمِ خاموش ⇒ همان `orderIdOf` ِ امروز.
 */
async function orderIdOfLink(sf, token) {
  const p = parseToken(sf.shop_id, token);
  if (!p) throw notFound('سفارش پیدا نشد', 'order_not_found');
  if (!(await linkPolicyOn(sf.shop_id))) return p.orderId;
  const row = await one(
    `SELECT created_at, track_gen FROM online_orders WHERE shop_id=$1 AND id=$2 AND source='storefront'`,
    [sf.shop_id, p.orderId]
  );
  if (!row) throw notFound('سفارش پیدا نشد', 'order_not_found');
  if (p.gen !== Number(row.track_gen)) {
    throw new ApiError(410, 'link_revoked', 'این لینکِ پیگیری باطل شده است؛ سفارش را از «سفارش‌های من» با شمارهٔ خودتان ببینید');
  }
  const iat = p.iat === null ? Number(row.created_at) : p.iat;
  if (now() > iat + config.storefront.trackLinkTtlMs) {
    throw new ApiError(410, 'link_expired', 'مهلتِ این لینکِ پیگیری تمام شده است؛ سفارش را از «سفارش‌های من» با شمارهٔ خودتان ببینید');
  }
  return p.orderId;
}

/**
 * «باطل کردنِ لینکِ پیگیری» از سمتِ دکان — نسل یکی بالا می‌رود و هر لینکِ پیشین ۴۱۰ می‌گیرد.
 * فقط سفارشِ ویترین و فقط با پرچمِ روشن؛ لینکِ تازه (نسلِ نو) برمی‌گردد تا دکان دوباره بفرستد.
 */
async function revokeLink(shopId, orderId) {
  if (!(await linkPolicyOn(shopId))) {
    throw new ApiError(403, 'link_policy_disabled', 'باطل کردنِ لینکِ پیگیری برای این دکان روشن نیست');
  }
  const row = await one(
    `UPDATE online_orders SET track_gen = track_gen + 1
      WHERE shop_id=$1 AND id=$2 AND source='storefront' RETURNING id, track_gen`,
    [shopId, orderId]
  );
  if (!row) throw notFound('سفارشِ ویترین پیدا نشد', 'order_not_found');
  tokenKey();
  return { gen: Number(row.track_gen), token: tokenOf(shopId, row.id, { gen: row.track_gen, at: now() }) };
}

  return { LINK_FLAG, tokenOf, linkPolicyOn, listTokenOf, parseToken, orderIdOf, orderIdOfLink, revokeLink };
};
