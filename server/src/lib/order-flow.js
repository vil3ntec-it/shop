'use strict';
/**
 * جریانِ سفارشِ آنلاین پس از ثبت (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۳): «پرداخت پس از پذیرش» و «به فاکتور» ِ یک‌ضرب.
 * همان ماشینِ وضعیتِ `order-states.js` است — وضعیتِ تازه‌ای نیست؛ این پرونده فقط دو لایه رویش می‌گذارد.
 *
 *   پرداخت ─ تنظیمِ دکان `storefronts.pay_after_accept` (مهاجرتِ ۲۱۰، پیش‌فرض خاموش ⇒ رفتارِ PAY-21 ِ امروز):
 *            روشن ⇒ سفارشِ ویترین تا «قبول» نشده پرداختِ آنلاین ندارد (`/pay` ⇒ ۴۰۹ `order_not_accepted`)؛ پس از
 *            قبول، پیگیریِ مشتری کارتِ «پرداخت» دارد: «نقد هنگامِ تحویل/برداشت» ([choose]) یا حساب‌پی (همان
 *            `/pay` وقتی برای همین دکان روشن است). انتخاب رویدادِ `note` با `detail.type = 'pay_choice'` است
 *            (مثلِ `ready_delay`، بی ستون) و وضعیتِ پرداخت/نقد را عوض نمی‌کند: نقد همان `cod_status` ِ امروز
 *            می‌ماند تا پیک یا پیشخوان بگیرد (`delivery-settle`، `orders.codIn`).
 *   فاکتور ─ [claimInvoice]: سفارشی که قلم‌هایش قطعی است (از «بسته‌بندی‌شده» به بعد — پس از بسته‌بندی هیچ قلمی
 *            عوض نمی‌شود) یک بار «فاکتور شد» می‌گیرد: رویدادِ `note` با `detail.type = 'invoice'` زیرِ قفلِ ردیف.
 *            برنامهٔ دکان فروشِ همان سفارش را با شناسهٔ ثابتِ `o-<سفارش>` و **زمانِ همین رویداد** می‌نویسد
 *            (`OrderFulfillment.fulfill` ⇒ `SalesEngine.record`) ⇒ دو بار زدن یا دو دستگاه یک فاکتور؛ تحویلِ
 *            بعدی همان فروش را «پیش‌تر ثبت شده» می‌بیند (یک بار کسرِ موجودی). سفارشِ فاکتورشده که لغو شود ⇒
 *            همان لغوِ فروشِ «برگشت به دکان» (`order-ledger.BACK_IN_SHOP`).
 *
 * ⛔ مشتری نه قیمت می‌گذارد نه سفارشِ خودش را قبول می‌کند: [choose] فقط «نقد» را می‌پذیرد و فقط پس از قبولِ
 *    فروشنده؛ مبلغ همیشه همان `total_minor` ِ سرور است. فاکتور فقط با `orders.manage` (درِ `routes/order-flow.js`).
 * ⛔ shop_id فقط از نشست یا ویترینِ باز (`sf.open`) — هرگز از بدنه.
 */
const { one, many, now, tx } = require('../db');
const S = require('./order-states');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict } = require('../middleware/errors');

/** قلم‌ها قطعی‌اند (پس از بسته‌بندی) و کالا هنوز به دکان برنگشته ⇒ فاکتورپذیر */
const INVOICE_STATUSES = Object.freeze(['packed', 'ready_for_pickup', 'out_for_delivery', 'delivery_failed',
  'retry_scheduled', 'picked_up', 'delivered']);
/** پس از قبول و پیش از رسیدن به دستِ مشتری ⇒ مشتری روشِ پرداخت را برمی‌گزیند */
const PAY_OPEN = Object.freeze(['accepted', 'picking', 'packed', 'ready_for_pickup', 'out_for_delivery',
  'delivery_failed', 'retry_scheduled']);
const CHOICES = Object.freeze(['cash']);
const MODE_AFTER = 'after_accept';
const MODE_ANY = 'any';
const PAID = ['paid', 'partially_refunded', 'refunded'];

const saleIdOf = (orderId) => `o-${orderId}`;
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};

/** تازه‌ترین رویدادِ `note` ِ همین نوع در خطِ زمانیِ جزئیات (`orders.detail`) — یا null */
function noteOf(events, type) {
  let hit = null;
  for (const e of events || []) if (e.kind === 'note' && e.detail && e.detail.type === type) hit = e;
  return hit;
}

/** «فاکتور شد» ِ یک سفارش (جزئیات) — `{ saleId, at, by }` یا null */
function invoiceOf(order) {
  const e = noteOf(order && order.events, 'invoice');
  return e ? { saleId: saleIdOf(order.id), at: Number(e.at), by: e.actorId || '' } : null;
}

/** انتخابِ مشتری (`cash`) یا '' */
function choiceOf(order) {
  const e = noteOf(order && order.events, 'pay_choice');
  return e && CHOICES.includes(e.detail.method) ? e.detail.method : '';
}

/**
 * حالِ پرداخت برای دیدنِ فروشنده و مشتری — خالص. `o` شکلِ جزئیاتِ سرور (`status`، `paymentStatus`،
 * `codStatus`) با `choice`؛ `mode` = `after_accept` یا `any`.
 *   awaiting_accept · awaiting_choice · cash · online_pending · paid_online · cash_collected · cash_settled ·
 *   refunded · closed
 */
function payStateOf(o, mode) {
  if (o.paymentStatus === 'refunded' || o.paymentStatus === 'partially_refunded') return 'refunded';
  if (o.paymentStatus === 'paid') return 'paid_online';
  if (o.paymentStatus === 'pending') return 'online_pending';
  if (o.codStatus === 'settled_to_shop') return 'cash_settled';
  if (o.codStatus === 'collected_by_courier' || o.codStatus === 'collected_in_store') return 'cash_collected';
  if (S.isTerminal(o.status) || ['returned_to_store', 'restocked'].includes(o.status)) return 'closed';
  if (mode === MODE_AFTER) {
    if (o.status === 'new') return 'awaiting_accept';
    if (o.choice !== 'cash' && PAY_OPEN.includes(o.status)) return 'awaiting_choice';
  }
  return 'cash';
}

/** تنظیمِ دکان؛ دکانِ بی ویترین ⇒ `any` */
async function modeOf(shopId) {
  const r = await one('SELECT pay_after_accept FROM storefronts WHERE shop_id = $1', [String(shopId)]);
  return r && r.pay_after_accept === true ? MODE_AFTER : MODE_ANY;
}

/** فقط سفارشِ ویترین «پرداخت پس از پذیرش» دارد؛ سفارشِ دستیِ کارمند همان نقدِ امروز */
const modeFor = (mode, source) => (source === 'storefront' ? mode : MODE_ANY);

/** `payStep` و `invoice` ِ جزئیاتِ کارمند (`GET /shop/orders/:id`) */
async function staffView(shopId, order) {
  const mode = modeFor(await modeOf(shopId), order.source);
  const choice = choiceOf(order);
  return {
    invoice: invoiceOf(order),
    payStep: { mode, state: payStateOf({ ...order, choice }, mode), choice },
  };
}

/** ردیف و رویدادهای `note` ِ پرداخت/فاکتورِ یک سفارش — برای پیگیری و درها (بی بارِ کاملِ جزئیات) */
async function factsOf(shopId, orderId) {
  const o = await one(
    `SELECT id, status, source, payment_status, cod_status, fulfillment, total_minor, is_test
       FROM online_orders WHERE shop_id = $1 AND id = $2`, [String(shopId), String(orderId || '')]
  );
  if (!o) return null;
  const notes = await many(
    `SELECT detail FROM online_order_events WHERE order_id = $1 AND kind = 'note'
        AND detail->>'type' IN ('pay_choice', 'invoice') ORDER BY seq`, [o.id]
  );
  let choice = '';
  let invoiced = false;
  for (const n of notes) {
    const d = asJson(n.detail);
    if (d.type === 'invoice') invoiced = true;
    if (d.type === 'pay_choice' && CHOICES.includes(d.method)) choice = d.method;
  }
  return {
    status: o.status, source: o.source, paymentStatus: o.payment_status, codStatus: o.cod_status,
    fulfillment: o.fulfillment, totalMinor: Number(o.total_minor), isTest: o.is_test === true, choice, invoiced,
  };
}

/**
 * چرا پرداختِ آنلاینِ همین سفارش همین حالا بسته است — یا ''. پیش از `pay.createForStorefront` و برای دکمهٔ پیگیری.
 *   order_not_accepted — «پرداخت پس از پذیرش» روشن و سفارش هنوز «تازه»
 *   order_invoiced     — فاکتورِ سفارش با نقد ثبت شده ⇒ روشِ پرداخت دیگر عوض نمی‌شود (فاکتور با پول بخواند)
 */
function blockOf(f, mode) {
  if (!f) return '';
  if (modeFor(mode, f.source) === MODE_AFTER && f.status === 'new') return 'order_not_accepted';
  if (f.invoiced && !PAID.includes(f.paymentStatus) && f.paymentStatus !== 'pending') return 'order_invoiced';
  return '';
}

const BLOCK_MESSAGE = {
  order_not_accepted: 'پرداخت پس از پذیرشِ فروشنده باز می‌شود',
  order_invoiced: 'فاکتورِ این سفارش با پرداختِ نقد ثبت شده است؛ هنگامِ تحویل نقد بپردازید',
};

async function guardOnlinePay(shopId, orderId) {
  const code = blockOf(await factsOf(shopId, orderId), await modeOf(shopId));
  if (code) throw conflict(BLOCK_MESSAGE[code], code);
}

/**
 * پیگیریِ مشتری: `blocked` (برای `onlinePay.available`) و `step` (کارتِ «پرداخت»؛ فقط با «پرداخت پس از
 * پذیرش» ِ روشن — خاموش ⇒ `undefined` و پاسخِ پیگیری همان پیشین).
 */
async function trackView(shopId, orderId) {
  const [f, mode] = await Promise.all([factsOf(shopId, orderId), modeOf(shopId)]);
  const blocked = Boolean(blockOf(f, mode));
  if (!f || modeFor(mode, f.source) !== MODE_AFTER) return { blocked, step: undefined };
  return {
    blocked,
    step: {
      state: payStateOf(f, MODE_AFTER), choice: f.choice, amountMinor: f.totalMinor,
      cash: f.fulfillment === 'pickup' ? 'pickup' : 'delivery',
      canChoose: PAY_OPEN.includes(f.status) && !PAID.includes(f.paymentStatus) && f.paymentStatus !== 'pending',
    },
  };
}

/** رویدادِ `note` درونِ تراکنش + نسخهٔ تازهٔ ردیف (تا صفِ زندهٔ برنامه و پیگیری تازه شوند) */
async function noteIn(c, shopId, row, actor, detail, at) {
  const seq = Number((await c.query(
    'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id = $1', [row.id]
  )).rows[0].n);
  await c.query(
    `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
       actor_type, actor_id, reason, note, detail, created_at)
     VALUES ($1,$2,$3,$4,'note',$5,$5,$6,$7,'','',$8::jsonb,$9)`,
    [ulid(at), row.id, shopId, seq, row.status, actor.type, actor.id || '', JSON.stringify(detail), at]
  );
  await c.query('UPDATE online_orders SET version = version + 1, updated_at = $3 WHERE shop_id = $1 AND id = $2',
    [shopId, row.id, at]);
}

const lockRow = async (c, shopId, id) => (await c.query(
  'SELECT * FROM online_orders WHERE shop_id = $1 AND id = $2 FOR UPDATE', [String(shopId), String(id || '')]
)).rows[0];

const latestNote = async (c, orderId, type) => (await c.query(
  `SELECT detail, created_at, actor_id FROM online_order_events WHERE order_id = $1 AND kind = 'note'
      AND detail->>'type' = $2 ORDER BY seq DESC LIMIT 1`, [orderId, type]
)).rows[0];

const withCurrent = (err, current) => Object.assign(err, { data: { current } });

/**
 * «نقد هنگامِ تحویل/برداشت» ِ مشتری (توکنِ پیگیری؛ درِ `routes/order-pay-store.js`). تکرارِ همان ⇒ بی رویدادِ دوم.
 * @returns {Promise<{changed:boolean}>}
 */
async function choose(shopId, orderId, body) {
  const method = typeof body?.method === 'string' ? body.method.trim() : '';
  if (!CHOICES.includes(method)) throw badRequest('روشِ پرداخت معتبر نیست', 'bad_payment_choice');
  if ((await modeOf(shopId)) !== MODE_AFTER) throw notFound('پیدا نشد', 'not_found');
  return tx(async (c) => {
    const row = await lockRow(c, shopId, orderId);
    if (!row || row.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
    if (row.status === 'new') throw withCurrent(conflict(BLOCK_MESSAGE.order_not_accepted, 'order_not_accepted'), row.status);
    if (!PAY_OPEN.includes(row.status)) throw withCurrent(conflict('پرداختِ این سفارش دیگر باز نیست', 'payment_closed'), row.status);
    if (PAID.includes(row.payment_status)) throw conflict('این سفارش پرداخت شده است', 'already_paid');
    if (row.payment_status === 'pending') throw conflict('پرداختِ آنلاینِ این سفارش در جریان است', 'payment_pending');
    const prev = await latestNote(c, row.id, 'pay_choice');
    if (prev && asJson(prev.detail).method === method) return { changed: false };
    await noteIn(c, shopId, row, { type: 'customer', id: '' }, { type: 'pay_choice', method }, now());
    return { changed: true };
  });
}

/**
 * «فاکتور شد» ِ یک سفارش — یک بار، زیرِ قفلِ ردیف. دومین بار (همان دستگاه یا دیگری) همان را برمی‌گرداند.
 * @returns {Promise<{invoice:{saleId:string, at:number, by:string}, created:boolean}>}
 */
async function claimInvoice(shopId, orderId, actor) {
  return tx(async (c) => {
    const row = await lockRow(c, shopId, orderId);
    if (!row) throw notFound('سفارش پیدا نشد', 'order_not_found');
    const prev = await latestNote(c, row.id, 'invoice');
    if (prev) {
      return { invoice: { saleId: saleIdOf(row.id), at: Number(prev.created_at), by: prev.actor_id || '' }, created: false };
    }
    if (row.is_test) throw conflict('سفارشِ آزمایشی فاکتور و کسرِ موجودی ندارد', 'test_order');
    if (!INVOICE_STATUSES.includes(row.status)) {
      throw withCurrent(conflict('فاکتور پس از «بسته شد» زده می‌شود؛ قلم‌ها تا آن‌جا عوض می‌شوند', 'invoice_not_ready'), row.status);
    }
    if (row.payment_status === 'pending') throw conflict('پرداختِ آنلاینِ این سفارش در جریان است؛ پس از روشن شدنش فاکتور بزنید', 'payment_pending');
    const at = now();
    await noteIn(c, shopId, row, actor, { type: 'invoice', saleId: saleIdOf(row.id) }, at);
    return { invoice: { saleId: saleIdOf(row.id), at, by: actor.id || '' }, created: true };
  });
}

module.exports = {
  INVOICE_STATUSES, PAY_OPEN, CHOICES, MODE_AFTER, MODE_ANY,
  saleIdOf, invoiceOf, choiceOf, payStateOf, blockOf, modeOf, staffView, factsOf, trackView, guardOnlinePay,
  choose, claimInvoice,
};
