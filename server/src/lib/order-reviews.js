'use strict';
/**
 * NTF-09 — امتیاز و نظرِ مشتری برای سفارش، با بازبینیِ دکان (v3 §۶.۱۹)، پشتِ پرچمِ خاموشِ `shop.orders.reviews`، مهاجرتِ ۱۹۱.
 *
 *   مشتری ── با همان توکنِ پیگیری: `POST /api/store/:slug/orders/:token/review` `{stars, comment?}`.
 *   پیگیری ── کلیدِ `review` در `GET /api/store/:slug/orders/:token` (فقط با پرچم؛ خاموش ⇒ کلید نیست).
 *   دکان ─── `routes/order-feedback.js`: فهرست و «تأیید»/«رد» (`orders.manage`).
 *   عمومی ── `GET /api/store/:slug/reviews`: فقط تأییدشده‌ها — ستاره، نوشته و روز؛ بی نام، شماره و شمارهٔ سفارش.
 *
 * ⛔ فقط پس از تحویل (`delivered`/`picked_up` و وضعیت‌های پس از آن)؛ یک نظر برای هر سفارش (UNIQUE ِ `order_id`):
 *    همان نظر دوباره ⇒ همان پاسخ (`replayed`)، نظرِ دیگر ⇒ ۴۰۹ `already_reviewed`.
 * ⛔ هیچ نظری پیش از «تأیید» ِ دکان جایی منتشر نمی‌شود؛ «رد» ِ نظرِ تأییدشده آن را از نمای عمومی برمی‌دارد.
 * ⛔ پرچمِ خاموش ⇒ درهای مشتری و عمومی ۴۰۴، درِ دکان ۴۰۳، و هیچ ردیفی.
 */
const crypto = require('crypto');
const db = require('../db');
const { now } = db;
const orders = require('./orders');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const FLAG = 'shop.orders.reviews';
const DELIVERED = Object.freeze(['delivered', 'picked_up', 'return_requested', 'refunded_partial', 'refunded_full']);
const COMMENT_MAX = 500;
const STATUSES = Object.freeze(['pending', 'approved', 'rejected']);
const DECISIONS = Object.freeze({ approve: 'approved', reject: 'rejected' });
const ID_RE = /^[0-9A-Z]{26}$/;
const PUBLIC_LIMIT = 20;

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

/** ورودی ⇒ `{stars, comment, hash}` — ستارهٔ صحیحِ ۱ تا ۵؛ نوشتهٔ اختیاری تا ۵۰۰ نویسه، بی نویسهٔ کنترلی. */
function parse(body) {
  const b = body && typeof body === 'object' ? body : {};
  if (!Number.isSafeInteger(b.stars) || b.stars < 1 || b.stars > 5) throw badRequest('ستاره ۱ تا ۵', 'bad_stars');
  if (b.comment !== undefined && b.comment !== null && typeof b.comment !== 'string') throw badRequest('نوشته معتبر نیست', 'bad_comment');
  // eslint-disable-next-line no-control-regex
  const comment = String(b.comment || '').replace(/[\u0000-\u0009\u000B-\u001F\u007F‎‏‪-‮⁦-⁩]/g, ' ').trim();
  if ([...comment].length > COMMENT_MAX) throw badRequest(`نوشته حداکثر ${COMMENT_MAX} نویسه`, 'bad_comment');
  return { stars: b.stars, comment, hash: crypto.createHash('sha256').update(JSON.stringify([b.stars, comment])).digest('hex') };
}

const num = (v) => (v === null || v === undefined ? null : Number(v));
const customerReview = (r) => ({ stars: Number(r.stars), comment: r.comment, status: r.status, createdAt: Number(r.created_at) });
const shopReview = (r) => ({
  ...customerReview(r), id: r.id, orderId: r.order_id, orderNo: Number(r.order_no),
  moderatedBy: r.moderated_by || null, moderatedAt: num(r.moderated_at),
});

/** کلیدِ `review` ِ پیگیری — بی پرچم `undefined` (کلید نیاید). */
async function customerView(shopId, orderId, status) {
  if (!(await flagOn(shopId))) return undefined;
  const r = await db.one('SELECT * FROM order_reviews WHERE shop_id=$1 AND order_id=$2', [shopId, String(orderId || '')]);
  return { canReview: !r && DELIVERED.includes(status), commentMax: COMMENT_MAX, review: r ? customerReview(r) : null };
}

/** مشتری ⇒ `{review, replayed}` */
async function submit(store, token, body) {
  if (!(await flagOn(store.shop_id))) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const id = await require('./storefront').orderIdOfLink(store, token);
  const v = parse(body);
  return db.tx(async (c) => {
    const o = (await c.query('SELECT id, source, status FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [store.shop_id, id])).rows[0];
    if (!o || o.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
    const prev = (await c.query('SELECT * FROM order_reviews WHERE order_id=$1', [o.id])).rows[0];
    if (prev) {
      if (prev.request_hash !== v.hash) throw conflict('برای این سفارش پیش‌تر نظر داده‌اید', 'already_reviewed');
      return { review: customerReview(prev), replayed: true };
    }
    if (!DELIVERED.includes(o.status)) throw conflict('نظر فقط پس از تحویلِ سفارش', 'not_delivered');
    const at = now();
    const row = (await c.query(
      `INSERT INTO order_reviews (id, shop_id, order_id, stars, comment, request_hash, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`, [ulid(at), store.shop_id, o.id, v.stars, v.comment, v.hash, at])).rows[0];
    return { review: customerReview(row), replayed: false };
  });
}

async function needFlag(shopId) {
  if (!(await flagOn(shopId))) throw forbidden('نظرِ مشتری برای این دکان روشن نیست', 'reviews_disabled');
}

/** دکان: فهرست — `status` = pending (پیش‌فرض) | approved | rejected | all؛ تازه‌ترین اول، حداکثر ۲۰۰. */
async function forShop(shopId, q = {}) {
  await needFlag(shopId);
  const status = q.status === undefined || q.status === '' ? 'pending' : String(q.status);
  if (status !== 'all' && !STATUSES.includes(status)) throw badRequest('وضعیت معتبر نیست', 'bad_status');
  const rows = await db.many(
    `SELECT r.*, o.order_no FROM order_reviews r JOIN online_orders o ON o.id = r.order_id
      WHERE r.shop_id=$1 AND ($2 = 'all' OR r.status = $2) ORDER BY r.created_at DESC, r.id DESC LIMIT 200`, [shopId, status]);
  return { reviews: rows.map(shopReview) };
}

/** دکان: `{decision: approve|reject}` ⇒ `{review, unchanged}`؛ همان تصمیم دوباره بی‌اثر. */
async function moderate(shopId, reviewId, body, actor) {
  await needFlag(shopId);
  const to = DECISIONS[body && body.decision];
  if (!to) throw badRequest('تصمیم فقط approve یا reject', 'bad_decision');
  const id = String(reviewId || '');
  if (!ID_RE.test(id)) throw notFound('نظر پیدا نشد', 'review_not_found');
  return db.tx(async (c) => {
    const r = (await c.query(
      `SELECT r.*, o.order_no FROM order_reviews r JOIN online_orders o ON o.id = r.order_id
        WHERE r.shop_id=$1 AND r.id=$2 FOR UPDATE OF r`, [shopId, id])).rows[0];
    if (!r) throw notFound('نظر پیدا نشد', 'review_not_found');
    if (r.status === to) return { review: shopReview(r), unchanged: true };
    const u = (await c.query(
      'UPDATE order_reviews SET status=$2, moderated_by=$3, moderated_at=$4 WHERE id=$1 RETURNING *',
      [id, to, String((actor && actor.id) || ''), now()])).rows[0];
    return { review: shopReview({ ...u, order_no: r.order_no }), unchanged: false, from: r.status };
  });
}

/** عمومی: فقط تأییدشده‌ها — جمع و ۲۰ تای تازه، بی هیچ نشانی از مشتری یا سفارش. */
async function publicList(store) {
  if (!(await flagOn(store.shop_id))) throw notFound('پیدا نشد', 'not_found');
  const s = await db.one(
    `SELECT COUNT(*)::int AS n, COALESCE(SUM(stars), 0)::int AS total FROM order_reviews WHERE shop_id=$1 AND status='approved'`, [store.shop_id]);
  const rows = await db.many(
    `SELECT stars, comment, moderated_at, created_at FROM order_reviews WHERE shop_id=$1 AND status='approved'
      ORDER BY moderated_at DESC NULLS LAST, id DESC LIMIT $2`, [store.shop_id, PUBLIC_LIMIT]);
  const n = Number(s.n);
  return {
    //  میانگین به دهم، عددِ صحیح و گردِ نیمه‌به‌بالا (همان فرمولِ امتیازِ تحویل، DLV-18)
    count: n, avgTenths: n ? Math.floor((Number(s.total) * 20 + n) / (2 * n)) : null,
    reviews: rows.map((r) => ({ stars: Number(r.stars), comment: r.comment, at: Number(r.created_at) })),
  };
}

module.exports = { FLAG, DELIVERED, COMMENT_MAX, parse, customerView, submit, forShop, moderate, publicList };
