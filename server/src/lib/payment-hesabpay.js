'use strict';
/**
 * آداپتورِ حساب‌پی (HesabPay) — تنها جایی که چیزی از خودِ حساب‌پی می‌داند (PAY-21).
 * حساب‌پی کیف‌پولِ خودش و کارتِ بانکیِ AfPay را می‌پذیرد؛ صفحهٔ پرداخت میزبانیِ خودِ اوست.
 *
 * ⛔ هر دکان حسابِ خودش (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱، مهاجرتِ ۲۰۴): کلید و رازِ پذیرنده از ردیفِ رمزشدهٔ
 *    همان دکان (`lib/hesabpay-account.js`) با `forShop(...)` می‌آیند و پول مستقیم به حسابِ همان دکان می‌رود.
 *    `HESABPAY_API_KEY` / `HESABPAY_WEBHOOK_SECRET` / `HESABPAY_MERCHANT_ID` ِ محیط دیگر **هیچ‌جا** خوانده
 *    نمی‌شوند — نه برای دکانِ بی‌حساب، نه به‌جای حسابِ خراب. خودِ این پرونده هیچ `createSession` /
 *    `verifyWebhook` / `fetchStatus` ِ بی‌دکان ندارد؛ کسی که بی `forShop` صدا بزند خطا می‌گیرد، نه کلیدِ سکو.
 *
 * ⚠️⚠️ «تأییدنشده»: سندِ رسمیِ توسعه‌دهنده (docs.hesab.com / developers.hesab.com) از این
 * محیط خوانده نشد. نشانیِ `payment/create-session` و این‌که کلید در سرآیند می‌رود از افزونهٔ
 * WooCommerce ِ جامعه آمده است؛ بقیه (نامِ سرآیندِ امضا، شکلِ بدنهٔ وب‌هوک، درِ «وضعیت»)
 * حدسِ محتاطانه است و هر جا با برچسبِ «تأییدنشده» / `TODO(hesabpay-sandbox)` آمده. پیش از روشن کردن
 * برای هر دکان، با یک حسابِ پذیرندهٔ sandbox و سندِ رسمی همین فایل را درست کنید — بقیهٔ سرور دست
 * نمی‌خورد (قرارداد: `lib/payment-providers.js`).
 *
 * محیط (فقط تنظیمِ سرور؛ هیچ کلیدِ پرداختی):
 *   HESABPAY_BASE_URL        پیش‌فرض https://api.hesab.com/api/v1
 *   HESABPAY_TIMEOUT_MS      مهلتِ هر درخواست به حساب‌پی (پیش‌فرض ۱۰ ثانیه؛ کف ۱ و سقف ۳۰ ثانیه)
 *   HESABPAY_CRED_KEY        کلیدِ رمزگذاریِ کلیدهای دکان‌ها (`lib/hesabpay-account.js`)؛ نبود ⇒
 *                            `configured() = false` و حالتِ «خودکار» برای همه خاموش.
 */
const crypto = require('crypto');
const P = require('./payment-providers');

const DEFAULT_BASE = 'https://api.hesab.com/api/v1';
//  TODO(hesabpay-sandbox): درِ ساختنِ جلسه (از افزونهٔ WooCommerce) — تأییدنشده
const CREATE_PATH = '/payment/create-session';
//  TODO(hesabpay-sandbox): درِ خواندنِ وضعیتِ یک جلسه برای تأییدِ دوم و تطبیقِ شبانه
const STATUS_PATH = (sid) => `/payment/status/${encodeURIComponent(sid)}`;
//  TODO(hesabpay-sandbox): نامِ سرآیندِ امضا؛ مقدار hex ِ HMAC-SHA256 ِ بدنهٔ خام (با یا بی پیشوندِ `sha256=`)
const SIG_HEADERS = ['x-hesab-signature', 'x-signature', 'x-hesabpay-signature'];
//  TODO(hesabpay-sandbox): شکلِ سرآیندِ کلید
const authHeader = (key) => ({ Authorization: `API-KEY ${key}` });
//  TODO(hesabpay-sandbox): نامِ وضعیت‌ها ⇒ وضعیتِ قرارداد
const STATUS_MAP = {
  paid: 'paid', success: 'paid', successful: 'paid', completed: 'paid', complete: 'paid', approved: 'paid',
  failed: 'failed', failure: 'failed', declined: 'failed', rejected: 'failed', error: 'failed',
  pending: 'pending', processing: 'pending', created: 'pending', initiated: 'pending',
  expired: 'expired', timeout: 'expired',
  cancelled: 'cancelled', canceled: 'cancelled',
};

//  TODO(hesabpay-sandbox): نامِ فیلدِ شناسهٔ پذیرنده در وب‌هوک و درِ وضعیت
const MERCHANT_FIELDS = ['merchant_id', 'merchantId', 'merchant', 'merchant_code'];
const REF_FIELDS = ['reference', 'order_id', 'merchant_reference'];

/*
 *  سقفِ مهلتِ هر درخواست. باید خیلی کمتر از اجارهٔ پردازشِ رویداد (`EVENT_LEASE_MS` = ۲ دقیقه در
 *  `payment-intents.js`) بماند؛ وگرنه پردازشِ دوم ِ همان رویداد پیش از پایانِ اولی شروع می‌شود (پول امن
 *  می‌ماند — compare-and-set ِ `markPaid` — ولی outcome ِ رویدادها بازنویسی می‌شود).
 */
const MAX_TIMEOUT_MS = 30_000;

/** فقط تنظیمِ سرور — هیچ کلید یا رازی از محیط خوانده نمی‌شود (کلیدِ هر دکان: `forShop`). */
function env() {
  return {
    base: String(process.env.HESABPAY_BASE_URL || DEFAULT_BASE).trim().replace(/\/+$/, ''),
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

/** این سرور می‌تواند حساب‌پیِ دکان‌ها را به کار ببرد؟ (کلیدِ رمزگذاری + نشانیِ https) — نه «کلیدِ سکو». */
const configured = () => require('./hesabpay-account').credKeyReady() && baseAllowed(env().base);

/** درخواست با مهلت و کلیدِ همان دکان؛ پاسخِ غیر ۲xx یا JSON ِ خراب ⇒ خطا (پیامِ داخلی، بی کلید). */
async function call(apiKey, method, path, body) {
  const e = env();
  if (!baseAllowed(e.base)) throw new Error('hesabpay: HESABPAY_BASE_URL باید https باشد');
  if (!apiKey) throw new Error('hesabpay: کلیدِ دکان نیست');
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), e.timeoutMs);
  try {
    const res = await fetch(`${e.base}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeader(apiKey) },
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

/** شناسهٔ جلسه و مرجعِ ما از بدنهٔ **امضانشده** — فقط برای پیدا کردنِ دکانِ قصد در درِ بی‌نشانیِ دکان؛ هیچ چیزش باور نمی‌شود. */
function peekRefs(raw) {
  let b = null;
  try { b = Buffer.isBuffer(raw) && raw.length ? JSON.parse(raw.toString('utf8')) : null; } catch { b = null; }
  if (!b || typeof b !== 'object') return { sessionId: '', reference: '' };
  const cut = (v) => String(v ?? '').slice(0, 200);
  return {
    sessionId: cut(pick(b, ['session_id', 'sessionId', 'payment_session_id'])),
    reference: cut(pick(b, ['reference', 'order_id', 'merchant_reference'])),
  };
}

/**
 * آداپتورِ حساب‌پی با کلیدهای **یک** دکان (همان قراردادِ `payment-providers`). کلید، راز و شناسهٔ پذیرنده فقط در
 * بستار می‌مانند — نه روی شیء (هیچ `JSON.stringify` / لاگی آن‌ها را نمی‌بیند).
 *   payeeFp          اثرِ انگشتِ حسابِ **فعالِ** دکان (`hesabpay-guard`) ⇒ روی هر قصد؛ «پرداخت شد» فقط اگر همان بماند
 *   merchantMatches  پذیرنده‌ای که وب‌هوک/درِ وضعیت گفت همان پذیرندهٔ **همین** دکان است؟ (مقایسهٔ زمان‌ثابت)
 * @param {{apiKey:string, secret:string, merchantId?:string, webhookToken?:string, payeeFp?:string}} creds
 */
function forShop({ apiKey, secret, merchantId = '', webhookToken = '', payeeFp = '' } = {}) {
  const key = String(apiKey || '').trim();
  const sec = String(secret || '').trim();
  const merchant = String(merchantId || '').trim();
  const merchantMatches = (v) => Boolean(merchant) && (typeof v === 'string' || typeof v === 'number')
    && P.safeEqual(String(v).trim(), merchant);

  async function createSession({ intentId, amountMinor, currency, description, returnUrl, callbackUrl }) {
    //  TODO(hesabpay-sandbox): شکلِ بدنه — فهرستِ اقلام (نام، تعداد، قیمت) مثلِ افزونهٔ WooCommerce، به‌علاوهٔ مرجعِ ما
    const body = {
      items: [{ id: intentId, name: String(description || 'خرید').slice(0, 120), price: P.minorToDecimal(amountMinor), quantity: 1 }],
      amount: P.minorToDecimal(amountMinor),
      currency,
      reference: intentId,
      order_id: intentId,
      ...(callbackUrl ? { callback_url: callbackUrl, webhook_url: callbackUrl } : {}),
      ...(returnUrl ? { redirect_success_url: returnUrl, redirect_failure_url: returnUrl, return_url: returnUrl } : {}),
      ...(merchant ? { merchant_id: merchant } : {}),
    };
    const r = await call(key, 'POST', CREATE_PATH, body);
    const checkoutUrl = String(pick(r, ['url', 'payment_url', 'checkout_url', 'session_url', 'redirect_url']) || '');
    if (!/^https:\/\//.test(checkoutUrl)) throw new Error('hesabpay: نشانیِ پرداخت در پاسخ نبود');
    //  TODO(hesabpay-sandbox): شناسهٔ جلسه؛ نبودش ⇒ مرجعِ خودمان (وب‌هوک با `reference` هم پیدا می‌شود)
    const providerSessionId = String(pick(r, ['session_id', 'sessionId', 'id', 'token']) || `ref:${intentId}`);
    const exp = Number(pick(r, ['expires_at', 'expiresAt', 'expiry']));
    //  ثانیه یا میلی‌ثانیه؛ نبود ⇒ ۱۵ دقیقه
    const expiresAt = Number.isFinite(exp) && exp > 0 ? (exp < 1e12 ? exp * 1000 : exp) : Date.now() + 15 * 60 * 1000;
    return { providerSessionId, checkoutUrl, expiresAt };
  }

  /**
   * امضا: HMAC-SHA256 ِ بدنهٔ خام با رازِ **همین دکان**، مقایسهٔ زمان‌ثابت (TODO(hesabpay-sandbox): سرآیند).
   * ⚠️ افزونهٔ WooCommerce به‌جای سنجشِ محلی درِ `hesab/webhooks/verify-signature` ِ حساب‌پی را
   * صدا می‌زند؛ اگر سندِ رسمی همان را گفت، فقط همین تابع عوض می‌شود. تأییدِ دوم با `fetchStatus`
   * (پیش‌فرض روشن) در هر حال پیش از «پرداخت شد» انجام می‌شود.
   */
  async function verifyWebhook(raw, headers) {
    if (!sec) return { ok: false, reason: 'not_configured' };
    if (!Buffer.isBuffer(raw) || !raw.length) return { ok: false, reason: 'empty_body' };
    const given = SIG_HEADERS.map((h) => headers[h]).find((v) => typeof v === 'string' && v.length);
    const sig = String(given || '').trim().replace(/^sha256=/i, '').toLowerCase();
    if (!P.safeEqual(sig, P.hmacHex(sec, raw))) return { ok: false, reason: 'bad_signature' };
    let b;
    try { b = JSON.parse(raw.toString('utf8')); } catch { return { ok: false, reason: 'bad_json' }; }
    if (!b || typeof b !== 'object') return { ok: false, reason: 'bad_json' };
    //  TODO(hesabpay-sandbox): نامِ فیلدهای وب‌هوک
    const sessionId = String(pick(b, ['session_id', 'sessionId', 'payment_session_id']) || '');
    const reference = String(pick(b, REF_FIELDS) || '');
    //  در رویداد (حافظه) برای سنجشِ پذیرنده؛ هرگز در `raw` ِ ثبت‌شده
    const merchantId = String(pick(b, MERCHANT_FIELDS) ?? '').slice(0, 200);
    const providerRef = String(pick(b, ['transaction_id', 'transactionId', 'reference_number', 'txn_id']) || '');
    const status = statusOf(pick(b, ['status', 'payment_status', 'state', 'event']));
    //  شناسهٔ رویداد؛ نبود ⇒ اثرِ انگشتِ بدنه (همان بدنهٔ تکراری همان رویداد است)
    const eventId = String(pick(b, ['event_id', 'eventId', 'webhook_id', 'notification_id']) || '')
      || `sha256:${crypto.createHash('sha256').update(raw).digest('hex')}`;
    return {
      ok: true,
      event: {
        eventId, sessionId, reference, status, amountMinor: amountOf(b), currency: currencyOf(b), providerRef, merchantId,
        //  فقط فیلدهای لازم برای تطبیق — بی سرآیند و امضا
        raw: { sessionId, reference, status: pick(b, ['status', 'payment_status', 'state', 'event']) ?? null,
          amount: pick(b, ['amount', 'total', 'total_amount', 'price']) ?? null, currency: pick(b, ['currency', 'currency_code']) ?? null,
          providerRef },
      },
    };
  }

  async function fetchStatus(sessionId) {
    const r = await call(key, 'GET', STATUS_PATH(sessionId));
    return {
      status: statusOf(pick(r, ['status', 'payment_status', 'state'])),
      amountMinor: amountOf(r),
      currency: currencyOf(r),
      providerRef: String(pick(r, ['transaction_id', 'transactionId', 'reference_number', 'txn_id']) || ''),
      merchantId: String(pick(r, MERCHANT_FIELDS) ?? '').slice(0, 200),
      reference: String(pick(r, REF_FIELDS) || '').slice(0, 200),
    };
  }

  return Object.freeze({
    name: 'hesabpay',
    perShop: true,
    webhookToken: String(webhookToken || ''),
    payeeFp: String(payeeFp || ''),
    merchantMatches,
    configured: () => Boolean(key && sec && baseAllowed(env().base)),
    createSession, verifyWebhook, fetchStatus,
  });
}

module.exports = {
  name: 'hesabpay',
  //  ⛔ بی createSession / verifyWebhook / fetchStatus ِ سطحِ پرونده: فقط `forShop(کلیدهای همان دکان)`
  configured, forShop, peekRefs, baseAllowed,
  SIG_HEADER: SIG_HEADERS[0], DEFAULT_BASE, MAX_TIMEOUT_MS,
  requestTimeoutMs: () => env().timeoutMs,
};
