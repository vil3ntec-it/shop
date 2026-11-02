'use strict';
/**
 * پیش‌شرط‌های رویدادِ پیکِ دکان — DLV-07 و DLV-17 (v3 §۶.۱۸)، هر کدام پشتِ پرچمِ خاموشِ خودش.
 *
 *   کیسه‌ها ──── `shop.delivery_bag_scan`: «بسته را گرفتم» (`picked_up`) ِ پیک فقط با برچسبِ **همهٔ** کیسه‌های
 *                بسته (`VLNB-<سفارش>-<کیسه>-<همه>`، PCK-09) پذیرفته است: کیسهٔ سفارشِ دیگر (`bag_wrong_order`)،
 *                برچسبِ کهنه با تعدادِ دیگر (`label_mismatch`) و کیسهٔ جامانده (`bags_incomplete`، با `missing`) رد.
 *                سفارشی که با جمع‌آوری (PCK) بسته نشده کیسهٔ شمرده ندارد ⇒ چیزی برای اسکن نیست.
 *   نزدیکِ پین ─ `shop.delivery_near_pin` (+ `shop.delivery_near_pin.max_m`، عددِ صحیحِ ۲۰ تا ۵۰۰۰ متر، نبود یا
 *                نامعتبر ⇒ ۳۰۰): «تحویل شد» ِ پیک فقط با موقعیتِ همان لحظه (`position: {lat, lng}`) و در همان
 *                فاصله از پینِ سفارش. سفارشِ بی پین سنجیده نمی‌شود. ⛔ خودِ موقعیت هیچ‌جا نگه داشته نمی‌شود؛
 *                فقط فاصلهٔ گردشده در رویداد (DLV-16: موقعیت فقط در زمانِ مأموریت).
 *
 * ⛔ فقط رویدادِ پیک: کارمند (تأییدِ دستیِ مدیر با توضیح، گوشیِ خاموشِ پیک) از این دو نمی‌گذرد. سنجش پیش از
 * کدِ تحویل است، پس پیکِ دور از پین تلاشِ کد را هم نمی‌سوزاند. منطقِ خالص دوقلوی `core/courier/CourierChecks.kt`
 * است و هر دو با بردارِ `core/test-vectors/courier-checks-v1.json` سنجیده می‌شوند.
 *
 * جدا از `delivery.js`، تا آن فایل از سقفِ اندازه‌اش در `tools/quality/baseline.json` بزرگ‌تر نشود.
 */
const { many } = require('../db');
const orders = require('./orders');
const { parseLabel } = require('./pick-shelf');
const { badRequest } = require('../middleware/errors');

const BAG_FLAG = 'shop.delivery_bag_scan';
const PIN_FLAG = 'shop.delivery_near_pin';
const MAX_M_KEY = 'shop.delivery_near_pin.max_m';
const DEFAULT_MAX_M = 300;
const MIN_M = 20;
const MAX_M = 5000;
const MAX_LABELS = 100;
const EARTH_M = 6371008.8;
const BEFORE_PICKUP = Object.freeze(['requested', 'assigned']);

const REJECT = Object.freeze({
  bags_required: [422, 'برچسبِ کیسه‌های این سفارش را اسکن کنید'],
  bad_label: [422, 'برچسبِ کیسه شناخته نشد'],
  bag_wrong_order: [422, 'این کیسه مالِ سفارشِ دیگری است'],
  label_mismatch: [409, 'این برچسب با تعدادِ کیسه‌های بسته نمی‌خواند'],
  bags_incomplete: [422, 'همهٔ کیسه‌ها اسکن نشده‌اند'],
  position_required: [422, 'موقعیتِ همین لحظه لازم است'],
  too_far_from_pin: [422, 'از پینِ مشتری دور هستید؛ «تحویل شد» فقط نزدیکِ پین ثبت می‌شود'],
});

// ════════════════════════════════════════════════════════════════════
//  منطقِ خالص (همان بردار با Kotlin)
// ════════════════════════════════════════════════════════════════════

/** برچسب‌های اسکن‌شده در برابرِ سفارشِ `orderNo` با `bags` کیسه. تکرار بی‌اثر؛ نخستین خطا برنده. */
function bagsCheck(labels, { orderNo, bags }) {
  const seen = new Set();
  for (const raw of labels) {
    let l;
    try { l = parseLabel(raw); } catch { return { ok: false, code: 'bad_label' }; }
    if (l.orderNo !== Number(orderNo)) return { ok: false, code: 'bag_wrong_order' };
    if (l.of !== Number(bags)) return { ok: false, code: 'label_mismatch' };
    seen.add(l.bag);
  }
  const missing = [];
  for (let i = 1; i <= bags; i++) if (!seen.has(i)) missing.push(i);
  if (missing.length) return { ok: false, code: seen.size ? 'bags_incomplete' : 'bags_required', missing };
  return { ok: true, scanned: seen.size };
}

/** فاصلهٔ دو نقطه روی کره (هاورساین)، متر، گرد به عددِ صحیح. */
function distanceM(a, b) {
  const rad = (x) => (x * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return Math.round(2 * EARTH_M * Math.asin(Math.min(1, Math.sqrt(h))));
}

/** «تحویل شد» نزدیکِ پین؟ بی پین ⇒ سنجیده نمی‌شود. */
function nearPin(position, pin, maxM) {
  if (!pin) return { ok: true, checked: false };
  if (!position) return { ok: false, code: 'position_required' };
  const d = distanceM(position, pin);
  return d <= maxM ? { ok: true, checked: true, distanceM: d } : { ok: false, code: 'too_far_from_pin', distanceM: d, maxM };
}

/** مقدارِ `max_m` ِ دکان: عددِ صحیحِ ۲۰ تا ۵۰۰۰، وگرنه پیش‌فرض. */
const maxMOf = (v) => (Number.isSafeInteger(v) && v >= MIN_M && v <= MAX_M ? v : DEFAULT_MAX_M);

// ════════════════════════════════════════════════════════════════════
//  ورودی، پرچم و سنجش
// ════════════════════════════════════════════════════════════════════

const coord = (v, lim) => typeof v === 'number' && Number.isFinite(v) && Math.abs(v) <= lim;

/** کلیدهای تازهٔ رویدادِ پیک — فقط وقتی آمده‌اند (اثرِ انگشتِ رویدادهای پیشین عوض نمی‌شود). */
function parseInput(e) {
  const out = {};
  if (e.bags !== undefined && e.bags !== null) {
    if (!Array.isArray(e.bags) || e.bags.length > MAX_LABELS || e.bags.some((x) => typeof x !== 'string' || x.length > 40)) {
      throw badRequest('برچسب‌های کیسه معتبر نیستند', 'bad_bags');
    }
    out.bags = e.bags.map((x) => x.trim());
  }
  if (e.position !== undefined && e.position !== null) {
    const p = e.position;
    if (!p || typeof p !== 'object' || !coord(p.lat, 90) || !coord(p.lng, 180)) throw badRequest('موقعیت معتبر نیست', 'bad_position');
    out.position = { lat: p.lat, lng: p.lng };
  }
  return out;
}

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

/** فاصلهٔ مجازِ دکان؛ مقدارِ خودِ دکان بر `all` جلو می‌افتد. `run` ⇐ اتصالِ تراکنش (مثلِ `orders.flagOf`). */
async function maxMFor(shopId, run = null) {
  const sql = 'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)';
  const params = [MAX_M_KEY, 'all', String(shopId)];
  const rows = run ? (await run(sql, params)).rows : await many(sql, params);
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return maxMOf(v);
}

const reject = (code, data) => ({ code, extra: { http: REJECT[code][0], message: REJECT[code][1], ...(data ? { data } : {}) } });

/**
 * درونِ تراکنشِ `applyEvent`، پس از ماشینِ وضعیت و پیش از کدِ تحویل. ⇒ `{detail}` (گذر) یا `{code, extra}` (رد).
 */
async function verify(c, shopId, a, ev, actor) {
  if (actor.kind !== 'courier' || a.mode !== 'shop_courier') return { detail: {} };
  //  ⛔ همهٔ خواندن‌ها با `c`: رویدادهای هم‌زمانِ پشتِ `FOR UPDATE` ِ مأموریت اتصالِ دومی از استخر نگیرند
  const run = (text, params) => c.query(text, params);
  if (ev.to === 'picked_up' && await orders.flagOf(shopId, BAG_FLAG, run)) {
    const p = (await c.query(
      `SELECT p.bags, o.order_no FROM order_picks p JOIN online_orders o ON o.id = p.order_id
        WHERE p.shop_id=$1 AND p.order_id=$2 AND p.status='packed'`, [shopId, a.order_id]
    )).rows[0];
    if (!p || !(Number(p.bags) > 0)) return { detail: {} };
    const v = bagsCheck(ev.bags || [], { orderNo: Number(p.order_no), bags: Number(p.bags) });
    if (!v.ok) return reject(v.code, { bags: Number(p.bags), ...(v.missing ? { missing: v.missing } : {}) });
    return { detail: { bagsScanned: v.scanned } };
  }
  if (ev.to === 'delivered' && await orders.flagOf(shopId, PIN_FLAG, run)) {
    const o = (await c.query('SELECT address FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, a.order_id])).rows[0];
    const addr = typeof o?.address === 'string' ? decode(o.address) || {} : o?.address || {};
    const pin = coord(addr.lat, 90) && coord(addr.lng, 180) ? { lat: addr.lat, lng: addr.lng } : null;
    const v = nearPin(ev.position || null, pin, await maxMFor(shopId, run));
    if (!v.ok) return reject(v.code, v.distanceM === undefined ? null : { distanceM: v.distanceM, maxM: v.maxM });
    return { detail: v.checked ? { distanceM: v.distanceM } : {} };
  }
  return { detail: {} };
}

/**
 * کارتِ پیک: `bagScan: {bags}` پیش از گرفتنِ بسته و `nearPin: {maxM}` برای مأموریتِ پین‌دار — فقط با پرچم.
 */
async function decorate(shopId, payload) {
  const live = (payload.items || []).filter((x) => x.live);
  if (!live.length) return payload;
  const [bagOn, pinOn] = await Promise.all([orders.flagOf(shopId, BAG_FLAG), orders.flagOf(shopId, PIN_FLAG)]);
  if (bagOn) {
    const ids = live.filter((x) => BEFORE_PICKUP.includes(x.status)).map((x) => x.orderId);
    const rows = ids.length ? await many(
      `SELECT order_id, bags FROM order_picks WHERE shop_id=$1 AND order_id = ANY($2::text[]) AND status='packed' AND bags > 0`, [shopId, ids]
    ) : [];
    const bags = new Map(rows.map((r) => [r.order_id, Number(r.bags)]));
    for (const x of live) if (bags.has(x.orderId) && BEFORE_PICKUP.includes(x.status)) x.bagScan = { bags: bags.get(x.orderId) };
  }
  if (pinOn) {
    const maxM = await maxMFor(shopId);
    for (const x of live) if (x.address && x.address.lat !== undefined) x.nearPin = { maxM };
  }
  return payload;
}

module.exports = {
  BAG_FLAG, PIN_FLAG, MAX_M_KEY, DEFAULT_MAX_M, MIN_M, MAX_M,
  bagsCheck, distanceM, nearPin, maxMOf, parseInput, verify, decorate,
};
