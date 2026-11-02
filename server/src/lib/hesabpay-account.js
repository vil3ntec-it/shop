'use strict';
/**
 * حساب‌پیِ هر دکان — پول مستقیم به حسابِ همان دکان (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱؛ PAY-21، PAY-06، مهاجرتِ ۲۰۴).
 *
 * ── دو حالت ───────────────────────────────────────────────────────────
 *   simple  شمارهٔ حساب‌پیِ خودِ دکان یا QR ِ خودش (یک بار اسکن) — صندوق همان را با مبلغ و کدِ فاکتور نشان
 *           می‌دهد؛ «رسید» فقط با زدنِ «پول رسید» ِ صندوقدار. سرور هیچ پولی نمی‌بیند و هرگز خودکار تأیید نمی‌کند.
 *   auto    کلید و رازِ پذیرندهٔ خودِ دکان ⇒ قصدِ پرداختِ سرور (`payment-intents`) و وب‌هوکِ امضاشده با رازِ
 *           **همین دکان** (`/api/v1/payments/webhook/hesabpay/<webhook_token>`)؛ «پرداخت شد» فقط با مرجع و مبلغِ
 *           برابر از راهِ رسمی (وب‌هوک + تأییدِ دوم).
 *   ⛔ کلیدِ سکو (`HESABPAY_API_KEY` ِ محیط) هرگز جایگزین نیست: دکانِ بی‌حساب یا با حسابِ خراب ⇒ خاموش.
 *   ⛔ «مبلغِ پیامک برابرِ فاکتور ⇒ تأیید» ساخته نمی‌شود (جعلِ پیامک، دو فاکتورِ هم‌مبلغ، سیاستِ پیامکِ Play).
 *
 * ── راز ───────────────────────────────────────────────────────────────
 *   کلید، راز و شناسهٔ پذیرنده فقط رمزشده (AES-256-GCM، برچسبِ ۱۶ بایتی، AAD = دکان + ستون) با کلیدِ
 *   `HESABPAY_CRED_KEY` ِ محیط (۶۴ رقمِ hex، base64 ِ ۳۲ بایت، یا عبارتِ ≥ ۳۲ نویسه)؛ چرخشِ کلید با
 *   `HESABPAY_CRED_KEY_PREVIOUS` (فقط خواندن). نبودِ کلید ⇒ ذخیرهٔ «خودکار» با ۵۰۳ ِ روشن رد می‌شود.
 *   هیچ درِ API کلید یا راز را برنمی‌گرداند (فقط چهار نویسهٔ آخرِ کلید)؛ لاگ و سابقه هم نه.
 *
 * ── رمزِ حساب‌پی ──────────────────────────────────────────────────────
 *   جدا از رمزِ برنامه و ورود؛ ۶ رقم، نه یکسان و نه پشتِ سرِ هم؛ PBKDF2-SHA256 (۶۰۰٬۰۰۰ دور، نمکِ ۱۶ بایتی).
 *   فقط صاحبِ دکان. بازکردنِ بخش، عوض/پاک کردنِ حساب و عوض کردنِ رمز همه رمز می‌خواهند — در سرور، نه فقط در
 *   برنامه. ۵ خطا ⇒ قفل ۱۵ دقیقه، هر ۵ خطای بعدی دوبرابر تا ۲۴ ساعت. هر تغییر در همان تراکنش سابقه دارد
 *   (`audit_logs`، زنجیرهٔ هش) و به دستگاه‌های صاحبِ دکان خبر می‌رود. رمزِ فراموش‌شده: «بازنشانی» حساب را هم
 *   پاک می‌کند و تا ۲۴ ساعت حسابِ تازه نمی‌پذیرد (کسی که فقط نشستِ صاحب را دزدیده نمی‌تواند بی‌صدا پول را
 *   به حسابِ خودش ببرد).
 */
const crypto = require('crypto');
const { promisify } = require('util');
const config = require('../config');
const { one, query, tx, now } = require('../db');
const audit = require('./audit');
const { badRequest, forbidden, conflict, ApiError } = require('../middleware/errors');

const pbkdf2 = promisify(crypto.pbkdf2);

const MODES = Object.freeze(['off', 'simple', 'auto']);
const PIN_ITER = 600_000;
const PIN_MAX_FAILURES = 5;
const LOCK_BASE_MS = 15 * 60 * 1000;
const LOCK_MAX_MS = 24 * 60 * 60 * 1000;
const RESET_HOLD_MS = 24 * 60 * 60 * 1000;
const QR_MAX = 512;
const CRED_RE = /^[\x21-\x7e]{8,512}$/;
const MERCHANT_RE = /^[\x21-\x7e]{1,128}$/;
const TOKEN_RE = /^[A-Za-z0-9_-]{32}$/;

const isTestOrDev = () => ['test', 'development'].includes(String(process.env.NODE_ENV || '').trim());

// ════════════════════════════════════════════════════════════════════
//  رمزگذاریِ کلیدهای دکان
// ════════════════════════════════════════════════════════════════════

/** کلیدِ ۳۲ بایتی از متنِ محیط؛ کوتاه‌تر از ۳۲ نویسه (و نه hex/base64 ِ ۳۲ بایت) ⇒ null (ضعیف). */
function keyFrom(raw) {
  const s = String(raw || '').trim();
  if (!s) return null;
  if (/^[0-9a-fA-F]{64}$/.test(s)) return Buffer.from(s, 'hex');
  if (/^[A-Za-z0-9+/]{43}=$/.test(s) || /^[A-Za-z0-9_-]{43}$/.test(s)) {
    const b = Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
    if (b.length === 32) return b;
  }
  return s.length >= 32 ? crypto.createHash('sha256').update(s, 'utf8').digest() : null;
}

/** 'ok' | 'missing' | 'weak' — بی هیچ چیزی از خودِ کلید */
function credKeyState() {
  const raw = String(process.env.HESABPAY_CRED_KEY || '').trim();
  if (!raw) return 'missing';
  return keyFrom(raw) ? 'ok' : 'weak';
}
const credKeyReady = () => credKeyState() === 'ok';

function credKeyError() {
  return new ApiError(503, 'hesabpay_cred_key_missing',
    'سرور هنوز کلیدِ رمزگذاریِ حساب‌پی ندارد؛ کلیدهای دکان ذخیره نمی‌شوند. به پشتیبانی بگویید (HESABPAY_CRED_KEY).');
}

const aad = (shopId, field) => Buffer.from(`shop-hesabpay:v1:${shopId}:${field}`, 'utf8');

function seal(shopId, field, plain) {
  const key = keyFrom(process.env.HESABPAY_CRED_KEY);
  if (!key) throw credKeyError();
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', key, iv, { authTagLength: 16 });
  c.setAAD(aad(shopId, field));
  const body = Buffer.concat([c.update(String(plain), 'utf8'), c.final()]);
  return `v1.${iv.toString('base64url')}.${body.toString('base64url')}.${c.getAuthTag().toString('base64url')}`;
}

/** رمزگشایی با کلیدِ امروز و سپس کلیدِ پیشین؛ هر شکستی (کلیدِ دیگر، دست‌کاری، دکانِ دیگر) ⇒ null. */
function unseal(shopId, field, stored) {
  const parts = String(stored || '').split('.');
  if (parts.length !== 4 || parts[0] !== 'v1') return null;
  const keys = [process.env.HESABPAY_CRED_KEY, process.env.HESABPAY_CRED_KEY_PREVIOUS].map(keyFrom).filter(Boolean);
  for (const key of keys) {
    try {
      const d = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(parts[1], 'base64url'), { authTagLength: 16 });
      d.setAAD(aad(shopId, field));
      d.setAuthTag(Buffer.from(parts[3], 'base64url'));
      return Buffer.concat([d.update(Buffer.from(parts[2], 'base64url')), d.final()]).toString('utf8');
    } catch { /* کلیدِ بعدی */ }
  }
  return null;
}

// ════════════════════════════════════════════════════════════════════
//  رمزِ حساب‌پی (PIN)
// ════════════════════════════════════════════════════════════════════

/** رقم‌های فارسی/عربی ⇒ لاتین */
const latinDigits = (v) => String(v).replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
  .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));

/** null = خوب؛ 'length' (نه ۶ رقم) | 'weak' (همه یکسان یا پشتِ سرِ هم) — همان `PinGuard.newPinProblem` ِ برنامه */
function pinProblem(pin) {
  if (typeof pin !== 'string') return 'length';
  const p = latinDigits(pin);
  if (!/^\d{6}$/.test(p)) return 'length';
  if (/^(\d)\1{5}$/.test(p)) return 'weak';
  const d = [...p].map(Number);
  const step = (k) => d.every((x, i) => i === 0 || x - d[i - 1] === k);
  return step(1) || step(-1) ? 'weak' : null;
}

/** دورهای PBKDF2 ِ هش‌های تازه؛ فقط در آزمون/توسعه با `HESABPAY_PIN_ITER` کمتر (هرگز در تولید). */
function pinIter() {
  const v = Number(process.env.HESABPAY_PIN_ITER);
  return isTestOrDev() && Number.isSafeInteger(v) && v >= 1000 ? v : PIN_ITER;
}

async function hashPin(pin) {
  const salt = crypto.randomBytes(16);
  const iter = pinIter();
  const h = await pbkdf2(latinDigits(pin), salt, iter, 32, 'sha256');
  return `pbkdf2-sha256$${iter}$${salt.toString('base64')}$${h.toString('base64')}`;
}

async function pinMatches(pin, stored) {
  const parts = String(stored || '').split('$');
  if (parts.length !== 4 || parts[0] !== 'pbkdf2-sha256' || typeof pin !== 'string') return false;
  const iter = Number(parts[1]);
  const floor = isTestOrDev() ? 1000 : 100_000;
  if (!Number.isSafeInteger(iter) || iter < floor || iter > 10_000_000) return false;
  const want = Buffer.from(parts[3], 'base64');
  if (want.length !== 32) return false;
  const got = await pbkdf2(latinDigits(pin), Buffer.from(parts[2], 'base64'), iter, 32, 'sha256');
  return crypto.timingSafeEqual(got, want);
}

const lockMs = (failures) => Math.min(LOCK_MAX_MS, LOCK_BASE_MS * 2 ** (failures / PIN_MAX_FAILURES - 1));

function lockedError(until, t) {
  const e = new ApiError(423, 'hesabpay_pin_locked', 'رمزِ حساب‌پی چند بار اشتباه زده شد و فعلاً بسته است؛ بعداً دوباره بکوشید.');
  e.data = { lockedUntil: Number(until), retryAfter: Math.max(1, Math.ceil((Number(until) - t) / 1000)) };
  return e;
}
function wrongError(left) {
  const e = forbidden('رمزِ حساب‌پی درست نیست.', 'hesabpay_pin_wrong');
  e.data = { attemptsLeft: left };
  return e;
}

// ════════════════════════════════════════════════════════════════════
//  شماره و QR ِ حالتِ ساده
// ════════════════════════════════════════════════════════════════════

/**
 * شمارهٔ موبایلِ افغانستان (حساب‌پی با شمارهٔ موبایل است) ⇒ `07XXXXXXXX`؛ وگرنه null.
 * TODO(hesabpay-sandbox): اگر حساب‌پی شناسهٔ کیف‌پولِ غیرِ شماره هم دارد، همین‌جا بپذیرید.
 */
function normAccount(v) {
  if (typeof v !== 'string') return null;
  const s = latinDigits(v).replace(/[\s\-().]/g, '');
  const m = /^(?:\+93|0093|93|0)?(7\d{8})$/.exec(s);
  return m ? `0${m[1]}` : null;
}

/**
 * متنِ QR ِ خودِ دکان — همان‌طور نگه داشته و در صندوق دوباره کشیده می‌شود (مشتری همان QR ِ چاپیِ دکان را
 * می‌بیند). ⇒ '' (نداده) | null (نامعتبر) | متن.
 * TODO(hesabpay-sandbox): شکلِ QR ِ حساب‌پی (EMVCo؟ نشانی؟) تأیید نشده؛ اگر مبلغ/مرجع را می‌پذیرد، صندوق
 * آن‌ها را در QR بگذارد (`core/gateway/HesabPaySimple.kt`)؛ تا آن روز مبلغ و کد فقط درشت نوشته می‌شوند.
 */
function normQr(v) {
  if (v === undefined || v === null) return '';
  if (typeof v !== 'string') return null;
  const s = v.trim();
  if (!s) return '';
  if (s.length > QR_MAX || /[\u0000-\u001f\u007f]/.test(s)) return null;
  return s;
}

/** شماره‌ای که در خودِ QR آمده (اگر آمده) */
function accountInQr(qr) {
  const m = /(?:\+93|0093|93|0)?(7\d{8})(?!\d)/.exec(latinDigits(qr).replace(/[\s-]/g, ''));
  return m ? `0${m[1]}` : '';
}

// ════════════════════════════════════════════════════════════════════
//  ردیف، نما
// ════════════════════════════════════════════════════════════════════

async function lockRow(c, shopId) {
  const t = now();
  await c.query(
    `INSERT INTO shop_hesabpay (shop_id, created_at, updated_at) VALUES ($1,$2,$2) ON CONFLICT (shop_id) DO NOTHING`,
    [shopId, t]
  );
  return (await c.query('SELECT * FROM shop_hesabpay WHERE shop_id=$1 FOR UPDATE', [shopId])).rows[0];
}

const rowOf = (shopId) => one('SELECT * FROM shop_hesabpay WHERE shop_id=$1', [shopId]);

/** نشانیِ وب‌هوکِ همین دکان: پایهٔ `HESABPAY_CALLBACK_URL` (بی دکان) یا `SERVER_URL`؛ هیچ ⇒ مسیرِ نسبی. */
function webhookUrlOf(token) {
  const base = String(process.env.HESABPAY_CALLBACK_URL || '').trim().replace(/\/+$/, '')
    || `${String(config.serverUrl || '').trim().replace(/\/+$/, '')}/api/v1/payments/webhook/hesabpay`;
  return token ? `${base}/${token}` : '';
}

const flagOn = (shopId) => require('./orders').flagOf(shopId, 'pay.hesabpay');

/** حال بی رمز — فقط برای صاحب: رمز گذاشته شده؟ قفل؟ وصل است؟ هیچ شماره یا کلیدی نه. */
async function statusOf(shopId, row = undefined) {
  const r = row === undefined ? await rowOf(shopId) : row;
  const t = now();
  const lockedUntil = Number(r?.pin_locked_until || 0);
  const hold = Number(r?.hold_until || 0);
  const mode = MODES.includes(r?.mode) ? r.mode : 'off';
  const flag = await flagOn(shopId);
  const key = credKeyReady();
  return {
    pinSet: Boolean(r?.pin_hash),
    locked: lockedUntil > t,
    lockedUntil: lockedUntil > t ? lockedUntil : null,
    mode,
    connected: mode !== 'off',
    holdUntil: hold > t ? hold : null,
    //  «خودکار» برای این دکان شدنی است؟ پرچمِ `pay.hesabpay` + کلیدِ رمزگذاریِ سرور
    autoAvailable: flag && key,
    autoReason: !key ? 'server_not_ready' : !flag ? 'flag_off' : '',
  };
}

/** نمای کامل پس از رمز — شماره و QR (همان که مشتری می‌بیند)؛ از کلیدها فقط چهار نویسهٔ آخر. */
async function fullOf(shopId, row) {
  const base = await statusOf(shopId, row);
  return {
    ...base,
    account: row.mode === 'simple' ? row.account : '',
    qrPayload: row.mode === 'simple' ? row.qr_payload : '',
    auto: row.mode === 'auto' ? {
      keyLast4: row.key_last4,
      secretSet: Boolean(row.secret_enc),
      merchantSet: Boolean(row.merchant_enc),
      webhookUrl: webhookUrlOf(row.webhook_token),
    } : null,
  };
}

/** برای صندوق (`/shop/payments/config`): حالت و — فقط در «ساده» — شماره و QR ِ خودِ دکان. */
async function tillView(shopId) {
  const r = await one('SELECT mode, account, qr_payload FROM shop_hesabpay WHERE shop_id=$1', [shopId]);
  const mode = MODES.includes(r?.mode) ? r.mode : 'off';
  return { mode, simple: mode === 'simple' ? { account: r.account, qrPayload: r.qr_payload } : null };
}

// ════════════════════════════════════════════════════════════════════
//  آداپتورِ درگاه (فقط سرور؛ هرگز بیرون نمی‌رود)
// ════════════════════════════════════════════════════════════════════

function adapterOfRow(row) {
  if (!row || row.mode !== 'auto' || !row.webhook_token || !credKeyReady()) return null;
  const apiKey = unseal(row.shop_id, 'api_key', row.api_key_enc);
  const secret = unseal(row.shop_id, 'secret', row.secret_enc);
  if (!apiKey || !secret) return null;
  const merchantId = row.merchant_enc ? unseal(row.shop_id, 'merchant', row.merchant_enc) : '';
  if (merchantId === null) return null;
  const ad = require('./payment-hesabpay').forShop({ apiKey, secret, merchantId, webhookToken: row.webhook_token });
  return ad.configured() ? ad : null;
}

const GATEWAY_COLS = 'shop_id, mode, api_key_enc, secret_enc, merchant_enc, webhook_token';

/** آداپتورِ حساب‌پیِ **همین** دکان (حالتِ خودکار، کلیدِ سالم) یا null — هرگز کلیدِ سکو. */
async function gatewayOf(shopId) {
  if (!credKeyReady()) return null;
  return adapterOfRow(await one(`SELECT ${GATEWAY_COLS} FROM shop_hesabpay WHERE shop_id=$1`, [shopId]));
}

/** درِ وب‌هوکِ دکان: نشانی ⇒ دکان و آداپتورش؛ ناشناخته یا بی‌کلید ⇒ null. */
async function byWebhookToken(token) {
  if (!TOKEN_RE.test(String(token || '')) || !credKeyReady()) return null;
  const row = await one(`SELECT ${GATEWAY_COLS} FROM shop_hesabpay WHERE webhook_token=$1`, [String(token)]);
  const ad = adapterOfRow(row);
  return ad ? { shopId: row.shop_id, adapter: ad } : null;
}

// ════════════════════════════════════════════════════════════════════
//  خبر به صاحب
// ════════════════════════════════════════════════════════════════════

const NOTICE = Object.freeze({
  account_changed: ['حسابِ حساب‌پی عوض شد', 'حسابِ حساب‌پیِ دکان همین حالا عوض شد. اگر شما نبودید، همین حالا رمزِ حسابِ برنامه را عوض کنید، «خروج از همهٔ دستگاه‌ها» را بزنید و حساب‌پی را دوباره تنظیم کنید.'],
  account_removed: ['حسابِ حساب‌پی برداشته شد', 'حسابِ حساب‌پیِ دکان همین حالا برداشته شد. اگر شما نبودید، همین حالا رمزِ حسابِ برنامه را عوض کنید.'],
  pin_set: ['رمزِ حساب‌پی گذاشته شد', 'برای بخشِ حساب‌پیِ دکان رمزِ تازه گذاشته شد. اگر شما نبودید، همین حالا رمزِ حسابِ برنامه را عوض کنید.'],
  pin_changed: ['رمزِ حساب‌پی عوض شد', 'رمزِ بخشِ حساب‌پیِ دکان همین حالا عوض شد. اگر شما نبودید، همین حالا رمزِ حسابِ برنامه را عوض کنید.'],
  pin_reset: ['رمزِ حساب‌پی بازنشانی شد', 'رمز و حسابِ حساب‌پیِ دکان پاک شد و تا ۲۴ ساعت حسابِ تازه پذیرفته نمی‌شود. اگر شما نبودید، همین حالا رمزِ حسابِ برنامه را عوض کنید.'],
  pin_locked: ['رمزِ حساب‌پی قفل شد', 'رمزِ بخشِ حساب‌پی چند بار اشتباه زده شد و فعلاً بسته است. اگر شما نبودید، کسی دارد امتحان می‌کند — رمزِ حسابِ برنامه را عوض کنید.'],
});

/** پوش به دستگاه‌های صاحبانِ همین دکان — شکستش کار را برنمی‌گرداند. */
async function notifyOwners(shopId, kind) {
  const n = NOTICE[kind];
  if (!n) return null;
  try {
    const owners = (await query(
      `SELECT user_id FROM shop_members WHERE shop_id=$1 AND role='owner' AND status='active'`, [shopId]
    )).rows.map((r) => r.user_id);
    if (!owners.length) return { sent: 0 };
    return await require('./push').sendTo({ shopId, app: 'shop', onlyUserIds: owners },
      { title: n[0], body: n[1], data: { type: 'hesabpay_changed', kind } });
  } catch (err) {
    console.warn('[hesabpay] خبر به صاحب نرسید:', err.message);
    return null;
  }
}

// ════════════════════════════════════════════════════════════════════
//  کارها (همه فقط صاحب؛ مسیر: routes/shop-hesabpay.js)
// ════════════════════════════════════════════════════════════════════

const logIn = (c, shopId, actor, action, detail = {}) => audit.log({
  shopId, userId: String(actor.id || ''), action, targetType: 'shop', targetId: shopId, detail, ip: actor.ip || '',
}, { client: c });

function pinGiven(body) {
  const pin = body && typeof body === 'object' ? body.pin : undefined;
  if (typeof pin !== 'string' || !pin.trim()) throw badRequest('رمزِ حساب‌پی را بزنید.', 'hesabpay_pin_required');
  return pin.trim();
}

/**
 * کارِ `work(c, row, t)` فقط با رمزِ درست، زیرِ قفلِ ردیفِ دکان. خطای رمز (و قفل) در همان تراکنش شمرده و
 * ثبت می‌شود و **پس از** commit پرتاب می‌شود — شمارش با خطا برنمی‌گردد.
 */
async function withPin(shopId, pin, actor, work) {
  const out = await tx(async (c) => {
    const row = await lockRow(c, shopId);
    const t = now();
    if (!row.pin_hash) return { error: forbidden('اول برای بخشِ حساب‌پی رمز بگذارید.', 'hesabpay_pin_not_set') };
    if (Number(row.pin_locked_until) > t) return { error: lockedError(row.pin_locked_until, t) };
    if (!(await pinMatches(pin, row.pin_hash))) {
      const failures = Number(row.pin_failures) + 1;
      const lockNow = failures % PIN_MAX_FAILURES === 0;
      const until = lockNow ? t + lockMs(failures) : Number(row.pin_locked_until);
      await c.query('UPDATE shop_hesabpay SET pin_failures=$2, pin_locked_until=$3 WHERE shop_id=$1', [shopId, failures, until]);
      await logIn(c, shopId, actor, lockNow ? 'hesabpay.pin_locked' : 'hesabpay.pin_wrong', { failures });
      return {
        error: lockNow ? lockedError(until, t) : wrongError(PIN_MAX_FAILURES - (failures % PIN_MAX_FAILURES)),
        notify: lockNow ? 'pin_locked' : '',
      };
    }
    if (Number(row.pin_failures)) {
      await c.query('UPDATE shop_hesabpay SET pin_failures=0 WHERE shop_id=$1', [shopId]);
      row.pin_failures = 0;
    }
    return work(c, row, t);
  });
  if (out.notify) await notifyOwners(shopId, out.notify);
  if (out.error) throw out.error;
  return out;
}

/** پرداختِ «خودکار» ِ باز (زنده و هنوز نگذشته) ⇒ کلید عوض نشود تا وب‌هوکش با همان راز سنجیده شود. */
async function openGatewayPayment(c, shopId, t) {
  return Boolean((await c.query(
    `SELECT 1 AS x FROM payment_intents WHERE shop_id=$1 AND provider='hesabpay' AND status IN ('created','pending')
        AND expires_at > $2 LIMIT 1`, [shopId, t]
  )).rows[0]);
}
const openPaymentError = () => conflict('یک پرداختِ خودکارِ حساب‌پی همین حالا باز است؛ پس از پایان یا لغوش دوباره بکوشید.',
  'hesabpay_payments_open');

/** حال (بی رمز) */
const status = (shopId) => statusOf(shopId);

/** بازکردنِ بخش با رمز ⇒ نمای کامل */
async function open(shopId, body, actor) {
  const pin = pinGiven(body);
  const out = await withPin(shopId, pin, actor, async (c, row) => ({ row }));
  return fullOf(shopId, out.row);
}

/** گذاشتنِ نخستین رمز (بی رمزِ قبلی) یا عوض کردنش (با رمزِ فعلی) */
async function setPin(shopId, body, actor) {
  const b = body && typeof body === 'object' ? body : {};
  const problem = pinProblem(b.newPin);
  if (problem === 'length') throw badRequest('رمزِ حساب‌پی باید ۶ رقم باشد.', 'hesabpay_pin_format');
  if (problem === 'weak') throw badRequest('این رمز ساده است (همه یکسان یا پشتِ سرِ هم)؛ رمزِ دیگری بگذارید.', 'hesabpay_pin_weak');
  const hash = await hashPin(b.newPin);
  const first = await tx(async (c) => {
    const row = await lockRow(c, shopId);
    if (row.pin_hash) return false;
    const t = now();
    await c.query(
      `UPDATE shop_hesabpay SET pin_hash=$2, pin_failures=0, pin_locked_until=0, pin_changed_at=$3,
              version=version+1, updated_by=$4, updated_at=$3 WHERE shop_id=$1`, [shopId, hash, t, String(actor.id || '')]
    );
    await logIn(c, shopId, actor, 'hesabpay.pin_set', {});
    return true;
  });
  if (first) {
    await notifyOwners(shopId, 'pin_set');
    return statusOf(shopId);
  }
  const pin = pinGiven(b);
  await withPin(shopId, pin, actor, async (c, row, t) => {
    await c.query(
      `UPDATE shop_hesabpay SET pin_hash=$2, pin_failures=0, pin_locked_until=0, pin_changed_at=$3,
              version=version+1, updated_by=$4, updated_at=$3 WHERE shop_id=$1`, [shopId, hash, t, String(actor.id || '')]
    );
    await logIn(c, shopId, actor, 'hesabpay.pin_changed', {});
    return { row };
  });
  await notifyOwners(shopId, 'pin_changed');
  return statusOf(shopId);
}

/** رمز فراموش شد: رمز و حساب پاک، ۲۴ ساعت بی حسابِ تازه، خبر به صاحب. */
async function resetPin(shopId, actor) {
  const out = await tx(async (c) => {
    const row = await lockRow(c, shopId);
    if (!row.pin_hash && row.mode === 'off') return { changed: false };
    const t = now();
    await c.query(
      `UPDATE shop_hesabpay SET pin_hash='', pin_failures=0, pin_locked_until=0, pin_changed_at=$2,
              mode='off', account='', qr_payload='', api_key_enc='', secret_enc='', merchant_enc='', key_last4='',
              hold_until=$3, version=version+1, updated_by=$4, updated_at=$2 WHERE shop_id=$1`,
      [shopId, t, t + RESET_HOLD_MS, String(actor.id || '')]
    );
    await logIn(c, shopId, actor, 'hesabpay.pin_reset', { previousMode: row.mode, holdHours: RESET_HOLD_MS / 3_600_000 });
    return { changed: true };
  });
  if (out.changed) await notifyOwners(shopId, 'pin_reset');
  return statusOf(shopId);
}

/** ورودیِ حساب ⇒ ستون‌های تازه (پیش از رمز سنجیده می‌شود تا خطای فرم تلاشِ رمز را نسوزاند) */
function parseAccount(shopId, body) {
  const b = body && typeof body === 'object' ? body : {};
  const mode = String(b.mode || '');
  if (mode === 'simple') {
    const qr = normQr(b.qr);
    if (qr === null) throw badRequest('متنِ QR نامعتبر یا بلند است.', 'hesabpay_bad_qr');
    const given = b.account === undefined || b.account === null || b.account === '' ? '' : normAccount(b.account);
    if (given === null) throw badRequest('شمارهٔ حساب‌پی درست نیست (مثلِ 0799123456).', 'hesabpay_bad_account');
    const account = given || (qr ? accountInQr(qr) : '');
    if (!account && !qr) throw badRequest('شمارهٔ حساب‌پیِ دکان یا QR ِ آن را بدهید.', 'hesabpay_account_required');
    return { mode, account, qr_payload: qr, api_key_enc: '', secret_enc: '', merchant_enc: '', key_last4: '' };
  }
  if (mode === 'auto') {
    if (!credKeyReady()) throw credKeyError();
    const apiKey = typeof b.apiKey === 'string' ? b.apiKey.trim() : '';
    const secret = typeof b.webhookSecret === 'string' ? b.webhookSecret.trim() : '';
    const merchant = typeof b.merchantId === 'string' ? b.merchantId.trim() : '';
    if (!CRED_RE.test(apiKey)) throw badRequest('کلیدِ API ِ حساب‌پی نامعتبر است.', 'hesabpay_bad_api_key');
    if (!CRED_RE.test(secret)) throw badRequest('رازِ وب‌هوکِ حساب‌پی نامعتبر است.', 'hesabpay_bad_secret');
    if (merchant && !MERCHANT_RE.test(merchant)) throw badRequest('شناسهٔ پذیرنده نامعتبر است.', 'hesabpay_bad_merchant');
    return {
      mode, account: '', qr_payload: '',
      api_key_enc: seal(shopId, 'api_key', apiKey),
      secret_enc: seal(shopId, 'secret', secret),
      merchant_enc: merchant ? seal(shopId, 'merchant', merchant) : '',
      key_last4: apiKey.slice(-4),
    };
  }
  throw badRequest('حالت باید «ساده» (simple) یا «خودکار» (auto) باشد.', 'hesabpay_bad_mode');
}

/** گذاشتن یا عوض کردنِ حسابِ دکان (با رمز) */
async function saveAccount(shopId, body, actor) {
  const cols = parseAccount(shopId, body);
  const pin = pinGiven(body);
  const out = await withPin(shopId, pin, actor, async (c, row, t) => {
    if (Number(row.hold_until) > t) {
      const e = new ApiError(423, 'hesabpay_on_hold', 'پس از بازنشانیِ رمز، تا ۲۴ ساعت حسابِ تازه پذیرفته نمی‌شود.');
      e.data = { holdUntil: Number(row.hold_until) };
      return { error: e };
    }
    if (row.mode === 'auto' && await openGatewayPayment(c, shopId, t)) return { error: openPaymentError() };
    const token = cols.mode === 'auto' ? (row.webhook_token || crypto.randomBytes(24).toString('base64url')) : row.webhook_token;
    const r = (await c.query(
      `UPDATE shop_hesabpay SET mode=$2, account=$3, qr_payload=$4, api_key_enc=$5, secret_enc=$6, merchant_enc=$7,
              key_last4=$8, webhook_token=$9, version=version+1, updated_by=$10, updated_at=$11
        WHERE shop_id=$1 RETURNING *`,
      [shopId, cols.mode, cols.account, cols.qr_payload, cols.api_key_enc, cols.secret_enc, cols.merchant_enc,
        cols.key_last4, token, String(actor.id || ''), t]
    )).rows[0];
    await logIn(c, shopId, actor, 'hesabpay.account_set', {
      mode: cols.mode, previousMode: row.mode,
      ...(cols.mode === 'simple' ? { accountLast4: cols.account.slice(-4), qr: Boolean(cols.qr_payload) } : { keyLast4: cols.key_last4, merchant: Boolean(cols.merchant_enc) }),
    });
    return { row: r };
  });
  if (out.error) throw out.error;
  await notifyOwners(shopId, 'account_changed');
  return fullOf(shopId, out.row);
}

/** برداشتنِ حساب (با رمز) — رمز می‌ماند */
async function removeAccount(shopId, body, actor) {
  const pin = pinGiven(body);
  const out = await withPin(shopId, pin, actor, async (c, row, t) => {
    if (row.mode === 'off') return { row, unchanged: true };
    if (row.mode === 'auto' && await openGatewayPayment(c, shopId, t)) return { error: openPaymentError() };
    const r = (await c.query(
      `UPDATE shop_hesabpay SET mode='off', account='', qr_payload='', api_key_enc='', secret_enc='', merchant_enc='',
              key_last4='', version=version+1, updated_by=$2, updated_at=$3 WHERE shop_id=$1 RETURNING *`,
      [shopId, String(actor.id || ''), t]
    )).rows[0];
    await logIn(c, shopId, actor, 'hesabpay.account_removed', { previousMode: row.mode });
    return { row: r };
  });
  if (out.error) throw out.error;
  if (!out.unchanged) await notifyOwners(shopId, 'account_removed');
  return fullOf(shopId, out.row);
}

module.exports = {
  MODES, PIN_MAX_FAILURES, RESET_HOLD_MS,
  credKeyState, credKeyReady, pinProblem, normAccount, normQr, accountInQr, webhookUrlOf,
  status, open, setPin, resetPin, saveAccount, removeAccount, tillView, gatewayOf, byWebhookToken,
  //  فقط آزمون: رمزگذاری با AAD ِ دکان
  _seal: seal, _unseal: unseal,
};
