'use strict';
/**
 * ویترین — «خبرم کن وقتی موجود شد» و «خبرم کن وقتی ارزان شد» (WEB-10، §۶.۱۴).
 * شرحِ جدول: ‎migrations/188_storefront_alerts.sql‎.
 *
 *   پرچم ──── `shop.storefront.alerts` (`live_config`، دکان یا `all`، پیش‌فرض خاموش). خاموش ⇒ درِ مشتری همان
 *             پاسخِ مسیرِ ناشناختهٔ main، هیچ نوشتنی، و کارگر هشدارِ همان دکان را نمی‌سوزاند (فقط می‌گذرد).
 *   مشتری ─── فقط شمارهٔ تأییدشده (بلیتِ شماره یا نشستِ حساب) — همان هویتی که سفارشِ ویترین با آن ثبت می‌شود.
 *             یکتایی (دکان، شماره، کالا، نوع)؛ تکرارِ ثبت بی‌اثر؛ حداکثر `MAX_PER_SHOP` هشدارِ مسلح برای هر
 *             شماره در هر ویترین؛ سقفِ نوشتنِ هر شماره در درِ مسیر (`WRITES_PER_HOUR`). «لغو» ⇒ ردیف پاک.
 *   ثبت ───── `stock`: فقط وقتی نشانِ همان کارت «تمام شده» (یا «ناموجود») است؛ موجود ⇒ ۴۰۹ `in_stock`،
 *             موجودیِ نامعلوم ⇒ ۴۰۹ `stock_unknown` (گذار بی حالِ دانسته معنا ندارد). `price`: مبنا = قیمتِ
 *             همان لحظهٔ کارت (`priceMinor`، بی تخفیفِ سبد).
 *   گذار ──── کارگر (`tick`، هر `STORE_ALERTS_TICK_MS`، پیش‌فرض ۶۰ ثانیه) هشدارهای مسلح را کهنه‌ترین‌سنجیده
 *             اول می‌سنجد: `stock` وقتی نشانِ کارت `in_stock`/`in_warehouse` شد؛ `price` وقتی قیمتِ کارت کمتر از
 *             مبنا شد. کالای بیرون از ویترین یا موجودیِ نامعلوم ⇒ هیچ. هشدارِ کهنه‌تر از `TTL_MS` ⇒ `expired`.
 *   یک بار ── ⛔ سوختن فقط با `UPDATE … WHERE state = 'armed' RETURNING` ⇒ دو دورِ هم‌زمان یا پشتِ سرِ هم
 *             (یا دو نمونهٔ سرور) فقط یک بار خبر می‌دهند. قاعدهٔ «بی اسپم»: هر هشدار پس از یک گذار مصرف
 *             می‌شود (`fired`) و دیگر خبری نمی‌دهد، حتی اگر کالا دوباره تمام و موجود شود یا باز ارزان‌تر شود؛
 *             مشتری با ثبتِ دوباره همان ردیف را با مبنای تازه مسلح می‌کند.
 *   خبر ───── فقط کانال‌های بی‌پیامکِ `customer-notify` (پوش، واتس‌اپ، تلگرام؛ هرگز پیامک) و فقط با
 *             `shop.customer_notify` ِ روشن (وگرنه `notify_off`)؛ سقفِ هر شماره `PER_DAY` خبر در ۲۴ ساعت؛
 *             کانالِ پولی فقط زیرِ سقفِ روزانهٔ هزینهٔ همان دکان و کانال (NTF-02) که **مشترک** است: هزینهٔ
 *             این خبرها در `customer-notify.spentToday` و هزینهٔ اعلانِ سفارش این‌جا شمرده می‌شود. پاسخِ مبهم
 *             ⇒ `failed`، نه کانالِ بعدی و نه دوباره (شاید رفته باشد). امروز هیچ سرویس‌دهنده‌ای وصل نیست
 *             (D-09) ⇒ حالِ صادقانهٔ `no_channel`؛ وضعیت در «هشدارهای من» ِ مشتری دیده می‌شود.
 *   حریم ──── متن فقط نامِ ویترین و نامِ کالا (بی قیمت، بی شماره)؛ شماره فقط به سرویس‌دهنده، هرگز به لاگ.
 */
const { query, one, many, tx, now } = require('../db');
const orders = require('./orders');
const cost = require('./notify-cost');
const { ulid } = require('./ulid');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront.alerts';
const KINDS = Object.freeze(['stock', 'price']);
const LANGS = Object.freeze(['fa', 'ps', 'en']);
/** فقط کانال‌های بی‌پیامک، به ترتیبِ زنجیرهٔ `customer-notify` (ارزان ⇒ گران) */
const CHANNELS = Object.freeze(['push', 'whatsapp', 'telegram']);
const SELLABLE = Object.freeze(['in_stock', 'in_warehouse']);
const NOT_SELLABLE = Object.freeze(['out_of_stock', 'unavailable']);
const MAX_PER_SHOP = 50;
const WRITES_PER_HOUR = 30;
const PER_DAY = 3;
const DAY_MS = 24 * 60 * 60 * 1000;
const TTL_MS = 90 * DAY_MS;
const BATCH = 200;
const LIST_MAX = 100;
const tickMs = () => {
  const v = Number(process.env.STORE_ALERTS_TICK_MS);
  return Number.isSafeInteger(v) && v >= 1000 ? v : 60_000;
};

const TEMPLATES = Object.freeze({
  stock: { fa: '«{shop}»: «{product}» دوباره موجود است.', ps: '«{shop}»: «{product}» بیا موجود دی.', en: '{shop}: "{product}" is back in stock.' },
  price: { fa: '«{shop}»: قیمتِ «{product}» پایین آمد.', ps: '«{shop}»: د «{product}» بیه راټیټه شوه.', en: '{shop}: the price of "{product}" dropped.' },
});

const flagOn = (shopId) => orders.flagOf(shopId, FLAG_KEY);
const langOf = (v) => {
  const s = String(v || '').trim().slice(0, 2).toLowerCase();
  return LANGS.includes(s) ? s : 'fa';
};
const clean = (s) => require('./customer-notify').cleanName(s);

function render(kind, lang, vars) {
  const t = (TEMPLATES[kind] || TEMPLATES.stock)[LANGS.includes(lang) ? lang : 'fa'];
  return t.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? String(vars[k]) : m));
}

function linkFor(slug, productId, lang) {
  const base = String(process.env.STORE_PUBLIC_URL || '').trim().replace(/\/+$/, '');
  if (!base || !/^https:\/\//.test(base) || !slug) return '';
  return `${base}/${encodeURIComponent(slug)}${lang !== 'fa' ? `?lang=${lang}` : ''}#/p/${encodeURIComponent(productId)}`;
}

/** کارت‌های زندهٔ ویترین با نشانِ موجودی (همان `catalogByIds` + `applyStock` ِ فهرست) — Map(شناسه ⇒ کارت). */
async function liveOf(shopId, ids) {
  const sf = require('./storefront');
  const out = new Map();
  const uniq = [...new Set(ids)];
  for (let i = 0; i < uniq.length; i += 100) {
    const items = (await sf.catalogByIds(shopId, uniq.slice(i, i + 100).join(','))).items;
    for (const p of await require('./storefront-shelf').applyStock(shopId, items)) out.set(p.id, p);
  }
  return out;
}

function view(r) {
  return {
    productId: r.product_id,
    kind: r.kind,
    state: r.state,
    ...(r.kind === 'price' ? { basePriceMinor: Number(r.base_price_minor) } : {}),
    createdAt: Number(r.created_at),
    firedAt: r.fired_at === null || r.fired_at === undefined ? null : Number(r.fired_at),
    notice: r.notice || null,
  };
}

function kindOf(v) {
  const k = String(v || '');
  if (!KINDS.includes(k)) throw badRequest('نوعِ هشدار فقط stock یا price', 'bad_alert_kind');
  return k;
}

async function list(store, phone) {
  const rows = await many(
    `SELECT * FROM storefront_alerts WHERE shop_id = $1 AND phone = $2 ORDER BY created_at DESC, id LIMIT $3`,
    [store.shop_id, phone, LIST_MAX]
  );
  return { items: rows.map(view), max: MAX_PER_SHOP };
}

/** ثبت (یا مسلح کردنِ دوبارهٔ هشدارِ سوخته). ⇒ { alert, created } */
async function subscribe(store, phone, productId, rawKind, { lang = 'fa' } = {}, at = now()) {
  const kind = kindOf(rawKind);
  const sf = require('./storefront');
  const id = String(productId || '');
  const p = sf.PRODUCT_ID_RE.test(id) ? (await liveOf(store.shop_id, [id])).get(id) : null;
  if (!p) throw notFound('این کالا در ویترین نیست', 'product_not_found');
  if (kind === 'stock') {
    const st = p.stock && p.stock.status;
    if (SELLABLE.includes(st)) throw conflict('این کالا همین حالا موجود است', 'in_stock');
    if (!NOT_SELLABLE.includes(st)) throw conflict('موجودیِ این کالا هنوز معلوم نیست', 'stock_unknown');
  }
  const base = kind === 'price' ? p.priceMinor : null;
  return tx(async (c) => {
    //  سقفِ هر شماره در هر ویترین زیرِ قفلِ همان (دکان، شماره) — دو ثبتِ هم‌زمان از سقف نمی‌گذرند
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`store-alert:${store.shop_id}:${phone}`]);
    const cur = (await c.query(
      'SELECT * FROM storefront_alerts WHERE shop_id = $1 AND phone = $2 AND product_id = $3 AND kind = $4',
      [store.shop_id, phone, id, kind]
    )).rows[0];
    if (cur && cur.state === 'armed') return { alert: view(cur), created: false };
    const n = Number((await c.query(
      "SELECT COUNT(*)::int AS n FROM storefront_alerts WHERE shop_id = $1 AND phone = $2 AND state = 'armed'",
      [store.shop_id, phone]
    )).rows[0].n);
    if (n >= MAX_PER_SHOP) throw conflict(`حداکثر ${MAX_PER_SHOP} هشدار در این ویترین`, 'too_many_alerts');
    const row = cur ? (await c.query(
      `UPDATE storefront_alerts SET state = 'armed', lang = $2, base_price_minor = $3, fired_at = NULL, fired_price_minor = NULL,
              notice = '', channel = '', tried = '[]'::jsonb, sent_at = NULL, checked_at = $4, created_at = $4, updated_at = $4
        WHERE id = $1 RETURNING *`,
      [cur.id, langOf(lang), base, at]
    )).rows[0] : (await c.query(
      `INSERT INTO storefront_alerts (id, shop_id, phone, product_id, kind, lang, base_price_minor, checked_at, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$8,$8) RETURNING *`,
      [ulid(), store.shop_id, phone, id, kind, langOf(lang), base, at]
    )).rows[0];
    return { alert: view(row), created: true };
  });
}

async function unsubscribe(store, phone, productId, rawKind) {
  const kind = kindOf(rawKind);
  const r = await query(
    'DELETE FROM storefront_alerts WHERE shop_id = $1 AND phone = $2 AND product_id = $3 AND kind = $4',
    [store.shop_id, phone, String(productId || ''), kind]
  );
  return { deleted: r.rowCount > 0 };
}

// ════════════════════════════════════════════════════════════════════
//  کارگر
// ════════════════════════════════════════════════════════════════════

/** NTF-02: هزینهٔ خبرهای پذیرفتهٔ همین دکان و کانال در ۲۴ ساعتِ گذشته (میکرودلار) — بخشی از سقفِ مشترک. */
async function spentToday(shopId, channel, at) {
  const rows = await many(
    "SELECT tried FROM storefront_alerts WHERE shop_id = $1 AND channel = $2 AND notice = 'sent' AND sent_at > $3",
    [shopId, channel, at - cost.DAY_MS]
  );
  return cost.spentOf(rows.map((r) => (Array.isArray(r.tried) ? r.tried : JSON.parse(r.tried || '[]'))), channel);
}

async function settle(a, notice, { channel = '', tried = [], sentAt = null, at }) {
  await query(
    'UPDATE storefront_alerts SET notice = $2, channel = $3, tried = $4::jsonb, sent_at = $5, updated_at = $6 WHERE id = $1',
    [a.id, notice, channel, JSON.stringify(tried), sentAt, at]
  );
  return notice;
}

/** یک خبر برای هشدارِ تازه‌سوخته — هرگز استثنا به بیرون نمی‌دهد؛ ⇒ وضعیتِ `notice`. */
async function deliver(a, p, at) {
  const notify = require('./customer-notify');
  if (!(await notify.flagOf(a.shop_id))) return settle(a, 'notify_off', { at });
  const sent = await one(
    "SELECT COUNT(*)::int AS n FROM storefront_alerts WHERE phone = $1 AND notice = 'sent' AND sent_at > $2", [a.phone, at - DAY_MS]
  );
  if (sent.n >= PER_DAY) return settle(a, 'rate_limited', { at });
  const shop = await one('SELECT slug, title FROM storefronts WHERE shop_id = $1', [a.shop_id]);
  const lang = LANGS.includes(a.lang) ? a.lang : 'fa';
  const text = render(a.kind, lang, { shop: clean(shop && shop.title) || '—', product: clean(p.name) || '—' });
  const link = linkFor(shop && shop.slug, a.product_id, lang);
  const sized = link ? `${text} ${link}` : text;
  const msg = { to: { phone: a.phone }, shopId: a.shop_id, productId: a.product_id, event: `product_${a.kind}_alert`, lang, text, link };
  const tried = [];
  for (const channel of CHANNELS) {
    const provider = notify.providerOf(channel);
    if (!provider) { tried.push({ channel, result: 'not_configured' }); continue; }
    const price = cost.costMicros(channel, sized);
    if (price > 0 && !cost.allows(channel, sized, await notify.spentToday(a.shop_id, channel, at)).ok) {
      tried.push({ channel, result: 'cost_cap' });
      continue;
    }
    const out = await notify.callProvider(provider, { ...msg, channel, key: `alert:${a.id}:${a.fired_at}:${channel}` });
    if (out.status === 'accepted') {
      tried.push({ channel, result: 'accepted', costMicros: price });
      return settle(a, 'sent', { channel, tried, sentAt: at, at });
    }
    if (out.status === 'rejected') { tried.push({ channel, result: 'rejected', code: out.code }); continue; }
    tried.push({ channel, result: 'retry', code: out.code });
    return settle(a, 'failed', { tried, at });
  }
  return settle(a, tried.some((t) => t.result === 'cost_cap') ? 'cost_cap' : 'no_channel', { tried, at });
}

const touch = (ids, at) => (ids.length
  ? query("UPDATE storefront_alerts SET checked_at = $2 WHERE id = ANY($1::text[]) AND state = 'armed'", [ids, at]) : null);

/** هشدارهای مسلحِ یک دکان (همان ردیف‌هایی که دور خواند) ⇒ شمارِ سوخته‌ها؛ دو نمونهٔ سرور با یک دسته هم فقط یک بار. */
async function checkShop(shopId, list, at) {
  if (!(await flagOn(shopId))) { await touch(list.map((a) => a.id), at); return 0; }
  const old = list.filter((a) => at - Number(a.created_at) > TTL_MS).map((a) => a.id);
  if (old.length) {
    await query("UPDATE storefront_alerts SET state = 'expired', updated_at = $2 WHERE id = ANY($1::text[]) AND state = 'armed'", [old, at]);
  }
  const rest = list.filter((a) => !old.includes(a.id));
  const live = rest.length ? await liveOf(shopId, rest.map((a) => a.product_id)) : new Map();
  let fired = 0;
  const waiting = [];
  for (const a of rest) {
    const p = live.get(a.product_id);
    const hit = Boolean(p) && (a.kind === 'stock'
      ? Boolean(p.stock && SELLABLE.includes(p.stock.status))
      : p.priceMinor !== null && p.priceMinor < Number(a.base_price_minor));
    if (!hit) { waiting.push(a.id); continue; }
    const won = await one(
      `UPDATE storefront_alerts SET state = 'fired', fired_at = $2, fired_price_minor = $3, checked_at = $2, updated_at = $2
        WHERE id = $1 AND state = 'armed' RETURNING *`,
      [a.id, at, p.priceMinor]
    );
    if (!won) continue;   //  دورِ دیگری زودتر سوزاند ⇒ همان یک خبرِ او
    fired++;
    try { await deliver(won, p, at); } catch (err) { console.error('[storefront-alerts]', err.message); }
  }
  await touch(waiting, at);
  return fired;
}

let ticking = false;

/**
 * یک دورِ کارگر (`at` فقط برای آزمون). ⇒ { checked, fired }
 */
async function tick(at = now()) {
  if (ticking) return { checked: 0, fired: 0 };
  ticking = true;
  try {
    const rows = await many("SELECT * FROM storefront_alerts WHERE state = 'armed' ORDER BY checked_at, id LIMIT $1", [BATCH]);
    const byShop = new Map();
    for (const r of rows) {
      if (!byShop.has(r.shop_id)) byShop.set(r.shop_id, []);
      byShop.get(r.shop_id).push(r);
    }
    let fired = 0;
    for (const [shopId, list] of byShop) {
      try { fired += await checkShop(shopId, list, at); } catch (err) { console.error('[storefront-alerts]', err.message); }
    }
    return { checked: rows.length, fired };
  } finally {
    ticking = false;
  }
}

let timer = null;
function start() {
  if (timer) return;
  timer = setInterval(() => tick().catch((err) => console.error('[storefront-alerts]', err.message)), tickMs());
  if (timer.unref) timer.unref();
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  FLAG_KEY, KINDS, CHANNELS, MAX_PER_SHOP, WRITES_PER_HOUR, PER_DAY, TTL_MS, TEMPLATES,
  flagOn, langOf, render, list, subscribe, unsubscribe, spentToday, deliver, checkShop, tick, start, stop,
};
