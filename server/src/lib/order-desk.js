'use strict';
/**
 * میزِ سفارشِ دکان — ORD-08 («شلوغم» و توقفِ موقت)، ORD-09 (توقفِ خودکار) و ORD-04 (قبولِ خودکار).
 *
 *   پرچم ── `shop.orders.desk` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ هیچ ردیفی
 *           نوشته نمی‌شود، ویترین و صف همان رفتارِ پیشین را دارند و کارگر به هیچ دکانی دست نمی‌زند.
 *           نوشتن کلیدِ اشتراکِ `online_orders` را هم می‌خواهد (SUB-10، `routes/order-desk.js`).
 *   شلوغ ── `busy_until` + دقیقه‌های افزوده: تا آن لحظه زمانِ آماده‌شدنِ قبولِ خودکار بیشتر است و
 *           ویترین `busy` را در وضعیتِ عمومی می‌گیرد (برنامهٔ خرید زمانِ تحویل را با آن بالا می‌برد).
 *           پایان خودکار است: با گذشتنِ زمان «فعال» نیست (هیچ کارگری لازم نیست).
 *   توقف ── `paused_until` با دلیل و مدت: ویترین تا آن لحظه سفارشِ **تازه** نمی‌گیرد (۴۰۹ `store_paused`)؛
 *           تکرارِ سفارشی که پیش‌تر ثبت شده همان سفارش را برمی‌گرداند. سفارشِ دستیِ کارمند آزاد است.
 *   خودکار ─ (ORD-09، پیش‌فرض خاموش) بیش از `auto_pause_max_open` سفارشِ باز یا سفارشِ تازه‌ای قدیمی‌تر از
 *           `auto_pause_oldest_min` ⇒ توقف برای `auto_pause_minutes` با دلیلِ `overloaded`، سابقه و اعلان
 *           به صاحب و مدیر. کارگرِ هر دقیقه ([sweep]) و پس از هر سفارشِ ویترین ([afterOrder]). «ادامه» ِ
 *           دستی تا یک دورِ توقف مهلت دارد (دوباره بی‌درنگ متوقف نمی‌شود).
 *   قبول ── (ORD-04، پیش‌فرض خاموش) سفارشِ ویترینِ فوری، وقتی دکان همان لحظه «می‌پذیرد» (اتصالِ تأییدشده،
 *           نه توقف) و کمتر از `auto_accept_max_open` سفارش در آماده‌سازی است ⇒ «قبول» با بازیگرِ سیستم و
 *           زمانِ آماده = `auto_accept_ready_min` (+ دقیقه‌های «شلوغم»). سفارشِ زمان‌بندی‌شده و آزمایشی نه.
 *
 * ── ایمنی ───────────────────────────────────────────────────────────────
 *   • ساعت همیشه ساعتِ سرور است؛ کلاینت فقط «چند دقیقه» می‌گوید.
 *   • هر نوشتن زیرِ قفلِ ردیفِ `order_desk` (`FOR UPDATE`) و با `event_id` ِ یکتای کلاینت: تکرار ⇒ همان
 *     نتیجه بی اثرِ دوم؛ همان شناسه با بدنهٔ دیگر ⇒ ۴۰۹ `idempotency_key_reused`. تنظیم با `expectedVersion`.
 *   • قبولِ خودکار زیرِ قفلِ دکان (`pg_advisory_xact_lock`) ⇒ دو سفارشِ هم‌زمان از سقف نمی‌گذرند؛ گذار
 *     فقط از راهِ `orders.transitionIn` با `from: 'new'` (سفارشی که کارمند زودتر جواب داده دست نمی‌خورد).
 *   • توقفِ خودکار زیرِ قفلِ ردیف و با `event_id` ِ دقیقه ⇒ دو نمونهٔ سرور یک بار متوقف می‌کنند.
 */
const crypto = require('crypto');
const db = require('../db');
const { one, many, now, tx } = db;
const { ulid } = require('./ulid');
const { stable } = require('./idempotency');
const orders = require('./orders');
const { stripControl } = require('./validate');
const { badRequest, conflict, ApiError } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.desk';
/** دلیل‌های توقفِ دستی؛ «other» توضیح می‌خواهد. `overloaded` فقط کارِ سیستم است. */
const PAUSE_REASONS = Object.freeze(['busy', 'out_of_stock', 'staff', 'technical', 'other']);
const AUTO_REASON = 'overloaded';
/** «صفِ باز» برای توقفِ خودکار: هنوز تحویل نشده و در دستِ دکان */
const OPEN_STATUSES = Object.freeze(['new', 'accepted', 'picking', 'packed']);
/** «در آماده‌سازی» برای سقفِ قبولِ خودکار */
const PREP_STATUSES = Object.freeze(['accepted', 'picking', 'packed']);
const EVENT_RE = /^[A-Za-z0-9_.:-]{8,80}$/;
const MIN = 60_000;
const TICK_MS = MIN;
const BATCH = 500;
const SYSTEM = Object.freeze({ type: 'system', id: '' });

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);
const nul = (v) => (v === null || v === undefined ? null : Number(v));
const hashOf = (body) => {
  const { eventId, ...rest } = body && typeof body === 'object' ? body : {};
  return crypto.createHash('sha256').update(stable(rest)).digest('hex');
};

function int(v, field, min, max) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < min || v > max) {
    throw badRequest(`${field} باید عددِ صحیحِ ${min} تا ${max} باشد`, 'bad_number');
  }
  return v;
}

// ════════════════════════════════════════════════════════════════════
//  خواندن
// ════════════════════════════════════════════════════════════════════

/** نمای میز — `row` نبود ⇒ پیش‌فرض‌ها (همه خاموش). «شلوغ» و «توقف» فقط تا زمانشان. */
function view(row, at) {
  const r = row || {};
  const busyUntil = nul(r.busy_until);
  const pausedUntil = nul(r.paused_until);
  return {
    busy: busyUntil !== null && busyUntil > at ? { until: busyUntil, extraMinutes: Number(r.busy_extra_minutes) } : null,
    pause: pausedUntil !== null && pausedUntil > at
      ? { until: pausedUntil, reason: r.pause_reason, note: r.pause_note, source: r.pause_source } : null,
    autoPause: {
      enabled: r.auto_pause === true, maxOpen: Number(r.auto_pause_max_open ?? 10),
      oldestMinutes: Number(r.auto_pause_oldest_min ?? 30), minutes: Number(r.auto_pause_minutes ?? 30),
    },
    autoAccept: {
      enabled: r.auto_accept === true, maxOpen: Number(r.auto_accept_max_open ?? 5),
      readyMinutes: Number(r.auto_accept_ready_min ?? 15),
    },
    version: Number(r.version ?? 0),
    updatedAt: nul(r.updated_at),
  };
}

const rowOf = (shopId) => one('SELECT * FROM order_desk WHERE shop_id = $1', [String(shopId)]);

/** صفِ باز (توقفِ خودکار) — سفارشِ آزمایشی و زمان‌بندی‌شدهٔ هنوز نرسیده شمرده نمی‌شوند. */
async function queueOf(shopId, at) {
  const r = await one(
    `SELECT COUNT(*) FILTER (WHERE status = ANY($3::text[]))::int AS open,
            MIN(created_at) FILTER (WHERE status = 'new') AS oldest
       FROM online_orders
      WHERE shop_id = $1 AND is_test = false AND (scheduled_for IS NULL OR scheduled_for <= $2)`,
    [String(shopId), at, OPEN_STATUSES]
  );
  return { open: Number(r?.open || 0), oldestNewAt: nul(r?.oldest) };
}

/** وضعیتِ میز برای کارمند (`GET /api/shop/orders/desk`). */
async function stateOf(shopId, at = now()) {
  return { ...view(await rowOf(shopId), at), queue: await queueOf(shopId, at), serverTime: at };
}

// ════════════════════════════════════════════════════════════════════
//  نوشتن — یک در برای همه: قفلِ ردیف، کلیدِ یکتا، رویداد
// ════════════════════════════════════════════════════════════════════

/**
 * `mutate(row, at)` ⇒ `{ set: {ستون: مقدار}, untilAt?, reason?, note?, detail? }` — فقط سنجش و ساختِ تغییر.
 * @returns {Promise<{replayed:boolean, state:object}>}
 */
async function apply(shopId, kind, { body, actor }, mutate) {
  const b = body && typeof body === 'object' ? body : {};
  const eventId = typeof b.eventId === 'string' ? b.eventId.trim() : '';
  if (!EVENT_RE.test(eventId)) throw badRequest('شناسهٔ رویداد لازم است', 'bad_event_id');
  const hash = hashOf(b);
  const sid = String(shopId);
  return tx(async (c) => {
    const at = now();
    await c.query('INSERT INTO order_desk (shop_id, updated_at) VALUES ($1, $2) ON CONFLICT (shop_id) DO NOTHING', [sid, at]);
    const row = (await c.query('SELECT * FROM order_desk WHERE shop_id = $1 FOR UPDATE', [sid])).rows[0];
    const prev = (await c.query(
      'SELECT kind, request_hash FROM order_desk_events WHERE shop_id = $1 AND event_id = $2', [sid, eventId]
    )).rows[0];
    if (prev) {
      if (prev.kind !== kind || prev.request_hash !== hash) {
        throw conflict('این شناسهٔ رویداد پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
      }
      return { replayed: true, state: view(row, at) };
    }
    const ch = mutate(row, at);
    const cols = Object.keys(ch.set);
    const params = [sid, ...cols.map((k) => ch.set[k]), actor.id || '', at];
    await c.query(
      `UPDATE order_desk SET ${cols.map((k, i) => `${k} = $${i + 2}`).join(', ')},
              version = version + 1, updated_by = $${cols.length + 2}, updated_at = $${cols.length + 3}
        WHERE shop_id = $1`,
      params
    );
    await c.query(
      `INSERT INTO order_desk_events (id, shop_id, event_id, kind, actor_type, actor_id, until_at, reason, note,
         request_hash, detail, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12)`,
      [ulid(at), sid, eventId, kind, actor.type, actor.id || '', ch.untilAt ?? null, ch.reason || '', ch.note || '',
        hash, JSON.stringify(ch.detail || {}), at]
    );
    const next = (await c.query('SELECT * FROM order_desk WHERE shop_id = $1', [sid])).rows[0];
    return { replayed: false, state: view(next, at) };
  });
}

/** ORD-08 «شلوغم»: `minutes` ۱ تا ۲۴۰ (۰ = پایانِ همین حالا)، `extraMinutes` ۵ تا ۱۲۰ (پیش‌فرض ۲۰). */
function busy(shopId, { body, actor }) {
  const b = body && typeof body === 'object' ? body : {};
  const minutes = int(b.minutes, 'مدتِ «شلوغم»', 0, 240);
  const extra = minutes === 0 ? 0 : (b.extraMinutes === undefined ? 20 : int(b.extraMinutes, 'دقیقه‌های افزوده', 5, 120));
  return apply(shopId, minutes === 0 ? 'busy_end' : 'busy', { body: b, actor }, (row, at) => {
    const until = minutes === 0 ? null : at + minutes * MIN;
    return {
      set: { busy_until: until, busy_extra_minutes: extra },
      untilAt: until, detail: { minutes, extraMinutes: extra, prevUntil: nul(row.busy_until) },
    };
  });
}

/** ORD-08 توقفِ موقت: `minutes` ۵ تا ۱۴۴۰، دلیل از [PAUSE_REASONS]، «other» با توضیح. */
function pause(shopId, { body, actor }) {
  const b = body && typeof body === 'object' ? body : {};
  const minutes = int(b.minutes, 'مدتِ توقف', 5, 24 * 60);
  const reason = typeof b.reason === 'string' ? b.reason.trim() : '';
  if (!PAUSE_REASONS.includes(reason)) throw badRequest('دلیلِ توقف لازم است', 'bad_reason');
  const note = stripControl(typeof b.note === 'string' ? b.note.trim() : '').slice(0, 200);
  if (reason === 'other' && !note) throw badRequest('برای «دلیلِ دیگر» توضیح لازم است', 'note_required');
  return apply(shopId, 'pause', { body: b, actor }, (row, at) => {
    const until = at + minutes * MIN;
    return {
      set: { paused_until: until, pause_reason: reason, pause_note: note, pause_source: 'user' },
      untilAt: until, reason, note, detail: { minutes },
    };
  });
}

/** پایانِ توقف همین حالا (دستی یا خودکار). */
function resume(shopId, { body, actor }) {
  return apply(shopId, 'resume', { body, actor }, (row) => ({
    set: { paused_until: null, pause_reason: '', pause_note: '', pause_source: '' },
    detail: { prevUntil: nul(row.paused_until), prevSource: row.pause_source || '' },
  }));
}

/** ORD-04 و ORD-09: تنظیم‌ها (صاحب و مدیر). فقط بخش‌های آمده؛ `expectedVersion` ِ کهنه ⇒ ۴۰۹. */
function saveSettings(shopId, { body, actor }) {
  const b = body && typeof body === 'object' ? body : {};
  const set = {};
  const ap = b.autoPause;
  if (ap !== undefined) {
    if (!ap || typeof ap !== 'object') throw badRequest('تنظیمِ توقفِ خودکار معتبر نیست', 'bad_settings');
    if (ap.enabled !== undefined) set.auto_pause = ap.enabled === true;
    if (ap.maxOpen !== undefined) set.auto_pause_max_open = int(ap.maxOpen, 'سقفِ صف', 1, 500);
    if (ap.oldestMinutes !== undefined) set.auto_pause_oldest_min = int(ap.oldestMinutes, 'دقیقه‌های بی‌پاسخ', 1, 1440);
    if (ap.minutes !== undefined) set.auto_pause_minutes = int(ap.minutes, 'مدتِ توقفِ خودکار', 5, 1440);
  }
  const aa = b.autoAccept;
  if (aa !== undefined) {
    if (!aa || typeof aa !== 'object') throw badRequest('تنظیمِ قبولِ خودکار معتبر نیست', 'bad_settings');
    if (aa.enabled !== undefined) set.auto_accept = aa.enabled === true;
    if (aa.maxOpen !== undefined) set.auto_accept_max_open = int(aa.maxOpen, 'سقفِ صف', 1, 500);
    if (aa.readyMinutes !== undefined) set.auto_accept_ready_min = int(aa.readyMinutes, 'زمانِ آماده', 1, 1440);
  }
  if (!Object.keys(set).length) throw badRequest('تغییری نیامد', 'bad_settings');
  const expected = b.expectedVersion;
  if (expected !== undefined && (typeof expected !== 'number' || !Number.isSafeInteger(expected) || expected < 0)) {
    throw badRequest('نسخه معتبر نیست', 'bad_version');
  }
  return apply(shopId, 'settings', { body: b, actor }, (row) => {
    if (expected !== undefined && Number(row.version) !== expected) {
      throw Object.assign(conflict('تنظیم‌ها همین حالا جای دیگری عوض شد', 'version_conflict'), { data: { version: Number(row.version) } });
    }
    return { set, detail: { changed: Object.keys(set) } };
  });
}

// ════════════════════════════════════════════════════════════════════
//  ویترین
// ════════════════════════════════════════════════════════════════════

/**
 * پیش از ساختِ سفارشِ ویترین: توقفِ فعال ⇒ ۴۰۹ `store_paused` (با `pausedUntil`)، مگر همین کلید پیش‌تر
 * سفارشی ساخته باشد (پاسخِ گم‌شده ⇒ همان سفارش، `orders.create`).
 */
async function guardCheckout(shopId, key, at = now()) {
  if (!(await flagOf(shopId))) return;
  const row = await rowOf(shopId);
  const until = nul(row?.paused_until);
  if (until === null || until <= at) return;
  if (key && (await one('SELECT 1 AS x FROM online_orders WHERE shop_id = $1 AND client_key = $2', [String(shopId), key]))) return;
  throw Object.assign(new ApiError(409, 'store_paused', 'سفارش‌گیریِ این فروشگاه موقتاً متوقف است؛ کمی بعد دوباره امتحان کنید.'),
    { data: { pausedUntil: until } });
}

/**
 * وضعیتِ عمومیِ ویترین + میز: `busy` (برای زمانِ تحویلِ برنامهٔ خرید)، `pause` و `orderAcceptance: 'paused'`.
 * پرچم خاموش یا بی ردیف ⇒ همان شیء، دست‌نخورده. فقط زمان‌ها — دلیل و توضیحِ داخلی به مشتری نمی‌رسد.
 */
async function overlay(shopId, status, at = now()) {
  if (!status || !(await flagOf(shopId))) return status;
  const v = view(await rowOf(shopId), at);
  if (!v.busy && !v.pause) return status;
  const out = { ...status };
  if (v.busy) out.busy = { until: v.busy.until, extraMinutes: v.busy.extraMinutes };
  if (v.pause) { out.pause = { until: v.pause.until }; out.orderAcceptance = 'paused'; }
  return out;
}

/** پس از ساختِ سفارشِ تازهٔ ویترین: قبولِ خودکار (ORD-04) و سنجشِ توقفِ خودکار (ORD-09). خطا سفارش را پس نمی‌گیرد. */
async function afterOrder(sf, orderId, at = now()) {
  const shopId = String(sf.shop_id);
  if (!(await flagOf(shopId))) return { autoAccepted: false, autoPaused: false };
  const row = await rowOf(shopId);
  if (!row) return { autoAccepted: false, autoPaused: false };
  const autoAccepted = row.auto_accept === true ? await autoAccept(sf, row, orderId, at) : false;
  const autoPaused = row.auto_pause === true ? await evaluateAutoPause(shopId, at) : false;
  return { autoAccepted, autoPaused };
}

async function autoAccept(sf, row, orderId, at) {
  const v = view(row, at);
  if (v.pause) return false;
  //  فقط وقتی دکان همین لحظه واقعاً پاسخ می‌دهد (همان داوریِ ویترین: اتصالِ تأییدشده، باز)
  const st = await require('./storefront').statusOf(sf, at);
  if (st.orderAcceptance !== 'accepting') return false;
  const shopId = String(sf.shop_id);
  const readyInMinutes = v.autoAccept.readyMinutes + (v.busy ? v.busy.extraMinutes : 0);
  const done = await tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`order-desk:${shopId}`]);
    const o = (await c.query(
      'SELECT status, is_test, scheduled_for, source FROM online_orders WHERE shop_id = $1 AND id = $2 FOR UPDATE',
      [shopId, String(orderId)]
    )).rows[0];
    if (!o || o.status !== 'new' || o.is_test === true || o.scheduled_for !== null || o.source !== 'storefront') return false;
    const prep = Number((await c.query(
      'SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1 AND is_test = false AND status = ANY($2::text[])',
      [shopId, PREP_STATUSES]
    )).rows[0].n);
    if (prep >= v.autoAccept.maxOpen) return false;
    await orders.transitionIn(c, shopId, orderId, { to: 'accepted', from: 'new', readyInMinutes }, SYSTEM);
    return true;
  });
  if (done) {
    await require('./audit').log({
      shopId, actorType: 'system', action: 'order.auto_accepted', targetType: 'online_order', targetId: String(orderId),
      detail: { readyInMinutes, maxOpen: v.autoAccept.maxOpen, busy: Boolean(v.busy) },
    }).catch(() => {});
  }
  return done;
}

// ════════════════════════════════════════════════════════════════════
//  ORD-09 — توقفِ خودکار
// ════════════════════════════════════════════════════════════════════

/** صف از حد گذشته یا سفارشِ تازه بی‌پاسخ مانده ⇒ توقف (یک بار، حتی با چند نمونهٔ سرور). */
async function evaluateAutoPause(shopId, at = now()) {
  const sid = String(shopId);
  const pre = await rowOf(sid);
  if (!pre || pre.auto_pause !== true) return false;
  if (nul(pre.paused_until) !== null && Number(pre.paused_until) > at) return false;
  const q = await queueOf(sid, at);
  const staleMin = q.oldestNewAt === null ? 0 : Math.floor((at - q.oldestNewAt) / MIN);
  const over = q.open > Number(pre.auto_pause_max_open);
  const stale = q.oldestNewAt !== null && at - q.oldestNewAt > Number(pre.auto_pause_oldest_min) * MIN;
  if (!over && !stale) return false;
  const paused = await tx(async (c) => {
    const row = (await c.query('SELECT * FROM order_desk WHERE shop_id = $1 FOR UPDATE', [sid])).rows[0];
    if (!row || row.auto_pause !== true) return null;
    if (nul(row.paused_until) !== null && Number(row.paused_until) > at) return null;   // نمونهٔ دیگری همین حالا
    //  «ادامه» ِ دستیِ صاحب یا کارمند تا یک دورِ توقف مهلت دارد — وگرنه دقیقهٔ بعد دوباره متوقف می‌شد
    const resumed = (await c.query(
      `SELECT MAX(created_at) AS t FROM order_desk_events WHERE shop_id = $1 AND kind = 'resume'`, [sid]
    )).rows[0];
    if (nul(resumed?.t) !== null && at - Number(resumed.t) < Number(row.auto_pause_minutes) * MIN) return null;
    const until = at + Number(row.auto_pause_minutes) * MIN;
    const ins = await c.query(
      `INSERT INTO order_desk_events (id, shop_id, event_id, kind, actor_type, actor_id, until_at, reason, note, detail, created_at)
       VALUES ($1,$2,$3,'auto_pause','system','',$4,$5,'',$6::jsonb,$7)
       ON CONFLICT (shop_id, event_id) DO NOTHING RETURNING id`,
      [ulid(at), sid, `auto:${Math.floor(at / MIN)}`, until, AUTO_REASON,
        JSON.stringify({ open: q.open, oldestMinutes: staleMin, maxOpen: Number(row.auto_pause_max_open),
          limitMinutes: Number(row.auto_pause_oldest_min), over, stale }), at]
    );
    if (!ins.rows.length) return null;
    await c.query(
      `UPDATE order_desk SET paused_until = $2, pause_reason = $3, pause_note = '', pause_source = 'auto',
              version = version + 1, updated_by = '', updated_at = $4 WHERE shop_id = $1`,
      [sid, until, AUTO_REASON, at]
    );
    return { until };
  });
  if (!paused) return false;
  await require('./audit').log({
    shopId: sid, actorType: 'system', action: 'order.auto_paused', targetType: 'shop', targetId: sid,
    detail: { open: q.open, oldestMinutes: staleMin, until: paused.until, over, stale },
  }).catch(() => {});
  await notifyManagers(sid, { open: q.open, oldestMinutes: staleMin, until: paused.until });
  return true;
}

/** «سفارش‌گیری خودکار متوقف شد» به صاحب و مدیر — همان پرچم و پوشِ اعلانِ سفارش؛ خطایش توقف را پس نمی‌گیرد. */
async function notifyManagers(shopId, { open, oldestMinutes }) {
  try {
    const on = require('./order-notify');
    if (!(await on.flagOf(shopId))) return { sent: 0, skipped: 'flag_off' };
    const rows = await many(
      `SELECT user_id FROM shop_members WHERE shop_id = $1 AND status = 'active' AND role = 'manager'
       UNION SELECT owner_user_id AS user_id FROM shops WHERE id = $1`,
      [shopId]
    );
    const onlyUserIds = rows.map((r) => String(r.user_id)).filter(Boolean);
    return (await require('./push').sendTo({ shopId, app: 'shop', onlyUserIds }, {
      title: 'سفارش‌گیریِ ویترین خودکار متوقف شد',
      body: `${open} سفارشِ باز${oldestMinutes ? `؛ قدیمی‌ترین ${oldestMinutes} دقیقه بی‌پاسخ` : ''}. برای ادامه، «ادامهٔ سفارش‌گیری» را بزنید.`,
      channel: on.CHANNEL,
      data: { type: 'orders_auto_paused', shopId },
    })) || {};
  } catch (err) {
    console.error('[order-desk] اعلان', err && err.message);
    return { sent: 0, error: String((err && err.message) || err) };
  }
}

/** کارگرِ هر دقیقه: دکان‌هایی که توقفِ خودکار روشن دارند و الان متوقف نیستند. خروجی: شمارِ متوقف‌شده‌ها. */
async function sweep(at = now()) {
  const rows = await many(
    `SELECT shop_id FROM order_desk WHERE auto_pause AND (paused_until IS NULL OR paused_until <= $1)
      ORDER BY shop_id LIMIT $2`,
    [at, BATCH]
  );
  let n = 0;
  for (const r of rows) {
    try {
      if (!(await flagOf(r.shop_id))) continue;
      if (await evaluateAutoPause(r.shop_id, at)) n++;
    } catch (err) {
      console.error('[order-desk]', r.shop_id, err && err.message);
    }
  }
  return n;
}

let timer = null;
let sweeping = false;
async function tick() {
  if (sweeping) return 0;
  sweeping = true;
  try { return await sweep(); } finally { sweeping = false; }
}
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[order-desk]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  FLAG_KEY, PAUSE_REASONS, AUTO_REASON, OPEN_STATUSES, PREP_STATUSES,
  flagOf, view, stateOf, busy, pause, resume, saveSettings,
  guardCheckout, overlay, afterOrder, evaluateAutoPause, sweep, start, stop,
};
