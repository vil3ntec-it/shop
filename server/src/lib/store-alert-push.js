'use strict';
/**
 * «خبرم کن وقتی موجود/ارزان شد» (WEB-10، `storefront-alerts.js`) روی پوشِ وب ِ همین مرورگر — کانالِ `push` ِ خبرِ
 * هشدار، بی هیچ حسابِ بیرونی. مهاجرتِ ۴۷۵ (`store_alert_push`). رمز، VAPID، فهرستِ سفیدِ SSRF، ۴۰۴/۴۱۰ ⇒ پاک و قاعدهٔ
 * «دوباره» همه از `store-push.js` (`parse`، `endpointOk`، `fanOut`) — این‌جا فقط مدلِ اشتراک و متنِ پیام است.
 *
 *   برگهٔ کالا «روی همین دستگاه هم خبرم کن» (لمسِ مشتری) ─▶ POST /api/store/:slug/alerts/push {subscription, page}
 *        └─ ردیفِ (دکان، شمارهٔ تأییدشده، مرورگر) + اثباتِ همان تأیید (بلیت با نسل، یا نشستِ حساب) تا انقضای همان
 *   کارگرِ storefront-alerts (گذار) ─▶ `builtin('push')` ─▶ `send(msg)` ─▶ اثبات دوباره سنجیده ─▶ مرورگرهای همان شماره
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • ⛔ پیش‌فرض خاموش: بی `shop.storefront.alerts` **و** `shop.storefront.web_push` **و** `shop.customer_notify` ⇒ درِ
 *     اشتراک همان پاسخِ مسیرِ ناشناخته، «هشدارهای من» بی کلیدِ `push`، و کانال همان `not_configured`.
 *   • هویت فقط شمارهٔ تأییدشده (`storefront.verification`، همان تصمیمِ ثبتِ سفارش)؛ دکان فقط از `slug`. هر شماره فقط
 *     ردیف‌های خودش در همان دکان را می‌بیند، می‌سازد و پاک می‌کند (یکتایی (دکان، شماره، endpoint)).
 *   • ⛔ اثباتِ باطل یا منقضی ⇒ هیچ پیام: پیش از هر فرستادن — بلیت: نامنقضی و همان نسل («خروج از همه‌جا» و پاک کردنِ
 *     حساب نسل را بالا می‌برند)؛ نشست: لغونشده، نامنقضی، حسابِ فعال و **همان شماره** (عوض کردنِ شماره). نشد ⇒ ردیف پاک.
 *     ردیف حداکثر تا انقضای اثبات (و ۹۰ روز، عمرِ هشدار) می‌ماند؛ دیدنِ دوبارهٔ برگه با تأییدِ تازه آن را تازه می‌کند.
 *   • سقف: حداکثر `MAX_BROWSERS` مرورگر برای هر (دکان، شماره) زیرِ قفلِ همان (هم‌زمان هم)؛ سقفِ هر دکان در ۲۴ ساعت در
 *     `storefront-alerts.deliver` (`shopPerDay`)؛ سقفِ IP و هر شماره در درِ مسیر.
 *   • ⛔ کارِ مشتری هیچ پیامی برنمی‌انگیزد (نه پوش، نه پیامک): اشتراک فقط ردیف می‌سازد؛ پیام فقط از گذارِ کارگر، و زنجیرهٔ
 *     «خبرم کن» اصلاً پیامک ندارد (`storefront-alerts.CHANNELS`).
 *   • بی دادهٔ حساس: پیام فقط نامِ کالا و قیمت (+ نوعِ خبر)، رمزشده تا خودِ مرورگر؛ نه شماره، نه نامِ مشتری. لمس فقط
 *     برگهٔ همان کالا در همان مبدأ (`/#/p/…` یا `/store/<slug>#/p/…`؛ `sw.js` هر نشانیِ دیگری را دور می‌ریزد).
 */
const crypto = require('crypto');
const db = require('../db');
const { query, many, now } = db;
const { ulid } = require('./ulid');
const { badRequest, conflict } = require('../middleware/errors');
const push = require('./store-push');

const TABLE = 'store_alert_push';
const MAX_BROWSERS = 3;
const LANGS = Object.freeze(['fa', 'ps', 'en']);
const HEAD = Object.freeze({
  stock: { fa: 'دوباره موجود است', ps: 'بیا موجود دی', en: 'Back in stock' },
  price: { fa: 'قیمت پایین آمد', ps: 'بیه راټیټه شوه', en: 'Price dropped' },
});
const CURRENCY = Object.freeze({ fa: 'افغانی', ps: 'افغانۍ', en: 'AFN' });
const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';

const alertsTtl = () => require('./storefront-alerts').TTL_MS;

/** هر سه پرچم: «خبرم کن»، پوشِ وب و اعلانِ مشتری */
async function flagOn(shopId) {
  return (await require('./storefront-alerts').flagOn(shopId)) && (await push.flagOn(shopId));
}

/** کلیدِ `push` ِ «هشدارهای من» (فقط با پرچمِ پوش و اعلانِ روشن؛ «خبرم کن» را درِ مسیر سنجیده) — وگرنه undefined */
async function keyOf(shopId) {
  if (!(await push.flagOn(shopId))) return undefined;
  try { return { key: await push.publicKey() }; } catch (err) {
    console.error('[store-alert-push] vapid', err.message);
    return undefined;
  }
}

// ════════════════════════════════════════════════════════════════════
//  اشتراک — از برگهٔ کالا
// ════════════════════════════════════════════════════════════════════

/**
 * این مرورگر برای «خبرم کن» ِ همین شماره در همین دکان ⇒ `{ push: { on: true } }`. `proof` همان `storefront.verification`.
 * همان مرورگر دوباره ⇒ همان ردیف با کلید، صفحه و اثباتِ تازه (تمدید تا انقضای اثباتِ تازه).
 */
async function attach(store, proof, body, at = now()) {
  const sub = push.parse(body, store);   //  SSRF، کلیدِ P-256، auth، صفحهٔ همین ویترین ⇒ وگرنه ۴۰۰
  const exp = Math.min(Number(proof.expiresAt), at + alertsTtl());
  const vals = [sub.p256dh, sub.auth, sub.page, proof.proof, proof.proof === 'ticket' ? Number(proof.gen) || 0 : 0,
    proof.proof === 'session' ? proof.sessionId : null, exp, at];
  await db.tx(async (c) => {
    //  سقفِ مرورگرهای هر (دکان، شماره) زیرِ قفلِ همان — دو اشتراکِ هم‌زمان از سقف نمی‌گذرند
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`store-alert-push:${store.shop_id}:${proof.phone}`]);
    await c.query(`DELETE FROM ${TABLE} WHERE shop_id=$1 AND phone=$2 AND expires_at <= $3`, [store.shop_id, proof.phone, at]);
    const same = (await c.query(`SELECT id FROM ${TABLE} WHERE shop_id=$1 AND phone=$2 AND endpoint=$3`,
      [store.shop_id, proof.phone, sub.endpoint])).rows[0];
    if (same) {
      await c.query(`UPDATE ${TABLE} SET p256dh=$2, auth=$3, page=$4, proof=$5, ticket_gen=$6, session_id=$7, expires_at=$8, updated_at=$9
        WHERE id=$1`, [same.id, ...vals]);
      return;
    }
    const n = Number((await c.query(`SELECT COUNT(*)::int AS n FROM ${TABLE} WHERE shop_id=$1 AND phone=$2`,
      [store.shop_id, proof.phone])).rows[0].n);
    if (n >= MAX_BROWSERS) throw conflict(`«خبرم کن» ِ این دکان حداکثر به ${MAX_BROWSERS} دستگاه می‌رود`, 'alert_push_limit');
    await c.query(
      `INSERT INTO ${TABLE} (id, shop_id, phone, endpoint, p256dh, auth, page, proof, ticket_gen, session_id, expires_at, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$12)`,
      [ulid(at), store.shop_id, proof.phone, sub.endpoint, ...vals]
    );
  });
  await purge(at);
  return { push: { on: true } };
}

/** پاک‌سازیِ اشتراک‌های منقضی (با نمایه) — پس از هر اشتراک و در هر دورِ کارگرِ «خبرم کن» (کمینهٔ داده) */
const purge = (at = now()) => query(`DELETE FROM ${TABLE} WHERE expires_at <= $1`, [at]);

/** «خاموش» ⇒ اشتراکِ همین مرورگر برای همین شماره در همین دکان پاک. نبود ⇒ همان پاسخ (بی نشت). */
async function detach(store, phone, body) {
  const endpoint = body && typeof body === 'object' ? body.endpoint : '';
  if (!push.endpointOk(endpoint)) throw badRequest('اشتراکِ اعلانِ این مرورگر معتبر نیست', 'bad_subscription');
  await query(`DELETE FROM ${TABLE} WHERE shop_id=$1 AND phone=$2 AND endpoint=$3`, [store.shop_id, phone, endpoint]);
  return { push: { on: false } };
}

// ════════════════════════════════════════════════════════════════════
//  فرستادن — سرویس‌دهندهٔ کانالِ `push` ِ خبرِ «خبرم کن»
// ════════════════════════════════════════════════════════════════════

/** اثباتِ ردیف هنوز زنده است؟ (همان دو راهِ `storefront.verification`، این بار برای کاری که بعداً به نامِ همین شماره می‌شود) */
async function proofLive(r, at) {
  if (!(Number(r.expires_at) > at)) return false;
  const customers = require('./store-customers');
  if (r.proof === 'ticket') return (await customers.ticketGen(r.phone)) === Number(r.ticket_gen);
  const s = (await query(
    `SELECT s.revoked_at, s.expires_at, c.phone, c.status FROM store_customer_sessions s
       JOIN store_customers c ON c.id = s.customer_id WHERE s.id = $1`, [r.session_id]
  )).rows[0];
  return Boolean(s) && s.revoked_at === null && Number(s.expires_at) > at && s.status === 'active' && s.phone === r.phone;
}

/** قیمت به زبانِ پیام: fa/ps رقمِ فارسی با «٬» و «٫»؛ en رقمِ لاتین (همان `K.money` ِ ویترین) */
function money(minor, lang) {
  const s = (Math.round(Number(minor)) / 100).toLocaleString('en-US', { maximumFractionDigits: 2 });
  const n = lang === 'en' ? s : s.replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]).replace(/,/g, '٬').replace(/\./g, '٫');
  return `${n} ${CURRENCY[lang]}`;
}

const tagOf = (shopId, productId, kind) =>
  `a-${crypto.createHash('sha256').update(`store-alert-push\n${shopId}\n${productId}\n${kind}`).digest('base64url').slice(0, 12)}`;

/** متنِ پیام: فقط نام و قیمتِ کالا (+ نوعِ خبر)؛ لمس فقط برگهٔ همان کالا در همان صفحهٔ ویترین */
function payload(msg, page) {
  const lang = LANGS.includes(msg.lang) ? msg.lang : 'fa';
  const kind = HEAD[msg.kind] ? msg.kind : 'stock';
  const p = msg.priceMinor;
  const price = p !== null && p !== undefined && Number.isSafeInteger(Number(p)) && Number(p) >= 0 ? money(p, lang) : '';
  return {
    v: 1, title: String(msg.productName || '').slice(0, 80) || '…', body: price ? `${HEAD[kind][lang]} — ${price}` : HEAD[kind][lang],
    lang, url: `${page === '/' ? '/' : page}#/p/${encodeURIComponent(msg.productId)}`, tag: tagOf(msg.shopId, msg.productId, kind),
  };
}

/**
 * `msg` همان پیامِ `storefront-alerts.deliver`: `{ key, shopId, to: {phone}, productId, kind, productName, priceMinor, lang, at }`.
 * شماره فقط برای پیدا کردنِ ردیف‌ها خوانده می‌شود و هرگز به سرویسِ پوش نمی‌رسد. نتیجه همان قراردادِ `setProviders`.
 */
async function send(msg) {
  const phone = msg && msg.to && msg.to.phone;
  if (!phone || !msg.shopId || !msg.productId) return { status: 'rejected', code: 'no_subscription' };
  const at = Number(msg.at) || now();
  const subs = await many(`SELECT * FROM ${TABLE} WHERE shop_id=$1 AND phone=$2 ORDER BY created_at, id`, [msg.shopId, phone]);
  const live = [];
  const dead = [];
  for (const s of subs) (await proofLive(s, at) ? live : dead).push(s.id);
  //  ⛔ تأییدِ باطل یا منقضی ⇒ اشتراکِ همان هرگز پیام نمی‌گیرد و می‌رود
  if (dead.length) await query(`DELETE FROM ${TABLE} WHERE id = ANY($1::text[])`, [dead]);
  return push.fanOut(TABLE, subs.filter((s) => live.includes(s.id)), (s) => payload(msg, s.page), { topic: push.topicOf(msg.key) });
}

const provider = Object.freeze({ send });

/** کانالِ بی حسابِ بیرونیِ «خبرم کن»: `push` با پرچمِ پوشِ دکان (اعلانِ مشتری را `deliver` پیش‌تر سنجیده) — وگرنه null */
async function builtin(channel, shopId) {
  if (channel !== 'push') return null;
  return (await push.ownFlagOn(shopId)) ? provider : null;
}

module.exports = { TABLE, MAX_BROWSERS, flagOn, keyOf, attach, detach, purge, proofLive, money, payload, provider, builtin };
