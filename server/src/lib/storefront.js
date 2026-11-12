'use strict';
/**
 * ویترینِ اینترنتی و ثبتِ سفارشِ مشتری — فاز ۴ (WEB، CHK؛ بخش‌های ۳.۹ و ۳.۱۰).
 *
 *   روشن ── سه قفل پشتِ هم، هر سه پیش‌فرض خاموش: پرچم‌های `shop.orders` و
 *           `shop.storefront` در «تنظیماتِ زنده» (+ کلیدهای اشتراکِ
 *           `online_orders` و `storefront`) و `enabled` ِ خودِ دکان. هر کدام
 *           نباشد ⇒ نشانیِ عمومی ۴۰۴، درست مثلِ نشانیِ ناموجود.
 *   نشانی ─ D-06: `{slug}.vill3n.top` (پیشنهادِ خودِ مشخصات)؛ `/store/<slug>`
 *           همان صفحه روی هر میزبانی است، برای پیش از DNS ِ ستاره‌دار.
 *   کالا ── فقط کالایی که دکان «در ویترین» گذاشته (`storefront_products`)،
 *           حذف‌نشده و فعال در دفترِ همگام‌سازی (`sync_rows`، فقط خواندن).
 *   قیمت ── را سرور می‌گوید (قیمتِ آنلاین، وگرنه قیمتِ فروشِ دفتر) و درونِ
 *           همان تراکنشِ ساختِ سفارش دوباره می‌خواند (CHK-17). عددی که مشتری
 *           می‌فرستد خوانده نمی‌شود؛ `expectedTotalMinor` فقط برای این است
 *           که اگر جمع عوض شده، سفارش ثبت نشود و تغییر روشن نشان داده شود.
 *   ساخت ── فقط از راهِ `orders.create` با کلیدِ یکتایی (CHK-19).
 *   پیگیری ─ با توکنِ حدس‌نزدنی: `<شناسهٔ سفارش>.<HMAC>` — چیزی ذخیره
 *           نمی‌شود و تکرارِ همان درخواست همان توکن را برمی‌گرداند.
 *
 *   مشتری ─ ۲۳: دیدن و سبد آزاد؛ **ثبتِ سفارش فقط با شمارهٔ تأییدشده**
 *           (کدِ یک‌بارمصرفِ همان `lib/otp.js`). بی سرویسِ پیامک در تولید
 *           ⇒ ۵۰۳ ِ صریح، نه «فرستاده شد» ِ دروغ (BLOCKED-EXTERNAL).
 *   وضعیت ─ ۳۱.۱: «باز/بسته» از ساعتِ کاری و اعلامِ مالک، «اتصال» از آخرین
 *           تماسِ واقعیِ دستگاه‌های دکان (`sync_devices.last_seen_at`)، و
 *           «پذیرشِ سفارش» جدا — هیچ زمانِ ساختگی نشان داده نمی‌شود.
 *
 *   گامِ ۲ ── ناحیه و هزینهٔ ارسال، بازهٔ زمان‌بندی (`storefront-delivery.js`)،
 *           حسابِ مشتری (`store-customers.js`)، صفحهٔ کالا، پرفروش‌ها/تازه‌ها،
 *           خریدِ دوباره و عکسِ کوچکِ بانک + موجودیِ کاتالوگ (`storefront-shelf.js`).
 *
 * ⛔ هنوز نیست (و عمداً): رزروِ موجودی تا وقتی PR #263 روی سرور نیست (CHK-18)،
 * پرداختِ آنلاین (D-08، 🔌).
 */
const crypto = require('crypto');
const db = require('../db');
const { one, many, now } = db, rr = require('./read-replica');   //  نسخهٔ خواندنی (پیش‌فرض خاموش): فقط کاتالوگ و دسته‌های عمومی
const config = require('../config');
const orders = require('./orders');
const S = require('./order-states');
const sfd = require('./storefront-delivery');
const sfs = require('./storefront-seen');
const subst = require('./order-substitution');
const shelf = require('./storefront-shelf');
const views = require('./public-views');
const { stripControl } = require('./validate');
const { badRequest, notFound, conflict, ApiError } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront';
const FEATURE_KEY = 'storefront';
const SLUG_RE = /^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/;
const PRODUCT_ID_RE = /^[A-Za-z0-9_.:-]{1,80}$/;
/** زیردامنه‌هایی که مالِ خودِ سامانه‌اند یا گمراه می‌کنند. */
const RESERVED = new Set([
  'api', 'www', 'admin', 'panel', 'portal', 'sdk', 'shop', 'store', 'stores', 'beta', 'app', 'apps',
  'pump', 'mail', 'smtp', 'imap', 'pop', 'ftp', 'ns1', 'ns2', 'cdn', 'static', 'assets', 'help',
  'support', 'status', 'docs', 'blog', 'dev', 'test', 'staging', 'login', 'auth', 'account',
  'billing', 'pay', 'payment', 'download', 'downloads', 'home', 'vill3n', 'tohid', 'official',
]);
const MAX_CHECKOUT_LINES = 100;
const MAX_QTY_MILLI = 10_000_000;
const MAX_PRICE_MINOR = 100_000_000_000;
const MAX_FEE_MINOR = 100_000_000;
const MAX_DECLARE_MS = 7 * 24 * 60 * 60 * 1000;
const PAGE = 40;
const MAX_PAGE = 100;
const SHOP_ROWS = "r.app = 'shop' AND r.account_kind = 'shop' AND r.table_name = 'products'";
const LIVE_PRODUCT = "r.deleted_at IS NULL AND COALESCE(r.data->>'active', 'true') <> 'false'";

// ════════════════════════════════════════════════════════════════════
//  ورودی
// ════════════════════════════════════════════════════════════════════

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const text = (v, max) => stripControl(str(v)).slice(0, max);
/** S117 — یادداشتِ آزادِ مشتری (سفارش و قلم) بی شمارهٔ کاملِ کارت ذخیره می‌شود. */
const maskCards = (t) => require('./ops-errors').maskCards(t);
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});

function int(v, field, { min = 0, max = Number.MAX_SAFE_INTEGER } = {}) {
  if (typeof v !== 'number' || !Number.isSafeInteger(v)) throw badRequest(`${field} باید عددِ صحیح باشد`, 'bad_number');
  if (v < min || v > max) throw badRequest(`${field} در بازهٔ مجاز نیست`, 'bad_number');
  return v;
}

function bool(v, field) {
  if (typeof v !== 'boolean') throw badRequest(`${field} باید بله/نه باشد`, 'bad_boolean');
  return v;
}

/** «ي/ك» ِ عربی، اعراب، کشیده و نیم‌فاصله — همان چیزی که کاربر در جست‌وجو نمی‌بیند. */
function normalize(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/[ۀة]/g, 'ه').replace(/[أإآ]/g, 'ا')
    .replace(/[ً-ٰٟـ]/g, '')
    .replace(/[‌‍]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
/** همان نرمال‌سازی، در SQL، روی نامِ کالا. */
const SQL_NAME = "translate(lower(r.data->>'name'), 'يىكۀةأإآ' || chr(1600) || chr(8204), 'ییکههااا  ')";

const { SEARCH_FLAG, SEARCH_KEY, searchWhere, plan: searchPlan, finish: searchFinish } = require('./storefront-search');   //  SRCH-01 و WEB-05/06 (شرح همان‌جا)
const spid = require('./storefront-public-id')({ open, str, SLUG_RE });   //  S3: شناسهٔ عمومی و نشانیِ پیشین (شرح همان‌جا)
function slugOf(v) {
  const s = str(v).toLowerCase();
  if (!SLUG_RE.test(s) || s.includes('--')) {
    throw badRequest('نشانیِ ویترین: ۳ تا ۳۲ حرفِ کوچکِ انگلیسی، رقم یا خطِ تیره', 'bad_slug');
  }
  if (RESERVED.has(s)) throw badRequest('این نشانی رزرو است', 'slug_reserved');
  return s;
}

// ════════════════════════════════════════════════════════════════════
//  روشن یا خاموش
// ════════════════════════════════════════════════════════════════════

async function stateOf(shopId) {
  const [ordersFlag, storefrontFlag, { ent }] = await Promise.all([
    orders.flagOf(shopId), orders.flagOf(shopId, FLAG_KEY), orders.entitledOf(shopId),
  ]);
  const flag = ordersFlag && storefrontFlag;
  const entitled = ent.features.includes(FEATURE_KEY) && ent.features.includes(orders.FEATURE_KEY);
  return { flag, ordersFlag, storefrontFlag, entitled, enabled: flag && entitled, ...(await require('./storefront-intake').stateOf(shopId, ent, flag && entitled, FEATURE_KEY)), ent };   //  SUB-10 همین `enabled`؛ #467: accepting و…
}

/** ویترینِ عمومی — یا ۴۰۴؛ «نیست»، «خاموش» و «پرچم نیست» یک جواب‌اند. */
async function open(slug) {
  const s = str(slug).toLowerCase();
  const gone = () => notFound('این ویترین پیدا نشد', 'store_not_found');
  if (!SLUG_RE.test(s)) throw gone();
  //  جریمهٔ «تعلیقِ ویترین» (v3 §۳۸.۶) ⇒ همان «نیست»؛ تنظیماتِ ویترین دست نمی‌خورد.
  const row = await one(
    `SELECT sf.*, s.status AS shop_status,
            EXISTS (SELECT 1 FROM trust_sanctions ts WHERE ts.shop_id = sf.shop_id
                      AND ts.kind = 'suspend_storefront' AND ts.lifted_at IS NULL) AS trust_suspended
       FROM storefronts sf JOIN shops s ON s.id = sf.shop_id WHERE sf.slug = $1`,
    [s]
  );
  if (!row || row.enabled !== true || row.shop_status !== 'active' || row.trust_suspended === true) throw gone();
  if (!require('./storefront-intake').onRow(row, await stateOf(row.shop_id)).enabled) throw gone();   //  SUB-10: بسته ⇒ ۴۰۴؛ #467: accepting روی ردیف
  return row;
}

// ════════════════════════════════════════════════════════════════════
//  نمای عمومی
// ════════════════════════════════════════════════════════════════════

// ── ۳۱.۱ — وضعیتِ دکان برای مشتری ─────────────────────────────────

const asJson = (x, def) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x); } catch { return def; }
};

//  ساعتِ کار و وضعیت (`localParts`، `scheduleOf`، `presenceOf`): `lib/storefront-hours.js`
const { localParts, presenceOf } = require('./storefront-hours');

/** آخرین تماسِ واقعیِ دستگاه‌های دکان با سرور (همگام‌سازی) — یا ۰. */
async function lastSeenOf(shopId) {
  const r = await one(
    `SELECT MAX(last_seen_at) AS t FROM sync_devices WHERE app = 'shop' AND account_kind = 'shop' AND account_id = $1`,
    [shopId]
  );
  return r && r.t !== null && r.t !== undefined ? Number(r.t) : 0;
}

async function statusOf(sf, at = now()) {
  return presenceOf(sf, at, await lastSeenOf(sf.shop_id));
}

function profile(sf, categories = [], status = null) {
  return views.pub('shop', {
    slug: sf.slug,
    title: sf.title,
    tagline: sf.tagline,
    contactPhone: sf.contact_phone,
    accent: sf.accent,
    status,
    fulfillment: { delivery: sf.accept_delivery === true, pickup: sf.accept_pickup === true },
    deliveryFeeMinor: Number(sf.delivery_fee_minor),
    minOrderMinor: Number(sf.min_order_minor),
    currency: 'AFN',
    //  پرداختِ آنلاین منتظرِ D-08 است (🔌) — تا آن روز هیچ دکمه‌ای نیست
    payment: { methods: ['cod'], online: { available: false, decision: 'D-08' } },
    categories,
    //  مهاجرتِ ۲۱۵: نشانی و پیوندِ نقشهٔ دکان برای «دریافت از دکان» (ممیزیِ ۱۴۰۵/۰۸/۱۷، `storefront-sort.placeOf`) — نبود ⇒ کلید نیست
    ...browse.placeOf(sf),
  });
}

/** قیمتِ واحد به یک‌صدمِ افغانی — قیمتِ آنلاین، وگرنه قیمتِ فروشِ دفتر؛ نبود ⇒ null. */
function priceOf(row) {
  if (row.online_price_minor !== null && row.online_price_minor !== undefined) return Number(row.online_price_minor);
  const d = typeof row.data === 'string' ? JSON.parse(row.data) : row.data || {};
  const sale = Number(d.salePrice);
  if (!Number.isFinite(sale) || sale <= 0) return null;
  const minor = Math.round(sale * 100);
  return Number.isSafeInteger(minor) && minor <= MAX_PRICE_MINOR ? minor : null;
}

/** MKT-06: `consumptionDays` ِ کالا — همان `Product.CYCLE_MAX` ِ برنامه */
const CYCLE_MAX = 365;
function cycleOf(d) {
  const n = d && d.consumptionDays;
  return Number.isSafeInteger(n) && n >= 1 && n <= CYCLE_MAX ? { consumptionDays: n } : {};
}

function productView(row) {
  const d = typeof row.data === 'string' ? JSON.parse(row.data) : row.data || {};
  return views.pub('product', {
    id: row.id,
    name: text(d.name, 200),
    unit: text(d.unit, 30),
    category: text(d.category, 80),
    priceMinor: priceOf(row),
    maxQtyMilli: row.max_qty_milli === null || row.max_qty_milli === undefined ? null : Number(row.max_qty_milli),
    photo: row.has_photo === true,
    //  IMG-12: نسخهٔ عکسِ همگام‌شده برای `?v=` ِ نشانیِ عکس (بانک در `withBank` جایش را می‌گیرد)؛ بی عکس ⇒ بی کلید.
    //  WEB-15: `photo_hash` = «اصل» یا «اصل:کارت» (مهاجرتِ ۳۵۰) — همان `version` ِ `product-photos.forStore`
    ...(row.has_photo === true && row.photo_hash && { photoVersion: shelf.photoTag(`l:${row.photo_hash}`) }),
    //  MKT-06: چرخهٔ مصرف (روزِ صحیحِ ۱ تا ۳۶۵) — نبود یا نادرست ⇒ بی کلید (پاسخِ پیشین بی‌کم‌وکاست)
    ...cycleOf(d),
    //  ۳۲.۱/۳۲.۳: سرور هنوز دفترِ موجودی ندارد ⇒ «در حال بررسی»، هرگز «موجود»
    stock: { status: 'unverified', checkedAt: null },
  });
}

const PRODUCT_COLS = `r.row_id AS id, r.data, sp.online_price_minor, sp.max_qty_milli,
  EXISTS (SELECT 1 FROM product_photos ph WHERE ph.app = 'shop' AND ph.account_id = sp.shop_id
            AND ph.product_id = r.row_id AND (ph.data IS NOT NULL OR ph.store_key <> '')) AS has_photo,
  (SELECT ph.hash || COALESCE(':' || th.hash, '') FROM product_photos ph
     LEFT JOIN product_photo_thumbs th ON th.app = ph.app AND th.account_id = ph.account_id
                                      AND th.product_id = ph.product_id AND th.of_hash = ph.hash
    WHERE ph.app = 'shop' AND ph.account_id = sp.shop_id
            AND ph.product_id = r.row_id AND (ph.data IS NOT NULL OR ph.store_key <> '')) AS photo_hash`;
//  جریمهٔ «پنهان کردنِ کالا» (v3 §۳۸.۶، `trust_sanctions`) کالا را از کاتالوگ، عکس
//  و ثبتِ سفارش کنار می‌گذارد؛ `published` ِ خودِ دکان دست نمی‌خورد. بی جریمه ⇒ همان قبلی.
const NOT_HIDDEN = `NOT EXISTS (SELECT 1 FROM trust_sanctions ts WHERE ts.shop_id = sp.shop_id AND ts.product_id = sp.product_id
                     AND ts.kind = 'hide_listing' AND ts.lifted_at IS NULL)`;
const PRODUCT_FROM = `storefront_products sp
  JOIN sync_rows r ON ${SHOP_ROWS} AND r.account_id = sp.shop_id AND r.row_id = sp.product_id AND ${NOT_HIDDEN}`;
const sfc = require('./storefront-catalog')({ SHOP_ROWS, PRODUCT_FROM, NOT_HIDDEN, PRODUCT_ID_RE, MAX_PRICE_MINOR, MAX_QTY_MILLI, obj, int, bool, text, isActive, priceOf });   //  S1 و M2 (شرح همان‌جا)

async function categories(shopId, { hide = false } = {}) {
  const params = [shopId];
  const hidden = hide ? await require('./storefront-filters').hideWhere(shopId, params) : '';   //  WEB-09: دستهٔ همه‌ناموجود نه
  const rows = await rr.many(
    `SELECT DISTINCT r.data->>'category' AS c FROM ${await sfc.productFrom(shopId)}
      WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT} AND COALESCE(r.data->>'category', '') <> ''${hidden}
      ORDER BY 1 LIMIT 60`,
    params
  );
  return rows.map((r) => text(r.c, 80)).filter(Boolean);
}

//  نشانگرِ صفحه، «مرتب‌سازی»، شمار و «قیمتِ پیشینِ خط‌خورده» (WEB-02/07، ممیزیِ ۱۴۰۵/۰۸/۱۷) — `storefront-sort.js`
const browse = require('./storefront-sort')({ PRODUCT_ID_RE, MAX_PRICE_MINOR });

/** کاتالوگِ عمومی: فقط منتشرشده، زنده و قیمت‌دار؛ به ترتیبِ نام، صفحه‌بندیِ کلیدی؛ با `shop.storefront.search` (WEB-05/06) پرش به دسته، غلطِ تایپی و «پیدا نشد» ِ مفید. */
async function catalog(shopId, { q, category, cursor, limit, sort, contactPhone = '', filters = null } = {}) {
  const lim = Math.min(MAX_PAGE, Math.max(1, Number.parseInt(limit, 10) || PAGE));
  const params = [shopId];
  let where = `sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT}`;
  const raw = text(q, 60);
  const p = await searchPlan(shopId, raw, text(category, 80), { categories, normalize, SQL_NAME });   //  خاموش ⇒ همان قبلی
  if (raw && !p.jump && await orders.flagOf(shopId, SEARCH_FLAG)) where += searchWhere(raw, params, await require('./search-synonyms').forShop(shopId));
  else if (p.needle) {
    params.push(`%${p.needle.replace(/[\\%_]/g, (c) => `\\${c}`)}%`);
    where += ` AND ${p.name} LIKE $${params.length}`;
  }
  const cat = p.jump || text(category, 80);
  if (cat) where += ` AND r.data->>'category' = $${params.push(cat)}`;
  //  WEB-07 (`storefront-filters.js`، پرچمِ `shop.storefront.filters`): خاموش ⇒ `filters` تهی و همان پرس‌وجو
  if (filters) where += await filters.where(shopId, params);
  const from = await sfc.productFrom(shopId);
  const count = ((w, a) => () => one(`SELECT count(*) AS n FROM ${from} WHERE ${w}`, a))(where, [...params]);
  const pg = browse.pageOf(sort, cursor, params, lim);
  const limAt = params.push(lim + 1);
  const tail = pg.offset ? ` OFFSET $${params.push(pg.offset)}` : '';
  const rows = await rr.many(
    `SELECT ${PRODUCT_COLS} FROM ${from} WHERE ${where}${pg.where}
      ORDER BY ${pg.order}COALESCE(r.data->>'name', ''), r.row_id LIMIT $${limAt}${tail}`,
    params
  );
  const more = rows.length > lim;
  const page = more ? rows.slice(0, lim) : rows;
  //  کالای بی‌قیمت فروختنی نیست — نشان داده نمی‌شود
  const items = (filters ? filters.keep : (x) => x)(browse.withWas(page, await decorate(shopId, page.map(productView).filter((x) => x.priceMinor !== null && x.name))));
  const total = await browse.totalOf({ cursor, q: raw, more, items, filters, count });
  return searchFinish({ items, nextCursor: more ? pg.next(page[page.length - 1]) : null, ...(total !== undefined && { total }) },
    p, { cursor, shopId, raw, cat, lim, contactPhone, decorate });
}

/** عکسِ بانک (پرچمِ دکان) پیش از عکسِ همگام‌شده — همان ترتیبِ `photo()` ⇒ «عکس دارد» و نسخهٔ عکس (IMG-12) از بانک. */
async function withBank(shopId, items) {
  const bank = await shelf.bankIds(shopId, items.map((p) => p.id));
  for (const p of items) if (bank.has(p.id)) Object.assign(p, { photo: true, photoVersion: bank.get(p.id) });
  return items;
}

/** عکسِ بانک (اگر هست)، جزئیاتِ M4 (برند، بارکد، واحد، variant، `bankProductId`؛ فقط با `market.product_details`) و موجودیِ کاتالوگ (اگر روشن است) روی نماها. */
async function decorate(shopId, items) {
  if (!items.length) return items;
  await withBank(shopId, items);
  return shelf.applyStock(shopId, await require('./product-details').attach(shopId, items));
}

/** کالاهای منتشرشده، زنده و قیمت‌دار با همین شناسه‌ها — به همان ترتیب. */
async function liveByIds(shopId, ids, run) {
  if (!ids.length) return [];
  const sql = `SELECT ${PRODUCT_COLS}, sp.description, sp.specs FROM ${await sfc.productFrom(shopId)}
    WHERE sp.shop_id = $1 AND sp.product_id = ANY($2::text[]) AND sp.published AND ${LIVE_PRODUCT}`;
  const rows = run ? (await run(sql, [shopId, ids])).rows : await many(sql, [shopId, ids]);
  const byId = new Map(rows.map((r) => [r.id, r]));
  return ids.map((id) => byId.get(id)).filter(Boolean);
}

const RECENT_MS = 30 * 24 * 60 * 60 * 1000;
const SHELF = 8;
/*
 *  ⛔ k-گمنامی: «پرفروش» و «مکمل» از سبدِ مشتری‌ها ساخته می‌شوند؛ از یک یا دو
 *  سفارش، سبدِ یک نفر (چه خریده، با چه) را به همه نشان می‌داد. هر کالا/جفت
 *  فقط وقتی پیشنهاد می‌شود که دست‌کم `K_ANON` سفارشِ جدا از `K_ANON` شمارهٔ جدا
 *  پشتش باشد — چند سفارشِ یک نفر یک نفر است.
 */
const K_ANON = 3;

/**
 * WEB-02 — «پرفروش‌ها»: بیشترین سفارشِ واقعیِ ویترینِ همین دکان در ۳۰ روزِ
 * گذشته (بی آزمایشی و بی ردشده/لغوشده)؛ «تازه‌ها»: تازه‌ترین انتشارها در
 * ۳۰ روز؛ «پیشنهادها» (`deals`) در `storefront-deals.js`. بی عددِ ساختگی؛ نبودِ داده ⇒ خالی.
 */
async function highlights(shopId, at = now()) {
  const since = at - RECENT_MS;
  const [best, fresh] = await Promise.all([
    many(
      `SELECT l.product_id, COUNT(DISTINCT l.order_id)::int AS orders, SUM(l.qty_milli)::bigint AS qty
         FROM online_order_lines l JOIN online_orders o ON o.id = l.order_id
        WHERE l.shop_id = $1 AND o.shop_id = $1 AND o.source = 'storefront' AND NOT o.is_test
          AND o.created_at >= $2 AND o.status <> ALL($3::text[])
        GROUP BY l.product_id
       HAVING COUNT(DISTINCT l.order_id) >= $4 AND COUNT(DISTINCT o.customer_phone) >= $4
        ORDER BY 2 DESC, 3 DESC, 1 LIMIT 40`,
      [shopId, since, sfd.RELEASED, K_ANON]
    ),
    many(
      `SELECT product_id FROM storefront_products
        WHERE shop_id = $1 AND published AND published_at >= $2
        ORDER BY published_at DESC, product_id LIMIT 40`,
      [shopId, since]
    ),
  ]);
  const shelfOf = async (ids) => {
    const items = (await liveByIds(shopId, ids)).map(productView).filter((p) => p.priceMinor !== null && p.name);
    return (await decorate(shopId, items)).filter(shelf.suggestable).slice(0, SHELF);
  };
  const [bestsellers, newArrivals, deals] = await Promise.all([
    shelfOf(best.map((r) => r.product_id)), shelfOf(fresh.map((r) => r.product_id)), require('./storefront-deals').deals(many, { PRODUCT_FROM, LIVE_PRODUCT }, shopId, shelfOf),
  ]);
  return { bestsellers: views.pubList('product', bestsellers), newArrivals: views.pubList('product', newArrivals), deals: views.pubList('product', deals), windowDays: 30 };
}

function specsOf(v) {
  const list = Array.isArray(v) ? v : (() => { try { return JSON.parse(v); } catch { return []; } })();
  return (Array.isArray(list) ? list : [])
    .map((x) => ({ name: text(x && x.name, 40), value: text(x && x.value, 120) }))
    .filter((x) => x.name && x.value).slice(0, 20);
}

/**
 * WEB-08 — صفحهٔ کالا: نام، توضیح و مشخصاتِ عمومی (یادداشتِ داخلیِ دفتر
 * هرگز)، قیمت، واحد، موجودی، سقفِ هر سفارش؛ «مشابه» (همان دسته) و «مکمل»
 * (بیشترین هم‌سفارشی در همین ویترین). ناموجود پیشنهاد نمی‌شود (WEB-09).
 */
async function productDetail(shopId, productId) {
  if (!PRODUCT_ID_RE.test(String(productId || ''))) throw notFound('این کالا در ویترین نیست', 'product_not_found');
  const [row] = await liveByIds(shopId, [String(productId)]);
  const p = row ? productView(row) : null;
  if (!p || p.priceMinor === null || !p.name) throw notFound('این کالا در ویترین نیست', 'product_not_found');
  const product = { ...p, description: text(row.description, 2000), specs: specsOf(row.specs) };
  await decorate(shopId, [product]);

  const [sameCat, together] = await Promise.all([
    p.category ? many(
      `SELECT sp.product_id FROM ${await sfc.productFrom(shopId)}
        WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT} AND r.data->>'category' = $2 AND sp.product_id <> $3
        ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT 24`,
      [shopId, p.category, p.id]
    ) : [],
    many(
      `SELECT l2.product_id, COUNT(DISTINCT o.id)::int AS n
         FROM online_order_lines l1
         JOIN online_order_lines l2 ON l2.order_id = l1.order_id AND l2.product_id <> l1.product_id
         JOIN online_orders o ON o.id = l1.order_id
        WHERE l1.shop_id = $1 AND l1.product_id = $2 AND o.source = 'storefront' AND NOT o.is_test
          AND o.created_at >= $3 AND o.status <> ALL($4::text[])
        GROUP BY l2.product_id
       HAVING COUNT(DISTINCT o.id) >= $5 AND COUNT(DISTINCT o.customer_phone) >= $5
        ORDER BY 2 DESC, 1 LIMIT 24`,
      [shopId, p.id, now() - 3 * RECENT_MS, sfd.RELEASED, K_ANON]
    ),
  ]);
  const shelfOf = async (ids) => {
    const items = (await liveByIds(shopId, ids)).map(productView).filter((x) => x.priceMinor !== null && x.name);
    return (await decorate(shopId, items)).filter(shelf.suggestable).slice(0, SHELF);
  };
  const [similar, complementary] = await Promise.all([
    shelfOf(sameCat.map((r) => r.product_id)), shelfOf(together.map((r) => r.product_id)),
  ]);
  return { product: views.pub('product', product), similar: views.pubList('product', similar), complementary: views.pubList('product', complementary) };
}

/**
 * همان کالاهای کاتالوگ با شناسه (حداکثر ۱۰۰) — برای تازه کردنِ قیمت و «هنوز در
 * ویترین» ِ سبدِ مهمان و «خرید دوباره» (WEB-12، ۳۲.۳). نبودن ⇒ در فهرست نیست.
 */
async function catalogByIds(shopId, raw) {
  const ids = [...new Set(String(raw || '').split(',').map((s) => s.trim()).filter((s) => PRODUCT_ID_RE.test(s)))].slice(0, MAX_PAGE);
  if (!ids.length) return { items: [], nextCursor: null };
  const rows = await many(
    `SELECT ${PRODUCT_COLS} FROM ${await sfc.productFrom(shopId)}
      WHERE sp.shop_id = $1 AND sp.product_id = ANY($2::text[]) AND sp.published AND ${LIVE_PRODUCT}`,
    [shopId, ids]
  );
  //  عکسِ بانک همان کاتالوگ ⇒ عکس و نسخهٔ عکسِ سبد پس از تازه شدن نمی‌پرند (IMG-12)
  return { items: await withBank(shopId, rows.map(productView).filter((p) => p.priceMinor !== null && p.name)), nextCursor: null };
}

/**
 * عکسِ کالای منتشرشده — یا null. `size` = `display` (صفحهٔ کالا)، `thumb`
 * (کارت) یا `zoom` (بزرگ‌نمایی، IMG-11؛ فقط بانک): اول نسخهٔ همان اندازه از بانکِ تصاویر (پرچمِ دکان)، وگرنه عکسِ
 * همگام‌شدهٔ امروز — برای کارت نسخهٔ کارتِ همان عکس (۳۲۰px ِ ساختهٔ برنامه، مهاجرتِ ۳۵۰) و
 * اگر نیست همان عکسِ کامل (هرگز عکسِ شکسته یا کارتِ عکسِ پیشین).
 */
async function photo(shopId, productId, size = 'display') {
  if (!PRODUCT_ID_RE.test(String(productId || ''))) return null;
  const live = await one(
    `SELECT 1 FROM ${await sfc.productFrom(shopId)} WHERE sp.shop_id = $1 AND sp.product_id = $2 AND sp.published AND ${LIVE_PRODUCT}`,
    [shopId, productId]
  );
  if (!live) return null;
  //  IMG-11: نسخهٔ بزرگ فقط از بانک (عکسِ همگام‌شدهٔ برنامه خودش تا ۶۰۰KB است و بزرگ‌تر از display ندارد)
  if (size === 'zoom') return shelf.bankZoom(shopId, productId);
  const banked = await shelf.bankImage(shopId, productId, size === 'thumb' ? 'thumb' : 'display');
  if (banked) return banked;
  const legacy = await require('./product-photos').forStore({ app: 'shop', accountId: shopId }, productId, size);
  return legacy && { data: legacy.data, hash: legacy.hash, mime: legacy.mime, tag: shelf.photoTag(`l:${legacy.version}`) };
}

// ════════════════════════════════════════════════════════════════════
//  ثبتِ سفارش
// ════════════════════════════════════════════════════════════════════

function parseCheckout(body, sf, { substitution: subOn = false } = {}) {
  const b = obj(body);
  const fulfillment = str(b.fulfillment);
  if (!['delivery', 'pickup'].includes(fulfillment)) throw badRequest('ارسال یا دریافت از دکان؟', 'bad_fulfillment');
  if ((fulfillment === 'delivery' && !sf.accept_delivery) || (fulfillment === 'pickup' && !sf.accept_pickup)) {
    throw badRequest('این روشِ تحویل در این ویترین نیست', 'fulfillment_unavailable');
  }
  const paymentMethod = str(b.paymentMethod) || 'cod';
  if (paymentMethod !== 'cod') throw badRequest('فعلاً فقط نقد هنگامِ تحویل', 'bad_payment_method');
  //  CHK-01: مهمان فقط با نام و شماره (و پین برای ارسال)
  const contact = orders.parseContact(b, fulfillment, { phoneRequired: true });

  if (!Array.isArray(b.lines) || b.lines.length === 0) throw badRequest('سبد خالی است', 'bad_lines');
  if (b.lines.length > MAX_CHECKOUT_LINES) throw badRequest(`حداکثر ${MAX_CHECKOUT_LINES} قلم`, 'bad_lines');
  const seen = new Set();
  const lines = b.lines.map((l, i) => {
    const x = obj(l);
    const productId = str(x.productId);
    if (!PRODUCT_ID_RE.test(productId)) throw badRequest(`کالای قلمِ ${i + 1} معتبر نیست`, 'bad_lines');
    if (seen.has(productId)) throw badRequest('یک کالا دو بار در سبد آمده است', 'bad_lines');
    seen.add(productId);
    return {
      productId,
      qtyMilli: int(x.qtyMilli, `مقدارِ قلمِ ${i + 1}`, { min: 1, max: MAX_QTY_MILLI }),
      note: maskCards(text(x.note, 200)), ...sfs.expectedOf(x, i), ...subst.storeLine(x, productId, subOn, i),   // CHK-17 فیِ دیده‌شده، CHK-15 جایگزینی
    };
  });

  let expectedTotalMinor = null;
  if (b.expectedTotalMinor !== undefined && b.expectedTotalMinor !== null) {
    expectedTotalMinor = int(b.expectedTotalMinor, 'جمعِ دیده‌شده', { min: 0, max: MAX_PRICE_MINOR * 10 });
  }
  //  CHK-07/10: ناحیه‌ای که مشتری بی پین برگزیده (پین باشد، پین برنده است)
  const zoneId = fulfillment === 'delivery' ? str(obj(b.address).zoneId) : '';
  if (zoneId && !sfd.ID_RE.test(zoneId)) throw badRequest('ناحیه معتبر نیست', 'bad_zone');
  //  CHK-12: آغازِ بازهٔ برگزیده؛ نبود ⇒ «هرچه زودتر»
  const slotStartsAt = b.slotStartsAt === undefined || b.slotStartsAt === null || b.slotStartsAt === ''
    ? null : int(b.slotStartsAt, 'بازهٔ زمانی', { min: 1, max: 4102444800000 });
  return {
    fulfillment, paymentMethod, ...contact, note: maskCards(text(b.note, 500)), lines, expectedTotalMinor, zoneId, slotStartsAt,
    substitution: subst.orderOf(b, subOn), secondOrder: b.secondOrder === true,   //  S41: پاسخِ «سفارشِ دوم» به پرسشِ تکراری
  };
}

/** برگهٔ قیمتِ امروزِ سبد (همان که در `price_changed` برمی‌گردد). */
function quoteOf(lines, fees) {
  return {
    lines: lines.map((l) => ({
      productId: l.productId, name: l.name, unit: l.unit, qtyMilli: l.qtyMilli,
      unitPriceMinor: l.unitPriceMinor, lineTotalMinor: l.lineTotalMinor,
    })),
    subtotalMinor: fees.subtotalMinor,
    deliveryFeeMinor: fees.deliveryFeeMinor, ...sfd.feeParts(fees),
    totalMinor: fees.totalMinor,
    ...(fees.minOrderMinor !== undefined ? { minOrderMinor: fees.minOrderMinor } : {}),
  };
}

function withData(err, data) {
  Object.assign(err, { data });
  return err;
}

/**
 * قیمت‌گذاریِ سرور درونِ تراکنشِ `orders.create` — قیمت و «هنوز در ویترین»
 * همان لحظه از دفتر خوانده می‌شود.
 */
function pricer(shopId, sf, input, { zonesOn = false, from = PRODUCT_FROM } = {}) {
  return async (run, at) => {
    const ids = input.lines.map((l) => l.productId);
    const rows = (await run(
      `SELECT ${PRODUCT_COLS} FROM ${from}
        WHERE sp.shop_id = $1 AND sp.product_id = ANY($2::text[]) AND sp.published AND ${LIVE_PRODUCT}`,
      [shopId, subst.withSubstitutes(ids, input.lines)]
    )).rows;
    const byId = new Map(rows.map((r) => [r.id, productView(r)]));
    const unavailable = await require('./order-picking').unorderable(run, shopId, ids, (id) => !byId.has(id) || byId.get(id).priceMinor === null || !byId.get(id).name);
    if (unavailable.length) {
      throw withData(conflict('برخی کالاها دیگر در ویترین نیستند', 'product_unavailable'), { unavailable });
    }
    const lines = input.lines.map((l, i) => {
      const p = byId.get(l.productId);
      if (p.maxQtyMilli !== null && l.qtyMilli > p.maxQtyMilli) {
        throw withData(badRequest(`حداکثرِ «${p.name}» در هر سفارش بیشتر نمی‌شود`, 'qty_over_limit'),
          { productId: p.id, maxQtyMilli: p.maxQtyMilli });
      }
      return {
        lineNo: i + 1, productId: p.id, name: p.name, unit: p.unit, qtyMilli: l.qtyMilli,
        unitPriceMinor: p.priceMinor, lineTotalMinor: S.lineTotalMinor(l.qtyMilli, p.priceMinor),
        note: l.note, ...subst.storeResolved(l, byId, input.substitution),
      };
    });
    const subtotalMinor = lines.reduce((s, l) => s + l.lineTotalMinor, 0);
    //  ناحیه (هزینه/حداقلِ خودش؛ بی ناحیه همان ثابتِ امروز) و بازهٔ زمانی، زیرِ همین قفل
    const d = await sfd.forCheckout(run, sf, input, subtotalMinor, at, { zonesOn });
    const { deliveryFeeMinor } = d;
    const totalMinor = subtotalMinor + deliveryFeeMinor;
    if (!Number.isSafeInteger(totalMinor)) throw badRequest('جمعِ سفارش بیش از حد بزرگ است', 'bad_lines');
    const fees = { subtotalMinor, deliveryFeeMinor, totalMinor, minOrderMinor: d.minOrderMinor, ...sfd.feeParts(d) };
    if (sfd.belowMin(subtotalMinor, d)) {
      throw withData(badRequest('سبد از حداقلِ سفارش کمتر است', 'below_min_order'),
        { minOrderMinor: d.minOrderMinor, quote: quoteOf(lines, fees) });
    }
    //  CHK-17/S32: جمع یا فیِ هر قلم با آن‌چه مشتری دید فرق دارد ⇒ ثبت نمی‌شود؛ برگهٔ تازه و «X از … به …»
    sfs.assertSeen(input, lines, totalMinor, () => quoteOf(lines, fees));
    return {
      source: 'storefront', fulfillment: input.fulfillment, paymentMethod: input.paymentMethod,
      customerName: input.customerName, customerPhone: input.customerPhone,
      address: d.address, delivery: d.delivery, note: input.note, isTest: false,
      lines, subtotalMinor, discountMinor: 0, deliveryFeeMinor, totalMinor,
      scheduledFor: d.scheduledFor, itemsCount: lines.length, substitution: input.substitution,
    };
  };
}

// ── توکنِ پیگیری ─────────────────────────────────────────────────

function tokenKey() {
  const secret = config.secrets.api || config.secrets.jwt;
  if (!secret) throw new ApiError(503, 'storefront_unavailable', 'ویترین هنوز روی این سرور آماده نیست');
  return crypto.createHash('sha256').update(`storefront-order-token\n${secret}`).digest();
}

function macOf(shopId, orderId) {
  return crypto.createHmac('sha256', tokenKey()).update(`${shopId}\n${orderId}`).digest('base64url').slice(0, 32);
}

//  TEN-03: لینکِ پیگیریِ زمان‌دار و باطل‌شدنی — `lib/storefront-links.js` (شرح همان‌جا)
const { LINK_FLAG, tokenOf, linkPolicyOn, listTokenOf, currentTokenOf, parseToken, orderIdOf, orderIdOfLink, revokeLink } = require('./storefront-links')({ tokenKey, macOf, orders, one, now, config, ApiError, notFound });

/**
 * نمای مشتری — نه شناسهٔ کارمند، نه یادداشتِ داخلی، نه شماره و نشانی.
 * `delivery` (S5) فقط وقتی پرچمِ نمای پیک روشن است می‌آید (`delivery.customerView`).
 */
function publicOrder(o, token, delivery) {
  return views.pub('order', {
    token,
    orderNo: o.orderNo, status: o.status, statusReason: o.statusReason,
    substitution: o.substitution || '',   // CHK-15: ترجیحِ کلی
    fulfillment: o.fulfillment,
    paymentMethod: o.paymentMethod, paymentStatus: o.paymentStatus,
    currency: o.currency,
    itemsCount: o.itemsCount,
    lines: o.lines.map((l) => ({
      productId: l.productId, name: l.name, unit: l.unit, qtyMilli: l.qtyMilli,
      unitPriceMinor: l.unitPriceMinor, lineTotalMinor: l.lineTotalMinor, note: l.note, ...subst.publicLine(l),
    })),
    subtotalMinor: o.subtotalMinor,
    discountMinor: o.discountMinor,
    deliveryFeeMinor: o.deliveryFeeMinor,
    totalMinor: o.totalMinor,
    readyAt: o.readyAt,
    //  CHK-12: بازهٔ برگزیده (null = هرچه زودتر) · CHK-07: ناحیهٔ ارسال · CHK-06/07: ریزِ هزینهٔ ارسال
    scheduledFor: o.scheduledFor,
    slotEndsAt: o.delivery && o.delivery.slot ? Number(o.delivery.slot.endsAt) : null,
    zoneName: o.address && o.address.zone ? o.address.zone.name : '', ...sfd.orderFeeParts(o),
    createdAt: o.createdAt,
    updatedAt: o.updatedAt,
    //  CHK-22: لغوِ رایگان تا پیش از قبول
    canCancel: o.status === 'new',
    timeline: o.events
      .filter((e) => e.kind === 'created' || e.kind === 'status')
      .map((e) => ({ status: e.toStatus, at: e.at })),
    ...(delivery !== undefined ? { delivery } : {}),
  });
}

const CUSTOMER = Object.freeze({ type: 'customer', id: '' });

/** شمارهٔ تأییدشده: از بلیتِ تازه، وگرنه از نشستِ حسابِ مشتری — یا null (`storefront-ticket.verification`). */
async function verifiedPhone(ticket, session) {
  const v = await verification(ticket, session);
  return v ? v.phone : null;
}

async function checkout(sf, { key, body, ticket, session, ip = '' }) {
  //  ۲۳.۲: نهایی‌کردنِ سفارش ⇒ شمارهٔ تأییدشده (S133) — بلیت یا نشستِ حساب (S134)
  const verified = await verifiedPhone(ticket, session);
  if (!verified) throw new ApiError(401, 'verification_required', 'برای ثبتِ سفارش، شمارهٔ خود را تأیید کنید');
  const input = parseCheckout(body, sf, { substitution: await subst.flagOn(sf.shop_id) });
  if (input.customerPhone !== verified) {
    throw new ApiError(403, 'phone_not_verified', 'این شماره تأیید نشده است؛ همان شمارهٔ تأییدشده را بنویسید');
  }
  tokenKey();   // سرورِ بی رمز سفارش نمی‌گیرد که بعد پیگیری‌اش ممکن نباشد
  //  پرچمِ ناحیه/بازه و کاتالوگِ S1 (اگر منبع است: بی مهلت تازه؛ قیمت درونِ تراکنش از همان جدول) پیش از تراکنش (قفلِ سفارش کوتاه بماند)
  const [zonesOn, from] = await Promise.all([sfd.flagOn(sf.shop_id), sfc.productFrom(sf.shop_id, { strict: true })]);
  //  RSK-07: سقفِ شماره درونِ همان تراکنش و پس از قفلِ دکان (lib/order-rate)
  const price = require('./order-rate').guard(await require('./storefront-promos').wrap(pricer(sf.shop_id, sf, input, { zonesOn, from }), sf, { key, body, input, phone: verified, ip }), { shopId: sf.shop_id, phone: verified, secondOrder: input.secondOrder });   //  MKT-01/02، RSK-09: تخفیفِ ویترین پشتِ پرچم‌های خاموش
  const out = await orders.create(sf.shop_id, { key, body, actor: CUSTOMER, price, intakeKeys: [FEATURE_KEY] });   //  #467: دروازهٔ اشتراک با کلیدِ ویترین هم
  //  TEN-03: با پرچمِ لینکِ زمان‌دار، توکنِ v2 از همین لحظه (تکرارِ همان درخواست هم توکنِ تازه)
  const link = (await linkPolicyOn(sf.shop_id)) ? { gen: 0, at: now() } : null;
  return {
    order: publicOrder(out.order, tokenOf(sf.shop_id, out.order.id, link)), replayed: out.replayed,
    orderId: out.order.id,
  };
}

async function track(sf, token) {
  const id = await orderIdOfLink(sf, token);
  const o = await orders.get(sf.shop_id, id, CUSTOMER);
  if (o.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  return require('./order-late-apology').withApology(sf.shop_id, id, await require('./order-add-items').withAddItems(sf.shop_id, o, await require('./order-amend').withAmendment(sf.shop_id, id, publicOrder(o, token, await require('./delivery').customerView(sf.shop_id, id)))));   //  ORD-14 `amendment`، CHK-21 `addItems`، NTF-10 `apology` — هر کدام فقط با پرچمِ خودش
}

async function cancel(sf, token) {
  const id = await orderIdOfLink(sf, token);
  const o = await orders.get(sf.shop_id, id, CUSTOMER);
  if (o.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  try {
    const out = await orders.transition(sf.shop_id, id, { to: 'cancelled_by_customer', from: 'new' }, CUSTOMER);
    return { order: publicOrder(out.order, token), unchanged: out.unchanged };
  } catch (err) {
    if (err.code === 'status_changed' || err.code === 'invalid_transition') {
      throw conflict('سفارش قبول شده است؛ برای لغو با دکان تماس بگیرید', 'cannot_cancel');
    }
    throw err;
  }
}

//  ۲۳: تأییدِ شمارهٔ مشتری (بلیتِ امضاشده و کدِ یک‌بارمصرف) — `lib/storefront-ticket.js` (شرح همان‌جا)
const { ticketOf, parseTicket, verifiedPhoneOfTicket, verification, verificationAvailable, startVerification, checkVerification } = require('./storefront-ticket')({ tokenKey, obj });

/** S138: سفارش‌های همین شمارهٔ تأییدشده در همین ویترین — از هر دستگاهی. */
async function myOrders(sf, ticket, session = '') {
  const phone = await verifiedPhone(ticket, session);
  if (!phone) throw new ApiError(401, 'verification_required', 'برای دیدنِ سفارش‌ها، شمارهٔ خود را تأیید کنید');
  const rows = await many(
    `SELECT id, order_no, status, total_minor, items_count, created_at, track_gen FROM online_orders
      WHERE shop_id = $1 AND source = 'storefront' AND customer_phone = $2
      ORDER BY created_at DESC, id DESC LIMIT 20`,
    [sf.shop_id, phone]
  );
  const on = await linkPolicyOn(sf.shop_id);
  return views.pubList('orderSummary', rows.map((r) => ({
    orderNo: Number(r.order_no), status: r.status, totalMinor: Number(r.total_minor),
    itemsCount: Number(r.items_count), createdAt: Number(r.created_at), token: listTokenOf(sf.shop_id, r, on) })));
}

/**
 * تاریخچهٔ حسابِ مشتری در همین ویترین — صفحه‌بندیِ کلیدی (تازه‌ترین اول)،
 * با نامِ چند قلمِ اول. فقط همین شماره و همین دکان (S138).
 */
async function ordersOfPhone(sf, phone, { cursor, limit } = {}) {
  const lim = Math.min(50, Math.max(1, Number.parseInt(limit, 10) || 20));
  const params = [sf.shop_id, phone];
  let where = `shop_id = $1 AND source = 'storefront' AND customer_phone = $2`;
  if (cursor) {
    const m = /^(\d{1,15})\.([0-9A-Z]{26})$/.exec(String(cursor));
    if (!m) throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
    params.push(Number(m[1]), m[2]);
    where += ` AND (created_at, id) < ($3, $4)`;
  }
  params.push(lim + 1);
  const rows = await many(
    `SELECT id, order_no, status, fulfillment, total_minor, items_count, scheduled_for, created_at, track_gen FROM online_orders
      WHERE ${where} ORDER BY created_at DESC, id DESC LIMIT $${params.length}`,
    params
  );
  const more = rows.length > lim;
  const page = more ? rows.slice(0, lim) : rows;
  const names = page.length ? await many(
    `SELECT order_id, name FROM online_order_lines WHERE shop_id = $1 AND order_id = ANY($2::text[]) AND line_no <= 3
      ORDER BY order_id, line_no`,
    [sf.shop_id, page.map((r) => r.id)]
  ) : [];
  const last = page[page.length - 1];
  const on = await linkPolicyOn(sf.shop_id);
  return {
    orders: views.pubList('orderSummary', page.map((r) => ({
      orderNo: Number(r.order_no), status: r.status, fulfillment: r.fulfillment, totalMinor: Number(r.total_minor),
      itemsCount: Number(r.items_count), createdAt: Number(r.created_at),
      scheduledFor: r.scheduled_for === null ? null : Number(r.scheduled_for),
      preview: names.filter((n) => n.order_id === r.id).map((n) => n.name),
      token: listTokenOf(sf.shop_id, r, on),
    }))),
    nextCursor: more ? `${Number(last.created_at)}.${last.id}` : null,
  };
}

/**
 * WEB-12 — «خریدِ دوباره» با قیمت و موجودیِ امروز: هر قلمِ سفارشِ قبلی با
 * قیمتِ امروز (یا «دیگر در ویترین نیست»/«ناموجود»). چیزی ساخته نمی‌شود؛
 * سبد در مرورگر پر می‌شود و ثبت از همان راهِ همیشگی است.
 */
async function reorder(sf, token) {
  const o = await track(sf, token);
  const ids = o.lines.map((l) => l.productId);
  const live = await decorate(sf.shop_id, (await liveByIds(sf.shop_id, ids)).map(productView));
  const byId = new Map(live.map((p) => [p.id, p]));
  return {
    orderNo: o.orderNo,
    lines: views.pubList('reorderLine', o.lines.map((l) => {
      const p = byId.get(l.productId);
      const ok = Boolean(p && p.priceMinor !== null && p.name) && shelf.suggestable(p);
      const qtyMilli = ok && p.maxQtyMilli !== null ? Math.min(l.qtyMilli, p.maxQtyMilli) : l.qtyMilli;
      return {
        productId: l.productId, name: ok ? p.name : l.name, unit: ok ? p.unit : l.unit, qtyMilli,
        previousPriceMinor: l.unitPriceMinor,
        priceMinor: ok ? p.priceMinor : null,
        maxQtyMilli: ok ? p.maxQtyMilli : null,
        available: ok,
        reason: ok ? '' : (p && p.priceMinor !== null ? 'out_of_stock' : 'not_in_store'),
        stock: ok ? p.stock : null,
      };
    })),
  };
}

// ════════════════════════════════════════════════════════════════════
//  سمتِ دکان
// ════════════════════════════════════════════════════════════════════

//  تنظیمِ ویترین، اعلامِ حضور (۳۱.۱/۳۱.۲) و کالاهای «در ویترین» — `lib/storefront-owner.js` (شرح همان‌جا)
const { settingsOf, saveSettings, declarePresence, staffProducts, staffProduct, setProduct } = require('./storefront-owner')({
  profile, statusOf, slugOf, productView, isActive, specsOf, asJson, str, obj, int, bool, text, spid, sfc,
  PRODUCT_ID_RE, PAGE, MAX_PAGE, MAX_PRICE_MINOR, MAX_QTY_MILLI, MAX_FEE_MINOR, MAX_DECLARE_MS,
});

function isActive(data) {
  const d = typeof data === 'string' ? JSON.parse(data) : data || {};
  return String(d.active ?? 'true') !== 'false';
}

/** میزبانِ `<slug>.<دامنه>` ⇒ slug؛ وگرنه ''. */
function slugFromHost(host) {
  const domain = config.storefront.domain;
  if (!domain) return '';
  const h = String(host || '').toLowerCase().replace(/:\d+$/, '');
  if (!h.endsWith(`.${domain}`)) return '';
  const label = h.slice(0, -(domain.length + 1));
  if (!SLUG_RE.test(label) || RESERVED.has(label)) return '';
  return label;
}

module.exports = {
  FLAG_KEY, FEATURE_KEY, SLUG_RE, RESERVED, SEARCH_FLAG, SEARCH_KEY, PRODUCT_ID_RE, MAX_CHECKOUT_LINES, MAX_QTY_MILLI, ...spid,
  normalize, slugOf, slugFromHost, stateOf, open, profile, categories, catalog, catalogByIds, photo,
  highlights, productDetail, reorder, ordersOfPhone, verifiedPhone,
  localParts, presenceOf, statusOf,
  parseCheckout, checkout, track, cancel, tokenOf, parseToken, orderIdOf, orderIdOfLink, listTokenOf, currentTokenOf, linkPolicyOn, revokeLink, LINK_FLAG, publicOrder,
  K_ANON, ticketOf, parseTicket, verifiedPhoneOfTicket, verification, verificationAvailable, startVerification, checkVerification, myOrders,
  settingsOf, saveSettings, declarePresence, staffProducts, staffProduct, setProduct, ...sfc,   //  S1 و M2: storefront-catalog.js
  //  بازارچه (`lib/market.js`) همان دروازهٔ کالا را می‌خواند، نه نسخهٔ دومی از آن
  PRODUCT_FROM, PRODUCT_COLS, LIVE_PRODUCT, SQL_NAME, productView,
};
