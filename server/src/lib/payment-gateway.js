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

/** پاسخِ قطعیِ «پرداخت نشد» ِ ارائه‌دهنده در تأییدِ دوم/تطبیق. */
const PROVIDER_NO = Object.freeze(['failed', 'expired', 'cancelled']);

/** تأییدِ دومِ «paid» از خودِ ارائه‌دهنده (از `payment-intents.js` برای سقفِ اندازه). `st` ⇒ سنجشِ پذیرنده. */
async function confirmWith(ad, intent) {
  if (typeof ad.fetchStatus !== 'function') return { ok: false, reason: 'no_status_api' };
  let st;
  try { st = await ad.fetchStatus(intent.provider_session_id); } catch (err) {
    console.warn(`[payments] تأییدِ دوم از ${ad.name} نشد (${intent.id}): ${err.message}`);
    return { ok: false, reason: 'status_unreachable', retry: true };
  }
  //  فقط «نه» ِ قطعی پایانی است. pending/unknown/… یعنی درِ وضعیت هنوز هم‌گام نشده (پول شاید رفته)
  //  ⇒ «بعداً» (۵۰۳) تا ارائه‌دهنده همان رویداد را دوباره بفرستد؛ رویداد نهایی نمی‌شود.
  if (st.status !== 'paid') return { ok: false, reason: `provider_says_${st.status}`, retry: !PROVIDER_NO.includes(st.status) };
  if (st.amountMinor !== Number(intent.amount_minor) || st.currency !== intent.currency) {
    return { ok: false, reason: 'amount_mismatch', mismatch: true, got: { amountMinor: st.amountMinor, currency: st.currency } };
  }
  return { ok: true, providerRef: st.providerRef, st };
}

/**
 * حساب‌پیِ هر دکان، پیش از «پرداخت شد» (مبلغ و ارز پیش‌تر سنجیده): پول به پذیرندهٔ **همین** دکان رسید؟ (پذیرنده در
 * وب‌هوک یا درِ وضعیت + مرجعِ خودمان + اثرِ انگشتِ حسابِ فعال؛ `hesabpay-guard.payeeProof`). ثابت شد ⇒ null؛ نه ⇒
 * «نیازمندِ بررسی» (قصد همان وضعیت می‌ماند، هرگز «پرداخت شد») با هشدار و خبرِ صاحب. ساختگیِ آزمون بیرون است.
 */
async function payeeHeld(ad, intent, ev, st, source) {
  const g = require('./hesabpay-guard');
  const p = g.payeeProof(ad, intent, ev || {}, st);
  return p.ok ? null : g.holdForReview(intent, p.missing, source);
}

module.exports = { gatewayFor, callbackUrl, webhookTarget, intentAdapters, confirmWith, payeeHeld };
