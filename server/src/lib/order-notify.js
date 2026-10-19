'use strict';
/**
 * اعلانِ سفارش حتی وقتی برنامهٔ گوشی بسته است — v3 §۳۰ (NOTIF).
 *
 *   مشتری ─▶ orders.create ─┬─ سفارش + ردیف‌ها + رویداد       ┐ یک تراکنش
 *                           └─ ردیفِ order_notify_outbox       ┘ (enqueueIn)
 *   کارگرِ همین سرور ─▶ push.sendTo({shopId, app:'shop'}) ─▶ FCM ─▶ گوشی‌های دکان
 *   گوشی ─▶ POST /api/shop/order-notify/:id/ack  (رسید / باز شد)
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • ⛔ پیش‌فرض خاموش: بی کلیدِ `shop.order_notify` در «تنظیماتِ زنده»
 *     (scope = دکان یا `all`) هیچ ردیفی نوشته نمی‌شود و هیچ پیامی نمی‌رود.
 *   • فقط سفارشِ **مشتری** (`actor.type === 'customer'`)؛ سفارشِ دستیِ
 *     کارمند همان رفتارِ پیشین را دارد (هیچ اعلانی).
 *   • ثبتِ سفارش از فرستادن جداست (§۳۰.۱ بندِ ۶): ردیف در همان تراکنش
 *     نوشته می‌شود، فرستادن بعداً؛ خطای پوش هرگز سفارش را پس نمی‌گیرد.
 *     کامپیوترِ دکان هیچ نقشی ندارد (§۳۰.۳) — کارگر روی خودِ سرور است.
 *   • سه حالِ جدا (§۳۰.۱ بندِ ۷): «در سرور ثبت شد» (ردیف)، «سرویسِ پوش
 *     پذیرفت» (`provider_accepted`) و «کسی باز کرد / سفارش رسیدگی شد»
 *     (`acked`). پذیرفتنِ پوش اثباتِ دیده‌شدن نیست.
 *   • تلاش محدود (NOTIF-11): حداکثر `MAX_ATTEMPTS` بار فرستادن با فاصلهٔ
 *     فزاینده، سپس `MAX_REMINDERS` یادآوری اگر کسی باز نکرد؛ شمارنده‌ها
 *     هنگامِ «گرفتن» بالا می‌روند، پس حتی کارگری که وسطِ کار بمیرد هم
 *     نمی‌تواند حلقهٔ بی‌پایان بسازد.
 *   • کانالِ پشتیبان (§۳۰.۴) حداکثر یک بار برای هر اعلان (`fallback_at`
 *     اتمی گرفته می‌شود). امروز هیچ سرویس‌دهندهٔ پیامکی وصل نیست ⇒ حالِ
 *     صادقانهٔ `not_configured`؛ **هیچ پیامِ واقعی‌ای نمی‌رود**. پیامکِ
 *     OTP جداست و این‌جا به کار نمی‌رود (گیرنده، قالب و هزینهٔ دیگری دارد).
 *   • متنِ اعلان هیچ دادهٔ مشتری ندارد (§۳۰.۱ بندِ ۴): فقط شمارهٔ سفارش.
 *     لمسِ اعلان باید `/api/shop/orders/:id` را بخواند (دسترسی دوباره
 *     سنجیده می‌شود)؛ payload منبعِ داده نیست (بندِ ۵).
 *   • سفارشی که از «تازه» بیرون رفته (کسی قبول/رد کرده) دیگر زنگ نمی‌زند.
 *   • گیرنده فقط عضوِ فعالی است که `orders.view` دارد (صاحب همیشه) —
 *     همان سدِّ `/api/shop/orders`؛ پیک و عضوِ بسته‌شده زنگ نمی‌گیرند.
 */
const { query, one, many, now } = require('../db');
const push = require('./push');
const { can } = require('./permissions');
const staffPerms = require('./staff-perms');
const permCore = require('./perm-core');
const { ulid } = require('./ulid');
const { badRequest, notFound } = require('../middleware/errors');

const FLAG_KEY = 'shop.order_notify';
const EVENT = 'order_created';
const CHANNEL = 'orders';

const num = (k, d) => { const v = Number(process.env[k]); return Number.isFinite(v) && v > 0 ? v : d; };

const MAX_ATTEMPTS  = 5;                                         // ۱۵ث · ۳۰ث · ۱د · ۲د ⇒ ~۴ دقیقه
const MAX_REMINDERS = 2;
const BACKOFF_MS    = num('ORDER_NOTIFY_BACKOFF_MS', 15_000);
const ACK_WINDOW_MS = num('ORDER_NOTIFY_ACK_WINDOW_MS', 2 * 60_000);
//  بیشتر از بدترین زمانِ یک دورِ فرستادن (هر دستگاه ۱۵ ثانیه سقف دارد)
const STALE_MS      = num('ORDER_NOTIFY_STALE_MS', 3 * 60_000);
const TICK_MS       = num('ORDER_NOTIFY_TICK_MS', 2000);
const CLAIM_LIMIT   = 20;
const MAX_LIST      = 50;

const LIVE = ['pending', 'sending', 'provider_accepted'];
const OPEN = [...LIVE, 'failed', 'unacked'];
const ACK_KINDS = ['received', 'opened'];

// ════════════════════════════════════════════════════════════════════
//  پرچم
// ════════════════════════════════════════════════════════════════════

function decode(text) { try { return JSON.parse(text); } catch { return null; } }

/**
 * همان قاعدهٔ `orders.flagOf` (مقدارِ دکان بر `all` جلو می‌افتد؛ فقط `true`
 * روشن است) — ولی با `run` تا درونِ تراکنشِ ساختِ سفارش هم خوانده شود.
 */
async function flagIn(run, shopId) {
  const rows = (await run(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [FLAG_KEY, 'all', String(shopId)]
  )).rows;
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}
const flagOf = (shopId) => flagIn((t, p) => query(t, p), shopId);

// ════════════════════════════════════════════════════════════════════
//  نوشتن — درونِ تراکنشِ `orders.create`
// ════════════════════════════════════════════════════════════════════

/**
 * قلابِ `notify`ِ `orders.create`. پرچم خاموش ⇒ هیچ (null). تکرارِ همان
 * سفارش ⇒ ردیفِ دوم ساخته نمی‌شود (یکتاییِ order_id + event).
 * @returns {Promise<string|null>} شناسهٔ اعلان
 */
async function enqueueIn(run, { shopId, orderId, orderNo, at = now() }) {
  if (!(await flagIn(run, shopId))) return null;
  const r = await run(
    `INSERT INTO order_notify_outbox (id, shop_id, order_id, order_no, event, status,
                                      next_attempt_at, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,'pending',$6,$6,$6)
     ON CONFLICT (order_id, event) DO NOTHING RETURNING id`,
    [ulid(at), shopId, orderId, orderNo, EVENT, at]
  );
  soon();
  return r.rows[0] ? r.rows[0].id : null;
}

// ════════════════════════════════════════════════════════════════════
//  کانالِ پشتیبان (§۳۰.۴) — امروز «تنظیم نشده»
// ════════════════════════════════════════════════════════════════════

/**
 * `{ channel: 'sms', send: async ({shopId, orderId, orderNo, notifId, reason}) => ({ok, error}) }`
 *
 * ⛔ در کارِ عادی هیچ‌وقت مقدار نمی‌گیرد: سرویس‌دهندهٔ پیامکِ هشدار به مالک
 * (با قالب، رضایت و سقفِ هزینهٔ خودش) بیرونی است و هنوز تصویب نشده
 * (BLOCKED-EXTERNAL). فقط `test/*.test.js` آن را می‌نشاند تا «حداکثر یک
 * بار» سنجیده شود — همان الگوی `push.setDeliver`.
 */
let fallback = null;
function setFallback(fb) { fallback = fb && typeof fb.send === 'function' ? fb : null; }

/** اسلاتِ یگانهٔ کانالِ پشتیبان را اتمی می‌گیرد؛ دومی هیچ‌وقت نمی‌فرستد. */
async function escalate(row, lock, at, reason) {
  const won = await query(
    `UPDATE order_notify_outbox SET fallback_at=$2
      WHERE id=$1 AND fallback_at IS NULL AND status='sending' AND locked_at=$3 RETURNING id`,
    [row.id, at, lock]
  );
  if (!won.rows[0]) return {};
  if (!fallback) return { fallback_channel: 'sms', fallback_state: 'not_configured' };
  const channel = String(fallback.channel || 'sms').slice(0, 20);
  try {
    const out = await fallback.send({
      shopId: row.shop_id, orderId: row.order_id, orderNo: Number(row.order_no), notifId: row.id, reason,
    });
    return out && out.ok
      ? { fallback_channel: channel, fallback_state: 'provider_accepted' }
      : { fallback_channel: channel, fallback_state: 'provider_error' };
  } catch {
    return { fallback_channel: channel, fallback_state: 'provider_error' };
  }
}

// ════════════════════════════════════════════════════════════════════
//  فرستادن
// ════════════════════════════════════════════════════════════════════

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);

/** بی نام، تلفن، نشانی یا مبلغ — صفحهٔ قفل جای دادهٔ مشتری نیست. */
function messageFor(row, reminder) {
  const no = Number(row.order_no);
  return {
    title: `سفارشِ تازه — شمارهٔ ${faNum(no)}`,
    body: reminder ? 'این سفارش هنوز باز نشده است.' : 'برای دیدنِ جزئیات باز کنید.',
    channel: CHANNEL,
    data: {
      type: 'order_notify', notifId: row.id, orderId: row.order_id, orderNo: String(no),
      shopId: row.shop_id, reminder: reminder ? '1' : '0',
    },
  };
}

const RECIPIENT_PERMISSION = 'orders.view';

/**
 * کاربرانی که اعلانِ سفارش می‌گیرند: عضوِ فعالِ دکان که `orders.view` دارد
 * (همان سنجشِ `requirePermission`: سقفِ نقش، سپس لایه‌ها اگر کلید لایه‌پذیر
 * باشد) + صاحبِ دکان همیشه. پیک (`courier`) و عضوِ بسته‌شده بیرون‌اند.
 */
async function recipientIds(shopId) {
  const rows = await many(
    `SELECT user_id, role FROM shop_members WHERE shop_id=$1 AND status='active'
     UNION SELECT owner_user_id AS user_id, 'owner' AS role FROM shops WHERE id=$1`,
    [shopId]
  );
  const out = new Set();
  for (const r of rows) {
    if (!r.user_id || !can(r.role, RECIPIENT_PERMISSION)) continue;
    if (r.role !== 'owner' && staffPerms.isLayered(RECIPIENT_PERMISSION)) {
      const layers = await staffPerms.layersFor(shopId, r.role, r.user_id);
      if (!permCore.allows(r.role, layers, RECIPIENT_PERMISSION)) continue;
    }
    out.add(String(r.user_id));
  }
  return [...out];
}

/** همان راهِ موجودِ پوش؛ خروجی همیشه یک نتیجه است، هرگز استثنا. */
async function send(row, reminder) {
  try {
    const onlyUserIds = await recipientIds(row.shop_id);
    const out = (await push.sendTo({ shopId: row.shop_id, app: 'shop', onlyUserIds }, messageFor(row, reminder))) || {};
    const sent = Number(out.sent || 0);
    const total = Number(out.total || 0);
    if (sent > 0) return { sent, total, result: 'ok', error: '' };
    if (out.skipped) return { sent: 0, total, result: String(out.skipped).slice(0, 40), error: '' };
    return { sent: 0, total, result: 'provider_error', error: String(out.error || 'هیچ دستگاهی نپذیرفت').slice(0, 300) };
  } catch (err) {
    return { sent: 0, total: 0, result: 'provider_error', error: String((err && err.message) || err).slice(0, 300) };
  }
}

const backoffMs = (attempt) => BACKOFF_MS * Math.pow(2, Math.max(0, attempt - 1));

/**
 * ردیف‌های سررسیده را می‌گیرد؛ شمارندهٔ همان مرحله همین‌جا بالا می‌رود
 * (پیش از فرستادن)، پس هر «گرفتن» یک تلاشِ شمرده‌شده است.
 */
async function claimDue(at) {
  const due = `((status IN ('pending','provider_accepted') AND next_attempt_at <= $1)
               OR (status='sending' AND locked_at < $2))`;
  const r = await query(
    `UPDATE order_notify_outbox
        SET status='sending', locked_at=$1, updated_at=$1,
            attempts  = attempts  + CASE WHEN provider_accepted_at IS NULL THEN 1 ELSE 0 END,
            reminders = reminders + CASE WHEN provider_accepted_at IS NULL THEN 0 ELSE 1 END
      WHERE id IN (SELECT id FROM order_notify_outbox WHERE ${due}
                    ORDER BY next_attempt_at ASC LIMIT ${CLAIM_LIMIT})
        AND ${due}
      RETURNING *`,
    [at, at - STALE_MS]
  );
  return r.rows;
}

/** نوشتنِ نتیجه — فقط اگر ردیف هنوز در دستِ همین تلاش است (CAS). */
async function settle(row, lock, set) {
  const keys = Object.keys(set);
  const cols = keys.map((k, i) => `${k}=$${i + 3}`).join(', ');
  const r = await query(
    `UPDATE order_notify_outbox SET ${cols}, locked_at=NULL
      WHERE id=$1 AND status='sending' AND locked_at=$2 RETURNING *`,
    [row.id, lock, ...keys.map((k) => set[k])]
  );
  return r.rows[0] || null;
}

async function processRow(row, at) {
  const lock = Number(row.locked_at);
  const order = await one('SELECT status FROM online_orders WHERE id=$1 AND shop_id=$2', [row.order_id, row.shop_id]);
  if (!order) return settle(row, lock, { status: 'cancelled', last_result: 'order_missing', updated_at: at });
  //  کسی سفارش را قبول/رد کرده (از هر دستگاهی) ⇒ دیگر زنگ لازم نیست
  if (order.status !== 'new') {
    return settle(row, lock, { status: 'acked', acked_at: at, ack_source: 'order_status', updated_at: at });
  }
  //  کلیدِ قطعِ فوری: پرچم خاموش شد ⇒ هیچ پیامِ دیگری
  if (!(await flagOf(row.shop_id))) {
    return settle(row, lock, { status: 'cancelled', last_result: 'flag_off', updated_at: at });
  }

  const reminder = row.provider_accepted_at != null;
  if (reminder) {
    const n = Number(row.reminders);
    if (n > MAX_REMINDERS) {
      const fb = await escalate(row, lock, at, 'unacked');
      return settle(row, lock, { status: 'unacked', reminders: MAX_REMINDERS, ...fb, updated_at: at });
    }
    const out = await send(row, true);
    return settle(row, lock, {
      status: 'provider_accepted', next_attempt_at: at + ACK_WINDOW_MS,
      devices_total: out.total, devices_sent: out.sent, last_result: out.result, last_error: out.error,
      updated_at: at,
    });
  }

  const attempts = Number(row.attempts);
  if (attempts > MAX_ATTEMPTS) {
    //  فقط وقتی کارگری وسطِ کار مرده و ردیف چند بار دوباره گرفته شده
    const fb = await escalate(row, lock, at, 'failed');
    return settle(row, lock, { status: 'failed', attempts: MAX_ATTEMPTS, ...fb, updated_at: at });
  }
  const out = await send(row, false);
  const base = { devices_total: out.total, devices_sent: out.sent, last_result: out.result, last_error: out.error, updated_at: at };
  if (out.sent > 0) {
    return settle(row, lock, { ...base, status: 'provider_accepted', provider_accepted_at: at, next_attempt_at: at + ACK_WINDOW_MS });
  }
  if (attempts >= MAX_ATTEMPTS) {
    const fb = await escalate(row, lock, at, 'failed');
    return settle(row, lock, { ...base, status: 'failed', ...fb });
  }
  return settle(row, lock, { ...base, status: 'pending', next_attempt_at: at + backoffMs(attempts) });
}

let ticking = false;

/**
 * یک دورِ کارگر. `at` فقط برای آزمون (ساعتِ ساختگی)؛ در کارِ عادی همان
 * ساعتِ سرور است.
 * @returns {Promise<number>} شمارِ ردیف‌های گرفته‌شده
 */
async function tick(at = now()) {
  if (ticking) return 0;
  ticking = true;
  try {
    const rows = await claimDue(at);
    for (const row of rows) {
      //  خطای دیتابیس ⇒ ردیف در `sending` می‌ماند و بعد از STALE_MS دوباره
      //  گرفته می‌شود — با شمارندهٔ بالا رفته، پس باز هم محدود
      try { await processRow(row, at); } catch (err) { console.error('[order-notify]', err.message); }
    }
    return rows.length;
  } finally {
    ticking = false;
  }
}

let timer = null;
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[order-notify]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}
/** سفارشِ تازه ⇒ زودتر از تیکِ بعدی؛ اگر تراکنش هنوز commit نشده، تیکِ بعدی برش می‌دارد. */
function soon() {
  if (!timer) return;
  const t = setTimeout(() => tick().catch((err) => console.error('[order-notify]', err.message)), 300);
  if (t.unref) t.unref();
}

// ════════════════════════════════════════════════════════════════════
//  خواندن و رسید — برای برنامه‌های دکان
// ════════════════════════════════════════════════════════════════════

const n = (v) => (v == null ? null : Number(v));

function shape(r) {
  return {
    id: r.id,
    orderId: r.order_id,
    orderNo: Number(r.order_no),
    event: r.event,
    status: r.status,
    attempts: Number(r.attempts),
    reminders: Math.min(Number(r.reminders), MAX_REMINDERS),
    nextAttemptAt: LIVE.includes(r.status) ? n(r.next_attempt_at) : null,
    devicesTotal: Number(r.devices_total),
    devicesSent: Number(r.devices_sent),
    lastResult: r.last_result,
    lastError: r.last_error,
    createdAt: n(r.created_at),
    providerAcceptedAt: n(r.provider_accepted_at),
    firstReceivedAt: n(r.first_received_at),
    ackedAt: n(r.acked_at),
    ackSource: r.ack_source,
    fallback: { channel: r.fallback_channel, state: r.fallback_state, at: n(r.fallback_at) },
    //  لمسِ اعلان ⇒ همین را بخوان (دسترسی و دادهٔ تازه از سرور)، نه payload
    orderPath: `/api/shop/orders/${r.order_id}`,
  };
}

/** وضعیتِ صادقانه برای برنامه (§۳۰.۲ بندِ ۳): پوش هست؟ این دستگاه ثبت شده؟ */
async function config(shopId, { userId = '', deviceUid = '' } = {}) {
  const enabled = await flagOf(shopId);
  const cfg = await push.current();
  //  فقط دستگاه‌هایی که واقعاً اعلان می‌گیرند (دارندگانِ `orders.view`)
  const devices = await one(
    `SELECT COUNT(*)::int AS n FROM push_tokens WHERE status='active' AND app='shop' AND shop_id=$1
       AND user_id = ANY($2::text[])`, [shopId, await recipientIds(shopId)]
  );
  const mine = await one(
    `SELECT COUNT(*)::int AS n FROM push_tokens WHERE status='active' AND app='shop' AND shop_id=$1 AND
       ${deviceUid ? 'device_uid=$2' : 'user_id=$2'}`,
    [shopId, deviceUid || userId]
  );
  return {
    enabled,
    push: {
      configured: !!cfg.serviceAccount,
      enabled: !!cfg.enabled,
      shopDevices: devices.n,
      thisDeviceRegistered: mine.n > 0,
    },
    fallback: { sms: fallback ? 'configured' : 'not_configured' },
    policy: { maxAttempts: MAX_ATTEMPTS, backoffMs: BACKOFF_MS, ackWindowMs: ACK_WINDOW_MS, maxReminders: MAX_REMINDERS },
  };
}

/** اعلان‌هایی که هنوز کسی باز نکرده و سفارششان هنوز «تازه» است — برای برنامهٔ باز. */
async function pending(shopId, { limit } = {}) {
  const cap = Math.min(Math.max(1, Number(limit) || MAX_LIST), MAX_LIST);
  const rows = await many(
    `SELECT x.* FROM order_notify_outbox x JOIN online_orders o ON o.id = x.order_id
      WHERE x.shop_id=$1 AND x.status IN (${OPEN.map((s) => `'${s}'`).join(',')}) AND o.status='new'
      ORDER BY x.created_at DESC LIMIT $2`,
    [shopId, cap]
  );
  return rows.map(shape);
}

/**
 * رسیدِ دستگاه. `received` فقط ثبت می‌شود؛ `opened` اعلان را می‌بندد
 * (یادآوری و کانالِ پشتیبان دیگر نمی‌روند). تکرار ⇒ همان نتیجه.
 */
async function ack(shopId, notifId, { kind, userId = '', deviceUid = '' } = {}) {
  const k = kind === undefined ? 'opened' : String(kind);
  if (!ACK_KINDS.includes(k)) throw badRequest('نوعِ رسید معتبر نیست', 'bad_ack_kind');
  const id = String(notifId || '').slice(0, 64);
  const row = await one('SELECT id FROM order_notify_outbox WHERE id=$1 AND shop_id=$2', [id, shopId]);
  if (!row) throw notFound('اعلان پیدا نشد', 'notification_not_found');
  const at = now();
  const device = String(deviceUid || `user:${userId}`).slice(0, 120);
  await query(
    `INSERT INTO order_notify_receipts (notif_id, shop_id, device_key, kind, user_id, at)
     VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (notif_id, device_key, kind) DO NOTHING`,
    [id, shopId, device, k, userId, at]
  );
  //  «باز شد» یعنی «رسید» هم
  await query(
    'UPDATE order_notify_outbox SET first_received_at=$2 WHERE id=$1 AND first_received_at IS NULL', [id, at]
  );
  let changed = false;
  if (k === 'opened') {
    const r = await query(
      `UPDATE order_notify_outbox
          SET status='acked', acked_at=$2, ack_source='device', acked_by=$3, acked_device=$4,
              locked_at=NULL, updated_at=$2
        WHERE id=$1 AND status <> 'acked' RETURNING id`,
      [id, at, userId, device]
    );
    changed = !!r.rows[0];
  }
  const fresh = await one('SELECT * FROM order_notify_outbox WHERE id=$1', [id]);
  return { notification: shape(fresh), changed };
}

module.exports = {
  FLAG_KEY, EVENT, CHANNEL, MAX_ATTEMPTS, MAX_REMINDERS, BACKOFF_MS, ACK_WINDOW_MS, STALE_MS,
  flagOf, enqueueIn, messageFor, tick, start, stop, setFallback, config, pending, ack, shape, recipientIds,
};
