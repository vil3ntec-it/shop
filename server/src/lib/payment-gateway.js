'use strict';
/**
 * کدام درگاه برای کدام دکان — حساب‌پیِ هر دکان (۱۴۰۵/۰۸/۱۱، مهاجرتِ ۲۰۴). از `payment-intents.js` جدا شد تا زیرِ
 * سقفِ اندازه بماند؛ هسته (قصد، وب‌هوک، تطبیق) فقط از همین‌جا آداپتور می‌گیرد.
 *
 * ⛔ حساب‌پی فقط با کلیدِ **همان دکان** (`hesabpay-account.gatewayOf`)؛ دکانِ بی‌حساب ⇒ null، هرگز کلیدِ سکو.
 * ⛔ ساختگی (`fake`) فقط آزمون/توسعه (`payment-providers.fakeAllowed`)، بی‌دکان.
 */
const config = require('../config');
const { one } = require('../db');
const P = require('./payment-providers');

const accounts = () => require('./hesabpay-account');

/** آداپتورِ درگاهِ همین دکان: ساختگی (فقط آزمون/توسعه) یا حساب‌پیِ خودِ دکان در حالتِ «خودکار» با کلیدِ سالم؛ وگرنه null */
async function gatewayFor(shopId) {
  if (P.activeName() === 'fake') {
    const ad = P.adapter('fake');
    return ad && ad.configured() ? ad : null;
  }
  return accounts().gatewayOf(shopId);
}

/**
 * نشانیِ وب‌هوک برای ارائه‌دهنده. حساب‌پی: نشانیِ **همان دکان** (`…/webhook/hesabpay/<token>`، پایه از
 * `HESABPAY_CALLBACK_URL` یا `SERVER_URL`)؛ نسبی ⇒ '' (همانی که صاحب در داشبوردِ حساب‌پیِ خودش ثبت کرده).
 */
function callbackUrl(ad) {
  if (ad.name === 'hesabpay') {
    const u = accounts().webhookUrlOf(ad.webhookToken);
    return /^https?:\/\//.test(u) ? u : '';
  }
  const base = String(config.serverUrl || '').trim().replace(/\/+$/, '');
  return base ? `${base}/api/v1/payments/webhook/${ad.name}` : '';
}

/**
 * کدام آداپتور (و کدام دکان) این وب‌هوک را می‌سنجد؟ حساب‌پی: نشانیِ دکان (`token`)، یا در درِ کهنهٔ بی‌دکان قصدِ
 * نام‌برده در بدنهٔ هنوز امضانشده — فقط برای یافتنِ دکان؛ امضا در هر حال با رازِ **همان دکان**. null ⇒ ۴۰۱.
 */
async function webhookTarget(name, raw, { token } = {}) {
  if (name !== 'hesabpay') {
    const ad = P.webhookAdapter(name);
    return ad && ad.configured() ? { ad, shopId: null } : null;
  }
  if (token !== undefined) {
    const hit = await accounts().byWebhookToken(token);
    return hit ? { ad: hit.adapter, shopId: hit.shopId } : null;
  }
  const { sessionId, reference } = require('./payment-hesabpay').peekRefs(raw);
  let row = sessionId
    ? await one('SELECT shop_id FROM payment_intents WHERE provider=$1 AND provider_session_id=$2', [name, sessionId])
    : null;
  if (!row && reference) row = await one('SELECT shop_id FROM payment_intents WHERE provider=$1 AND id=$2', [name, reference]);
  if (!row) return null;
  const ad = await accounts().gatewayOf(row.shop_id);
  return ad ? { ad, shopId: row.shop_id } : null;
}

/** برای یک نوبتِ تطبیق: آداپتورِ هر قصد — حساب‌پی با کلیدِ دکانِ خودش (یک بار برای هر دکان)؛ بی حسابِ سالم ⇒ null */
function intentAdapters() {
  const perShop = new Map();
  return async (r) => {
    if (r.provider !== 'hesabpay') return P.webhookAdapter(r.provider);
    if (!perShop.has(r.shop_id)) perShop.set(r.shop_id, await accounts().gatewayOf(r.shop_id));
    return perShop.get(r.shop_id);
  };
}

module.exports = { gatewayFor, callbackUrl, webhookTarget, intentAdapters };
