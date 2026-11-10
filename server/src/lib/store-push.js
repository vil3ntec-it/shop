'use strict';
/**
 * پوشِ وب برای مشتریِ ویترین — کانالِ `push` ِ `lib/customer-notify.js` برای سفارشِ وب (§۳.۱۲ «مشتری: لینکِ پیگیری و
 * پوشِ PWA، بعد واتس‌اپ، تلگرام، پیامک»؛ CHK-20 پیامِ تأیید، NTF-01، ORD-16 «فرستادنِ دوباره»). مهاجرتِ ۴۷۰.
 *
 *   صفحهٔ پیگیری «خبرم کن» ─▶ POST /api/store/:slug/orders/:token/push  {subscription, page}
 *        └─ ردیفِ `store_push_subs` (همین سفارش، همین مرورگر) + آخرین پیامِ نرفته یک بار دیگر، فقط از پوش (`catchUp`)
 *   کارگرِ customer-notify ─▶ `provider.send(msg)` ─▶ رمزِ RFC 8291 (aes128gcm) + VAPID ─▶ سرویسِ پوشِ مرورگر
 *
 * ── آن‌چه این‌جا قفل است ─────────────────────────────────────────────
 *   • ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.storefront.web_push` **و** `shop.customer_notify` ِ همان دکان ⇒ درِ اشتراک ۴۰۴
 *     (همان «سفارش پیدا نشد»)، نمای پیگیری بی کلیدِ `push`، و کانالِ `push` همان `not_configured` ِ پیشین.
 *   • هیچ حسابِ بیرونی: جفتِ کلیدِ VAPID را خودِ سرور یک بار می‌سازد (`store_vapid_public`/`store_vapid_private` در
 *     app_config؛ کلیدِ خصوصی در پشتیبان مهر می‌شود، `backup-secrets.SECRET_KEYS`). جدا از کلیدِ پوشِ بخشِ پمپ.
 *   • اجازه همان توکنِ پیگیریِ همان سفارش (دکان از `slug`، سفارش از توکن، هرگز از بدنه)؛ لینکِ باطل‌شده (TEN-03، نسلِ
 *     `track_gen`) ⇒ اشتراکِ آن لینک دیگر پیامی نمی‌گیرد و پاک می‌شود. سفارشِ پایان‌یافته اشتراکِ تازه نمی‌گیرد.
 *   • ⛔ SSRF: سرور فقط به نشانیِ سرویس‌های پوشِ مرورگرها POST می‌کند (`PUSH_HOSTS`، https، درگاهِ ۴۴۳، بی user:pass)،
 *     هرگز redirect را دنبال نمی‌کند، با مهلتِ ۸ ثانیه (مرورگرها هم‌زمان). کلیدهای مرورگر سنجیده می‌شوند (نقطهٔ واقعیِ P-256، auth ۱۶ بایت).
 *   • حداکثر `MAX_SUBS` مرورگر برای هر سفارش؛ اشتراکِ مرده (۴۰۴/۴۱۰) پاک؛ اشتراکِ کهنه‌تر از `KEEP_MS` پاک.
 *   • بی دادهٔ حساس: متنِ پیام همان قالبِ NTF-03 (نامِ ویترین، شمارهٔ سفارش، وضعیت)، رمزشده تا خودِ مرورگر؛ شماره هرگز به
 *     سرویسِ پوش نمی‌رسد. نشانیِ لمسِ اعلان فقط مسیرِ همین مبدأ (`/#/o/…` یا `/store/<slug>#/o/…`).
 *   • کارِ مشتری هرگز کانالِ پولی را برنمی‌انگیزد: `catchUp` فقط `only_channel = 'push'`.
 *   • ⛔ آزمون هرگز شبکه نمی‌زند: `useTransport` (فقط آزمون)؛ در `NODE_ENV=test` بی آن هیچ درخواستی بیرون نمی‌رود.
 *   • «خبرم کن» ِ کالا (WEB-10، `store-alert-push.js`، مهاجرتِ ۴۷۵) همین رمز، VAPID، SSRF و ۴۰۴/۴۱۰ را از `parse`/`fanOut`
 *     می‌گیرد — جدولِ اشتراکِ خودش، نه کپیِ این‌ها.
 */
const crypto = require('crypto');
const db = require('../db');
const { query, one, many, now } = db;
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG = 'shop.storefront.web_push';
const PUB_KEY = 'store_vapid_public';
const PRIV_KEY = 'store_vapid_private';
const MAX_SUBS = 3;
const KEEP_MS = 30 * 24 * 60 * 60_000;
const TTL_S = 6 * 60 * 60;                 //  همان `MAX_AGE_MS` ِ customer-notify: دیرتر به کار نمی‌آید
const SEND_TIMEOUT_MS = 8_000;           //  زیرِ مهلتِ ۱۵ ثانیه‌ایِ `customer-notify.callProvider`

/** سرویس‌های پوشِ مرورگرها (کروم/اج/اپرا/سامسونگ: FCM؛ فایرفاکس؛ سافاری؛ اج روی ویندوز: WNS). */
const PUSH_HOSTS = Object.freeze(['fcm.googleapis.com', 'updates.push.services.mozilla.com', 'web.push.apple.com']);
const PUSH_SUFFIXES = Object.freeze(['.notify.windows.com', '.push.apple.com']);

let webpush = null;
try { webpush = require('web-push'); } catch { webpush = null; }

// ════════════════════════════════════════════════════════════════════
//  پرچم
// ════════════════════════════════════════════════════════════════════

const ownFlagOn = (shopId) => require('./orders').flagOf(shopId, FLAG);
/** کانال فقط وقتی کار دارد که اعلانِ مشتری هم روشن است (بی آن هیچ ردیفی در صف نیست). */
async function flagOn(shopId) {
  return (await ownFlagOn(shopId)) && (await require('./customer-notify').flagOf(shopId));
}

// ════════════════════════════════════════════════════════════════════
//  کلیدِ VAPID — یک بار، روی خودِ سرور
// ════════════════════════════════════════════════════════════════════

const b64 = (buf) => Buffer.from(buf).toString('base64url');
const unb64 = (s) => (typeof s === 'string' && /^[A-Za-z0-9_-]+$/.test(s) ? Buffer.from(s, 'base64url') : null);

/** جفتِ درست: خصوصیِ ۳۲ بایتی که عمومیِ ذخیره‌شده از آن درمی‌آید (پس از بازیابیِ نیمه‌کاره هم ناجور نمی‌ماند). */
function pairOk(pub, priv) {
  const p = unb64(pub); const k = unb64(priv);
  if (!p || !k || p.length !== 65 || k.length !== 32) return false;
  try {
    const ecdh = crypto.createECDH('prime256v1');
    ecdh.setPrivateKey(k);
    return ecdh.getPublicKey().equals(p);
  } catch { return false; }
}

let keysCache = null;
async function vapid() {
  if (keysCache && Date.now() - keysCache.at < 60_000) return keysCache;
  const read = async () => {
    const rows = await many('SELECT key, value FROM app_config WHERE key = ANY($1::text[])', [[PUB_KEY, PRIV_KEY]]);
    const m = Object.fromEntries(rows.map((r) => [r.key, r.value]));
    return { pub: m[PUB_KEY] || '', priv: m[PRIV_KEY] || '', any: rows.length > 0 };
  };
  let k = await read();
  if (!pairOk(k.pub, k.priv)) {
    if (!webpush) throw new Error('web-push نصب نیست');
    const g = webpush.generateVAPIDKeys();
    const at = now();
    //  هیچ ردیف ⇒ «اگر نبود» (چند نمونهٔ هم‌زمان یک جفت می‌گیرند)؛ جفتِ ناجور/نیمه (بازیابی) ⇒ جایگزین
    await query(
      `INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,$5), ($3,$4,$5)
       ON CONFLICT (key) DO ${k.any ? 'UPDATE SET value = excluded.value, updated_at = excluded.updated_at' : 'NOTHING'}`,
      [PUB_KEY, g.publicKey, PRIV_KEY, g.privateKey, at]
    );
    k = await read();
    if (!pairOk(k.pub, k.priv)) throw new Error('کلیدِ پوشِ ویترین ساخته نشد');
  }
  keysCache = { publicKey: k.pub, privateKey: k.priv, subject: process.env.VAPID_SUBJECT || 'mailto:support@vill3n.top', at: Date.now() };
  return keysCache;
}
const publicKey = async () => (await vapid()).publicKey;

// ════════════════════════════════════════════════════════════════════
//  سنجشِ اشتراک
// ════════════════════════════════════════════════════════════════════

/** فقط نشانیِ https ِ سرویسِ پوشِ مرورگر — هیچ نشانیِ دیگری (SSRF). */
function endpointOk(s) {
  if (typeof s !== 'string' || s.length < 12 || s.length > 1024 || /[\s\u0000-\u001f]/.test(s)) return false;
  let u;
  try { u = new URL(s); } catch { return false; }
  if (u.protocol !== 'https:' || u.username || u.password || (u.port && u.port !== '443')) return false;
  const h = u.hostname.toLowerCase();
  return PUSH_HOSTS.includes(h) || PUSH_SUFFIXES.some((x) => h.endsWith(x) && h.length > x.length);
}

/** کلیدِ مرورگر: نقطهٔ واقعیِ P-256 (۶۵ بایتِ فشرده‌نشده). */
function p256dhOk(s) {
  const p = unb64(s);
  if (!p || p.length !== 65 || p[0] !== 4 || s.length > 100) return false;
  try {
    crypto.createPublicKey({ key: { kty: 'EC', crv: 'P-256', x: b64(p.subarray(1, 33)), y: b64(p.subarray(33)) }, format: 'jwk' });
    return true;
  } catch { return false; }
}
const authOk = (s) => { const a = unb64(s); return Boolean(a) && a.length === 16 && s.length <= 32; };

/** `{subscription: {endpoint, keys: {p256dh, auth}}, page}` ⇒ ردیف — نادرست ⇒ ۴۰۰ `bad_subscription`. */
function parse(body, store) {
  const s = body && typeof body === 'object' ? body.subscription : null;
  const keys = s && typeof s === 'object' && s.keys && typeof s.keys === 'object' ? s.keys : {};
  if (!s || !endpointOk(s.endpoint) || !p256dhOk(keys.p256dh) || !authOk(keys.auth)) {
    throw badRequest('اشتراکِ اعلانِ این مرورگر معتبر نیست', 'bad_subscription');
  }
  //  صفحهٔ ویترینِ مشتری: نشانیِ خودِ دکان (`{slug}.vill3n.top/`) یا مسیرِ `/store/<slug>` — هیچ چیزِ دیگر
  const own = `/store/${store.slug}`;
  const page = body.page === '/' ? '/' : own;
  if (body.page !== undefined && body.page !== '/' && body.page !== own) throw badRequest('صفحهٔ ویترین معتبر نیست', 'bad_page');
  return { endpoint: s.endpoint, p256dh: keys.p256dh, auth: keys.auth, page };
}

// ════════════════════════════════════════════════════════════════════
//  اشتراک — از صفحهٔ پیگیری
// ════════════════════════════════════════════════════════════════════

const nf = () => notFound('سفارش پیدا نشد', 'order_not_found');

/**
 * سفارشی که دیگر هیچ پیامی برای مشتری ندارد: از این وضعیت به هیچ وضعیتِ پیام‌دار (`EVENT_OF` ِ customer-notify) نمی‌رسد
 * (پایان‌یافته، تحویل‌شده/برداشته — برگشت و بازپرداخت پیامِ پوش ندارند). از جدولِ گذارها حساب می‌شود، نه فهرستِ دستی.
 */
function quiet(status) {
  const { ORDER_TRANSITIONS } = require('./order-states');
  const { EVENT_OF } = require('./customer-notify');
  const seen = new Set();
  const q = [...(ORDER_TRANSITIONS[status] || [])];
  while (q.length) {
    const st = q.shift();
    if (seen.has(st)) continue;
    seen.add(st);
    if (EVENT_OF[st]) return false;
    q.push(...(ORDER_TRANSITIONS[st] || []));
  }
  return true;
}

async function orderOf(store, token) {
  if (!(await flagOn(store.shop_id))) throw nf();
  return require('./storefront').orderIdOfLink(store, token);   //  نادرست ۴۰۴، منقضی/باطل ۴۱۰
}

/** اشتراکِ این مرورگر برای این سفارش ⇒ `{ push: { on: true } }`. همان مرورگر دوباره ⇒ همان ردیف (کلیدِ تازه). */
async function attach(store, token, body, at = now()) {
  const orderId = await orderOf(store, token);
  const sub = parse(body, store);
  const fresh = await db.tx(async (c) => {
    //  قفلِ ردیفِ سفارش: دو «خبرم کن» ِ هم‌زمان از سقف نمی‌گذرند
    const o = (await c.query(
      'SELECT id, source, status, track_gen FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [store.shop_id, orderId]
    )).rows[0];
    if (!o || o.source !== 'storefront') throw nf();
    if (quiet(o.status)) throw conflict('این سفارش تمام شده است؛ خبرِ تازه‌ای ندارد', 'order_closed');
    const same = (await c.query('SELECT id FROM store_push_subs WHERE order_id=$1 AND endpoint=$2', [orderId, sub.endpoint])).rows[0];
    if (same) {
      await c.query('UPDATE store_push_subs SET p256dh=$2, auth=$3, page=$4, link_gen=$5 WHERE id=$1',
        [same.id, sub.p256dh, sub.auth, sub.page, Number(o.track_gen) || 0]);
      return false;
    }
    const n = Number((await c.query('SELECT COUNT(*)::int AS n FROM store_push_subs WHERE order_id=$1', [orderId])).rows[0].n);
    if (n >= MAX_SUBS) throw conflict(`خبرِ این سفارش حداکثر به ${MAX_SUBS} دستگاه می‌رود`, 'push_limit');
    await c.query(
      `INSERT INTO store_push_subs (id, shop_id, order_id, endpoint, p256dh, auth, page, link_gen, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
      [ulid(at), store.shop_id, orderId, sub.endpoint, sub.p256dh, sub.auth, sub.page, Number(o.track_gen) || 0, at]
    );
    return true;
  });
  //  مرورگرِ تازه: آخرینِ پیامِ نرفتهٔ سفارش (مثلاً «ثبت شد» که پیش از «خبرم کن» صف شده بود) یک بار، فقط از پوش
  if (fresh) await require('./customer-notify').catchUp(store.shop_id, orderId, 'push', at);
  await query('DELETE FROM store_push_subs WHERE created_at < $1', [at - KEEP_MS]);   //  پاک‌سازیِ کهنه، با نمایه
  return { push: { on: true } };
}

/** «خاموش» ⇒ اشتراکِ همین مرورگر برای همین سفارش پاک. نبود ⇒ همان پاسخ (بی نشت). */
async function detach(store, token, body) {
  const orderId = await orderOf(store, token);
  const endpoint = body && typeof body === 'object' ? body.endpoint : '';
  if (!endpointOk(endpoint)) throw badRequest('اشتراکِ اعلانِ این مرورگر معتبر نیست', 'bad_subscription');
  await query('DELETE FROM store_push_subs WHERE shop_id=$1 AND order_id=$2 AND endpoint=$3', [store.shop_id, orderId, endpoint]);
  return { push: { on: false } };
}

/** کلیدِ `push` ِ نمای پیگیری: فقط با پرچم‌های روشن و سفارشِ هنوز باز — وگرنه undefined (کلید نیست). */
async function viewOf(shopId, status) {
  if (quiet(status) || !(await flagOn(shopId))) return undefined;
  try { return { key: await publicKey() }; } catch (err) {
    console.error('[store-push] vapid', err.message);
    return undefined;
  }
}

// ════════════════════════════════════════════════════════════════════
//  فرستادن — سرویس‌دهندهٔ کانالِ `push` ِ customer-notify
// ════════════════════════════════════════════════════════════════════

async function netTransport({ endpoint, method, headers, body }) {
  if (process.env.NODE_ENV === 'test') return { status: 0, error: 'network_in_test' };
  const r = await fetch(endpoint, { method, headers, body, redirect: 'manual', signal: AbortSignal.timeout(SEND_TIMEOUT_MS) });
  try { if (r.body) await r.body.cancel(); } catch { /* پاسخ مهم نیست */ }
  return { status: r.status };
}
let transport = netTransport;
/** ⛔ فقط آزمون: `fn({endpoint, method, headers, body}) ⇒ {status}`؛ null ⇒ شبکه. */
function useTransport(fn) { transport = typeof fn === 'function' ? fn : netTransport; }

const topicOf = (key) => crypto.createHash('sha256').update(String(key)).digest('base64url').slice(0, 32);
const tagOf = (orderId) => `o-${crypto.createHash('sha256').update(`store-push\n${orderId}`).digest('base64url').slice(0, 12)}`;

/** یک مرورگر ⇒ 'ok' | 'gone' (اشتراک مرده) | 'rejected' | 'retry' */
async function deliver(sub, payload, keys, { topic, urgency }) {
  if (!endpointOk(sub.endpoint)) return 'gone';
  let d;
  try {
    d = webpush.generateRequestDetails({ endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } }, payload, {
      vapidDetails: { subject: keys.subject, publicKey: keys.publicKey, privateKey: keys.privateKey },
      TTL: TTL_S, urgency, topic, contentEncoding: 'aes128gcm',
    });
  } catch { return 'gone'; }   //  کلیدِ مرورگرِ خراب (سنجش پیش‌تر رد می‌کرد) — دیگر به کار نمی‌آید
  const headers = {};
  for (const [k, v] of Object.entries(d.headers)) if (k.toLowerCase() !== 'content-length') headers[k] = String(v);
  let st;
  try { st = Number((await transport({ endpoint: d.endpoint, method: 'POST', headers, body: d.body })).status) || 0; } catch { st = 0; }
  if (st >= 200 && st < 300) return 'ok';
  if (st === 404 || st === 410) return 'gone';
  if (st === 0 || st === 429 || st >= 500) return 'retry';
  return 'rejected';
}

/** جدول‌های اشتراکِ مرورگر (فقط همین دو؛ نامِ جدول هرگز از ورودی نمی‌آید) */
const SUB_TABLES = Object.freeze(['store_push_subs', 'store_alert_push']);

/**
 * یک پیام به چند مرورگرِ یک جدول — مشترکِ پیگیریِ سفارش و «خبرم کن» ِ کالا (`store-alert-push.js`): رمز، VAPID و SSRF ِ
 * `deliver`؛ ۴۰۴/۴۱۰ ⇒ ردیف پاک؛ ۲xx ⇒ `last_ok_at`. هم‌زمان: سه مرورگر × مهلتِ هر درخواست هرگز از مهلتِ کلِ
 * `customer-notify.callProvider` (۱۵ ثانیه) نمی‌گذرد. ⇒ همان قراردادِ `setProviders` (`accepted`/`retry`/`rejected`).
 */
async function fanOut(table, subs, payloadOf, { topic, urgency = 'normal' }) {
  if (!SUB_TABLES.includes(table)) throw new Error(`جدولِ اشتراکِ ناشناخته: ${table}`);
  if (!subs.length || !webpush) return { status: 'rejected', code: 'no_subscription' };
  const keys = await vapid();
  const results = await Promise.all(subs.map((s) => deliver(s, JSON.stringify(payloadOf(s)), keys, { topic, urgency })));
  const out = { ok: [], gone: [], rejected: 0, retry: 0 };
  subs.forEach((s, i) => {
    const r = results[i];
    if (r === 'ok') out.ok.push(s.id); else if (r === 'gone') out.gone.push(s.id); else out[r]++;
  });
  if (out.gone.length) await query(`DELETE FROM ${table} WHERE id = ANY($1::text[])`, [out.gone]);
  if (out.ok.length) {
    await query(`UPDATE ${table} SET last_ok_at=$2 WHERE id = ANY($1::text[])`, [out.ok, now()]);
    return { status: 'accepted', ref: `web_push:${out.ok.length}` };
  }
  if (out.retry) return { status: 'retry', code: 'push_unavailable' };
  return { status: 'rejected', code: out.gone.length ? 'push_gone' : 'push_rejected' };
}

/**
 * `msg` همان پیامِ customer-notify: `{ key, shopId, orderId, event, lang, title, text }`. شماره (`to.phone`) هرگز
 * خوانده یا فرستاده نمی‌شود. نتیجه همان قراردادِ `setProviders`.
 */
async function send(msg) {
  const o = await one('SELECT status, track_gen FROM online_orders WHERE shop_id=$1 AND id=$2', [msg.shopId, msg.orderId]);
  if (!o) return { status: 'rejected', code: 'no_subscription' };
  const subs = await many('SELECT * FROM store_push_subs WHERE shop_id=$1 AND order_id=$2 ORDER BY created_at, id', [msg.shopId, msg.orderId]);
  //  TEN-03: اشتراکِ لینکِ باطل‌شده (یا پاک‌شده با SEC-10) هرگز پیام نمی‌گیرد
  const stale = subs.filter((s) => Number(s.link_gen) !== Number(o.track_gen));
  if (stale.length) await query('DELETE FROM store_push_subs WHERE id = ANY($1::text[])', [stale.map((s) => s.id)]);
  const live = subs.filter((s) => Number(s.link_gen) === Number(o.track_gen));
  if (!live.length || !webpush) return { status: 'rejected', code: 'no_subscription' };

  const token = await require('./storefront').currentTokenOf(msg.shopId, msg.orderId);
  const notify = require('./customer-notify');
  const urgent = (notify.MATRIX[msg.event] || []).includes('sms');
  const out = await fanOut('store_push_subs', live, (s) => ({
    v: 1, title: String(msg.title || '').slice(0, 80), body: String(msg.text || '').slice(0, 300), lang: msg.lang,
    url: `${s.page === '/' ? '/' : s.page}#/o/${encodeURIComponent(token)}`, tag: tagOf(msg.orderId),
  }), { topic: topicOf(msg.key), urgency: urgent ? 'high' : 'normal' });
  //  سفارشی که دیگر خبری ندارد ⇒ اشتراکش هم نمی‌ماند (کمینهٔ داده)
  if (out.status === 'accepted' && quiet(o.status)) {
    await query('DELETE FROM store_push_subs WHERE shop_id=$1 AND order_id=$2', [msg.shopId, msg.orderId]);
  }
  return out;
}

const provider = Object.freeze({ send });

module.exports = {
  FLAG, PUB_KEY, PRIV_KEY, MAX_SUBS, KEEP_MS, PUSH_HOSTS,
  flagOn, ownFlagOn, vapid, publicKey, endpointOk, p256dhOk, authOk, parse, quiet, attach, detach, viewOf, provider, useTransport,
  fanOut, topicOf,
};
