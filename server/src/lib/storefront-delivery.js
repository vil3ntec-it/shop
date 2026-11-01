'use strict';
/**
 * ویترین — ناحیهٔ ارسال و بازهٔ زمان‌بندی (فاز ۴؛ CHK-05/06/07/10/12).
 * شرحِ جدول‌ها: ‎migrations/111_storefront_delivery.sql‎.
 *
 *   ناحیه ── چندضلعی یا دایره؛ هر کدام هزینه، حداقلِ سفارش و «رایگان از…» ِ
 *            خودش را دارد. «داخلِ محدوده» فقط با پینِ مشتری و بی ژئوکدر سنجیده
 *            می‌شود (CHK-10): پینِ بیرون ⇒ ردِ صریح با پیشنهادِ «دریافت از دکان».
 *            بی پین (مرورگرِ بی موقعیت) مشتری ناحیه‌اش را از فهرست برمی‌گزیند
 *            (۲۳.۲: «محل را دستی وارد کند») و سفارش نشانِ `source: customer` دارد.
 *            نخستین ناحیهٔ فعال به ترتیبِ مالک برنده است.
 *            دکانِ بی ناحیه ⇒ همان هزینه و حداقلِ ثابتِ امروز (هیچ تغییری).
 *   بازه ─── قاعدهٔ هفتگی ⇒ بازه‌های چند روزِ آینده به وقتِ محلیِ دکان، بی
 *            روزهای تعطیل، و فقط تا «مهلت» پیش از آغاز. ظرفیت = سفارش‌های زنده
 *            با `scheduled_for` درونِ بازه — درونِ همان تراکنش و قفلِ سفارشِ دکان
 *            شمرده می‌شود، پس دو سفارشِ هم‌زمان از ظرفیت بیرون نمی‌زنند.
 *            به مشتری فقط «هست/پر است/کم مانده» گفته می‌شود، نه عددِ سفارش‌ها.
 *
 *   هزینه ── (مهاجرتِ ۱۳۸) هزینهٔ ارسال = هزینهٔ ناحیه (رایگان از «رایگان از…»)
 *            + هزینهٔ سبدِ کوچک (CHK-06: ناحیه‌ای که آن را دارد سبدِ زیرِ حداقل را
 *            به‌جای رد با همین مبلغ می‌پذیرد) + هزینهٔ بازهٔ برگزیده (CHK-07: «زمانِ
 *            تحویل»؛ فقط «ارسال»). ریزِ هر سه کنارِ همان ناحیه/بازه در JSON ِ سفارش
 *            می‌ماند تا هر سه از همان سبد دیده شوند (CHK-04).
 *
 * ⛔ قیمت و هزینه را سرور می‌گوید؛ عددِ مشتری فقط برای «تغییر کرده؟» است.
 * ⛔ پرچمِ جدا و پیش‌فرض خاموشِ `shop.storefront.zones` (live_config، دکان یا all):
 *    صفحهٔ امروزِ ویترین (server/public/store) ناحیه و بازه را نمی‌شناسد؛ تا
 *    مشتری‌ای که این‌ها را نشان دهد آماده نشده، خاموش ⇒ هزینه و حداقلِ ثابتِ امروز،
 *    بی بازه، و تنظیمِ مالک ۴۰۳. ناحیه‌های ذخیره‌شده با خاموش کردن پاک نمی‌شوند.
 */
const db = require('../db');
const { tx } = db;
const { ulid } = require('./ulid');
const { stripControl } = require('./validate');
const { badRequest, conflict, notFound } = require('../middleware/errors');

const MAX_ZONES = 20;
const MAX_POINTS = 100;
const MAX_RULES = 40;
const MAX_FEE_MINOR = 100_000_000;
const MAX_PRICE_MINOR = 100_000_000_000;
const MIN_RADIUS_M = 50;
const MAX_RADIUS_M = 50_000;
const DAY_MS = 24 * 60 * 60 * 1000;
//  این وضعیت‌ها بازه را آزاد کرده‌اند
const RELEASED = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled'];
const ID_RE = /^[0-9A-Z]{26}$/;
const FLAG_KEY = 'shop.storefront.zones';

/** ناحیه و بازه برای این دکان روشن است؟ (پیش‌فرض خاموش) */
const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG_KEY);

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const text = (v, max) => stripControl(str(v)).slice(0, max);
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const asJson = (x, def) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x); } catch { return def; }
};
function int(v, field, { min = 0, max = Number.MAX_SAFE_INTEGER } = {}) {
  const n = typeof v === 'string' && /^-?\d+$/.test(v.trim()) ? Number(v) : v;
  if (!Number.isSafeInteger(n) || n < min || n > max) throw badRequest(`${field} معتبر نیست`, 'bad_delivery_settings');
  return n;
}
function num(v, field, min, max) {
  const n = typeof v === 'string' ? Number(v.trim()) : v;
  if (typeof n !== 'number' || !Number.isFinite(n) || n < min || n > max) {
    throw badRequest(`${field} معتبر نیست`, 'bad_delivery_settings');
  }
  return Math.round(n * 1e6) / 1e6;
}
const rowsOf = async (run, sql, params) => (await (run || db.query)(sql, params)).rows;

// ════════════════════════════════════════════════════════════════════
//  هندسه — بی سرویسِ بیرونی
// ════════════════════════════════════════════════════════════════════

/** نقطه درونِ چندضلعیِ [[lat,lng],…] (پرتو؛ لبه‌ها در مقیاسِ یک شهر تخت فرض می‌شوند). */
function inPolygon(lat, lng, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [yi, xi] = poly[i];
    const [yj, xj] = poly[j];
    if ((yi > lat) !== (yj > lat) && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

/** فاصلهٔ دو نقطه به متر (هاورسین). */
function distanceM(lat1, lng1, lat2, lng2) {
  const R = 6_371_008.8;
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

function contains(z, lat, lng) {
  if (z.kind === 'radius') return distanceM(z.centerLat, z.centerLng, lat, lng) <= z.radiusM;
  return inPolygon(lat, lng, z.polygon);
}

/** نخستین ناحیهٔ فعال (به ترتیبِ مالک) که پین درونش است — یا null. */
function zoneAt(zones, lat, lng) {
  return zones.find((z) => z.active && contains(z, lat, lng)) || null;
}

/*
 *  ⛔ حریمِ جای دکان: «درون/بیرون» ِ پینِ خام یک پیشگوست — با جست‌وجوی دودویی
 *  لبهٔ ناحیه تا یک متر و مرکزِ دایره (= جای دکان) دقیق پیدا می‌شد. پس پین
 *  هرگز خام سنجیده نمی‌شود: اول به مرکزِ خانه‌ای از شبکهٔ ثابتِ جهانی (لنگر
 *  صفر/صفر، نه جای دکان) با ضلعِ `GRID_M` می‌چسبد و پاسخ فقط تابعِ همان خانه
 *  است ⇒ هیچ کاوشی دقیق‌تر از یک خانه نمی‌شود. همین سنجش در ثبتِ سفارش هم
 *  هست تا «داخلِ محدوده» ِ پیش از ثبت و خودِ ثبت یکی باشند. پینِ خامِ مشتری
 *  برای پیک در نشانیِ سفارش می‌ماند؛ فاصله و لبه هرگز گفته نمی‌شود.
 */
const GRID_M = 300;
//  یک درجهٔ عرض روی همان کرهٔ `distanceM` (۲πR/۳۶۰ ≈ ۱۱۱۱۹۵ متر) ⇒ ارتفاعِ خانه دست‌کم ۳۰۰ متر
const M_PER_DEG_LAT = 111_195;

/** مرکزِ خانهٔ شبکه‌ای که پین در آن است. ردیف‌ها هم‌ارتفاع؛ پهنای خانه در هر ردیف با عرضِ مرکزِ همان ردیف. */
function snapPin(lat, lng) {
  const dLat = GRID_M / M_PER_DEG_LAT;
  const cLat = Math.max(-90, Math.min(90, (Math.floor(lat / dLat) + 0.5) * dLat));
  const dLng = GRID_M / (M_PER_DEG_LAT * Math.max(0.01, Math.cos((cLat * Math.PI) / 180)));
  const cLng = Math.max(-180, Math.min(180, (Math.floor(lng / dLng) + 0.5) * dLng));
  return { lat: cLat, lng: cLng };
}

/** ناحیهٔ پینِ مشتری — همیشه با مرکزِ خانهٔ شبکه، هرگز با مختصاتِ خام. */
function zoneForPin(zones, lat, lng) {
  const c = snapPin(lat, lng);
  return zoneAt(zones, c.lat, c.lng);
}

// ════════════════════════════════════════════════════════════════════
//  ردیف ⇄ نما
// ════════════════════════════════════════════════════════════════════

const nOrNull = (v) => (v === null || v === undefined ? null : Number(v));

function zoneView(r) {
  return {
    id: r.id, name: r.name, kind: r.kind,
    polygon: r.kind === 'polygon' ? asJson(r.polygon, []) : [],
    centerLat: nOrNull(r.center_lat), centerLng: nOrNull(r.center_lng), radiusM: nOrNull(r.radius_m),
    feeMinor: Number(r.fee_minor), minOrderMinor: Number(r.min_order_minor), freeOverMinor: nOrNull(r.free_over_minor),
    smallBasketFeeMinor: nOrNull(r.small_basket_fee_minor),
    active: r.active === true, sort: Number(r.sort),
  };
}

/** آن‌چه مشتری از ناحیه می‌بیند — بی مختصات. */
function publicZone(z) {
  return {
    id: z.id, name: z.name, feeMinor: z.feeMinor, minOrderMinor: z.minOrderMinor, freeOverMinor: z.freeOverMinor,
    //  CHK-06: null ⇒ زیرِ حداقل پذیرفته نیست؛ عدد ⇒ با همین هزینه پذیرفته است
    smallBasketFeeMinor: z.smallBasketFeeMinor ?? null,
  };
}

const bitsToDays = (b) => [0, 1, 2, 3, 4, 5, 6].filter((d) => (b >> d) & 1);

function ruleView(r) {
  return {
    id: r.id, fulfillment: r.fulfillment, weekdays: bitsToDays(Number(r.weekdays)),
    startMin: Number(r.start_min), endMin: Number(r.end_min), capacity: Number(r.capacity),
    cutoffMin: Number(r.cutoff_min), feeMinor: Number(r.fee_minor || 0), active: r.active === true, sort: Number(r.sort),
  };
}

async function zonesOf(shopId, run) {
  return (await rowsOf(run, 'SELECT * FROM storefront_zones WHERE shop_id = $1 ORDER BY sort, id', [shopId])).map(zoneView);
}

async function rulesOf(shopId, run) {
  return (await rowsOf(run, 'SELECT * FROM storefront_slot_rules WHERE shop_id = $1 ORDER BY sort, id', [shopId])).map(ruleView);
}

// ════════════════════════════════════════════════════════════════════
//  ورودیِ مالک
// ════════════════════════════════════════════════════════════════════

function parseZone(raw, i) {
  const z = obj(raw);
  const at = `ناحیهٔ ${i + 1}`;
  const name = text(z.name, 60);
  if (!name) throw badRequest(`${at}: نام لازم است`, 'bad_zone');
  const kind = str(z.kind);
  const out = {
    name, kind, polygon: [], centerLat: null, centerLng: null, radiusM: null,
    feeMinor: int(z.feeMinor ?? 0, `${at}: هزینهٔ ارسال`, { max: MAX_FEE_MINOR }),
    minOrderMinor: int(z.minOrderMinor ?? 0, `${at}: حداقلِ سفارش`, { max: MAX_PRICE_MINOR }),
    freeOverMinor: z.freeOverMinor === null || z.freeOverMinor === undefined || z.freeOverMinor === ''
      ? null : int(z.freeOverMinor, `${at}: ارسالِ رایگان از`, { min: 1, max: MAX_PRICE_MINOR }),
    //  CHK-06 — نیامدنِ کلید (برنامهٔ پیش از ۱۳۸) ⇒ undefined: مقدارِ پیشینِ همین ناحیه می‌ماند (saveSettings)؛
    //  null یا '' ⇒ پاک (زیرِ حداقل رد)
    smallBasketFeeMinor: !Object.prototype.hasOwnProperty.call(z, 'smallBasketFeeMinor') ? undefined
      : z.smallBasketFeeMinor === null || z.smallBasketFeeMinor === ''
        ? null : int(z.smallBasketFeeMinor, `${at}: هزینهٔ سبدِ کوچک`, { min: 1, max: MAX_FEE_MINOR }),
    active: z.active === undefined ? true : z.active === true,
  };
  if (out.smallBasketFeeMinor && out.minOrderMinor === 0) {
    throw badRequest(`${at}: هزینهٔ سبدِ کوچک بی حداقلِ سفارش معنا ندارد`, 'bad_zone');
  }
  if (kind === 'radius') {
    out.centerLat = num(z.centerLat, `${at}: عرضِ مرکز`, -90, 90);
    out.centerLng = num(z.centerLng, `${at}: طولِ مرکز`, -180, 180);
    out.radiusM = int(z.radiusM, `${at}: شعاع (متر)`, { min: MIN_RADIUS_M, max: MAX_RADIUS_M });
  } else if (kind === 'polygon') {
    if (!Array.isArray(z.polygon) || z.polygon.length < 3 || z.polygon.length > MAX_POINTS) {
      throw badRequest(`${at}: چندضلعی ۳ تا ${MAX_POINTS} نقطه می‌خواهد`, 'bad_zone');
    }
    out.polygon = z.polygon.map((p) => {
      if (!Array.isArray(p) || p.length !== 2) throw badRequest(`${at}: هر نقطه [عرض، طول] است`, 'bad_zone');
      return [num(p[0], `${at}: عرض`, -90, 90), num(p[1], `${at}: طول`, -180, 180)];
    });
    //  چندضلعیِ بی مساحت (همهٔ نقطه‌ها روی یک خط) هیچ پینی را نمی‌گیرد ⇒ اشتباهِ ورود است
    let area = 0;
    for (let k = 0, j = out.polygon.length - 1; k < out.polygon.length; j = k++) {
      area += out.polygon[j][1] * out.polygon[k][0] - out.polygon[k][1] * out.polygon[j][0];
    }
    if (Math.abs(area) < 1e-10) throw badRequest(`${at}: چندضلعی مساحت ندارد`, 'bad_zone');
  } else {
    throw badRequest(`${at}: نوع باید چندضلعی یا دایره باشد`, 'bad_zone');
  }
  return out;
}

function parseRule(raw, i) {
  const r = obj(raw);
  const at = `بازهٔ ${i + 1}`;
  const fulfillment = str(r.fulfillment);
  if (!['delivery', 'pickup'].includes(fulfillment)) throw badRequest(`${at}: ارسال یا دریافت از دکان؟`, 'bad_slot_rule');
  if (!Array.isArray(r.weekdays) || !r.weekdays.length) throw badRequest(`${at}: دست‌کم یک روز`, 'bad_slot_rule');
  let weekdays = 0;
  for (const d of r.weekdays) weekdays |= 1 << int(d, `${at}: روز`, { min: 0, max: 6 });
  const startMin = int(r.startMin, `${at}: آغاز`, { min: 0, max: 1439 });
  const endMin = int(r.endMin, `${at}: پایان`, { min: 1, max: 1440 });
  if (endMin <= startMin) throw badRequest(`${at}: پایان باید پس از آغاز باشد`, 'bad_slot_rule');
  //  CHK-07 — نیامدنِ کلید (برنامهٔ پیش از ۱۳۸) ⇒ undefined: هزینهٔ پیشینِ همین بازه می‌ماند (saveSettings)
  const feeMinor = !Object.prototype.hasOwnProperty.call(r, 'feeMinor') ? undefined
    : int(r.feeMinor ?? 0, `${at}: هزینهٔ بازه`, { max: MAX_FEE_MINOR });
  if (feeMinor && fulfillment !== 'delivery') throw badRequest(`${at}: «دریافت از دکان» هزینهٔ بازه ندارد`, 'bad_slot_rule');
  return {
    fulfillment, weekdays, startMin, endMin,
    capacity: int(r.capacity, `${at}: ظرفیت`, { min: 1, max: 1000 }),
    cutoffMin: int(r.cutoffMin ?? 60, `${at}: مهلتِ سفارش`, { min: 0, max: 10080 }),
    feeMinor,
    active: r.active === undefined ? true : r.active === true,
  };
}

/*
 *  ⛔ برنامهٔ پیش از مهاجرتِ ۱۳۸ کلیدِ هزینهٔ سبدِ کوچک/بازه را نمی‌شناسد و با هر
 *  «ذخیره» فهرستِ کامل را بی آن می‌فرستد؛ پاک شدنِ بی‌صدای تنظیمِ صاحب دکان از
 *  دستگاهِ دیگر بدترین خطاست (همان قاعدهٔ newer-fields: «مقدارِ کهنه می‌ماند»).
 *  پس کلیدِ نیامده مقدارِ پیشینِ همان ناحیه (هم‌نام) یا همان بازه (همان روش، روزها و
 *  ساعت) را نگه می‌دارد؛ نبودِ همتا ⇒ بی هزینه. فقط null/۰ ِ صریح پاک می‌کند.
 */
function keepZoneFee(z, prev) {
  if (z.smallBasketFeeMinor !== undefined) return z.smallBasketFeeMinor;
  const p = prev.find((x) => x.name === z.name);
  return p && p.smallBasketFeeMinor && z.minOrderMinor > 0 ? p.smallBasketFeeMinor : null;
}

function keepRuleFee(r, prev) {
  if (r.feeMinor !== undefined) return r.feeMinor;
  if (r.fulfillment !== 'delivery') return 0;
  const p = prev.find((x) => x.fulfillment === r.fulfillment && x.weekdays === r.weekdays
    && x.startMin === r.startMin && x.endMin === r.endMin);
  return p ? p.feeMinor : 0;
}

/** دو بازهٔ فعالِ یک روش در یک روز روی هم نیفتند — وگرنه «ظرفیت» دو معنا دارد. */
function checkOverlap(rules) {
  const act = rules.filter((r) => r.active);
  for (let a = 0; a < act.length; a++) {
    for (let b = a + 1; b < act.length; b++) {
      const x = act[a];
      const y = act[b];
      if (x.fulfillment === y.fulfillment && (x.weekdays & y.weekdays) && x.startMin < y.endMin && y.startMin < x.endMin) {
        throw badRequest('دو بازهٔ یک روش در یک روز روی هم افتاده‌اند', 'slot_overlap');
      }
    }
  }
}

async function settingsOf(shopId) {
  const [zones, rules, sf] = await Promise.all([
    zonesOf(shopId), rulesOf(shopId), db.one('SELECT allow_asap, slot_days FROM storefronts WHERE shop_id = $1', [shopId]),
  ]);
  return {
    zones, slotRules: rules,
    allowAsap: sf ? sf.allow_asap !== false : true,
    slotDays: sf ? Number(sf.slot_days) : 3,
  };
}

/**
 * ذخیرهٔ ناحیه‌ها و بازه‌ها — هر فهرستِ آمده کامل جایگزین می‌شود (اتمی)؛
 * فهرستِ نیامده دست نمی‌خورد. ویترین باید پیش‌تر ساخته شده باشد.
 */
async function saveSettings(shopId, body, userId) {
  const b = obj(body);
  const has = (k) => Object.prototype.hasOwnProperty.call(b, k);
  let zones = null;
  let rules = null;
  if (has('zones')) {
    if (!Array.isArray(b.zones) || b.zones.length > MAX_ZONES) throw badRequest(`حداکثر ${MAX_ZONES} ناحیه`, 'bad_zone');
    zones = b.zones.map(parseZone);
  }
  if (has('slotRules')) {
    if (!Array.isArray(b.slotRules) || b.slotRules.length > MAX_RULES) throw badRequest(`حداکثر ${MAX_RULES} بازه`, 'bad_slot_rule');
    rules = b.slotRules.map(parseRule);
    checkOverlap(rules);
  }
  const allowAsap = has('allowAsap') ? b.allowAsap === true : null;
  if (has('allowAsap') && typeof b.allowAsap !== 'boolean') throw badRequest('«هرچه زودتر» باید بله یا نه باشد', 'bad_delivery_settings');
  const slotDays = has('slotDays') ? int(b.slotDays, 'روزهای پیشِ رو', { min: 1, max: 14 }) : null;

  await tx(async (c) => {
    const sf = (await c.query('SELECT shop_id FROM storefronts WHERE shop_id = $1 FOR UPDATE', [shopId])).rows[0];
    if (!sf) throw notFound('اول ویترین را بسازید', 'storefront_missing');
    const at = db.now();
    const run = (t, p) => c.query(t, p);
    if (zones) {
      const prev = await zonesOf(shopId, run);
      await c.query('DELETE FROM storefront_zones WHERE shop_id = $1', [shopId]);
      for (const [i, z] of zones.entries()) {
        await c.query(
          `INSERT INTO storefront_zones (id, shop_id, name, kind, polygon, center_lat, center_lng, radius_m,
             fee_minor, min_order_minor, free_over_minor, small_basket_fee_minor, active, sort, updated_by,
             created_at, updated_at)
           VALUES ($1,$2,$3,$4,$5::jsonb,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$16)`,
          [ulid(at), shopId, z.name, z.kind, JSON.stringify(z.polygon), z.centerLat, z.centerLng, z.radiusM,
            z.feeMinor, z.minOrderMinor, z.freeOverMinor, keepZoneFee(z, prev), z.active, i, userId || '', at]
        );
      }
    }
    if (rules) {
      const prev = (await rowsOf(run, 'SELECT * FROM storefront_slot_rules WHERE shop_id = $1', [shopId]))
        .map((r) => ({ fulfillment: r.fulfillment, weekdays: Number(r.weekdays), startMin: Number(r.start_min),
          endMin: Number(r.end_min), feeMinor: Number(r.fee_minor || 0) }));
      await c.query('DELETE FROM storefront_slot_rules WHERE shop_id = $1', [shopId]);
      for (const [i, r] of rules.entries()) {
        await c.query(
          `INSERT INTO storefront_slot_rules (id, shop_id, fulfillment, weekdays, start_min, end_min, capacity,
             cutoff_min, fee_minor, active, sort, updated_by, created_at, updated_at)
           VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$13)`,
          [ulid(at), shopId, r.fulfillment, r.weekdays, r.startMin, r.endMin, r.capacity, r.cutoffMin,
            keepRuleFee(r, prev), r.active, i, userId || '', at]
        );
      }
    }
    if (allowAsap !== null || slotDays !== null) {
      await c.query(
        `UPDATE storefronts SET allow_asap = COALESCE($2, allow_asap), slot_days = COALESCE($3, slot_days),
           updated_by = $4, updated_at = $5 WHERE shop_id = $1`,
        [shopId, allowAsap, slotDays, userId || '', at]
      );
    }
  });
  return settingsOf(shopId);
}

// ════════════════════════════════════════════════════════════════════
//  زمان — به وقتِ محلیِ دکان، مستقل از ساعتِ گوشیِ مشتری
// ════════════════════════════════════════════════════════════════════

function partsAt(at, tz) {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).formatToParts(new Date(at)).map((x) => [x.type, x.value]));
  return { y: Number(p.year), m: Number(p.month), d: Number(p.day), h: Number(p.hour) % 24, mi: Number(p.minute) };
}

/** اختلافِ وقتِ محلی با UTC در لحظهٔ `at` (میلی‌ثانیه). */
function offsetAt(at, tz) {
  const p = partsAt(at, tz);
  return Date.UTC(p.y, p.m - 1, p.d, p.h, p.mi) - Math.floor(at / 60000) * 60000;
}

/** «YYYY-MM-DD» + دقیقه از نیمه‌شبِ محلی ⇒ لحظهٔ واقعی (با تغییرِ ساعتِ تابستانی هم درست). */
function epochOf(date, minutes, tz) {
  const [y, m, d] = date.split('-').map(Number);
  const wall = Date.UTC(y, m - 1, d) + minutes * 60000;
  const first = wall - offsetAt(wall, tz);
  return wall - offsetAt(first, tz);
}

function localDate(at, tz) {
  const p = partsAt(at, tz);
  return `${p.y}-${String(p.m).padStart(2, '0')}-${String(p.d).padStart(2, '0')}`;
}

function addDays(date, n) {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
}

const dowOf = (date) => new Date(`${date}T00:00:00Z`).getUTCDay();

// ════════════════════════════════════════════════════════════════════
//  بازه‌های پیشِ رو و ظرفیت
// ════════════════════════════════════════════════════════════════════

/** بازه‌های بازِ یک روش در `slot_days` روزِ آینده (بی تعطیلی، فقط پیش از مهلت). */
function slotsOf(sf, rules, fulfillment, at) {
  const tz = sf.timezone || 'Asia/Kabul';
  const closed = new Set(asJson(sf.closed_dates, []));
  const days = Math.min(14, Math.max(1, Number(sf.slot_days) || 3));
  const mine = rules.filter((r) => r.active && r.fulfillment === fulfillment);
  if (!mine.length) return [];
  const today = localDate(at, tz);
  const out = [];
  for (let i = 0; i < days; i++) {
    const date = addDays(today, i);
    if (closed.has(date)) continue;
    const dow = dowOf(date);
    for (const r of mine) {
      if (!r.weekdays.includes(dow)) continue;
      const startsAt = epochOf(date, r.startMin, tz);
      const endsAt = epochOf(date, r.endMin, tz);
      if (at >= startsAt - r.cutoffMin * 60000) continue;
      out.push({
        ruleId: r.id, date, startMin: r.startMin, endMin: r.endMin, startsAt, endsAt, capacity: r.capacity,
        feeMinor: r.feeMinor || 0,
      });
    }
  }
  return out.sort((a, b) => a.startsAt - b.startsAt);
}

/** همان بازه‌ها با «پر شده» — شمارش از سفارش‌های زندهٔ همان روش. */
async function slotsWithLoad(sf, fulfillment, at, { run, rules } = {}) {
  const slots = slotsOf(sf, rules || await rulesOf(sf.shop_id, run), fulfillment, at);
  if (!slots.length) return [];
  const from = slots[0].startsAt;
  const to = Math.max(...slots.map((s) => s.endsAt));
  const rows = await rowsOf(run,
    `SELECT scheduled_for AS t, COUNT(*)::int AS n FROM online_orders
      WHERE shop_id = $1 AND fulfillment = $2 AND scheduled_for >= $3 AND scheduled_for < $4
        AND status <> ALL($5::text[])
      GROUP BY scheduled_for`,
    [sf.shop_id, fulfillment, from, to, RELEASED]);
  return slots.map((s) => {
    const booked = rows.reduce((n, r) => n + (Number(r.t) >= s.startsAt && Number(r.t) < s.endsAt ? Number(r.n) : 0), 0);
    return { ...s, booked, remaining: Math.max(0, s.capacity - booked) };
  });
}

/** نمای مشتری — بی عددِ سفارش‌ها. */
function publicSlot(s) {
  return {
    startsAt: s.startsAt, endsAt: s.endsAt, date: s.date, startMin: s.startMin, endMin: s.endMin,
    available: s.remaining > 0, few: s.remaining > 0 && s.remaining <= 2,
    //  CHK-07: هزینهٔ اضافهٔ همین بازه (۰ = بی هزینهٔ اضافه)
    feeMinor: s.feeMinor || 0,
  };
}

// ════════════════════════════════════════════════════════════════════
//  نمای عمومی
// ════════════════════════════════════════════════════════════════════

/** برای پروفایلِ ویترین: نام و هزینهٔ ناحیه‌ها و این‌که کدام روش بازه دارد. */
async function publicInfo(sf) {
  if (!(await flagOn(sf.shop_id))) {
    return { zoned: false, zones: [], allowAsap: true, scheduling: { delivery: false, pickup: false } };
  }
  const [zones, rules] = await Promise.all([zonesOf(sf.shop_id), rulesOf(sf.shop_id)]);
  const act = zones.filter((z) => z.active);
  return {
    zoned: act.length > 0,
    zones: act.map(publicZone),
    allowAsap: sf.allow_asap !== false,
    scheduling: {
      delivery: rules.some((r) => r.active && r.fulfillment === 'delivery'),
      pickup: rules.some((r) => r.active && r.fulfillment === 'pickup'),
    },
  };
}

/**
 * «این پین در کدام ناحیه است؟» — بی ذخیره و بی ژئوکدر؛ با خانهٔ شبکه
 * (`zoneForPin`) و سقفِ روزانهٔ هر IP در مسیر (`storeLocateDailyMax`).
 */
async function locate(sf, latRaw, lngRaw) {
  const lat = Number(latRaw);
  const lng = Number(lngRaw);
  if (!Number.isFinite(lat) || lat < -90 || lat > 90 || !Number.isFinite(lng) || lng < -180 || lng > 180) {
    throw badRequest('مختصاتِ پین معتبر نیست', 'bad_address');
  }
  const zones = (await flagOn(sf.shop_id)) ? (await zonesOf(sf.shop_id)).filter((z) => z.active) : [];
  if (!zones.length) return { zoned: false, inZone: true, zone: null, pickup: sf.accept_pickup === true };
  const z = zoneForPin(zones, lat, lng);
  const out = { zoned: true, inZone: Boolean(z), zone: z ? publicZone(z) : null, pickup: sf.accept_pickup === true };
  //  CHK-10/S34: «خبرم کن» فقط با پرچمِ خودش و فقط برای پینِ بیرون؛ خاموش ⇒ همان پاسخِ پیشین
  if (!z && sf.accept_delivery && (await require('./storefront-area-requests').flagOn(sf.shop_id))) out.notifyArea = true;
  return out;
}

async function publicSlots(sf, fulfillment, at = db.now()) {
  if (!['delivery', 'pickup'].includes(fulfillment)) throw badRequest('ارسال یا دریافت از دکان؟', 'bad_fulfillment');
  const on = await flagOn(sf.shop_id);
  const rules = on ? (await rulesOf(sf.shop_id)).filter((r) => r.active && r.fulfillment === fulfillment) : [];
  const slots = rules.length ? await slotsWithLoad(sf, fulfillment, at, { rules }) : [];
  return {
    fulfillment,
    scheduling: rules.length > 0,
    //  «هرچه زودتر» فقط وقتی بسته است که این روش واقعاً بازه داشته باشد
    allowAsap: !rules.length || sf.allow_asap !== false,
    timezone: sf.timezone || 'Asia/Kabul',
    slots: slots.map(publicSlot),
  };
}

// ════════════════════════════════════════════════════════════════════
//  ثبتِ سفارش — درونِ تراکنش و قفلِ `orders.create`
// ════════════════════════════════════════════════════════════════════

function withData(err, data) {
  return Object.assign(err, { data });
}

/**
 * هزینه، حداقل، نشانی و زمانِ سفارش. `input` همان خروجیِ `parseCheckout`
 * است (+ `zoneId` و `slotStartsAt`). خطاها دادهٔ کافی برای نشان دادنِ راهِ
 * درست دارند (ناحیه‌ها، بازه‌های تازه، امکانِ «دریافت از دکان»).
 */
async function forCheckout(run, sf, input, subtotalMinor, at, { zonesOn = false } = {}) {
  let deliveryFeeMinor = 0;
  let minOrderMinor = Number(sf.min_order_minor);
  let smallBasketFeeMinor = 0;
  let slotFeeMinor = 0;
  let address = input.address;
  let delivery = input.delivery;

  if (input.fulfillment === 'delivery') {
    const zones = zonesOn ? (await zonesOf(sf.shop_id, run)).filter((z) => z.active) : [];
    if (!zones.length) {
      deliveryFeeMinor = Number(sf.delivery_fee_minor);   // رفتارِ امروز
    } else {
      let zone = null;
      let source = 'pin';
      if (address.lat !== undefined && address.lat !== null) {
        zone = zoneForPin(zones, address.lat, address.lng);
        if (!zone) {
          throw withData(badRequest('این نشانی بیرون از محدودهٔ ارسالِ دکان است', 'out_of_zone'),
            { pickup: sf.accept_pickup === true, zones: zones.map(publicZone) });
        }
      } else if (input.zoneId) {
        zone = zones.find((z) => z.id === input.zoneId) || null;
        source = 'customer';
        if (!zone) throw withData(badRequest('این ناحیه دیگر در فهرستِ ارسال نیست', 'bad_zone'), { zones: zones.map(publicZone) });
      } else {
        throw withData(badRequest('برای ارسال، پین بگذارید یا ناحیهٔ خود را برگزینید', 'zone_required'),
          { zones: zones.map(publicZone) });
      }
      minOrderMinor = zone.minOrderMinor;
      deliveryFeeMinor = zone.freeOverMinor !== null && subtotalMinor >= zone.freeOverMinor ? 0 : zone.feeMinor;
      //  CHK-06: زیرِ حداقل و ناحیه هزینهٔ سبدِ کوچک دارد ⇒ پذیرفته با همین هزینه (نه رد)
      if (zone.smallBasketFeeMinor && subtotalMinor < zone.minOrderMinor) smallBasketFeeMinor = zone.smallBasketFeeMinor;
      address = {
        ...address,
        zone: { id: zone.id, name: zone.name, source, ...(smallBasketFeeMinor ? { smallBasketFeeMinor } : {}) },
      };
    }
  }

  let scheduledFor = null;
  const rules = zonesOn ? (await rulesOf(sf.shop_id, run)).filter((r) => r.active && r.fulfillment === input.fulfillment) : [];
  if (input.slotStartsAt === null) {
    if (rules.length && sf.allow_asap === false) {
      const slots = await slotsWithLoad(sf, input.fulfillment, at, { run, rules });
      throw withData(badRequest('یک بازهٔ زمانی برگزینید', 'slot_required'), { slots: slots.map(publicSlot) });
    }
  } else {
    if (!rules.length) throw badRequest('این ویترین برای این روش زمان‌بندی ندارد', 'slot_unavailable');
    const slots = await slotsWithLoad(sf, input.fulfillment, at, { run, rules });
    const s = slots.find((x) => x.startsAt === input.slotStartsAt);
    if (!s) {
      throw withData(conflict('این بازه دیگر در دسترس نیست؛ بازهٔ دیگری برگزینید', 'slot_unavailable'),
        { slots: slots.map(publicSlot) });
    }
    if (s.remaining <= 0) {
      throw withData(conflict('این بازه پر شد؛ بازهٔ دیگری برگزینید', 'slot_full'), { slots: slots.map(publicSlot) });
    }
    scheduledFor = s.startsAt;
    //  CHK-07: هزینهٔ زمانِ تحویل — فقط ارسال (CHECK ِ ۱۳۸ برای «دریافت» صفر نگهش می‌دارد)
    if (input.fulfillment === 'delivery') slotFeeMinor = s.feeMinor || 0;
    delivery = { ...delivery, slot: { startsAt: s.startsAt, endsAt: s.endsAt, ...(slotFeeMinor ? { feeMinor: slotFeeMinor } : {}) } };
  }
  deliveryFeeMinor += smallBasketFeeMinor + slotFeeMinor;
  return { deliveryFeeMinor, minOrderMinor, smallBasketFeeMinor, slotFeeMinor, address, delivery, scheduledFor };
}

/** ریزِ هزینهٔ ارسال برای برگهٔ قیمت (CHK-04/06/07) — فقط بخشِ ناصفر؛ هر دو درونِ `deliveryFeeMinor` اند. */
function feeParts(d) {
  return {
    ...(d.smallBasketFeeMinor ? { smallBasketFeeMinor: d.smallBasketFeeMinor } : {}),
    ...(d.slotFeeMinor ? { slotFeeMinor: d.slotFeeMinor } : {}),
  };
}

/** CHK-06: زیرِ حداقل رد می‌شود مگر ناحیه «هزینهٔ سبدِ کوچک» داشته و همان اعمال شده باشد. */
const belowMin = (subtotalMinor, d) => subtotalMinor < d.minOrderMinor && !d.smallBasketFeeMinor;

/** همان ریز از روی سفارشِ ثبت‌شده (کنارِ `address.zone` و `delivery.slot`) — برای نمای مشتری. */
function orderFeeParts(o) {
  return {
    smallBasketFeeMinor: o.address && o.address.zone ? Number(o.address.zone.smallBasketFeeMinor || 0) : 0,
    slotFeeMinor: o.delivery && o.delivery.slot ? Number(o.delivery.slot.feeMinor || 0) : 0,
  };
}

module.exports = {
  MAX_ZONES, MAX_RULES, RELEASED, ID_RE, DAY_MS, FLAG_KEY, flagOn,
  GRID_M, M_PER_DEG_LAT, inPolygon, distanceM, contains, zoneAt, snapPin, zoneForPin, publicZone, epochOf, localDate, addDays,
  zonesOf, rulesOf, settingsOf, saveSettings, slotsOf, slotsWithLoad, publicSlot,
  publicInfo, locate, publicSlots, forCheckout, feeParts, belowMin, orderFeeParts,
};
