'use strict';
/**
 * زمانِ تقریبیِ تحویل، زنده و بر اساسِ شلوغی — WEB-04 (§۶.۱۴؛ §۲۰.۷: همیشه بازه و «تقریبی»، هرگز وعدهٔ دقیق).
 * شرحِ جدول: ‎migrations/193_storefront_eta.sql‎.
 *
 *   پرچم ── `shop.storefront.eta` (live_config، دکان یا all؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ `GET /api/store/:slug/eta`
 *           همان پاسخِ مسیرِ ناشناختهٔ امروز، `features` ِ ویترین و پاسخِ پیگیری بی کلیدِ `eta`، تنظیمِ مالک ۴۰۳.
 *   داده ── همه از سرور و دادهٔ واقعی، هر بار از نو:
 *     • صف ── سفارش‌های باز و هنوز بسته‌بندی‌نشده (new/accepted/picking)؛ آزمایشی و زمان‌بندی‌شدهٔ نرسیده نه.
 *              هر `parallel` سفارشِ جلوتر یک دورِ آماده‌سازی انتظار می‌افزاید.
 *     • آماده ─ میانهٔ «قبول ⇒ بسته‌بندی» ِ ۱۴ روزِ گذشته (دست‌کم ۳ نمونه)؛ کمتر ⇒ دقیقهٔ پیش‌فرضِ صاحب.
 *     • شلوغ ── «شلوغم» ِ میزِ سفارش (ORD-08): دقیقه‌های افزوده تا پایانش.
 *     • پیک ── پیک‌های فعالِ دکان (ظرفیت = `max_live`، تهی = ۱) در برابرِ مأموریت‌های زنده و بسته‌های منتظرِ
 *              ارسال؛ ظرفیت پر ⇒ هر دورِ پر یک «راه» انتظار. دکانِ بی پیکِ خودش ⇒ بی این بخش.
 *     • راه ── دقیقهٔ ناحیه از تنظیمِ صاحب (`zoneTravel`)، وگرنه دقیقهٔ پیش‌فرضِ راه.
 *   پیگیری ─ همان اجزا برای همان سفارش و مرحله‌اش: «در انتظارِ قبول» (صفِ جلوترش)، «آماده‌سازی» (زمانِ آماده ِ
 *            خودِ دکان اگر گفته، وگرنه باقیِ میانه)، «منتظرِ پیک»، «در راه» (زمانِ رسیدنِ پیک اگر گفته، وگرنه باقیِ راه).
 *            زمان‌بندی‌شده (بازهٔ CHK-12 خودش وعده است) و پایان‌یافته ⇒ `null`.
 *
 * ⛔ خروجیِ عمومی فقط `{minMinutes, maxMinutes}` است (بازه، گرد به ۵ دقیقه): نه شمارِ صف و پیک، نه هیچ
 *    دادهٔ مشتری. دکانِ بسته، متوقف یا بی اتصالِ تأییدشده ⇒ بی عدد (`reason`)، نه عددِ ساختگی (S102).
 */
const db = require('../db');
const { one, many, now } = db;
const config = require('../config');
const orders = require('./orders');
const orderDesk = require('./order-desk');
const sfd = require('./storefront-delivery');
const { LIVE } = require('./delivery-states');
const { int, asJson } = require('./delivery-input');
const { badRequest, conflict } = require('../middleware/errors');

const FLAG = 'shop.storefront.eta';
const MIN = 60_000;
const WINDOW_MS = 14 * 24 * 60 * MIN;
const MIN_SAMPLES = 3;
const MAX_SAMPLES = 200;
const MAX_MINUTES = 720;
/** «در انتظارِ قبول»: دکان هنوز ندیده — سرِ بالای بازه این‌قدر بازتر */
const ACCEPT_SLACK = 5;
const DEFAULTS = Object.freeze({ show: true, prepMinutes: 15, travelMinutes: 20, parallel: 2 });
const QUEUE = Object.freeze(['new', 'accepted', 'picking']);
const PREPARING = Object.freeze(['accepted', 'picking']);
const ON_THE_WAY = Object.freeze(['picked_up', 'moving']);
const ZONE_RE = /^[0-9A-Z]{26}$/;

const flagOn = (shopId) => orders.flagOf(String(shopId), FLAG);
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});

// ════════════════════════════════════════════════════════════════════
//  حسابِ خالص (بی پایگاه) — آزمون‌پذیر
// ════════════════════════════════════════════════════════════════════

function median(xs) {
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

/** هر `parallel` سفارشِ جلوتر یک دورِ آماده‌سازی. */
const queueWait = (ahead, parallel, prep) => Math.floor(Math.max(0, ahead) / Math.max(1, parallel)) * prep;

/** `ahead` تحویلِ جلوتر در برابرِ ظرفیتِ پیک‌ها؛ ظرفیتِ پر ⇒ هر دورِ پر یک «راه». */
function courierWait(c, travel, ahead) {
  if (!c || !(c.cap > 0) || ahead < c.cap) return 0;
  return (Math.floor((ahead - c.cap) / c.cap) + 1) * travel;
}

/** برآوردِ نقطه‌ای ⇒ بازه؛ همیشه دو عددِ جدا (هرگز یک وعده)، زیرِ ۱۵ دقیقه به دقیقه، وگرنه به ۵ دقیقه. */
function range(e, slack = 0) {
  const m = clamp(Math.round(e), 1, MAX_MINUTES);
  const step = m < 15 ? 1 : 5;
  const lo = Math.max(1, Math.floor((m * 0.9) / step) * step);
  const hi = Math.max(lo + step, Math.ceil((m * 1.25 + slack) / step) * step);
  return { minMinutes: Math.min(lo, MAX_MINUTES - step), maxMinutes: Math.min(hi, MAX_MINUTES) };
}

const travelOf = (settings, zoneId) => {
  const z = zoneId ? settings.zoneTravel[zoneId] : undefined;
  return Number.isSafeInteger(z) ? z : settings.travelMinutes;
};

/** سفارشِ تازه، همین حالا: صف + آماده‌سازی + «شلوغم» (+ انتظارِ پیک + راه). */
function estimate(sig, fulfillment, zoneId = null) {
  const base = queueWait(sig.queue, sig.settings.parallel, sig.prep) + sig.prep + sig.busyExtra;
  if (fulfillment !== 'delivery') return range(base);
  const travel = travelOf(sig.settings, zoneId);
  return range(base + courierWait(sig.courier, travel, sig.courier.live + sig.courier.waiting) + travel);
}

// ════════════════════════════════════════════════════════════════════
//  تنظیمِ صاحب
// ════════════════════════════════════════════════════════════════════

function settingsView(r) {
  if (!r) return { ...DEFAULTS, zoneTravel: {}, version: 0, updatedAt: null };
  const zt = Object.entries(asJson(r.zone_travel)).filter(([k, v]) => ZONE_RE.test(k) && Number.isSafeInteger(v));
  return {
    show: r.show !== false, prepMinutes: Number(r.prep_min), travelMinutes: Number(r.travel_min), parallel: Number(r.lanes),
    zoneTravel: Object.fromEntries(zt), version: Number(r.version), updatedAt: Number(r.updated_at),
  };
}

async function settingsOf(shopId) {
  return settingsView(await one('SELECT * FROM storefront_eta WHERE shop_id = $1', [String(shopId)]));
}

function zoneTravelOf(raw, zoneIds) {
  if (raw === null) return {};
  if (typeof raw !== 'object' || Array.isArray(raw) || Object.keys(raw).length > sfd.MAX_ZONES) {
    throw badRequest('دقیقهٔ راهِ ناحیه‌ها فهرستِ «ناحیه: دقیقه» است', 'bad_zone_travel');
  }
  const out = {};
  for (const [id, v] of Object.entries(raw)) {
    if (!zoneIds.has(id)) throw badRequest('این ناحیه در فهرستِ ارسالِ دکان نیست', 'bad_zone');
    if (v === null || v === '') continue;
    out[id] = int(v, 'دقیقهٔ راهِ ناحیه', { min: 0, max: 240 });
  }
  return out;
}

/**
 * تنظیم با `expectedVersion` ِ اختیاری؛ دو نوشتنِ هم‌زمان ⇒ دومی ۴۰۹ `version_conflict` (بی نوشتنِ نیمه‌کاره).
 * فیلدِ نیامده همان مقدارِ پیشین می‌ماند.
 */
async function saveSettings(shopId, body, userId = '') {
  const b = obj(body);
  const prev = await settingsOf(shopId);
  const stale = () => Object.assign(conflict('تنظیم‌ها همین حالا جای دیگری عوض شد', 'version_conflict'), { data: { version: prev.version } });
  if (b.expectedVersion !== undefined && b.expectedVersion !== null && Number(b.expectedVersion) !== prev.version) throw stale();
  if (b.show !== undefined && typeof b.show !== 'boolean') throw badRequest('نمایش باید روشن یا خاموش باشد', 'bad_show');
  const next = {
    show: b.show === undefined ? prev.show : b.show,
    prepMinutes: b.prepMinutes === undefined ? prev.prepMinutes : int(b.prepMinutes, 'دقیقهٔ آماده‌سازی', { min: 1, max: 240 }),
    travelMinutes: b.travelMinutes === undefined ? prev.travelMinutes : int(b.travelMinutes, 'دقیقهٔ راه', { min: 0, max: 240 }),
    parallel: b.parallel === undefined ? prev.parallel : int(b.parallel, 'سفارشِ هم‌زمان', { min: 1, max: 20 }),
    zoneTravel: prev.zoneTravel,
  };
  if (b.zoneTravel !== undefined) {
    const ids = new Set((await sfd.zonesOf(String(shopId))).map((z) => z.id));
    next.zoneTravel = zoneTravelOf(b.zoneTravel, ids);
  }
  const row = await one(
    `INSERT INTO storefront_eta (shop_id, show, prep_min, travel_min, lanes, zone_travel, version, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6::jsonb,1,$7,$8)
     ON CONFLICT (shop_id) DO UPDATE SET show=EXCLUDED.show, prep_min=EXCLUDED.prep_min, travel_min=EXCLUDED.travel_min,
       lanes=EXCLUDED.lanes, zone_travel=EXCLUDED.zone_travel, version=storefront_eta.version + 1,
       updated_by=EXCLUDED.updated_by, updated_at=EXCLUDED.updated_at
     WHERE storefront_eta.version = $9
     RETURNING *`,
    [String(shopId), next.show, next.prepMinutes, next.travelMinutes, next.parallel, JSON.stringify(next.zoneTravel),
      String(userId || ''), now(), prev.version]
  );
  if (!row) throw stale();
  cache.delete(String(shopId));
  return settingsView(row);
}

// ════════════════════════════════════════════════════════════════════
//  نشانه‌های زنده
// ════════════════════════════════════════════════════════════════════

/** میانهٔ «قبول ⇒ بسته‌بندی» (دقیقه) در پنجرهٔ ۱۴ روزه؛ کمتر از ۳ نمونه ⇒ `median: null`. */
async function prepSample(shopId, at) {
  const rows = await many(
    `SELECT p.created_at - a.created_at AS ms
       FROM online_order_events p
       JOIN online_order_events a ON a.order_id = p.order_id AND a.kind = 'status' AND a.to_status = 'accepted'
       JOIN online_orders o ON o.id = p.order_id AND o.is_test = false
      WHERE p.shop_id = $1 AND p.kind = 'status' AND p.to_status = 'packed' AND p.created_at >= $2
      ORDER BY p.created_at DESC
      LIMIT ${MAX_SAMPLES}`,
    [String(shopId), at - WINDOW_MS]
  );
  const mins = rows.map((r) => Number(r.ms) / MIN).filter((m) => m > 0 && m <= 24 * 60);
  return { samples: mins.length, median: mins.length >= MIN_SAMPLES ? median(mins) : null };
}

/** سفارش‌های باز و بسته‌بندی‌نشده؛ `before` = فقط آن‌هایی که پیش از این لحظه ثبت شدند. */
async function queueCount(shopId, at, { statuses = QUEUE, before = null, except = '' } = {}) {
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM online_orders
      WHERE shop_id = $1 AND is_test = false AND status = ANY($2::text[]) AND id <> $3
        AND (scheduled_for IS NULL OR scheduled_for <= $4) AND ($5::bigint IS NULL OR created_at < $5)`,
    [String(shopId), statuses, String(except), at, before]
  );
  return Number(r?.n || 0);
}

/** پیک‌های فعالِ خودِ دکان، ظرفیتشان، مأموریت‌های زنده و بسته‌های ارسالیِ هنوز بی مأموریت. */
async function courierLoad(shopId) {
  const r = await one(
    `SELECT (SELECT COUNT(*)::int FROM shop_couriers WHERE shop_id = $1 AND active) AS couriers,
            (SELECT COALESCE(SUM(COALESCE(max_live, 1)), 0)::int FROM shop_couriers WHERE shop_id = $1 AND active) AS cap,
            (SELECT COUNT(*)::int FROM delivery_assignments
              WHERE shop_id = $1 AND mode = 'shop_courier' AND status = ANY($2::text[])) AS live,
            (SELECT COUNT(*)::int FROM online_orders o
              WHERE o.shop_id = $1 AND o.is_test = false AND o.fulfillment = 'delivery' AND o.status = 'packed'
                AND NOT EXISTS (SELECT 1 FROM delivery_assignments d WHERE d.order_id = o.id AND d.status = ANY($2::text[]))) AS waiting`,
    [String(shopId), LIVE]
  );
  return { couriers: Number(r?.couriers || 0), cap: Number(r?.cap || 0), live: Number(r?.live || 0), waiting: Number(r?.waiting || 0) };
}

async function signalsOf(shopId, at, settings, desk) {
  const [sample, queue, courier] = await Promise.all([prepSample(shopId, at), queueCount(shopId, at), courierLoad(shopId)]);
  return {
    settings, queue, courier, samples: sample.samples,
    prep: clamp(Math.round(sample.median ?? settings.prepMinutes), 1, 240),
    busyExtra: desk && desk.busy ? Number(desk.busy.extraMinutes) || 0 : 0,
  };
}

/** دکان همین حالا سفارش را می‌بیند؟ نه ⇒ دلیل (بی عدد). */
async function openness(sf, at) {
  const [status, desk] = await Promise.all([require('./storefront').statusOf(sf, at), orderDesk.overlay(sf.shop_id, {}, at)]);
  if (desk.pause) return { reason: 'paused', desk };
  if (status.orderAcceptance === 'later') return { reason: 'closed', desk };
  if (status.orderAcceptance !== 'accepting') return { reason: 'unconfirmed', desk };
  return { reason: '', desk };
}

// ════════════════════════════════════════════════════════════════════
//  ویترین (پیش از سفارش) — عمومی
// ════════════════════════════════════════════════════════════════════

/** `undefined` = پرچم خاموش. */
async function publicEta(sf, at = now()) {
  if (!(await flagOn(sf.shop_id))) return undefined;
  const settings = await settingsOf(sf.shop_id);
  if (!settings.show) return { available: false, reason: 'hidden' };
  const { reason, desk } = await openness(sf, at);
  if (reason) return { available: false, reason };
  const sig = await signalsOf(sf.shop_id, at, settings, desk);
  const delivery = sf.accept_delivery !== false;
  const zones = delivery && (await sfd.flagOn(sf.shop_id)) ? (await sfd.zonesOf(sf.shop_id)).filter((z) => z.active) : [];
  return {
    available: true, approximate: true,
    delivery: delivery ? estimate(sig, 'delivery') : null,
    pickup: sf.accept_pickup === true ? estimate(sig, 'pickup') : null,
    zones: zones.map((z) => ({ id: z.id, ...estimate(sig, 'delivery', z.id) })),
    updatedAt: at,
  };
}

const cache = new Map();
const CACHE_MAX = 5000;

/** همان `publicEta` با کشِ کوتاهِ همین نمونهٔ سرور (`STORE_ETA_CACHE_MS`، پیش‌فرض ۳۰ ثانیه). پرچم هر بار سنجیده می‌شود. */
async function cachedPublic(sf, at = now()) {
  if (!(await flagOn(sf.shop_id))) return undefined;
  const ttl = config.storefront.etaCacheMs;
  const hit = ttl > 0 ? cache.get(sf.shop_id) : null;
  if (hit && at - hit.at >= 0 && at - hit.at < ttl) return hit.value;
  const value = await publicEta(sf, at);
  if (ttl > 0 && value !== undefined) {
    cache.delete(sf.shop_id);
    cache.set(sf.shop_id, { at, value });
    if (cache.size > CACHE_MAX) cache.delete(cache.keys().next().value);
  }
  return value;
}

// ════════════════════════════════════════════════════════════════════
//  پیگیریِ یک سفارش — زنده، با مرحله
// ════════════════════════════════════════════════════════════════════

const lastAt = async (orderId, status) => {
  const r = await one(
    `SELECT MAX(created_at) AS t FROM online_order_events WHERE order_id = $1 AND kind = 'status' AND to_status = $2`,
    [orderId, status]
  );
  return r && r.t !== null && r.t !== undefined ? Number(r.t) : null;
};
const minutesSince = (t, at) => (t === null ? 0 : Math.max(0, (at - t) / MIN));

/**
 * `{stage, minMinutes, maxMinutes, approximate, updatedAt}` برای همین سفارش؛ `undefined` = پرچم خاموش
 * (پاسخِ پیگیری بی کلید)، `null` = این مرحله زمانِ تقریبی ندارد.
 */
async function orderEta(sf, orderId, at = now()) {
  const shopId = sf.shop_id;
  if (!(await flagOn(shopId))) return undefined;
  const settings = await settingsOf(shopId);
  if (!settings.show) return null;
  const o = await one(
    'SELECT id, status, fulfillment, created_at, ready_at, scheduled_for, address FROM online_orders WHERE shop_id = $1 AND id = $2',
    [shopId, String(orderId || '')]
  );
  if (!o) return null;
  if (o.scheduled_for !== null && o.scheduled_for !== undefined && Number(o.scheduled_for) > at) return null;
  const delivery = o.fulfillment === 'delivery';
  const zoneId = obj(obj(asJson(o.address)).zone).id || null;
  const travel = travelOf(settings, zoneId);
  const created = Number(o.created_at);
  const done = (stage, e, slack = 0) => ({ stage, ...range(e, slack), approximate: true, updatedAt: at });

  if (o.status === 'out_for_delivery') {
    const a = await one(
      `SELECT eta_at FROM delivery_assignments WHERE shop_id = $1 AND order_id = $2 AND status = ANY($3::text[])
        ORDER BY attempt_no DESC LIMIT 1`,
      [shopId, o.id, ON_THE_WAY]
    );
    const etaAt = a && a.eta_at !== null && a.eta_at !== undefined ? Number(a.eta_at) : null;
    if (etaAt !== null && etaAt > at) return done('on_the_way', (etaAt - at) / MIN);
    return done('on_the_way', Math.max(2, travel - minutesSince(await lastAt(o.id, 'out_for_delivery'), at)));
  }
  if (o.status === 'packed') {
    if (!delivery) return done('ready', 2);
    const c = await courierLoad(shopId);
    return done('courier', courierWait(c, travel, c.live + Math.max(0, c.waiting - 1)) + travel);
  }
  if (!QUEUE.includes(o.status)) return null;

  const [sample, c] = await Promise.all([prepSample(shopId, at), delivery ? courierLoad(shopId) : null]);
  const prep = clamp(Math.round(sample.median ?? settings.prepMinutes), 1, 240);
  const tail = delivery ? courierWait(c, travel, c.live + c.waiting) + travel : 0;
  if (o.status === 'new') {
    const { reason, desk } = await openness(sf, at);
    if (reason) return null;
    const ahead = await queueCount(shopId, at, { before: created, except: o.id });
    const busy = desk.busy ? Number(desk.busy.extraMinutes) || 0 : 0;
    return done('waiting', queueWait(ahead, settings.parallel, prep) + prep + busy + tail, ACCEPT_SLACK);
  }
  //  آماده‌سازی: زمانِ آماده‌ای که خودِ دکان گفته مقدم است؛ وگرنه باقیِ میانه + صفِ جلوتر
  const readyAt = o.ready_at !== null && o.ready_at !== undefined ? Number(o.ready_at) : null;
  if (readyAt !== null && readyAt > at) return done('preparing', (readyAt - at) / MIN + tail);
  const left = Math.max(Math.ceil(prep * 0.2), prep - minutesSince(await lastAt(o.id, 'accepted'), at));
  const ahead = await queueCount(shopId, at, { statuses: PREPARING, before: created, except: o.id });
  return done('preparing', queueWait(ahead, settings.parallel, prep) + left + tail);
}

/** نمای صاحب: تنظیم، نشانه‌های همین لحظه و همان پیش‌نمایشی که مشتری می‌بیند. */
async function ownerView(shopId, at = now()) {
  shopId = String(shopId);
  const sf = await one('SELECT * FROM storefronts WHERE shop_id = $1', [shopId]);
  const settings = await settingsOf(shopId);
  const desk = await orderDesk.overlay(shopId, {}, at);
  const sig = await signalsOf(shopId, at, settings, desk);
  const zones = (await sfd.flagOn(shopId)) ? (await sfd.zonesOf(shopId)).map((z) => ({ id: z.id, name: z.name, active: z.active })) : [];
  return {
    settings, zones,
    signals: {
      queue: sig.queue, prepMinutes: sig.prep, samples: sig.samples, prepFromHistory: sig.samples >= MIN_SAMPLES,
      busyExtraMinutes: sig.busyExtra, couriers: sig.courier,
    },
    //  ویترینی هنوز ساخته نشده ⇒ بی پیش‌نمایش
    preview: sf ? await publicEta(sf, at) : null,
  };
}

module.exports = {
  FLAG, DEFAULTS, MIN_SAMPLES, WINDOW_MS, MAX_MINUTES, flagOn,
  median, queueWait, courierWait, range, estimate,
  settingsOf, saveSettings, prepSample, queueCount, courierLoad, publicEta, cachedPublic, orderEta, ownerView,
  _forget: () => cache.clear(),
};
