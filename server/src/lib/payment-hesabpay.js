'use strict';
/**
 * آداپتورِ حساب‌پی (HesabPay) — تنها جایی که چیزی از خودِ حساب‌پی می‌داند (PAY-21).
 * حساب‌پی کیف‌پولِ خودش و کارتِ بانکیِ AfPay را می‌پذیرد؛ صفحهٔ پرداخت میزبانیِ خودِ اوست.
 *
 * ⚠️⚠️ «تأییدنشده»: سندِ رسمیِ توسعه‌دهنده (docs.hesab.com / developers.hesab.com) از این
 * محیط خوانده نشد. نشانیِ `payment/create-session` و این‌که کلید در سرآیند می‌رود از افزونهٔ
 * WooCommerce ِ جامعه آمده است؛ بقیه (نامِ سرآیندِ امضا، شکلِ بدنهٔ وب‌هوک، درِ «وضعیت»)
 * حدسِ محتاطانه است و هر جا با برچسبِ «تأییدنشده» آمده. پیش از روشن کردن برای هر دکان، با
 * حسابِ پذیرنده و سندِ رسمی همین فایل را درست کنید — بقیهٔ سرور دست نمی‌خورد
 * (قرارداد: `lib/payment-providers.js`).
 *
 * محیط (هیچ کلیدی در کد نیست؛ هر بار خوانده می‌شود):
 *   HESABPAY_API_KEY         کلیدِ API ِ پذیرنده                  — لازم
 *   HESABPAY_WEBHOOK_SECRET  رازِ امضای وب‌هوک                     — لازم
 *   HESABPAY_BASE_URL        پیش‌فرض https://api.hesab.com/api/v1
 *   HESABPAY_MERCHANT_ID     اختیاری؛ اگر حساب‌پی خواست در بدنه می‌رود
 *   HESABPAY_TIMEOUT_MS      مهلتِ هر درخواست به حساب‌پی (پیش‌فرض ۱۰ ثانیه؛ کف ۱ و سقف ۳۰ ثانیه)
 * نبودِ یکی از دو کلیدِ لازم ⇒ `configured() = false` و بخش برای همه خاموش است.
 */
const crypto = require('crypto');
const P = require('./payment-providers');

const DEFAULT_BASE = 'https://api.hesab.com/api/v1';
//  تأییدنشده: درِ ساختنِ جلسه (از افزونهٔ WooCommerce)
const CREATE_PATH = '/payment/create-session';
//  تأییدنشده: درِ خواندنِ وضعیتِ یک جلسه برای تأییدِ دوم و تطبیقِ شبانه
const STATUS_PATH = (sid) => `/payment/status/${encodeURIComponent(sid)}`;
//  تأییدنشده: نامِ سرآیندِ امضا؛ مقدار hex ِ HMAC-SHA256 ِ بدنهٔ خام (با یا بی پیشوندِ `sha256=`)
const SIG_HEADERS = ['x-hesab-signature', 'x-signature', 'x-hesabpay-signature'];
//  تأییدنشده: شکلِ سرآیندِ کلید
const authHeader = (key) => ({ Authorization: `API-KEY ${key}` });
//  تأییدنشده: نامِ وضعیت‌ها ⇒ وضعیتِ قرارداد
const STATUS_MAP = {
  paid: 'paid', success: 'paid', successful: 'paid', completed: 'paid', complete: 'paid', approved: 'paid',
  failed: 'failed', failure: 'failed', declined: 'failed', rejected: 'failed', error: 'failed',
  pending: 'pending', processing: 'pending', created: 'pending', initiated: 'pending',
  expired: 'expired', timeout: 'expired',
  cancelled: 'cancelled', canceled: 'cancelled',
};

/*
 *  سقفِ مهلتِ هر درخواست. باید خیلی کمتر از اجارهٔ پردازشِ رویداد (`EVENT_LEASE_MS` = ۲ دقیقه در
 *  `payment-intents.js`) بماند؛ وگرنه پردازشِ دوم ِ همان رویداد پیش از پایانِ اولی شروع می‌شود (پول امن
 *  می‌ماند — compare-and-set ِ `markPaid` — ولی outcome ِ رویدادها بازنویسی می‌شود).
 */
const MAX_TIMEOUT_MS = 30_000;

function env() {
  return {
    apiKey: String(process.env.HESABPAY_API_KEY || '').trim(),
    secret: String(process.env.HESABPAY_WEBHOOK_SECRET || '').trim(),
    base: String(process.env.HESABPAY_BASE_URL || DEFAULT_BASE).trim().replace(/\/+$/, ''),
    merchantId: String(process.env.HESABPAY_MERCHANT_ID || '').trim(),
    timeoutMs: Math.min(MAX_TIMEOUT_MS, Math.max(1000, Number(process.env.HESABPAY_TIMEOUT_MS) || 10_000)),
  };
}

/*
 *  ⛔ فقط https: کلیدِ API در سرآیندِ هر درخواست می‌رود. تنها استثنا سرورِ ساختگیِ آزمون روی loopback
 *  (127.0.0.1 / localhost / [::1]) و فقط با NODE_ENV ِ صریحِ test/development — هرگز در تولید.
 */
const LOOPBACK_HTTP = /^http:\/\/(?:127\.0\.0\.1|localhost|\[::1\])(?::\d+)?(?:\/|$)/i;
function baseAllowed(base) {
  if (/^https:\/\/[^/\s]+/i.test(base)) return true;
  return P.isTestOrDev() && LOOPBACK_HTTP.test(base);
}

const configured = () => { const e = env(); return Boolean(e.apiKey && e.secret && baseAllowed(e.base)); };

/** درخواست با مهلت؛ پاسخِ غیر ۲xx یا JSON ِ خراب ⇒ خطا (پیامِ داخلی، بی کلید). */
async function call(method, path, body) {
  const e = env();
  if (!baseAllowed(e.base)) throw new Error('hesabpay: HESABPAY_BASE_URL باید https باشد');
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), e.timeoutMs);
  try {
    const res = await fetch(`${e.base}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeader(e.apiKey) },
      body: body ? JSON.stringify(body) : undefined,
      signal: ctl.signal,
    });
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : {}; } catch { /* پایین */ }
    if (!res.ok || !json || typeof json !== 'object') {
      const err = new Error(`hesabpay ${method} ${path} ⇒ ${res.status}`);
      err.status = res.status;
      throw err;
    }
    return json;
  } finally {
    clearTimeout(timer);
  }
}

/** نخستین مقدارِ ناتهی از چند مسیرِ ممکن (`data.x` هم) — تأییدنشده بودنِ نام‌ها را می‌پوشاند. */
function pick(obj, names) {
  for (const n of names) {
    for (const src of [obj, obj?.data, obj?.payment, obj?.transaction]) {
      const v = src && typeof src === 'object' ? src[n] : undefined;
      if (v !== undefined && v !== null && v !== '') return v;
    }
  }
  return undefined;
}

const statusOf = (v) => STATUS_MAP[String(v || '').trim().toLowerCase()] || 'unknown';

function amountOf(obj) {
  const v = pick(obj, ['amount', 'total', 'total_amount', 'price']);
  return v === undefined ? null : P.decimalToMinor(v);
}

//  تأییدنشده: حساب‌پی فقط افغانی است؛ نیامدنِ ارز یعنی AFN. ارزِ دیگری که بیاید همان‌طور
//  می‌ماند و هستهٔ پرداخت آن را «اختلاف» می‌شمارد.
const currencyOf = (obj) => String(pick(obj, ['currency', 'currency_code']) || 'AFN').trim().toUpperCase();

async function createSession({ intentId, amountMinor, currency, description, returnUrl, callbackUrl }) {
  const e = env();
  //  تأییدنشده: شکلِ بدنه — فهرستِ اقلام (نام، تعداد، قیمت) مثلِ افزونهٔ WooCommerce، به‌علاوهٔ مرجعِ ما
  const body = {
    items: [{ id: intentId, name: String(description || 'خرید').slice(0, 120), price: P.minorToDecimal(amountMinor), quantity: 1 }],
    amount: P.minorToDecimal(amountMinor),
    currency,
    reference: intentId,
    order_id: intentId,
    ...(callbackUrl ? { callback_url: callbackUrl, webhook_url: callbackUrl } : {}),
    ...(returnUrl ? { redirect_success_url: returnUrl, redirect_failure_url: returnUrl, return_url: returnUrl } : {}),
    ...(e.merchantId ? { merchant_id: e.merchantId } : {}),
  };
  const r = await call('POST', CREATE_PATH, body);
  const checkoutUrl = String(pick(r, ['url', 'payment_url', 'checkout_url', 'session_url', 'redirect_url']) || '');
  if (!/^https:\/\//.test(checkoutUrl)) throw new Error('hesabpay: نشانیِ پرداخت در پاسخ نبود');
  //  تأییدنشده: شناسهٔ جلسه؛ نبودش ⇒ مرجعِ خودمان (وب‌هوک با `reference` هم پیدا می‌شود)
  const providerSessionId = String(pick(r, ['session_id', 'sessionId', 'id', 'token']) || `ref:${intentId}`);
  const exp = Number(pick(r, ['expires_at', 'expiresAt', 'expiry']));
  //  ثانیه یا میلی‌ثانیه؛ نبود ⇒ ۱۵ دقیقه
  const expiresAt = Number.isFinite(exp) && exp > 0 ? (exp < 1e12 ? exp * 1000 : exp) : Date.now() + 15 * 60 * 1000;
  return { providerSessionId, checkoutUrl, expiresAt };
}

/**
 * امضا: HMAC-SHA256 ِ بدنهٔ خام با HESABPAY_WEBHOOK_SECRET، مقایسهٔ زمان‌ثابت (تأییدنشده: سرآیند).
 * ⚠️ افزونهٔ WooCommerce به‌جای سنجشِ محلی درِ `hesab/webhooks/verify-signature` ِ حساب‌پی را
 * صدا می‌زند؛ اگر سندِ رسمی همان را گفت، فقط همین تابع عوض می‌شود. تأییدِ دوم با `fetchStatus`
 * (پیش‌فرض روشن) در هر حال پیش از «پرداخت شد» انجام می‌شود.
 */
async function verifyWebhook(raw, headers) {
  const { secret } = env();
  if (!secret) return { ok: false, reason: 'not_configured' };
  if (!Buffer.isBuffer(raw) || !raw.length) return { ok: false, reason: 'empty_body' };
  const given = SIG_HEADERS.map((h) => headers[h]).find((v) => typeof v === 'string' && v.length);
  const sig = String(given || '').trim().replace(/^sha256=/i, '').toLowerCase();
  if (!P.safeEqual(sig, P.hmacHex(secret, raw))) return { ok: false, reason: 'bad_signature' };
  let b;
  try { b = JSON.parse(raw.toString('utf8')); } catch { return { ok: false, reason: 'bad_json' }; }
  if (!b || typeof b !== 'object') return { ok: false, reason: 'bad_json' };
  const sessionId = String(pick(b, ['session_id', 'sessionId', 'payment_session_id']) || '');
  const reference = String(pick(b, ['reference', 'order_id', 'merchant_reference']) || '');
  const providerRef = String(pick(b, ['transaction_id', 'transactionId', 'reference_number', 'txn_id']) || '');
  const status = statusOf(pick(b, ['status', 'payment_status', 'state', 'event']));
  //  شناسهٔ رویداد؛ نبود ⇒ اثرِ انگشتِ بدنه (همان بدنهٔ تکراری همان رویداد است)
  const eventId = String(pick(b, ['event_id', 'eventId', 'webhook_id', 'notification_id']) || '')
    || `sha256:${crypto.createHash('sha256').update(raw).digest('hex')}`;
  return {
    ok: true,
    event: {
      eventId, sessionId, reference, status, amountMinor: amountOf(b), currency: currencyOf(b), providerRef,
      //  فقط فیلدهای لازم برای تطبیق — بی سرآیند و امضا
      raw: { sessionId, reference, status: pick(b, ['status', 'payment_status', 'state', 'event']) ?? null,
        amount: pick(b, ['amount', 'total', 'total_amount', 'price']) ?? null, currency: pick(b, ['currency', 'currency_code']) ?? null,
        providerRef },
    },
  };
}

async function fetchStatus(sessionId) {
  const r = await call('GET', STATUS_PATH(sessionId));
  return {
    status: statusOf(pick(r, ['status', 'payment_status', 'state'])),
    amountMinor: amountOf(r),
    currency: currencyOf(r),
    providerRef: String(pick(r, ['transaction_id', 'transactionId', 'reference_number', 'txn_id']) || ''),
  };
}

module.exports = {
  name: 'hesabpay',
  configured, createSession, verifyWebhook, fetchStatus,
  SIG_HEADER: SIG_HEADERS[0], DEFAULT_BASE, MAX_TIMEOUT_MS,
  requestTimeoutMs: () => env().timeoutMs,
};
