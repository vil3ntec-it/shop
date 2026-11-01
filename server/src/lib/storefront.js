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
const { one, many, now, tx } = db;
const config = require('../config');
const orders = require('./orders');
const S = require('./order-states');
const sfd = require('./storefront-delivery');
const sfs = require('./storefront-seen');
const subst = require('./order-substitution');
const shelf = require('./storefront-shelf');
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

const { SEARCH_FLAG, SEARCH_KEY, searchWhere } = require('./storefront-search');   //  SRCH-01 (شرح همان‌جا)
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
  return { flag, ordersFlag, storefrontFlag, entitled, enabled: flag && entitled, ent };
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
  if (!(await stateOf(row.shop_id)).enabled) throw gone();
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
  return {
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
  };
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

function productView(row) {
  const d = typeof row.data === 'string' ? JSON.parse(row.data) : row.data || {};
  return {
    id: row.id,
    name: text(d.name, 200),
    unit: text(d.unit, 30),
    category: text(d.category, 80),
    priceMinor: priceOf(row),
    maxQtyMilli: row.max_qty_milli === null || row.max_qty_milli === undefined ? null : Number(row.max_qty_milli),
    photo: row.has_photo === true,
    //  ۳۲.۱/۳۲.۳: سرور هنوز دفترِ موجودی ندارد ⇒ «در حال بررسی»، هرگز «موجود»
    stock: { status: 'unverified', checkedAt: null },
  };
}

const PRODUCT_COLS = `r.row_id AS id, r.data, sp.online_price_minor, sp.max_qty_milli,
  EXISTS (SELECT 1 FROM product_photos ph WHERE ph.app = 'shop' AND ph.account_id = sp.shop_id
            AND ph.product_id = r.row_id AND ph.data IS NOT NULL) AS has_photo`;
//  جریمهٔ «پنهان کردنِ کالا» (v3 §۳۸.۶، `trust_sanctions`) کالا را از کاتالوگ، عکس
//  و ثبتِ سفارش کنار می‌گذارد؛ `published` ِ خودِ دکان دست نمی‌خورد. بی جریمه ⇒ همان قبلی.
const NOT_HIDDEN = `NOT EXISTS (SELECT 1 FROM trust_sanctions ts WHERE ts.shop_id = sp.shop_id AND ts.product_id = sp.product_id
                     AND ts.kind = 'hide_listing' AND ts.lifted_at IS NULL)`;
const PRODUCT_FROM = `storefront_products sp
  JOIN sync_rows r ON ${SHOP_ROWS} AND r.account_id = sp.shop_id AND r.row_id = sp.product_id AND ${NOT_HIDDEN}`;
const sfc = require('./storefront-catalog')({ SHOP_ROWS, PRODUCT_FROM, NOT_HIDDEN, PRODUCT_ID_RE, MAX_PRICE_MINOR, MAX_QTY_MILLI, obj, int, bool, text, isActive, priceOf });   //  S1 و M2 (شرح همان‌جا)

async function categories(shopId) {
  const rows = await many(
    `SELECT DISTINCT r.data->>'category' AS c FROM ${await sfc.productFrom(shopId)}
      WHERE sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT} AND COALESCE(r.data->>'category', '') <> ''
      ORDER BY 1 LIMIT 60`,
    [shopId]
  );
  return rows.map((r) => text(r.c, 80)).filter(Boolean);
}

function encodeCursor(name, id) {
  return Buffer.from(JSON.stringify([name, id])).toString('base64url');
}
function decodeCursor(raw) {
  try {
    const [name, id] = JSON.parse(Buffer.from(String(raw), 'base64url').toString('utf8'));
    if (typeof name !== 'string' || typeof id !== 'string' || !PRODUCT_ID_RE.test(id)) throw new Error('x');
    return { name, id };
  } catch {
    throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
  }
}

/** کاتالوگِ عمومی: فقط منتشرشده، زنده و قیمت‌دار؛ به ترتیبِ نام، صفحه‌بندیِ کلیدی. */
async function catalog(shopId, { q, category, cursor, limit } = {}) {
  const lim = Math.min(MAX_PAGE, Math.max(1, Number.parseInt(limit, 10) || PAGE));
  const params = [shopId];
  let where = `sp.shop_id = $1 AND sp.published AND ${LIVE_PRODUCT}`;
  const raw = text(q, 60);
  const needle = normalize(raw);
  if (raw && await orders.flagOf(shopId, SEARCH_FLAG)) where += searchWhere(raw, params);
  else if (needle) {
    params.push(`%${needle.replace(/[\\%_]/g, (c) => `\\${c}`)}%`);
    where += ` AND ${SQL_NAME} LIKE $${params.length}`;
  }
  const cat = text(category, 80);
  if (cat) {
    params.push(cat);
    where += ` AND r.data->>'category' = $${params.length}`;
  }
  if (cursor) {
    const c = decodeCursor(cursor);
    params.push(c.name, c.id);
    where += ` AND (COALESCE(r.data->>'name', ''), r.row_id) > ($${params.length - 1}, $${params.length})`;
  }
  params.push(lim + 1);
  const rows = await many(
    `SELECT ${PRODUCT_COLS} FROM ${await sfc.productFrom(shopId)} WHERE ${where}
      ORDER BY COALESCE(r.data->>'name', ''), r.row_id LIMIT $${params.length}`,
    params
  );
  const more = rows.length > lim;
  const page = more ? rows.slice(0, lim) : rows;
  const last = page[page.length - 1];
  return {
    //  کالای بی‌قیمت فروختنی نیست — نشان داده نمی‌شود
    items: await decorate(shopId, page.map(productView).filter((p) => p.priceMinor !== null && p.name)),
    nextCursor: more ? encodeCursor(String((last.data && last.data.name) || ''), last.id) : null,
  };
}

/** عکسِ بانک (اگر هست)، جزئیاتِ M4 (برند، بارکد، واحد، variant، `bankProductId`؛ فقط با `market.product_details`) و موجودیِ کاتالوگ (اگر روشن است) روی نماها. */
async function decorate(shopId, items) {
  if (!items.length) return items;
  const bank = await shelf.bankIds(shopId, items.map((p) => p.id));
  for (const p of items) if (bank.has(p.id)) p.photo = true;
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
  return { bestsellers, newArrivals, deals, windowDays: 30 };
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
  return { product, similar, complementary };
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
  return { items: rows.map(productView).filter((p) => p.priceMinor !== null && p.name), nextCursor: null };
}

/**
 * عکسِ کالای منتشرشده — یا null. `size` = `display` (صفحهٔ کالا) یا `thumb`
 * (کارت): اول نسخهٔ همان اندازه از بانکِ تصاویر (پرچمِ دکان)، وگرنه عکسِ
 * همگام‌شدهٔ امروز.
 */
async function photo(shopId, productId, size = 'display') {
  if (!PRODUCT_ID_RE.test(String(productId || ''))) return null;
  const live = await one(
    `SELECT 1 FROM ${await sfc.productFrom(shopId)} WHERE sp.shop_id = $1 AND sp.product_id = $2 AND sp.published AND ${LIVE_PRODUCT}`,
    [shopId, productId]
  );
  if (!live) return null;
  const banked = await shelf.bankImage(shopId, productId, size === 'thumb' ? 'thumb' : 'display');
  if (banked) return banked;
  return require('./product-photos').get({ app: 'shop', accountId: shopId }, productId);
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
      note: text(x.note, 200), ...sfs.expectedOf(x, i), ...subst.storeLine(x, productId, subOn, i),   // CHK-17 فیِ دیده‌شده، CHK-15 جایگزینی
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
    fulfillment, paymentMethod, ...contact, note: text(b.note, 500), lines, expectedTotalMinor, zoneId, slotStartsAt,
    substitution: subst.orderOf(b, subOn),
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
    deliveryFeeMinor: fees.deliveryFeeMinor,
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
    const fees = { subtotalMinor, deliveryFeeMinor, totalMinor, minOrderMinor: d.minOrderMinor };
    if (subtotalMinor < d.minOrderMinor) {
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
const { LINK_FLAG, tokenOf, linkPolicyOn, listTokenOf, orderIdOf, orderIdOfLink, revokeLink } = require('./storefront-links')({ tokenKey, macOf, orders, one, now, config, ApiError, notFound });

/**
 * نمای مشتری — نه شناسهٔ کارمند، نه یادداشتِ داخلی، نه شماره و نشانی.
 * `delivery` (S5) فقط وقتی پرچمِ نمای پیک روشن است می‌آید (`delivery.customerView`).
 */
function publicOrder(o, token, delivery) {
  return {
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
    //  CHK-12: بازهٔ برگزیده (null = هرچه زودتر) · CHK-07: ناحیهٔ ارسال
    scheduledFor: o.scheduledFor,
    slotEndsAt: o.delivery && o.delivery.slot ? Number(o.delivery.slot.endsAt) : null,
    zoneName: o.address && o.address.zone ? o.address.zone.name : '',
    createdAt: o.createdAt,
    updatedAt: o.updatedAt,
    //  CHK-22: لغوِ رایگان تا پیش از قبول
    canCancel: o.status === 'new',
    timeline: o.events
      .filter((e) => e.kind === 'created' || e.kind === 'status')
      .map((e) => ({ status: e.toStatus, at: e.at })),
    ...(delivery !== undefined ? { delivery } : {}),
  };
}

const CUSTOMER = Object.freeze({ type: 'customer', id: '' });

/** شمارهٔ تأییدشده: از بلیتِ تازه، وگرنه از نشستِ حسابِ مشتری — یا null. */
async function verifiedPhone(ticket, session) {
  const fromTicket = await verifiedPhoneOfTicket(ticket);
  if (fromTicket) return fromTicket;
  const a = session ? await require('./store-customers').auth(session) : null;
  return a ? a.customer.phone : null;
}

async function checkout(sf, { key, body, ticket, session }) {
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
  const price = require('./order-rate').guard(pricer(sf.shop_id, sf, input, { zonesOn, from }), { shopId: sf.shop_id, phone: verified });
  const out = await orders.create(sf.shop_id, { key, body, actor: CUSTOMER, price });
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
  return require('./order-amend').withAmendment(sf.shop_id, id, publicOrder(o, token, await require('./delivery').customerView(sf.shop_id, id)));
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

// ── ۲۳: تأییدِ شمارهٔ مشتری ─────────────────────────────────────────
//  بلیتِ امضاشده: `v2.<شماره>.<صدور>.<انقضا>.<نسل>.<HMAC>` — می‌گوید «این
//  شماره در لحظهٔ صدور با کدِ یک‌بارمصرف تأیید شد». خودِ بلیت در سرور ذخیره
//  نمی‌شود؛ فقط «نسلِ» هر شماره (HMAC ِ شماره ⇒ عدد، store_phone_revocations):
//  «خروج از همه‌جا» و پاک کردنِ حساب نسل را یکی بالا می‌برند و هر بلیتِ کهنه‌تر
//  همان دم بی‌اعتبار است. بلیتِ v1 (پیش از نسل) همان نسلِ صفر است.
//  ⛔ ساختن/ورود به حساب (نشستِ ۱۸۰ روزه) فقط با بلیتِ صادرشده در چند دقیقهٔ
//  اخیر (`accountTicketMaxMs`)؛ ثبت و «سفارش‌های من» با مهلتِ کاملِ بلیت.

const OTP_PURPOSE = 'store_order';
const TICKET_V1 = /^v1\.([A-Za-z0-9_-]{4,40})\.([0-9a-z]{1,12})\.([A-Za-z0-9_-]{32})$/;
const TICKET_V2 = /^v2\.([A-Za-z0-9_-]{4,40})\.([0-9a-z]{1,12})\.([0-9a-z]{1,12})\.([0-9a-z]{1,7})\.([A-Za-z0-9_-]{32})$/;

function ticketMacV1(phone, exp) {
  return crypto.createHmac('sha256', tokenKey()).update(`customer-phone\n${phone}\n${exp}`)
    .digest('base64url').slice(0, 32);
}

function ticketMac(phone, iat, exp, gen) {
  return crypto.createHmac('sha256', tokenKey()).update(`customer-phone-v2\n${phone}\n${iat}\n${exp}\n${gen}`)
    .digest('base64url').slice(0, 32);
}

function ticketOf(phone, at = now(), gen = 0) {
  const exp = at + config.storefront.ticketTtlMs;
  const b64 = Buffer.from(phone).toString('base64url');
  const g = Math.max(0, Math.trunc(Number(gen) || 0));
  return {
    ticket: `v2.${b64}.${at.toString(36)}.${exp.toString(36)}.${g.toString(36)}.${ticketMac(phone, at, exp, g)}`,
    expiresAt: exp,
  };
}

const sameMac = (given, want) => {
  const a = Buffer.from(given);
  const b = Buffer.from(want);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};

/** بلیتِ سالم و نامنقضی ⇒ `{ phone, issuedAt, expiresAt, gen }` — یا null. نسل این‌جا سنجیده نمی‌شود. */
function parseTicket(ticket, at = now()) {
  const t = String(ticket || '');
  let m = TICKET_V2.exec(t);
  if (m) {
    const phone = Buffer.from(m[1], 'base64url').toString('utf8');
    const [iat, exp, gen] = [m[2], m[3], m[4]].map((x) => Number.parseInt(x, 36));
    if (!/^\+[1-9]\d{7,14}$/.test(phone) || !(exp > at)) return null;
    return sameMac(m[5], ticketMac(phone, iat, exp, gen)) ? { phone, issuedAt: iat, expiresAt: exp, gen } : null;
  }
  m = TICKET_V1.exec(t);
  if (!m) return null;
  const phone = Buffer.from(m[1], 'base64url').toString('utf8');
  const exp = Number.parseInt(m[2], 36);
  if (!/^\+[1-9]\d{7,14}$/.test(phone) || !(exp > at)) return null;
  return sameMac(m[3], ticketMacV1(phone, exp))
    ? { phone, issuedAt: exp - config.storefront.ticketTtlMs, expiresAt: exp, gen: 0 } : null;
}

/**
 * شمارهٔ تأییدشده از بلیت — یا null (منقضی، دست‌خورده، بی‌شکل، نسلِ لغوشده،
 * یا کهنه‌تر از `maxAgeMs`). ناهم‌گام است چون نسلِ شماره را از پایگاه می‌خواند.
 * ⛔ جای `phoneOfTicket` ِ هم‌گامِ پیشین: نامِ تازه تا فراخوانِ کهنه‌ای که
 * await ندارد، به‌جای Promise ِ «راست» با خطای آشکار بشکند.
 */
async function verifiedPhoneOfTicket(ticket, { maxAgeMs = null, at = now() } = {}) {
  const t = parseTicket(ticket, at);
  if (!t) return null;
  if (maxAgeMs !== null && !(t.issuedAt >= at - maxAgeMs)) return null;
  const gen = await require('./store-customers').ticketGen(t.phone);
  return gen === t.gen ? t.phone : null;
}

/**
 * آیا کد واقعاً به مشتری می‌رسد؟ در تولید، راهِ `log` یعنی کد فقط در لاگِ
 * سرور می‌نشیند ⇒ تأیید در دسترس نیست و همین را صریح می‌گوییم (۲۳.۳ بندِ ۳).
 */
async function verificationAvailable() {
  if (config.env !== 'production') return true;
  const { provider } = await require('./sms-settings').current();
  return Boolean(provider) && provider !== 'log';
}

async function startVerification(body, { ip = '', shopId = '' } = {}) {
  const phone = require('./validate').phone(obj(body).phone, { required: true });
  if (!(await verificationAvailable())) {
    throw new ApiError(503, 'verification_unavailable',
      'تأییدِ شماره هنوز روی این سرور راه‌اندازی نشده است؛ فعلاً برای سفارش با شمارهٔ دکان تماس بگیرید');
  }
  tokenKey();
  const out = await require('./otp').request(phone, { purpose: OTP_PURPOSE, ip, app: 'shop', shopId });
  return {
    sent: out.sent === true, expiresAt: out.expiresAt, resendSeconds: out.resendSeconds,
    ...(out.devCode ? { devCode: out.devCode } : {}),
  };
}

async function checkVerification(body) {
  const b = obj(body);
  const phone = require('./validate').phone(b.phone, { required: true });
  tokenKey();
  await require('./otp').verify(phone, b.code, { purpose: OTP_PURPOSE });
  const gen = await require('./store-customers').ticketGen(phone);
  return { phone, ...ticketOf(phone, now(), gen) };
}

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
  return rows.map((r) => ({
    orderNo: Number(r.order_no), status: r.status, totalMinor: Number(r.total_minor),
    itemsCount: Number(r.items_count), createdAt: Number(r.created_at), token: listTokenOf(sf.shop_id, r, on),
  }));
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
    orders: page.map((r) => ({
      orderNo: Number(r.order_no), status: r.status, fulfillment: r.fulfillment, totalMinor: Number(r.total_minor),
      itemsCount: Number(r.items_count), createdAt: Number(r.created_at),
      scheduledFor: r.scheduled_for === null ? null : Number(r.scheduled_for),
      preview: names.filter((n) => n.order_id === r.id).map((n) => n.name),
      token: listTokenOf(sf.shop_id, r, on),
    })),
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
    lines: o.lines.map((l) => {
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
    }),
  };
}

// ════════════════════════════════════════════════════════════════════
//  سمتِ دکان
// ════════════════════════════════════════════════════════════════════

function settingsView(sf, status = null) {
  if (!sf) return null;
  const { categories: _c, ...pub } = profile(sf, [], status);
  return {
    ...pub,
    enabled: sf.enabled === true,
    notice: sf.notice || '',
    timezone: sf.timezone || 'Asia/Kabul',
    weeklyHours: asJson(sf.weekly_hours, {}),
    closedDates: asJson(sf.closed_dates, []),
    presenceMode: sf.presence_mode || 'auto',
    declaredUntil: sf.declared_until === null || sf.declared_until === undefined ? null : Number(sf.declared_until),
    updatedAt: Number(sf.updated_at),
  };
}

async function settingsOf(shopId) {
  const sf = await one('SELECT * FROM storefronts WHERE shop_id = $1', [shopId]);
  return sf ? settingsView(sf, await statusOf(sf)) : null;
}

function timezoneOf(v) {
  const tz = str(v);
  try {
    if (!tz || tz.length > 64) throw new Error('x');
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return tz;
  } catch {
    throw badRequest('منطقهٔ زمانی معتبر نیست', 'bad_timezone');
  }
}

/** {"0":[[480,1200]], …} — بازه‌ها به دقیقه از نیمه‌شب، بی هم‌پوشانی؛ شبِ پس از ۲۴ در روزِ بعد. */
function weeklyHoursOf(v) {
  const h = obj(v);
  const out = {};
  for (const [day, list] of Object.entries(h)) {
    if (!/^[0-6]$/.test(day) || !Array.isArray(list) || list.length > 4) throw badRequest('ساعتِ کاری معتبر نیست', 'bad_hours');
    const ranges = list.map((r) => {
      if (!Array.isArray(r) || r.length !== 2) throw badRequest('ساعتِ کاری معتبر نیست', 'bad_hours');
      const o = int(r[0], 'آغاز', { min: 0, max: 1439 });
      const c = int(r[1], 'پایان', { min: 1, max: 1440 });
      if (c <= o) throw badRequest('پایانِ بازه باید پس از آغاز باشد', 'bad_hours');
      return [o, c];
    }).sort((a, b) => a[0] - b[0]);
    for (let i = 1; i < ranges.length; i++) {
      if (ranges[i][0] < ranges[i - 1][1]) throw badRequest('بازه‌های یک روز روی هم افتاده‌اند', 'bad_hours');
    }
    out[day] = ranges;
  }
  return out;
}

function closedDatesOf(v) {
  if (!Array.isArray(v) || v.length > 60) throw badRequest('فهرستِ تعطیلی معتبر نیست', 'bad_closed_dates');
  const out = [...new Set(v.map((d) => {
    const s = str(d);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(s) || Number.isNaN(Date.parse(`${s}T00:00:00Z`))) {
      throw badRequest('تاریخِ تعطیلی باید به شکلِ YYYY-MM-DD باشد', 'bad_closed_dates');
    }
    return s;
  }))].sort();
  return out;
}

async function recordEvent(c, shopId, e) {
  const r = await c.query(
    `INSERT INTO storefront_presence_events (id, shop_id, event_id, actor_id, kind, status, declared_until,
       store_timezone, detail, source_device_id, client_event_at, server_received_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb,$10,$11,$12)
     ON CONFLICT (shop_id, event_id) DO NOTHING RETURNING id`,
    [require('./ulid').ulid(e.at), shopId, e.eventId, e.actorId || '', e.kind, e.status || '', e.declaredUntil ?? null,
      e.timezone || '', JSON.stringify(e.detail || {}), e.deviceId || '', e.clientEventAt ?? null, e.at]
  );
  return r.rows.length > 0;
}

/** تنظیمِ ویترین — فقط فیلدهای آمده عوض می‌شوند. */
async function saveSettings(shopId, body, userId) {
  const b = obj(body);
  const has = (k) => Object.prototype.hasOwnProperty.call(b, k);
  const saved = await tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', ['storefront-slugs']);
    const cur = (await c.query('SELECT * FROM storefronts WHERE shop_id = $1 FOR UPDATE', [shopId])).rows[0];
    const v = {
      slug: cur?.slug || '', enabled: cur?.enabled === true, title: cur?.title || '', tagline: cur?.tagline || '',
      contact_phone: cur?.contact_phone || '', accent: cur?.accent || '', notice: cur?.notice || '',
      timezone: cur?.timezone || 'Asia/Kabul',
      weekly_hours: cur ? asJson(cur.weekly_hours, {}) : {},
      closed_dates: cur ? asJson(cur.closed_dates, []) : [],
      accept_delivery: cur ? cur.accept_delivery === true : true,
      accept_pickup: cur ? cur.accept_pickup === true : true,
      delivery_fee_minor: cur ? Number(cur.delivery_fee_minor) : 0,
      min_order_minor: cur ? Number(cur.min_order_minor) : 0,
    };
    if (has('slug') || !cur) v.slug = slugOf(b.slug);
    if (has('enabled')) v.enabled = bool(b.enabled, 'روشن بودن');
    if (has('title')) v.title = text(b.title, 60);
    if (has('tagline')) v.tagline = text(b.tagline, 120);
    if (has('contactPhone')) v.contact_phone = require('./validate').phone(b.contactPhone) || '';
    if (has('notice')) v.notice = text(b.notice, 120);
    if (has('accent')) {
      const a = str(b.accent).toLowerCase();
      if (a && !/^#[0-9a-f]{6}$/.test(a)) throw badRequest('رنگ باید به شکلِ ‎#rrggbb‎ باشد', 'bad_accent');
      v.accent = a;
    }
    if (has('timezone')) v.timezone = timezoneOf(b.timezone);
    if (has('weeklyHours')) v.weekly_hours = weeklyHoursOf(b.weeklyHours);
    if (has('closedDates')) v.closed_dates = closedDatesOf(b.closedDates);
    if (has('acceptDelivery')) v.accept_delivery = bool(b.acceptDelivery, 'ارسال');
    if (has('acceptPickup')) v.accept_pickup = bool(b.acceptPickup, 'دریافت از دکان');
    if (has('deliveryFeeMinor')) v.delivery_fee_minor = int(b.deliveryFeeMinor, 'هزینهٔ ارسال', { max: MAX_FEE_MINOR });
    if (has('minOrderMinor')) v.min_order_minor = int(b.minOrderMinor, 'حداقلِ سفارش', { max: MAX_PRICE_MINOR });
    if (!v.accept_delivery && !v.accept_pickup) throw badRequest('دست‌کم یکی از ارسال یا دریافت از دکان', 'bad_fulfillment');
    if (v.enabled && !v.title) throw badRequest('برای روشن کردن، نامِ ویترین لازم است', 'title_required');

    const taken = (await c.query('SELECT 1 FROM storefronts WHERE slug = $1 AND shop_id <> $2', [v.slug, shopId])).rows[0];
    if (taken) throw conflict('این نشانی را دکانِ دیگری گرفته است', 'slug_taken');

    const at = now();
    await c.query(
      `INSERT INTO storefronts (shop_id, slug, enabled, title, tagline, contact_phone, accent, notice, timezone,
         weekly_hours, closed_dates, accept_delivery, accept_pickup, delivery_fee_minor, min_order_minor,
         updated_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11::jsonb,$12,$13,$14,$15,$16,$17,$17)
       ON CONFLICT (shop_id) DO UPDATE SET slug = EXCLUDED.slug, enabled = EXCLUDED.enabled, title = EXCLUDED.title,
         tagline = EXCLUDED.tagline, contact_phone = EXCLUDED.contact_phone, accent = EXCLUDED.accent,
         notice = EXCLUDED.notice, timezone = EXCLUDED.timezone, weekly_hours = EXCLUDED.weekly_hours,
         closed_dates = EXCLUDED.closed_dates, accept_delivery = EXCLUDED.accept_delivery,
         accept_pickup = EXCLUDED.accept_pickup, delivery_fee_minor = EXCLUDED.delivery_fee_minor,
         min_order_minor = EXCLUDED.min_order_minor, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at`,
      [shopId, v.slug, v.enabled, v.title, v.tagline, v.contact_phone, v.accent, v.notice, v.timezone,
        JSON.stringify(v.weekly_hours), JSON.stringify(v.closed_dates), v.accept_delivery, v.accept_pickup,
        v.delivery_fee_minor, v.min_order_minor, userId || '', at]
    );
    //  ۳۱.۲ بندِ ۱: تغییرِ ساعتِ کاری هم رویداد دارد
    if (has('timezone') || has('weeklyHours') || has('closedDates')) {
      await recordEvent(c, shopId, {
        eventId: `hours-${require('./ulid').ulid(at)}`, actorId: userId, kind: 'hours', timezone: v.timezone,
        detail: { weeklyHours: v.weekly_hours, closedDates: v.closed_dates }, at,
      });
    }
    return (await c.query('SELECT * FROM storefronts WHERE shop_id = $1', [shopId])).rows[0];
  });
  return settingsView(saved, await statusOf(saved));
}

/**
 * ۳۱.۱/۳۱.۲ — اعلامِ مالک: «آنلاین تا ساعتِ…»، «آنلاین»، «آفلاین»، «بسته»
 * یا «طبقِ ساعتِ کاری». `eventId` از برنامه می‌آید تا صفِ آفلاین دوباره‌فرستادن
 * را بی‌اثر کند؛ رویدادِ کهنه‌تر (زمانِ دستگاه) در تاریخچه می‌نشیند ولی
 * وضعیتِ تازه‌تر را برنمی‌گرداند. «ثبت شد» فقط پس از همین پاسخ.
 */
async function declarePresence(shopId, body, { userId = '', deviceId = '' } = {}) {
  const b = obj(body);
  const eventId = str(b.eventId);
  if (!/^[A-Za-z0-9_.:-]{8,80}$/.test(eventId)) throw badRequest('شناسهٔ رویداد لازم است', 'bad_event_id');
  const mode = str(b.mode);
  if (!['auto', 'online', 'online_until', 'offline', 'closed'].includes(mode)) throw badRequest('وضعیت معتبر نیست', 'bad_mode');
  const at = now();
  let declaredUntil = null;
  if (mode === 'online_until') {
    declaredUntil = int(b.declaredUntil, 'زمانِ پایان', { min: at + 60_000, max: at + MAX_DECLARE_MS });
  }
  const clientEventAt = b.clientEventAt === undefined || b.clientEventAt === null
    ? null : int(b.clientEventAt, 'زمانِ دستگاه', { min: 0, max: 4102444800000 });

  const row = await tx(async (c) => {
    const sf = (await c.query('SELECT * FROM storefronts WHERE shop_id = $1 FOR UPDATE', [shopId])).rows[0];
    if (!sf) throw notFound('هنوز ویترینی ساخته نشده است', 'storefront_missing');
    const newer = clientEventAt === null ? null : (await c.query(
      `SELECT 1 FROM storefront_presence_events WHERE shop_id = $1 AND kind = 'presence'
          AND (detail->>'applied') = 'true' AND COALESCE(client_event_at, server_received_at) > $2 LIMIT 1`,
      [shopId, clientEventAt]
    )).rows[0];
    const applied = !newer;
    const inserted = await recordEvent(c, shopId, {
      eventId, actorId: userId, kind: 'presence', status: mode, declaredUntil, timezone: sf.timezone,
      detail: { applied }, deviceId: text(b.deviceId, 80) || deviceId, clientEventAt, at,
    });
    if (inserted && applied) {
      await c.query(
        'UPDATE storefronts SET presence_mode = $2, declared_until = $3, updated_by = $4, updated_at = $5 WHERE shop_id = $1',
        [shopId, mode, declaredUntil, userId || '', at]
      );
    }
    const sfNow = (await c.query('SELECT * FROM storefronts WHERE shop_id = $1', [shopId])).rows[0];
    return { sf: sfNow, replayed: !inserted, applied: inserted ? applied : null };
  });
  return { status: await statusOf(row.sf), replayed: row.replayed, applied: row.applied, serverReceivedAt: at };
}

/** همهٔ کالاهای دفتر با نشانِ ویترین — برای صفحهٔ «در ویترین» ِ برنامه. */
async function staffProducts(shopId, { cursor, limit } = {}) {
  const lim = Math.min(MAX_PAGE, Math.max(1, Number.parseInt(limit, 10) || PAGE));
  const params = [shopId];
  const src = await sfc.ledgerSource(shopId, { strict: false });   //  S1: همان منبعِ ویترین
  let where = src.where;
  if (cursor) {
    if (!PRODUCT_ID_RE.test(String(cursor))) throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
    params.push(String(cursor));
    where += ` AND r.row_id > $${params.length}`;
  }
  params.push(lim + 1);
  const rows = await many(
    `SELECT r.row_id AS id, r.data, sp.published, sp.online_price_minor, sp.max_qty_milli, sp.description, sp.specs
       FROM ${src.from}
       LEFT JOIN storefront_products sp ON sp.shop_id = r.account_id AND sp.product_id = r.row_id
      WHERE ${where} ORDER BY r.row_id LIMIT $${params.length}`,
    params
  );
  const more = rows.length > lim;
  const page = more ? rows.slice(0, lim) : rows;
  return {
    items: page.map((r) => ({
      ...productView({ ...r, online_price_minor: null }),
      published: r.published === true,
      onlinePriceMinor: r.online_price_minor === null || r.online_price_minor === undefined ? null : Number(r.online_price_minor),
      active: isActive(r.data),
      description: text(r.description, 2000),
      specs: specsOf(r.specs),
    })),
    nextCursor: more ? page[page.length - 1].id : null,
  };
}

function isActive(data) {
  const d = typeof data === 'string' ? JSON.parse(data) : data || {};
  return String(d.active ?? 'true') !== 'false';
}

/** گذاشتن یا برداشتنِ یک کالا از ویترین. */
async function setProduct(shopId, productId, body, userId) {
  const id = String(productId || '');
  if (!PRODUCT_ID_RE.test(id)) throw badRequest('شناسهٔ کالا معتبر نیست', 'bad_product');
  const b = obj(body);
  const published = bool(b.published, 'در ویترین');
  const nullable = (k, field, opts) => (b[k] === null || b[k] === undefined ? null : int(b[k], field, opts));
  const onlinePriceMinor = nullable('onlinePriceMinor', 'قیمتِ آنلاین', { min: 0, max: MAX_PRICE_MINOR });
  const maxQtyMilli = nullable('maxQtyMilli', 'حداکثرِ هر سفارش', { min: 1, max: MAX_QTY_MILLI });
  //  WEB-08: توضیح و مشخصاتِ عمومی — فقط اگر آمده باشند (برنامه‌های قدیمی‌تر نمی‌فرستند)
  const has = (k) => Object.prototype.hasOwnProperty.call(b, k);
  const description = has('description') ? text(b.description, 2000) : null;
  let specs = null;
  if (has('specs')) {
    if (!Array.isArray(b.specs) || b.specs.length > 20) throw badRequest('حداکثر ۲۰ مشخصه', 'bad_specs');
    specs = specsOf(b.specs);
  }
  const src = await sfc.ledgerSource(shopId);
  const exists = await one(`SELECT 1 FROM ${src.from} WHERE ${src.where} AND r.row_id = $2`, [shopId, id]);
  if (!exists) throw notFound('این کالا در دفترِ دکان نیست', 'product_not_found');
  const at = now();
  const row = await one(
    `INSERT INTO storefront_products (shop_id, product_id, published, online_price_minor, max_qty_milli, updated_by, updated_at,
       description, specs, published_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,COALESCE($8,''),COALESCE($9::jsonb,'[]'::jsonb),CASE WHEN $3 THEN $7::bigint END)
     ON CONFLICT (shop_id, product_id) DO UPDATE SET published = EXCLUDED.published,
       online_price_minor = EXCLUDED.online_price_minor, max_qty_milli = EXCLUDED.max_qty_milli,
       updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at,
       description = COALESCE($8, storefront_products.description),
       specs = COALESCE($9::jsonb, storefront_products.specs),
       published_at = COALESCE(storefront_products.published_at, EXCLUDED.published_at)
     RETURNING product_id, description, specs`,
    [shopId, id, published, onlinePriceMinor, maxQtyMilli, userId || '', at, description,
      specs === null ? null : JSON.stringify(specs)]
  );
  return {
    productId: id, published, onlinePriceMinor, maxQtyMilli,
    description: text(row.description, 2000), specs: specsOf(row.specs),
  };
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
  FLAG_KEY, FEATURE_KEY, SLUG_RE, RESERVED, SEARCH_FLAG, SEARCH_KEY, PRODUCT_ID_RE, MAX_CHECKOUT_LINES, MAX_QTY_MILLI,
  normalize, slugOf, slugFromHost, stateOf, open, profile, categories, catalog, catalogByIds, photo,
  highlights, productDetail, reorder, ordersOfPhone, verifiedPhone,
  localParts, presenceOf, statusOf,
  parseCheckout, checkout, track, cancel, tokenOf, orderIdOf, orderIdOfLink, listTokenOf, linkPolicyOn, revokeLink, LINK_FLAG,
  K_ANON, ticketOf, parseTicket, verifiedPhoneOfTicket, verificationAvailable, startVerification, checkVerification, myOrders,
  settingsOf, saveSettings, declarePresence, staffProducts, setProduct, ...sfc,   //  S1 و M2: storefront-catalog.js
  //  بازارچه (`lib/market.js`) همان دروازهٔ کالا را می‌خواند، نه نسخهٔ دومی از آن
  PRODUCT_FROM, PRODUCT_COLS, LIVE_PRODUCT, SQL_NAME, productView,
};
