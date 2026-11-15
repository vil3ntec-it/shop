'use strict';
/**
 * CHK-10 — پیامِ خودکارِ «رسیدیم»: وقتی ناحیهٔ فعالِ ارسالِ دکان خانهٔ کسی را پوشاند که «وقتی به محلِ شما رسیدیم خبرم کن»
 * زده بود (`lib/storefront-area-requests.js`)، **یک** ایمیل به ایمیلِ تأییدشدهٔ همان درخواست. «فقط ایمیل» (۱۴۰۵/۰۸/۲۳):
 * دیگر بیرونی نیست (D-09 لازم نیست) — همان سرویسِ ایمیلِ سرور و همان کانالِ `email` ِ اعلانِ مشتری (`customer-notify-email`).
 * حال روی همان ردیف (مهاجرتِ ۹۴۵)، نه دفترِ دوم.
 *
 *   پرچم ──── `shop.storefront.area_email` (دکان یا `all`، پیش‌فرض خاموش) روی همان دو پرچمِ «خبرم کن» و ناحیه
 *             (`storefront-area-requests.flagOn`). خاموش ⇒ کارگر این دکان را نمی‌خواند و هیچ ردیفی عوض نمی‌شود.
 *   سرویس ─── فقط وقتی ایمیلِ سرور واقعاً پیکربندی شده (`customer-notify-email.ready`)؛ وگرنه هیچ ردیفی گرفته نمی‌شود و
 *             درخواست منتظر می‌ماند (دکان همان «تماس»/«خبر دادم» ِ دستی را دارد) — هیچ کارِ زیان‌بار.
 *   کِی ────── کارگر (`tick`، هر `STORE_AREA_EMAIL_TICK_MS`، پیش‌فرض ۵ دقیقه) و [soon] پس از ذخیرهٔ ناحیه‌ها؛ پوشش با
 *             همان `zoneAt` ِ ناحیه‌های فعالِ امروز (همان سنجشِ فهرستِ دکان).
 *   یک بار ── ⛔ گرفتن فقط با `UPDATE … WHERE notice = '' RETURNING` ⇒ دو دور یا دو نمونهٔ سرور فقط یک بار می‌فرستند؛ پاسخِ
 *             «پذیرفت» ⇒ `sent` = «انجام شد» (از فهرستِ دکان بیرون، مثلِ «خبر دادم»)؛ رد یا پاسخِ مبهم ⇒ `failed` و هرگز دوباره
 *             (شاید رفته باشد — همان قاعدهٔ NTF-04) — دکان آن را در فهرست می‌بیند. درخواستِ دوبارهٔ مشتری حال را از نو می‌کند.
 *   سقف ───── پیش از گرفتن (ردیفِ نگرفته منتظر می‌ماند تا دورِ بعد): (۱) سقفِ روزانهٔ هزینهٔ هر دکان و هر کانال (NTF-02،
 *             `notify-cost`) **مشترک** با اعلانِ مشتری و «خبرم کن» ِ کالا (`customer-notify.spentToday`)؛ (۲) حداکثر
 *             `STORE_AREA_EMAIL_SHOP_PER_DAY` (پیش‌فرض ۱۰۰، ۱ تا ۱۰۰۰) نامهٔ رفتهٔ هر دکان در ۲۴ ساعت؛ (۳) هر گیرنده حداکثر `PER_DAY`
 *             نامهٔ «رسیدیم» در ۲۴ ساعت از همهٔ دکان‌ها.
 *   حریم ──── فقط کلیدِ ایمیل (کلیدِ کهنهٔ شماره ⇒ هیچ). متن فقط نامِ ویترین و «حالا به محلِ شما ارسال دارد» با پیوندِ ویترین
 *             (اگر `STORE_PUBLIC_URL`) — نه نشانی، نه پین، نه نامِ ناحیه، نه ایمیل. «پس گرفتن» و «حذفِ دادهٔ مشتری» ردیف را پاک
 *             می‌کنند ⇒ نامه‌ای نمی‌رود؛ ردیفِ رفته پس از `KEEP_SENT_MS` پاک می‌شود. ایمیل هرگز به لاگ نمی‌رود.
 */
const { query, one, many, now } = require('../db');
const orders = require('./orders');
const cost = require('./notify-cost');

const FLAG_KEY = 'shop.storefront.area_email';
/** سقفِ نامه‌های رفتهٔ هر دکان در ۲۴ ساعت — تنظیمِ سرور (همان الگوی `STORE_ALERTS_SHOP_PER_DAY` ِ «خبرم کن» ِ کالا) */
const SHOP_PER_DAY_ENV = 'STORE_AREA_EMAIL_SHOP_PER_DAY';
const DEFAULT_SHOP_PER_DAY = 100;
const MAX_SHOP_PER_DAY = 1000;
const PER_DAY = 3;
const CHANNEL = 'email';
const DAY_MS = 24 * 60 * 60 * 1000;
/** ردیفِ رفته فقط برای سقف‌های ۲۴ ساعت نگه داشته می‌شود */
const KEEP_SENT_MS = 2 * DAY_MS;
const tickMs = () => {
  const v = Number(process.env.STORE_AREA_EMAIL_TICK_MS);
  return Number.isSafeInteger(v) && v >= 10_000 ? v : 5 * 60_000;
};

const TEMPLATES = Object.freeze({
  fa: '«{shop}» حالا به محلِ شما هم ارسال دارد؛ همین حالا می‌توانید سفارش بدهید.',
  ps: '«{shop}» اوس ستاسو سیمې ته هم لېږد لري؛ همدا اوس فرمایش ورکولی شئ.',
  en: '{shop} now delivers to your area. You can order now.',
});
/** چرا این نامه آمد — تنها نامهٔ همین درخواست */
const FOOTERS = Object.freeze({
  fa: 'این تنها پیامِ درخواستِ «خبرم کن» ِ شماست.',
  ps: 'دا ستاسو د «خبر مې کړه» غوښتنې یوازینی پیغام دی.',
  en: 'This is the only message for your "notify me" request.',
});

const decode = (text) => { try { return JSON.parse(text); } catch { return null; } };
const asJson = (v) => (Array.isArray(v) ? v : decode(v || '[]') || []);

/** هر سه پرچم: «رسیدیم» + «خبرم کن» + ناحیه */
async function flagOn(shopId) {
  return (await orders.flagOf(shopId, FLAG_KEY)) && (await require('./storefront-area-requests').flagOn(shopId));
}

function perDayOf(v) {
  const n = typeof v === 'string' && v.trim() !== '' ? Number(v) : v;
  return Number.isSafeInteger(n) && n >= 1 && n <= MAX_SHOP_PER_DAY ? n : DEFAULT_SHOP_PER_DAY;
}
/** عددِ صحیحِ ۱ تا ۱۰۰۰؛ نبود یا نامعتبر ⇒ ۱۰۰ */
const shopPerDay = () => perDayOf(process.env[SHOP_PER_DAY_ENV]);

function render(lang, vars) {
  const t = TEMPLATES[TEMPLATES[lang] ? lang : 'fa'];
  return t.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? String(vars[k]) : m));
}

function linkFor(slug, lang) {
  const base = String(process.env.STORE_PUBLIC_URL || '').trim().replace(/\/+$/, '');
  if (!base || !/^https:\/\//.test(base) || !slug) return '';
  return `${base}/${encodeURIComponent(slug)}${lang !== 'fa' ? `?lang=${lang}` : ''}`;
}

/** NTF-02: هزینهٔ نامه‌های «رسیدیم» ِ پذیرفتهٔ همین دکان و کانال در ۲۴ ساعت (میکرودلار) — بخشی از سقفِ مشترک */
async function spentToday(shopId, channel, at) {
  if (channel !== CHANNEL) return 0;
  const rows = await many(
    "SELECT tried FROM storefront_area_requests WHERE shop_id = $1 AND notice = 'sent' AND sent_at > $2",
    [shopId, at - cost.DAY_MS]
  );
  return cost.spentOf(rows.map((r) => asJson(r.tried)), channel);
}

const sentSince = async (where, params) => Number((await one(
  `SELECT COUNT(*)::int AS n FROM storefront_area_requests WHERE notice = 'sent' AND ${where}`, params
)).n);

async function settle(id, notice, tried, at) {
  await query(
    `UPDATE storefront_area_requests SET notice = $2, tried = $3::jsonb, sent_at = $4 WHERE id = $1 AND notice = 'sending'`,
    [id, notice, JSON.stringify(tried), notice === 'sent' ? at : null]
  );
  return notice;
}

/**
 * دکانِ روشن: درخواست‌های منتظرِ پوشیده ⇒ هر کدام یک نامه (زیرِ سقف‌ها). ⇒ `{ covered, sent, failed, waiting }`؛
 * پرچمِ خاموش یا ایمیلِ پیکربندی‌نشده ⇒ صفر و هیچ نوشتن.
 */
async function checkShop(shopId, at = now()) {
  const out = { covered: 0, sent: 0, failed: 0, waiting: 0 };
  if (!(await flagOn(shopId))) return out;
  const mail = require('./customer-notify-email');
  if (!(await mail.ready())) return out;
  const areas = require('./storefront-area-requests');
  const sfd = require('./storefront-delivery');
  const zones = await areas.activeZones(shopId);
  if (!zones.length) return out;
  const rows = await many(
    `SELECT id, phone, cell_lat, cell_lng, lang, created_at FROM storefront_area_requests
      WHERE shop_id = $1 AND notice = '' AND updated_at >= $2 ORDER BY created_at, id LIMIT $3`,
    //  همهٔ منتظرها (حداکثر همان سقفِ هر دکان): پوشش در JS سنجیده می‌شود، پس دسته‌ای از نپوشیده‌ها جلوی پوشیده‌ها را نمی‌گیرد
    [shopId, at - areas.RETAIN_MS, areas.MAX_PER_SHOP]
  );
  const covered = rows.filter((r) => r.phone.includes('@') && sfd.zoneAt(zones, Number(r.cell_lat), Number(r.cell_lng)));
  out.covered = covered.length;
  if (!covered.length) return out;
  const notify = require('./customer-notify');
  const shop = await one('SELECT slug, title FROM storefronts WHERE shop_id = $1', [shopId]);
  const title = notify.cleanName(shop && shop.title);
  const cap = shopPerDay();
  let shopSent = await sentSince('shop_id = $1 AND sent_at > $2', [shopId, at - DAY_MS]);
  for (const r of covered) {
    if (shopSent >= cap) { out.waiting += 1; continue; }
    if (await sentSince('phone = $1 AND sent_at > $2', [r.phone, at - DAY_MS]) >= PER_DAY) { out.waiting += 1; continue; }
    const lang = areas.LANGS.includes(r.lang) ? r.lang : 'fa';
    const text = `${render(lang, { shop: title || '—' })}\n\n${FOOTERS[lang]}`;
    const link = linkFor(shop && shop.slug, lang);
    const sized = link ? `${text} ${link}` : text;
    const price = cost.costMicros(CHANNEL, sized);
    if (price > 0 && !cost.allows(CHANNEL, sized, await notify.spentToday(shopId, CHANNEL, at)).ok) { out.waiting += 1; continue; }
    //  ⛔ یک بار: فقط ردیفِ هنوز منتظر گرفته می‌شود
    const won = await one(
      `UPDATE storefront_area_requests SET notice = 'sending' WHERE id = $1 AND notice = '' RETURNING id, created_at`, [r.id]
    );
    if (!won) continue;
    const provider = notify.providerOf(CHANNEL) || mail.provider;
    const res = await notify.callProvider(provider, {
      to: { email: r.phone }, shopId, event: 'area_arrived', lang, text, link, title: title || '—', at,
      channel: CHANNEL, key: `area:${r.id}:${Number(won.created_at)}:${CHANNEL}`,
    });
    if (res.status === 'accepted') {
      await settle(r.id, 'sent', [{ channel: CHANNEL, result: 'accepted', costMicros: price }], at);
      out.sent += 1;
      shopSent += 1;
    } else {
      //  ⛔ فقط کدِ کوتاهِ ماشینی (`smtp_550`، `no_email`) — متنِ خطای سرویس ممکن است نشانی داشته باشد
      const code = /^[a-z0-9_]{1,40}$/i.test(res.code || '') ? res.code : 'error';
      await settle(r.id, 'failed', [{ channel: CHANNEL, result: res.status, code }], at);
      out.failed += 1;
    }
  }
  return out;
}

let ticking = false;

/** یک دورِ کارگر (`at` فقط برای آزمون). ⇒ `{ shops, sent }` */
async function tick(at = now()) {
  if (ticking) return { shops: 0, sent: 0 };
  ticking = true;
  try {
    //  ردیفِ رفته فقط تا سقف‌های ۲۴ ساعت لازم است (کمینه‌سازیِ داده)
    await query("DELETE FROM storefront_area_requests WHERE notice = 'sent' AND sent_at < $1", [at - KEEP_SENT_MS]);
    const shops = await many("SELECT DISTINCT shop_id FROM storefront_area_requests WHERE notice = '' AND phone LIKE '%@%'");
    let sent = 0;
    for (const s of shops) {
      try { sent += (await checkShop(s.shop_id, at)).sent; } catch (err) { console.error('[storefront-area-email]', err.message); }
    }
    return { shops: shops.length, sent };
  } finally {
    ticking = false;
  }
}

let timer = null;
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[storefront-area-email]', err.message)), tickMs());
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}
/** پس از ذخیرهٔ ناحیه‌ها ⇒ یک دورِ زودتر از دورِ بعد (فقط وقتی کارگر روشن است؛ همان قفلِ `ticking`؛ هرگز استثنا) */
function soon() {
  if (!timer) return;
  const t = setTimeout(() => tick().catch((err) => console.error('[storefront-area-email]', err.message)), 1000);
  if (t.unref) t.unref();
}

module.exports = {
  FLAG_KEY, SHOP_PER_DAY_ENV, DEFAULT_SHOP_PER_DAY, PER_DAY, CHANNEL, KEEP_SENT_MS, TEMPLATES, FOOTERS,
  flagOn, shopPerDay, perDayOf, render, linkFor, spentToday, checkShop, tick, start, stop, soon,
};
