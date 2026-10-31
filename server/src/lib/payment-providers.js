'use strict';
/**
 * درگاه‌های پرداخت — قراردادِ مشترک، فهرست و ارائه‌دهندهٔ ساختگی (PAY-21، PAY-08).
 *
 * هر ارائه‌دهنده (امروز حساب‌پی؛ فردا M-Paisa، My Money، e& money، ATOMA Pay) یک
 * آداپتور با همین شکل است و **همهٔ** جزئیاتِ خودش (نشانی، سرآیند، نامِ فیلدها، امضا)
 * فقط در فایلِ خودش می‌ماند؛ بقیهٔ سرور فقط این قرارداد را می‌شناسد:
 *
 *   name                    'hesabpay' | 'fake' | …
 *   configured()            ⇒ boolean — کلیدها داده شده‌اند؟ (نه ⇒ «پیکربندی نشده»، بخش خاموش)
 *   createSession({intentId, amountMinor, currency, description, returnUrl, callbackUrl})
 *                           ⇒ { providerSessionId, checkoutUrl, expiresAt }
 *   verifyWebhook(rawBody: Buffer, headers)
 *                           ⇒ { ok:false, reason } | { ok:true, event }
 *       event = { eventId, sessionId, reference, status, amountMinor, currency, providerRef, raw }
 *       status ∈ 'paid' | 'failed' | 'pending' | 'expired' | 'cancelled'
 *   fetchStatus(sessionId)  ⇒ { status, amountMinor, currency, providerRef } (اختیاری؛ نبود ⇒ تأییدِ دوم ممکن نیست)
 *
 * ⛔ ساختگی (`fake`) فقط با `NODE_ENV` ِ صریحِ `test` یا `development`، `PAYMENTS_PROVIDER=fake` و
 *    `PAYMENTS_FAKE_SECRET` ِ داده‌شده (رازِ پیش‌فرض در کد نیست)؛ تولید، NODE_ENV ِ خالی یا هر محیطِ
 *    دیگر (staging…) ⇒ هرگز، حتی اگر متغیر داده شده باشد (`activeName`) — و `/webhook/fake` ۴۰۱.
 * ⛔ هیچ کلیدی در کد نیست؛ همه از محیط و **هر بار** خوانده می‌شوند (تنظیمِ تازه بی ری‌استارتِ کد).
 */
const crypto = require('crypto');

// ── پول: واحدِ کوچک (یک‌صدمِ افغانی) ⇄ رشتهٔ دهدهیِ ارائه‌دهنده — دقیق، بی ممیزِ شناور ──

const MAX_MINOR = Number.MAX_SAFE_INTEGER;

/** 145050 ⇒ '1450.50' — همیشه دو رقمِ اعشار، بی گرد کردن. */
function minorToDecimal(minor) {
  if (!Number.isSafeInteger(minor) || minor < 0) throw new RangeError('minorToDecimal: عددِ صحیحِ نامنفی لازم است');
  const s = String(minor).padStart(3, '0');
  return `${s.slice(0, -2)}.${s.slice(-2)}`;
}

/**
 * '1450.5' | 1450.5 | '1450' ⇒ 145050؛ بیش از دو رقمِ اعشار، منفی، نماد یا بیرون از بازه ⇒ null.
 * گرد نمی‌کند: مبلغِ ارائه‌دهنده باید دقیقاً همان پول باشد، وگرنه «اختلاف» است.
 */
function decimalToMinor(v) {
  let s;
  if (typeof v === 'number') {
    if (!Number.isFinite(v) || v < 0) return null;
    s = String(v);
    if (/e/i.test(s)) return null;
  } else if (typeof v === 'string') s = v.trim();
  else return null;
  const m = /^(\d{1,16})(?:\.(\d{1,2}))?$/.exec(s);
  if (!m) return null;
  const minor = Number(m[1]) * 100 + Number((m[2] || '').padEnd(2, '0'));
  return Number.isSafeInteger(minor) && minor <= MAX_MINOR ? minor : null;
}

/** مقایسهٔ زمان‌ثابت دو رشته (طولِ نابرابر ⇒ false، باز هم بی نشتِ زمان روی محتوا). */
function safeEqual(a, b) {
  const x = Buffer.from(String(a || ''), 'utf8');
  const y = Buffer.from(String(b || ''), 'utf8');
  if (x.length !== y.length || x.length === 0) {
    crypto.timingSafeEqual(x, x);
    return false;
  }
  return crypto.timingSafeEqual(x, y);
}

const hmacHex = (secret, raw) => crypto.createHmac('sha256', String(secret)).update(raw).digest('hex');

// ── ساختگی: قطعی، برای آزمون و توسعه ─────────────────────────────────────

/*
 *  ⛔ فقط برای آزمون و سرورِ توسعه. نشانیِ پرداخت ساختگی است و هیچ پولی جابه‌جا
 *  نمی‌شود. رویدادِ «پرداخت شد» را آزمون (یا `scripts/fake-pay.js`) با امضای
 *  `PAYMENTS_FAKE_SECRET` به `/api/v1/payments/webhook/fake` می‌فرستد و وضعیتِ
 *  تأییدِ دوم را با `fake.settle` می‌گذارد. بی آن راز ساختگی «پیکربندی نشده» است.
 */
const FAKE_SIG_HEADER = 'x-fake-signature';
const fakeState = new Map();

/** رازِ امضای ساختگی فقط از محیط؛ نبود ⇒ ساختگی خاموش (هرگز پیش‌فرضی که هر کسی بداند). */
function fakeSecret() { return String(process.env.PAYMENTS_FAKE_SECRET || '').trim(); }
function fakeSecretOrThrow() {
  const s = fakeSecret();
  if (!s) throw new Error('fake: PAYMENTS_FAKE_SECRET داده نشده');
  return s;
}

const fake = Object.freeze({
  name: 'fake',
  SIG_HEADER: FAKE_SIG_HEADER,
  configured: () => fakeSecret() !== '',
  /** شناسهٔ جلسهٔ ساختگی از خودِ شناسهٔ قصد — قطعی، تا اسکریپتِ توسعه بی دیتابیس بسازدش. */
  sessionOf: (intentId) => `fake_${crypto.createHash('sha256').update(String(intentId)).digest('hex').slice(0, 24)}`,
  async createSession({ intentId, amountMinor, currency }) {
    if (process.env.PAYMENTS_FAKE_FAIL === '1') throw new Error('fake: createSession failed');
    const sid = fake.sessionOf(intentId);
    fakeState.set(sid, { status: 'pending', amountMinor, currency, providerRef: '' });
    const ttl = Number(process.env.PAYMENTS_FAKE_TTL_MS) || 15 * 60 * 1000;
    return { providerSessionId: sid, checkoutUrl: `https://pay.invalid/fake/${sid}`, expiresAt: Date.now() + ttl };
  },
  /** امضا: HMAC-SHA256 ِ بدنهٔ خام، hex، در `x-fake-signature`. */
  sign: (raw) => hmacHex(fakeSecretOrThrow(), raw),
  async verifyWebhook(raw, headers) {
    const secret = fakeSecret();
    if (!secret) return { ok: false, reason: 'not_configured' };
    if (!Buffer.isBuffer(raw) || !raw.length) return { ok: false, reason: 'empty_body' };
    if (!safeEqual(headers[FAKE_SIG_HEADER], hmacHex(secret, raw))) return { ok: false, reason: 'bad_signature' };
    let b;
    try { b = JSON.parse(raw.toString('utf8')); } catch { return { ok: false, reason: 'bad_json' }; }
    if (!b || typeof b !== 'object') return { ok: false, reason: 'bad_json' };
    //  توسعه (`scripts/fake-pay.js`): رویدادِ امضاشده با `settle: true` همان را «وضعیتِ ارائه‌دهنده» هم می‌کند
    if (b.settle === true && b.sessionId) {
      fakeState.set(String(b.sessionId), { status: String(b.status || ''), amountMinor: b.amountMinor, currency: String(b.currency || ''), providerRef: String(b.providerRef || '') });
    }
    return {
      ok: true,
      event: {
        eventId: String(b.eventId || ''), sessionId: String(b.sessionId || ''), reference: String(b.reference || ''),
        status: String(b.status || ''), amountMinor: Number.isSafeInteger(b.amountMinor) ? b.amountMinor : null,
        currency: String(b.currency || ''), providerRef: String(b.providerRef || ''),
        raw: { eventId: b.eventId, sessionId: b.sessionId, status: b.status, amountMinor: b.amountMinor, currency: b.currency, providerRef: b.providerRef },
      },
    };
  },
  async fetchStatus(sessionId) {
    if (process.env.PAYMENTS_FAKE_STATUS_DOWN === '1') throw new Error('fake: status endpoint down');
    const s = fakeState.get(String(sessionId));
    if (!s) return { status: 'unknown', amountMinor: null, currency: '', providerRef: '' };
    return { ...s };
  },
  /** آزمون/توسعه: آن‌چه «ارائه‌دهنده» در تأییدِ دوم می‌گوید. */
  settle(sessionId, patch) {
    const cur = fakeState.get(String(sessionId)) || {};
    fakeState.set(String(sessionId), { ...cur, ...patch });
  },
  _reset() { fakeState.clear(); },
});

// ── فهرست ─────────────────────────────────────────────────────────────────

const ADAPTERS = { fake };
function adapter(name) {
  if (name === 'hesabpay') return require('./payment-hesabpay');
  return ADAPTERS[name] || null;
}

const isProduction = () => (process.env.NODE_ENV || '') === 'production';
/** محیطی که ساختگی و سرورِ ساختگیِ loopback را می‌پذیرد — فقط با NODE_ENV ِ صریح؛ خالی یا هر نامِ دیگر نه. */
const FAKE_ENVS = Object.freeze(['test', 'development']);
const isTestOrDev = () => FAKE_ENVS.includes(String(process.env.NODE_ENV || '').trim());

/** ساختگی مجاز است؟ فقط با NODE_ENV ِ صریحِ test/development، درخواستِ صریح و رازِ داده‌شده. */
function fakeAllowed() {
  return isTestOrDev() && (process.env.PAYMENTS_PROVIDER || '').trim() === 'fake' && fake.configured();
}

let warnedFakeIgnored = false;
/**
 * ارائه‌دهندهٔ «حساب‌پی» ِ این سرور: در توسعه/آزمون با `PAYMENTS_PROVIDER=fake` همان ساختگی،
 * وگرنه آداپتورِ واقعی. fake ِ نامجاز (تولید، NODE_ENV ِ دیگر، بی راز) ⇒ نادیده و یک هشدار در لاگ.
 */
function activeName() {
  if (fakeAllowed()) return 'fake';
  if ((process.env.PAYMENTS_PROVIDER || '').trim() === 'fake' && !warnedFakeIgnored) {
    warnedFakeIgnored = true;
    console.error('[payments] PAYMENTS_PROVIDER=fake نادیده گرفته شد — ساختگی فقط با NODE_ENV=test|development و PAYMENTS_FAKE_SECRET');
  }
  return 'hesabpay';
}

/** وب‌هوک از کدام آداپتور پذیرفته می‌شود — ساختگی فقط وقتی مجاز است. */
function webhookAdapter(name) {
  if (name === 'fake') return fakeAllowed() ? fake : null;
  return adapter(name);
}

module.exports = {
  minorToDecimal, decimalToMinor, safeEqual, hmacHex,
  adapter, activeName, webhookAdapter, fakeAllowed, isProduction, isTestOrDev, fake,
};
