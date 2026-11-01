'use strict';
/**
 * چند سفارش در یک مسیر با ترتیبِ پیشنهادی — DLV-04 (v3 §۶.۱۸)، پشتِ پرچمِ خاموشِ `shop.delivery_route`.
 *
 *   ترتیب ── «نزدیک‌ترین بعدی» از نقطهٔ شروع (موقعیتِ همین لحظهٔ پیک، وگرنه نخستین مأموریتِ پین‌دار به
 *            ترتیبِ سپردن)، سپس بهبودِ 2-opt تا وقتی کوتاه‌تر می‌شود. فاصله‌ها عددِ صحیحِ متر (`distanceM` ِ
 *            `courier-checks`) ⇒ تساوی فقط با جای ورودی شکسته می‌شود و Kotlin همان ترتیب را می‌سازد.
 *   ایستگاه ─ مأموریتِ زندهٔ خودِ پیک پس از «قبول» و پیش از پایان (`assigned`/`picked_up`/`moving`/`arrived`).
 *            بی پین ⇒ ته، به همان ترتیبِ سپردن (`unpinned`).
 *
 * ⛔ فقط پیشنهاد است: هیچ وضعیتی عوض نمی‌شود و ترتیبِ فهرستِ مأموریت‌ها همان می‌ماند. موقعیتِ پیک فقط برای
 * همین پاسخ خوانده می‌شود و هیچ‌جا نمی‌ماند (DLV-16). منطقِ خالص دوقلوی `core/courier/RoutePlan.kt` است و هر
 * دو با بردارِ `core/test-vectors/courier-route-v1.json` سنجیده می‌شوند.
 */
const { distanceM } = require('./courier-checks');
const { badRequest } = require('../middleware/errors');

const FLAG = 'shop.delivery_route';
const STOP_STATUSES = Object.freeze(['assigned', 'picked_up', 'moving', 'arrived']);
const MAX_PASSES = 50;

/**
 * @param {{lat:number,lng:number}|null} start
 * @param {Array<{lat:number,lng:number}|null>} pins  به ترتیبِ سپردن
 * @returns {{order:number[], legsM:number[], totalM:number, unpinned:number[]}}
 */
function plan(start, pins) {
  const idx = [];
  const unpinned = [];
  pins.forEach((p, i) => (p ? idx : unpinned).push(i));
  if (!idx.length) return { order: [], legsM: [], totalM: 0, unpinned };
  const d = (a, b) => distanceM(pins[a], pins[b]);
  const fromStart = (a) => (start ? distanceM(start, pins[a]) : 0);

  //  ۱) نزدیک‌ترین بعدی — تساوی با جای ورودی
  const left = new Set(idx);
  const order = [];
  let cur = -1;
  if (!start) { cur = idx[0]; order.push(cur); left.delete(cur); }
  while (left.size) {
    let best = -1;
    let bestD = Infinity;
    for (const i of idx) {
      if (!left.has(i)) continue;
      const di = cur === -1 ? fromStart(i) : d(cur, i);
      if (di < bestD) { best = i; bestD = di; }
    }
    order.push(best);
    left.delete(best);
    cur = best;
  }

  //  ۲) 2-opt روی مسیرِ باز: برگرداندنِ [i..j]؛ نخستین بهبود پذیرفته، تا وقتی کوتاه‌تر شود
  const n = order.length;
  const prevOf = (i) => (i > 0 ? order[i - 1] : -1);
  const edge = (a, b) => (a === -1 ? (start ? fromStart(b) : 0) : d(a, b));
  for (let pass = 0; pass < MAX_PASSES; pass++) {
    let improved = false;
    for (let i = 0; i < n - 1 && !improved; i++) {
      for (let j = i + 1; j < n && !improved; j++) {
        const p = prevOf(i);
        const nx = j + 1 < n ? order[j + 1] : null;
        const before = edge(p, order[i]) + (nx === null ? 0 : d(order[j], nx));
        const after = edge(p, order[j]) + (nx === null ? 0 : d(order[i], nx));
        if (after < before) {
          const seg = order.slice(i, j + 1).reverse();
          order.splice(i, seg.length, ...seg);
          improved = true;
        }
      }
    }
    if (!improved) break;
  }

  const legsM = order.map((s, k) => (k === 0 ? fromStart(s) : d(order[k - 1], s)));
  return { order, legsM, totalM: legsM.reduce((a, b) => a + b, 0), unpinned };
}

const coord = (v, lim) => Number.isFinite(v) && Math.abs(v) <= lim;

/** `?lat=&lng=` ⇒ نقطهٔ شروع یا null؛ نیمه یا نامعتبر ⇒ ۴۰۰ */
function startOf(query) {
  const q = query || {};
  if ((q.lat === undefined || q.lat === '') && (q.lng === undefined || q.lng === '')) return null;
  const lat = Number(q.lat);
  const lng = Number(q.lng);
  if (!coord(lat, 90) || !coord(lng, 180)) throw badRequest('موقعیت معتبر نیست', 'bad_position');
  return { lat, lng };
}

/** از فهرستِ مأموریت‌های زندهٔ پیک (`delivery.missions`) ⇒ پاسخِ `GET /api/courier/route` */
function routeOf(items, start) {
  const stops = items.filter((x) => x.live && STOP_STATUSES.includes(x.status));
  const pins = stops.map((x) => (x.address && x.address.lat !== undefined ? { lat: x.address.lat, lng: x.address.lng } : null));
  const p = plan(start, pins);
  const brief = (x) => ({ assignmentId: x.id, orderNo: x.orderNo, status: x.status, landmark: x.address?.landmark || '' });
  return {
    from: start ? 'position' : 'first_stop',
    stops: p.order.map((i, k) => ({ ...brief(stops[i]), seq: k + 1, legM: p.legsM[k] })),
    unpinned: p.unpinned.map((i) => brief(stops[i])),
    totalM: p.totalM,
  };
}

module.exports = { FLAG, STOP_STATUSES, plan, startOf, routeOf };
