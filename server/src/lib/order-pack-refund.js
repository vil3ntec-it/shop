'use strict';
/**
 * ORD-15 / PAY-23 — بازپرداختِ دستیِ کسرِ سفارشِ آنلاینِ **پرداخت‌شده** هنگامِ بسته‌بندی (تصمیمِ ۱-۶ ب، ۱۴۰۵/۰۸/۱۹:
 * «دکان پول را خودش — نقد یا حساب‌پی — پس می‌دهد و در برنامه «پس داده شد» می‌زند؛ سرور ثبت می‌کند»؛ تا حساب‌پیِ خودکار).
 *
 *   پرچم ── `shop.orders.manual_refund` (دکان یا `all`؛ ⛔ پیش‌فرض خاموش ⇒ همان ۴۰۹ ِ `paid_needs_refund` ِ امروز).
 *   درخواست ── `pack` با `refund: { method: 'cash' | 'hesabpay', note? }`؛ بی آن ⇒ همان ۴۰۹ (برنامهٔ کهنه دست نمی‌خورد).
 *   مجوز ── `orders.refund` (صاحب و مدیر؛ همان دفترِ بازپرداخت و تسویهٔ ویرایش) — جمع‌آورِ بی آن ۴۰۳.
 *   مبلغ ── فقط سرور می‌سازد: پرداختی (`total_minor` ِ پیش از بسته‌بندی) − جمعِ تازه؛ جمعِ بیشتر از پرداختی همان ۴۰۹ ِ
 *            `paid_total_increase` (پولِ بی‌رضایت گرفته نمی‌شود). هیچ درگاهی صدا زده نمی‌شود — فقط **ثبت**.
 *   ثبت ── در همان تراکنشِ بسته‌بندی: وضعیتِ پرداخت ⇒ `partially_refunded` و رویدادِ `payment` (از وضعیتِ پیشینِ پرداخت
 *            به همان، مثلِ `payment-intents`) با مبلغ، روش، پرداختی و جمعِ تازه (گذشته پاک نمی‌شود)؛ تکرارِ همان کلیدِ بسته‌بندی همان برگه را برمی‌گرداند و دوباره ثبت نمی‌کند.
 * ⛔ مبلغ عددِ صحیح به واحدِ کوچک؛ هرگز بیش از پرداختی.
 */
const { badRequest, forbidden } = require('../middleware/errors');

const FLAG = 'shop.orders.manual_refund';
const METHODS = Object.freeze(['cash', 'hesabpay']);

/** `refund` ِ بدنهٔ بسته‌بندی ⇒ null (نیامده) یا `{method, note}`؛ ناجور ⇒ ۴۰۰ */
function parse(body) {
  const r = body && typeof body === 'object' ? body.refund : undefined;
  if (r === undefined || r === null) return null;
  if (typeof r !== 'object' || Array.isArray(r) || !METHODS.includes(r.method)) {
    throw badRequest('روشِ بازپرداخت فقط نقد یا حساب‌پی (دستی) است', 'bad_refund');
  }
  if (r.note !== undefined && typeof r.note !== 'string') throw badRequest('یادداشتِ بازپرداخت متن است', 'bad_refund');
  const { stripControl } = require('./validate');
  return { method: r.method, note: stripControl(String(r.note || '')).trim().slice(0, 300) };
}

/**
 * پیش از رد کردنِ بسته‌بندیِ پرداخت‌شده: راهِ دستی باز است؟ ⇒ `{method, note}` یا null (همان ۴۰۹ ِ امروز).
 * پرچمِ روشن و درخواستِ صریح ولی بی `orders.refund` ⇒ ۴۰۳.
 */
async function allowIn(run, shopId, body, actor) {
  const want = parse(body);
  if (!want) return null;
  if (!(await require('./orders').flagOf(shopId, FLAG, run))) return null;
  if (actor.type !== 'system' && !require('./permissions').can(actor.role, 'orders.refund')) {
    throw forbidden('بازپرداخت در حدِ دسترسیِ شما نیست؛ صاحب یا مدیرِ دکان «پس داده شد» را بزند', 'permission_denied');
  }
  return want;
}

/** ۴۰۹ ِ `paid_needs_refund` به برنامه بگوید «پس داده شد» را نشان دهد؟ (پرچمِ روشن و `orders.refund`) */
async function offered(run, shopId, actor) {
  if (actor.type !== 'system' && !require('./permissions').can(actor.role, 'orders.refund')) return false;
  return require('./orders').flagOf(shopId, FLAG, run);
}

/** درونِ تراکنشِ بسته‌بندی، پس از نوشتنِ جمعِ تازه: وضعیتِ پرداخت و رویدادِ `payment` */
async function recordIn(c, { shopId, order, actor, refund, paidMinor, totalMinor, at, key }) {
  const amountMinor = paidMinor - totalMinor;
  await c.query("UPDATE online_orders SET payment_status='partially_refunded' WHERE shop_id=$1 AND id=$2", [shopId, order.id]);
  const seq = Number((await c.query(
    'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [order.id]
  )).rows[0].n);
  const detail = { manualRefund: { amountMinor, method: refund.method, paidMinor, totalMinor, key } };
  await c.query(
    `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
       actor_type, actor_id, reason, note, detail, created_at)
     VALUES ($1,$2,$3,$4,'payment',$5,'partially_refunded',$6,$7,'',$8,$9::jsonb,$10)`,
    [require('./ulid').ulid(at), order.id, shopId, seq, order.payment_status, actor.type, actor.id || '',
      refund.note || `بازپرداختِ دستی (${refund.method === 'cash' ? 'نقد' : 'حساب‌پی'}) ِ کسرِ بسته‌بندی`, JSON.stringify(detail), at]
  );
  return { amountMinor, method: refund.method };
}

module.exports = { FLAG, METHODS, parse, allowIn, offered, recordIn };
