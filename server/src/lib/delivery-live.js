'use strict';
/**
 * S5: نمای پیک برای مشتری (قراردادِ بازار §۳، §۴۰.۱) — جدا از `lib/delivery.js`، بی تغییرِ رفتار.
 * `stateOf` از خودِ `lib/delivery` هنگامِ صدا خوانده می‌شود (نه هنگامِ بارگذاری) تا حلقهٔ require نسازد.
 */
const db = require('../db');
const { one, now, tx } = db;
const D = require('./delivery-states');
const orders = require('./orders');
const { notFound, conflict, badRequest, ApiError } = require('../middleware/errors');
const { text, int, optInt, n } = require('./delivery-input');
const { CODE_MAX_ATTEMPTS, derivedCode, codeMatches } = require('./delivery-codes');
const stateOf = (shopId) => require('./delivery').stateOf(shopId);

//  S5 (قراردادِ بازار §۳): نمای پیک برای مشتری — هر دو پیش‌فرض خاموش
const VIEW_FLAG = 'shop.delivery_customer_view';
const LOCATION_FLAG = 'shop.delivery_live_location';
/** «در راه» — تنها بازه‌ای که موقعیت گرفته و نشان داده می‌شود (پیش از «رسیدم»). */
const ON_THE_WAY = Object.freeze(['picked_up', 'moving']);
const LOCATION_TTL_MS = 10 * 60_000;        // نقطهٔ کهنه‌تر نشان داده نمی‌شود و پاک می‌شود
const LOCATION_MIN_INTERVAL_MS = 5_000;     // نقطه‌های تندتر نادیده
const MAX_ETA_MINUTES = 240;

/** وضعیتِ عمومی — مشتری گذارهای درونیِ دکان و پیک را نمی‌بیند. */
const PUBLIC_STATUS = Object.freeze({
  requested: 'waiting', rejected: 'waiting', withdrawn: 'waiting', assigned: 'assigned',
  picked_up: 'on_the_way', moving: 'on_the_way', arrived: 'arrived',
  delivered: 'delivered', failed: 'failed', returned: 'returned',
});
const ACCEPTED = Object.freeze(['assigned', 'picked_up', 'moving', 'arrived', 'failed']);

const firstName = (name) => text(name, 80).split(/\s+/)[0].slice(0, 30);
//  ۳ رقمِ اعشار ≈ ۱۱۰ متر: «تقریبی»، نه درِ خانه
const approx = (x) => Math.round(Number(x) * 1000) / 1000;

/** کدِ زنده برای مشتری — فقط اگر دانه هنوز هست و با هشِ ذخیره‌شده جور است. */
function liveCode(a) {
  if (!a.code_nonce || !a.code_hash || a.code_used_at != null) return null;
  if (!D.LIVE.includes(a.status) || Number(a.code_attempts) >= CODE_MAX_ATTEMPTS) return null;
  const code = derivedCode(a.id, a.code_nonce);
  return codeMatches(a, code) ? code : null;
}

/**
 * نمای پیک برای مشتریِ همین سفارش (فراخواننده سفارش را با توکنِ پیگیری پیدا
 * کرده است). `undefined` = پرچم خاموش ⇒ پاسخِ مشتری همان شکلِ پیشین را دارد؛
 * `null` = هنوز مأموریتی نیست. ⛔ نه شماره، نه شناسهٔ پیک، نه نشانیِ دقیق.
 */
async function customerView(shopId, orderId) {
  const st = await stateOf(shopId);
  if (!st.readable || !(await orders.flagOf(shopId, VIEW_FLAG))) return undefined;
  const a = await one(
    `SELECT a.*, c.name AS courier_name FROM delivery_assignments a
       LEFT JOIN shop_couriers c ON c.shop_id = a.shop_id AND c.id = a.courier_id
      WHERE a.shop_id=$1 AND a.order_id=$2 ORDER BY a.attempt_no DESC LIMIT 1`,
    [shopId, String(orderId || '')]
  );
  if (!a) return null;
  const live = D.LIVE.includes(a.status);
  const onTheWay = ON_THE_WAY.includes(a.status);
  let location = null;
  if (onTheWay && await orders.flagOf(shopId, LOCATION_FLAG)) {
    const p = await one(
      'SELECT lat, lng, server_time FROM delivery_locations WHERE assignment_id=$1 AND server_time >= $2',
      [a.id, now() - LOCATION_TTL_MS]
    );
    if (p) location = { lat: approx(p.lat), lng: approx(p.lng), at: Number(p.server_time), approximate: true };
  }
  const chatOn = live && await require('./shop-chat').flagOf(shopId);
  return {
    status: PUBLIC_STATUS[a.status],
    carrier: a.mode === 'pickup' ? 'pickup' : a.mode === 'external' ? 'external' : 'courier',
    courierName: a.mode === 'shop_courier' && ACCEPTED.includes(a.status) ? firstName(a.courier_name) || null : null,
    //  «تماس با پیک» از راهِ گفت‌وگوی پوشیدهٔ همین سفارش — هرگز شمارهٔ خام
    contact: { via: 'order_chat', available: chatOn },
    code: liveCode(a),
    etaAt: onTheWay && a.eta_at != null ? Number(a.eta_at) : null,
    location,
    deliveredAt: n(a.delivered_at),
    updatedAt: Number(a.updated_at),
  };
}

/** مأموریتِ «در راهِ» همین پیک، قفل‌شده — یا رد. */
async function onTheWayOf(c, shopId, assignmentId, actor) {
  const a = (await c.query(
    'SELECT id, shop_id, status, courier_id FROM delivery_assignments WHERE shop_id=$1 AND id=$2 FOR UPDATE',
    [shopId, String(assignmentId || '')]
  )).rows[0];
  if (!a || a.courier_id !== actor.courierId) throw notFound('مأموریت پیدا نشد', 'assignment_not_found');
  if (actor.active !== true) throw new ApiError(403, 'courier_inactive', 'این پیک غیرفعال است');
  if (!ON_THE_WAY.includes(a.status)) throw conflict('این مأموریت «در راه» نیست', 'not_on_the_way');
  return a;
}

/** نقطه‌های کهنه‌تر از نگهداری می‌روند (هر نوشتن و هر آزمون صدایش می‌کند). */
async function purgeLocations(run = (t, p) => db.query(t, p)) {
  await run('DELETE FROM delivery_locations WHERE server_time < $1', [now() - LOCATION_TTL_MS]);
}

/**
 * پیک آخرین نقطه‌اش را می‌فرستد (`/api/courier`). فقط با هر دو پرچم، فقط
 * مأموریتِ خودش و فقط «در راه». فقط آخرین نقطه نگه داشته می‌شود.
 */
async function putLocation(shopId, assignmentId, body, actor) {
  if (!(await orders.flagOf(shopId, VIEW_FLAG)) || !(await orders.flagOf(shopId, LOCATION_FLAG))) {
    throw notFound('موقعیتِ زنده برای این دکان روشن نیست', 'live_location_disabled');
  }
  const b = body && typeof body === 'object' ? body : {};
  const coord = (v, max, field) => {
    if (typeof v !== 'number' || !Number.isFinite(v) || Math.abs(v) > max) throw badRequest(`${field} نامعتبر است`, 'bad_location');
    return v;
  };
  const lat = coord(b.lat, 90, 'عرض');
  const lng = coord(b.lng, 180, 'طول');
  const accuracyM = optInt(b.accuracyM, 'دقت', { min: 0, max: 100_000 });
  const deviceTime = optInt(b.deviceTime, 'زمانِ دستگاه', { min: 0, max: 4102444800000 });
  return tx(async (c) => {
    const a = await onTheWayOf(c, shopId, assignmentId, actor);
    const at = now();
    const r = await c.query(
      `INSERT INTO delivery_locations (assignment_id, shop_id, lat, lng, accuracy_m, device_time, server_time)
       VALUES ($1,$2,$3,$4,$5,$6,$7)
       ON CONFLICT (assignment_id) DO UPDATE SET lat=EXCLUDED.lat, lng=EXCLUDED.lng, accuracy_m=EXCLUDED.accuracy_m,
              device_time=EXCLUDED.device_time, server_time=EXCLUDED.server_time
        WHERE delivery_locations.server_time <= $8`,
      [a.id, shopId, lat, lng, accuracyM, deviceTime, at, at - LOCATION_MIN_INTERVAL_MS]
    );
    await purgeLocations((t, p) => c.query(t, p));
    return { accepted: r.rowCount > 0, serverTime: at };
  });
}

/** «تقریباً N دقیقهٔ دیگر» از خودِ پیک — فقط با پرچمِ نمای مشتری و فقط «در راه». */
async function putEta(shopId, assignmentId, body, actor) {
  if (!(await orders.flagOf(shopId, VIEW_FLAG))) throw notFound('نمای پیک برای مشتری روشن نیست', 'customer_view_disabled');
  const b = body && typeof body === 'object' ? body : {};
  const minutes = int(b.minutes, 'دقیقه', { min: 1, max: MAX_ETA_MINUTES });
  return tx(async (c) => {
    const a = await onTheWayOf(c, shopId, assignmentId, actor);
    const etaAt = now() + minutes * 60_000;
    await c.query('UPDATE delivery_assignments SET eta_at=$2 WHERE id=$1', [a.id, etaAt]);
    return { etaAt };
  });
}

module.exports = {
  VIEW_FLAG, LOCATION_FLAG, ON_THE_WAY, LOCATION_TTL_MS, LOCATION_MIN_INTERVAL_MS, customerView, putLocation, putEta, purgeLocations,
};
