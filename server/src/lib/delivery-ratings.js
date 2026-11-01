'use strict';
/**
 * امتیازِ دوطرفهٔ تحویل — DLV-18 (v3 §۶.۱۸)، پشتِ پرچمِ خاموشِ `shop.delivery_ratings`، مهاجرتِ ۱۸۵.
 *
 *   مشتری ⇒ پیک ── با همان توکنِ پیگیریِ ویترین (`/api/store/:slug/orders/:token/rating`).
 *   پیک ⇒ تحویل ── پیکِ همان مأموریت (`POST /api/courier/missions/:id/rating`): نشانی پیدا شد؟ مشتری آماده بود؟
 *   دکان ────────── `GET /api/shop/delivery/ratings` (`delivery.couriers`: صاحب و مدیر) — جمعِ هر پیک و تازه‌ها.
 *
 * ⛔ فقط پس از «تحویل شد» ِ پیکِ دکان و تا ۷ روز (زمانِ سرور)؛ از هر سو یک بار — همان امتیاز دوباره ⇒ همان پاسخ
 * (`replayed`)، امتیازِ دیگر ⇒ ۴۰۹ `already_rated`. فقط ستارهٔ ۱ تا ۵ و برچسب از فهرستِ ثابتِ همان سو (بی متنِ آزاد،
 * پس نه دشنام و نه دادهٔ شخصی). امتیازِ پیک به مشتری هرگز به مشتری یا پیکِ دیگر نشان داده نمی‌شود؛ پیک هم
 * امتیازِ خودش را نمی‌بیند (DLV-20: بی فشار). منطقِ خالص دوقلوی `core/delivery/DeliveryRating.kt` با بردارِ
 * `core/test-vectors/delivery-ratings-v1.json`.
 */
const crypto = require('crypto');
const db = require('../db');
const { now } = db;
const orders = require('./orders');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const FLAG = 'shop.delivery_ratings';
const WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_TAGS = 4;
const TAGS = Object.freeze({
  customer: Object.freeze(['on_time', 'polite', 'careful', 'late', 'rude', 'damaged']),
  courier: Object.freeze(['easy_to_find', 'ready', 'hard_to_find', 'not_ready', 'wrong_pin']),
});

// ════════════════════════════════════════════════════════════════════
//  منطقِ خالص (همان بردار با Kotlin)
// ════════════════════════════════════════════════════════════════════

/** ورودیِ امتیاز ⇒ `{stars, tags}` (برچسب‌ها یکتا و به ترتیبِ فهرست) یا خطای `bad_stars`/`bad_tags`. */
function parse(body, rater) {
  const b = body && typeof body === 'object' ? body : {};
  if (!Number.isSafeInteger(b.stars) || b.stars < 1 || b.stars > 5) return { error: 'bad_stars' };
  const raw = b.tags === undefined || b.tags === null ? [] : b.tags;
  if (!Array.isArray(raw) || raw.length > MAX_TAGS * 2 || raw.some((t) => typeof t !== 'string')) return { error: 'bad_tags' };
  const allowed = TAGS[rater];
  const set = new Set(raw.map((t) => t.trim()));
  if ([...set].some((t) => !allowed.includes(t)) || set.size > MAX_TAGS) return { error: 'bad_tags' };
  return { stars: b.stars, tags: allowed.filter((t) => set.has(t)) };
}

/** میانگین به دهم (عددِ صحیح: ۴٫۳ ⇒ ۴۳، گرد به بالا در نیمه) و شمارِ هر برچسب. */
function summarize(list) {
  const tags = {};
  let sum = 0;
  for (const r of list) {
    sum += r.stars;
    for (const t of r.tags) tags[t] = (tags[t] || 0) + 1;
  }
  //  عددِ صحیح، بی ممیزِ شناور: floor((۲۰·جمع + n) / ۲n) = گردِ نیمه‌به‌بالا (همان Kotlin)
  return { count: list.length, avgTenths: list.length ? Math.floor((sum * 20 + list.length) / (2 * list.length)) : null, tags };
}

// ════════════════════════════════════════════════════════════════════
//  نوشتن و خواندن
// ════════════════════════════════════════════════════════════════════

const fail = (code) => {
  const m = {
    bad_stars: [badRequest, 'ستاره ۱ تا ۵'], bad_tags: [badRequest, 'برچسب‌ها معتبر نیستند'],
    not_ratable: [conflict, 'فقط تحویلِ انجام‌شدهٔ پیکِ دکان امتیاز می‌گیرد'],
    rating_closed: [conflict, 'مهلتِ امتیاز (۷ روز پس از تحویل) گذشته است'],
    already_rated: [conflict, 'این تحویل پیش‌تر امتیاز گرفته است'],
  }[code];
  return m[0](m[1], code);
};

const tagsOf = (v) => (Array.isArray(v) ? v : typeof v === 'string' ? JSON.parse(v) : []);
const view = (r) => ({ rater: r.rater, stars: Number(r.stars), tags: tagsOf(r.tags), createdAt: Number(r.created_at) });

/** یک امتیاز برای مأموریتِ `a` از سوی `rater` — زیرِ قفلِ ردیفِ مأموریت. ⇒ `{rating, replayed}` */
async function write(a, rater, body) {
  const v = parse(body, rater);
  if (v.error) throw fail(v.error);
  const hash = crypto.createHash('sha256').update(JSON.stringify([v.stars, v.tags])).digest('hex');
  return db.tx(async (c) => {
    const row = (await c.query('SELECT * FROM delivery_assignments WHERE id=$1 FOR UPDATE', [a.id])).rows[0];
    if (!row || row.status !== 'delivered' || row.mode !== 'shop_courier') throw fail('not_ratable');
    const prev = (await c.query('SELECT * FROM delivery_ratings WHERE assignment_id=$1 AND rater=$2', [row.id, rater])).rows[0];
    if (prev) {
      if (prev.request_hash !== hash) throw fail('already_rated');
      return { rating: view(prev), replayed: true };
    }
    const at = now();
    if (at > Number(row.delivered_at) + WINDOW_MS) throw fail('rating_closed');
    const ins = (await c.query(
      `INSERT INTO delivery_ratings (id, shop_id, assignment_id, order_id, courier_id, rater, stars, tags, request_hash, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10) RETURNING *`,
      [ulid(at), row.shop_id, row.id, row.order_id, row.courier_id || '', rater, v.stars, JSON.stringify(v.tags), hash, at]
    )).rows[0];
    return { rating: view(ins), replayed: false };
  });
}

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);

/** پیک ⇒ تحویل. مأموریتِ پیکِ دیگر «پیدا نشد». */
async function rateByCourier(shopId, assignmentId, body, actor) {
  if (!(await flagOn(shopId))) throw notFound('امتیازِ تحویل برای این دکان روشن نیست', 'ratings_disabled');
  const a = await db.one('SELECT id, courier_id FROM delivery_assignments WHERE shop_id=$1 AND id=$2', [shopId, String(assignmentId || '')]);
  if (!a || a.courier_id !== actor.courierId) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
  return write(a, 'courier', body);
}

/** مأموریتِ تحویل‌شدهٔ پیکِ دکان برای سفارشِ ویترین (آخرین)، یا null */
const deliveredOf = (shopId, orderId) => db.one(
  `SELECT id, delivered_at FROM delivery_assignments WHERE shop_id=$1 AND order_id=$2 AND status='delivered' AND mode='shop_courier'
    ORDER BY delivered_at DESC LIMIT 1`, [shopId, orderId]
);

/** مشتری: سفارش از توکن (همان قاعدهٔ لینکِ پیگیری). پرچمِ خاموش ⇒ همان «پیدا نشد». */
async function orderOfCustomer(store, token) {
  if (!(await flagOn(store.shop_id))) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const sf = require('./storefront');
  const id = await sf.orderIdOfLink(store, token);
  const o = await db.one('SELECT id, source FROM online_orders WHERE shop_id=$1 AND id=$2', [store.shop_id, id]);
  if (!o || o.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  return o.id;
}

/** آیا مشتری می‌تواند امتیاز بدهد؟ ⇒ `{canRate, rating, tags}` */
async function customerState(store, token) {
  const orderId = await orderOfCustomer(store, token);
  const a = await deliveredOf(store.shop_id, orderId);
  const prev = a ? await db.one(`SELECT * FROM delivery_ratings WHERE assignment_id=$1 AND rater='customer'`, [a.id]) : null;
  const open = Boolean(a) && !prev && now() <= Number(a.delivered_at) + WINDOW_MS;
  return { canRate: open, rating: prev ? view(prev) : null, tags: TAGS.customer };
}

async function rateByCustomer(store, token, body) {
  const orderId = await orderOfCustomer(store, token);
  const a = await deliveredOf(store.shop_id, orderId);
  if (!a) throw fail('not_ratable');
  return write(a, 'customer', body);
}

/** دکان: جمعِ هر پیک (امتیازِ مشتری به او و امتیازِ او به تحویل‌ها) و تازه‌ترین‌ها؛ بازهٔ پیش‌فرض ۹۰ روز. */
async function forShop(shopId, q = {}) {
  if (!(await flagOn(shopId))) throw forbidden('امتیازِ تحویل برای این دکان روشن نیست', 'ratings_disabled');
  const num = (v, d) => (v === undefined || v === '' ? d : Number(v));
  const to = num(q.to, now());
  const from = num(q.from, to - 90 * 24 * 60 * 60 * 1000);
  if (!Number.isSafeInteger(from) || !Number.isSafeInteger(to) || from > to) throw badRequest('بازه معتبر نیست', 'bad_range');
  const rows = await db.many(
    `SELECT r.*, o.order_no FROM delivery_ratings r JOIN online_orders o ON o.id = r.order_id
      WHERE r.shop_id=$1 AND r.created_at BETWEEN $2 AND $3 ORDER BY r.created_at DESC, r.id DESC LIMIT 2000`, [shopId, from, to]
  );
  const couriers = await db.many('SELECT id, name FROM shop_couriers WHERE shop_id=$1 ORDER BY name, id', [shopId]);
  const list = rows.map((r) => ({ ...view(r), courierId: r.courier_id, assignmentId: r.assignment_id, orderNo: Number(r.order_no) }));
  const of = (cid, rater) => summarize(list.filter((r) => r.courierId === cid && r.rater === rater));
  return {
    from, to,
    couriers: couriers.map((c) => ({ courierId: c.id, name: c.name, fromCustomers: of(c.id, 'customer'), byCourier: of(c.id, 'courier') }))
      .filter((c) => c.fromCustomers.count || c.byCourier.count),
    recent: list.slice(0, 50),
  };
}

module.exports = { FLAG, WINDOW_MS, MAX_TAGS, TAGS, parse, summarize, rateByCourier, customerState, rateByCustomer, forShop };
