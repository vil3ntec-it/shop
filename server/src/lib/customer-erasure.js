'use strict';
/**
 * SEC-10 — حذفِ دادهٔ شخصیِ مشتریِ **یک دکان** با نگه‌داشتنِ اسنادِ مالیِ ناشناس‌شده
 * (§۶.۱۳، §۱۰ «نگهداری و حذف»، §۲۶ بندِ ۱۷). پشتِ پرچمِ `shop.customer_erasure`
 * (`live_config`، پیش‌فرض خاموش).
 *
 * چه کسی: صاحبِ دکان با تأییدِ دوبارهٔ هویت (`reauth`)، یا خودِ مشتری از حسابِ ویترینش با
 * بلیتِ تازهٔ «شماره تأیید شد» ِ همان شمارهٔ حساب. شاگرد، دکانِ دیگر و مشتریِ دیگر ⇒ رد.
 * دامنه همیشه **یک دکان** است (`shop_id` از توکن یا ویترین، هرگز از بدنه).
 *
 * چه می‌شود — همه در **یک** تراکنش، با سابقه‌ای در همان تراکنش:
 *   online_orders              نام، شماره (⇐ `erased:<شناسهٔ حذف>`؛ COUNT DISTINCT ِ مشتری‌ها همان
 *                              می‌ماند)، نشانی (جز `zone`، سندِ هزینهٔ ارسال)، `delivery.leaveWith` و
 *                              یادداشت پاک؛ نسلِ لینکِ پیگیری +۱. مبلغ‌ها، وضعیت، شمار و زمان‌ها همان.
 *   online_order_lines         یادداشتِ هر قلم پاک؛ کالا، مقدار و مبلغ همان.
 *   online_order_events،
 *   delivery_events            یادداشت پاک، `detail`/`reason` بی متنِ آزاد، شماره و مختصات (`scrub`)؛
 *                              کد، شناسه و عدد (مبلغ) می‌مانند.
 *   delivery_photos، order_pick_photos، delivery_locations   پاک.
 *   customer_notify_outbox     HMAC ِ شماره ⇐ `erased`، خطای متنی پاک، صفِ مانده ⇐ `expired`.
 *   shop_chat_conversations    شماره ⇐ `erased:<شناسهٔ گفت‌وگو>`، نام پاک، پیوندِ حساب NULL؛
 *     shop_chat_messages       متن/رسانه/meta پاک و «پاک‌شده» (ردیف و شمار می‌ماند)؛
 *     shop_chat_media، shop_chat_pins   پاک؛ shop_chat_reports: snapshot و یادداشت پاک.
 *   store_customer_carts، store_customer_favorites   سبد و ♥ ِ همین دکان پاک.
 *   storefront_area_requests   «خبرم کن» ِ همین شماره در همین دکان پاک.
 *
 * عمداً **نمی‌ماند/نمی‌رود** (فهرستِ `KEPT`، در پاسخ هم):
 *   order_ledger_docs (زنجیرهٔ هش، فقط شناسه)، online_order_refunds، courier_settlements،
 *   delivery_assignments، delivery_ratings — سندِ مالی/تسویه، بی دادهٔ شخصیِ مشتری؛
 *   shop_risk_lists و shop_chat_blocks — بازدارندهٔ سوءاستفاده؛ trust_cases — پروندهٔ مدیرِ سامانه؛
 *   store_customers/addresses/sessions — حسابِ سراسریِ مشتری، فقط با `DELETE /api/store/:slug/account`
 *   ِ خودش؛ دفترِ همگام‌سازیِ دستگاه (قرض‌داران و فروش‌ها) — دفترِ خودِ دستگاه است و سازوکارِ
 *   فرمانِ حذف به دستگاه نیست ⇒ تا دکان در برنامه پاکش نکند می‌ماند؛ audit_logs و پشتیبان‌ها.
 *
 * ⛔ سفارشِ باز (نه پایانی، نه تحویل/برداشته‌شده) ⇒ ۴۰۹ `open_orders`: پیک و دکان هنوز نشانی و شماره
 *    را لازم دارند. ⛔ برگشت‌ناپذیر: هیچ نسخه‌ای از دادهٔ پاک‌شده جایی نوشته نمی‌شود. ⛔ تکرارپذیر:
 *    بارِ دوم چیزی برای پاک کردن نمی‌یابد (همهٔ شمارش‌ها صفر) و هیچ ردیفی را عوض نمی‌کند.
 * سابقه (`audit_logs`): فقط شناسهٔ حذف، روش و شمارش‌ها — نه شماره، نه نام، نه شناسهٔ سفارش.
 */
const { tx, one, newId, now } = require('../db');
const audit = require('./audit');
const S = require('./order-states');
const v = require('./validate');
const { flagOf } = require('./orders');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG = 'shop.customer_erasure';
const ERASED_PREFIX = 'erased:';
const KEPT = Object.freeze([
  'order_ledger_docs', 'online_order_refunds', 'courier_settlements', 'delivery_assignments', 'delivery_ratings',
  'shop_risk_lists', 'shop_chat_blocks', 'trust_cases', 'store_customers', 'device_sync_ledger', 'audit_logs', 'backups',
]);
const COUNT_KEYS = Object.freeze([
  'orders', 'orderLines', 'orderEvents', 'deliveryEvents', 'deliveryPhotos', 'pickPhotos', 'deliveryLocations',
  'notifyOutbox', 'conversations', 'messages', 'chatMedia', 'chatPins', 'chatReports', 'carts', 'favorites', 'areaRequests',
]);

const flagOn = (shopId) => flagOf(shopId, FLAG);
/** پرچمِ دکانِ یک نشانیِ ویترین؛ نشانیِ ناشناس ⇒ false (بی خطا، بی نشت). */
async function flagOnSlug(slug) {
  const row = await one('SELECT shop_id FROM storefronts WHERE slug = $1', [String(slug || '').toLowerCase().slice(0, 80)]);
  return row ? flagOn(row.shop_id) : false;
}

/** سفارشی که دیگر به نشانی/شمارهٔ مشتری نیاز ندارد: پایانی، یا تحویل/برداشته‌شده. */
const settled = (status) => S.isTerminal(status) || status === 'delivered' || status === 'picked_up';

//  ── پاک‌سازیِ `detail` ِ رویدادها ────────────────────────────────────
//  کلیدهایی که دادهٔ شخصی‌اند، هر جا باشند؛ رشته فقط وقتی می‌ماند که کد یا شناسه باشد
//  (بی فاصله و بی حرفِ غیرِلاتین؛ رشتهٔ تمام‌رقمی — مثلِ شماره — نمی‌ماند).
const PII_KEY = /^(phone\d?|phones|customer_?(name|phone)|name|address|text|landmark|plus_?code|gate_?color|leave_?with|notes?|body|comment|lat|lng|latitude|longitude|accuracy(_?m)?|area|snapshot)$/i;
const CODE = /^[A-Za-z_][A-Za-z0-9_.:/-]{0,79}$/;
const ID = /^(?=[0-9A-Za-z_-]*[A-Za-z])[0-9A-Za-z_-]{8,64}$/;
const keepString = (s) => CODE.test(s) || ID.test(s);

function scrub(x) {
  if (x === null || typeof x === 'number' || typeof x === 'boolean') return x;
  if (typeof x === 'string') return keepString(x) ? x : '';
  if (Array.isArray(x)) return x.map(scrub);
  if (typeof x === 'object') {
    const out = {};
    for (const [k, val] of Object.entries(x)) if (!PII_KEY.test(k)) out[k] = scrub(val);
    return out;
  }
  return null;
}

const asObj = (x) => {
  if (x && typeof x === 'object') return x;
  try { const o = JSON.parse(String(x || '{}')); return o && typeof o === 'object' ? o : {}; } catch { return {}; }
};
const ids = (rows, k = 'id') => rows.map((r) => r[k]);

/** یادداشت/دلیل/جزئیاتِ رویدادهای یک جدول؛ فقط ردیفی که واقعاً عوض می‌شود نوشته می‌شود. */
async function scrubEvents(c, table, shopId, orderIds) {
  if (!orderIds.length) return 0;
  const rows = (await c.query(
    `SELECT id, note, reason, detail FROM ${table} WHERE shop_id=$1 AND order_id = ANY($2::text[]) ORDER BY id`,
    [shopId, orderIds]
  )).rows;
  let n = 0;
  for (const r of rows) {
    const before = asObj(r.detail);
    const detail = scrub(before);
    const reason = keepString(String(r.reason || '')) ? String(r.reason || '') : '';
    if (r.note === '' && reason === String(r.reason || '') && JSON.stringify(detail) === JSON.stringify(before)) continue;
    await c.query(`UPDATE ${table} SET note='', reason=$2, detail=$3::jsonb WHERE id=$1`, [r.id, reason, JSON.stringify(detail)]);
    n += 1;
  }
  return n;
}

/**
 * هدف: `{ phone }` (هر شکلی که validate.phone بپذیرد)، `{ orderId }` (مشتریِ همان سفارش؛ سفارشِ
 * بی شماره ⇒ فقط همان سفارش) یا — فقط برای مشتری — `{ phone, customerId }` ِ حسابِ خودش.
 */
async function resolveTarget(c, shopId, input) {
  const b = input && typeof input === 'object' ? input : {};
  let phone = null;
  let mode = 'phone';
  const orderOnly = [];
  if (b.orderId !== undefined && b.orderId !== null && b.orderId !== '') {
    mode = 'order';
    const o = (await c.query('SELECT id, customer_phone FROM online_orders WHERE shop_id=$1 AND id=$2',
      [shopId, String(b.orderId).slice(0, 80)])).rows[0];
    if (!o) throw notFound('این سفارش در این دکان نیست', 'order_not_found');
    if (o.customer_phone && !o.customer_phone.startsWith(ERASED_PREFIX)) phone = o.customer_phone;
    else orderOnly.push(o.id);
  } else if (b.phone !== undefined && b.phone !== null && b.phone !== '') {
    phone = v.phone(b.phone, { required: true });
  } else {
    throw badRequest('شمارهٔ مشتری یا شناسهٔ سفارش لازم است', 'bad_target');
  }
  const forms = phone ? v.phoneForms(phone) : [];
  let customerIds = b.customerId ? [String(b.customerId)] : [];
  if (forms.length && !b.customerId) {
    customerIds = ids((await c.query('SELECT id FROM store_customers WHERE phone = ANY($1::text[])', [forms])).rows);
  }
  return { forms, orderOnly, customerIds, mode: b.customerId ? 'account' : mode };
}

class DryRun extends Error {
  constructor(out) { super('dry-run'); this.out = out; }
}

/**
 * پاک/ناشناس کردن. `dryRun` ⇒ همان کار در تراکنشی که برگردانده می‌شود: شمارش‌ها دقیقاً همان
 * چیزی‌اند که اجرای واقعی می‌کند، و هیچ چیزی نوشته نمی‌شود (سابقه هم نه).
 * @param {string} shopId
 * @param {{phone?, orderId?, customerId?}} target
 * @param {{dryRun?: boolean, by: 'owner'|'customer', userId?: string, ip?: string}} opts
 */
async function erase(shopId, target, { dryRun = false, by, userId = '', ip = '' } = {}) {
  const erasureId = newId('era');
  const placeholder = ERASED_PREFIX + erasureId;
  try {
    return await tx(async (c) => {
      const t = await resolveTarget(c, shopId, target);
      const orders = (await c.query(
        `SELECT id, status FROM online_orders
          WHERE shop_id=$1 AND (customer_phone = ANY($2::text[]) OR id = ANY($3::text[]))
          ORDER BY id FOR UPDATE`,
        [shopId, t.forms, t.orderOnly]
      )).rows;
      const open = orders.filter((o) => !settled(o.status)).length;
      if (open && !dryRun) {
        const e = conflict(`${open} سفارشِ بازِ این مشتری هنوز تمام نشده است؛ پس از پایانشان دوباره بخواهید`, 'open_orders');
        e.data = { openOrders: open };
        throw e;
      }
      const orderIds = ids(orders);
      const at = now();
      const counts = Object.fromEntries(COUNT_KEYS.map((k) => [k, 0]));
      const q = async (sql, params) => (await c.query(sql, params)).rows.length;

      if (orderIds.length) {
        counts.orders = await q(
          `UPDATE online_orders SET customer_name='',
                  customer_phone = CASE WHEN customer_phone = '' OR customer_phone LIKE 'erased:%' THEN customer_phone ELSE $3 END,
                  address = CASE WHEN address->'zone' IS NOT NULL THEN jsonb_build_object('zone', address->'zone') ELSE '{}'::jsonb END,
                  delivery = delivery - 'leaveWith', note='', track_gen = track_gen + 1, version = version + 1
            WHERE shop_id=$1 AND id = ANY($2::text[])
              AND (customer_name <> '' OR (customer_phone <> '' AND customer_phone NOT LIKE 'erased:%') OR note <> ''
                   OR (address - 'zone') <> '{}'::jsonb OR (delivery->'leaveWith') IS NOT NULL)
            RETURNING id`,
          [shopId, orderIds, placeholder]
        );
        counts.orderLines = await q(
          `UPDATE online_order_lines SET note='' WHERE shop_id=$1 AND order_id = ANY($2::text[]) AND note <> '' RETURNING id`,
          [shopId, orderIds]
        );
        counts.orderEvents = await scrubEvents(c, 'online_order_events', shopId, orderIds);
        counts.deliveryEvents = await scrubEvents(c, 'delivery_events', shopId, orderIds);
        counts.deliveryPhotos = await q('DELETE FROM delivery_photos WHERE shop_id=$1 AND order_id = ANY($2::text[]) RETURNING id', [shopId, orderIds]);
        counts.pickPhotos = await q('DELETE FROM order_pick_photos WHERE shop_id=$1 AND order_id = ANY($2::text[]) RETURNING id', [shopId, orderIds]);
        counts.deliveryLocations = await q(
          `DELETE FROM delivery_locations WHERE shop_id=$1 AND assignment_id IN (
             SELECT id FROM delivery_assignments WHERE shop_id=$1 AND order_id = ANY($2::text[])) RETURNING assignment_id`,
          [shopId, orderIds]
        );
        counts.notifyOutbox = await q(
          `UPDATE customer_notify_outbox SET phone_key='erased', last_error='',
                  status = CASE WHEN status IN ('pending','sending') THEN 'expired' ELSE status END, updated_at=$3
            WHERE shop_id=$1 AND order_id = ANY($2::text[]) AND phone_key <> 'erased' RETURNING id`,
          [shopId, orderIds, at]
        );
      }

      //  گفت‌وگوها: همین شماره، همین حساب یا همین سفارش‌ها — فقط آن‌که هنوز چیزی برای پاک کردن دارد
      const convIds = ids((await c.query(
        `SELECT cv.id FROM shop_chat_conversations cv
          WHERE cv.shop_id=$1
            AND (cv.customer_phone = ANY($2::text[]) OR cv.customer_id = ANY($3::text[]) OR (cv.order_id <> '' AND cv.order_id = ANY($4::text[])))
            AND (cv.customer_phone NOT LIKE 'erased:%' OR cv.customer_name <> '' OR cv.customer_id IS NOT NULL
                 OR EXISTS (SELECT 1 FROM shop_chat_messages m WHERE m.conversation_id = cv.id
                              AND (m.body <> '' OR m.meta <> '{}'::jsonb OR m.media_id IS NOT NULL OR m.thumb_id IS NOT NULL OR m.deleted_at IS NULL))
                 OR EXISTS (SELECT 1 FROM shop_chat_media x WHERE x.conversation_id = cv.id)
                 OR EXISTS (SELECT 1 FROM shop_chat_reports r WHERE r.conversation_id = cv.id AND (r.snapshot <> '{}'::jsonb OR r.note <> '')))
          ORDER BY cv.id FOR UPDATE`,
        [shopId, t.forms, t.customerIds, orderIds]
      )).rows);
      if (convIds.length) {
        counts.conversations = await q(
          `UPDATE shop_chat_conversations SET customer_phone = 'erased:' || id, customer_name='', customer_id=NULL, rev = rev + 1
            WHERE shop_id=$1 AND id = ANY($2::text[]) RETURNING id`,
          [shopId, convIds]
        );
        //  همان «پاک برای همه» ِ چت (rev ِ تازهٔ گفت‌وگو ⇒ برنامه‌ها پاک شدن را می‌گیرند)
        counts.messages = await q(
          `UPDATE shop_chat_messages m SET body='', meta='{}'::jsonb, media_id=NULL, thumb_id=NULL,
                  deleted_at = COALESCE(m.deleted_at, $3), deleted_by = CASE WHEN m.deleted_by = '' THEN 'erasure' ELSE m.deleted_by END,
                  rev = cv.rev
             FROM shop_chat_conversations cv
            WHERE cv.id = m.conversation_id AND cv.shop_id=$1 AND m.conversation_id = ANY($2::text[])
              AND (m.body <> '' OR m.meta <> '{}'::jsonb OR m.media_id IS NOT NULL OR m.thumb_id IS NOT NULL OR m.deleted_at IS NULL)
            RETURNING m.id`,
          [shopId, convIds, at]
        );
        counts.chatMedia = await q('DELETE FROM shop_chat_media WHERE shop_id=$1 AND conversation_id = ANY($2::text[]) RETURNING id', [shopId, convIds]);
        counts.chatPins = await q('DELETE FROM shop_chat_pins WHERE shop_id=$1 AND conversation_id = ANY($2::text[]) RETURNING message_id', [shopId, convIds]);
        counts.chatReports = await q(
          `UPDATE shop_chat_reports SET snapshot='{}'::jsonb, note=''
            WHERE shop_id=$1 AND conversation_id = ANY($2::text[]) AND (snapshot <> '{}'::jsonb OR note <> '') RETURNING id`,
          [shopId, convIds]
        );
      }

      if (t.customerIds.length) {
        counts.carts = await q('DELETE FROM store_customer_carts WHERE shop_id=$1 AND customer_id = ANY($2::text[]) RETURNING customer_id', [shopId, t.customerIds]);
        counts.favorites = await q('DELETE FROM store_customer_favorites WHERE shop_id=$1 AND customer_id = ANY($2::text[]) RETURNING product_id', [shopId, t.customerIds]);
      }
      if (t.forms.length) {
        counts.areaRequests = await q('DELETE FROM storefront_area_requests WHERE shop_id=$1 AND phone = ANY($2::text[]) RETURNING id', [shopId, t.forms]);
      }

      const total = COUNT_KEYS.reduce((s, k) => s + counts[k], 0);
      const out = { dryRun: Boolean(dryRun), mode: t.mode, openOrders: open, counts, total, kept: KEPT };
      if (dryRun) throw new DryRun(out);
      //  سابقه در همان تراکنش؛ ⛔ بی شماره، نام، نشانی یا شناسهٔ سفارش/حساب — فقط شمارش‌ها
      await audit.logIn(c, {
        shopId, actorType: by === 'customer' ? 'customer' : 'user', userId: by === 'customer' ? '' : userId,
        action: by === 'customer' ? 'store_customer.erased' : 'shop.customer_erased',
        targetType: 'customer_erasure', targetId: erasureId,
        detail: { by, mode: t.mode, counts, total }, ip: by === 'customer' ? '' : ip,
      });
      return { ...out, erasureId };
    });
  } catch (err) {
    if (err instanceof DryRun) return err.out;
    throw err;
  }
}

module.exports = { FLAG, KEPT, COUNT_KEYS, ERASED_PREFIX, flagOn, flagOnSlug, erase, scrub, settled };
