'use strict';
/**
 * PAY-07 (د۱۸) — آینهٔ وضعیتِ بعدیِ قصدِ حساب‌پیِ خودکار روی فاکتورِ دستگاه.
 *
 * فروشِ صندوق با قصدِ «پرداخت‌شده» بسته می‌شود و ردیفِ پرداختش `verification = gateway` است (سرور مرجع؛ برنامه وضعیتش را
 * دستی عوض نمی‌کند — `PayStatus.manual`). پس از آن سرور هنوز می‌تواند آن را عوض کند: `needs_review` (پرداختِ دیرِ تأییدنشده،
 * S35) یا `refunded` (بازپرداختِ مدیر). دستگاه این را نمی‌دید. حالا دستگاه پس از هر دورِ همگام‌سازی همین فهرست را از
 * cursor ِ خودش می‌گیرد و روی ردیفِ درگاهِ همان فاکتور نشان می‌دهد (`core/pay/PayMirror`) — **سندِ دفتر نمی‌سازد** (هیچ
 * دستگاهی نمی‌تواند وضعیتِ درگاه را جعل کند؛ هر دستگاه از سرور می‌خواند).
 *
 *   changes(shopId, { after, limit }) ⇒ { intents: [{ id, refId, method, status, needsReview, updatedAt }], cursor, more }
 *     فقط `kind = pos_sale` و `status ∈ {paid, refunded}`، همین دکان، `(updated_at, id) > after` به همان ترتیب.
 *     cursor = `<updated_at>~<id>` ِ آخرین ردیف (بی ردیف ⇒ همان after).
 *
 * ⛔ پرچمِ `shop.pay_status_mirror` (`live_config`، دکان یا `all`)، پیش‌فرض خاموش ⇒ ۴۰۴ `pay_mirror_off` پیش از هر
 *    خواندنِ پرسش؛ برنامه ۴۰۴ را «نیست» می‌خواند و هیچ نمی‌کند ⇒ رفتارِ امروز.
 * ⛔ دکان فقط از نشست (`requireShop`)؛ نقش `data.write` (همان درِ قصدها)؛ سقفِ نرخِ هر کاربر (`RATE_PAY_MIRROR_MAX`).
 */
const { many } = require('../db');
const { flagOf } = require('./orders');
const { badRequest, notFound } = require('../middleware/errors');
const v = require('./validate');

const FLAG = 'shop.pay_status_mirror';
const MAX = 200;
const CURSOR_RE = /^(\d{1,16})~([A-Za-z0-9_]{1,64})$/;

async function requireOn(shopId) {
  if (!(await flagOf(shopId, FLAG))) throw notFound('آینهٔ وضعیتِ پرداخت روشن نیست', 'pay_mirror_off');
}

/** `?after=<ms>~<id>&limit=` ⇒ پرسشِ سنجیده؛ بدشکل ⇒ ۴۰۰ */
function parseQuery(q = {}) {
  let after = { at: 0, id: '' };
  if (q.after !== undefined && q.after !== '') {
    const m = typeof q.after === 'string' ? CURSOR_RE.exec(q.after) : null;
    if (!m || !Number.isSafeInteger(Number(m[1]))) throw badRequest('نشانِ ادامه درست نیست', 'bad_cursor');
    after = { at: Number(m[1]), id: m[2] };
  }
  const limit = v.integer(q.limit, { field: 'limit', min: 1, max: MAX, def: 100 });
  return { after, limit };
}

async function changes(shopId, { after = { at: 0, id: '' }, limit = 100 } = {}) {
  const rows = await many(
    `SELECT id, ref_id, status, needs_review, updated_at FROM payment_intents
      WHERE shop_id=$1 AND kind='pos_sale' AND status IN ('paid','refunded')
        AND (updated_at, id) > ($2::bigint, $3::text)
      ORDER BY updated_at, id LIMIT $4`,
    [shopId, after.at, after.id, limit + 1]
  );
  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  return {
    intents: page.map((r) => ({
      id: r.id, refId: r.ref_id, method: require('./payment-intents').METHOD_ID, status: r.status,
      needsReview: r.needs_review === true, updatedAt: Number(r.updated_at),
    })),
    cursor: last ? `${Number(last.updated_at)}~${last.id}` : (after.at || after.id ? `${after.at}~${after.id}` : ''),
    more: rows.length > limit,
  };
}

module.exports = { FLAG, MAX, requireOn, parseQuery, changes };
