'use strict';
/**
 * NOTIF-11 — کانالِ پشتیبانِ اعلانِ سفارش (§۳۰.۴، §۳۰.۵): وقتی پوش به هیچ گوشیِ دکان نرسید یا کسی سفارش را در برنامه
 * باز نکرد (`order-notify.escalate`)، **یک** ایمیل از سرویسِ ایمیلِ خودِ سرور (`lib/mailer`) به ایمیلِ تأییدشدهٔ صاحبِ دکان.
 * «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): ورود فقط با ایمیلِ تأییدشده است، پس `users.email` ِ صاحب همان راهِ تماسِ سنجیدهٔ اوست.
 *
 *   پرچم ──── `shop.order_notify_email` (دکان یا `all`، پیش‌فرض خاموش؛ با `shop.order_notify`). خاموش ⇒ [escalate] و [status]
 *             null ⇒ `order-notify` بایت‌به‌بایت همان پیشین (`not_configured`، هیچ نامه).
 *   سرویس ─── فقط وقتی ایمیلِ سرور واقعاً پیکربندی شده (`customer-notify-email.ready`: نه `log`، فیلدهای لازمِ SMTP/API)؛
 *             وگرنه حالِ صادقانهٔ `not_configured` و هیچ تلاشی. ⛔ هیچ سرویسِ بیرونیِ تازه‌ای نیست.
 *   گیرنده ── فقط صاحبِ فعالِ همان دکان با ایمیلِ معتبر (کاربرِ کارمندِ بی ایمیل یا حسابِ بسته ⇒ `no_email`، هیچ نامه).
 *   سقف ───── حداکثر `shop.order_notify_email.per_day` (پیش‌فرض ۳، ۱ تا ۵۰) نامهٔ پشتیبان در ۲۴ ساعتِ هر دکان (هر تلاش
 *             شمرده می‌شود، حتی ناموفق)؛ بیشتر ⇒ `throttled`. شمارش و رزروِ جا زیرِ قفلِ همان دکان ⇒ دو کارگرِ هم‌زمان
 *             از سقف نمی‌گذرند. همان صف (`order_notify_outbox.fallback_*`، مهاجرتِ ۹۴۴)، نه دفترِ دوم.
 *   یک بار ── «حداکثر یک بار برای هر اعلان» همان `fallback_at` ِ اتمیِ `order-notify.escalate` است؛ این‌جا فقط فرستادن.
 *   متن ───── فقط نامِ دکان، شمارهٔ سفارش و «برنامه را باز کنید» (§۳۰.۱ بندِ ۴) — نه نامِ مشتری، شماره، نشانی، اقلام یا مبلغ.
 *             همان قالبِ ایمیلِ «پیام» ِ برنامهٔ دکان (`mail-templates.messageHtml`)؛ نبودِ قالب ⇒ کارتِ سادهٔ `mailer.card`.
 */
const { one, many, tx } = require('../db');
const orders = require('./orders');

const FLAG_KEY = 'shop.order_notify_email';
const PER_DAY_KEY = 'shop.order_notify_email.per_day';
const DEFAULT_PER_DAY = 3;
const MIN_PER_DAY = 1;
const MAX_PER_DAY = 50;
const DAY_MS = 24 * 60 * 60 * 1000;
const CHANNEL = 'email';

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const faNum = (n) => String(n).replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)]);
const decode = (text) => { try { return JSON.parse(text); } catch { return null; } };

const flagOn = (shopId) => orders.flagOf(shopId, FLAG_KEY);

/** عددِ صحیحِ ۱ تا ۵۰؛ نبود یا نامعتبر ⇒ ۳ (مقدارِ دکان بر `all` جلو می‌افتد) */
function perDayOf(v) {
  return Number.isSafeInteger(v) && v >= MIN_PER_DAY && v <= MAX_PER_DAY ? v : DEFAULT_PER_DAY;
}
async function perDay(shopId) {
  const rows = await many('SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [PER_DAY_KEY, 'all', require('./branch-key').shopOf(shopId)]);
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return perDayOf(v);
}

/** ایمیلِ تأییدشدهٔ صاحبِ فعالِ دکان (کوچک‌حرف) یا '' */
async function ownerEmailOf(shopId) {
  const r = await one(
    `SELECT u.email, u.status FROM shops s JOIN users u ON u.id = s.owner_user_id WHERE s.id = $1`, [require('./branch-key').shopOf(shopId)]
  );
  if (!r || r.status !== 'active') return '';
  try { return require('./validate').email(r.email) || ''; } catch { return ''; }
}

const ready = () => require('./customer-notify-email').ready();

/**
 * حالِ کانال برای `GET /api/shop/order-notify/config`: پرچمِ خاموش ⇒ null (همان پاسخِ پیشین)؛ روشن ⇒ `configured` فقط
 * وقتی ایمیلِ سرور آماده و صاحب ایمیل دارد، وگرنه `not_configured`.
 */
async function status(shopId) {
  if (!(await flagOn(shopId))) return null;
  return (await ready()) && (await ownerEmailOf(shopId)) ? 'configured' : 'not_configured';
}

/** نامه — بی هیچ دادهٔ مشتری */
function mailOf(to, shopName, orderNo, reason) {
  const shop = String(shopName || '').replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, 60);
  const title = `${shop ? `«${shop}»: ` : ''}سفارشِ تازه — شمارهٔ ${faNum(orderNo)}`;
  const why = reason === 'unacked'
    ? 'این سفارش هنوز در برنامهٔ دکان باز نشده است.'
    : 'اعلانِ این سفارش به هیچ گوشیِ دکان نرسید.';
  const body = `${why}\n\nبرای دیدنِ جزئیات و رسیدگی، برنامهٔ دکان را باز کنید.`;
  const mailer = require('./mailer');
  const { messageHtml, escapeHtml } = require('./mail-templates');
  return {
    to,
    subject: title,
    text: body,
    html: messageHtml({ app: 'shop', title, body })
      || mailer.card({ title: escapeHtml(title), lead: escapeHtml(why), body: escapeHtml(body.split('\n\n')[1]) }),
  };
}

/**
 * از درونِ `order-notify.escalate`، پس از گرفتنِ اتمیِ `fallback_at` ِ همین ردیف. پرچمِ خاموش ⇒ null (فراخوان همان پیشین را
 * می‌نویسد)؛ وگرنه همیشه `{ fallback_channel: 'email', fallback_state }` — هرگز استثنا.
 */
async function escalate(row, at, reason) {
  if (!(await flagOn(row.shop_id))) return null;
  const out = (state) => ({ fallback_channel: CHANNEL, fallback_state: state });
  try {
    if (!(await ready())) return out('not_configured');
    const to = await ownerEmailOf(row.shop_id);
    if (!to) return out('no_email');
    const cap = await perDay(row.shop_id);
    //  سقفِ روزانه زیرِ قفلِ همان دکان: شمارِ تلاش‌های ۲۴ ساعت (پذیرفته، خطا، یا در راه = حالِ تهی) و رزروِ جای همین ردیف
    const won = await tx(async (c) => {
      await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`order-notify-email:${row.shop_id}`]);
      const n = Number((await c.query(
        `SELECT COUNT(*)::int AS n FROM order_notify_outbox
          WHERE shop_id = $1 AND id <> $2 AND fallback_channel = $3 AND fallback_at > $4
            AND fallback_state IN ('', 'provider_accepted', 'provider_error')`,
        [row.shop_id, row.id, CHANNEL, at - DAY_MS]
      )).rows[0].n);
      if (n >= cap) return false;
      await c.query('UPDATE order_notify_outbox SET fallback_channel = $2 WHERE id = $1', [row.id, CHANNEL]);
      return true;
    });
    if (!won) return out('throttled');
    const shop = await one('SELECT name FROM shops WHERE id = $1', [row.shop_id]);
    await require('./mailer').send(mailOf(to, shop && shop.name, Number(row.order_no), reason));
    return out('provider_accepted');
  } catch (err) {
    //  ⛔ متنِ خطای سرویسِ ایمیل ممکن است نشانیِ گیرنده را داشته باشد ⇒ فقط کدِ SMTP یا متنِ بی «@»
    const msg = err && err.smtpCode ? `smtp_${err.smtpCode}` : String((err && err.message) || err).slice(0, 200);
    console.error('[order-notify-email] نامهٔ پشتیبان نرفت:', msg.includes('@') ? 'error' : msg);
    return out('provider_error');
  }
}

module.exports = {
  FLAG_KEY, PER_DAY_KEY, DEFAULT_PER_DAY, MIN_PER_DAY, MAX_PER_DAY, CHANNEL,
  flagOn, perDay, perDayOf, ownerEmailOf, status, mailOf, escalate,
};
