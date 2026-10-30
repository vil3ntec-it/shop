'use strict';
/**
 * اعلانِ وضعیتِ سفارش به مشتری — NTF-01 (کدام رویداد از کدام کانال)، NTF-03 (قالب به زبانِ مشتری)
 * و NTF-04 (هر رویداد یک پیام، تکرار ممنوع) — §۶.۱۹ و §۳.۱۲.
 *
 *   orders.create / applyTransition ─┬─ سفارش یا گذار + رویداد          ┐ یک تراکنش
 *                                     └─ ردیفِ customer_notify_outbox   ┘ (onStatusIn)
 *   کارگرِ همین سرور ─▶ کانال‌های مجازِ همان رویداد، ارزان ⇒ گران ─▶ اولین «پذیرفت» ⇒ تمام
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • ⛔ پیش‌فرض خاموش: بی کلیدِ `shop.customer_notify` در «تنظیماتِ زنده» (scope = دکان یا
 *     `all`) هیچ ردیفی نوشته نمی‌شود و هیچ پیامی نمی‌رود؛ خاموش شدنِ پرچم ردیفِ مانده را
 *     `cancelled` می‌کند. فقط زبانِ سفارش (`online_orders.customer_lang`) همیشه ثبت می‌شود.
 *   • فقط سفارشِ ویترین (`source = storefront`، شمارهٔ تأییدشده) و نه آزمایشی؛ سفارشِ دستیِ
 *     کارمند (تلفنی، تلگرام، …) هیچ — شماره‌اش تأیید نشده و رضایتی نداده است.
 *   • NTF-01: `EVENT_OF` (وضعیت ⇒ رویداد) و `MATRIX` (رویداد ⇒ کانال‌های مجاز، به ترتیبِ §۳.۱۲:
 *     پوش، واتس‌اپ، تلگرام، پیامک). پیامک (گران‌ترین) فقط برای رویدادی که مشتری همان دم باید
 *     بداند یا کاری بکند. وضعیت‌های درونیِ دکان (جمع‌آوری، بسته‌بندی، برگشت به قفسه) و کارِ خودِ
 *     مشتری (لغوِ او) پیام ندارند.
 *   • NTF-04: یکتایی (order_id, event)؛ زنجیره در اولین «پذیرفت» می‌ایستد. پاسخِ مبهم (خطا یا
 *     مهلت) کانالِ بعدی را صدا **نمی‌زند** — شاید پیام رفته باشد — و همان زنجیره با همان کلید
 *     (`<ردیف>:<کانال>`) بعداً دوباره، حداکثر `MAX_ATTEMPTS` بار. رویدادِ تازه‌ترِ همان سفارش
 *     ردیف‌های هنوز‌نرفته را `superseded` می‌کند: پیامِ کهنه نمی‌رود.
 *   • سقفِ هر مشتری روی همهٔ دکان‌ها (`PER_HOUR`، `PER_DAY` پیامِ رفته) ⇒ `rate_limited`.
 *     شمارش با `phone_key` (HMAC ِ شماره)، نه خودِ شماره.
 *   • بی دادهٔ حساس (§۳۰.۱ بندِ ۴): متن فقط نامِ ویترین، شمارهٔ سفارش و وضعیت — نه نام، شماره،
 *     نشانی، اقلام یا مبلغ. لینکِ پیگیری (همان توکنِ HMAC ِ صفحهٔ «سفارشِ من») فقط اگر
 *     `STORE_PUBLIC_URL` داده شده باشد. شماره فقط به خودِ سرویس‌دهنده می‌رسد، هرگز به لاگ.
 *   • ⛔ هیچ پیامِ واقعی: امروز هیچ سرویس‌دهنده‌ای وصل نیست (D-09: بودجه و حساب بیرونی‌اند) ⇒
 *     حالِ صادقانهٔ `no_channel`. پیامکِ کدِ ورود (`lib/otp`) این‌جا به کار نمی‌رود. رابط
 *     `setProviders` است؛ امروز فقط آزمون آن را با سرویس‌دهندهٔ جعلی می‌نشاند.
 */
const crypto = require('crypto');
const { query, one, many, now } = require('../db');
const { ulid } = require('./ulid');
const { serverSecret } = require('./server-secret');

const FLAG_KEY = 'shop.customer_notify';

/** ترتیبِ زنجیره (§۳.۱۲): ارزان ⇒ گران. */
const CHANNELS = Object.freeze(['push', 'whatsapp', 'telegram', 'sms']);
const LANGS = Object.freeze(['fa', 'ps', 'en']);

/** NTF-01، گامِ ۱: کدام وضعیت کدام رویدادِ مشتری است. نبودن ⇒ بی پیام. */
const EVENT_OF = Object.freeze({
  new: 'order_received',
  accepted: 'order_accepted',
  rejected: 'order_rejected',
  auto_rejected: 'order_rejected',
  ready_for_pickup: 'order_ready',
  out_for_delivery: 'order_on_the_way',
  delivery_failed: 'order_delivery_failed',
  retry_scheduled: 'order_delivery_retry',
  delivered: 'order_completed',
  picked_up: 'order_completed',
  cancelled: 'order_cancelled',
});

const FREE = Object.freeze(['push', 'whatsapp', 'telegram']);
const URGENT = Object.freeze([...FREE, 'sms']);
/** NTF-01، گامِ ۲: هر رویداد از کدام کانال‌ها (به ترتیبِ `CHANNELS`). */
const MATRIX = Object.freeze({
  order_received: FREE,
  order_accepted: FREE,
  order_rejected: URGENT,          // مشتری منتظر است؛ باید جای دیگری بخرد
  order_ready: URGENT,             // باید بیاید و ببرد
  order_on_the_way: URGENT,        // نقد آماده و در دسترس باشد
  order_delivery_failed: URGENT,   // پیک نرسید؛ تماس لازم است
  order_delivery_retry: FREE,
  order_completed: FREE,
  order_cancelled: URGENT,
});
const EVENTS = Object.freeze(Object.keys(MATRIX));

/**
 * NTF-03: قالبِ هر رویداد به سه زبانِ وبِ خریدار (همان fa، ps، en ِ `public/kit/i18n`).
 * جای‌نگهدارِ مجاز فقط `{no}` (شمارهٔ سفارش) و `{shop}` (نامِ ویترین) — هیچ دادهٔ دیگری.
 */
const TEMPLATES = Object.freeze({
  fa: Object.freeze({
    order_received: 'سفارشِ شمارهٔ {no} در «{shop}» ثبت شد — منتظرِ تأییدِ دکان.',
    order_accepted: '«{shop}» سفارشِ شمارهٔ {no} را قبول کرد.',
    order_rejected: '«{shop}» سفارشِ شمارهٔ {no} را نپذیرفت.',
    order_ready: 'سفارشِ شمارهٔ {no} آمادهٔ دریافت از «{shop}» است.',
    order_on_the_way: 'سفارشِ شمارهٔ {no} از «{shop}» در راه است.',
    order_delivery_failed: 'سفارشِ شمارهٔ {no} از «{shop}» تحویل نشد.',
    order_delivery_retry: 'تحویلِ دوبارهٔ سفارشِ شمارهٔ {no} از «{shop}» برنامه‌ریزی شد.',
    order_completed: 'سفارشِ شمارهٔ {no} از «{shop}» تحویل شد. سپاس!',
    order_cancelled: '«{shop}» سفارشِ شمارهٔ {no} را لغو کرد.',
  }),
  ps: Object.freeze({
    order_received: 'د فرمایش شمېره {no} په «{shop}» کې ثبت شو — د دوکان تایید ته په تمه.',
    order_accepted: '«{shop}» د فرمایش شمېره {no} ومانه.',
    order_rejected: '«{shop}» د فرمایش شمېره {no} ونه مانه.',
    order_ready: 'د فرمایش شمېره {no} له «{shop}» څخه اخیستو ته چمتو ده.',
    order_on_the_way: 'د «{shop}» د فرمایش شمېره {no} په لار کې ده.',
    order_delivery_failed: 'د «{shop}» د فرمایش شمېره {no} ونه رسېده.',
    order_delivery_retry: 'د «{shop}» د فرمایش شمېره {no} بیا رسول ټاکل شوي.',
    order_completed: 'د «{shop}» د فرمایش شمېره {no} ورسېده. مننه!',
    order_cancelled: '«{shop}» د فرمایش شمېره {no} لغوه کړه.',
  }),
  en: Object.freeze({
    order_received: 'Order #{no} at {shop} is placed — waiting for the shop.',
    order_accepted: '{shop} accepted order #{no}.',
    order_rejected: '{shop} declined order #{no}.',
    order_ready: 'Order #{no} is ready for pickup at {shop}.',
    order_on_the_way: 'Order #{no} from {shop} is on the way.',
    order_delivery_failed: 'Order #{no} from {shop} could not be delivered.',
    order_delivery_retry: 'Redelivery of order #{no} from {shop} is scheduled.',
    order_completed: 'Order #{no} from {shop} was delivered. Thank you!',
    order_cancelled: '{shop} cancelled order #{no}.',
  }),
});

const num = (k, d) => { const v = Number(process.env[k]); return Number.isFinite(v) && v > 0 ? v : d; };

const MAX_ATTEMPTS = 4;                                            // ۳۰ث · ۱د · ۲د ⇒ ~۴ دقیقه
const BACKOFF_MS = num('CUSTOMER_NOTIFY_BACKOFF_MS', 30_000);
const STALE_MS = num('CUSTOMER_NOTIFY_STALE_MS', 3 * 60_000);
const MAX_AGE_MS = num('CUSTOMER_NOTIFY_MAX_AGE_MS', 6 * 60 * 60_000);   // دیرتر ⇒ «expired»
const SEND_TIMEOUT_MS = num('CUSTOMER_NOTIFY_SEND_TIMEOUT_MS', 15_000);
const TICK_MS = num('CUSTOMER_NOTIFY_TICK_MS', 5000);
const PER_HOUR = 8;
const PER_DAY = 30;
const CLAIM_LIMIT = 20;

const LIVE = ['pending', 'sending'];

// ════════════════════════════════════════════════════════════════════
//  پرچم
// ════════════════════════════════════════════════════════════════════

function decode(text) { try { return JSON.parse(text); } catch { return null; } }

/** همان قاعدهٔ `orders.flagOf` (مقدارِ دکان بر `all` جلو می‌افتد؛ فقط `true`)، با `run` ِ تراکنش. */
async function flagIn(run, shopId) {
  const rows = (await run(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [FLAG_KEY, 'all', String(shopId)]
  )).rows;
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}
const flagOf = (shopId) => flagIn((t, p) => query(t, p), shopId);

// ════════════════════════════════════════════════════════════════════
//  نوشتن — درونِ تراکنشِ سفارش
// ════════════════════════════════════════════════════════════════════

function langOf(v) {
  const s = typeof v === 'string' ? v.trim().toLowerCase() : '';
  return LANGS.includes(s) ? s : '';
}

/** HMAC ِ شماره (‎+930…‎ و ‎+93…‎ یکی، همان `order-rate.canon`) — فقط برای سقفِ هر مشتری. */
function phoneKey(phone) {
  const canon = require('./order-rate').canon(phone);
  const key = crypto.createHash('sha256')
    .update(`customer-notify-phone\n${serverSecret(['api', 'jwt'], 'customer-notify')}`).digest();
  return crypto.createHmac('sha256', key).update(canon).digest('hex');
}

/**
 * گذارِ سفارش ⇒ ردیفِ اعلانِ مشتری (اگر رویداد پیام دارد و پرچم روشن است).
 * `order` همان ردیفِ `online_orders` (پیش از گذار؛ `source`، `is_test`، `customer_phone`،
 * `customer_lang`). تکرارِ همان رویداد ⇒ ردیفِ دوم ساخته نمی‌شود.
 * @returns {Promise<string|null>} شناسهٔ ردیف
 */
async function onStatusIn(run, { shopId, order, to, at = now() }) {
  const event = EVENT_OF[to];
  if (!event || !order || order.source !== 'storefront' || order.is_test === true || !order.customer_phone) return null;
  if (!(await flagIn(run, shopId))) return null;
  const r = await run(
    `INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, lang, phone_key, status,
                                          next_attempt_at, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,'pending',$7,$7,$7)
     ON CONFLICT (order_id, event) DO NOTHING RETURNING id`,
    [ulid(at), shopId, order.id, event, langOf(order.customer_lang) || 'fa', phoneKey(order.customer_phone), at]
  );
  const id = r.rows[0] ? r.rows[0].id : null;
  //  تکرارِ رویداد (مثلاً «در راه» ِ دوم پس از تحویلِ دوباره) ⇒ هیچ: نه پیامِ دوم، نه کنار زدنِ پیامِ دیگر
  if (!id) return null;
  //  پیامِ کهنه‌ترِ همین سفارش که هنوز نرفته دیگر به کار نمی‌آید
  await run(
    `UPDATE customer_notify_outbox SET status='superseded', updated_at=$3
      WHERE order_id=$1 AND id <> $2 AND status='pending'`,
    [order.id, id, at]
  );
  soon();
  return id;
}

/**
 * سفارشِ تازهٔ مشتری: زبانِ او (اگر معتبر) روی سفارش، سپس رویدادِ «ثبت شد».
 * زبان همیشه ثبت می‌شود (دادهٔ سفارش است، نه پیام)؛ ردیفِ اعلان فقط با پرچم.
 */
async function onCreatedIn(run, { shopId, orderId, lang, at = now() }) {
  const l = langOf(lang);
  if (l) await run('UPDATE online_orders SET customer_lang=$3 WHERE shop_id=$1 AND id=$2', [shopId, orderId, l]);
  const order = (await run(
    'SELECT id, source, is_test, customer_phone, customer_lang FROM online_orders WHERE shop_id=$1 AND id=$2',
    [shopId, orderId]
  )).rows[0];
  return onStatusIn(run, { shopId, order, to: 'new', at });
}

// ════════════════════════════════════════════════════════════════════
//  سرویس‌دهنده‌ها — امروز هیچ (D-09)
// ════════════════════════════════════════════════════════════════════

/**
 * `{ [channel]: { send: async (msg) => result } }`. `msg`:
 *   { key, channel, to: { phone }, shopId, orderId, event, lang, title, text, link }
 * `result`:
 *   { status: 'accepted', ref? } ─ پذیرفته شد ⇒ پایان
 *   { status: 'rejected', code? } ─ قطعاً نرفت (نشانی ندارد، قالب رد شد، سقفِ هزینه) ⇒ کانالِ بعدی
 *   { status: 'retry', error? }   ─ گذرا یا مبهم ⇒ همان زنجیره بعداً با همان `key` (سرویس‌دهنده
 *                                   تکرار را با همین کلید می‌شناسد)؛ استثنا و مهلت هم همین
 * ⛔ در کارِ عادی هیچ‌وقت مقدار نمی‌گیرد: سرویس‌دهنده‌ها، رضایت و بودجه بیرونی‌اند و هنوز
 * تصویب نشده‌اند (D-09). فقط `test/*.test.js` جعلی می‌نشاند — همان الگوی `order-notify.setFallback`.
 */
let providers = {};
function setProviders(map) {
  const out = {};
  for (const ch of CHANNELS) {
    const p = map && map[ch];
    if (p && typeof p.send === 'function') out[ch] = p;
  }
  providers = out;
}

function channelState() {
  return Object.fromEntries(CHANNELS.map((ch) => [ch, providers[ch] ? 'configured' : 'not_configured']));
}

// ════════════════════════════════════════════════════════════════════
//  پیام
// ════════════════════════════════════════════════════════════════════

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);

/** نامِ ویترین برای متن: بی نویسهٔ کنترلی و جهتی، حداکثر ۶۰. */
function cleanName(s) {
  return String(s || '').replace(/[\u0000-\u001f\u007f‎‏‪-‮⁦-⁩]/g, ' ')
    .replace(/\s+/g, ' ').trim().slice(0, 60);
}

/** NTF-03: متنِ رویداد به زبانِ مشتری (زبانِ ناشناس ⇒ fa). */
function render(event, lang, { no, shop }) {
  const l = LANGS.includes(lang) ? lang : 'fa';
  const tpl = TEMPLATES[l][event];
  if (!tpl) throw new Error(`no template: ${event}`);
  const digits = l === 'en' ? String(no) : faNum(no);
  return tpl.replace('{no}', digits).replace('{shop}', cleanName(shop));
}

/** لینکِ صفحهٔ «سفارشِ من» به زبانِ مشتری — فقط اگر نشانیِ عمومیِ ویترین داده شده باشد. */
function linkFor({ shopId, orderId, slug, lang }) {
  const base = String(process.env.STORE_PUBLIC_URL || '').trim().replace(/\/+$/, '');
  if (!base || !/^https:\/\//.test(base) || !slug) return '';
  const token = require('./storefront').tokenOf(shopId, orderId);
  return `${base}/${encodeURIComponent(slug)}${lang && lang !== 'fa' ? `?lang=${lang}` : ''}#/o/${token}`;
}

// ════════════════════════════════════════════════════════════════════
//  کارگر
// ════════════════════════════════════════════════════════════════════

const backoffMs = (attempt) => BACKOFF_MS * Math.pow(2, Math.max(0, attempt - 1));

/** ردیف‌های سررسیده را می‌گیرد؛ `attempts` همین‌جا بالا می‌رود، پس هر «گرفتن» یک تلاشِ شمرده است. */
async function claimDue(at) {
  const due = `((status='pending' AND next_attempt_at <= $1) OR (status='sending' AND locked_at < $2))`;
  const r = await query(
    `UPDATE customer_notify_outbox
        SET status='sending', locked_at=$1, updated_at=$1, attempts = attempts + 1
      WHERE id IN (SELECT id FROM customer_notify_outbox WHERE ${due}
                    ORDER BY next_attempt_at ASC LIMIT ${CLAIM_LIMIT})
        AND ${due}
      RETURNING *`,
    [at, at - STALE_MS]
  );
  return r.rows;
}

/** نوشتنِ نتیجه — فقط اگر ردیف هنوز در دستِ همین تلاش است (CAS). */
async function settle(row, lock, set) {
  const keys = Object.keys(set);
  const cols = keys.map((k, i) => `${k}=$${i + 3}${k === 'tried' ? '::jsonb' : ''}`).join(', ');
  const r = await query(
    `UPDATE customer_notify_outbox SET ${cols}, locked_at=NULL
      WHERE id=$1 AND status='sending' AND locked_at=$2 RETURNING *`,
    [row.id, lock, ...keys.map((k) => (k === 'tried' ? JSON.stringify(set[k]) : set[k]))]
  );
  return r.rows[0] || null;
}

/** پیام‌های رفتهٔ همین مشتری در پنجره (همهٔ دکان‌ها). */
async function sentSince(phoneKeyValue, since) {
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM customer_notify_outbox WHERE phone_key=$1 AND status='sent' AND sent_at > $2`,
    [phoneKeyValue, since]
  );
  return r.n;
}

async function overCap(row, at) {
  if (await sentSince(row.phone_key, at - 60 * 60_000) >= PER_HOUR) return true;
  return await sentSince(row.phone_key, at - 24 * 60 * 60_000) >= PER_DAY;
}

/** یک بار صدا زدنِ سرویس‌دهنده، با مهلت؛ هرگز استثنا نمی‌دهد. */
async function callProvider(p, msg) {
  let timer;
  try {
    const out = await Promise.race([
      Promise.resolve().then(() => p.send(msg)),
      new Promise((resolve) => { timer = setTimeout(() => resolve({ status: 'retry', error: 'timeout' }), SEND_TIMEOUT_MS); }),
    ]);
    const status = out && ['accepted', 'rejected', 'retry'].includes(out.status) ? out.status : 'retry';
    return {
      status,
      ref: String((out && out.ref) || '').slice(0, 80),
      code: String((out && (out.code || out.error)) || '').slice(0, 80),
    };
  } catch (err) {
    return { status: 'retry', ref: '', code: String((err && err.message) || err).slice(0, 80) };
  } finally {
    clearTimeout(timer);
  }
}

async function processRow(row, at) {
  const lock = Number(row.locked_at);
  //  فقط وقتی کارگری وسطِ کار مرده و ردیف بیش از سقف دوباره گرفته شده
  if (Number(row.attempts) > MAX_ATTEMPTS) {
    return settle(row, lock, { status: 'failed', attempts: MAX_ATTEMPTS, last_error: 'stale', updated_at: at });
  }
  const order = await one(
    `SELECT o.id, o.order_no, o.status, o.source, o.is_test, o.customer_phone, s.slug, s.title
       FROM online_orders o LEFT JOIN storefronts s ON s.shop_id = o.shop_id
      WHERE o.id=$1 AND o.shop_id=$2`,
    [row.order_id, row.shop_id]
  );
  if (!order || order.source !== 'storefront' || !order.customer_phone) {
    return settle(row, lock, { status: 'cancelled', last_error: 'order_missing', updated_at: at });
  }
  //  کلیدِ قطعِ فوری: پرچم خاموش شد ⇒ هیچ پیامِ دیگری
  if (!(await flagOf(row.shop_id))) return settle(row, lock, { status: 'cancelled', last_error: 'flag_off', updated_at: at });
  if (at - Number(row.created_at) > MAX_AGE_MS) return settle(row, lock, { status: 'expired', updated_at: at });
  if (await overCap(row, at)) return settle(row, lock, { status: 'rate_limited', updated_at: at });

  const lang = LANGS.includes(row.lang) ? row.lang : 'fa';
  const shop = cleanName(order.title) || '—';
  const msg = {
    channel: '', key: '', to: { phone: order.customer_phone }, shopId: row.shop_id, orderId: row.order_id,
    event: row.event, lang, title: shop,
    text: render(row.event, lang, { no: Number(order.order_no), shop }),
    link: linkFor({ shopId: row.shop_id, orderId: row.order_id, slug: order.slug, lang }),
  };

  const tried = [];
  for (const channel of MATRIX[row.event] || []) {
    const p = providers[channel];
    if (!p) { tried.push({ channel, result: 'not_configured' }); continue; }
    const out = await callProvider(p, { ...msg, channel, key: `${row.id}:${channel}` });
    if (out.status === 'accepted') {
      tried.push({ channel, result: 'accepted' });
      return settle(row, lock, {
        status: 'sent', channel, sent_at: at, provider_ref: out.ref, tried, last_error: '', updated_at: at,
      });
    }
    if (out.status === 'rejected') { tried.push({ channel, result: 'rejected', code: out.code }); continue; }
    //  مبهم ⇒ کانالِ بعدی نه (شاید همین رفته باشد)؛ همان زنجیره بعداً، محدود
    tried.push({ channel, result: 'retry', code: out.code });
    const attempts = Number(row.attempts);
    if (attempts >= MAX_ATTEMPTS) return settle(row, lock, { status: 'failed', tried, last_error: out.code, updated_at: at });
    return settle(row, lock, {
      status: 'pending', next_attempt_at: at + backoffMs(attempts), tried, last_error: out.code, updated_at: at,
    });
  }
  return settle(row, lock, { status: 'no_channel', tried, updated_at: at });
}

let ticking = false;

/**
 * یک دورِ کارگر. `at` فقط برای آزمون (ساعتِ ساختگی).
 * @returns {Promise<number>} شمارِ ردیف‌های گرفته‌شده
 */
async function tick(at = now()) {
  if (ticking) return 0;
  ticking = true;
  try {
    const rows = await claimDue(at);
    for (const row of rows) {
      //  خطای دیتابیس ⇒ ردیف در `sending` می‌ماند و پس از STALE_MS دوباره گرفته می‌شود — با
      //  `attempts` ِ بالا رفته، پس باز هم محدود
      try { await processRow(row, at); } catch (err) { console.error('[customer-notify]', err.message); }
    }
    return rows.length;
  } finally {
    ticking = false;
  }
}

let timer = null;
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[customer-notify]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}
/** ردیفِ تازه ⇒ زودتر از تیکِ بعدی؛ اگر تراکنش هنوز commit نشده، تیکِ بعدی برش می‌دارد. */
function soon() {
  if (!timer) return;
  const t = setTimeout(() => tick().catch((err) => console.error('[customer-notify]', err.message)), 300);
  if (t.unref) t.unref();
}

// ════════════════════════════════════════════════════════════════════
//  خواندن — برای برنامهٔ مدیریتِ دکان
// ════════════════════════════════════════════════════════════════════

const n = (v) => (v == null ? null : Number(v));
const asJson = (x) => (Array.isArray(x) ? x : decode(x) || []);

/** بی شماره، بی متن، بی لینک: فقط چه رویدادی، از کدام کانال و چه شد. */
function shape(r) {
  return {
    id: r.id,
    event: r.event,
    lang: r.lang,
    status: r.status,
    channel: r.channel,
    attempts: Number(r.attempts),
    tried: asJson(r.tried).map((t) => ({ channel: String(t.channel || ''), result: String(t.result || '') })),
    nextAttemptAt: LIVE.includes(r.status) ? n(r.next_attempt_at) : null,
    sentAt: n(r.sent_at),
    createdAt: n(r.created_at),
    updatedAt: n(r.updated_at),
  };
}

/**
 * اعلان‌های مشتریِ یک سفارش (رویداد، کانال، نتیجه) و حالِ کانال‌ها — برای جزئیاتِ سفارش در
 * برنامهٔ دکان. سفارشِ دکانِ دیگر ⇒ null (مسیر ۴۰۴ می‌دهد).
 */
async function forOrder(shopId, orderId) {
  const id = String(orderId || '').slice(0, 64);
  const order = await one('SELECT id, source FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id]);
  if (!order) return null;
  const rows = await many(
    'SELECT * FROM customer_notify_outbox WHERE shop_id=$1 AND order_id=$2 ORDER BY created_at, id', [shopId, id]
  );
  return {
    enabled: await flagOf(shopId),
    eligible: order.source === 'storefront',
    channels: channelState(),
    items: rows.map(shape),
  };
}

module.exports = {
  FLAG_KEY, CHANNELS, LANGS, EVENT_OF, MATRIX, EVENTS, TEMPLATES, MAX_ATTEMPTS, PER_HOUR, PER_DAY, MAX_AGE_MS,
  flagOf, onStatusIn, onCreatedIn, setProviders, channelState, render, linkFor, phoneKey,
  tick, start, stop, forOrder, shape,
};
