'use strict';
/**
 * ORD-19 — کارِ گروهی روی چند سفارش: یک تغییرِ وضعیت برای تا [MAX_IDS] سفارش.
 *
 *   پرچم ── `shop.orders.bulk` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش).
 *   راه ─── هر سفارش جدا از همان درِ یگانهٔ `orders.transition` می‌گذرد (قفلِ ردیف، `order-states.check`،
 *           رویداد در همان تراکنش) — هیچ قاعدهٔ تازه‌ای نیست؛ یکی که نشد بقیه را نگه نمی‌دارد و نتیجهٔ هر
 *           کدام جدا برمی‌گردد (`ok`، وضعیتِ تازه، یا کدِ خطا و وضعیتِ جاری).
 *   تکرار ─ همان درخواست دوباره ⇒ سفارش‌های رسیده `unchanged` (بی رویدادِ دوم)؛ `from` ِ ناهمخوان ⇒ همان
 *           ۴۰۹ ِ `status_changed` برای همان سفارش.
 *   ⛔ بازپرداخت (`refunded_*`) و گذارهای فقط‌سیستمی گروهی نیستند: پول مبلغ و دفترِ خودش را می‌خواهد.
 */
const orders = require('./orders');
const S = require('./order-states');
const { badRequest } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.bulk';
const MAX_IDS = 50;
const ID_RE = /^[A-Za-z0-9_-]{1,64}$/;

const flagOf = (shopId) => orders.flagOf(shopId, FLAG_KEY);

function parse(body) {
  const b = body && typeof body === 'object' ? body : {};
  const ids = Array.isArray(b.ids) ? b.ids.map((x) => (typeof x === 'string' ? x.trim() : '')) : null;
  if (!ids || !ids.length || ids.length > MAX_IDS) throw badRequest(`فهرستِ سفارش‌ها باید ۱ تا ${MAX_IDS} شناسه باشد`, 'bad_ids');
  if (ids.some((x) => !ID_RE.test(x))) throw badRequest('شناسهٔ سفارش معتبر نیست', 'bad_ids');
  if (new Set(ids).size !== ids.length) throw badRequest('شناسهٔ تکراری در فهرست', 'bad_ids');
  const to = typeof b.to === 'string' ? b.to.trim() : '';
  if (!S.ORDER_STATUSES.includes(to)) throw badRequest('وضعیتِ مقصد معتبر نیست', 'bad_status');
  if (S.MONEY_TARGETS.includes(to) || S.SYSTEM_ONLY.includes(to)) {
    throw badRequest('این تغییر گروهی نمی‌شود؛ تک‌تک و از راهِ خودش', 'bulk_not_allowed');
  }
  const input = { to };
  for (const k of ['from', 'reason', 'note']) if (b[k] !== undefined) input[k] = b[k];
  if (b.readyInMinutes !== undefined) input.readyInMinutes = b.readyInMinutes;
  return { ids, input };
}

/**
 * @returns {Promise<{results: Array<{id:string, ok:boolean, status?:string, unchanged?:boolean, from?:string,
 *   orderNo?:number, reason?:string, code?:string, message?:string, current?:string}>, okCount:number, failedCount:number}>}
 */
async function transition(shopId, body, actor) {
  const { ids, input } = parse(body);
  const results = [];
  for (const id of ids) {
    try {
      const out = await orders.transition(shopId, id, input, actor);
      results.push({
        id, ok: true, status: out.order.status, unchanged: out.unchanged, from: out.from,
        orderNo: out.order.orderNo, reason: out.order.statusReason,
      });
    } catch (err) {
      if (!err || !err.status || err.status >= 500) throw err;
      results.push({ id, ok: false, code: err.code || 'error', message: err.message, ...(err.current ? { current: err.current } : {}) });
    }
  }
  const okCount = results.filter((r) => r.ok).length;
  return { results, okCount, failedCount: results.length - okCount };
}

module.exports = { FLAG_KEY, MAX_IDS, flagOf, parse, transition };
